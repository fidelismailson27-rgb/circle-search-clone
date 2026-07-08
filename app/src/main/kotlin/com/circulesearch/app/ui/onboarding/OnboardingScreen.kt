package com.circulesearch.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.circulesearch.app.domain.model.PermissionStatus

/**
 * Step sequence: overlay → accessibility → capture capability (FR-017), skipping any
 * already granted. Re-checks live OS state on every resume, since granting overlay/
 * accessibility happens in a separate system Settings screen (FR-013 requires
 * detecting this correctly on return, not just on explicit confirmation).
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onComplete()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Set up Circle Search", style = MaterialTheme.typography.headlineSmall)
            state.currentStep?.let { step ->
                PermissionExplainerCard(
                    type = step,
                    onGrantRequested = {
                        viewModel.markExplained(step)
                        when (step) {
                            PermissionStatus.Type.Overlay -> context.startActivity(overlaySettingsIntent(context.packageName))
                            PermissionStatus.Type.Accessibility -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            // No external screen to open — the actual system consent dialog
                            // only appears at the moment of a real capture (research.md R1).
                            PermissionStatus.Type.MediaProjectionCapability -> viewModel.refresh()
                        }
                    },
                )
            }
        }
    }
}

private fun overlaySettingsIntent(packageName: String) =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
