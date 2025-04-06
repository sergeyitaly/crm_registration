// RegistrationViewModel.kt
package com.daniel.crmregistration.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daniel.crmregistration.models.Contact
import com.daniel.crmregistration.network.ApiResponse
import com.daniel.crmregistration.network.ApiService
import com.daniel.crmregistration.network.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import javax.inject.Inject

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Failure(val exception: Exception) : Result<Nothing>()
}

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _registrationState = MutableStateFlow<Result<Unit>?>(null)
    private fun handleResponse(response: Response<ApiResponse>) {
        when {
            response.isSuccessful -> {
                response.body()?.let { apiResponse ->
                    if (apiResponse.success) {
                        _registrationState.value = Result.Success(Unit)
                    } else {
                        _registrationState.value = Result.Failure(
                            Exception(apiResponse.message ?: "Registration failed")
                        )
                    }
                } ?: run {
                    _registrationState.value = Result.Failure(
                        Exception("Empty response from server")
                    )
                }
            }
            else -> {
                _registrationState.value = Result.Failure(
                    Exception("Server error: ${response.code()} - ${response.errorBody()?.string()}")
                )
            }
        }
    }
}