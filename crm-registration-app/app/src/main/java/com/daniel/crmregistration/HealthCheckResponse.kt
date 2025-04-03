package com.daniel.crmregistration.network

data class HealthCheckResponse(
    val status: String,
    val version: String,
    val timestamp: String
)