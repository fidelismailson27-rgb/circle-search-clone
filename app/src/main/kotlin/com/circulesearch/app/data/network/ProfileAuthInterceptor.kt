package com.circulesearch.app.data.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Sets `Authorization: Bearer <key>` from the request's [ProfileAuthTag], rather than
 * a single static header — a conversation can target different
 * [com.circulesearch.app.domain.model.AiEndpointProfile]s across its turns (fallback,
 * research.md R2), so the key must travel with each individual request.
 */
class ProfileAuthInterceptor
    @Inject
    constructor() : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val auth = original.tag(ProfileAuthTag::class.java)

            if (auth == null || auth.apiKey.isBlank()) {
                // Some local/self-hosted endpoints require no key at all — forward as-is
                // rather than sending an empty/malformed Authorization header.
                return chain.proceed(original)
            }

            val authenticated =
                original.newBuilder()
                    .header("Authorization", "Bearer ${auth.apiKey}")
                    .build()
            return chain.proceed(authenticated)
        }
    }
