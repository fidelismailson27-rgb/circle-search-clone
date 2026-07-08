package com.circulesearch.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.circulesearch.app.domain.model.AiEndpointProfile
import kotlinx.coroutines.launch

/**
 * FR-008 field validation (delegated to `SaveEndpointProfileUseCase`) and FR-012
 * credential handling: [existingProfile]'s stored `apiKey` is **never** re-hydrated
 * into the input field — it starts blank, meaning "keep the current key," and only a
 * non-blank entry overwrites it. The field itself is also masked
 * ([PasswordVisualTransformation]) regardless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    existingProfile: AiEndpointProfile?,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val saveError by viewModel.saveError.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existingProfile?.name.orEmpty()) }
    var baseUrl by remember { mutableStateOf(existingProfile?.baseUrl.orEmpty()) }
    var modelName by remember { mutableStateOf(existingProfile?.modelName.orEmpty()) }
    var apiKeyInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (existingProfile == null) "New Profile" else "Edit Profile") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text(if (existingProfile == null) "API Key" else "API Key (leave blank to keep current)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = {
                val profile =
                    (existingProfile ?: viewModel.newDraftProfile()).copy(
                        name = name,
                        baseUrl = baseUrl,
                        modelName = modelName,
                        apiKey = apiKeyInput.ifBlank { existingProfile?.apiKey.orEmpty() },
                    )
                coroutineScope.launch {
                    if (viewModel.trySaveProfile(profile)) onSaved()
                }
            }) {
                Text("Save")
            }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
