package com.circulesearch.app.ui.result

import com.circulesearch.app.domain.model.ConversationSession
import com.circulesearch.app.domain.model.SearchError
import com.circulesearch.app.domain.repository.OverlaySelectionRegion
import com.circulesearch.app.domain.usecase.StartVisualSearchUseCase
import com.circulesearch.app.domain.usecase.VisualSearchOutcome
import com.circulesearch.app.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Owns the result panel's [ResultUiState] and the [ConversationSession] it displays
 * (data-model.md — this object owns the session, never persisted). A new search
 * cancels any prior one still in flight (FR-018/FR-023); dismissing the panel cancels
 * whatever is in flight and drops the whole session (FR-007/FR-028/FR-029).
 *
 * Deliberately **not** an `androidx.lifecycle.ViewModel`: the result panel is hosted
 * inside a `WindowManager` overlay (research.md R1), not a normal Activity/Fragment/
 * nav-graph destination, so there is no `ViewModelStoreOwner` to scope a real
 * `ViewModel` to, and no configuration-change-survival need to justify one — this
 * object's natural lifetime already matches exactly one search interaction, owned and
 * explicitly `close()`d by whatever coordinates the overlay (T022).
 */
class ResultViewModel
    @Inject
    constructor(
        private val startVisualSearchUseCase: StartVisualSearchUseCase,
        @DefaultDispatcher defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + defaultDispatcher)

        private val _uiState = MutableStateFlow<ResultUiState>(ResultUiState.Idle)
        val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

        private var searchJob: Job? = null
        private var lastSelection: OverlaySelectionRegion? = null

        fun startSearch(selection: OverlaySelectionRegion) {
            lastSelection = selection
            // A new trigger supersedes a search still in progress (FR-018/FR-023).
            searchJob?.cancel()
            _uiState.value = ResultUiState.Loading

            searchJob =
                scope.launch {
                    startVisualSearchUseCase(selection).collect(::applyOutcome)
                }
        }

        fun retry() {
            lastSelection?.let(::startSearch)
        }

        /** FR-007/FR-028: cancel any in-flight request and release the whole conversation. */
        fun dismiss() {
            searchJob?.cancel()
            searchJob = null
            lastSelection = null
            _uiState.value = ResultUiState.Idle
        }

        /** Must be called by the owner when the overlay is torn down — releases this object's scope entirely. */
        fun close() {
            scope.cancel()
        }

        private fun applyOutcome(outcome: VisualSearchOutcome) {
            _uiState.value =
                when (outcome) {
                    is VisualSearchOutcome.Capturing -> ResultUiState.Loading
                    is VisualSearchOutcome.Streaming -> ResultUiState.Streaming(outcome.partialText)
                    is VisualSearchOutcome.Succeeded -> ResultUiState.Conversation(outcome.toInitialSession())
                    is VisualSearchOutcome.Failed -> ResultUiState.Error(outcome.error)
                }
        }

        private fun VisualSearchOutcome.Succeeded.toInitialSession(): ConversationSession =
            ConversationSession(
                id = UUID.randomUUID().toString(),
                visualSearchRequestId = UUID.randomUUID().toString(),
                messages = listOf(message),
                profilesImageSentTo = message.producedByProfileId?.let { setOf(it) } ?: emptySet(),
                sessionPinnedProfileId = null,
                state = ConversationSession.State.Active,
                originalImageBytes = imageBytesSent,
            )
    }

sealed interface ResultUiState {
    data object Idle : ResultUiState

    data object Loading : ResultUiState

    data class Streaming(val partialText: String) : ResultUiState

    data class Conversation(val session: ConversationSession) : ResultUiState

    data class Error(val error: SearchError) : ResultUiState
}
