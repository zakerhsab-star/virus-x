#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import re
import subprocess
import sys
from pathlib import Path


def find_project_root() -> Path:
    cwd = Path.cwd()
    if (cwd / "app" / "build.gradle.kts").exists():
        return cwd
    if (cwd / "VirusX" / "app" / "build.gradle.kts").exists():
        return cwd / "VirusX"
    script_dir = Path(__file__).resolve().parent
    if (script_dir / "app" / "build.gradle.kts").exists():
        return script_dir
    if (script_dir / "VirusX" / "app" / "build.gradle.kts").exists():
        return script_dir / "VirusX"
    raise FileNotFoundError(
        "لم يتم العثور على مجلد مشروع VirusX! ضع الملف داخل مجلد VirusX أو في مجلد Downloads بجانبه."
    )


FILES = {
    "app/src/main/java/com/virusx/app/data/remote/dto/MessageDto.kt": """package com.virusx.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val id: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("message_type") val messageType: String = "text",
    val content: String? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("reply_to_message_id") val replyToMessageId: String? = null,
    @SerialName("whisper_target_id") val whisperTargetId: String? = null,
    @SerialName("voice_view_once") val voiceViewOnce: Boolean = false,
    @SerialName("voice_played") val voicePlayed: Boolean = false,
    @SerialName("voice_duration_seconds") val voiceDurationSeconds: Int? = null,
    @SerialName("voice_waveform") val voiceWaveform: List<Float>? = null,
    @SerialName("voice_pitch_filter") val voicePitchFilter: String? = null,
    @SerialName("voice_transcript") val voiceTranscript: String? = null,
    val status: String = "sent",
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("reactions") val reactions: Map<String, List<String>> = emptyMap(),
    @SerialName("destruct_seconds") val destructSeconds: Int? = null,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
""",

    "app/src/main/java/com/virusx/app/data/local/entity/MessageEntity.kt": """package com.virusx.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.virusx.app.data.remote.dto.MessageDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val messageType: String = "text",
    val content: String? = null,
    val mediaUrl: String? = null,
    val replyToMessageId: String? = null,
    val whisperTargetId: String? = null,
    val voiceViewOnce: Boolean = false,
    val voicePlayed: Boolean = false,
    val voiceDurationSeconds: Int? = null,
    val voiceWaveformJson: String? = null,
    val voicePitchFilter: String? = null,
    val voiceTranscript: String? = null,
    val status: String = "sent",
    val isDeleted: Boolean = false,
    val deletedAt: String? = null,
    val reactionsJson: String? = null,
    val destructSeconds: Int? = null,
    val editedAt: String? = null,
    val createdAt: String? = null
)

fun MessageDto.toEntity(): MessageEntity = MessageEntity(
    id = id,
    chatId = chatId,
    senderId = senderId,
    messageType = messageType,
    content = content,
    mediaUrl = mediaUrl,
    replyToMessageId = replyToMessageId,
    whisperTargetId = whisperTargetId,
    voiceViewOnce = voiceViewOnce,
    voicePlayed = voicePlayed,
    voiceDurationSeconds = voiceDurationSeconds,
    voiceWaveformJson = voiceWaveform?.joinToString(","),
    voicePitchFilter = voicePitchFilter,
    voiceTranscript = voiceTranscript,
    status = status,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    reactionsJson = runCatching { Json.encodeToString(reactions) }.getOrNull(),
    destructSeconds = destructSeconds,
    editedAt = editedAt,
    createdAt = createdAt
)
""",

    "app/src/main/java/com/virusx/app/data/repository/ChatDetailRepository.kt": """package com.virusx.app.data.repository

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

    private val _typingUsers = MutableStateFlow<Map<String, String>>(emptyMap())
    val typingUsers: StateFlow<Map<String, String>> = _typingUsers.asStateFlow()

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

    suspend fun addMemberToGroup(chatId: String, newUserId: String): Result<Unit> = runCatching {
        postgrest.rpc(
            function = "add_group_member",
            parameters = buildJsonObject {
                put("_chat_id", chatId)
                put("_new_user_id", newUserId)
            }
        )
        Unit
    }

    suspend fun removeMemberFromGroup(chatId: String, targetUserId: String): Result<Unit> = runCatching {
        postgrest.rpc(
            function = "remove_group_member",
            parameters = buildJsonObject {
                put("_chat_id", chatId)
                put("_target_user_id", targetUserId)
            }
        )
        Unit
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
                message = buildJsonObject {
                    put("chat_id", chatId)
                    put("user_id", userId)
                    put("display_name", displayName)
                    put("is_typing", isTyping)
                }
            )
        }
    }

    suspend fun sendTextMessage(
        chatId: String,
        content: String,
        replyToMessageId: String? = null,
        whisperTargetId: String? = null,
        destructSeconds: Int? = null
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
                if (destructSeconds != null && destructSeconds > 0) put("destruct_seconds", destructSeconds)
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
        whisperTargetId: String? = null,
        destructSeconds: Int? = null
    ): Result<MessageDto> = runCatching {
        val senderId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")

        val compressedBytes = withContext(Dispatchers.IO) {
            compressImageUriToJpeg(imageUri)
        }

        val objectPath = "$senderId/img_${UUID.randomUUID()}.jpg"
        val bucket = storage.from("chat-media")
        bucket.upload(path = objectPath, data = compressedBytes) {
            upsert = false
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
                if (destructSeconds != null && destructSeconds > 0) put("destruct_seconds", destructSeconds)
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
            upsert = false
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

    suspend fun toggleReaction(messageId: String, emoji: String): Result<Unit> = runCatching {
        postgrest.rpc(
            function = "toggle_message_reaction",
            parameters = buildJsonObject {
                put("_message_id", messageId)
                put("_emoji", emoji)
            }
        )
        Unit
    }

    suspend fun editMessage(messageId: String, newContent: String): Result<Unit> = runCatching {
        postgrest.rpc(
            function = "edit_message_content",
            parameters = buildJsonObject {
                put("_message_id", messageId)
                put("_new_content", newContent.trim())
            }
        )
        Unit
    }

    suspend fun expireSelfDestructMessage(messageId: String): Result<Unit> = runCatching {
        postgrest.rpc(
            function = "expire_destruct_message",
            parameters = buildJsonObject {
                put("_message_id", messageId)
            }
        )
        Unit
    }

    suspend fun markVoiceNotePlayed(messageId: String): Result<Unit> = runCatching {
        postgrest.from("messages").update(
            buildJsonObject {
                put("voice_played", true)
            }
        ) {
            filter { eq("id", messageId) }
        }
        Unit
    }

    suspend fun deleteMessageForEveryone(messageId: String): Result<Unit> = runCatching {
        postgrest.rpc(
            function = "delete_message_for_everyone",
            parameters = buildJsonObject {
                put("_message_id", messageId)
            }
        )
        Unit
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
        Unit
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
""",

    "app/src/main/java/com/virusx/app/ui/chat/ChatViewModel.kt": """package com.virusx.app.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.local.entity.MessageEntity
import com.virusx.app.data.remote.dto.ChatSyncSessionDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.repository.AuthRepository
import com.virusx.app.data.repository.ChatDetailRepository
import com.virusx.app.data.repository.ChatRepository
import com.virusx.app.util.VoiceNoteManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChatUiState(
    val chatId: String = "",
    val chatTitle: String = "محادثة VirusX",
    val isGroup: Boolean = false,
    val chatCreatorId: String? = null,
    val peerOnline: Boolean = false,
    val members: List<ProfileDto> = emptyList(),
    val memberSearchQuery: String = "",
    val memberSearchResults: List<ProfileDto> = emptyList(),
    val currentUserId: String? = null,
    val currentUserName: String = "عضو",
    val inputText: String = "",
    val replyToMessage: MessageEntity? = null,
    val editingMessage: MessageEntity? = null,
    val whisperTargetUser: ProfileDto? = null,
    val destructTimerSeconds: Int? = null,
    val selectedPitchFilter: String = "normal",
    val voiceViewOnce: Boolean = false,
    val isSending: Boolean = false,
    val statusBanner: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatDetailRepository: ChatDetailRepository,
    private val chatRepository: ChatRepository,
    private val voiceNoteManager: VoiceNoteManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    val activeSyncSession: StateFlow<ChatSyncSessionDto?> = chatDetailRepository.activeSyncSession
    val typingUsers: StateFlow<Map<String, String>> = chatDetailRepository.typingUsers

    val isRecording: StateFlow<Boolean> = voiceNoteManager.isRecording
    val liveWaveform: StateFlow<List<Float>> = voiceNoteManager.liveWaveform
    val recordingDurationSeconds: StateFlow<Int> = voiceNoteManager.recordingDurationSeconds
    val currentlyPlayingMessageId: StateFlow<String?> = voiceNoteManager.currentlyPlayingMessageId
    val isTranscribing: StateFlow<Boolean> = voiceNoteManager.isTranscribing

    private var messagesObserveJob: Job? = null
    private var typingResetJob: Job? = null
    private var memberSearchJob: Job? = null
    private var initializedChatId: String? = null
    private var lastScreenshotAlertMs: Long = 0L

    fun initChat(chatId: String) {
        if (initializedChatId == chatId) return
        initializedChatId = chatId

        val myId = chatDetailRepository.getCurrentUserId()
        _uiState.update { it.copy(chatId = chatId, currentUserId = myId) }

        messagesObserveJob?.cancel()
        messagesObserveJob = viewModelScope.launch {
            chatDetailRepository.observeLocalMessages(chatId).collect { list ->
                _messages.value = list
            }
        }

        viewModelScope.launch {
            val myProfile = authRepository.refreshCurrentUserProfile()
            refreshChatMembers(chatId, myId, myProfile?.displayName ?: "عضو")
            chatDetailRepository.fetchInitialMessagesAndSyncSession(chatId)
            chatDetailRepository.subscribeToChatRealtime(chatId, viewModelScope)
        }
    }

    private suspend fun refreshChatMembers(chatId: String, myId: String?, fallbackName: String) {
        val (chatDto, memberProfiles) = chatDetailRepository.fetchChatDetailsAndMembers(chatId)
        val isGroupChat = chatDto?.type == "group"
        val peer = if (!isGroupChat) memberProfiles.firstOrNull { it.id != myId } else null
        val title = if (isGroupChat) {
            chatDto?.name ?: "مجموعة VirusX"
        } else {
            peer?.displayName ?: chatDto?.name ?: "محادثة خاصة"
        }

        _uiState.update {
            it.copy(
                chatTitle = title,
                isGroup = isGroupChat,
                chatCreatorId = chatDto?.createdBy,
                peerOnline = peer?.isOnline ?: false,
                members = memberProfiles,
                currentUserName = fallbackName
            )
        }
    }

    fun onMemberSearchChange(query: String) {
        _uiState.update { it.copy(memberSearchQuery = query) }
        memberSearchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(memberSearchResults = emptyList()) }
            return
        }
        memberSearchJob = viewModelScope.launch {
            delay(250L)
            val existingIds = _uiState.value.members.map { it.id }.toSet()
            val found = chatRepository.searchUsersByNameUsernameOrPhone(query)
                .filter { it.id !in existingIds }
            _uiState.update { it.copy(memberSearchResults = found) }
        }
    }

    fun addMemberToGroup(user: ProfileDto) {
        val chatId = _uiState.value.chatId
        if (chatId.isBlank()) return
        viewModelScope.launch {
            chatDetailRepository.addMemberToGroup(chatId, user.id).fold(
                onSuccess = {
                    refreshChatMembers(chatId, _uiState.value.currentUserId, _uiState.value.currentUserName)
                    _uiState.update {
                        it.copy(
                            memberSearchQuery = "",
                            memberSearchResults = emptyList(),
                            statusBanner = "✅ تمت إضافة ${user.displayName} إلى المجموعة"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = cleanError(err, "تعذر إضافة العضو")) }
                }
            )
        }
    }

    fun removeMemberFromGroup(user: ProfileDto) {
        val chatId = _uiState.value.chatId
        if (chatId.isBlank()) return
        viewModelScope.launch {
            chatDetailRepository.removeMemberFromGroup(chatId, user.id).fold(
                onSuccess = {
                    refreshChatMembers(chatId, _uiState.value.currentUserId, _uiState.value.currentUserName)
                    _uiState.update { it.copy(statusBanner = "تمت إزالة ${user.displayName} من المجموعة") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = cleanError(err, "تعذر إزالة العضو")) }
                }
            )
        }
    }

    fun onInputTextChange(newText: String) {
        _uiState.update { it.copy(inputText = newText, errorMessage = null) }
        val state = _uiState.value
        if (state.chatId.isBlank()) return

        typingResetJob?.cancel()
        typingResetJob = viewModelScope.launch {
            chatDetailRepository.sendTypingIndicator(state.chatId, state.currentUserName, newText.isNotBlank())
            if (newText.isNotBlank()) {
                delay(2500L)
                chatDetailRepository.sendTypingIndicator(state.chatId, state.currentUserName, false)
            }
        }
    }

    fun setReplyToMessage(message: MessageEntity?) {
        _uiState.update { it.copy(replyToMessage = message, editingMessage = null) }
    }

    fun startEditingMessage(message: MessageEntity) {
        _uiState.update {
            it.copy(
                editingMessage = message,
                replyToMessage = null,
                inputText = message.content.orEmpty()
            )
        }
    }

    fun cancelEditingMessage() {
        _uiState.update { it.copy(editingMessage = null, inputText = "") }
    }

    fun cycleDestructTimer() {
        val next = when (_uiState.value.destructTimerSeconds) {
            null -> 10
            10 -> 30
            30 -> 60
            else -> null
        }
        _uiState.update { it.copy(destructTimerSeconds = next) }
    }

    fun setWhisperTarget(profile: ProfileDto?) {
        _uiState.update { it.copy(whisperTargetUser = profile) }
    }

    fun setPitchFilter(filter: String) {
        _uiState.update { it.copy(selectedPitchFilter = filter) }
    }

    fun toggleVoiceViewOnce() {
        _uiState.update { it.copy(voiceViewOnce = !it.voiceViewOnce) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, statusBanner = null) }
    }

    fun sendTextMessage() {
        val state = _uiState.value
        val content = state.inputText.trim()
        if (content.isEmpty() || state.isSending || state.chatId.isBlank()) return

        val editingTarget = state.editingMessage
        if (editingTarget != null) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSending = true, inputText = "", editingMessage = null) }
                chatDetailRepository.editMessage(editingTarget.id, content).fold(
                    onSuccess = {
                        chatDetailRepository.fetchInitialMessagesAndSyncSession(state.chatId)
                        _uiState.update { it.copy(isSending = false) }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                inputText = content,
                                errorMessage = cleanError(err, "فشل تعديل الرسالة")
                            )
                        }
                    }
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, inputText = "") }
            chatDetailRepository.sendTypingIndicator(state.chatId, state.currentUserName, false)

            chatDetailRepository.sendTextMessage(
                chatId = state.chatId,
                content = content,
                replyToMessageId = state.replyToMessage?.id,
                whisperTargetId = state.whisperTargetUser?.id,
                destructSeconds = state.destructTimerSeconds
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            replyToMessage = null,
                            whisperTargetUser = null,
                            destructTimerSeconds = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            inputText = content,
                            errorMessage = cleanError(err, "فشل إرسال الرسالة")
                        )
                    }
                }
            )
        }
    }

    fun toggleMessageReaction(messageId: String, emoji: String) {
        val chatId = _uiState.value.chatId
        viewModelScope.launch {
            chatDetailRepository.toggleReaction(messageId, emoji).onSuccess {
                if (chatId.isNotBlank()) {
                    chatDetailRepository.fetchInitialMessagesAndSyncSession(chatId)
                }
            }
        }
    }

    fun expireSelfDestructMessage(messageId: String) {
        viewModelScope.launch {
            chatDetailRepository.expireSelfDestructMessage(messageId)
        }
    }

    fun reportScreenshotTaken() {
        val now = System.currentTimeMillis()
        if (now - lastScreenshotAlertMs < 5000L) return
        lastScreenshotAlertMs = now
        val state = _uiState.value
        if (state.chatId.isBlank()) return
        viewModelScope.launch {
            chatDetailRepository.sendTextMessage(
                chatId = state.chatId,
                content = "[SCREENSHOT_ALERT] ⚠️ قام ${state.currentUserName} بأخذ لقطة شاشة لهذه المحادثة!"
            )
        }
    }

    fun sendImageMessage(uri: Uri) {
        val state = _uiState.value
        if (state.isSending || state.chatId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            chatDetailRepository.sendImageMessage(
                chatId = state.chatId,
                imageUri = uri,
                caption = state.inputText.takeIf { it.isNotBlank() },
                replyToMessageId = state.replyToMessage?.id,
                whisperTargetId = state.whisperTargetUser?.id,
                destructSeconds = state.destructTimerSeconds
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            inputText = "",
                            replyToMessage = null,
                            whisperTargetUser = null,
                            destructTimerSeconds = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = cleanError(err, "فشل رفع الصورة")
                        )
                    }
                }
            )
        }
    }

    fun startVoiceRecording() {
        voiceNoteManager.startRecording(viewModelScope).onFailure { err ->
            _uiState.update {
                it.copy(errorMessage = cleanError(err, "تعذر بدء التسجيل الصوتى"))
            }
        }
    }

    fun cancelVoiceRecording() {
        val recorded = voiceNoteManager.stopRecording()
        recorded?.file?.delete()
    }

    fun stopAndSendVoiceNote() {
        val state = _uiState.value
        val recorded = voiceNoteManager.stopRecording() ?: return
        if (state.chatId.isBlank()) {
            recorded.file.delete()
            return
        }

        val attachedTranscript = state.inputText.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            chatDetailRepository.sendVoiceNoteMessage(
                chatId = state.chatId,
                audioFile = recorded.file,
                durationSeconds = recorded.durationSeconds,
                waveform = recorded.waveform,
                viewOnce = state.voiceViewOnce,
                pitchFilter = state.selectedPitchFilter,
                transcript = attachedTranscript,
                replyToMessageId = state.replyToMessage?.id,
                whisperTargetId = state.whisperTargetUser?.id
            ).fold(
                onSuccess = {
                    recorded.file.delete()
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            inputText = "",
                            replyToMessage = null,
                            whisperTargetUser = null,
                            voiceViewOnce = false,
                            selectedPitchFilter = "normal"
                        )
                    }
                },
                onFailure = { err ->
                    recorded.file.delete()
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = cleanError(err, "فشل إرسال الرسالة الصوتية")
                        )
                    }
                }
            )
        }
    }

    fun transcribeSpeechIntoInput() {
        voiceNoteManager.recognizeSpeechToText(
            onResult = { recognized ->
                _uiState.update { current ->
                    val combined = if (current.inputText.isBlank()) recognized else "${current.inputText} $recognized"
                    current.copy(inputText = combined)
                }
            },
            onError = { errMsg ->
                _uiState.update { it.copy(errorMessage = errMsg) }
            }
        )
    }

    fun togglePlayVoiceNote(message: MessageEntity) {
        val myId = _uiState.value.currentUserId
        if (message.voiceViewOnce && message.voicePlayed && message.senderId != myId) {
            _uiState.update {
                it.copy(errorMessage = "🔒 هذه الرسالة الصوتية للاستماع مرة واحدة فقط وقد تم تشغيلها بالفعل")
            }
            return
        }

        if (currentlyPlayingMessageId.value == message.id) {
            voiceNoteManager.stopPlayback()
            return
        }

        val url = message.mediaUrl
        if (url.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "رابط الملف الصوتى غير متوفر") }
            return
        }

        voiceNoteManager.playVoiceNote(
            messageId = message.id,
            mediaUrl = url,
            pitchFilter = message.voicePitchFilter
        ) { finishedMsgId ->
            if (message.voiceViewOnce && message.senderId != myId) {
                viewModelScope.launch {
                    chatDetailRepository.markVoiceNotePlayed(finishedMsgId)
                }
            }
        }
    }

    fun deleteMessageForEveryone(messageId: String) {
        viewModelScope.launch {
            chatDetailRepository.deleteMessageForEveryone(messageId).onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = cleanError(err, "تعذر حذف الرسالة"))
                }
            }
        }
    }

    fun startSyncSession(mediaUrl: String, mediaType: String) {
        val chatId = _uiState.value.chatId
        if (chatId.isBlank() || mediaUrl.isBlank()) return
        viewModelScope.launch {
            chatDetailRepository.startSyncMediaSession(chatId, mediaUrl, mediaType).onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = cleanError(err, "تعذر بدء جلسة المشغل المتزامن"))
                }
            }
        }
    }

    fun toggleSyncSessionPlayPause() {
        val session = activeSyncSession.value ?: return
        viewModelScope.launch {
            chatDetailRepository.updateSyncSessionPlayback(
                sessionId = session.id,
                positionSeconds = session.positionSeconds,
                isPlaying = !session.isPlaying
            )
        }
    }

    fun seekSyncSessionBy(deltaSeconds: Double) {
        val session = activeSyncSession.value ?: return
        val nextPos = (session.positionSeconds + deltaSeconds).coerceAtLeast(0.0)
        viewModelScope.launch {
            chatDetailRepository.updateSyncSessionPlayback(
                sessionId = session.id,
                positionSeconds = nextPos,
                isPlaying = session.isPlaying
            )
        }
    }

    private fun cleanError(err: Throwable, fallback: String): String {
        val raw = err.localizedMessage ?: return fallback
        return raw.substringBefore("URL:").substringBefore("Headers:").trim().ifBlank { fallback }
    }

    override fun onCleared() {
        voiceNoteManager.stopPlayback()
        voiceNoteManager.stopRecording()
        viewModelScope.launch {
            withContext(NonCancellable) {
                chatDetailRepository.unsubscribeFromChatRealtime()
            }
        }
        super.onCleared()
    }
}
""",

    "app/src/main/java/com/virusx/app/ui/chat/ChatScreen.kt": """package com.virusx.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.virusx.app.data.local.entity.MessageEntity
import com.virusx.app.data.remote.dto.ChatSyncSessionDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.ui.theme.DangerRed
import com.virusx.app.ui.theme.OnlineGreen
import com.virusx.app.ui.theme.ReceivedBubbleColor
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
import com.virusx.app.ui.theme.VirusXWhisperSurface
import com.virusx.app.ui.theme.WarningAmber
import com.virusx.app.ui.theme.WhisperMessageGradient
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val activeSyncSession by viewModel.activeSyncSession.collectAsState()
    val typingUsers by viewModel.typingUsers.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val liveWaveform by viewModel.liveWaveform.collectAsState()
    val recordingDuration by viewModel.recordingDurationSeconds.collectAsState()
    val playingMessageId by viewModel.currentlyPlayingMessageId.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()

    var showWhisperTargetDialog by remember { mutableStateOf(false) }
    var showStartSyncDialog by remember { mutableStateOf(false) }
    var showGroupMembersDialog by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var selectedMessageForActions by remember { mutableStateOf<MessageEntity?>(null) }

    val listState = rememberLazyListState()

    DisposableEffect(context, chatId) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (uri != null && uri.toString().contains(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())) {
                    runCatching {
                        val projection = arrayOf(
                            MediaStore.Images.Media.DISPLAY_NAME,
                            MediaStore.Images.Media.RELATIVE_PATH,
                            MediaStore.Images.Media.DATE_ADDED
                        )
                        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val name = cursor.getString(0).orEmpty().lowercase()
                                val path = cursor.getString(1).orEmpty().lowercase()
                                val addedSec = cursor.getLong(2)
                                val nowSec = System.currentTimeMillis() / 1000L
                                if ((name.contains("screenshot") || path.contains("screenshot")) &&
                                    kotlin.math.abs(nowSec - addedSec) <= 5L
                                ) {
                                    viewModel.reportScreenshotTaken()
                                }
                            }
                        }
                    }
                }
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        }
        onDispose {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }

    LaunchedEffect(chatId) {
        viewModel.initChat(chatId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceRecording()
    }

    val speechPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.transcribeSpeechIntoInput()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.sendImageMessage(uri)
    }

    val memberMap = remember(state.members) { state.members.associateBy { it.id } }
    val messageMap = remember(messages) { messages.associateBy { it.id } }

    Scaffold(
        containerColor = VirusXBackground,
        topBar = {
            ChatTopBar(
                title = state.chatTitle,
                isGroup = state.isGroup,
                memberCount = state.members.size,
                peerOnline = state.peerOnline,
                typingUsers = typingUsers.values.toList(),
                onBack = onBack,
                onOpenGroupMembers = { showGroupMembersDialog = true },
                onOpenSyncDialog = { showStartSyncDialog = true }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            activeSyncSession?.let { session ->
                SyncMiniPlayerBar(
                    session = session,
                    onTogglePlayPause = viewModel::toggleSyncSessionPlayPause,
                    onSeekBackward = { viewModel.seekSyncSessionBy(-10.0) },
                    onSeekForward = { viewModel.seekSyncSessionBy(10.0) }
                )
            }

            state.statusBanner?.let { banner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OnlineGreen.copy(alpha = 0.2f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(banner, color = OnlineGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::clearError, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(15.dp))
                    }
                }
            }

            state.errorMessage?.let { err ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DangerRed.copy(alpha = 0.2f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(err, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::clearError, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == state.currentUserId
                    val senderProfile = memberMap[msg.senderId]
                    val whisperTargetProfile = msg.whisperTargetId?.let { memberMap[it] }
                    val repliedMessage = msg.replyToMessageId?.let { messageMap[it] }

                    MessageBubbleItem(
                        message = msg,
                        isMine = isMine,
                        isGroup = state.isGroup,
                        currentUserId = state.currentUserId,
                        senderName = senderProfile?.displayName ?: "عضو",
                        whisperTargetName = whisperTargetProfile?.displayName,
                        repliedMessage = repliedMessage,
                        isPlayingVoice = playingMessageId == msg.id,
                        onPlayVoice = { viewModel.togglePlayVoiceNote(msg) },
                        onOpenFullImage = { url -> fullScreenImageUrl = url },
                        onExpireSelfDestruct = { viewModel.expireSelfDestructMessage(msg.id) },
                        onToggleReaction = { emoji -> viewModel.toggleMessageReaction(msg.id, emoji) },
                        onSwipeReply = { viewModel.setReplyToMessage(msg) },
                        onLongPress = { selectedMessageForActions = msg }
                    )
                }
            }

            ChatComposerSection(
                state = state,
                isRecording = isRecording,
                liveWaveform = liveWaveform,
                recordingDuration = recordingDuration,
                isTranscribing = isTranscribing,
                onInputChange = viewModel::onInputTextChange,
                onSendText = viewModel::sendTextMessage,
                onPickImage = { imagePickerLauncher.launch("image/*") },
                onOpenWhisperPicker = { showWhisperTargetDialog = true },
                onCycleDestructTimer = viewModel::cycleDestructTimer,
                onClearReply = { viewModel.setReplyToMessage(null) },
                onCancelEdit = viewModel::cancelEditingMessage,
                onClearWhisper = { viewModel.setWhisperTarget(null) },
                onSelectPitchFilter = viewModel::setPitchFilter,
                onToggleViewOnce = viewModel::toggleVoiceViewOnce,
                onStartRecording = {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) viewModel.startVoiceRecording()
                    else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onCancelRecording = viewModel::cancelVoiceRecording,
                onSendVoice = viewModel::stopAndSendVoiceNote,
                onSpeechToText = {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) viewModel.transcribeSpeechIntoInput()
                    else speechPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )
        }
    }

    fullScreenImageUrl?.let { imgUrl ->
        Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { fullScreenImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imgUrl,
                    contentDescription = "عرض الصورة كاملة",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
                IconButton(
                    onClick = { fullScreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(VirusXSurface)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextPrimary)
                }
            }
        }
    }

    if (showGroupMembersDialog && state.isGroup) {
        GroupMembersDialog(
            members = state.members,
            currentUserId = state.currentUserId,
            creatorId = state.chatCreatorId,
            searchQuery = state.memberSearchQuery,
            searchResults = state.memberSearchResults,
            onSearchChange = viewModel::onMemberSearchChange,
            onAddMember = viewModel::addMemberToGroup,
            onRemoveMember = viewModel::removeMemberFromGroup,
            onDismiss = { showGroupMembersDialog = false }
        )
    }

    if (showWhisperTargetDialog) {
        val otherMembers = state.members.filter { it.id != state.currentUserId }
        AlertDialog(
            onDismissRequest = { showWhisperTargetDialog = false },
            containerColor = VirusXSurface,
            title = { Text("🔒 إرسال همسة سرية داخل الجروب", color = TextPrimary) },
            text = {
                if (otherMembers.isEmpty()) {
                    Text("لا يوجد أعضاء آخرون في المجموعة حالياً. اضغط على أيقونة إضافة الأعضاء في الأعلى لإضافة أصدقائك.", color = TextSecondary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "اختر العضو الذي سيشاهد محتوى الهمسة:",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        otherMembers.forEach { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VirusXSurfaceElevated)
                                    .clickable {
                                        viewModel.setWhisperTarget(member)
                                        showWhisperTargetDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(member.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("@${member.username}", color = VirusXSecondaryWhisper, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWhisperTargetDialog = false }) {
                    Text("إغلاق", color = TextSecondary)
                }
            }
        )
    }

    if (showStartSyncDialog) {
        StartSyncSessionDialog(
            onDismiss = { showStartSyncDialog = false },
            onStart = { url, type ->
                viewModel.startSyncSession(url, type)
                showStartSyncDialog = false
            }
        )
    }

    selectedMessageForActions?.let { targetMsg ->
        val isMine = targetMsg.senderId == state.currentUserId
        AlertDialog(
            onDismissRequest = { selectedMessageForActions = null },
            containerColor = VirusXSurface,
            title = { Text("خيارات وتفاعل سريع", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!targetMsg.isDeleted) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(VirusXSurfaceElevated)
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf("❤️", "😂", "😮", "😢", "🔥", "👍").forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 22.sp,
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.toggleMessageReaction(targetMsg.id, emoji)
                                            selectedMessageForActions = null
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            viewModel.setReplyToMessage(targetMsg)
                            selectedMessageForActions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = VirusXSecondaryWhisper)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("رد مقتبس على الرسالة", color = TextPrimary)
                    }

                    if (isMine && !targetMsg.isDeleted && targetMsg.messageType == "text") {
                        TextButton(
                            onClick = {
                                viewModel.startEditingMessage(targetMsg)
                                selectedMessageForActions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = VirusXSecondaryWhisper)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تعديل نص الرسالة", color = TextPrimary)
                        }
                    }

                    if (isMine && !targetMsg.isDeleted) {
                        TextButton(
                            onClick = {
                                viewModel.deleteMessageForEveryone(targetMsg.id)
                                selectedMessageForActions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حذف الرسالة لدى الجميع", color = DangerRed)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForActions = null }) {
                    Text("إلغاء", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    isGroup: Boolean,
    memberCount: Int,
    peerOnline: Boolean,
    typingUsers: List<String>,
    onBack: () -> Unit,
    onOpenGroupMembers: () -> Unit,
    onOpenSyncDialog: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VirusXSurface)
            .statusBarsPadding()
            .border(0.5.dp, VirusXGlassBorder)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "رجوع",
                tint = TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = isGroup, onClick = onOpenGroupMembers)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when {
                typingUsers.isNotEmpty() -> "✍️ ${typingUsers.joinToString(", ")} يكتب الآن..."
                isGroup -> "$memberCount أعضاء • اضغط لإضافة أصدقاء"
                peerOnline -> "متصل الآن"
                else -> "غير متصل"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    typingUsers.isNotEmpty() -> VirusXSecondaryWhisper
                    peerOnline -> OnlineGreen
                    isGroup -> VirusXSecondaryWhisper
                    else -> TextSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isGroup) {
            IconButton(onClick = onOpenGroupMembers) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "إضافة أعضاء للمجموعة",
                    tint = OnlineGreen
                )
            }
        }

        IconButton(onClick = onOpenSyncDialog) {
            Icon(
                imageVector = Icons.Default.Podcasts,
                contentDescription = "تشغيل متزامن مشترك",
                tint = VirusXSecondaryWhisper
            )
        }
    }
}

@Composable
private fun GroupMembersDialog(
    members: List<ProfileDto>,
    currentUserId: String?,
    creatorId: String?,
    searchQuery: String,
    searchResults: List<ProfileDto>,
    onSearchChange: (String) -> Unit,
    onAddMember: (ProfileDto) -> Unit,
    onRemoveMember: (ProfileDto) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VirusXSurface,
        title = {
            Text("أعضاء المجموعة (${members.size}/50)", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("ابحث لإضافة صديق (بالاسم / @ / الهاتف)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (searchResults.isNotEmpty()) {
                    Text("نتائج البحث (اضغط للإضافة):", color = OnlineGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    searchResults.forEach { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VirusXSurfaceElevated)
                                .clickable { onAddMember(candidate) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(candidate.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("@${candidate.username} • ${candidate.phoneNumber ?: ""}", color = VirusXSecondaryWhisper, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.PersonAdd, contentDescription = "إضافة", tint = OnlineGreen)
                        }
                    }
                }

                Text("الأعضاء الحاليون:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                members.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VirusXSurfaceElevated)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (member.id == creatorId) "${member.displayName} (المشرف)" else member.displayName,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text("@${member.username}", color = TextMuted, fontSize = 11.sp)
                        }
                        if (currentUserId == creatorId && member.id != currentUserId) {
                            IconButton(onClick = { onRemoveMember(member) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.PersonRemove, contentDescription = "إزالة", tint = DangerRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("تم", color = VirusXSecondaryWhisper, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SyncMiniPlayerBar(
    session: ChatSyncSessionDto,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit
) {
    val context = LocalContext.current
    val syncPlayer = remember(session.mediaUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(session.mediaUrl))
            prepare()
        }
    }

    DisposableEffect(syncPlayer) {
        onDispose {
            runCatching {
                syncPlayer.stop()
                syncPlayer.release()
            }
        }
    }

    LaunchedEffect(session.isPlaying, session.positionSeconds) {
        val targetMs = (session.positionSeconds * 1000).toLong()
        if (kotlin.math.abs(syncPlayer.currentPosition - targetMs) > 2500L) {
            syncPlayer.seekTo(targetMs)
        }
        if (session.isPlaying && !syncPlayer.isPlaying) {
            syncPlayer.play()
        } else if (!session.isPlaying && syncPlayer.isPlaying) {
            syncPlayer.pause()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VirusXWhisperSurface)
            .border(1.dp, VirusXSecondaryWhisper.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Podcasts, contentDescription = null, tint = VirusXSecondaryWhisper, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("المشغل المتزامن المباشر (${session.mediaType})", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(session.mediaUrl, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onSeekBackward, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Replay10, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (session.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = VirusXSecondaryWhisper
            )
        }
        IconButton(onClick = onSeekForward, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Forward10, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MessageBubbleItem(
    message: MessageEntity,
    isMine: Boolean,
    isGroup: Boolean,
    currentUserId: String?,
    senderName: String,
    whisperTargetName: String?,
    repliedMessage: MessageEntity?,
    isPlayingVoice: Boolean,
    onPlayVoice: () -> Unit,
    onOpenFullImage: (String) -> Unit,
    onExpireSelfDestruct: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onSwipeReply: () -> Unit,
    onLongPress: () -> Unit
) {
    val rawContent = message.content.orEmpty()
    if (rawContent.startsWith("[SCREENSHOT_ALERT]")) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rawContent.removePrefix("[SCREENSHOT_ALERT]").trim(),
                color = WarningAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(WarningAmber.copy(alpha = 0.14f))
                    .border(1.dp, WarningAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        return
    }

    val destructSec = message.destructSeconds
    var remainingSeconds by remember(message.id, destructSec, message.isDeleted) {
        mutableIntStateOf(if (!message.isDeleted && destructSec != null && destructSec > 0) destructSec else 0)
    }
    LaunchedEffect(message.id, destructSec, message.isDeleted) {
        if (!message.isDeleted && destructSec != null && destructSec > 0) {
            var left = destructSec
            while (left > 0) {
                remainingSeconds = left
                delay(1000L)
                left--
            }
            remainingSeconds = 0
            onExpireSelfDestruct()
        }
    }

    val reactionData = remember(message.reactionsJson) {
        val emojis = ArrayList<String>()
        val counts = ArrayList<Int>()
        val mineFlags = ArrayList<Boolean>()
        runCatching {
            val raw = message.reactionsJson
            if (!raw.isNullOrBlank()) {
                val obj = JSONObject(raw)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = obj.optJSONArray(k)
                    if (arr != null && arr.length() > 0) {
                        emojis.add(k)
                        counts.add(arr.length())
                        val hasMe = (0 until arr.length()).any { idx -> arr.getString(idx) == currentUserId }
                        mineFlags.add(hasMe)
                    }
                }
            }
        }
        Triple(emojis, counts, mineFlags)
    }

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val isWhisper = message.whisperTargetId != null
    val canReadWhisper = !isWhisper || isMine || message.whisperTargetId == currentUserId

    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(dragOffset) > 60f) onSwipeReply()
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> dragOffset += dragAmount }
                )
            },
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 96.dp, max = 285.dp)
                .clip(bubbleShape)
                .then(
                    when {
                        isWhisper -> Modifier
                            .background(VirusXWhisperSurface)
                            .border(1.5.dp, VirusXSecondaryWhisper, bubbleShape)
                        isMine -> Modifier.background(SentMessageGradient)
                        else -> Modifier
                            .background(ReceivedBubbleColor)
                            .border(1.dp, VirusXGlassBorder, bubbleShape)
                    }
                )
                .combinedClickable(
                    onClick = {
                        val urlToOpen = message.mediaUrl
                        if (message.messageType == "image" && !urlToOpen.isNullOrBlank() && !message.isDeleted && canReadWhisper) {
                            onOpenFullImage(urlToOpen)
                        }
                    },
                    onLongClick = onLongPress
                )
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            if (isGroup && !isMine) {
                Text(
                    text = senderName,
                    color = VirusXSecondaryWhisper,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            if (isWhisper) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(WhisperMessageGradient)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (canReadWhisper) "همسة سرية إلى ${whisperTargetName ?: "عضو"}" else "همسة مشفرة لعضو آخر",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
            }

            if (repliedMessage != null) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(7.dp)
                ) {
                    Text("↩️ رد على رسالة:", color = VirusXSecondaryWhisper, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (repliedMessage.isDeleted) "رسالة محذوفة" else (repliedMessage.content ?: "مرفق"),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
            }

            when {
                message.isDeleted -> {
                    Text(
                        text = rawContent.ifBlank { "🚫 تم حذف هذه الرسالة" },
                        color = TextMuted,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp
                    )
                }
                !canReadWhisper -> {
                    Text(
                        text = "🔒 محتوى هذه الهمسة مشفر ولا يظهر إلا للمرسل والمستقبل المحدد.",
                        color = TextSecondary,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp
                    )
                }
                message.messageType == "image" -> {
                    val imgUrl = message.mediaUrl
                    if (!imgUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = imgUrl,
                            contentDescription = "صورة مرسلة - اضغط للتكبير",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenFullImage(imgUrl) }
                        )
                        Text(
                            text = "🔍 اضغط لعرض الصورة كاملة",
                            color = VirusXSecondaryWhisper,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    if (!message.content.isNullOrBlank() && message.content != "📷 صورة") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = message.content, color = TextPrimary, fontSize = 14.sp)
                    }
                }
                message.messageType == "voice" -> {
                    VoiceNoteBubbleContent(
                        message = message,
                        isMine = isMine,
                        isPlaying = isPlayingVoice,
                        onPlayToggle = onPlayVoice
                    )
                }
                else -> {
                    Text(
                        text = rawContent,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }
            }

            if (!message.isDeleted) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (remainingSeconds > 0) {
                        Text(
                            text = "⏳ ${remainingSeconds}ث",
                            color = WarningAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!message.editedAt.isNullOrBlank()) {
                        Text(
                            text = "(معدّلة)",
                            color = TextPrimary.copy(alpha = 0.65f),
                            fontSize = 10.sp
                        )
                    }
                    if (isMine) {
                        Text(
                            text = if (message.status == "read") "✓✓ مقروءة" else "✓ أُرسلت",
                            color = if (message.status == "read") VirusXSecondaryWhisper else TextPrimary.copy(alpha = 0.75f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        val (rEmojis, rCounts, rMine) = reactionData
        if (rEmojis.isNotEmpty() && !message.isDeleted) {
            FlowRow(
                modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rEmojis.indices.forEach { idx ->
                    val emoji = rEmojis[idx]
                    val count = rCounts[idx]
                    val reactedByMe = rMine[idx]
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (reactedByMe) VirusXPrimary.copy(alpha = 0.35f) else VirusXSurfaceElevated)
                            .border(1.dp, if (reactedByMe) VirusXSecondaryWhisper else VirusXGlassBorder, CircleShape)
                            .clickable { onToggleReaction(emoji) }
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emoji, fontSize = 12.sp)
                        if (count > 1) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("$count", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceNoteBubbleContent(
    message: MessageEntity,
    isMine: Boolean,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit
) {
    val expiredViewOnce = message.voiceViewOnce && message.voicePlayed && !isMine
    var showTranscript by remember { mutableStateOf(false) }

    val waveformBars = remember(message.voiceWaveformJson) {
        message.voiceWaveformJson
            ?.split(",")
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: List(22) { 0.35f }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onPlayToggle,
                enabled = !expiredViewOnce,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.25f))
            ) {
                Icon(
                    imageVector = when {
                        expiredViewOnce -> Icons.Default.Lock
                        isPlaying -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    tint = TextPrimary
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                waveformBars.take(24).forEach { amp ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((amp.coerceIn(0.15f, 1f) * 26).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isPlaying) VirusXSecondaryWhisper else TextPrimary.copy(alpha = 0.8f))
                    )
                }
            }

            Text(
                text = "${message.voiceDurationSeconds ?: 0}ث",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!message.voicePitchFilter.isNullOrBlank() && message.voicePitchFilter != "normal") {
                Text("🎛️ فلتر: ${message.voicePitchFilter}", color = VirusXSecondaryWhisper, fontSize = 10.sp)
            }
            if (message.voiceViewOnce) {
                Text(
                    text = if (expiredViewOnce) "🔒 تم الاستماع (مرة واحدة)" else "🔥 استماع مرة واحدة",
                    color = if (expiredViewOnce) TextMuted else VirusXSecondaryWhisper,
                    fontSize = 10.sp
                )
            }
            if (!message.voiceTranscript.isNullOrBlank() && !expiredViewOnce) {
                Text(
                    text = if (showTranscript) "إخفاء النص ▲" else "عرض النص (أ ب) ▼",
                    color = VirusXSecondaryWhisper,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier.clickable { showTranscript = !showTranscript }
                )
            }
        }

        if (showTranscript && !message.voiceTranscript.isNullOrBlank() && !expiredViewOnce) {
            Text(
                text = "📝 ${message.voiceTranscript}",
                color = TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun ChatComposerSection(
    state: ChatUiState,
    isRecording: Boolean,
    liveWaveform: List<Float>,
    recordingDuration: Int,
    isTranscribing: Boolean,
    onInputChange: (String) -> Unit,
    onSendText: () -> Unit,
    onPickImage: () -> Unit,
    onOpenWhisperPicker: () -> Unit,
    onCycleDestructTimer: () -> Unit,
    onClearReply: () -> Unit,
    onCancelEdit: () -> Unit,
    onClearWhisper: () -> Unit,
    onSelectPitchFilter: (String) -> Unit,
    onToggleViewOnce: () -> Unit,
    onStartRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSendVoice: () -> Unit,
    onSpeechToText: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VirusXSurface)
            .border(0.5.dp, VirusXGlassBorder)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        state.editingMessage?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VirusXPrimary.copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = VirusXSecondaryWhisper, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("جاري تعديل الرسالة...", color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onCancelEdit, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                }
            }
        }

        state.replyToMessage?.let { replyMsg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VirusXSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = VirusXPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "رد على: ${replyMsg.content ?: "رسالة"}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearReply, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                }
            }
        }

        state.whisperTargetUser?.let { targetUser ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VirusXWhisperSurface)
                    .border(1.dp, VirusXSecondaryWhisper, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = VirusXSecondaryWhisper, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "همسة سرية موجهة إلى: ${targetUser.displayName}",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearWhisper, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                }
            }
        }

        if (isRecording) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val filters = listOf(
                        "normal" to "صوت طبيعى",
                        "deep" to "صوت عميق",
                        "robot" to "روبوت",
                        "fast" to "سريع"
                    )
                    items(filters) { (key, label) ->
                        FilterChip(
                            selected = state.selectedPitchFilter == key,
                            onClick = { onSelectPitchFilter(key) },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.voiceViewOnce,
                            onClick = onToggleViewOnce,
                            label = { Text("🔥 مرة واحدة", fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VirusXSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancelRecording) {
                        Icon(Icons.Default.Delete, contentDescription = "إلغاء التسجيل", tint = DangerRed)
                    }

                    Text("🔴 ${recordingDuration}ث", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val bars = if (liveWaveform.isEmpty()) List(20) { 0.2f } else liveWaveform.takeLast(24)
                        bars.forEach { amp ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height((amp * 24).dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(VirusXSecondaryWhisper)
                            )
                        }
                    }

                    IconButton(
                        onClick = onSendVoice,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(VirusXPrimary)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال الفويس نوت", tint = TextPrimary)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = onInputChange,
                    placeholder = { Text("اكتب رسالتك هنا...", color = TextMuted, fontSize = 14.sp) },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = VirusXSurfaceElevated,
                        unfocusedContainerColor = VirusXSurfaceElevated,
                        focusedBorderColor = VirusXPrimary,
                        unfocusedBorderColor = VirusXGlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )

                if (state.inputText.isNotBlank()) {
                    IconButton(
                        onClick = onSendText,
                        enabled = !state.isSending,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(VirusXPrimary, VirusXSecondaryWhisper)))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال", tint = TextPrimary)
                    }
                } else {
                    IconButton(
                        onClick = onStartRecording,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(VirusXPrimary)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "تسجيل رسالة صوتية", tint = TextPrimary)
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = false,
                        onClick = onPickImage,
                        label = { Text("📷 صورة", fontSize = 11.sp) }
                    )
                }
                item {
                    val timerLabel = state.destructTimerSeconds?.let { "⏳ تدمير: ${it}ث" } ?: "⏳ مؤقت"
                    FilterChip(
                        selected = state.destructTimerSeconds != null,
                        onClick = onCycleDestructTimer,
                        label = { Text(timerLabel, fontSize = 11.sp) }
                    )
                }
                if (state.isGroup) {
                    item {
                        FilterChip(
                            selected = state.whisperTargetUser != null,
                            onClick = onOpenWhisperPicker,
                            label = { Text("🔒 همسة سرية", fontSize = 11.sp) }
                        )
                    }
                }
                item {
                    FilterChip(
                        selected = isTranscribing,
                        onClick = onSpeechToText,
                        label = { Text(if (isTranscribing) "جاري الاستماع..." else "🎙️ إملاء نصى", fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StartSyncSessionDialog(
    onDismiss: () -> Unit,
    onStart: (String, String) -> Unit
) {
    var mediaUrl by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf("audio") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VirusXSurface,
        title = { Text("🎧 بدء جلسة استماع/مشاهدة متزامنة", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "أدخل رابط مباشر لملف صوتى (MP3/M4A) لتشغيله بشكل متزامن مع جميع أعضاء المحادثة:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = mediaUrl,
                    onValueChange = { mediaUrl = it },
                    label = { Text("رابط المقطع (URL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mediaType == "audio", onClick = { mediaType = "audio" }, label = { Text("صوت (Audio)") })
                    FilterChip(selected = mediaType == "video", onClick = { mediaType = "video" }, label = { Text("فيديو (Video)") })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (mediaUrl.isNotBlank()) onStart(mediaUrl, mediaType) },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary)
            ) {
                Text("تشغيل للجميع")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}
""",

    "app/src/main/java/com/virusx/app/data/repository/AdminRepository.kt": """package com.virusx.app.data.repository

import com.virusx.app.data.remote.dto.MessageDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.remote.dto.StoryAskQuestionDto
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AdminChatOverviewDto(
    val id: String,
    val type: String,
    val name: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("member_count") val memberCount: Long = 0
)

@Singleton
class AdminRepository @Inject constructor(
    private val postgrest: Postgrest
) {
    suspend fun fetchAllUsers(): Result<List<ProfileDto>> = runCatching {
        postgrest.from("profiles")
            .select {
                order("is_online", Order.DESCENDING)
                order("last_seen", Order.DESCENDING)
            }.decodeList<ProfileDto>()
    }

    suspend fun fetchAllChatsForInspector(): Result<List<AdminChatOverviewDto>> = runCatching {
        postgrest.rpc(function = "admin_fetch_all_chats").decodeList<AdminChatOverviewDto>()
    }

    suspend fun fetchChatMessagesForInspector(chatId: String): Result<List<MessageDto>> = runCatching {
        postgrest.rpc(
            function = "admin_fetch_chat_messages",
            parameters = buildJsonObject {
                put("_chat_id", chatId)
            }
        ).decodeList<MessageDto>()
    }

    suspend fun fetchDeletedMessages(): Result<List<MessageDto>> = runCatching {
        postgrest.from("messages")
            .select {
                filter { eq("is_deleted", true) }
                order("deleted_at", Order.DESCENDING)
                limit(100)
            }.decodeList<MessageDto>()
    }

    suspend fun fetchAllAnonymousQuestions(): Result<List<StoryAskQuestionDto>> = runCatching {
        postgrest.from("story_ask_questions")
            .select {
                order("created_at", Order.DESCENDING)
                limit(100)
            }.decodeList<StoryAskQuestionDto>()
    }

    suspend fun revealAnonymousAsker(questionId: String): Result<String> = runCatching {
        postgrest.rpc(
            function = "reveal_anonymous_asker",
            parameters = buildJsonObject {
                put("_question_id", questionId)
            }
        ).decodeAs<String>()
    }

    suspend fun setUserBan(targetUserId: String, banned: Boolean, reason: String?): Result<Unit> = runCatching {
        postgrest.rpc(
            function = "set_user_ban",
            parameters = buildJsonObject {
                put("_target_user_id", targetUserId)
                put("_banned", banned)
                put("_reason", reason)
            }
        )
        Unit
    }

    suspend fun muteUserForHours(targetUserId: String, hours: Long): Result<Unit> = runCatching {
        val mutedUntilIso = if (hours <= 0L) {
            Instant.now().minus(1, ChronoUnit.MINUTES).toString()
        } else {
            Instant.now().plus(hours, ChronoUnit.HOURS).toString()
        }
        postgrest.rpc(
            function = "set_user_mute",
            parameters = buildJsonObject {
                put("_target_user_id", targetUserId)
                put("_muted_until", mutedUntilIso)
            }
        )
        Unit
    }

    suspend fun sendGlobalBroadcast(title: String, body: String): Result<Unit> = runCatching {
        postgrest.rpc(
            function = "send_broadcast",
            parameters = buildJsonObject {
                put("_title", title.trim())
                put("_body", body.trim())
            }
        )
        Unit
    }

    suspend fun updateAppConfig(
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
        Unit
    }

    suspend fun lockAdminPromotionCode(): Result<Unit> = runCatching {
        postgrest.rpc(function = "lock_admin_promotion")
        Unit
    }
}
""",

    "app/src/main/java/com/virusx/app/ui/admin/SuperAdminViewModel.kt": """package com.virusx.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.remote.dto.MessageDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.remote.dto.StoryAskQuestionDto
import com.virusx.app.data.repository.AdminChatOverviewDto
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
    val allChats: List<AdminChatOverviewDto> = emptyList(),
    val inspectedChat: AdminChatOverviewDto? = null,
    val inspectedMessages: List<MessageDto> = emptyList(),
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
            val chats = adminRepository.fetchAllChatsForInspector().getOrDefault(emptyList())
            val deleted = adminRepository.fetchDeletedMessages().getOrDefault(emptyList())
            val questions = adminRepository.fetchAllAnonymousQuestions().getOrDefault(emptyList())

            _uiState.update {
                it.copy(
                    users = users,
                    allChats = chats,
                    deletedMessages = deleted,
                    anonymousQuestions = questions,
                    isLoading = false
                )
            }
        }
    }

    fun inspectChat(chat: AdminChatOverviewDto) {
        viewModelScope.launch {
            _uiState.update { it.copy(inspectedChat = chat, inspectedMessages = emptyList()) }
            val msgs = adminRepository.fetchChatMessagesForInspector(chat.id).getOrDefault(emptyList())
            _uiState.update { it.copy(inspectedMessages = msgs) }
        }
    }

    fun closeChatInspector() {
        _uiState.update { it.copy(inspectedChat = null, inspectedMessages = emptyList()) }
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
                    val msg = if (hours > 0L) "تم كتم ${user.displayName} لمدة $hours ساعة" else "تم فك الكتم عن ${user.displayName}"
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

    fun publishOtaUpdate(
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
""",

    "app/src/main/java/com/virusx/app/ui/admin/SuperAdminScreen.kt": """package com.virusx.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.virusx.app.data.remote.dto.MessageDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.remote.dto.StoryAskQuestionDto
import com.virusx.app.data.repository.AdminChatOverviewDto
import com.virusx.app.ui.theme.DangerRed
import com.virusx.app.ui.theme.OnlineGreen
import com.virusx.app.ui.theme.TextMuted
import com.virusx.app.ui.theme.TextPrimary
import com.virusx.app.ui.theme.TextSecondary
import com.virusx.app.ui.theme.VirusXBackground
import com.virusx.app.ui.theme.VirusXGlassBorder
import com.virusx.app.ui.theme.VirusXPrimary
import com.virusx.app.ui.theme.VirusXSecondaryWhisper
import com.virusx.app.ui.theme.VirusXSurface
import com.virusx.app.ui.theme.VirusXSurfaceElevated
import com.virusx.app.ui.theme.WarningAmber

@Composable
fun SuperAdminScreen(
    onBack: () -> Unit,
    viewModel: SuperAdminViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val userMap = remember(state.users) { state.users.associateBy { it.id } }
    val onlineCount = remember(state.users) { state.users.count { it.isOnline } }

    Scaffold(
        containerColor = VirusXBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VirusXSurface)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                    }
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = VirusXSecondaryWhisper)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "لوحة تحكم المطور (Super Admin)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                }

                IconButton(onClick = viewModel::refreshDashboard) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = VirusXSecondaryWhisper)
                }
            }

            state.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = OnlineGreen,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VirusXSurfaceElevated)
                        .clickable { viewModel.clearStatusMessage() }
                        .padding(12.dp)
                )
            }

            val tabs = listOf(
                "رادار المتصلين ($onlineCount/${state.users.size})",
                "المراقب المباشر (${state.allChats.size})",
                "الرسائل المحذوفة (${state.deletedMessages.size})",
                "كشف المجهول (${state.anonymousQuestions.size})",
                "التحديثات والأمان"
            )

            ScrollableTabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = VirusXSurface,
                contentColor = VirusXSecondaryWhisper,
                edgePadding = 12.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = {
                            Text(
                                text = title,
                                color = if (state.selectedTab == index) VirusXSecondaryWhisper else TextSecondary,
                                fontWeight = if (state.selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VirusXPrimary)
                }
            } else {
                when (state.selectedTab) {
                    0 -> AdminLiveRadarUsersTab(
                        users = state.users,
                        onlineCount = onlineCount,
                        onToggleBan = { viewModel.toggleBanUser(it) },
                        onMute24h = { viewModel.muteUser(it, 24L) },
                        onUnmute = { viewModel.muteUser(it, 0L) }
                    )
                    1 -> AdminLiveChatInspectorTab(
                        chats = state.allChats,
                        userMap = userMap,
                        onInspect = viewModel::inspectChat
                    )
                    2 -> AdminDeletedMessagesTab(
                        messages = state.deletedMessages,
                        userMap = userMap
                    )
                    3 -> AdminRevealQuestionsTab(
                        questions = state.anonymousQuestions,
                        revealedMap = state.revealedAskersMap,
                        onReveal = viewModel::revealQuestionAsker
                    )
                    4 -> AdminBroadcastAndSecurityTab(
                        onSendBroadcast = viewModel::sendBroadcast,
                        onPublishOta = viewModel::publishOtaUpdate,
                        onLockPromotion = viewModel::lockAdminCodePermanently
                    )
                }
            }
        }
    }

    state.inspectedChat?.let { targetChat ->
        AlertDialog(
            onDismissRequest = viewModel::closeChatInspector,
            containerColor = VirusXSurface,
            title = {
                Text(
                    text = "👁️ مراقبة: ${targetChat.name ?: if (targetChat.type == "group") "مجموعة" else "محادثة خاصة"}",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (state.inspectedMessages.isEmpty()) {
                    Text("لا توجد رسائل داخل هذه المحادثة بعد.", color = TextMuted)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.inspectedMessages, key = { it.id }) { m ->
                            val sender = userMap[m.senderId]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VirusXSurfaceElevated)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "${sender?.displayName ?: m.senderId} (@${sender?.username ?: "-"})",
                                    color = VirusXSecondaryWhisper,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = m.content ?: "[مرفق وسائط]",
                                    color = if (m.isDeleted) DangerRed else TextPrimary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = m.createdAt ?: "",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::closeChatInspector) {
                    Text("إغلاق", color = VirusXSecondaryWhisper)
                }
            }
        )
    }
}

@Composable
private fun AdminLiveRadarUsersTab(
    users: List<ProfileDto>,
    onlineCount: Int,
    onToggleBan: (ProfileDto) -> Unit,
    onMute24h: (ProfileDto) -> Unit,
    onUnmute: (ProfileDto) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OnlineGreen.copy(alpha = 0.14f))
                    .border(1.dp, OnlineGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Public, contentDescription = null, tint = OnlineGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "رادار النشاط المباشر: $onlineCount متصل الآن من إجمالى ${users.size} عضو",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        items(users, key = { it.id }) { user ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(VirusXSurface)
                    .border(
                        width = 1.dp,
                        color = if (user.isOnline) OnlineGreen.copy(alpha = 0.5f) else VirusXGlassBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${user.displayName} (@${user.username})",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (user.isOnline) "🟢 متصل الآن" else "⚪ غير متصل",
                        color = if (user.isOnline) OnlineGreen else TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Text("📞 الهاتف: ${user.phoneNumber ?: "غير مسجل"}", color = VirusXSecondaryWhisper, fontSize = 13.sp)
                Text("📧 البريد: ${user.email ?: "غير متاح"}", color = TextSecondary, fontSize = 12.sp)
                Text("📱 نوع الجهاز: ${user.deviceType ?: "غير معروف"} | الصلاحية: ${user.role}", color = TextSecondary, fontSize = 12.sp)
                Text("🕒 آخر نشاط: ${user.lastSeen ?: "الآن"}", color = TextMuted, fontSize = 11.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onToggleBan(user) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (user.isBanned) OnlineGreen else DangerRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (user.isBanned) "فك الحظر" else "حظر", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (user.mutedUntil.isNullOrBlank()) onMute24h(user) else onUnmute(user)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (user.mutedUntil.isNullOrBlank()) "كتم 24س" else "فك الكتم", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminLiveChatInspectorTab(
    chats: List<AdminChatOverviewDto>,
    userMap: Map<String, ProfileDto>,
    onInspect: (AdminChatOverviewDto) -> Unit
) {
    if (chats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد محادثات أو مجموعات مسجلة حالياً.", color = TextMuted)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chats, key = { it.id }) { chat ->
                val creator = chat.createdBy?.let { userMap[it] }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VirusXSurface)
                        .border(1.dp, VirusXGlassBorder, RoundedCornerShape(16.dp))
                        .clickable { onInspect(chat) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (chat.type == "group") "👥 مجموعة: ${chat.name ?: "بدون اسم"}" else "💬 محادثة خاصة",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "المنشئ: ${creator?.displayName ?: "عضو"} | عدد الأعضاء: ${chat.memberCount}",
                            color = VirusXSecondaryWhisper,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "تاريخ الإنشاء: ${chat.createdAt ?: "-"}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Icon(Icons.Default.Visibility, contentDescription = "فحص المحادثة", tint = VirusXSecondaryWhisper)
                }
            }
        }
    }
}

@Composable
private fun AdminDeletedMessagesTab(
    messages: List<MessageDto>,
    userMap: Map<String, ProfileDto>
) {
    if (messages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد رسائل محذوفة مسجلة حالياً.", color = TextMuted)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val sender = userMap[msg.senderId]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VirusXSurface)
                        .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "المرسل: ${sender?.displayName ?: msg.senderId} (@${sender?.username ?: "-"})",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Text("المحتوى: ${msg.content ?: "[ملف وسائط]"}", color = VirusXSecondaryWhisper, fontSize = 14.sp)
                    Text("تاريخ الحذف: ${msg.deletedAt ?: msg.createdAt ?: "-"}", color = TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AdminRevealQuestionsTab(
    questions: List<StoryAskQuestionDto>,
    revealedMap: Map<String, ProfileDto>,
    onReveal: (StoryAskQuestionDto) -> Unit
) {
    if (questions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد أسئلة مجهولة في الحالات حالياً.", color = TextMuted)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(questions, key = { it.id }) { q ->
                val revealedProfile = revealedMap[q.id]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VirusXSurface)
                        .border(1.dp, VirusXGlassBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("❓ السؤال: ${q.questionText}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    if (revealedProfile != null) {
                        Text(
                            text = "🕵️ الهوية المكشوفة: ${revealedProfile.displayName} (@${revealedProfile.username}) | هاتف: ${revealedProfile.phoneNumber ?: "-"}",
                            color = OnlineGreen,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    } else {
                        Button(
                            onClick = { onReveal(q) },
                            colors = ButtonDefaults.buttonColors(containerColor = VirusXSecondaryWhisper)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = VirusXBackground, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("كشف هوية السائل", color = VirusXBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminBroadcastAndSecurityTab(
    onSendBroadcast: (String, String) -> Unit,
    onPublishOta: (String, String, String, String, Boolean, Boolean) -> Unit,
    onLockPromotion: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var verCode by remember { mutableStateOf("3") }
    var verName by remember { mutableStateOf("1.0.2") }
    var apkUrl by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("تحديث جديد يتضمن إضافات وتحسينات في الأداء") }
    var forceUpdate by remember { mutableStateOf(false) }
    var maintMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(VirusXSurface)
                .border(1.dp, VirusXGlassBorder, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = VirusXSecondaryWhisper)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إرسال إشعار عام لكل المستخدمين", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان الإشعار العام") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("نص الإشعار") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    onSendBroadcast(title, body)
                    title = ""
                    body = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إرسال الإشعار الآن")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(VirusXSurface)
                .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = DangerRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("قفل كود الترقية السري نهائياً", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onLockPromotion,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("تفعيل القفل النهائي")
            }
        }
    }
}
"""
}


