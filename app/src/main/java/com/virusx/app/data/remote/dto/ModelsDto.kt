package com.virusx.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ProfileDto(
    @SerialName("id") val id: String,
    @SerialName("username") val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("email") val email: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("device_type") val deviceType: String? = null,
    @SerialName("mood_status") val moodStatus: String? = null,
    @SerialName("role") val role: String = "user",
    @SerialName("is_banned") val isBanned: Boolean = false,
    @SerialName("muted_until") val mutedUntil: String? = null,
    @SerialName("dnd_enabled") val dndEnabled: Boolean = false,
    @SerialName("dnd_auto_reply") val dndAutoReply: String? = null,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class UserPrivateSettingsDto(
    @SerialName("user_id") val userId: String,
    @SerialName("vault_pin_hash") val vaultPinHash: String? = null,
    @SerialName("dnd_emergency_keyword") val dndEmergencyKeyword: String? = null,
    @SerialName("ban_reason") val banReason: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ChatDto(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String, // 'direct' | 'group'
    @SerialName("name") val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ChatInsertDto(
    @SerialName("type") val type: String,
    @SerialName("name") val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_by") val createdBy: String
)

@Serializable
data class ChatMemberDto(
    @SerialName("chat_id") val chatId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("member_role") val memberRole: String = "member", // 'member' | 'group_admin'
    @SerialName("joined_at") val joinedAt: String? = null
)

@Serializable
data class MessageDto(
    @SerialName("id") val id: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("message_type") val messageType: String = "text",
    @SerialName("content") val content: String? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("reply_to_message_id") val replyToMessageId: String? = null,
    @SerialName("whisper_target_id") val whisperTargetId: String? = null,
    @SerialName("voice_view_once") val voiceViewOnce: Boolean = false,
    @SerialName("voice_played") val voicePlayed: Boolean = false,
    @SerialName("voice_duration_seconds") val voiceDurationSeconds: Int? = null,
    @SerialName("voice_waveform") val voiceWaveform: List<Float>? = null,
    @SerialName("voice_pitch_filter") val voicePitchFilter: String? = null,
    @SerialName("voice_transcript") val voiceTranscript: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("status") val status: String = "sent",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class MessageReadStatusDto(
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("read_at") val readAt: String? = null
)

@Serializable
data class ChatSyncSessionDto(
    @SerialName("id") val id: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("media_url") val mediaUrl: String,
    @SerialName("media_type") val mediaType: String, // 'audio' | 'video'
    @SerialName("started_by") val startedBy: String,
    @SerialName("position_seconds") val positionSeconds: Double = 0.0,
    @SerialName("is_playing") val isPlaying: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class StoryDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("story_type") val storyType: String, // 'media' | 'text' | 'poll' | 'ask' | 'countdown'
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("text_content") val textContent: String? = null,
    @SerialName("background_color") val backgroundColor: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class StoryPollDto(
    @SerialName("id") val id: String,
    @SerialName("story_id") val storyId: String,
    @SerialName("question") val question: String,
    @SerialName("options") val options: List<String> = emptyList()
)

@Serializable
data class StoryPollVoteDto(
    @SerialName("poll_id") val pollId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("option_index") val optionIndex: Int,
    @SerialName("voted_at") val votedAt: String? = null
)

@Serializable
data class StoryAskQuestionDto(
    @SerialName("id") val id: String,
    @SerialName("story_id") val storyId: String,
    @SerialName("asker_id") val askerId: String,
    @SerialName("question_text") val questionText: String,
    @SerialName("answered") val answered: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class StoryAskQuestionAnonymizedDto(
    @SerialName("id") val id: String,
    @SerialName("story_id") val storyId: String,
    @SerialName("question_text") val questionText: String,
    @SerialName("answered") val answered: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class StoryCountdownDto(
    @SerialName("id") val id: String,
    @SerialName("story_id") val storyId: String,
    @SerialName("title") val title: String,
    @SerialName("target_datetime") val targetDatetime: String
)

@Serializable
data class AdminAuditLogDto(
    @SerialName("id") val id: String,
    @SerialName("admin_id") val adminId: String,
    @SerialName("action_type") val actionType: String,
    @SerialName("target_id") val targetId: String? = null,
    @SerialName("details") val details: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class BroadcastDto(
    @SerialName("id") val id: String,
    @SerialName("admin_id") val adminId: String,
    @SerialName("title") val title: String,
    @SerialName("body") val body: String,
    @SerialName("created_at") val createdAt: String? = null
)
