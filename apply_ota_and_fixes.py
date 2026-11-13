#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from pathlib import Path

FILES = {
    # =========================================================================
    # 1. APP CONFIG DTO (Remote OTA Update & Maintenance Config)
    # =========================================================================
    "app/src/main/java/com/virusx/app/data/remote/dto/AppConfigDto.kt": r'''package com.virusx.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppConfigDto(
    val id: Int = 1,
    @SerialName("latest_version_code") val latestVersionCode: Int = 1,
    @SerialName("latest_version_name") val latestVersionName: String = "1.0.0",
    @SerialName("apk_download_url") val apkDownloadUrl: String = "",
    @SerialName("release_notes") val releaseNotes: String = "",
    @SerialName("force_update") val forceUpdate: Boolean = false,
    @SerialName("maintenance_mode") val maintenanceMode: Boolean = false,
    @SerialName("maintenance_message") val maintenanceMessage: String = ""
)
''',

    # =========================================================================
    # 2. IN-APP OTA UPDATE MANAGER (Downloads & Installs APK in 1 Tap)
    # =========================================================================
    "app/src/main/java/com/virusx/app/util/OtaUpdateManager.kt": r'''package com.virusx.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OtaUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    suspend fun downloadAndInstallApk(apkUrl: String): Result<Unit> = runCatching {
        if (apkUrl.isBlank()) {
            throw IllegalArgumentException("رابط التحديث غير متوفر حالياً")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            throw IllegalStateException("يرجى تفعيل خيار 'السماح من هذا المصدر' ثم الضغط على تحديث مرة أخرى")
        }

        _downloadProgress.value = 0.05f
        val apkFile = withContext(Dispatchers.IO) {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(updatesDir, "VirusX_latest.apk")
            if (outFile.exists()) outFile.delete()

            val connection = (URL(apkUrl.trim()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                instanceFollowRedirects = true
                connect()
            }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("تعذر تحميل التحديث (كود ${connection.responseCode})")
            }

            val totalBytes = connection.contentLength.toLong()
            var downloaded = 0L
            connection.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            _downloadProgress.value = (downloaded.toFloat() / totalBytes).coerceIn(0.05f, 0.99f)
                        }
                    }
                }
            }
            outFile
        }

        _downloadProgress.value = null
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }.also {
        _downloadProgress.value = null
    }
}
''',

    # =========================================================================
    # 3. FILE PROVIDER PATHS FOR IN-APP APK INSTALLER
    # =========================================================================
    "app/src/main/res/xml/file_paths.xml": r'''<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="apk_updates" path="updates/" />
    <cache-path name="shared_cache" path="." />
</paths>
''',

    # =========================================================================
    # 4. CHAT REPOSITORY (Atomic Direct Chat & Group Creation RPCs + AppConfig)
    # =========================================================================
    "app/src/main/java/com/virusx/app/data/repository/ChatRepository.kt": r'''package com.virusx.app.data.repository

import com.virusx.app.data.local.dao.ChatDao
import com.virusx.app.data.local.dao.ProfileDao
import com.virusx.app.data.local.entity.ChatEntity
import com.virusx.app.data.local.entity.toDto
import com.virusx.app.data.local.entity.toEntity
import com.virusx.app.data.remote.dto.AppConfigDto
import com.virusx.app.data.remote.dto.BroadcastDto
import com.virusx.app.data.remote.dto.ChatDto
import com.virusx.app.data.remote.dto.ChatInsertDto
import com.virusx.app.data.remote.dto.ChatMemberDto
import com.virusx.app.data.remote.dto.MessageDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.util.VaultPreferencesManager
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val chatDao: ChatDao,
    private val profileDao: ProfileDao,
    private val vaultPrefs: VaultPreferencesManager
) {
    val visibleChatsFlow: Flow<List<ChatEntity>> = chatDao.observeVisibleChats()
    val vaultChatsFlow: Flow<List<ChatEntity>> = chatDao.observeVaultChats()

    suspend fun syncUserChats(): Result<Unit> = runCatching {
        val currentUserId = auth.currentUserOrNull()?.id ?: return@runCatching

        val myMemberships = postgrest.from("chat_members")
            .select {
                filter { eq("user_id", currentUserId) }
            }.decodeList<ChatMemberDto>()

        if (myMemberships.isEmpty()) return@runCatching
        val chatIds = myMemberships.map { it.chatId }

        val chats = postgrest.from("chats")
            .select {
                filter { isIn("id", chatIds) }
            }.decodeList<ChatDto>()

        val allMembers = postgrest.from("chat_members")
            .select {
                filter { isIn("chat_id", chatIds) }
            }.decodeList<ChatMemberDto>()

        val userIds = allMembers.map { it.userId }.distinct()
        val profiles = if (userIds.isNotEmpty()) {
            postgrest.from("profiles")
                .select {
                    filter { isIn("id", userIds) }
                }.decodeList<ProfileDto>()
        } else {
            emptyList()
        }

        profileDao.upsertProfiles(profiles.map { it.toEntity() })
        val profileMap = profiles.associateBy { it.id }

        val entities = chats.map { chat ->
            val membersOfChat = allMembers.filter { it.chatId == chat.id }
            val peerMember = membersOfChat.firstOrNull { it.userId != currentUserId }
            val peerProfile = peerMember?.let { profileMap[it.userId] }

            val lastMessage = runCatching {
                postgrest.from("messages")
                    .select {
                        filter {
                            eq("chat_id", chat.id)
                            eq("is_deleted", false)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(1)
                    }.decodeSingleOrNull<MessageDto>()
            }.getOrNull()

            val previewText = when {
                lastMessage == null -> "ابدأ المحادثة الآن..."
                lastMessage.whisperTargetId != null -> "🔒 همسة سرية داخل الجروب"
                lastMessage.messageType == "voice" -> "🎤 رسالة صوتية (${lastMessage.voiceDurationSeconds ?: 0} ث)"
                lastMessage.messageType == "image" -> "📷 صورة"
                lastMessage.messageType == "video" -> "🎬 فيديو"
                else -> lastMessage.content ?: ""
            }

            val isHidden = chatDao.isChatHidden(chat.id) ?: vaultPrefs.isChatHidden(chat.id)

            val resolvedTitle = if (chat.type == "direct") {
                peerProfile?.displayName ?: chat.name ?: "محادثة خاصة"
            } else {
                chat.name ?: "مجموعة VirusX"
            }

            val resolvedAvatar = if (chat.type == "direct") {
                peerProfile?.avatarUrl ?: chat.avatarUrl
            } else {
                chat.avatarUrl
            }

            ChatEntity(
                id = chat.id,
                type = chat.type,
                displayTitle = resolvedTitle,
                avatarUrl = resolvedAvatar,
                peerUserId = peerProfile?.id,
                peerOnline = peerProfile?.isOnline ?: false,
                peerPhone = peerProfile?.phoneNumber,
                lastMessagePreview = previewText,
                lastMessageTime = lastMessage?.createdAt ?: chat.createdAt,
                unreadCount = 0,
                isHiddenInVault = isHidden,
                createdAt = chat.createdAt
            )
        }

        chatDao.upsertChats(entities)
    }

    suspend fun searchUsersByNameUsernameOrPhone(query: String): List<ProfileDto> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()
        val currentUserId = auth.currentUserOrNull()?.id

        return try {
            val remoteProfiles = postgrest.from("profiles")
                .select {
                    filter {
                        or {
                            ilike("display_name", "%$clean%")
                            ilike("username", "%$clean%")
                            ilike("phone_number", "%$clean%")
                        }
                    }
                    limit(30)
                }.decodeList<ProfileDto>()

            profileDao.upsertProfiles(remoteProfiles.map { it.toEntity() })
            remoteProfiles.filter { it.id != currentUserId }
        } catch (e: Exception) {
            profileDao.searchProfilesLocally(clean)
                .map { it.toDto() }
                .filter { it.id != currentUserId }
        }
    }

    suspend fun getOrCreateDirectChat(targetProfile: ProfileDto): Result<String> = runCatching {
        val currentUserId = auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("غير مسجل الدخول")

        val rpcChatId = runCatching {
            postgrest.rpc(
                function = "create_direct_chat",
                parameters = buildJsonObject {
                    put("_peer_id", targetProfile.id)
                }
            ).decodeAs<String>()
        }.getOrNull()

        if (!rpcChatId.isNullOrBlank()) {
            syncUserChats()
            return@runCatching rpcChatId
        }

        val createdChat = postgrest.from("chats")
            .insert(
                ChatInsertDto(
                    type = "direct",
                    name = null,
                    createdBy = currentUserId
                )
            ) {
                select()
            }.decodeSingle<ChatDto>()

        postgrest.from("chat_members").insert(
            listOf(
                ChatMemberDto(chatId = createdChat.id, userId = currentUserId, memberRole = "group_admin"),
                ChatMemberDto(chatId = createdChat.id, userId = targetProfile.id, memberRole = "member")
            )
        )

        syncUserChats()
        createdChat.id
    }

    suspend fun createGroupChat(groupName: String, memberUserIds: List<String>): Result<String> = runCatching {
        val currentUserId = auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("غير مسجل الدخول")

        val allIds = (memberUserIds + currentUserId).distinct()
        if (allIds.size > 50) {
            throw IllegalArgumentException("الحد الأقصى لأعضاء المجموعة هو 50 عضواً")
        }

        val rpcGroupId = runCatching {
            postgrest.rpc(
                function = "create_group_chat",
                parameters = buildJsonObject {
                    put("_name", groupName.trim())
                    put("_member_ids", JsonArray(memberUserIds.map { JsonPrimitive(it) }))
                }
            ).decodeAs<String>()
        }.getOrNull()

        if (!rpcGroupId.isNullOrBlank()) {
            syncUserChats()
            return@runCatching rpcGroupId
        }

        val createdChat = postgrest.from("chats")
            .insert(
                ChatInsertDto(
                    type = "group",
                    name = groupName.trim(),
                    createdBy = currentUserId
                )
            ) {
                select()
            }.decodeSingle<ChatDto>()

        postgrest.from("chat_members").insert(
            ChatMemberDto(
                chatId = createdChat.id,
                userId = currentUserId,
                memberRole = "group_admin"
            )
        )

        val otherMembers = allIds.filter { it != currentUserId }.map { uid ->
            ChatMemberDto(
                chatId = createdChat.id,
                userId = uid,
                memberRole = "member"
            )
        }
        if (otherMembers.isNotEmpty()) {
            postgrest.from("chat_members").insert(otherMembers)
        }

        syncUserChats()
        createdChat.id
    }

    suspend fun toggleChatHiddenInVault(chatId: String, hidden: Boolean) {
        vaultPrefs.setChatHidden(chatId, hidden)
        chatDao.setChatHiddenInVault(chatId, hidden)
    }

    suspend fun fetchLatestBroadcast(): BroadcastDto? = runCatching {
        postgrest.from("broadcasts")
            .select {
                order("created_at", Order.DESCENDING)
                limit(1)
            }.decodeSingleOrNull<BroadcastDto>()
    }.getOrNull()

    suspend fun fetchAppConfig(): AppConfigDto? = runCatching {
        postgrest.from("app_config")
            .select {
                filter { eq("id", 1) }
                limit(1)
            }.decodeSingleOrNull<AppConfigDto>()
    }.getOrNull()
}
''',

    # =========================================================================
    # 5. HOME VIEWMODEL (Clean Errors + OTA Update Check + Maintenance Mode)
    # =========================================================================
    "app/src/main/java/com/virusx/app/ui/home/HomeViewModel.kt": r'''package com.virusx.app.ui.home

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
''',

    # =========================================================================
    # 6. HOME SCREEN UI (Avatars, Clear Errors, Loading State, OTA Update Dialog)
    # =========================================================================
    "app/src/main/java/com/virusx/app/ui/home/HomeScreen.kt": r'''package com.virusx.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.virusx.app.data.local.entity.ChatEntity
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.ui.theme.DangerRed
import com.virusx.app.ui.theme.OnlineGreen
import com.virusx.app.ui.theme.SentMessageGradient
import com.virusx.app.ui.theme.TextMuted
import com.virusx.app.ui.theme.TextPrimary
import com.virusx.app.ui.theme.TextSecondary
import com.virusx.app.ui.theme.VirusXBackground
import com.virusx.app.ui.theme.VirusXGlassBorder
import com.virusx.app.ui.theme.VirusXPrimary
import com.virusx.app.ui.theme.VirusXSecondaryWhisper
import com.virusx.app.ui.theme.VirusXSurface
import com.virusx.app.ui.theme.VirusXSurfaceElevated

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenChat: (String) -> Unit,
    onOpenStories: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStealthVault: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val visibleChats by viewModel.visibleChats.collectAsState()
    val otaProgress by viewModel.otaDownloadProgress.collectAsState()
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var selectedChatForVaultDialog by remember { mutableStateOf<ChatEntity?>(null) }

    LaunchedEffect(state.openStealthVaultEvent) {
        if (state.openStealthVaultEvent) {
            viewModel.consumeStealthVaultEvent()
            onOpenStealthVault()
        }
    }

    LaunchedEffect(state.navigateToChatId) {
        val targetChatId = state.navigateToChatId
        if (targetChatId != null) {
            viewModel.consumeNavigateToChat()
            onOpenChat(targetChatId)
        }
    }

    // Full-screen maintenance overlay if enabled on server
    state.maintenanceConfig?.let { maint ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VirusXBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.Build, contentDescription = null, tint = VirusXSecondaryWhisper, modifier = Modifier.size(56.dp))
                Text("تحديث وصيانة النظام", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text(
                    text = maint.maintenanceMessage.ifBlank { "التطبيق تحت الصيانة حالياً، سنعود قريباً." },
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = viewModel::refreshAll,
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary)
                ) {
                    Text("إعادة المحاولة")
                }
            }
        }
        return
    }

    val filteredChats = remember(visibleChats, state.searchQuery) {
        if (state.searchQuery.isBlank()) {
            visibleChats
        } else {
            val q = state.searchQuery.trim()
            visibleChats.filter {
                it.displayTitle.contains(q, ignoreCase = true) ||
                    (it.peerPhone?.contains(q, ignoreCase = true) == true)
            }
        }
    }

    Scaffold(
        containerColor = VirusXBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateGroupDialog = true },
                containerColor = VirusXPrimary,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Default.GroupAdd, contentDescription = "إنشاء جروب جديد")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VirusX",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = state.currentUserProfile?.let {
                            "متصل كـ ${it.displayName} (@${it.username})"
                        } ?: "مراسلة فورية مشفرة",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onOpenStories) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesomeMotion,
                            contentDescription = "الحالات التفاعلية",
                            tint = VirusXSecondaryWhisper
                        )
                    }
                    IconButton(onClick = viewModel::refreshAll) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تحديث",
                            tint = TextSecondary
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = TextPrimary
                        )
                    }
                }
            }

            if (state.isRefreshing) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    color = VirusXSecondaryWhisper,
                    trackColor = VirusXSurface,
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                )
            }

            state.errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DangerRed.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = err,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = viewModel::clearError, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stealth Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "ابحث بالاسم، اليوزرنيم، أو رقم الهاتف...",
                        color = TextMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = VirusXPrimary
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "مسح",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = VirusXSurface,
                    unfocusedContainerColor = VirusXSurface,
                    focusedBorderColor = VirusXPrimary,
                    unfocusedBorderColor = VirusXGlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Global Admin Broadcast Banner
            state.latestBroadcast?.let { broadcast ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(VirusXSurfaceElevated)
                        .border(1.dp, VirusXGlassBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = VirusXSecondaryWhisper,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = broadcast.title,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = broadcast.body,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.searchQuery.isNotBlank() && state.searchedUsers.isNotEmpty()) {
                    item {
                        Text(
                            text = "نتائج البحث عن الأعضاء (${state.searchedUsers.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = VirusXSecondaryWhisper,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(state.searchedUsers, key = { "user_${it.id}" }) { user ->
                        UserSearchResultCard(
                            profile = user,
                            onClick = { viewModel.startDirectChatWithUser(user) }
                        )
                    }
                }

                item {
                    Text(
                        text = "المحادثات النشطة (اضغط مطولاً لإخفاء المحادثة في الخزنة السرية)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if (filteredChats.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد محادثات ظاهرة حالياً.\nابحث عن صديق بالاسم أو اليوزرنيم أو رقم الهاتف لبدء الدردشة.",
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(filteredChats, key = { it.id }) { chat ->
                        ChatRowItem(
                            chat = chat,
                            onClick = { onOpenChat(chat.id) },
                            onLongClick = { selectedChatForVaultDialog = chat }
                        )
                    }
                }
            }
        }
    }

    // In-App OTA Update Dialog
    state.availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { if (!update.forceUpdate) viewModel.dismissUpdateDialog() },
            containerColor = VirusXSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = VirusXSecondaryWhisper)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تحديث جديد متاح (${update.latestVersionName})", color = TextPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = update.releaseNotes.ifBlank { "يتوفر إصدار جديد من VirusX يتضمن تحسينات وإصلاحات هامة." },
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    if (otaProgress != null) {
                        LinearProgressIndicator(
                            progress = { otaProgress ?: 0f },
                            color = VirusXSecondaryWhisper,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "جاري تحميل التحديث (${((otaProgress ?: 0f) * 100).toInt()}%)...",
                            color = VirusXSecondaryWhisper,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::startOtaUpdate,
                    enabled = otaProgress == null,
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary)
                ) {
                    Text("تحديث وتثبيت الآن")
                }
            },
            dismissButton = {
                if (!update.forceUpdate && otaProgress == null) {
                    TextButton(onClick = viewModel::dismissUpdateDialog) {
                        Text("لاحقاً", color = TextSecondary)
                    }
                }
            }
        )
    }

    selectedChatForVaultDialog?.let { targetChat ->
        AlertDialog(
            onDismissRequest = { selectedChatForVaultDialog = null },
            containerColor = VirusXSurface,
            title = {
                Text("إخفاء المحادثة في الخزنة السرية", color = TextPrimary)
            },
            text = {
                Text(
                    "هل تريد إخفاء محادثة \"${targetChat.displayTitle}\" تماماً من القائمة الرئيسية؟ يمكنك فتحها لاحقاً بكتابة الـ PIN السري داخل شريط البحث.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleChatVaultState(targetChat.id, true)
                        selectedChatForVaultDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إخفاء الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedChatForVaultDialog = null }) {
                    Text("إلغاء", color = TextSecondary)
                }
            }
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { groupName ->
                viewModel.createNewGroupChat(groupName, emptyList())
                showCreateGroupDialog = false
            }
        )
    }
}

@Composable
private fun UserSearchResultCard(
    profile: ProfileDto,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VirusXSurfaceElevated)
            .border(1.dp, VirusXGlassBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(SentMessageGradient),
            contentAlignment = Alignment.Center
        ) {
            if (!profile.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = profile.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = profile.displayName.take(1).uppercase(),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.displayName,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@${profile.username}",
                    color = VirusXSecondaryWhisper,
                    fontSize = 13.sp
                )
                if (!profile.phoneNumber.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = profile.phoneNumber,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "بدء محادثة",
            tint = VirusXPrimary
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRowItem(
    chat: ChatEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(VirusXSurface)
            .border(1.dp, VirusXGlassBorder, RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(SentMessageGradient),
                contentAlignment = Alignment.Center
            ) {
                if (!chat.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = chat.avatarUrl,
                        contentDescription = chat.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = chat.displayTitle.take(1).uppercase(),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
            if (chat.peerOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(OnlineGreen)
                        .border(2.dp, VirusXSurface, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.displayTitle,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (chat.type == "group") "مجموعة" else "خاص",
                    color = VirusXSecondaryWhisper,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = chat.lastMessagePreview ?: "لا توجد رسائل بعد",
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VirusXSurface,
        title = { Text("إنشاء مجموعة جديدة (حتى 50 عضو)", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("اسم المجموعة") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (groupName.isNotBlank()) onCreate(groupName) },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary)
            ) {
                Text("إنشاء")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}
'''
}


