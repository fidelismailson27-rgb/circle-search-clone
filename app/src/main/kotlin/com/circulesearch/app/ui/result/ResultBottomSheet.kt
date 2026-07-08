package com.circulesearch.app.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.circulesearch.app.domain.model.SearchError

/**
 * Explicit loading (skeleton) / streaming / conversation / error states (constitution
 * X) — the error state always includes a visible retry action (FR-024). Once an
 * initial answer exists, the panel becomes a running chat ([ChatMessageList] +
 * [ChatInputBar], FR-030). Dismissing cancels any in-flight request and drops the
 * whole session via [ResultViewModel.dismiss] (FR-007/FR-029).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultBottomSheet(
    viewModel: ResultViewModel,
    onDismissed: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    if (uiState is ResultUiState.Idle) return

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.dismiss()
            onDismissed()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val state = uiState) {
                is ResultUiState.Idle -> Unit
                is ResultUiState.Loading -> LoadingSkeleton()
                is ResultUiState.Streaming -> {
                    Text(state.partialText)
                    state.answeredByProfileName?.let { AnsweredByCaption(it) }
                }
                is ResultUiState.Conversation -> {
                    ChatMessageList(
                        session = state.session,
                        pendingFollowUp = state.pendingFollowUp,
                        resolveProfileName = viewModel::profileName,
                        onRetryFollowUp = viewModel::retry,
                        modifier = Modifier.heightIn(max = 360.dp),
                    )
                    ChatInputBar(
                        enabled = state.pendingFollowUp !is PendingFollowUp.Loading && state.pendingFollowUp !is PendingFollowUp.Streaming,
                        onSend = viewModel::sendFollowUp,
                    )
                }
                is ResultUiState.Error -> ErrorState(error = state.error, onRetry = viewModel::retry)
            }
        }
    }
}

@Composable
private fun LoadingSkeleton() {
    CircularProgressIndicator()
}

@Composable
internal fun AnsweredByCaption(profileName: String) {
    Text(
        text = "Answered by $profileName",
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ErrorState(
    error: SearchError,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(error.toResultUserMessage())
        OutlinedButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

internal fun SearchError.toResultUserMessage(): String =
    when (this) {
        is SearchError.Network -> "Network error. Check your connection and try again."
        is SearchError.Timeout -> "The request took too long. Try again."
        is SearchError.Http -> "The AI endpoint returned an error (code $code)."
        is SearchError.MalformedResponse -> "The AI endpoint's response couldn't be understood."
        is SearchError.AllProfilesExhausted -> {
            // FR-016: one clear, aggregated error naming every profile that was tried.
            val names = attempts.joinToString(", ") { it.profileName }
            "All configured AI endpoints failed to respond: $names."
        }
        is SearchError.CaptureBlocked -> "This screen couldn't be captured."
        is SearchError.NoActiveProfileConfigured -> "No AI endpoint is configured yet. Open Settings to add one."
        is SearchError.PermissionsMissing -> "A required permission is missing or was revoked. Open Circle Search to fix it."
        is SearchError.Cancelled -> "Search cancelled."
    }
