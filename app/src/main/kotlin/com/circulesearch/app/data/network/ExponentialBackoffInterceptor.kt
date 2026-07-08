package com.circulesearch.app.data.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/**
 * Retries transient failures (429/5xx responses, or a thrown [IOException]) with
 * exponential backoff (constitution: "timeouts agressivos... com retry exponencial e
 * cancelamento"). Never retries a plain 4xx client error other than 429 — that is a
 * request-shape problem retrying cannot fix.
 */
class ExponentialBackoffInterceptor
    @Inject
    constructor() : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            var lastException: IOException? = null

            for (attempt in 0..MAX_RETRIES) {
                if (attempt > 0) {
                    Thread.sleep(INITIAL_BACKOFF_MILLIS * (1L shl (attempt - 1)))
                }
                try {
                    val response = chain.proceed(request)
                    val isRetryable = response.code in RETRYABLE_STATUS_CODES
                    if (!isRetryable || attempt == MAX_RETRIES) return response
                    response.close()
                } catch (e: IOException) {
                    lastException = e
                    if (attempt == MAX_RETRIES) throw e
                }
            }
            throw lastException ?: IOException("Request failed after $MAX_RETRIES retries")
        }

        private companion object {
            const val MAX_RETRIES = 3
            const val INITIAL_BACKOFF_MILLIS = 500L
            val RETRYABLE_STATUS_CODES = setOf(429, 500, 502, 503, 504)
        }
    }