def main():
    root = Path.cwd()
    print(f"[*] Applying Bug Fixes & In-App OTA Updater in: {root}")
    for rel_path, content in FILES.items():
        file_path = root / rel_path
        file_path.parent.mkdir(parents=True, exist_ok=True)
        clean_content = content.replace("\u00a0", " ").strip() + "\n"
        file_path.write_text(clean_content, encoding="utf-8")
        print(f"  [+] Updated/Created: {rel_path}")

    # 1. Patch AndroidManifest.xml with REQUEST_INSTALL_PACKAGES & FileProvider
    manifest_path = root / "app/src/main/AndroidManifest.xml"
    if manifest_path.exists():
        m_txt = manifest_path.read_text(encoding="utf-8")
        if "REQUEST_INSTALL_PACKAGES" not in m_txt:
            m_txt = m_txt.replace(
                "<application",
                '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n\n    <application'
            )
        if ".fileprovider" not in m_txt:
            provider_xml = """        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>"""
            m_txt = m_txt.replace("    </application>", provider_xml)
        manifest_path.write_text(m_txt, encoding="utf-8")
        print("  [+] Configured FileProvider & OTA Install Permission in AndroidManifest.xml")

    # 2. Patch AdminRepository.kt with updateAppConfig()
    admin_repo = root / "app/src/main/java/com/virusx/app/data/repository/AdminRepository.kt"
    if admin_repo.exists():
        ar_txt = admin_repo.read_text(encoding="utf-8")
        if "updateAppConfig" not in ar_txt:
            ota_method = """    suspend fun updateAppConfig(
        latestVersionCode: Int,
        latestVersionName: String,
        apkDownloadUrl: String,
        releaseNotes: String,
        forceUpdate: Boolean,
        maintenanceMode: Boolean
    ): Result<Unit> = runCatching {
        postgrest.from("app_config").update(
            buildJsonObject {
                put("latest_version_code", latestVersionCode)
                put("latest_version_name", latestVersionName.trim())
                put("apk_download_url", apkDownloadUrl.trim())
                put("release_notes", releaseNotes.trim())
                put("force_update", forceUpdate)
                put("maintenance_mode", maintenanceMode)
            }
        ) {
            filter { eq("id", 1) }
        }
    }

    suspend fun lockAdminPromotionCode"""
            ar_txt = ar_txt.replace("    suspend fun lockAdminPromotionCode", ota_method)
            admin_repo.write_text(ar_txt, encoding="utf-8")
            print("  [+] Added updateAppConfig() to AdminRepository.kt")

    # 3. Patch SuperAdminViewModel.kt with publishOtaUpdate()
    sa_vm = root / "app/src/main/java/com/virusx/app/ui/admin/SuperAdminViewModel.kt"
    if sa_vm.exists():
        vm_txt = sa_vm.read_text(encoding="utf-8")
        if "publishOtaUpdate" not in vm_txt:
            ota_vm_method = """    fun publishOtaUpdate(
        versionCodeStr: String,
        versionName: String,
        apkUrl: String,
        notes: String,
        forceUpdate: Boolean,
        maintenanceMode: Boolean
    ) {
        val code = versionCodeStr.trim().toIntOrNull() ?: 1
        viewModelScope.launch {
            adminRepository.updateAppConfig(
                latestVersionCode = code,
                latestVersionName = versionName,
                apkDownloadUrl = apkUrl,
                releaseNotes = notes,
                forceUpdate = forceUpdate,
                maintenanceMode = maintenanceMode
            ).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم نشر إعدادات التحديث السحابي لجميع المستخدمين بنجاح") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun lockAdminCodePermanently"""
            vm_txt = vm_txt.replace("    fun lockAdminCodePermanently", ota_vm_method)
            sa_vm.write_text(vm_txt, encoding="utf-8")
            print("  [+] Added publishOtaUpdate() to SuperAdminViewModel.kt")

    # 4. Patch SuperAdminScreen.kt to include OTA Update Publisher inside Tab 4
    sa_screen = root / "app/src/main/java/com/virusx/app/ui/admin/SuperAdminScreen.kt"
    if sa_screen.exists():
        sc_txt = sa_screen.read_text(encoding="utf-8")
        if "onPublishOta" not in sc_txt:
            if "androidx.compose.foundation.rememberScrollState" not in sc_txt:
                sc_txt = sc_txt.replace(
                    "import androidx.compose.foundation.lazy.items",
                    "import androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\nimport androidx.compose.material3.Switch"
                )
            sc_txt = sc_txt.replace(
                "                    3 -> AdminBroadcastAndSecurityTab(\n                        onSendBroadcast = viewModel::sendBroadcast,\n                        onLockPromotion = viewModel::lockAdminCodePermanently\n                    )",
                "                    3 -> AdminBroadcastAndSecurityTab(\n                        onSendBroadcast = viewModel::sendBroadcast,\n                        onPublishOta = viewModel::publishOtaUpdate,\n                        onLockPromotion = viewModel::lockAdminCodePermanently\n                    )"
            )
            old_tab4 = """@Composable
private fun AdminBroadcastAndSecurityTab(
    onSendBroadcast: (String, String) -> Unit,
    onLockPromotion: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {"""
            new_tab4 = """@Composable
private fun AdminBroadcastAndSecurityTab(
    onSendBroadcast: (String, String) -> Unit,
    onPublishOta: (String, String, String, String, Boolean, Boolean) -> Unit,
    onLockPromotion: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var verCode by remember { mutableStateOf("2") }
    var verName by remember { mutableStateOf("1.0.1") }
    var apkUrl by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("تحسينات في الأداء وإصلاح الأخطاء") }
    var forceUpdate by remember { mutableStateOf(false) }
    var maintMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // In-App OTA Update Publisher Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(VirusXSurface)
                .border(1.dp, VirusXSecondaryWhisper.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🚀 نشر تحديث تلقائي للتطبيق (OTA Update)", color = VirusXSecondaryWhisper, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = verCode,
                    onValueChange = { verCode = it },
                    label = { Text("رقم الإصدار (Code)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = verName,
                    onValueChange = { verName = it },
                    label = { Text("اسم الإصدار") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = apkUrl,
                onValueChange = { apkUrl = it },
                label = { Text("رابط تحميل ملف APK المباشر") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات التحديث") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("تحديث إجباري (Force Update)", color = TextPrimary, fontSize = 13.sp)
                Switch(checked = forceUpdate, onCheckedChange = { forceUpdate = it })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("وضع الصيانة المؤقت (Maintenance)", color = TextPrimary, fontSize = 13.sp)
                Switch(checked = maintMode, onCheckedChange = { maintMode = it })
            }
            Button(
                onClick = { onPublishOta(verCode, verName, apkUrl, notes, forceUpdate, maintMode) },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXSecondaryWhisper),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("نشر التحديث الآن لكل الأجهزة", color = VirusXBackground, fontWeight = FontWeight.Bold)
            }
        }"""
            sc_txt = sc_txt.replace(old_tab4, new_tab4)
            sa_screen.write_text(sc_txt, encoding="utf-8")
            print("  [+] Added In-App OTA Update Publisher UI to SuperAdminScreen.kt")

    # Clean up temporary inspect file
    inspect_file = root / "home_inspect.txt"
    if inspect_file.exists():
        inspect_file.unlink()

    print("\n[✓] All fixes and OTA Updater applied successfully!")


if __name__ == "__main__":
    main()