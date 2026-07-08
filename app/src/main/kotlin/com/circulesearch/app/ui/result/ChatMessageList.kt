package com.circulesearch.app.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.circulesearch.app.domain.model.ChatMessage
import com.circulesearch.app.domain.model.ConversationSession

/**
 * The running conversation (FR-030): every turn so far, plus whatever is currently
 * pending (loading/streaming/error) for a follow-up in flight.
 */
@Composable
fun ChatMessageList(
    session: ConversationSession,
    pendingFollowUp: PendingFollowUp?,
    resolveProfileName: (String?) -> String?,
    onRetryFollowUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(session.messages, key = { it.id }) { message ->
            ChatMessageRow(message, resolveProfileName)
        }
        pendingFollowUp?.let {
            item(key = "pending-follow-up") { PendingFollowUpRow(it, onRetryFollowUp) }
        }
    }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    resolveProfileName: (String?) -> String?,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = if (message.role == ChatMessage.Role.User) "You" else "Assistant",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(message.textContent)
        if (message.role == ChatMessage.Role.Assistant) {
            resolveProfileName(message.producedByProfileId)?.let { name ->
                Text(
                    text = "Answered by $name" + if (message.usedTextFallback) " (from on-screen text)" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PendingFollowUpRow(
    pending: PendingFollowUp,
    onRetry: () -> Unit,
) {
    when (pending) {
        is PendingFollowUp.Loading -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        is PendingFollowUp.Streaming -> {
            Column {
                Text(pending.partialText)
                pending.answeredByProfileName?.let {
                    Text("Answered by $it", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        is PendingFollowUp.Failed -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pending.error.toResultUserMessage(), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}
