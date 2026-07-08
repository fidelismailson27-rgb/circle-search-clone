package com.circulesearch.app.data.network

import javax.inject.Inject

/**
 * Research.md R3 — the compressed selection image is attached to a profile's request
 * only the first time that specific profile is used within a Conversation Session;
 * every other turn to a profile that has already seen it is text-only. Kept as one
 * small, isolated, swappable policy so this token-economy tradeoff can be changed to
 * "always attach" later without touching the rest of the chat pipeline.
 */
class ImageAttachmentPolicy
    @Inject
    constructor() {
        fun shouldAttachImage(
            profileId: String,
            profilesImageAlreadySentTo: Set<String>,
        ): Boolean = profileId !in profilesImageAlreadySentTo
    }
