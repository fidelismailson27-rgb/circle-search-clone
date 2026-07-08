package com.circulesearch.app.data.repository

import com.circulesearch.app.data.settings.EndpointProfileLocalDataSource
import com.circulesearch.app.data.settings.SearchPreferencesLocalDataSource
import com.circulesearch.app.data.settings.dto.PersistedConnectionTestResultDto
import com.circulesearch.app.data.settings.dto.PersistedEndpointProfileDto
import com.circulesearch.app.data.settings.dto.PersistedEndpointProfilesDto
import com.circulesearch.app.data.settings.dto.PersistedSearchPreferencesDto
import com.circulesearch.app.domain.model.AiEndpointProfile
import com.circulesearch.app.domain.model.ConnectionTestResult
import com.circulesearch.app.domain.model.SearchPreferences
import com.circulesearch.app.domain.repository.EndpointProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EndpointProfileRepositoryImpl
    @Inject
    constructor(
        private val profilesDataSource: EndpointProfileLocalDataSource,
        private val preferencesDataSource: SearchPreferencesLocalDataSource,
    ) : EndpointProfileRepository {
        override fun observeProfiles(): Flow<List<AiEndpointProfile>> =
            profilesDataSource.profiles.map { dto -> dto.profiles.map { it.toDomain() } }

        override suspend fun getProfile(id: String): AiEndpointProfile? = currentProfiles().find { it.id == id }

        override suspend fun getActiveProfile(): AiEndpointProfile? = currentProfiles().find { it.isActive }

        override suspend fun getFallbackChain(): List<AiEndpointProfile> =
            currentProfiles()
                .filter { !it.isActive && it.fallbackOrder != null }
                .sortedBy { it.fallbackOrder }

        override suspend fun saveProfile(profile: AiEndpointProfile) {
            require(profile.name.isNotBlank()) { "Profile name must not be blank" }
            require(profile.baseUrl.isNotBlank()) { "Profile baseUrl must not be blank" }
            require(profile.modelName.isNotBlank()) { "Profile modelName must not be blank" }

            val withoutExisting = currentProfiles().filterNot { it.id == profile.id }
            val updated =
                if (profile.isActive) {
                    // Enforce the single-active-profile invariant (FR-009).
                    withoutExisting.map { it.copy(isActive = false) } + profile
                } else {
                    withoutExisting + profile
                }
            persist(updated)
        }

        override suspend fun deleteProfile(id: String) {
            persist(currentProfiles().filterNot { it.id == id })
        }

        override suspend fun setActiveProfile(id: String) {
            persist(currentProfiles().map { it.copy(isActive = it.id == id) })
        }

        override suspend fun setFallbackOrder(orderedProfileIds: List<String>) {
            val rank = orderedProfileIds.withIndex().associate { (index, id) -> id to index + 1 }
            persist(
                currentProfiles().map { profile ->
                    if (profile.isActive) profile.copy(fallbackOrder = null) else profile.copy(fallbackOrder = rank[profile.id])
                },
            )
        }

        override suspend fun recordConnectionTestResult(
            id: String,
            result: ConnectionTestResult,
        ) {
            persist(currentProfiles().map { if (it.id == id) it.copy(lastConnectionTest = result) else it })
        }

        override fun observeSearchPreferences(): Flow<SearchPreferences> = preferencesDataSource.preferences.map { it.toDomain() }

        override suspend fun updateSearchPreferences(preferences: SearchPreferences) {
            preferencesDataSource.save(
                PersistedSearchPreferencesDto(preferences.textFallbackEnabled, preferences.compressionQuality),
            )
        }

        private suspend fun currentProfiles(): List<AiEndpointProfile> =
            profilesDataSource.profiles.first().profiles.map { it.toDomain() }

        private suspend fun persist(profiles: List<AiEndpointProfile>) {
            profilesDataSource.save(PersistedEndpointProfilesDto(profiles.map { it.toDto() }))
        }
    }

private fun PersistedEndpointProfileDto.toDomain() =
    AiEndpointProfile(
        id = id,
        name = name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelName = modelName,
        isActive = isActive,
        fallbackOrder = fallbackOrder,
        lastConnectionTest =
            lastConnectionTest?.let {
                ConnectionTestResult(Instant.ofEpochMilli(it.epochMillis), it.success, it.message)
            },
    )

private fun AiEndpointProfile.toDto() =
    PersistedEndpointProfileDto(
        id = id,
        name = name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelName = modelName,
        isActive = isActive,
        fallbackOrder = fallbackOrder,
        lastConnectionTest =
            lastConnectionTest?.let {
                PersistedConnectionTestResultDto(it.timestamp.toEpochMilli(), it.success, it.message)
            },
    )

private fun PersistedSearchPreferencesDto.toDomain() = SearchPreferences(textFallbackEnabled, compressionQuality)
