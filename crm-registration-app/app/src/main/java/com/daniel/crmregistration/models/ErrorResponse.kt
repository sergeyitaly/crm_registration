// app/src/main/java/com/daniel/crmregistration/models/ErrorResponse.kt
package com.daniel.crmregistration.models

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: ErrorDetail? = null
)

@Serializable
data class ErrorDetail(
    val code: String? = null,
    val message: String? = null
)