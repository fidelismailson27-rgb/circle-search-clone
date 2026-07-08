package com.circulesearch.app.ui.result

import com.circulesearch.app.di.DefaultDispatcher
import com.circulesearch.app.domain.model.AiEndpointProfile
import com.circulesearch.app.domain.model.ChatMessage
import com.circulesearch.app.domain.model.ConversationSession
import com.circulesearch.app.domain.model.SearchError
import com.circulesearch.app.domain.repository.ChatTurnResult
import com.circulesearch.app.domain.repository.EndpointProfileRepository
import com.circulesearch.app.domain.repository.OverlaySelectionRegion
import com.circulesearch.app.domain.usecase.SendFollowUpMessageUseCase
import com.circulesearch.app.domain.usecase.StartVisualSearchUseCase
import com.circulesearch.app.domain.usecase.VisualSearchOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Owns the result panel's [ResultUiState] and the [ConversationSession] it displays
 * (data-model.md — this object owns the session, never persisted, FR-028). A new
 * search or follow-up cancels anything still in flight (FR-018/FR-023); dismissing
 * the panel cancels whatever is in flight and drops the whole session (FR-007/
 * FR-029).
 *
 * Deliberately **not** an `androidx.lifecycle.ViewModel`: the result panel is hosted
 * inside a `WindowManager` overlay (research.md R1), not a normal Activity/Fragment/
 * nav-graph destination, so there is no `ViewModelStoreOwner` to scope a real
 * `ViewModel` to — this object's natural lifetime already matches exactly one search
 * interaction, owned and explicitly `close()`d by whatever coordinates the overlay
 * (T022).
 */
class ResultViewModel
    @Inject
    constructor(
        private val startVisualSearchUseCase: StartVisualSearchUseCase,
        private val sendFollowUpMessageUseCase: SendFollowUpMessageUseCase,
        private val endpointProfileRepository: EndpointProfileRepository,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + defaultDispatcher)

        private val _uiState = MutableStateFlow<ResultUiState>(ResultUiState.Idle)
        val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

        private var knownProfiles: List<AiEndpointProfile> = emptyList()
        private var searchJob: Job? = null
        private var lastSelection: OverlaySelectionRegion? = null
        private var currentSession: ConversationSession? = null

        fun startSearch(selection: OverlaySelectionRegion) {
            lastSelection = selection
            currentSession = null
            // A new trigger supersedes a search still in progress (FR-018/FR-023).
            searchJob?.cancel()
            _uiState.value = ResultUiState.Loading

            searchJob =
                scope.launch {
                    // Snapshot for resolving "answered by {name}" (FR-015) and for
                    // session-pinning comparisons (R2) — doesn't need to be live-reactive.
                    knownProfiles = endpointProfileRepository.observeProfiles().first()
                    startVisualSearchUseCase(selection).collect(::applyInitialOutcome)
                }
        }

        /** FR-026: a follow-up turn within the same, already-active conversation. */
        fun sendFollowUp(text: String) {
            val session = currentSession ?: return
            if (text.isBlank()) return
            launchFollowUp(session, text, appendUserMessage = true)
        }

        fun retry() {
            val session = currentSession
            if (session != null) {
                val lastUserMessage = session.messages.lastOrNull { it.role == ChatMessage.Role.User } ?: return
                launchFollowUp(session, lastUserMessage.textContent, appendUserMessage = false)
            } else {
                lastSelection?.let(::startSearch)
            }
        }

        /** FR-007/FR-028/FR-029: cancel any in-flight request and release the whole conversation. */
        fun dismiss() {
            searchJob?.cancel()
            searchJob = null
            lastSelection = null
            currentSession = null
            _uiState.value = ResultUiState.Idle
        }

        /** Must be called by the owner when the overlay is torn down — releases this object's scope entirely. */
        fun close() {
            scope.cancel()
        }

        fun profileName(profileId: String?): String? = profileId?.let { id -> knownProfiles.find { it.id == id }?.name }

        private fun applyInitialOutcome(outcome: VisualSearchOutcome) {
            _uiState.value =
                when (outcome) {
                    is VisualSearchOutcome.Capturing -> ResultUiState.Loading
                    is VisualSearchOutcome.Streaming ->
                        ResultUiState.Streaming(outcome.partialText, profileName(outcome.answeredByProfileId))
                    is VisualSearchOutcome.Succeeded -> {
                        val session = outcome.toInitialSession()
                        currentSession = session
                        ResultUiState.Conversation(session)
                    }
                    is VisualSearchOutcome.Failed -> ResultUiState.Error(outcome.error)
                }
        }

        private fun launchFollowUp(
            session: ConversationSession,
            text: String,
            appendUserMessage: Boolean,
        ) {
            val sessionWithUserTurn = if (appendUserMessage) session.withAppendedUserMessage(text) else session
            currentSession = sessionWithUserTurn
            _uiState.value = ResultUiState.Conversation(sessionWithUserTurn, pendingFollowUp = PendingFollowUp.Loading)

            searchJob?.cancel()
            searchJob =
                scope.launch {
                    sendFollowUpMessageUseCase(sessionWithUserTurn, text).collect { result ->
                        applyFollowUpOutcome(sessionWithUserTurn, result)
                    }
                }
        }

        private fun applyFollowUpOutcome(
            sessionSoFar: ConversationSession,
            result: ChatTurnResult,
        ) {
            _uiState.value =
                when (result) {
                    is ChatTurnResult.Streaming ->
                        ResultUiState.Conversation(
                            sessionSoFar,
                            pendingFollowUp = PendingFollowUp.Streaming(result.partialText, profileName(result.answeredByProfileId)),
                        )
                    is ChatTurnResult.Success -> {
                        val finalSession = sessionSoFar.withAppendedAssistantMessage(result.message, ::isNonActiveProfile)
                        currentSession = finalSession
                        ResultUiState.Conversation(finalSession)
                    }
                    is ChatTurnResult.Failed ->
                        ResultUiState.Conversation(sessionSoFar, pendingFollowUp = PendingFollowUp.Failed(result.error))
                }
        }

        private fun isNonActiveProfile(profileId: String): Boolean = knownProfiles.find { it.id == profileId }?.isActive != true

        private fun VisualSearchOutcome.Succeeded.toInitialSession(): ConversationSession =
            ConversationSession(
                id = UUID.randomUUID().toString(),
                visualSearchRequestId = UUID.randomUUID().toString(),
                messages = listOf(message),
                profilesImageSentTo = message.producedByProfileId?.let { setOf(it) } ?: emptySet(),
                sessionPinnedProfileId = message.producedByProfileId?.takeIf(::isNonActiveProfile),
                state = ConversationSession.State.Active,
                originalImageBytes = imageBytesSent,
            )
    }

