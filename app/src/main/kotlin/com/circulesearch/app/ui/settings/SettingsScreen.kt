package com.circulesearch.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.circulesearch.app.domain.model.AiEndpointProfile
import com.circulesearch.app.domain.model.SearchPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onEditProfile: (AiEndpointProfile?) -> Unit,
    onManageFallbackOrder: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val testResults by viewModel.testResults.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Circle Search Settings") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditProfile(null) }) {
                Icon(Icons.Default.Add, contentDescription = "Add profile")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("AI Endpoint Profiles", style = MaterialTheme.typography.titleMedium)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.profiles, key = { it.id }) { profile ->
                    ProfileRow(
                        profile = profile,
                        testResultMessage = testResults[profile.id]?.message,
                        onSetActive = { viewModel.setActiveProfile(profile.id) },
                        onEdit = { onEditProfile(profile) },
                        onDelete = { viewModel.deleteProfile(profile.id) },
                        onTest = { viewModel.testConnection(profile) },
                    )
                }
            }

            if (state.profiles.size > 1) {
                Button(onClick = onManageFallbackOrder) { Text("Manage fallback order") }
            }

            PreferencesSection(preferences = state.preferences, onPreferencesChanged = viewModel::updatePreferences)
        }
    }
}

@Composable
private fun ProfileRow(
    profile: AiEndpointProfile,
    testResultMessage: String?,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = profile.isActive, onClick = onSetActive)
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name.ifBlank { "(unnamed profile)" })
                    // FR-012: never the API key, only the non-secret connection details.
                    Text(profile.baseUrl, style = MaterialTheme.typography.bodySmall)
                    Text(profile.modelName, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row {
                TextButton(onClick = onTest) { Text("Test") }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
            testResultMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun PreferencesSection(
    preferences: SearchPreferences,
    onPreferencesChanged: (SearchPreferences) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Preferences", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Text fallback when capture is blocked", modifier = Modifier.weight(1f))
            Switch(
                checked = preferences.textFallbackEnabled,
                onCheckedChange = { onPreferencesChanged(preferences.copy(textFallbackEnabled = it)) },
            )
        }
        Text("Compression quality: ${preferences.compressionQuality}")
        Slider(
            value = preferences.compressionQuality.toFloat(),
            onValueChange = { onPreferencesChanged(preferences.copy(compressionQuality = it.toInt())) },
            valueRange = MIN_QUALITY..MAX_QUALITY,
        )
    }
}

private const val MIN_QUALITY = 10f
private const val MAX_QUALITY = 100f
