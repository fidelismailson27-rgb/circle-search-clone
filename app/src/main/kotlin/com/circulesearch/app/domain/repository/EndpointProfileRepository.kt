package com.circulesearch.app.domain.repository

import com.circulesearch.app.domain.model.AiEndpointProfile
import com.circulesearch.app.domain.model.ConnectionTestResult
import com.circulesearch.app.domain.model.SearchPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for the user's BYOK configuration (constitution IX). Backed by an
 * encrypted DataStore (T014/T015) — never a plaintext store.
 */
interface EndpointProfileRepository {
    fun observeProfiles(): Flow<List<AiEndpointProfile>>

    suspend fun getProfile(id: String): AiEndpointProfile?

    suspend fun getActiveProfile(): AiEndpointProfile?

    /** Non-active profiles ordered by [AiEndpointProfile.fallbackOrder] ascending, nulls excluded. */
    suspend fun getFallbackChain(): List<AiEndpointProfile>

    /** Validates and saves; enforces the single-active-profile invariant (FR-009). */
    suspend fun saveProfile(profile: AiEndpointProfile)

    suspend fun deleteProfile(id: String)

    suspend fun setActiveProfile(id: String)

    suspend fun setFallbackOrder(orderedProfileIds: List<String>)

    suspend fun recordConnectionTestResult(id: String, result: ConnectionTestResult)

    fun observeSearchPreferences(): Flow<SearchPreferences>

    suspend fun updateSearchPreferences(preferences: SearchPreferences)
}
