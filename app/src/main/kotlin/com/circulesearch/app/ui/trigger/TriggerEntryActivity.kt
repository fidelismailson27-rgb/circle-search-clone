package com.circulesearch.app.ui.trigger

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.circulesearch.app.domain.repository.EndpointProfileRepository
import com.circulesearch.app.domain.usecase.CheckRequiredPermissionsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The app's one public interface (`contracts/trigger-intent-contract.md`, T009) —
 * exported, no visible UI of its own. Checks the two hard preconditions the core flow
 * needs, then hands off to [VisualSearchFlowController] and finishes immediately, so
 * the caller's own foreground app never visibly loses focus (FR-002).
 */
@AndroidEntryPoint
class TriggerEntryActivity : ComponentActivity() {
    @Inject
    lateinit var checkRequiredPermissionsUseCase: CheckRequiredPermissionsUseCase

    @Inject
    lateinit var endpointProfileRepository: EndpointProfileRepository

    @Inject
    lateinit var flowController: VisualSearchFlowController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val missingPermissions = checkRequiredPermissionsUseCase()
            if (missingPermissions.isNotEmpty()) {
                // FR-013/FR-018: proper onboarding routing lands with US4 (T059-T064);
                // until then, this is a clear, non-silent notice rather than a no-op
                // (constitution V — never fail silently).
                notify("Circle Search needs permissions granted first — open the app to set up.")
                finish()
                return@launch
            }

            if (endpointProfileRepository.getActiveProfile() == null) {
                // FR-017/FR-022: proper Settings routing lands with US2 (T041-T044).
                notify("No AI endpoint configured yet — open the app to add one.")
                finish()
                return@launch
            }

            flowController.startNewSearch()
            finish()
        }
    }

    private fun notify(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
