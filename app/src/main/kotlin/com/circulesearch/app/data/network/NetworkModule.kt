package com.circulesearch.app.data.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Single OkHttp client + single Retrofit instance for every BYOK profile
 * (research.md R4, constitution IX) — no per-provider client instances. The
 * `baseUrl` below is a required-but-unused Retrofit placeholder: every real call
 * supplies its full target URL via `@Url` (see [OpenAiCompatibleApi]).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val PLACEHOLDER_BASE_URL = "https://byok-base-url.invalid/"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        profileAuthInterceptor: ProfileAuthInterceptor,
        exponentialBackoffInterceptor: ExponentialBackoffInterceptor,
    ): OkHttpClient {
        // BASIC only (method/URL/response code) — never HEADERS or BODY, since headers
        // include the user's BYOK API key and the body may include image/base64 payloads.
        val loggingInterceptor = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

        return OkHttpClient.Builder()
            .addInterceptor(profileAuthInterceptor)
            .addInterceptor(exponentialBackoffInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()

    @Provides
    @Singleton
    fun provideOpenAiCompatibleApi(retrofit: Retrofit): OpenAiCompatibleApi = retrofit.create(OpenAiCompatibleApi::class.java)

    // Read timeout is generous relative to a typical REST API: LLM completions,
    // especially against self-hosted/local models (research.md), can legitimately
    // take much longer than a normal web request — "aggressive" here means bounded
    // and retried, not short.
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 60L
    private const val WRITE_TIMEOUT_SECONDS = 30L
}
