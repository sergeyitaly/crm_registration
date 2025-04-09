package com.daniel.crmregistration.network

import com.daniel.crmregistration.models.Contact
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
@Headers(
    "Content-Type: application/json",
    "OData-MaxVersion: 4.0",
    "OData-Version: 4.0",
    "Prefer: return=representation"
)
    @POST("contacts")
    suspend fun createContact(
        @Body contact: Contact,
        @Header("Authorization") authHeader: String
    ): Response<Contact>

@Headers(
    "Content-Type: application/json",
    "OData-MaxVersion: 4.0",
    "OData-Version: 4.0",
    "Prefer: return=representation"
)
    @PATCH("contacts({contactId})")
    suspend fun updateContact(
        @Path("contactId") contactId: String,
        @Body contact: Contact,
        @Header("Authorization") authHeader: String
    ): Response<Contact> 

    @GET("contacts({contactId})")
    suspend fun getContact(
        @Path("contactId") contactId: String,
        @Header("Authorization") authHeader: String
    ): Response<Contact>

    @GET("health")
    suspend fun healthCheck(): Response<HealthCheckResponse>

    @GET("crm-contacts-link")
    suspend fun getCrmEntityListLink(
        @Query("entity_name") entityName: String
    ): Response<CrmEntityLinkResponse>
}

// Move these data classes outside the interface
data class HealthCheckResponse(
    val status: String,
    val version: String,
    val timestamp: String
)

data class CrmEntityLinkResponse(
    val crm_entity_link: String,
    val expires_in: Int
)