package com.series76.assistant.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.series76.assistant.data.auth.AuthRepository
import com.series76.assistant.data.remote.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val SESSION_EXPIRED_MESSAGE = "Your session expired. Please sign in again."

data class LoginUiState(val loading: Boolean = false, val error: String? = null)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // This ViewModel outlives a sign-in, so it watches for an expiry instead of checking once.
        viewModelScope.launch {
            auth.sessionExpired.collect { expired ->
                if (expired) _uiState.value = LoginUiState(error = SESSION_EXPIRED_MESSAGE)
            }
        }
    }

    fun login(email: String, password: String) {
        _uiState.value = LoginUiState(loading = true)
        viewModelScope.launch {
            // Success needs no state of its own: the saved token moves the app past this screen.
            _uiState.value =
                when (val result = auth.login(email, password)) {
                    is ApiResult.Success -> LoginUiState()
                    is ApiResult.Failure -> LoginUiState(error = result.message)
                }
        }
    }
}
