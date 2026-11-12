package com.virusx.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.local.entity.ChatEntity
import com.virusx.app.data.remote.dto.BroadcastDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.repository.AuthRepository
import com.virusx.app.data.repository.ChatRepository
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
    val isRefreshing: Boolean = false,
    val openStealthVaultEvent: Boolean = false,
    val navigateToChatId: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val vaultPrefs: VaultPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val visibleChats: StateFlow<List<ChatEntity>> = chatRepository.visibleChatsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultChats: StateFlow<List<ChatEntity>> = chatRepository.vaultChatsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val profile = authRepository.refreshCurrentUserProfile()
            chatRepository.syncUserChats()
            val broadcast = chatRepository.fetchLatestBroadcast()
            _uiState.update {
                it.copy(
                    currentUserProfile = profile,
                    latestBroadcast = broadcast,
                    isRefreshing = false
                )
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }

        viewModelScope.launch {
            // 1. Stealth Search Vault Trigger: Check if query matches Vault PIN
            val storedPinHash = vaultPrefs.vaultPinHashFlow.first()
            if (PinHashUtil.verifyPin(newQuery.trim(), storedPinHash)) {
                // Immediately clear search query so PIN never lingers on screen and unlock Vault
                _uiState.update {
                    it.copy(
                        searchQuery = "",
                        searchedUsers = emptyList(),
                        openStealthVaultEvent = true
                    )
                }
                return@launch
            }

            // 2. Otherwise search users by Display Name, Username, or Phone Number
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
            _uiState.update { it.copy(isRefreshing = true) }
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
                            errorMessage = err.localizedMessage
                        )
                    }
                }
            )
        }
    }

    fun createNewGroupChat(groupName: String, memberIds: List<String>) {
        if (groupName.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            chatRepository.createNewGroupChatCompat(groupName, memberIds).fold(
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
                            errorMessage = err.localizedMessage
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
}

private suspend fun ChatRepository.createNewGroupChatCompat(
    groupName: String,
    memberIds: List<String>
): Result<String> = createGroupChat(groupName, memberIds)
