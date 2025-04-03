package com.daniel.crmregistration.network

import android.content.Context
import com.daniel.crmregistration.Secrets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context,
    secrets: Secrets,
    @Named("tokenClient") okHttpClient: OkHttpClient
) {
    private val context: Context = context
    private val secrets: Secrets = secrets
    private val okHttpClient: OkHttpClient = okHttpClient

    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    suspend fun getAuthToken(): String {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) {
            return cachedToken!!
        }

        return withContext(Dispatchers.IO) {
            acquireNewToken().also {
                cachedToken = it
                tokenExpiry = System.currentTimeMillis() + 3600000 // 1 hour expiry
            }
        }
    }

    fun getAuthTokenBlocking(): String = runBlocking { getAuthToken() }

    private suspend fun acquireNewToken(): String {
        return try {
            val tokenResponse = requestNewToken()
            "Bearer ${tokenResponse.accessToken}"
        } catch (e: Exception) {
            throw Exception("Failed to acquire token: ${e.message}")
        }
    }

    private suspend fun requestNewToken(): TokenResponse {
        val requestBody = FormBody.Builder()
            .add("client_id", secrets.clientId)
            .add("client_secret", secrets.clientSecret)
            .add("scope", secrets.resource)
            .add("grant_type", "client_credentials")
            .build()

        val request = Request.Builder()
            .url("https://login.microsoftonline.com/${secrets.tenantId}/oauth2/v2.0/token")
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response body")

        if (!response.isSuccessful) {
            throw Exception("Token request failed: ${response.code} - $responseBody")
        }

        return parseTokenResponse(responseBody)
    }

    private fun parseTokenResponse(json: String): TokenResponse {
        val jsonObject = JSONObject(json)
        return TokenResponse(
            accessToken = jsonObject.getString("access_token"),
            expiresIn = jsonObject.getInt("expires_in"),
            tokenType = jsonObject.getString("token_type")
        )
    }

    private data class TokenResponse(
        val accessToken: String,
        val expiresIn: Int,
        val tokenType: String
    )
}