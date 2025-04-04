// Secrets.kt
package com.daniel.crmregistration

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.*
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Secrets @Inject constructor(
    @ApplicationContext val context: Context
) {
    private val properties: Properties by lazy {
        Properties().apply {
            try {
                // Read the entire content first
                val content = context.assets.open("secrets.properties").use { input ->
                    input.bufferedReader().use { it.readText() }
                }
                
                Log.d("SECRETS_DEBUG", "Raw content:\n$content")
                
                // Load properties from the string content
                load(StringReader(content))
                
                // Debug log all loaded properties
                Log.d("SECRETS_DEBUG", "Loaded properties:")
                forEach { (k, v) -> Log.d("SECRETS_DEBUG", "$k = $v") }
                
            } catch (e: FileNotFoundException) {
                Log.e("SECRETS", "secrets.properties not found in assets", e)
                throw SecretsConfigurationException("secrets.properties not found in assets", e)
            } catch (e: IOException) {
                Log.e("SECRETS", "Error reading secrets.properties", e)
                throw SecretsConfigurationException("Error reading secrets.properties", e)
            } catch (e: Exception) {
                Log.e("SECRETS", "Unexpected error loading secrets", e)
                throw SecretsConfigurationException("Unexpected error loading secrets", e)
            }
        }
    }

    private fun getRequiredProperty(key: String): String {
        return properties.getProperty(key)?.takeIf { it.isNotBlank() }
            ?: throw MissingPropertyException(key).also {
                Log.e("MISSING_PROPERTY", """
                    Missing property: $key
                    Available properties: ${properties.keys.joinToString()}
                """.trimIndent())
            }
    }

    val crmUrl: String get() = getRequiredProperty("CRM_URL")
    val tenantId: String get() = getRequiredProperty("TENANT_ID")
    val clientId: String get() = getRequiredProperty("CLIENT_ID")
    val clientSecret: String get() = getRequiredProperty("CLIENT_SECRET")
    val resource: String get() = getRequiredProperty("RESOURCE")
    val appId: String get() = getRequiredProperty("APP_ID")
    val prodBaseUrl: String get() = getRequiredProperty("PROD_BASE_URL")
    val localBaseUrl: String get() = getRequiredProperty("CRM_BASE_URL")
    fun getBackendBaseUrl(): String {
        return if (BuildConfig.DEBUG) localBaseUrl else prodBaseUrl
    }
}

// Custom exception classes
class SecretsConfigurationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class MissingPropertyException(propertyName: String) :
    RuntimeException("Required property '$propertyName' is missing or empty")