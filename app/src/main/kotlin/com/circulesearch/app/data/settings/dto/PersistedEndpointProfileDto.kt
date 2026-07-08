package com.circulesearch.app.data.settings.dto

import kotlinx.serialization.Serializable

/**
 * On-disk shape of [com.circulesearch.app.domain.model.AiEndpointProfile]. Kept
 * separate from the domain model so the domain layer stays free of serialization
 * framework annotations (constitution II) — timestamps are epoch millis here since
 * kotlinx.serialization has no built-in `java.time.Instant` support.
 */
@Serializable
data class PersistedEndpointProfileDto(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val isActive: Boolean,
    val fallbackOrder: Int?,
    val lastConnectionTest: PersistedConnectionTestResultDto?,
)

@Serializable
data class PersistedConnectionTestResultDto(
    val epochMillis: Long,
    val success: Boolean,
    val message: String,
)

@Serializable
data class PersistedEndpointProfilesDto(
    val profiles: List<PersistedEndpointProfileDto> = emptyList(),
)
