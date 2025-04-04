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
    @ApplicationContext private val context: Context,
    private val secrets: Secrets,
    @Named("tokenClient") private val okHttpClient: OkHttpClient
) {
    private var cachedAuthToken: String? = null
    private var authTokenExpiry: Long = 0

    private var cachedCrmToken: String? = null
    private var crmTokenExpiry: Long = 0

    suspend fun getAuthToken(): String {
        return getToken(::acquireNewAuthToken, ::cachedAuthToken, ::authTokenExpiry) {
            cachedAuthToken = it
            authTokenExpiry = System.currentTimeMillis() + 3600000
        }
    }

    fun getAuthTokenBlocking(): String = runBlocking {
        getAuthToken()
    }

    suspend fun getCrmToken(): String {
        return getToken(::acquireNewCrmToken, ::cachedCrmToken, ::crmTokenExpiry) {
            cachedCrmToken = it
            crmTokenExpiry = System.currentTimeMillis() + 3600000
        }
    }

    private suspend fun getToken(
        acquireToken: suspend () -> String,
        cachedToken: () -> String?,
        expiryTime: () -> Long,
        updateCache: (String) -> Unit
    ): String {
        if (cachedToken() != null && System.currentTimeMillis() < expiryTime()) {
            return cachedToken()!!
        }
        return withContext(Dispatchers.IO) {
            acquireToken().also(updateCache)
        }
    }

    private suspend fun acquireNewAuthToken(): String {
        return "Bearer " + requestToken().accessToken
    }

    private suspend fun acquireNewCrmToken(): String {
        return "Bearer " + requestToken().accessToken
    }

    private suspend fun requestToken(): TokenResponse {
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
