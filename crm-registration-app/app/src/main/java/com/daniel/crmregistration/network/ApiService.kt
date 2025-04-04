// ApiService.kt
package com.daniel.crmregistration.network

import com.daniel.crmregistration.models.Contact
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    // Backend endpoints (Fly.io)
    @POST("contacts")
    suspend fun createContact(
        @Body contact: Contact,
        @Header("Authorization") authToken: String
    ): Response<ApiResponse>

    @GET("health")
    suspend fun healthCheck(): Response<HealthCheckResponse>
    
    @GET("crm-entity-link")
    suspend fun getCrmEntityLink(
        @Query("entity_name") entityName: String
    ): Response<CrmEntityLinkResponse>


    @GET("contacts({id})/generate-link")
    suspend fun generateCrmContactLink(
        @Path("id") contactId: String,
        @Query("app_id") appId: String
    ): Response<CrmLinkResponse>
    
    data class CrmLinkResponse(
        val link: String
    )

}