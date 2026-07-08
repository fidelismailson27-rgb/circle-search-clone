package com.circulesearch.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.circulesearch.app.domain.usecase.CheckRequiredPermissionsUseCase
import com.circulesearch.app.ui.theme.CircleSearchTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The app's own launcher-visible entry point — distinct from [com.circulesearch.app.ui.trigger.TriggerEntryActivity],
 * which is the hidden, externally-invoked trigger. Routes to onboarding when any
 * required permission is missing, otherwise straight to Settings (T064).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var checkRequiredPermissionsUseCase: CheckRequiredPermissionsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CircleSearchTheme {
                Surface {
                    MainContent(checkRequiredPermissionsUseCase)
                }
            }
        }
    }
}

@Composable
private fun MainContent(checkRequiredPermissionsUseCase: CheckRequiredPermissionsUseCase) {
    val startDestination by
        produceState<String?>(initialValue = null) {
            value =
                if (checkRequiredPermissionsUseCase().isNotEmpty()) {
                    CircleSearchDestinations.ONBOARDING
                } else {
                    CircleSearchDestinations.SETTINGS
                }
        }
    startDestination?.let { destination -> AppNavHost(startDestination = destination) }
}
