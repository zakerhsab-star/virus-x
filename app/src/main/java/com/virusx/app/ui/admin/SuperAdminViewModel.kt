package com.virusx.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.remote.dto.MessageDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.remote.dto.StoryAskQuestionDto
import com.virusx.app.data.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SuperAdminUiState(
    val selectedTab: Int = 0,
    val users: List<ProfileDto> = emptyList(),
    val deletedMessages: List<MessageDto> = emptyList(),
    val anonymousQuestions: List<StoryAskQuestionDto> = emptyList(),
    val revealedAskersMap: Map<String, ProfileDto> = emptyMap(),
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class SuperAdminViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SuperAdminUiState())
    val uiState: StateFlow<SuperAdminUiState> = _uiState.asStateFlow()

    init {
        refreshDashboard()
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val users = adminRepository.fetchAllUsers().getOrDefault(emptyList())
            val deleted = adminRepository.fetchDeletedMessages().getOrDefault(emptyList())
            val questions = adminRepository.fetchAllAnonymousQuestions().getOrDefault(emptyList())

            _uiState.update {
                it.copy(
                    users = users,
                    deletedMessages = deleted,
                    anonymousQuestions = questions,
                    isLoading = false
                )
            }
        }
    }

    fun toggleBanUser(user: ProfileDto, reason: String = "مخالفة سياسات الاستخدام") {
        viewModelScope.launch {
            val nextBanState = !user.isBanned
            adminRepository.setUserBan(user.id, nextBanState, if (nextBanState) reason else null).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(statusMessage = if (nextBanState) "تم حظر ${user.displayName}" else "تم إلغاء حظر ${user.displayName}")
                    }
                    refreshDashboard()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun muteUser(user: ProfileDto, hours: Long) {
        viewModelScope.launch {
            adminRepository.muteUserForHours(user.id, hours).fold(
                onSuccess = {
                    val msg = if (hours > 0) "تم كتم ${user.displayName} لمدة $hours ساعة" else "تم فك الكتم عن ${user.displayName}"
                    _uiState.update { it.copy(statusMessage = msg) }
                    refreshDashboard()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun revealQuestionAsker(question: StoryAskQuestionDto) {
        viewModelScope.launch {
            adminRepository.revealAnonymousAsker(question.id).fold(
                onSuccess = { askerUserId ->
                    val foundProfile = _uiState.value.users.firstOrNull { it.id == askerUserId }
                    if (foundProfile != null) {
                        val updated = _uiState.value.revealedAskersMap.toMutableMap()
                        updated[question.id] = foundProfile
                        _uiState.update {
                            it.copy(
                                revealedAskersMap = updated,
                                statusMessage = "تم كشف هوية السائل: ${foundProfile.displayName} (@${foundProfile.username})"
                            )
                        }
                    } else {
                        _uiState.update { it.copy(statusMessage = "معرف السائل: $askerUserId") }
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun sendBroadcast(title: String, body: String) {
        if (title.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            adminRepository.sendGlobalBroadcast(title, body).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم إرسال الإشعار العام لجميع المستخدمين بنجاح") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun lockAdminCodePermanently() {
        viewModelScope.launch {
            adminRepository.lockAdminPromotionCode().fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم قفل الترقية الذاتية بالكود السري نهائياً") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }
}
