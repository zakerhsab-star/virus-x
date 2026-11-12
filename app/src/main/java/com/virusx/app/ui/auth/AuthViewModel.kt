package com.virusx.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isSignUpMode: Boolean = false,
    val email: String = "",
    val password: String = "",
    val username: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun toggleMode() {
        _uiState.update {
            it.copy(
                isSignUpMode = !it.isSignUpMode,
                errorMessage = null
            )
        }
    }

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, errorMessage = null) }
    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value, errorMessage = null) }
    fun onPhoneNumberChange(value: String) = _uiState.update { it.copy(phoneNumber = value, errorMessage = null) }

    fun submit() {
        val state = _uiState.value
        if (state.isLoading) return

        if (state.email.isBlank() || state.password.length < 6) {
            _uiState.update { it.copy(errorMessage = "يرجى إدخال بريد إلكتروني صحيح وكلمة مرور من 6 أحرف على الأقل") }
            return
        }

        if (state.isSignUpMode) {
            if (state.displayName.isBlank() || state.username.length < 3) {
                _uiState.update { it.copy(errorMessage = "يرجى إدخال الاسم الكامل واسم مستخدم لا يقل عن 3 أحرف") }
                return
            }
            val cleanPhone = state.phoneNumber.trim()
            if (cleanPhone.length < 10 || !cleanPhone.all { it.isDigit() || it == '+' }) {
                _uiState.update { it.copy(errorMessage = "رقم الهاتف حقل إجباري، يرجى إدخال رقم هاتف صحيح") }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = if (state.isSignUpMode) {
                authRepository.signUp(
                    email = state.email,
                    password = state.password,
                    username = state.username,
                    displayName = state.displayName,
                    phoneNumber = state.phoneNumber
                )
            } else {
                authRepository.signIn(
                    email = state.email,
                    password = state.password
                )
            }

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "حدث خطأ أثناء الاتصال بالخادم"
                        )
                    }
                }
            )
        }
    }
}