def clean_stories_imports(root: Path):
    stories_ui = root / "app/src/main/java/com/virusx/app/ui/stories/StoriesScreen.kt"
    if not stories_ui.exists():
        return
    lines = stories_ui.read_text(encoding="utf-8").splitlines()
    seen_imports = set()
    cleaned = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("import "):
            if stripped in seen_imports:
                continue
            seen_imports.add(stripped)
        cleaned.append(line)
    stories_ui.write_text("\n".join(cleaned) + "\n", encoding="utf-8")
    print("[+] Cleaned imports in StoriesScreen.kt")


def ensure_version_v1_0_2(root: Path):
    gradle_file = root / "app/build.gradle.kts"
    if not gradle_file.exists():
        return
    txt = gradle_file.read_text(encoding="utf-8")
    txt = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 3", txt, count=1)
    txt = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.0.2"', txt, count=1)
    gradle_file.write_text(txt, encoding="utf-8")
    print("[+] Verified versionCode = 3, versionName = 1.0.2 in app/build.gradle.kts")


def audit_kotlin_brackets(root: Path):
    broken = []
    for kt in (root / "app/src/main/java").rglob("*.kt"):
        for idx, line in enumerate(kt.read_text(encoding="utf-8").splitlines(), 1):
            if "->" not in line and ">=" not in line and "<=" not in line and " > " not in line and " < " not in line:
                if line.count("<") != line.count(">"):
                    broken.append(f"{kt.name}:{idx}: {line.strip()}")
    if broken:
        print("[!] Found broken lines:")
        for b in broken:
            print("   ", b)
        sys.exit(1)
    print("[✓] Audit passed: 0 broken generic brackets across all Kotlin files!")


def main():
    root = find_project_root()
    print(f"[*] Found VirusX project at: {root}")
    os.chdir(root)

    for rel_path, content in FILES.items():
        target = root / rel_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content.strip() + "\n", encoding="utf-8")
        print(f"  [+] Written cleanly: {rel_path}")

    clean_stories_imports(root)
    ensure_version_v1_0_2(root)
    audit_kotlin_brackets(root)

    subprocess.run(["git", "add", "."], check=True)
    commit_res = subprocess.run(["git", "commit", "-m", "Clean v1.0.2 build from standalone script"])
    if commit_res.returncode == 0:
        subprocess.run(["git", "push"], check=True)
    else:
        subprocess.run(["git", "push"], check=True)

    apk_url = "https://github.com/zakerhsab-star/virus-x/releases/download/v1.0.2/VirusX-v1.0.2.apk"
    try:
        subprocess.run(["clip"], input=apk_url.encode("utf-8"), check=True)
        print(f"\n[✓] DONE! Pushed to GitHub & copied link to clipboard:\n    {apk_url}")
    except Exception:
        print(f"\n[✓] DONE! Pushed to GitHub. Direct link:\n    {apk_url}")


if __name__ == "__main__":
    main()
