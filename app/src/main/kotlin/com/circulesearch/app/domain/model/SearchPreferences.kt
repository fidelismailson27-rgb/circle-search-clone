package com.circulesearch.app.domain.model

/** Singleton, user-adjustable search behavior (constitution VI/VIII). */
data class SearchPreferences(
    val textFallbackEnabled: Boolean = true,
    val compressionQuality: Int = DEFAULT_COMPRESSION_QUALITY,
) {
    init {
        require(compressionQuality in 0..100) {
            "compressionQuality must be within 0..100, was $compressionQuality"
        }
    }

    companion object {
        const val DEFAULT_COMPRESSION_QUALITY = 80
    }
}
