package com.circulesearch.app.domain.usecase

import com.circulesearch.app.domain.repository.EndpointProfileRepository
import javax.inject.Inject

/** Persists the user-defined fallback sequence among non-active profiles (FR-013). */
class ReorderFallbackUseCase
    @Inject
    constructor(
        private val repository: EndpointProfileRepository,
    ) {
        suspend operator fun invoke(orderedProfileIds: List<String>) {
            repository.setFallbackOrder(orderedProfileIds)
        }
    }
