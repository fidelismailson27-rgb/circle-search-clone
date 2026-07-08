package com.circulesearch.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController

/** Route names for [AppNavHost] — destinations are registered by the task that implements each screen. */
object CircleSearchDestinations {
    const val ONBOARDING = "onboarding"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = CircleSearchDestinations.SETTINGS,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        // CircleSearchDestinations.ONBOARDING registered by T059/T061.
        // CircleSearchDestinations.SETTINGS registered by T041/T044.
    }
}
