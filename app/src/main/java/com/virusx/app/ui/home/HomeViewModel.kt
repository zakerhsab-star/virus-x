package com.virusx.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.BuildConfig
import com.virusx.app.data.local.entity.ChatEntity
import com.virusx.app.data.remote.dto.AppConfigDto
import com.virusx.app.data.remote.dto.BroadcastDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.repository.AuthRepository
import com.virusx.app.data.repository.ChatRepository
import com.virusx.app.util.OtaUpdateManager
import com.virusx.app.util.PinHashUtil
import com.virusx.app.util.VaultPreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val searchQuery: String = "",
    val searchedUsers: List<ProfileDto> = emptyList(),
    val isSearchingUsers: Boolean = false,
    val currentUserProfile: ProfileDto? = null,
    val latestBroadcast: BroadcastDto? = null,
    val availableUpdate: AppConfigDto? = null,
    val maintenanceConfig: AppConfigDto? = null,
    val isRefreshing: Boolean = false,
    val openStealthVaultEvent: Boolean = false,
    val navigateToChatId: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val vaultPrefs: VaultPreferencesManager,
    private val otaUpdateManager: OtaUpdateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val visibleChats: StateFlow<List<ChatEntity>> = chatRepository.visibleChatsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultChats: StateFlow<List<ChatEntity>> = chatRepository.vaultChatsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val otaDownloadProgress: StateFlow<Float?> = otaUpdateManager.downloadProgress

    private var searchJob: Job? = null

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val profile = authRepository.refreshCurrentUserProfile()
            chatRepository.syncUserChats()
            val broadcast = chatRepository.fetchLatestBroadcast()
            val config = chatRepository.fetchAppConfig()

            val updateAvailable = config?.takeIf {
                it.latestVersionCode > BuildConfig.VERSION_CODE && it.apkDownloadUrl.isNotBlank()
            }
            val inMaintenance = config?.takeIf {
                it.maintenanceMode && profile?.role != "super_admin"
            }

            _uiState.update {
                it.copy(
                    currentUserProfile = profile,
                    latestBroadcast = broadcast,
                    availableUpdate = updateAvailable,
                    maintenanceConfig = inMaintenance,
                    isRefreshing = false
                )
            }
        }
    }

    fun dismissUpdateDialog() {
        val current = _uiState.value.availableUpdate ?: return
        if (!current.forceUpdate) {
            _uiState.update { it.copy(availableUpdate = null) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun startOtaUpdate() {
        val url = _uiState.value.availableUpdate?.apkDownloadUrl ?: return
        viewModelScope.launch {
            otaUpdateManager.downloadAndInstallApk(url).onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = cleanError(err, "تعذر تحميل التحديث"))
                }
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }

        viewModelScope.launch {
            val storedPinHash = vaultPrefs.vaultPinHashFlow.first()
            if (PinHashUtil.verifyPin(newQuery.trim(), storedPinHash)) {
                _uiState.update {
                    it.copy(
                        searchQuery = "",
                        searchedUsers = emptyList(),
                        openStealthVaultEvent = true
                    )
                }
                return@launch
            }

            searchJob?.cancel()
            if (newQuery.isBlank()) {
                _uiState.update { it.copy(searchedUsers = emptyList(), isSearchingUsers = false) }
                return@launch
            }

            searchJob = viewModelScope.launch {
                _uiState.update { it.copy(isSearchingUsers = true) }
                delay(250)
                val users = chatRepository.searchUsersByNameUsernameOrPhone(newQuery)
                _uiState.update {
                    it.copy(
                        searchedUsers = users,
                        isSearchingUsers = false
                    )
                }
            }
        }
    }

    fun consumeStealthVaultEvent() {
        _uiState.update { it.copy(openStealthVaultEvent = false) }
    }

    fun consumeNavigateToChat() {
        _uiState.update { it.copy(navigateToChatId = null) }
    }

    fun startDirectChatWithUser(profile: ProfileDto) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            chatRepository.getOrCreateDirectChat(profile).fold(
                onSuccess = { chatId ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            searchQuery = "",
                            searchedUsers = emptyList(),
                            navigateToChatId = chatId
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = cleanError(err, "تعذر بدء المحادثة، تأكد من تشغيل تحديث SQL")
                        )
                    }
                }
            )
        }
    }

    fun createNewGroupChat(groupName: String, memberIds: List<String>) {
        if (groupName.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            chatRepository.createGroupChat(groupName, memberIds).fold(
                onSuccess = { chatId ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            navigateToChatId = chatId
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = cleanError(err, "تعذر إنشاء المجموعة، تأكد من تشغيل تحديث SQL")
                        )
                    }
                }
            )
        }
    }

    fun toggleChatVaultState(chatId: String, hide: Boolean) {
        viewModelScope.launch {
            chatRepository.toggleChatHiddenInVault(chatId, hide)
        }
    }

    private fun cleanError(err: Throwable, fallback: String): String {
        val raw = err.localizedMessage ?: return fallback
        return raw.substringBefore("URL:").substringBefore("Headers:").trim().ifBlank { fallback }
    }
}
