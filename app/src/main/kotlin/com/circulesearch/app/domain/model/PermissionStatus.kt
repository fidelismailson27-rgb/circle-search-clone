package com.circulesearch.app.domain.model

import java.time.Instant

/**
 * Tracked per required system permission (FR-013/FR-017/FR-018). [Type.MediaProjectionCapability]
 * means "the app is allowed to *prompt* for capture consent" — an actual grant is
 * never a durable status (research.md R1: re-obtained on every single capture).
 */
data class PermissionStatus(
    val permissionType: Type,
    val explainedToUser: Boolean,
    val granted: Boolean,
    val lastCheckedAt: Instant,
) {
    enum class Type { Overlay, Accessibility, MediaProjectionCapability }
}
