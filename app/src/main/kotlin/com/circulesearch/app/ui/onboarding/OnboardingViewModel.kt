package com.circulesearch.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.circulesearch.app.domain.model.PermissionStatus
import com.circulesearch.app.domain.repository.PermissionStatusRepository
import com.circulesearch.app.domain.usecase.CheckRequiredPermissionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tracks [PermissionStatus] per required permission, advancing through them in order
 * and skipping any already granted (FR-013/FR-017) — re-check the live OS grant state
 * with [refresh] every time the user returns from a system Settings screen.
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val permissionStatusRepository: PermissionStatusRepository,
        private val checkRequiredPermissionsUseCase: CheckRequiredPermissionsUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OnboardingUiState())
        val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                checkRequiredPermissionsUseCase() // refreshes live OS grant state as a side effect
                val statuses = permissionStatusRepository.observePermissionStatuses().first()
                // MediaProjectionCapability has no durable "granted" flag to gate on (it's
                // always reported true — research.md R1) — its onboarding step is instead
                // gated on explainedToUser, so it's still shown exactly once.
                val nextStep = statuses.firstOrNull { !it.granted || !it.explainedToUser }?.permissionType
                _uiState.value = OnboardingUiState(statuses = statuses, currentStep = nextStep, isComplete = nextStep == null)
            }
        }

        fun markExplained(type: PermissionStatus.Type) {
            viewModelScope.launch { permissionStatusRepository.markExplained(type) }
        }
    }

data class OnboardingUiState(
    val statuses: List<PermissionStatus> = emptyList(),
    val currentStep: PermissionStatus.Type? = null,
    val isComplete: Boolean = false,
)
