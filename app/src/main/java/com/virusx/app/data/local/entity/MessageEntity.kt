package com.virusx.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.virusx.app.data.remote.dto.MessageDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "chat_id") val chatId: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    @ColumnInfo(name = "message_type") val messageType: String = "text",
    val content: String? = null,
    @ColumnInfo(name = "media_url") val mediaUrl: String? = null,
    @ColumnInfo(name = "reply_to_message_id") val replyToMessageId: String? = null,
    @ColumnInfo(name = "whisper_target_id") val whisperTargetId: String? = null,
    @ColumnInfo(name = "voice_view_once") val voiceViewOnce: Boolean = false,
    @ColumnInfo(name = "voice_played") val voicePlayed: Boolean = false,
    @ColumnInfo(name = "voice_duration_seconds") val voiceDurationSeconds: Int? = null,
    @ColumnInfo(name = "voice_waveform") val voiceWaveformJson: String? = null,
    @ColumnInfo(name = "voice_pitch_filter") val voicePitchFilter: String? = null,
    @ColumnInfo(name = "voice_transcript") val voiceTranscript: String? = null,
    val status: String = "sent",
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @ColumnInfo(name = "reactions_json") val reactionsJson: String? = null,
    @ColumnInfo(name = "destruct_seconds") val destructSeconds: Int? = null,
    @ColumnInfo(name = "edited_at") val editedAt: String? = null,
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
    status = status,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    reactionsJson = runCatching { Json.encodeToString(reactions) }.getOrNull(),
    destructSeconds = destructSeconds,
    editedAt = editedAt,
    createdAt = createdAt
)
