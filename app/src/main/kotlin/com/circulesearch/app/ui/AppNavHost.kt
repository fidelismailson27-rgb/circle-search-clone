package com.circulesearch.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.circulesearch.app.ui.onboarding.OnboardingScreen
import com.circulesearch.app.ui.settings.FallbackOrderScreen
import com.circulesearch.app.ui.settings.ProfileEditorScreen
import com.circulesearch.app.ui.settings.SettingsScreen
import com.circulesearch.app.ui.settings.SettingsViewModel

/** Route names for [AppNavHost]. */
object CircleSearchDestinations {
    const val ONBOARDING = "onboarding"
    const val SETTINGS = "settings"
    const val PROFILE_EDITOR = "settings/profile/{profileId}"
    const val FALLBACK_ORDER = "settings/fallback-order"

    const val NEW_PROFILE_SENTINEL = "new"

    fun profileEditorRoute(profileId: String?) = "settings/profile/${profileId ?: NEW_PROFILE_SENTINEL}"
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = CircleSearchDestinations.SETTINGS,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(CircleSearchDestinations.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(CircleSearchDestinations.SETTINGS) {
                        popUpTo(CircleSearchDestinations.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(CircleSearchDestinations.SETTINGS) {
            SettingsScreen(
                onEditProfile = { profile -> navController.navigate(CircleSearchDestinations.profileEditorRoute(profile?.id)) },
                onManageFallbackOrder = { navController.navigate(CircleSearchDestinations.FALLBACK_ORDER) },
            )
        }
        composable(CircleSearchDestinations.PROFILE_EDITOR) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.takeUnless { it == CircleSearchDestinations.NEW_PROFILE_SENTINEL }
            ProfileEditorScreenRoute(profileId = profileId, onDone = { navController.popBackStack() })
        }
        composable(CircleSearchDestinations.FALLBACK_ORDER) {
            FallbackOrderScreen(onDone = { navController.popBackStack() })
        }
    }
}

/**
 * Resolves [profileId] against the live profile list before handing off to
 * [ProfileEditorScreen] — Compose Navigation only passes primitive route arguments,
 * not the [com.circulesearch.app.domain.model.AiEndpointProfile] object itself.
 */
@Composable
private fun ProfileEditorScreenRoute(
    profileId: String?,
    onDone: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val existingProfile = profileId?.let { id -> state.profiles.find { it.id == id } }
    ProfileEditorScreen(existingProfile = existingProfile, onSaved = onDone, onCancel = onDone, viewModel = viewModel)
}
