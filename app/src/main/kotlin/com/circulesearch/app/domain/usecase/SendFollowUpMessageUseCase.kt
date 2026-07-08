package com.circulesearch.app.domain.usecase

import com.circulesearch.app.domain.model.AiEndpointProfile
import com.circulesearch.app.domain.model.ConversationSession
import com.circulesearch.app.domain.repository.ChatTurnResult
import com.circulesearch.app.domain.repository.EndpointProfileRepository
import com.circulesearch.app.domain.repository.VisualSearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Sends a follow-up turn (FR-026/FR-027), replaying the session's full history and
 * applying `ImageAttachmentPolicy`'s per-profile image tracking (both inside
 * [VisualSearchRepository.sendFollowUpMessage]). Implements research.md R2's
 * session-pinning continuation: if the session already has a
 * [ConversationSession.sessionPinnedProfileId] (a fallback profile that answered a
 * prior turn), candidates start from that profile's position in the fallback order —
 * never restarting from the beginning and re-trying an already-known-bad profile.
 */
class SendFollowUpMessageUseCase
    @Inject
    constructor(
        private val visualSearchRepository: VisualSearchRepository,
        private val endpointProfileRepository: EndpointProfileRepository,
    ) {
        operator fun invoke(
            session: ConversationSession,
            userText: String,
        ): Flow<ChatTurnResult> =
            flow {
                val candidates = candidateProfiles(session)
                emitAll(
                    visualSearchRepository.sendFollowUpMessage(
                        priorMessages = session.messages,
                        profilesImageAlreadySentTo = session.profilesImageSentTo,
                        originalImageBytes = session.originalImageBytes,
                        candidateProfiles = candidates,
                        userText = userText,
                    ),
                )
            }

        private suspend fun candidateProfiles(session: ConversationSession): List<AiEndpointProfile> {
            val activeProfile = endpointProfileRepository.getActiveProfile()
            val fallbackChain = endpointProfileRepository.getFallbackChain()
            val defaultOrder = listOfNotNull(activeProfile) + fallbackChain

            val pinnedId = session.sessionPinnedProfileId ?: return defaultOrder
            val pinnedIndex = defaultOrder.indexOfFirst { it.id == pinnedId }
            if (pinnedIndex < 0) return defaultOrder

            return defaultOrder.drop(pinnedIndex)
        }
    }
