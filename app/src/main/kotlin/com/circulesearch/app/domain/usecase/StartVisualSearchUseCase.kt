package com.circulesearch.app.domain.usecase

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
    ) {
        operator fun invoke(selection: OverlaySelectionRegion): Flow<VisualSearchOutcome> =
            flow {
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
                        // Baseline MVP behavior: a clear, non-silent failure (constitution
                        // V). US5 (T068) routes this to the text-extraction fallback instead.
                        emit(VisualSearchOutcome.Failed(SearchError.CaptureBlocked))
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

        private companion object {
            const val INITIAL_TURN_PROMPT =
                "Describe what's circled in this image and answer any obvious question about it."
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
