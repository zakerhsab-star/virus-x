#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from pathlib import Path

FILES = {
    # =========================================================================
    # 1. UPDATED AUTH REPOSITORY (Confirm Email Support + is_username_available)
    # =========================================================================
    "app/src/main/java/com/virusx/app/data/repository/AuthRepository.kt": r'''package com.virusx.app.data.repository

import android.os.Build
import com.virusx.app.data.local.dao.ChatDao
import com.virusx.app.data.local.dao.MessageDao
import com.virusx.app.data.local.dao.ProfileDao
import com.virusx.app.data.local.entity.toEntity
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.remote.dto.UserPrivateSettingsDto
import com.virusx.app.util.PinHashUtil
import com.virusx.app.util.VaultPreferencesManager
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

enum class SignUpResult {
    AUTHENTICATED,
    AWAITING_EMAIL_CONFIRMATION
}

@Singleton
class AuthRepository @Inject constructor(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val profileDao: ProfileDao,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val vaultPrefs: VaultPreferencesManager
) {
    val isAuthenticatedFlow: Flow<Boolean> = auth.sessionStatus.map { status ->
        status is SessionStatus.Authenticated
    }

    fun getCurrentUserId(): String? = auth.currentUserOrNull()?.id

    suspend fun isPhoneAvailable(phoneNumber: String): Boolean {
        val cleanPhone = phoneNumber.trim()
        return try {
            postgrest.rpc(
                function = "is_phone_available",
                parameters = buildJsonObject {
                    put("_phone", cleanPhone)
                }
            ).decodeAs<Boolean>()
        } catch (e: Exception) {
            val existing = postgrest.from("profiles")
                .select {
                    filter { eq("phone_number", cleanPhone) }
                }.decodeList<ProfileDto>()
            existing.isEmpty()
        }
    }

    suspend fun isUsernameAvailable(username: String): Boolean {
        val cleanUsername = username.trim().lowercase()
        return try {
            postgrest.rpc(
                function = "is_username_available",
                parameters = buildJsonObject {
                    put("_username", cleanUsername)
                }
            ).decodeAs<Boolean>()
        } catch (e: Exception) {
            val existing = postgrest.from("profiles")
                .select {
                    filter { eq("username", cleanUsername) }
                }.decodeList<ProfileDto>()
            existing.isEmpty()
        }
    }

    suspend fun signUp(
        email: String,
        password: String,
        username: String,
        displayName: String,
        phoneNumber: String
    ): Result<SignUpResult> = runCatching {
        val normalizedUsername = username.trim().lowercase()
        val normalizedPhone = phoneNumber.trim()
        val normalizedName = displayName.trim()

        if (!isPhoneAvailable(normalizedPhone)) {
            throw IllegalStateException("رقم الهاتف مسجل بالفعل لحساب آخر")
        }

        if (!isUsernameAvailable(normalizedUsername)) {
            throw IllegalStateException("اسم المستخدم (@$normalizedUsername) مستخدم بالفعل")
        }

        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            this.data = buildJsonObject {
                put("username", normalizedUsername)
                put("display_name", normalizedName)
                put("phone_number", normalizedPhone)
            }
        }

        val userId = getCurrentUserId()
        if (userId == null) {
            SignUpResult.AWAITING_EMAIL_CONFIRMATION
        } else {
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            postgrest.from("profiles").update(
                buildJsonObject {
                    put("device_type", deviceModel)
                    put("phone_number", normalizedPhone)
                    put("is_online", true)
                }
            ) {
                filter { eq("id", userId) }
            }
            refreshCurrentUserProfile()
            SignUpResult.AUTHENTICATED
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        val userId = getCurrentUserId()
            ?: throw IllegalStateException("يرجى تفعيل حسابك عبر الرابط المرسل إلى بريدك الإلكتروني أولاً")
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        postgrest.from("profiles").update(
            buildJsonObject {
                put("device_type", deviceModel)
                put("is_online", true)
            }
        ) {
            filter { eq("id", userId) }
        }
        refreshCurrentUserProfile()
    }

    suspend fun signOut(): Result<Unit> = runCatching {
        val userId = getCurrentUserId()
        if (userId != null) {
            runCatching {
                postgrest.from("profiles").update(
                    buildJsonObject {
                        put("is_online", false)
                    }
                ) {
                    filter { eq("id", userId) }
                }
            }
        }
        auth.signOut()
        profileDao.clearAll()
        chatDao.clearAll()
        messageDao.clearAll()
        vaultPrefs.clearAll()
    }

    suspend fun refreshCurrentUserProfile(): ProfileDto? {
        val userId = getCurrentUserId() ?: return null
        return try {
            val profile = postgrest.from("profiles")
                .select {
                    filter { eq("id", userId) }
                }.decodeSingleOrNull<ProfileDto>()

            if (profile != null) {
                profileDao.upsertProfile(profile.toEntity())
            }

            val privateSettings = postgrest.from("user_private_settings")
                .select {
                    filter { eq("user_id", userId) }
                }.decodeSingleOrNull<UserPrivateSettingsDto>()

            if (privateSettings != null) {
                vaultPrefs.saveVaultPinHash(privateSettings.vaultPinHash)
                vaultPrefs.saveEmergencyKeyword(privateSettings.dndEmergencyKeyword)
            }
            profile
        } catch (e: Exception) {
            profileDao.getProfileById(userId)?.let {
                ProfileDto(
                    id = it.id,
                    username = it.username,
                    displayName = it.displayName,
                    email = it.email,
                    phoneNumber = it.phoneNumber,
                    avatarUrl = it.avatarUrl,
                    deviceType = it.deviceType,
                    moodStatus = it.moodStatus,
                    role = it.role,
                    isBanned = it.isBanned,
                    mutedUntil = it.mutedUntil,
                    dndEnabled = it.dndEnabled,
                    dndAutoReply = it.dndAutoReply,
                    isOnline = it.isOnline,
                    lastSeen = it.lastSeen,
                    createdAt = it.createdAt
                )
            }
        }
    }

    suspend fun getCurrentUserPrivateSettings(): UserPrivateSettingsDto? {
        val userId = getCurrentUserId() ?: return null
        return runCatching {
            postgrest.from("user_private_settings")
                .select {
                    filter { eq("user_id", userId) }
                }.decodeSingleOrNull<UserPrivateSettingsDto>()
        }.getOrNull()
    }

    suspend fun updateProfileBasicInfo(
        displayName: String,
        moodStatus: String?,
        phoneNumber: String
    ): Result<ProfileDto> = runCatching {
        val userId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")
        val current = refreshCurrentUserProfile()
        val cleanPhone = phoneNumber.trim()

        if (cleanPhone.isNotEmpty() && cleanPhone != current?.phoneNumber) {
            val available = isPhoneAvailable(cleanPhone)
            if (!available) {
                throw IllegalStateException("رقم الهاتف مستخدم بالفعل من قِبل عضو آخر")
            }
        }

        postgrest.from("profiles").update(
            buildJsonObject {
                put("display_name", displayName.trim())
                put("mood_status", moodStatus?.trim())
                if (cleanPhone.isNotEmpty()) {
                    put("phone_number", cleanPhone)
                }
            }
        ) {
            filter { eq("id", userId) }
        }
        refreshCurrentUserProfile() ?: throw IllegalStateException("تعذر تحديث الملف الشخصي")
    }

    suspend fun updateSmartDndSettings(
        dndEnabled: Boolean,
        autoReply: String,
        emergencyKeyword: String
    ): Result<Unit> = runCatching {
        val userId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")
        postgrest.from("profiles").update(
            buildJsonObject {
                put("dnd_enabled", dndEnabled)
                put("dnd_auto_reply", autoReply.trim())
            }
        ) {
            filter { eq("id", userId) }
        }

        postgrest.from("user_private_settings").update(
            buildJsonObject {
                put("dnd_emergency_keyword", emergencyKeyword.trim())
            }
        ) {
            filter { eq("user_id", userId) }
        }

        vaultPrefs.saveEmergencyKeyword(emergencyKeyword.trim())
        refreshCurrentUserProfile()
    }

    suspend fun updateVaultPin(rawPin: String): Result<Unit> = runCatching {
        val userId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")
        if (rawPin.trim().length < 4) {
            throw IllegalArgumentException("يجب أن يتكون رمز الخزنة السرية من 4 خانات على الأقل")
        }
        val hashed = PinHashUtil.hashPin(rawPin)
        postgrest.from("user_private_settings").update(
            buildJsonObject {
                put("vault_pin_hash", hashed)
            }
        ) {
            filter { eq("user_id", userId) }
        }
        vaultPrefs.saveVaultPinHash(hashed)
    }

    suspend fun redeemAdminCode(secretCode: String): Result<Boolean> = runCatching {
        val success = postgrest.rpc(
            function = "redeem_admin_code",
            parameters = buildJsonObject {
                put("_code", secretCode.trim())
            }
        ).decodeAs<Boolean>()

        if (success) {
            refreshCurrentUserProfile()
        }
        success
    }
}
''',

    # =========================================================================
    # 2. UPDATED AUTH VIEWMODEL (Handles Email Confirmation State Cleanly)
    # =========================================================================
    "app/src/main/java/com/virusx/app/ui/auth/AuthViewModel.kt": r'''package com.virusx.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.repository.AuthRepository
import com.virusx.app.data.repository.SignUpResult
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
            if (state.isSignUpMode) {
                authRepository.signUp(
                    email = state.email,
                    password = state.password,
                    username = state.username,
                    displayName = state.displayName,
                    phoneNumber = state.phoneNumber
                ).fold(
                    onSuccess = { signUpResult ->
                        if (signUpResult == SignUpResult.AWAITING_EMAIL_CONFIRMATION) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isSignUpMode = false,
                                    password = "",
                                    isSuccess = false,
                                    errorMessage = "تم إنشاء الحساب! يرجى فتح بريدك الإلكتروني والضغط على رابط التفعيل ثم تسجيل الدخول"
                                )
                            }
                        } else {
                            _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage ?: "حدث خطأ أثناء إنشاء الحساب"
                            )
                        }
                    }
                )
            } else {
                authRepository.signIn(
                    email = state.email,
                    password = state.password
                ).fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage ?: "بيانات الدخول غير صحيحة أو لم يتم تفعيل الإيميل بعد"
                            )
                        }
                    }
                )
            }
        }
    }
}
''',

    # =========================================================================
    # 3. CHAT DETAIL REPOSITORY (Realtime Messages, Whispers, Media, Sync, DND)
    # =========================================================================
    "app/src/main/java/com/virusx/app/data/repository/ChatDetailRepository.kt": r'''package com.virusx.app.data.repository

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.virusx.app.VirusXApplication
import com.virusx.app.data.local.dao.MessageDao
import com.virusx.app.data.local.dao.ProfileDao
import com.virusx.app.data.local.entity.MessageEntity
import com.virusx.app.data.local.entity.toEntity
import com.virusx.app.data.remote.dto.ChatDto
import com.virusx.app.data.remote.dto.ChatMemberDto
import com.virusx.app.data.remote.dto.ChatSyncSessionDto
import com.virusx.app.data.remote.dto.MessageDto
import com.virusx.app.data.remote.dto.MessageReadStatusDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.util.VaultPreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

@Serializable
data class TypingEventPayload(
    @SerialName("chat_id") val chatId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("is_typing") val isTyping: Boolean
)

@Singleton
class ChatDetailRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val realtime: Realtime,
    private val storage: Storage,
    private val messageDao: MessageDao,
    private val profileDao: ProfileDao,
    private val vaultPrefs: VaultPreferencesManager
) {
    private val _activeSyncSession = MutableStateFlow<ChatSyncSessionDto?>(null)
    val activeSyncSession: StateFlow<ChatSyncSessionDto?> = _activeSyncSession.asStateFlow()

    private val _typingUsers = MutableStateFlow>(emptyMap())
    val typingUsers: StateFlow> = _typingUsers.asStateFlow()

    private var activeRealtimeChannel: RealtimeChannel? = null
    private val autoRepliedSendersInSession = mutableSetOf<String>()

    fun observeLocalMessages(chatId: String): Flow<List<MessageEntity>> {
        return messageDao.observeMessagesForChat(chatId)
    }

    fun getCurrentUserId(): String? = auth.currentUserOrNull()?.id

    suspend fun fetchChatDetailsAndMembers(chatId: String): Pair<ChatDto?, List<ProfileDto>> {
        val chat = runCatching {
            postgrest.from("chats")
                .select { filter { eq("id", chatId) } }
                .decodeSingleOrNull<ChatDto>()
        }.getOrNull()

        val memberships = runCatching {
            postgrest.from("chat_members")
                .select { filter { eq("chat_id", chatId) } }
                .decodeList<ChatMemberDto>()
        }.getOrDefault(emptyList())

        val memberIds = memberships.map { it.userId }.distinct()
        val profiles = if (memberIds.isNotEmpty()) {
            runCatching {
                postgrest.from("profiles")
                    .select { filter { isIn("id", memberIds) } }
                    .decodeList<ProfileDto>()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        if (profiles.isNotEmpty()) {
            profileDao.upsertProfiles(profiles.map { it.toEntity() })
        }

        return chat to profiles
    }

    suspend fun fetchInitialMessagesAndSyncSession(chatId: String) {
        runCatching {
            val remoteMessages = postgrest.from("messages")
                .select {
                    filter { eq("chat_id", chatId) }
                    order("created_at", Order.ASCENDING)
                }.decodeList<MessageDto>()

            messageDao.upsertMessages(remoteMessages.map { it.toEntity() })
            markUnreadMessagesAsRead(chatId, remoteMessages)
        }

        runCatching {
            val session = postgrest.from("chat_sync_sessions")
                .select {
                    filter { eq("chat_id", chatId) }
                    order("updated_at", Order.DESCENDING)
                    limit(1)
                }.decodeSingleOrNull<ChatSyncSessionDto>()
            _activeSyncSession.value = session
        }
    }

    suspend fun subscribeToChatRealtime(chatId: String, scope: CoroutineScope) {
        unsubscribeFromChatRealtime()

        val channel = realtime.channel("virusx_chat_$chatId")
        activeRealtimeChannel = channel

        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }.onEach { action ->
            when (action) {
                is PostgresAction.Insert -> {
                    val msg = runCatching { action.decodeRecord<MessageDto>() }.getOrNull()
                    if (msg != null && msg.chatId == chatId) {
                        messageDao.upsertMessage(msg.toEntity())
                        handleIncomingMessageSideEffects(msg)
                    }
                }
                is PostgresAction.Update -> {
                    val msg = runCatching { action.decodeRecord<MessageDto>() }.getOrNull()
                    if (msg != null && msg.chatId == chatId) {
                        messageDao.upsertMessage(msg.toEntity())
                    }
                }
                is PostgresAction.Delete -> {}
                else -> {}
            }
        }.launchIn(scope)

        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "chat_sync_sessions"
        }.onEach { action ->
            when (action) {
                is PostgresAction.Insert -> {
                    val session = runCatching { action.decodeRecord<ChatSyncSessionDto>() }.getOrNull()
                    if (session != null && session.chatId == chatId) {
                        _activeSyncSession.value = session
                    }
                }
                is PostgresAction.Update -> {
                    val session = runCatching { action.decodeRecord<ChatSyncSessionDto>() }.getOrNull()
                    if (session != null && session.chatId == chatId) {
                        _activeSyncSession.value = session
                    }
                }
                else -> {}
            }
        }.launchIn(scope)

        channel.broadcastFlow<TypingEventPayload>(event = "typing")
            .onEach { payload ->
                val myId = getCurrentUserId()
                if (payload.chatId == chatId && payload.userId != myId) {
                    val updated = _typingUsers.value.toMutableMap()
                    if (payload.isTyping) {
                        updated[payload.userId] = payload.displayName
                    } else {
                        updated.remove(payload.userId)
                    }
                    _typingUsers.value = updated
                }
            }.launchIn(scope)

        runCatching { channel.subscribe() }
    }

    suspend fun unsubscribeFromChatRealtime() {
        val channel = activeRealtimeChannel
        activeRealtimeChannel = null
        _typingUsers.value = emptyMap()
        if (channel != null) {
            runCatching { channel.unsubscribe() }
        }
    }

    suspend fun sendTypingIndicator(chatId: String, displayName: String, isTyping: Boolean) {
        val userId = getCurrentUserId() ?: return
        val channel = activeRealtimeChannel ?: return
        runCatching {
            channel.broadcast(
                event = "typing",
                message = TypingEventPayload(
                    chatId = chatId,
                    userId = userId,
                    displayName = displayName,
                    isTyping = isTyping
                )
            )
        }
    }

    suspend fun sendTextMessage(
        chatId: String,
        content: String,
        replyToMessageId: String? = null,
        whisperTargetId: String? = null
    ): Result<MessageDto> = runCatching {
        val senderId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")
        val inserted = postgrest.from("messages").insert(
            buildJsonObject {
                put("chat_id", chatId)
                put("sender_id", senderId)
                put("message_type", "text")
                put("content", content.trim())
                if (replyToMessageId != null) put("reply_to_message_id", replyToMessageId)
                if (whisperTargetId != null) put("whisper_target_id", whisperTargetId)
                put("status", "sent")
            }
        ) {
            select()
        }.decodeSingle<MessageDto>()

        messageDao.upsertMessage(inserted.toEntity())
        inserted
    }

    suspend fun sendImageMessage(
        chatId: String,
        imageUri: Uri,
        caption: String? = null,
        replyToMessageId: String? = null,
        whisperTargetId: String? = null
    ): Result<MessageDto> = runCatching {
        val senderId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")

        val compressedBytes = withContext(Dispatchers.IO) {
            compressImageUriToJpeg(imageUri)
        }

        val objectPath = "$senderId/img_${UUID.randomUUID()}.jpg"
        val bucket = storage.from("chat-media")
        bucket.upload(path = objectPath, data = compressedBytes) {
            upsert = true
        }

        val mediaUrl = runCatching {
            bucket.createSignedUrl(path = objectPath, expiresIn = 365.days)
        }.getOrElse {
            bucket.publicUrl(objectPath)
        }

        val inserted = postgrest.from("messages").insert(
            buildJsonObject {
                put("chat_id", chatId)
                put("sender_id", senderId)
                put("message_type", "image")
                put("content", caption?.takeIf { it.isNotBlank() } ?: "📷 صورة")
                put("media_url", mediaUrl)
                if (replyToMessageId != null) put("reply_to_message_id", replyToMessageId)
                if (whisperTargetId != null) put("whisper_target_id", whisperTargetId)
                put("status", "sent")
            }
        ) {
            select()
        }.decodeSingle<MessageDto>()

        messageDao.upsertMessage(inserted.toEntity())
        inserted
    }

    suspend fun sendVoiceNoteMessage(
        chatId: String,
        audioFile: File,
        durationSeconds: Int,
        waveform: List<Float>,
        viewOnce: Boolean,
        pitchFilter: String,
        transcript: String?,
        replyToMessageId: String? = null,
        whisperTargetId: String? = null
    ): Result<MessageDto> = runCatching {
        val senderId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")
        val audioBytes = withContext(Dispatchers.IO) { audioFile.readBytes() }

        val objectPath = "$senderId/voice_${UUID.randomUUID()}.m4a"
        val bucket = storage.from("voice-notes")
        bucket.upload(path = objectPath, data = audioBytes) {
            upsert = true
        }

        val mediaUrl = runCatching {
            bucket.createSignedUrl(path = objectPath, expiresIn = 365.days)
        }.getOrElse {
            bucket.publicUrl(objectPath)
        }

        val waveformJsonArray = JsonArray(waveform.map { JsonPrimitive(it) })

        val inserted = postgrest.from("messages").insert(
            buildJsonObject {
                put("chat_id", chatId)
                put("sender_id", senderId)
                put("message_type", "voice")
                put("content", transcript?.takeIf { it.isNotBlank() } ?: "🎤 رسالة صوتية")
                put("media_url", mediaUrl)
                put("voice_view_once", viewOnce)
                put("voice_played", false)
                put("voice_duration_seconds", durationSeconds)
                put("voice_waveform", waveformJsonArray)
                put("voice_pitch_filter", pitchFilter)
                if (!transcript.isNullOrBlank()) {
                    put("voice_transcript", transcript.trim())
                }
                if (replyToMessageId != null) put("reply_to_message_id", replyToMessageId)
                if (whisperTargetId != null) put("whisper_target_id", whisperTargetId)
                put("status", "sent")
            }
        ) {
            select()
        }.decodeSingle<MessageDto>()

        messageDao.upsertMessage(inserted.toEntity())
        inserted
    }

    suspend fun markVoiceNotePlayed(messageId: String): Result<Unit> = runCatching {
        postgrest.from("messages").update(
            buildJsonObject {
                put("voice_played", true)
            }
        ) {
            filter { eq("id", messageId) }
        }
    }

    suspend fun updateVoiceTranscript(messageId: String, transcriptText: String): Result<Unit> = runCatching {
        postgrest.from("messages").update(
            buildJsonObject {
                put("voice_transcript", transcriptText.trim())
            }
        ) {
            filter { eq("id", messageId) }
        }
    }

    suspend fun deleteMessageForEveryone(messageId: String): Result<Unit> = runCatching {
        postgrest.rpc(
            function = "delete_message_for_everyone",
            parameters = buildJsonObject {
                put("_message_id", messageId)
            }
        )
    }

    suspend fun startSyncMediaSession(
        chatId: String,
        mediaUrl: String,
        mediaType: String = "audio"
    ): Result<ChatSyncSessionDto> = runCatching {
        val userId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")
        val created = postgrest.from("chat_sync_sessions").insert(
            buildJsonObject {
                put("chat_id", chatId)
                put("media_url", mediaUrl.trim())
                put("media_type", mediaType)
                put("started_by", userId)
                put("position_seconds", 0.0)
                put("is_playing", true)
            }
        ) {
            select()
        }.decodeSingle<ChatSyncSessionDto>()

        _activeSyncSession.value = created
        created
    }

    suspend fun updateSyncSessionPlayback(
        sessionId: String,
        positionSeconds: Double,
        isPlaying: Boolean
    ): Result<Unit> = runCatching {
        postgrest.from("chat_sync_sessions").update(
            buildJsonObject {
                put("position_seconds", positionSeconds)
                put("is_playing", isPlaying)
            }
        ) {
            filter { eq("id", sessionId) }
        }
    }

    private suspend fun markUnreadMessagesAsRead(chatId: String, messages: List<MessageDto>) {
        val myId = getCurrentUserId() ?: return
        val unreadFromOthers = messages.filter { it.senderId != myId && it.status != "read" }
        for (msg in unreadFromOthers) {
            runCatching {
                postgrest.from("message_read_status").insert(
                    MessageReadStatusDto(messageId = msg.id, userId = myId)
                )
                postgrest.from("messages").update(
                    buildJsonObject { put("status", "read") }
                ) {
                    filter { eq("id", msg.id) }
                }
            }
        }
    }

    private suspend fun handleIncomingMessageSideEffects(incoming: MessageDto) {
        val myId = getCurrentUserId() ?: return
        if (incoming.senderId == myId) return

        markUnreadMessagesAsRead(incoming.chatId, listOf(incoming))

        val myProfile = profileDao.getProfileById(myId) ?: return
        val emergencyKeyword = vaultPrefs.emergencyKeywordFlow.first()?.trim()
        val messageBody = incoming.content.orEmpty()

        val containsEmergencyKeyword = !emergencyKeyword.isNullOrBlank() &&
            messageBody.contains(emergencyKeyword, ignoreCase = true)

        if (containsEmergencyKeyword) {
            triggerEmergencyDndNotification(
                senderName = profileDao.getProfileById(incoming.senderId)?.displayName ?: "تنبيه طوارئ",
                messageContent = messageBody
            )
            return
        }

        if (myProfile.dndEnabled && !autoRepliedSendersInSession.contains(incoming.senderId)) {
            autoRepliedSendersInSession.add(incoming.senderId)
            val replyText = myProfile.dndAutoReply?.takeIf { it.isNotBlank() }
                ?: "أنا في المحاضرة حالياً (وضع عدم الإزعاج مفعّل) 📚. إذا كان الأمر عاجلاً أرسل كلمة الطوارئ."
            runCatching {
                sendTextMessage(
                    chatId = incoming.chatId,
                    content = "🤖 [رد تلقائي - وضع المحاضرة]: $replyText",
                    replyToMessageId = incoming.id
                )
            }
        }
    }

    private fun triggerEmergencyDndNotification(senderName: String, messageContent: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(context, VirusXApplication.CHANNEL_EMERGENCY_DND)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 طوارئ VirusX من $senderName")
            .setContentText(messageContent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun compressImageUriToJpeg(uri: Uri): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("تعذر قراءة ملف الصورة")
        val originalBitmap = inputStream.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalArgumentException("صيغة الصورة غير مدعومة")

        val maxDimension = 1280
        val ratio = minOf(
            maxDimension.toFloat() / originalBitmap.width,
            maxDimension.toFloat() / originalBitmap.height,
            1.0f
        )
        val targetWidth = (originalBitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (originalBitmap.height * ratio).toInt().coerceAtLeast(1)

        val scaledBitmap = if (ratio < 1.0f) {
            Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
        } else {
            originalBitmap
        }

        val output = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
        return output.toByteArray()
    }
}
''',

    # =========================================================================
    # 4. SMART VOICE NOTE MANAGER (Recorder, Waveform, Pitch Filters, STT)
    # =========================================================================
    "app/src/main/java/com/virusx/app/util/VoiceNoteManager.kt": r'''package com.virusx.app.util

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RecordedVoiceNoteResult(
    val file: File,
    val durationSeconds: Int,
    val waveform: List<Float>
)

@Singleton
class VoiceNoteManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordingStartTimeMs: Long = 0L
    private var amplitudeSamplingJob: Job? = null
    private val collectedAmplitudes = mutableListOf<Float>()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _liveWaveform = MutableStateFlow<List<Float>>(emptyList())
    val liveWaveform: StateFlow<List<Float>> = _liveWaveform.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    private val _currentlyPlayingMessageId = MutableStateFlow<String?>(null)
    val currentlyPlayingMessageId: StateFlow<String?> = _currentlyPlayingMessageId.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private var exoPlayer: ExoPlayer? = null

    fun startRecording(scope: CoroutineScope): Result<Unit> = runCatching {
        stopRecording()

        val voiceDir = File(context.cacheDir, "voice_notes").apply { mkdirs() }
        val outputFile = File(voiceDir, "vn_${System.currentTimeMillis()}.m4a")
        currentOutputFile = outputFile
        collectedAmplitudes.clear()

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }

        mediaRecorder = recorder
        recordingStartTimeMs = System.currentTimeMillis()
        _isRecording.value = true
        _recordingDurationSeconds.value = 0
        _liveWaveform.value = emptyList()

        amplitudeSamplingJob = scope.launch(Dispatchers.Default) {
            while (isActive && _isRecording.value) {
                val maxAmp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                val normalized = (maxAmp / 32767f).coerceIn(0.08f, 1.0f)
                collectedAmplitudes.add(normalized)
                _liveWaveform.value = collectedAmplitudes.takeLast(40)
                _recordingDurationSeconds.value =
                    ((System.currentTimeMillis() - recordingStartTimeMs) / 1000L).toInt()
                delay(120L)
            }
        }
    }

    fun stopRecording(): RecordedVoiceNoteResult? {
        amplitudeSamplingJob?.cancel()
        amplitudeSamplingJob = null
        _isRecording.value = false

        val recorder = mediaRecorder ?: return null
        mediaRecorder = null

        runCatching {
            recorder.stop()
            recorder.release()
        }

        val file = currentOutputFile ?: return null
        if (!file.exists() || file.length() == 0L) return null

        val durationSec = ((System.currentTimeMillis() - recordingStartTimeMs) / 1000L)
            .toInt()
            .coerceAtLeast(1)

        val finalWaveform = if (collectedAmplitudes.isEmpty()) {
            List(24) { 0.25f }
        } else {
            downsampleWaveform(collectedAmplitudes, targetBars = 32)
        }

        return RecordedVoiceNoteResult(
            file = file,
            durationSeconds = durationSec,
            waveform = finalWaveform
        )
    }

    fun playVoiceNote(
        messageId: String,
        mediaUrl: String,
        pitchFilter: String?,
        onCompleted: (String) -> Unit
    ) {
        stopPlayback()

        val player = ExoPlayer.Builder(context).build()
        exoPlayer = player
        _currentlyPlayingMessageId.value = messageId

        val playbackParams = when (pitchFilter) {
            "deep" -> PlaybackParameters(0.92f, 0.72f)
            "robot" -> PlaybackParameters(1.08f, 1.35f)
            "fast" -> PlaybackParameters(1.45f, 1.05f)
            else -> PlaybackParameters(1.0f, 1.0f)
        }

        player.playbackParameters = playbackParams
        player.setMediaItem(MediaItem.fromUri(mediaUrl))
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val finishedId = _currentlyPlayingMessageId.value
                    stopPlayback()
                    if (finishedId != null) {
                        onCompleted(finishedId)
                    }
                }
            }
        })
        player.prepare()
        player.play()
    }

    fun stopPlayback() {
        _currentlyPlayingMessageId.value = null
        exoPlayer?.let { player ->
            runCatching {
                player.stop()
                player.release()
            }
        }
        exoPlayer = null
    }

    fun recognizeSpeechToText(
        languageTag: String = "ar-EG",
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("خدمة التعرف الصوتي غير متوفرة على هذا الجهاز")
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        _isTranscribing.value = true

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _isTranscribing.value = false
            }

            override fun onError(error: Int) {
                _isTranscribing.value = false
                recognizer.destroy()
                onError("تعذر تحويل الصوت لنص (كود: $error)")
            }

            override fun onResults(results: Bundle?) {
                _isTranscribing.value = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                recognizer.destroy()
                if (text.isNotBlank()) {
                    onResult(text)
                } else {
                    onError("لم يتم التقاط نص واضح")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }

    private fun downsampleWaveform(source: List<Float>, targetBars: Int): List<Float> {
        if (source.size <= targetBars) return source
        val step = source.size.toFloat() / targetBars
        return List(targetBars) { index ->
            val pos = (index * step).toInt().coerceIn(0, source.lastIndex)
            source[pos]
        }
    }
}
'''
}


def main():
    root = Path.cwd()
    print(f"[*] Applying Task 4 (Part 1) files in: {root}")
    for rel_path, content in FILES.items():
        file_path = root / rel_path
        file_path.parent.mkdir(parents=True, exist_ok=True)
        file_path.write_text(content.strip() + "\n", encoding="utf-8")
        print(f"  [+] Updated/Created: {rel_path}")
    print("\n[✓] Task 4 Part 1 completed (4 files).")


if __name__ == "__main__":
    main()