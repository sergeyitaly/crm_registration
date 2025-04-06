// CrmRepository.kt
package com.daniel.crmregistration.repository


import com.daniel.crmregistration.models.Contact
import com.daniel.crmregistration.network.ApiService
import com.daniel.crmregistration.network.TokenManager
import javax.inject.Inject
import com.daniel.crmregistration.Secrets
import android.util.Patterns

class CrmRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    private val secrets: Secrets
) {
    suspend fun createContact(contact: Contact) = try {
        val token = tokenManager.getAuthToken()
        apiService.createContact(contact, token)
    } catch (e: Exception) {
        throw Exception("Failed to create contact: ${e.message}")
    }

    fun getCrmEntityListLink(entityName: String): String {
        return try {
            // Clean the base URL - remove any trailing quotes or slashes
            val baseUrl = secrets.crmMainUrl
                .trim()  // Remove whitespace
                .removeSurrounding("\"")  // Remove quotes if present
                .removeSuffix("/")  // Remove trailing slash
            
            val url = "$baseUrl/main.aspx?" +
                "appid=${secrets.appId}&" +
                "pagetype=entitylist&" +
                "etn=$entityName"
            
            // Simple URL validation
            if (!url.startsWith("http")) {
                throw IllegalArgumentException("Invalid CRM URL format: Must start with http:// or https://")
            }
            
            url
        } catch (e: Exception) {
            throw Exception("Failed to generate CRM list link: ${e.message}")
        }
    }

    suspend fun generateContactLink(contactId: String): String {
        return try {
            // Construct contact-specific URL
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