private fun ConversationSession.withAppendedUserMessage(text: String): ConversationSession =
    copy(
        messages =
            messages +
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatMessage.Role.User,
                    textContent = text,
                    includesImage = false,
                    producedByProfileId = null,
                    usedTextFallback = false,
                    createdAt = Instant.now(),
                ),
    )

/**
 * Appends the assistant's reply and, per research.md R2, updates
 * [ConversationSession.sessionPinnedProfileId] whenever a non-active (fallback)
 * profile is the one that actually answered — pinning that profile for the rest of
 * this session only, without touching the user's persisted active-profile setting.
 */
private fun ConversationSession.withAppendedAssistantMessage(
    message: ChatMessage,
    isNonActiveProfile: (String) -> Boolean,
): ConversationSession {
    val answeredBy = message.producedByProfileId
    val updatedPin =
        when {
            answeredBy == null -> sessionPinnedProfileId
            isNonActiveProfile(answeredBy) -> answeredBy
            else -> sessionPinnedProfileId
        }
    return copy(
        messages = messages + message,
        profilesImageSentTo = answeredBy?.let { profilesImageSentTo + it } ?: profilesImageSentTo,
        sessionPinnedProfileId = updatedPin,
    )
}

sealed interface ResultUiState {
    data object Idle : ResultUiState

    data object Loading : ResultUiState

    /** [answeredByProfileName] is discreetly shown once known, even mid-stream (FR-015). */
    data class Streaming(val partialText: String, val answeredByProfileName: String?) : ResultUiState

    data class Conversation(val session: ConversationSession, val pendingFollowUp: PendingFollowUp? = null) : ResultUiState

    data class Error(val error: SearchError) : ResultUiState
}

/** State of a follow-up turn (FR-026) layered on top of an already-visible [ResultUiState.Conversation]. */
sealed interface PendingFollowUp {
    data object Loading : PendingFollowUp

    data class Streaming(val partialText: String, val answeredByProfileName: String?) : PendingFollowUp

    data class Failed(val error: SearchError) : PendingFollowUp
}
