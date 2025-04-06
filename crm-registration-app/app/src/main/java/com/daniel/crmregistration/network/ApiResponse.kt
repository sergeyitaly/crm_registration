//ApiResponse.kt

package com.daniel.crmregistration.network

data class ApiResponse(
    val success: Boolean,
    val message: String? = null,
    val data: Any? = null
)