package com.virusx.app.data.repository

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
