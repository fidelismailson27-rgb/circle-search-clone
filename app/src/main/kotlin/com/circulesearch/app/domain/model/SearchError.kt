package com.circulesearch.app.domain.model

/** Explicit, typed failure modes — never a silently-swallowed exception (constitution V). */
sealed class SearchError {
    data class Network(val cause: Throwable) : SearchError()

    data object Timeout : SearchError()

    data class Http(val code: Int, val body: String?) : SearchError()

    data class MalformedResponse(val details: String) : SearchError()

    /** The active profile and every profile in its fallback order failed (FR-016). */
    data class AllProfilesExhausted(val attempts: List<ProfileAttempt>) : SearchError()

    data object CaptureBlocked : SearchError()

    data object NoActiveProfileConfigured : SearchError()

    /** FR-018: a previously granted permission (overlay/accessibility) was revoked since last checked. */
    data class PermissionsMissing(val missing: List<PermissionStatus.Type>) : SearchError()

    data object Cancelled : SearchError()
}

data class ProfileAttempt(val profileId: String, val profileName: String, val error: SearchError)
