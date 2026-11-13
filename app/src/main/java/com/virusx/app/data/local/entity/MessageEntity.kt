package com.virusx.app.data.local.entity

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
