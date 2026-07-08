package com.circulesearch.app.domain.repository

import com.circulesearch.app.domain.model.PermissionStatus
import kotlinx.coroutines.flow.Flow

/** Tracks onboarding explanation + live OS grant status per permission (FR-013/FR-017/FR-018). */
interface PermissionStatusRepository {
    fun observePermissionStatuses(): Flow<List<PermissionStatus>>

    /** Re-reads the live OS grant state for [type] (e.g. after returning from a system settings screen). */
    suspend fun refreshPermissionStatus(type: PermissionStatus.Type)

    suspend fun markExplained(type: PermissionStatus.Type)
}
