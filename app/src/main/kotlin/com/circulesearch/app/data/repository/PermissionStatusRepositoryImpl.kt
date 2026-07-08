package com.circulesearch.app.data.repository

import android.content.Context
import android.provider.Settings
import com.circulesearch.app.domain.model.PermissionStatus
import com.circulesearch.app.domain.repository.PermissionStatusRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionStatusRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PermissionStatusRepository {
        private val statuses =
            MutableStateFlow(
                PermissionStatus.Type.entries.map { type ->
                    PermissionStatus(
                        permissionType = type,
                        explainedToUser = false,
                        granted = isCurrentlyGranted(type),
                        lastCheckedAt = Instant.now(),
                    )
                },
            )

        override fun observePermissionStatuses(): Flow<List<PermissionStatus>> = statuses.asStateFlow()

        override suspend fun refreshPermissionStatus(type: PermissionStatus.Type) {
            statuses.update { current ->
                current.map {
                    if (it.permissionType == type) {
                        it.copy(granted = isCurrentlyGranted(type), lastCheckedAt = Instant.now())
                    } else {
                        it
                    }
                }
            }
        }

        override suspend fun markExplained(type: PermissionStatus.Type) {
            statuses.update { current ->
                current.map { if (it.permissionType == type) it.copy(explainedToUser = true) else it }
            }
        }

        private fun isCurrentlyGranted(type: PermissionStatus.Type): Boolean =
            when (type) {
                PermissionStatus.Type.Overlay -> Settings.canDrawOverlays(context)
                PermissionStatus.Type.Accessibility -> isTriggerAccessibilityServiceEnabled()
                // MediaProjection consent has no durable OS-level "granted" flag to query —
                // it is re-requested on every single capture (research.md R1). Treated as
                // always "available to prompt for" rather than a persistent grant.
                PermissionStatus.Type.MediaProjectionCapability -> true
            }

        private fun isTriggerAccessibilityServiceEnabled(): Boolean {
            val expectedComponent = "${context.packageName}/$TRIGGER_ACCESSIBILITY_SERVICE_CLASS_NAME"
            val enabledServices =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ) ?: return false
            return enabledServices.splitToSequence(':').any { it.equals(expectedComponent, ignoreCase = true) }
        }

        private companion object {
            // Matches data/accessibility/TriggerAccessibilityService.kt, implemented by T062 —
            // referenced here by fully-qualified name (not by class literal) so this
            // foundational repository does not need to depend on that not-yet-existing class.
            const val TRIGGER_ACCESSIBILITY_SERVICE_CLASS_NAME =
                "com.circulesearch.app.data.accessibility.TriggerAccessibilityService"
        }
    }
