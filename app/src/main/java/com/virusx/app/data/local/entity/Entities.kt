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
