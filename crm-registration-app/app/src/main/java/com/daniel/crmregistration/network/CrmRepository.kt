// CrmRepository.kt
package com.daniel.crmregistration.repository

import com.daniel.crmregistration.models.Contact
import com.daniel.crmregistration.network.ApiService
import com.daniel.crmregistration.network.TokenManager
import javax.inject.Inject

class CrmRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun createContact(contact: Contact) = try {
        val token = tokenManager.getAuthToken()
        apiService.createContact(contact, token)
    } catch (e: Exception) {
        throw Exception("Failed to create contact: ${e.message}")
    }

    suspend fun getCrmEntityLink(entityName: String) = try {
        val token = tokenManager.getAuthToken()
        apiService.getCrmEntityLink(entityName)
    } catch (e: Exception) {
        throw Exception("Failed to get CRM link: ${e.message}")
    }
}