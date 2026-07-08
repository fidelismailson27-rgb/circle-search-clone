package com.circulesearch.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.circulesearch.app.domain.model.AiEndpointProfile
import com.circulesearch.app.domain.model.ConnectionTestResult
import com.circulesearch.app.domain.model.SearchPreferences
import com.circulesearch.app.domain.repository.EndpointProfileRepository
import com.circulesearch.app.domain.usecase.ReorderFallbackUseCase
import com.circulesearch.app.domain.usecase.SaveEndpointProfileUseCase
import com.circulesearch.app.domain.usecase.TestEndpointConnectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Settings is a normal in-app screen reached through [com.circulesearch.app.ui.MainActivity]'s
 * NavHost (unlike [com.circulesearch.app.ui.result.ResultViewModel]), so a real
 * `androidx.lifecycle.ViewModel` — scoped to the nav-graph destination — is the
 * correct fit here.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: EndpointProfileRepository,
        private val saveEndpointProfileUseCase: SaveEndpointProfileUseCase,
        private val testEndpointConnectionUseCase: TestEndpointConnectionUseCase,
        private val reorderFallbackUseCase: ReorderFallbackUseCase,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            combine(repository.observeProfiles(), repository.observeSearchPreferences()) { profiles, preferences ->
                SettingsUiState(profiles = profiles, preferences = preferences)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MILLIS), SettingsUiState())

        private val _saveError = MutableStateFlow<String?>(null)
        val saveError: StateFlow<String?> = _saveError

        private val _testResults = MutableStateFlow<Map<String, ConnectionTestResult>>(emptyMap())
        val testResults: StateFlow<Map<String, ConnectionTestResult>> = _testResults

        /**
         * Awaitable by the caller (e.g. a Composable's own coroutine scope) so the UI
         * can navigate away only on confirmed success, rather than racing this
         * ViewModel's own async state update.
         */
        suspend fun trySaveProfile(profile: AiEndpointProfile): Boolean {
            val result = saveEndpointProfileUseCase(profile)
            _saveError.value = result.exceptionOrNull()?.message
            return result.isSuccess
        }

        fun deleteProfile(id: String) {
            viewModelScope.launch { repository.deleteProfile(id) }
        }

        fun setActiveProfile(id: String) {
            viewModelScope.launch { repository.setActiveProfile(id) }
        }

        fun testConnection(profile: AiEndpointProfile) {
            viewModelScope.launch {
                val result = testEndpointConnectionUseCase(profile)
                _testResults.value = _testResults.value + (profile.id to result)
            }
        }

        fun reorderFallback(orderedIds: List<String>) {
            viewModelScope.launch { reorderFallbackUseCase(orderedIds) }
        }

        fun updatePreferences(preferences: SearchPreferences) {
            viewModelScope.launch { repository.updateSearchPreferences(preferences) }
        }

        fun clearSaveError() {
            _saveError.value = null
        }

        fun newDraftProfile(): AiEndpointProfile =
            AiEndpointProfile(
                id = UUID.randomUUID().toString(),
                name = "",
                baseUrl = "",
                apiKey = "",
                modelName = "",
                isActive = uiState.value.profiles.isEmpty(),
                fallbackOrder = null,
                lastConnectionTest = null,
            )

        private companion object {
            const val STATE_SHARING_TIMEOUT_MILLIS = 5000L
        }
    }

data class SettingsUiState(
    val profiles: List<AiEndpointProfile> = emptyList(),
    val preferences: SearchPreferences = SearchPreferences(),
)
