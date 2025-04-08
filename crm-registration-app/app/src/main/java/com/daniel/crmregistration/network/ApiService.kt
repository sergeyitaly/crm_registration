package com.daniel.crmregistration.network

import com.daniel.crmregistration.models.Contact
import retrofit2.Response
import retrofit2.http.*
import com.google.gson.annotations.SerializedName

interface ApiService {
    @POST("contacts")
    @Headers("Content-Type: application/json")
    suspend fun upsertContact(
        @Body contact: Contact,
        @Header("Authorization") authHeader: String
    ): Response<ApiResponse>


    
    @GET("health")
    suspend fun healthCheck(): Response<HealthCheckResponse>
    
    data class HealthCheckResponse(
        val status: String
    )

    @GET("crm-contacts-link")
    suspend fun getCrmEntityListLink(
        @Query("entity_name") entityName: String
    ): Response<CrmEntityLinkResponse>

    // Properly annotated data class
    data class CrmEntityLinkResponse(
        @SerializedName("crm_entity_link")
        val crmEntityLink: String
    )
}
