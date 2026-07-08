package com.circulesearch.app.domain.usecase

import com.circulesearch.app.domain.model.AiEndpointProfile
import com.circulesearch.app.domain.model.ConnectionTestResult
import com.circulesearch.app.domain.repository.EndpointProfileRepository
import com.circulesearch.app.domain.repository.VisualSearchRepository
import javax.inject.Inject

/**
 * On-demand connection test for a single profile (FR-010), recording the outcome
 * back onto the profile so Settings can show it (data-model.md `lastConnectionTest`).
 */
class TestEndpointConnectionUseCase
    @Inject
    constructor(
        private val visualSearchRepository: VisualSearchRepository,
        private val endpointProfileRepository: EndpointProfileRepository,
    ) {
        suspend operator fun invoke(profile: AiEndpointProfile): ConnectionTestResult {
            val result = visualSearchRepository.testConnection(profile)
            endpointProfileRepository.recordConnectionTestResult(profile.id, result)
            return result
        }
    }
