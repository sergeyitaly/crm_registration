package com.daniel.crmregistration.network

import android.content.Context
import com.daniel.crmregistration.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton;

@Singleton
class AuthService @Inject constructor(
    private val secrets: Secrets,
    private val okHttpClient: OkHttpClient
) {
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    suspend fun getAccessToken(): String {
        // Return cached token if valid (with 5 minute buffer)
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry - 300000) {
            return cachedToken!!
        }

        return try {
            val token = acquireNewToken()
            cachedToken = token
            tokenExpiry = System.currentTimeMillis() + 3600000 // 1 hour expiry
            token
        } catch (e: Exception) {
            cachedToken = null // Clear cache on failure
            throw e
        }
    }

    private suspend fun acquireNewToken(): String = withContext(Dispatchers.IO) {
        val requestBody = FormBody.Builder()
            .add("client_id", secrets.clientId)
            .add("client_secret", secrets.clientSecret)
            .add("scope", "${secrets.resource}/.default")
            .add("grant_type", "client_credentials")
            .build()

        val request = Request.Builder()
            .url("https://login.microsoftonline.com/${secrets.tenantId}/oauth2/v2.0/token")
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Auth failed: ${response.code} ${response.message}")
        }

        response.body?.string()?.let { json ->
            "Bearer ${json.substringAfter("\"access_token\":\"").substringBefore("\"")}"
        } ?: throw Exception("Empty auth response")
    }
}