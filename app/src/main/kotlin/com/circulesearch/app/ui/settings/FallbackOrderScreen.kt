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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.circulesearch.app.domain.model.AiEndpointProfile

/**
 * Lets the user order their non-active profiles into a fallback sequence (FR-013).
 * Up/down controls rather than drag gestures — simpler and fully accessible, and
 * this list is expected to be short (a handful of profiles at most).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FallbackOrderScreen(
    onDone: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val nonActiveProfiles = state.profiles.filterNot { it.isActive }.sortedBy { it.fallbackOrder ?: Int.MAX_VALUE }

    var orderedIds by remember(nonActiveProfiles) { mutableStateOf(nonActiveProfiles.map { it.id }) }
    val orderedProfiles = orderedIds.mapNotNull { id -> nonActiveProfiles.find { it.id == id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fallback Order") },
                actions = {
                    IconButton(onClick = {
                        viewModel.reorderFallback(orderedIds)
                        onDone()
                    }) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("The active profile is tried first. If it fails, these profiles are tried in order.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(orderedProfiles, key = { it.id }) { profile ->
                    val index = orderedIds.indexOf(profile.id)
                    FallbackRow(
                        profile = profile,
                        position = index + 1,
                        canMoveUp = index > 0,
                        canMoveDown = index < orderedIds.lastIndex,
                        onMoveUp = { orderedIds = orderedIds.moved(index, index - 1) },
                        onMoveDown = { orderedIds = orderedIds.moved(index, index + 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FallbackRow(
    profile: AiEndpointProfile,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$position.", modifier = Modifier.padding(end = 8.dp))
            Text(profile.name.ifBlank { "(unnamed profile)" }, modifier = Modifier.weight(1f))
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
            }
        }
    }
}

private fun List<String>.moved(
    from: Int,
    to: Int,
): List<String> {
    if (to !in indices) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    return mutable
}
