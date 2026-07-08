package com.circulesearch.app.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
 * Explicit loading (skeleton) / streaming / success / error states (constitution X)
 * — the error state always includes a visible retry action (FR-024). Dismissing
 * cancels any in-flight request and drops the session via [ResultViewModel.dismiss].
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
                is ResultUiState.Streaming -> Text(state.partialText)
                is ResultUiState.Conversation -> {
                    state.session.messages.forEach { message -> Text(message.textContent) }
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
private fun ErrorState(
    error: SearchError,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(error.toUserMessage())
        OutlinedButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

private fun SearchError.toUserMessage(): String =
    when (this) {
        is SearchError.Network -> "Network error. Check your connection and try again."
        is SearchError.Timeout -> "The request took too long. Try again."
        is SearchError.Http -> "The AI endpoint returned an error (code $code)."
        is SearchError.MalformedResponse -> "The AI endpoint's response couldn't be understood."
        is SearchError.AllProfilesExhausted -> "All configured AI endpoints failed to respond."
        is SearchError.CaptureBlocked -> "This screen couldn't be captured."
        is SearchError.NoActiveProfileConfigured -> "No AI endpoint is configured yet. Open Settings to add one."
        is SearchError.Cancelled -> "Search cancelled."
    }
