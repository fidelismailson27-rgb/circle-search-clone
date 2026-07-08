package com.circulesearch.app.domain.usecase

import com.circulesearch.app.di.IoDispatcher
import com.circulesearch.app.domain.model.AiEndpointProfile
import com.circulesearch.app.domain.repository.EndpointProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.URI
import javax.inject.Inject

/**
 * Validates and saves a profile (FR-008/FR-009/FR-011). Also enforces
 * research.md R4's corrected private-IP restriction here at the application layer:
 * Android's `network-security-config` cannot itself express "cleartext only for
 * private IPs" (no CIDR/range support — verified against the real schema during
 * T006), so a plain `http://` Base URL is rejected unless its host resolves to a
 * loopback/private/link-local address.
 */
class SaveEndpointProfileUseCase
    @Inject
    constructor(
        private val repository: EndpointProfileRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend operator fun invoke(profile: AiEndpointProfile): Result<Unit> {
            val validationError = validate(profile)
            if (validationError != null) return Result.failure(IllegalArgumentException(validationError))
            repository.saveProfile(profile)
            return Result.success(Unit)
        }

        private suspend fun validate(profile: AiEndpointProfile): String? {
            if (profile.name.isBlank()) return "Name is required."
            if (profile.baseUrl.isBlank()) return "Base URL is required."
            if (profile.modelName.isBlank()) return "Model is required."

            val uri = runCatching { URI(profile.baseUrl) }.getOrNull() ?: return "Base URL is not a valid URL."
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return "Base URL must start with http:// or https://."

            if (scheme == "http") {
                val host = uri.host ?: return "Base URL is missing a host."
                val isPrivate = withContext(ioDispatcher) { isPrivateOrLoopbackHost(host) }
                if (!isPrivate) {
                    return "Plain http:// is only allowed for local network addresses (e.g. 192.168.x.x). Use https:// for public endpoints."
                }
            }
            return null
        }

        private fun isPrivateOrLoopbackHost(host: String): Boolean =
            runCatching {
                val address = InetAddress.getByName(host)
                address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress
            }.getOrDefault(false)
    }
