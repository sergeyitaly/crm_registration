package com.daniel.crmregistration.network

import com.daniel.crmregistration.Secrets
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.daniel.crmregistration.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

@Singleton
class RetrofitClient @Inject constructor(
    private val secrets: Secrets,
    private val tokenManager: TokenManager
) {
    // Configure JSON parser (shared instance)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    // For your backend service (no auth needed)
    val apiService: ApiService by lazy {
        createBackendRetrofit(secrets.getBackendBaseUrl())
            .create(ApiService::class.java)
    }

    // For direct CRM access (with auth)
    val crmApiService: ApiService by lazy {
        createCrmRetrofit(secrets.crmUrl)
            .create(ApiService::class.java)
    }

    private fun createBackendRetrofit(baseUrl: String): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(createBackendClient())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    private fun createCrmRetrofit(baseUrl: String): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(createCrmClient())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    private fun createBackendClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(createLoggingInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun createCrmClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(createLoggingInterceptor())
            .addInterceptor(createCrmAuthInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun createLoggingInterceptor() = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        redactHeader("Authorization")
        redactHeader("Set-Cookie")
    }

    private fun createCrmAuthInterceptor() = Interceptor { chain ->
        val token = runBlocking { tokenManager.getCrmToken() }
        chain.proceed(
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .addHeader("OData-MaxVersion", "4.0")
                .addHeader("OData-Version", "4.0")
                .addHeader("Content-Type", "application/json")
                .build()
        )
    }
}