package com.circulesearch.app.ui.result

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.circulesearch.app.domain.model.ChatMessage
import com.circulesearch.app.domain.model.ConversationSession
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** FR-019 visibility requirement (T070): a text-fallback-derived answer must be visibly labeled as such. */
class ResultBottomSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun textFallbackAnswerIsVisiblyLabeled() {
        val message =
            ChatMessage(
                id = "message-1",
                role = ChatMessage.Role.Assistant,
                textContent = "It's a red mug.",
                includesImage = false,
                producedByProfileId = "profile-1",
                usedTextFallback = true,
                createdAt = Instant.now(),
            )
        val session =
            ConversationSession(
                id = "session-1",
                visualSearchRequestId = "request-1",
                messages = listOf(message),
                profilesImageSentTo = emptySet(),
                sessionPinnedProfileId = null,
                state = ConversationSession.State.Active,
                originalImageBytes = null,
            )

        composeRule.setContent {
            ChatMessageList(
                session = session,
                pendingFollowUp = null,
                resolveProfileName = { "Test Profile" },
                onRetryFollowUp = {},
            )
        }

        composeRule.onNodeWithText("It's a red mug.").assertExists()
        composeRule.onNodeWithText("Answered by Test Profile (from on-screen text)").assertExists()
    }
}
