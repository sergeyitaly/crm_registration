package com.daniel.crmregistration.network

import com.daniel.crmregistration.Secrets
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.daniel.crmregistration.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import android.util.Log
import java.io.IOException


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
        Log.d("RetrofitClient", "Initializing backend API with URL: ${secrets.getBackendBaseUrl()}")
        createBackendRetrofit(secrets.getBackendBaseUrl().ensureTrailingSlash())
            .create(ApiService::class.java)
    }

    // For direct CRM access (with auth)
    val crmApiService: ApiService by lazy {
        Log.d("RetrofitClient", "Initializing CRM API with URL: ${secrets.crmUrl}")
        createCrmRetrofit(secrets.crmUrl.ensureTrailingSlash())
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
            .connectTimeout(30, TimeUnit.SECONDS) // Increased timeout for mobile
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(ConnectivityInterceptor()) // Add connectivity check
            .build()
    }

    private fun createCrmClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(createLoggingInterceptor())
            .addInterceptor(createCrmAuthInterceptor())
            .connectTimeout(45, TimeUnit.SECONDS) // Longer timeout for CRM
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(ConnectivityInterceptor()) // Add connectivity check
            .build()
    }

    private fun createLoggingInterceptor() = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.BASIC // Keep basic logs in production
        }
        redactHeader("Authorization")
        redactHeader("Set-Cookie")
    }

    private fun createCrmAuthInterceptor() = Interceptor { chain ->
        Log.d("AuthInterceptor", "Attempting to get CRM token")
        val token = runBlocking { 
            tokenManager.getCrmToken().also {
                Log.d("AuthInterceptor", "Token received (first 10 chars): ${it.take(10)}...")
            }
        }
        
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .addHeader("OData-MaxVersion", "4.0")
            .addHeader("OData-Version", "4.0")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()
            
        Log.d("AuthInterceptor", "Making request to: ${request.url}")
        chain.proceed(request)
    }

    private fun String.ensureTrailingSlash(): String {
        return if (endsWith("/")) this else "$this/"
    }
}

// Add this class to check network connectivity before making requests
class ConnectivityInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        // You should implement proper network availability check here
        // For example using ConnectivityManager
        if (!isNetworkAvailable()) {
            throw NoNetworkException()
        }
        return chain.proceed(chain.request())
    }
    
    private fun isNetworkAvailable(): Boolean {
        // Implement actual network check
        return true
    }
}

class NoNetworkException : IOException("No network connection available")