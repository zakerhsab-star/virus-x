package com.virusx.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val profile: ProfileDto? = null,
    val displayName: String = "",
    val phoneNumber: String = "",
    val moodStatus: String = "",
    val dndEnabled: Boolean = false,
    val dndAutoReply: String = "",
    val dndEmergencyKeyword: String = "",
    val versionTapCount: Int = 0,
    val showAdminCodeDialog: Boolean = false,
    val showVaultPinDialog: Boolean = false,
    val statusMessage: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val profile = authRepository.refreshCurrentUserProfile()
            val privateSettings = authRepository.getCurrentUserPrivateSettings()
            _uiState.update {
                it.copy(
                    profile = profile,
                    displayName = profile?.displayName ?: "",
                    phoneNumber = profile?.phoneNumber ?: "",
                    moodStatus = profile?.moodStatus ?: "",
                    dndEnabled = profile?.dndEnabled ?: false,
                    dndAutoReply = profile?.dndAutoReply ?: "أنا في المحاضرة حالياً، سأرد عليك لاحقاً 📚",
                    dndEmergencyKeyword = privateSettings?.dndEmergencyKeyword ?: "طوارئ",
                    isLoading = false
                )
            }
        }
    }

    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value) }
    fun onPhoneNumberChange(value: String) = _uiState.update { it.copy(phoneNumber = value) }
    fun onMoodStatusChange(value: String) = _uiState.update { it.copy(moodStatus = value) }
    fun onDndEnabledChange(enabled: Boolean) = _uiState.update { it.copy(dndEnabled = enabled) }
    fun onDndAutoReplyChange(value: String) = _uiState.update { it.copy(dndAutoReply = value) }
    fun onDndEmergencyKeywordChange(value: String) = _uiState.update { it.copy(dndEmergencyKeyword = value) }

    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            authRepository.uploadUserAvatar(uri).fold(
                onSuccess = {
                    val refreshed = authRepository.refreshCurrentUserProfile()
                    _uiState.update {
                        it.copy(
                            profile = refreshed,
                            isLoading = false,
                            statusMessage = "تم تحديث الصورة الشخصية بنجاح"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = cleanError(err, "فشل رفع الصورة الشخصية")
                        )
                    }
                }
            )
        }
    }

    fun saveProfileInfo() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            authRepository.updateProfileBasicInfo(
                displayName = state.displayName,
                moodStatus = state.moodStatus,
                phoneNumber = state.phoneNumber
            ).fold(
                onSuccess = { updated ->
                    _uiState.update {
                        it.copy(
                            profile = updated,
                            isLoading = false,
                            statusMessage = "تم حفظ بيانات الملف الشخصي ورقم الهاتف بنجاح"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = cleanError(err, "فشل تحديث البيانات")
                        )
                    }
                }
            )
        }
    }

    fun saveSmartDndSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            authRepository.updateSmartDndSettings(
                dndEnabled = state.dndEnabled,
                autoReply = state.dndAutoReply,
                emergencyKeyword = state.dndEmergencyKeyword
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "تم تحديث إعدادات وضع المحاضرة الذكي (Smart DND)"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = err.localizedMessage ?: "تعذر حفظ إعدادات DND"
                        )
                    }
                }
            )
        }
    }

    fun openVaultPinDialog() = _uiState.update { it.copy(showVaultPinDialog = true) }
    fun closeVaultPinDialog() = _uiState.update { it.copy(showVaultPinDialog = false) }

    fun saveVaultPin(newPin: String) {
        viewModelScope.launch {
            authRepository.updateVaultPin(newPin).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            showVaultPinDialog = false,
                            statusMessage = "تم تعيين رمز الخزنة السرية! اكتب هذا الرمز في شريط البحث الرئيسي لفتحها."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(statusMessage = err.localizedMessage ?: "تعذر حفظ الرمز السري")
                    }
                }
            )
        }
    }

    fun onVersionNumberTapped() {
        val nextCount = _uiState.value.versionTapCount + 1
        if (nextCount >= 5) {
            _uiState.update {
                it.copy(
                    versionTapCount = 0,
                    showAdminCodeDialog = true
                )
            }
        } else {
            _uiState.update { it.copy(versionTapCount = nextCount) }
        }
    }

    fun dismissAdminCodeDialog() {
        _uiState.update { it.copy(showAdminCodeDialog = false) }
    }

    fun submitSecretAdminCode(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showAdminCodeDialog = false) }
            authRepository.redeemAdminCode(code).fold(
                onSuccess = { promoted ->
                    val refreshed = authRepository.refreshCurrentUserProfile()
                    _uiState.update {
                        it.copy(
                            profile = refreshed,
                            isLoading = false,
                            statusMessage = if (promoted) {
                                "تم تفعيل صلاحيات المشرف العام (Super Admin) بنجاح!"
                            } else {
                                "كود الإشراف غير صحيح"
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = cleanError(err, "تعذر التحقق من كود الإشراف")
                        )
                    }
                }
            )
        }
    }

    private fun cleanError(err: Throwable, fallback: String): String {
        val raw = err.localizedMessage ?: return fallback
        return raw.substringBefore("URL:").substringBefore("Headers:").trim().ifBlank { fallback }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onSignedOut()
        }
    }
}
