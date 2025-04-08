package com.daniel.crmregistration.repository

import com.daniel.crmregistration.models.Contact
import com.daniel.crmregistration.network.ApiService
import com.daniel.crmregistration.network.TokenManager
import com.daniel.crmregistration.network.ApiResponse
import com.daniel.crmregistration.Secrets
import javax.inject.Inject
import retrofit2.Response

class CrmRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    private val secrets: Secrets
) {
    suspend fun upsertContact(contact: Contact): Response<ApiResponse> = try {
        val token = tokenManager.getAuthToken() ?: throw Exception("Authentication token not available")
        apiService.upsertContact(contact, token)
    } catch (e: Exception) {
        throw Exception("Failed to upsert contact: ${e.message}")
    }

    fun getCrmEntityListLink(entityName: String): String {
        return try {
            // Clean the base URL
            val baseUrl = secrets.crmMainUrl
                .trim()
                .removeSurrounding("\"")
                .removeSuffix("/")
            
            val url = "$baseUrl/main.aspx?" +
                "appid=${secrets.appId}&" +
                "pagetype=entitylist&" +
                "etn=$entityName"
            
            if (!url.startsWith("http")) {
                throw IllegalArgumentException("Invalid CRM URL format")
            }
            
            url
        } catch (e: Exception) {
            throw Exception("Failed to generate CRM list link: ${e.message}")
        }
    }

    suspend fun generateContactLink(contactId: String): String {
        return try {
            "${secrets.crmMainUrl}main.aspx?" +
                "appid=${secrets.appId}&" +
                "pagetype=entityrecord&" +
                "etn=contact&" +
                "id=$contactId"
        } catch (e: Exception) {
            throw Exception("Failed to generate contact link: ${e.message}")
        }
    }
}