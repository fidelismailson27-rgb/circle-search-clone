package com.circulesearch.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.circulesearch.app.data.settings.dto.PersistedEndpointProfilesDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's [PersistedEndpointProfilesDto] (BYOK profiles, including API
 * keys) as one Tink-encrypted, Base64-encoded blob inside a single DataStore
 * `Preferences` entry (constitution IX). Nothing here is ever written in plaintext.
 */
@Singleton
class EndpointProfileLocalDataSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val cipher: EncryptedBlobCipher,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        val profiles: Flow<PersistedEndpointProfilesDto> =
            context.circleSearchSettingsDataStore.data.map { prefs -> decode(prefs[PROFILES_KEY]) }

        suspend fun save(profiles: PersistedEndpointProfilesDto) {
            val plaintext = json.encodeToString(profiles).toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.encrypt(plaintext)
            val encoded = Base64.getEncoder().encodeToString(ciphertext)
            context.circleSearchSettingsDataStore.edit { prefs -> prefs[PROFILES_KEY] = encoded }
        }

        private fun decode(encoded: String?): PersistedEndpointProfilesDto {
            if (encoded.isNullOrEmpty()) return PersistedEndpointProfilesDto()
            val ciphertext = Base64.getDecoder().decode(encoded)
            val plaintext = cipher.decrypt(ciphertext)
            return json.decodeFromString(plaintext.toString(Charsets.UTF_8))
        }

        private companion object {
            val PROFILES_KEY = stringPreferencesKey("encrypted_endpoint_profiles")
        }
    }
