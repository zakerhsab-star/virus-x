package com.virusx.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.virusx.app.data.remote.dto.MessageDto
import com.virusx.app.data.remote.dto.ProfileDto

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "email") val email: String? = null,
    @ColumnInfo(name = "phone_number") val phoneNumber: String? = null,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String? = null,
    @ColumnInfo(name = "device_type") val deviceType: String? = null,
    @ColumnInfo(name = "mood_status") val moodStatus: String? = null,
    @ColumnInfo(name = "role") val role: String = "user",
    @ColumnInfo(name = "is_banned") val isBanned: Boolean = false,
    @ColumnInfo(name = "muted_until") val mutedUntil: String? = null,
    @ColumnInfo(name = "dnd_enabled") val dndEnabled: Boolean = false,
    @ColumnInfo(name = "dnd_auto_reply") val dndAutoReply: String? = null,
    @ColumnInfo(name = "is_online") val isOnline: Boolean = false,
    @ColumnInfo(name = "last_seen") val lastSeen: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String? = null
)

fun ProfileDto.toEntity(): ProfileEntity = ProfileEntity(
    id = id,
    username = username,
    displayName = displayName,
    email = email,
    phoneNumber = phoneNumber,
    avatarUrl = avatarUrl,
    deviceType = deviceType,
    moodStatus = moodStatus,
    role = role,
    isBanned = isBanned,
    mutedUntil = mutedUntil,
    dndEnabled = dndEnabled,
    dndAutoReply = dndAutoReply,
    isOnline = isOnline,
    lastSeen = lastSeen,
    createdAt = createdAt
)

fun ProfileEntity.toDto(): ProfileDto = ProfileDto(
    id = id,
    username = username,
    displayName = displayName,
    email = email,
    phoneNumber = phoneNumber,
    avatarUrl = avatarUrl,
    deviceType = deviceType,
    moodStatus = moodStatus,
    role = role,
    isBanned = isBanned,
    mutedUntil = mutedUntil,
    dndEnabled = dndEnabled,
    dndAutoReply = dndAutoReply,
    isOnline = isOnline,
    lastSeen = lastSeen,
    createdAt = createdAt
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "type") val type: String, // 'direct' | 'group'
    @ColumnInfo(name = "display_title") val displayTitle: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String? = null,
    @ColumnInfo(name = "peer_user_id") val peerUserId: String? = null,
    @ColumnInfo(name = "peer_online") val peerOnline: Boolean = false,
    @ColumnInfo(name = "peer_phone") val peerPhone: String? = null,
    @ColumnInfo(name = "last_message_preview") val lastMessagePreview: String? = null,
    @ColumnInfo(name = "last_message_time") val lastMessageTime: String? = null,
    @ColumnInfo(name = "unread_count") val unreadCount: Int = 0,
    @ColumnInfo(name = "is_hidden_in_vault") val isHiddenInVault: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String? = null
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "chat_id") val chatId: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    @ColumnInfo(name = "message_type") val messageType: String = "text",
    @ColumnInfo(name = "content") val content: String? = null,
    @ColumnInfo(name = "media_url") val mediaUrl: String? = null,
    @ColumnInfo(name = "reply_to_message_id") val replyToMessageId: String? = null,
    @ColumnInfo(name = "whisper_target_id") val whisperTargetId: String? = null,
    @ColumnInfo(name = "voice_view_once") val voiceViewOnce: Boolean = false,
    @ColumnInfo(name = "voice_played") val voicePlayed: Boolean = false,
    @ColumnInfo(name = "voice_duration_seconds") val voiceDurationSeconds: Int? = null,
    @ColumnInfo(name = "voice_waveform_json") val voiceWaveformJson: String? = null,
    @ColumnInfo(name = "voice_pitch_filter") val voicePitchFilter: String? = null,
    @ColumnInfo(name = "voice_transcript") val voiceTranscript: String? = null,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @ColumnInfo(name = "status") val status: String = "sent",
    @ColumnInfo(name = "created_at") val createdAt: String? = null
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
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    status = status,
    createdAt = createdAt
)
