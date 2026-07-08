package com.circulesearch.app.domain.usecase

import com.circulesearch.app.domain.model.AiEndpointProfile
import com.circulesearch.app.domain.model.ChatMessage
import com.circulesearch.app.domain.model.SearchError
import com.circulesearch.app.domain.repository.ChatTurnResult
import com.circulesearch.app.domain.repository.EndpointProfileRepository
import com.circulesearch.app.domain.repository.OverlaySelectionRegion
import com.circulesearch.app.domain.repository.ScreenCaptureOutcome
import com.circulesearch.app.domain.repository.ScreenCaptureRepository
import com.circulesearch.app.domain.repository.VisualSearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Orchestrates the core P1 flow (FR-001..FR-007): confirms an active profile exists,
 * captures+crops+compresses the selection, then sends the initial message — emitting
 * explicit `Capturing`/`Streaming`/`Succeeded`/`Failed` states (constitution V/X).
 * Automatic fallback across profiles is handled inside [VisualSearchRepository]
 * itself (US2/T047); this use case only supplies the fallback chain.
 */
class StartVisualSearchUseCase
    @Inject
    constructor(
        private val endpointProfileRepository: EndpointProfileRepository,
        private val screenCaptureRepository: ScreenCaptureRepository,
        private val visualSearchRepository: VisualSearchRepository,
        private val checkRequiredPermissionsUseCase: CheckRequiredPermissionsUseCase,
    ) {
        operator fun invoke(selection: OverlaySelectionRegion): Flow<VisualSearchOutcome> =
            flow {
                // FR-018: re-check live permission state at time of use, not just at
                // onboarding — a previously granted permission may have been revoked.
                val missingPermissions = checkRequiredPermissionsUseCase()
                if (missingPermissions.isNotEmpty()) {
                    emit(VisualSearchOutcome.Failed(SearchError.PermissionsMissing(missingPermissions.map { it.permissionType })))
                    return@flow
                }

                val activeProfile = endpointProfileRepository.getActiveProfile()
                if (activeProfile == null) {
                    // No profile configured/active yet — FR-017/FR-022, redirect-to-Settings
                    // is the caller's (ui layer's) responsibility on seeing this outcome.
                    emit(VisualSearchOutcome.Failed(SearchError.NoActiveProfileConfigured))
                    return@flow
                }

                emit(VisualSearchOutcome.Capturing)
                val preferences = endpointProfileRepository.observeSearchPreferences().first()

                when (val captureOutcome = screenCaptureRepository.captureAndProcessSelection(selection, preferences.compressionQuality)) {
                    is ScreenCaptureOutcome.Failed -> emit(VisualSearchOutcome.Failed(captureOutcome.error))
                    is ScreenCaptureOutcome.Blocked -> {
                        emitAll(handleBlockedCapture(activeProfile, preferences.textFallbackEnabled))
                    }
                    is ScreenCaptureOutcome.Success -> {
                        val fallbackChain = endpointProfileRepository.getFallbackChain()
                        val turns =
                            visualSearchRepository.sendInitialMessage(
                                activeProfile = activeProfile,
                                fallbackChain = fallbackChain,
                                imageBytes = captureOutcome.compressedImageBytes,
                                userText = INITIAL_TURN_PROMPT,
                            )
                        emitAll(turns.map { it.toOutcome(captureOutcome.compressedImageBytes) })
                    }
                }
            }

        /**
         * FR-019/FR-020: when the frame is blocked (typically `FLAG_SECURE`), fall back
         * to on-screen text extraction if the user has enabled that preference and
         * extraction actually yields something usable — otherwise a clear failure,
         * never a silent/blank result (constitution V).
         */
        private fun handleBlockedCapture(
            activeProfile: AiEndpointProfile,
            textFallbackEnabled: Boolean,
        ): Flow<VisualSearchOutcome> =
            flow {
                if (!textFallbackEnabled) {
                    emit(VisualSearchOutcome.Failed(SearchError.CaptureBlocked))
                    return@flow
                }

                val extractedText = screenCaptureRepository.extractFallbackText()
                if (extractedText.isNullOrBlank()) {
                    emit(VisualSearchOutcome.Failed(SearchError.CaptureBlocked))
                    return@flow
                }

                val fallbackChain = endpointProfileRepository.getFallbackChain()
                val turns =
                    visualSearchRepository.sendInitialMessage(
                        activeProfile = activeProfile,
                        fallbackChain = fallbackChain,
                        imageBytes = null,
                        userText = "$TEXT_FALLBACK_PROMPT\n\n$extractedText",
                        usedTextFallback = true,
                    )
                emitAll(turns.map { it.toOutcome(imageBytesSent = null) })
            }

        private companion object {
            const val INITIAL_TURN_PROMPT =
                "Describe what's circled in this image and answer any obvious question about it."
            const val TEXT_FALLBACK_PROMPT =
                "The screen could not be captured as an image, so here is the on-screen text instead. " +
                    "Answer based on this text:"
        }
    }

sealed interface VisualSearchOutcome {
    data object Capturing : VisualSearchOutcome

    data class Streaming(val partialText: String, val answeredByProfileId: String) : VisualSearchOutcome

    data class Succeeded(val message: ChatMessage, val imageBytesSent: ByteArray?) : VisualSearchOutcome

    data class Failed(val error: SearchError) : VisualSearchOutcome
}

private fun ChatTurnResult.toOutcome(imageBytesSent: ByteArray?): VisualSearchOutcome =
    when (this) {
        is ChatTurnResult.Streaming -> VisualSearchOutcome.Streaming(partialText, answeredByProfileId)
        is ChatTurnResult.Success -> VisualSearchOutcome.Succeeded(message, imageBytesSent)
        is ChatTurnResult.Failed -> VisualSearchOutcome.Failed(error)
    }
