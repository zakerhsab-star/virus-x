package com.virusx.app.data.remote.dto

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
