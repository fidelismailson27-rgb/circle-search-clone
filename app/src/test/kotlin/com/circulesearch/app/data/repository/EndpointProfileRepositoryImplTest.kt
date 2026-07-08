package com.circulesearch.app.data.repository

import com.circulesearch.app.data.settings.EndpointProfileLocalDataSource
import com.circulesearch.app.data.settings.SearchPreferencesLocalDataSource
import com.circulesearch.app.data.settings.dto.PersistedEndpointProfilesDto
import com.circulesearch.app.data.settings.dto.PersistedSearchPreferencesDto
import com.circulesearch.app.domain.model.AiEndpointProfile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EndpointProfileRepositoryImplTest {
    private lateinit var profilesFlow: MutableStateFlow<PersistedEndpointProfilesDto>
    private lateinit var profilesDataSource: EndpointProfileLocalDataSource
    private lateinit var preferencesDataSource: SearchPreferencesLocalDataSource
    private lateinit var repository: EndpointProfileRepositoryImpl

    @Before
    fun setUp() {
        profilesFlow = MutableStateFlow(PersistedEndpointProfilesDto())
        profilesDataSource =
            mockk {
                every { profiles } returns profilesFlow
                coEvery { save(any()) } answers { profilesFlow.value = firstArg() }
            }
        preferencesDataSource =
            mockk {
                every { preferences } returns MutableStateFlow(PersistedSearchPreferencesDto())
            }
        repository = EndpointProfileRepositoryImpl(profilesDataSource, preferencesDataSource)
    }

    @Test
    fun `saving the first active profile leaves it the only active profile`() =
        runTest {
            repository.saveProfile(profile("a", isActive = true))

            val profiles = repository.observeProfiles().first()
            assertEquals(1, profiles.size)
            assertTrue(profiles.single().isActive)
        }

    @Test
    fun `marking a new profile active deactivates the previously active one`() =
        runTest {
            repository.saveProfile(profile("a", isActive = true))
            repository.saveProfile(profile("b", isActive = true))

            val profiles = repository.observeProfiles().first()
            assertEquals(1, profiles.count { it.isActive })
            assertTrue(profiles.single { it.id == "b" }.isActive)
            assertTrue(!profiles.single { it.id == "a" }.isActive)
        }

    @Test
    fun `setActiveProfile switches which single profile is active`() =
        runTest {
            repository.saveProfile(profile("a", isActive = true))
            repository.saveProfile(profile("b", isActive = false))

            repository.setActiveProfile("b")

            val profiles = repository.observeProfiles().first()
            assertTrue(profiles.single { it.id == "b" }.isActive)
            assertTrue(!profiles.single { it.id == "a" }.isActive)
        }

    @Test
    fun `setFallbackOrder assigns ascending order and excludes the active profile`() =
        runTest {
            repository.saveProfile(profile("active", isActive = true))
            repository.saveProfile(profile("b", isActive = false))
            repository.saveProfile(profile("c", isActive = false))

            repository.setFallbackOrder(listOf("c", "b"))

            val chain = repository.getFallbackChain()
            assertEquals(listOf("c", "b"), chain.map { it.id })
            assertNull(repository.getProfile("active")?.fallbackOrder)
        }

    @Test
    fun `deleteProfile removes it from the observed list`() =
        runTest {
            repository.saveProfile(profile("a", isActive = true))
            repository.saveProfile(profile("b", isActive = false))

            repository.deleteProfile("b")

            val profiles = repository.observeProfiles().first()
            assertEquals(listOf("a"), profiles.map { it.id })
        }

    private fun profile(
        id: String,
        isActive: Boolean,
    ) = AiEndpointProfile(
        id = id,
        name = "Profile $id",
        baseUrl = "https://example.com/$id",
        apiKey = "key-$id",
        modelName = "model-$id",
        isActive = isActive,
        fallbackOrder = null,
        lastConnectionTest = null,
    )
}
