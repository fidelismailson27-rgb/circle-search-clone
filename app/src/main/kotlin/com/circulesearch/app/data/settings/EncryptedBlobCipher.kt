package com.circulesearch.app.data.settings

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Keystore-backed AEAD encryption for BYOK credentials (constitution IX).
 * The keyset itself is stored (encrypted, via [AndroidKeysetManager]'s Keystore
 * master key) in a dedicated SharedPreferences file that holds no plaintext secret —
 * only the encrypted values ever get written into DataStore by the callers of this
 * class (T014/T015).
 *
 * Uses Tink directly (`com.google.crypto.tink:tink-android`) rather than
 * `androidx.security.crypto`'s `EncryptedSharedPreferences`, which is deprecated —
 * `AndroidKeysetManager` + `Aead` is the same underlying primitive that library used
 * internally.
 */
@Singleton
class EncryptedBlobCipher
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val aead: Aead =
            run {
                AeadConfig.register()
                AndroidKeysetManager.Builder()
                    .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                    .keysetHandle
                    .getPrimitive(Aead::class.java)
            }

        fun encrypt(plaintext: ByteArray): ByteArray = aead.encrypt(plaintext, NO_ASSOCIATED_DATA)

        fun decrypt(ciphertext: ByteArray): ByteArray = aead.decrypt(ciphertext, NO_ASSOCIATED_DATA)

        private companion object {
            const val KEYSET_NAME = "circle_search_byok_keyset"
            const val PREF_FILE_NAME = "circle_search_byok_keyset_prefs"
            const val MASTER_KEY_URI = "android-keystore://circle_search_master_key"
            val NO_ASSOCIATED_DATA = ByteArray(0)
        }
    }
