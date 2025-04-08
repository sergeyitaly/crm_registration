package com.daniel.crmregistration.network

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        return try {
            val authToken = tokenManager.getAuthTokenBlocking().trim()
            
            // Validate token format more strictly
            val formattedToken = when {
                authToken.isBlank() -> {
                    tokenManager.refreshToken() // Force refresh if empty
                    return null
                }
                authToken.startsWith("Bearer ") -> authToken
                else -> "Bearer ${authToken.trim()}"
            }

            // Verify token is not malformed
            if (formattedToken.split(" ").size != 2 || formattedToken.split(" ")[1].isBlank()) {
                tokenManager.refreshToken()
                return null
            }

            response.request.newBuilder()
                .header("Authorization", formattedToken)
                .header("Content-Type", "application/json")
                .build()
        } catch (e: Exception) {
            null
        }
    }
}