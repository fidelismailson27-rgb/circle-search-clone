package com.circulesearch.app.domain.usecase

import com.circulesearch.app.domain.model.PermissionStatus
import com.circulesearch.app.domain.repository.PermissionStatusRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Refreshes live OS permission state and returns whichever required permissions are
 * currently missing (FR-013/FR-018) — used both by onboarding and by
 * `StartVisualSearchUseCase`'s at-time-of-use check.
 */
class CheckRequiredPermissionsUseCase
    @Inject
    constructor(
        private val permissionStatusRepository: PermissionStatusRepository,
    ) {
        suspend operator fun invoke(): List<PermissionStatus> {
            PermissionStatus.Type.entries.forEach { permissionStatusRepository.refreshPermissionStatus(it) }
            return permissionStatusRepository.observePermissionStatuses().first().filterNot { it.granted }
        }
    }
