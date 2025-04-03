package com.daniel.crmregistration.network

import com.daniel.crmregistration.Secrets
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrofitClient @Inject constructor(
    private val secrets: Secrets,
    private val tokenManager: TokenManager,
    private val loggingInterceptor: HttpLoggingInterceptor
) {
    val apiService: ApiService by lazy { retrofit.create(ApiService::class.java) }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(secrets.crmUrl) // Comes from your Secrets class
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val authInterceptor = Interceptor { chain ->
        val token = runBlocking { tokenManager.getAuthToken() }
        chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .addHeader("OData-MaxVersion", "4.0")
            .addHeader("OData-Version", "4.0")
            .addHeader("Content-Type", "application/json")
            .build()
            .let { chain.proceed(it) }
    }
}

// For development/testing (without DI)
object RetrofitClientBasic {
    // Choose the appropriate base URL for your environment:
    
    // 1. For local development (Android emulator connecting to localhost)
    //private const val LOCAL_DEV_URL = "http://10.0.2.2:8000/" 
    
    // 2. For local network (physical device connecting to your dev machine)
    // private const val LOCAL_NETWORK_URL = "http://YOUR_LOCAL_IP:8000/"
    
    // 3. For production/staging server
     private const val PRODUCTION_URL = "https://crm-registration.fly.dev/"
    
    // Currently using local dev URL (change as needed)
    //private const val BASE_URL = LOCAL_DEV_URL
    private const val BASE_URL = PRODUCTION_URL

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}