package com.circulesearch.app.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** research.md R3: attach the image only the first time a given profile sees it in a session. */
class ImageAttachmentPolicyTest {
    private val policy = ImageAttachmentPolicy()

    @Test
    fun `attaches image for a profile that has not seen it yet`() {
        assertTrue(policy.shouldAttachImage("profile-a", profilesImageAlreadySentTo = emptySet()))
    }

    @Test
    fun `does not re-attach image for a profile that already received it`() {
        assertFalse(policy.shouldAttachImage("profile-a", profilesImageAlreadySentTo = setOf("profile-a")))
    }

    @Test
    fun `attaches image for a new fallback profile even if another profile already saw it`() {
        // R2/R3: a fallback introducing a profile that hasn't seen the image yet must
        // still receive it once, even though a different profile already has.
        assertTrue(policy.shouldAttachImage("profile-b", profilesImageAlreadySentTo = setOf("profile-a")))
    }

    @Test
    fun `does not attach for a profile already in a multi-profile sent set`() {
        assertFalse(policy.shouldAttachImage("profile-b", profilesImageAlreadySentTo = setOf("profile-a", "profile-b")))
    }
}
