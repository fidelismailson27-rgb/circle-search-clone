package com.circulesearch.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.circulesearch.app.data.settings.dto.PersistedSearchPreferencesDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists [PersistedSearchPreferencesDto] in plain (non-encrypted) DataStore
 * entries — nothing here is a secret, unlike [EndpointProfileLocalDataSource].
 */
@Singleton
class SearchPreferencesLocalDataSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val preferences: Flow<PersistedSearchPreferencesDto> =
            context.circleSearchSettingsDataStore.data.map { prefs ->
                PersistedSearchPreferencesDto(
                    textFallbackEnabled = prefs[TEXT_FALLBACK_KEY] ?: true,
                    compressionQuality = prefs[COMPRESSION_QUALITY_KEY] ?: 80,
                )
            }

        suspend fun save(preferences: PersistedSearchPreferencesDto) {
            context.circleSearchSettingsDataStore.edit { prefs ->
                prefs[TEXT_FALLBACK_KEY] = preferences.textFallbackEnabled
                prefs[COMPRESSION_QUALITY_KEY] = preferences.compressionQuality
            }
        }

        private companion object {
            val TEXT_FALLBACK_KEY = booleanPreferencesKey("text_fallback_enabled")
            val COMPRESSION_QUALITY_KEY = intPreferencesKey("compression_quality")
        }
    }
