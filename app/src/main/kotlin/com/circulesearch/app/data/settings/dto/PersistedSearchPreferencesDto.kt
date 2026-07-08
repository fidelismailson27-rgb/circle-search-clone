package com.circulesearch.app.data.settings.dto

import kotlinx.serialization.Serializable

/** On-disk shape of [com.circulesearch.app.domain.model.SearchPreferences]. */
@Serializable
data class PersistedSearchPreferencesDto(
    val textFallbackEnabled: Boolean = true,
    val compressionQuality: Int = 80,
)
