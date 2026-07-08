package com.circulesearch.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.circulesearch.app.domain.model.PermissionStatus

/** FR-017: plain-language explanation shown before the corresponding system prompt/settings screen. */
@Composable
fun PermissionExplainerCard(
    type: PermissionStatus.Type,
    onGrantRequested: () -> Unit,
) {
    val (title, explanation) = type.explainerText()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(explanation)
            Button(onClick = onGrantRequested) { Text("Continue") }
        }
    }
}

private fun PermissionStatus.Type.explainerText(): Pair<String, String> =
    when (this) {
        PermissionStatus.Type.Overlay ->
            "Draw over other apps" to
                "Circle Search draws a selection outline directly on top of whatever " +
                "you're looking at, so you can circle something without leaving the app you're in."
        PermissionStatus.Type.Accessibility ->
            "Accessibility service" to
                "Used only as a fallback: if a screen blocks image capture (like some " +
                "banking apps), Circle Search reads the on-screen text instead, only at the " +
                "moment you ask it to — never continuously."
        PermissionStatus.Type.MediaProjectionCapability ->
            "Screen capture" to
                "Each time you circle something, Android will ask you to confirm screen " +
                "capture for that one search. This happens every time by design — Circle " +
                "Search never captures your screen without you starting a search first."
    }
