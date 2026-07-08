package com.circulesearch.app.domain.model

import java.time.Instant

/**
 * A single named BYOK connection (constitution IX). The user maintains a collection
 * of these; exactly one has [isActive] = true, and the rest may carry a
 * [fallbackOrder] used when the active profile's request fails (research.md R2).
 */
data class AiEndpointProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val isActive: Boolean,
    val fallbackOrder: Int?,
    val lastConnectionTest: ConnectionTestResult?,
)

data class ConnectionTestResult(
    val timestamp: Instant,
    val success: Boolean,
    val message: String,
)
