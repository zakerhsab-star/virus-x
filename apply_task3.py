#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
from pathlib import Path

FILES = {
    # =========================================================================
    # 1. REMOTE DTOs (Matching SQL Schema + Patch v2 + phone_number)
    # =========================================================================
    "app/src/main/java/com/virusx/app/data/remote/dto/ModelsDto.kt": r'''package com.virusx.app.data.remote.dto

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
''',

    # =========================================================================
    # 2. ROOM ENTITIES (Local Cache + Vault Hidden Flag + phoneNumber)
    # =========================================================================
    "app/src/main/java/com/virusx/app/data/local/entity/Entities.kt": r'''package com.virusx.app.data.local.entity

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
''',

    # =========================================================================
    # 3. ROOM DAOs
    # =========================================================================
    "app/src/main/java/com/virusx/app/data/local/dao/Daos.kt": r'''package com.virusx.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.virusx.app.data.local.entity.ChatEntity
import com.virusx.app.data.local.entity.MessageEntity
import com.virusx.app.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE id = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :userId LIMIT 1")
    suspend fun getProfileById(userId: String): ProfileEntity?

    @Query(
        """
        SELECT * FROM profiles
        WHERE username LIKE '%' || :query || '%'
           OR display_name LIKE '%' || :query || '%'
           OR phone_number LIKE '%' || :query || '%'
        ORDER BY display_name ASC
        """
    )
    suspend fun searchProfilesLocally(query: String): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfiles(profiles: List<ProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles")
    suspend fun clearAll()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE is_hidden_in_vault = 0 ORDER BY COALESCE(last_message_time, created_at) DESC")
    fun observeVisibleChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE is_hidden_in_vault = 1 ORDER BY COALESCE(last_message_time, created_at) DESC")
    fun observeVaultChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Query("SELECT is_hidden_in_vault FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun isChatHidden(chatId: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChats(chats: List<ChatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: ChatEntity)

    @Query("UPDATE chats SET is_hidden_in_vault = :hidden WHERE id = :chatId")
    suspend fun setChatHiddenInVault(chatId: String, hidden: Boolean)

    @Query("DELETE FROM chats")
    suspend fun clearAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chat_id = :chatId ORDER BY created_at ASC")
    fun observeMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE chat_id = :chatId")
    suspend fun clearChatMessages(chatId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}
''',

    # =========================================================================
    # 4. ROOM DATABASE & HILT MODULE
    # =========================================================================
    "app/src/main/java/com/virusx/app/data/local/VirusXDatabase.kt": r'''package com.virusx.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.virusx.app.data.local.dao.ChatDao
import com.virusx.app.data.local.dao.MessageDao
import com.virusx.app.data.local.dao.ProfileDao
import com.virusx.app.data.local.entity.ChatEntity
import com.virusx.app.data.local.entity.MessageEntity
import com.virusx.app.data.local.entity.ProfileEntity

@Database(
    entities = [
        ProfileEntity::class,
        ChatEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VirusXDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
}
''',

    "app/src/main/java/com/virusx/app/di/DatabaseModule.kt": r'''package com.virusx.app.di

import android.content.Context
import androidx.room.Room
import com.virusx.app.data.local.VirusXDatabase
import com.virusx.app.data.local.dao.ChatDao
import com.virusx.app.data.local.dao.MessageDao
import com.virusx.app.data.local.dao.ProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVirusXDatabase(@ApplicationContext context: Context): VirusXDatabase {
        return Room.databaseBuilder(
            context,
            VirusXDatabase::class.java,
            "virusx_local.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideProfileDao(db: VirusXDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideChatDao(db: VirusXDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMessageDao(db: VirusXDatabase): MessageDao = db.messageDao()
}
''',

    # =========================================================================
    # 5. SECURITY & STEALTH VAULT PREFERENCES
    # =========================================================================
    "app/src/main/java/com/virusx/app/util/SecurityUtils.kt": r'''package com.virusx.app.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vaultDataStore by preferencesDataStore(name = "virusx_stealth_vault_prefs")

object PinHashUtil {
    private const val SALT = "VirusX_Stealth_Vault_2026_Salt"

    fun hashPin(rawPin: String): String {
        val clean = rawPin.trim()
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$SALT:$clean".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(rawInput: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank() || rawInput.isBlank()) return false
        return hashPin(rawInput) == storedHash
    }
}

@Singleton
class VaultPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val KEY_VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
    private val KEY_HIDDEN_CHAT_IDS = stringSetPreferencesKey("hidden_chat_ids")
    private val KEY_EMERGENCY_KEYWORD = stringPreferencesKey("dnd_emergency_keyword")

    val vaultPinHashFlow: Flow<String?> = context.vaultDataStore.data.map { prefs ->
        prefs[KEY_VAULT_PIN_HASH]
    }

    val hiddenChatIdsFlow: Flow<Set<String>> = context.vaultDataStore.data.map { prefs ->
        prefs[KEY_HIDDEN_CHAT_IDS] ?: emptySet()
    }

    val emergencyKeywordFlow: Flow<String?> = context.vaultDataStore.data.map { prefs ->
        prefs[KEY_EMERGENCY_KEYWORD]
    }

    suspend fun saveVaultPinHash(hash: String?) {
        context.vaultDataStore.edit { prefs ->
            if (hash.isNullOrBlank()) {
                prefs.remove(KEY_VAULT_PIN_HASH)
            } else {
                prefs[KEY_VAULT_PIN_HASH] = hash
            }
        }
    }

    suspend fun saveEmergencyKeyword(keyword: String?) {
        context.vaultDataStore.edit { prefs ->
            if (keyword.isNullOrBlank()) {
                prefs.remove(KEY_EMERGENCY_KEYWORD)
            } else {
                prefs[KEY_EMERGENCY_KEYWORD] = keyword.trim()
            }
        }
    }

    suspend fun setChatHidden(chatId: String, hidden: Boolean) {
        context.vaultDataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_CHAT_IDS]?.toMutableSet() ?: mutableSetOf()
            if (hidden) current.add(chatId) else current.remove(chatId)
            prefs[KEY_HIDDEN_CHAT_IDS] = current
        }
    }

    suspend fun isChatHidden(chatId: String): Boolean {
        val set = hiddenChatIdsFlow.first()
        return set.contains(chatId)
    }

    suspend fun clearAll() {
        context.vaultDataStore.edit { it.clear() }
    }
}
''',

    # =========================================================================
    # 6. AUTH & PROFILE REPOSITORY (Phone Availability RPC + Metadata + Admin)
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
            // Fallback query if RPC fails for any network reason
            val existing = postgrest.from("profiles")
                .select {
                    filter {
                        eq("phone_number", cleanPhone)
                    }
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
    ): Result<Unit> = runCatching {
        val normalizedUsername = username.trim().lowercase()
        val normalizedPhone = phoneNumber.trim()
        val normalizedName = displayName.trim()

        // 1. Check phone number availability via RPC `is_phone_available`
        val phoneFree = isPhoneAvailable(normalizedPhone)
        if (!phoneFree) {
            throw IllegalStateException("رقم الهاتف مسجل بالفعل لحساب آخر")
        }

        // 2. Check username availability
        val existingUsername = postgrest.from("profiles")
            .select {
                filter {
                    eq("username", normalizedUsername)
                }
            }.decodeList<ProfileDto>()
        if (existingUsername.isNotEmpty()) {
            throw IllegalStateException("اسم المستخدم (@$normalizedUsername) مستخدم بالفعل")
        }

        // 3. Sign up with Supabase Auth passing phone_number in user_metadata (data)
        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            this.data = buildJsonObject {
                put("username", normalizedUsername)
                put("display_name", normalizedName)
                put("phone_number", normalizedPhone)
            }
        }

        // 4. Sync device info and ensure phone_number is updated on profile
        val userId = getCurrentUserId()
        if (userId != null) {
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
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        val userId = getCurrentUserId() ?: throw IllegalStateException("تعذر الحصول على جلسة المستخدم")
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
    # 7. CHAT & USER SEARCH REPOSITORY (Search by Name, Username, Phone + Vault)
    # =========================================================================
    "app/src/main/java/com/virusx/app/data/repository/ChatRepository.kt": r'''package com.virusx.app.data.repository

import com.virusx.app.data.local.dao.ChatDao
import com.virusx.app.data.local.dao.ProfileDao
import com.virusx.app.data.local.entity.ChatEntity
import com.virusx.app.data.local.entity.toDto
import com.virusx.app.data.local.entity.toEntity
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
import kotlinx.coroutines.flow.Flow
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

        // 1. Fetch chat memberships for current user
        val myMemberships = postgrest.from("chat_members")
            .select {
                filter { eq("user_id", currentUserId) }
            }.decodeList<ChatMemberDto>()

        if (myMemberships.isEmpty()) return@runCatching
        val chatIds = myMemberships.map { it.chatId }

        // 2. Fetch chats
        val chats = postgrest.from("chats")
            .select {
                filter { isIn("id", chatIds) }
            }.decodeList<ChatDto>()

        // 3. Fetch all members of those chats to resolve peer names for direct chats
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

        // 4. Fetch latest message for each chat
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

        // Check if direct chat with targetProfile already exists locally
        val myMemberships = postgrest.from("chat_members")
            .select {
                filter { eq("user_id", currentUserId) }
            }.decodeList<ChatMemberDto>()

        if (myMemberships.isNotEmpty()) {
            val myChatIds = myMemberships.map { it.chatId }
            val peerMemberships = postgrest.from("chat_members")
                .select {
                    filter {
                        eq("user_id", targetProfile.id)
                        isIn("chat_id", myChatIds)
                    }
                }.decodeList<ChatMemberDto>()

            if (peerMemberships.isNotEmpty()) {
                val sharedChatIds = peerMemberships.map { it.chatId }
                val existingDirect = postgrest.from("chats")
                    .select {
                        filter {
                            eq("type", "direct")
                            isIn("id", sharedChatIds)
                        }
                    }.decodeList<ChatDto>()
                    .firstOrNull()

                if (existingDirect != null) {
                    syncUserChats()
                    return@runCatching existingDirect.id
                }
            }
        }

        // Create a new direct chat
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

        // Insert creator first so RLS `is_chat_member` passes for subsequent members
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
}
''',

    # =========================================================================
    # 8. AUTH VIEWMODEL & SCREEN (Mandatory Phone Number + Transparency Notice)
    # =========================================================================
    "app/src/main/java/com/virusx/app/ui/auth/AuthViewModel.kt": r'''package com.virusx.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.repository.AuthRepository
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
            val result = if (state.isSignUpMode) {
                authRepository.signUp(
                    email = state.email,
                    password = state.password,
                    username = state.username,
                    displayName = state.displayName,
                    phoneNumber = state.phoneNumber
                )
            } else {
                authRepository.signIn(
                    email = state.email,
                    password = state.password
                )
            }

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "حدث خطأ أثناء الاتصال بالخادم"
                        )
                    }
                }
            )
        }
    }
}
''',

    "app/src/main/java/com/virusx/app/ui/auth/AuthScreen.kt": r'''package com.virusx.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.virusx.app.ui.theme.DangerRed
import com.virusx.app.ui.theme.SentMessageGradient
import com.virusx.app.ui.theme.TextPrimary
import com.virusx.app.ui.theme.TextSecondary
import com.virusx.app.ui.theme.VirusXBackground
import com.virusx.app.ui.theme.VirusXGlassBorder
import com.virusx.app.ui.theme.VirusXPrimary
import com.virusx.app.ui.theme.VirusXSecondaryWhisper
import com.virusx.app.ui.theme.VirusXSurface
import com.virusx.app.ui.theme.VirusXSurfaceElevated

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onAuthSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VirusXBackground)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(24.dp))
                .background(VirusXSurface)
                .border(1.dp, VirusXGlassBorder, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SentMessageGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "VirusX Logo",
                    tint = TextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "VirusX",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )

            Text(
                text = if (state.isSignUpMode) "إنشاء حساب جديد في مجتمع الأصدقاء" else "تسجيل الدخول للمتابعة",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(visible = state.isSignUpMode) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = viewModel::onDisplayNameChange,
                        label = { Text("الاسم الكامل (Display Name)") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = authFieldColors()
                    )

                    OutlinedTextField(
                        value = state.username,
                        onValueChange = viewModel::onUsernameChange,
                        label = { Text("اسم المستخدم (Username)") },
                        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = authFieldColors()
                    )

                    OutlinedTextField(
                        value = state.phoneNumber,
                        onValueChange = viewModel::onPhoneNumberChange,
                        label = { Text("رقم الهاتف (إجباري)") },
                        placeholder = { Text("01xxxxxxxxx") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = authFieldColors()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("البريد الإلكتروني") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = authFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("كلمة المرور") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = authFieldColors()
            )

            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.errorMessage ?: "",
                    color = DangerRed,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            if (state.isSignUpMode) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VirusXSurfaceElevated)
                        .border(1.dp, VirusXGlassBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = VirusXSecondaryWhisper,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "إشعار الشفافية والأمان: هذه المنصة خاصة ومُدارة تقنياً وتخضع للإشراف الإداري لضمان أمان جميع المستخدمين ومنع الإساءة.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = viewModel::submit,
                enabled = !state.isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = TextPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = if (state.isSignUpMode) "إنشاء الحساب الآن" else "تسجيل الدخول",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = viewModel::toggleMode) {
                Text(
                    text = if (state.isSignUpMode) {
                        "لديك حساب بالفعل؟ تسجيل الدخول"
                    } else {
                        "مستخدم جديد؟ إنشاء حساب"
                    },
                    color = VirusXSecondaryWhisper
                )
            }
        }
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VirusXPrimary,
    unfocusedBorderColor = VirusXGlassBorder,
    focusedLabelColor = VirusXPrimary,
    unfocusedLabelColor = TextSecondary,
    cursorColor = VirusXPrimary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
''',

    # =========================================================================
    # 9. HOME VIEWMODEL & SCREEN (Search by Name/Username/Phone + Stealth Vault)
    # =========================================================================
    "app/src/main/java/com/virusx/app/ui/home/HomeViewModel.kt": r'''package com.virusx.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.local.entity.ChatEntity
import com.virusx.app.data.remote.dto.BroadcastDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.repository.AuthRepository
import com.virusx.app.data.repository.ChatRepository
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
    val isRefreshing: Boolean = false,
    val openStealthVaultEvent: Boolean = false,
    val navigateToChatId: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val vaultPrefs: VaultPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val visibleChats: StateFlow<List<ChatEntity>> = chatRepository.visibleChatsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultChats: StateFlow<List<ChatEntity>> = chatRepository.vaultChatsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val profile = authRepository.refreshCurrentUserProfile()
            chatRepository.syncUserChats()
            val broadcast = chatRepository.fetchLatestBroadcast()
            _uiState.update {
                it.copy(
                    currentUserProfile = profile,
                    latestBroadcast = broadcast,
                    isRefreshing = false
                )
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }

        viewModelScope.launch {
            // 1. Stealth Search Vault Trigger: Check if query matches Vault PIN
            val storedPinHash = vaultPrefs.vaultPinHashFlow.first()
            if (PinHashUtil.verifyPin(newQuery.trim(), storedPinHash)) {
                // Immediately clear search query so PIN never lingers on screen and unlock Vault
                _uiState.update {
                    it.copy(
                        searchQuery = "",
                        searchedUsers = emptyList(),
                        openStealthVaultEvent = true
                    )
                }
                return@launch
            }

            // 2. Otherwise search users by Display Name, Username, or Phone Number
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
            _uiState.update { it.copy(isRefreshing = true) }
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
                            errorMessage = err.localizedMessage
                        )
                    }
                }
            )
        }
    }

    fun createNewGroupChat(groupName: String, memberIds: List<String>) {
        if (groupName.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            chatRepository.createNewGroupChatCompat(groupName, memberIds).fold(
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
                            errorMessage = err.localizedMessage
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
}

private suspend fun ChatRepository.createNewGroupChatCompat(
    groupName: String,
    memberIds: List<String>
): Result<String> = createGroupChat(groupName, memberIds)
''',

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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.virusx.app.data.local.entity.ChatEntity
import com.virusx.app.data.remote.dto.ProfileDto
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
                Column {
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
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

            Spacer(modifier = Modifier.height(14.dp))

            // Stealth Search Bar (Searches by Name, Username, Phone OR unlocks Stealth Vault on PIN match)
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
                // Directory Search Results (Name / Username / Phone Number)
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

    // Long-press dialog to move chat into Stealth Vault
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
            Text(
                text = profile.displayName.take(1).uppercase(),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
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
                Text(
                    text = chat.displayTitle.take(1).uppercase(),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
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
''',

    # =========================================================================
    # 10. STEALTH VAULT SCREEN (Unlocked only via Search Bar PIN)
    # =========================================================================
    "app/src/main/java/com/virusx/app/ui/vault/VaultScreen.kt": r'''package com.virusx.app.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.virusx.app.ui.home.HomeViewModel
import com.virusx.app.ui.theme.TextMuted
import com.virusx.app.ui.theme.TextPrimary
import com.virusx.app.ui.theme.TextSecondary
import com.virusx.app.ui.theme.VirusXBackground
import com.virusx.app.ui.theme.VirusXGlassBorder
import com.virusx.app.ui.theme.VirusXSecondaryWhisper
import com.virusx.app.ui.theme.VirusXSurface
import com.virusx.app.ui.theme.WhisperMessageGradient

@Composable
fun VaultScreen(
    homeViewModel: HomeViewModel,
    onOpenChat: (String) -> Unit,
    onBack: () -> Unit
) {
    val vaultChats by homeViewModel.vaultChats.collectAsState()

    Scaffold(
        containerColor = VirusXBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "إغلاق الخزنة",
                        tint = TextPrimary
                    )
                }
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = VirusXSecondaryWhisper,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "خزنة المحادثات السرية (Stealth Vault)",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "هذه المحادثات مخفية تماماً من الشاشة الرئيسية والبحث العادي",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (vaultChats.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد محادثات مخفية حالياً.\nاضغط مطولاً على أي محادثة في الشاشة الرئيسية لإخفائها هنا.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(vaultChats, key = { it.id }) { chat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(VirusXSurface)
                                .border(1.dp, VirusXGlassBorder, RoundedCornerShape(18.dp))
                                .clickable { onOpenChat(chat.id) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(WhisperMessageGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chat.displayTitle.take(1).uppercase(),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chat.displayTitle,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = chat.lastMessagePreview ?: "محادثة مشفرة",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }

                            IconButton(
                                onClick = { homeViewModel.toggleChatVaultState(chat.id, false) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "إظهار في القائمة الرئيسية",
                                    tint = VirusXSecondaryWhisper
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
''',

    # =========================================================================
    # 11. SETTINGS VIEWMODEL & SCREEN (Smart DND + Vault PIN + 5-Tap Admin)
    # =========================================================================
    "app/src/main/java/com/virusx/app/ui/settings/SettingsViewModel.kt": r'''package com.virusx.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val profile: ProfileDto? = null,
    val displayName: String = "",
    val phoneNumber: String = "",
    val moodStatus: String = "",
    val dndEnabled: Boolean = false,
    val dndAutoReply: String = "",
    val dndEmergencyKeyword: String = "",
    val versionTapCount: Int = 0,
    val showAdminCodeDialog: Boolean = false,
    val showVaultPinDialog: Boolean = false,
    val statusMessage: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val profile = authRepository.refreshCurrentUserProfile()
            val privateSettings = authRepository.getCurrentUserPrivateSettings()
            _uiState.update {
                it.copy(
                    profile = profile,
                    displayName = profile?.displayName ?: "",
                    phoneNumber = profile?.phoneNumber ?: "",
                    moodStatus = profile?.moodStatus ?: "",
                    dndEnabled = profile?.dndEnabled ?: false,
                    dndAutoReply = profile?.dndAutoReply ?: "أنا في المحاضرة حالياً، سأرد عليك لاحقاً 📚",
                    dndEmergencyKeyword = privateSettings?.dndEmergencyKeyword ?: "طوارئ",
                    isLoading = false
                )
            }
        }
    }

    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value) }
    fun onPhoneNumberChange(value: String) = _uiState.update { it.copy(phoneNumber = value) }
    fun onMoodStatusChange(value: String) = _uiState.update { it.copy(moodStatus = value) }
    fun onDndEnabledChange(enabled: Boolean) = _uiState.update { it.copy(dndEnabled = enabled) }
    fun onDndAutoReplyChange(value: String) = _uiState.update { it.copy(dndAutoReply = value) }
    fun onDndEmergencyKeywordChange(value: String) = _uiState.update { it.copy(dndEmergencyKeyword = value) }

    fun saveProfileInfo() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            authRepository.updateProfileBasicInfo(
                displayName = state.displayName,
                moodStatus = state.moodStatus,
                phoneNumber = state.phoneNumber
            ).fold(
                onSuccess = { updated ->
                    _uiState.update {
                        it.copy(
                            profile = updated,
                            isLoading = false,
                            statusMessage = "تم حفظ بيانات الملف الشخصي ورقم الهاتف بنجاح"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = err.localizedMessage ?: "فشل تحديث البيانات"
                        )
                    }
                }
            )
        }
    }

    fun saveSmartDndSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            authRepository.updateSmartDndSettings(
                dndEnabled = state.dndEnabled,
                autoReply = state.dndAutoReply,
                emergencyKeyword = state.dndEmergencyKeyword
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "تم تحديث إعدادات وضع المحاضرة الذكي (Smart DND)"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = err.localizedMessage ?: "تعذر حفظ إعدادات DND"
                        )
                    }
                }
            )
        }
    }

    fun openVaultPinDialog() = _uiState.update { it.copy(showVaultPinDialog = true) }
    fun closeVaultPinDialog() = _uiState.update { it.copy(showVaultPinDialog = false) }

    fun saveVaultPin(newPin: String) {
        viewModelScope.launch {
            authRepository.updateVaultPin(newPin).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            showVaultPinDialog = false,
                            statusMessage = "تم تعيين رمز الخزنة السرية! اكتب هذا الرمز في شريط البحث الرئيسي لفتحها."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(statusMessage = err.localizedMessage ?: "تعذر حفظ الرمز السري")
                    }
                }
            )
        }
    }

    // 5 Taps on Version Number -> Opens Secret Admin Code Dialog
    fun onVersionNumberTapped() {
        val nextCount = _uiState.value.versionTapCount + 1
        if (nextCount >= 5) {
            _uiState.update {
                it.copy(
                    versionTapCount = 0,
                    showAdminCodeDialog = true
                )
            }
        } else {
            _uiState.update { it.copy(versionTapCount = nextCount) }
        }
    }

    fun dismissAdminCodeDialog() {
        _uiState.update { it.copy(showAdminCodeDialog = false) }
    }

    fun submitSecretAdminCode(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showAdminCodeDialog = false) }
            authRepository.redeemAdminCode(code).fold(
                onSuccess = { promoted ->
                    val refreshed = authRepository.refreshCurrentUserProfile()
                    _uiState.update {
                        it.copy(
                            profile = refreshed,
                            isLoading = false,
                            statusMessage = if (promoted) {
                                "تم تفعيل صلاحيات المشرف العام (Super Admin) بنجاح!"
                            } else {
                                "كود الإشراف غير صحيح"
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = err.localizedMessage ?: "تعذر التحقق من كود الإشراف"
                        )
                    }
                }
            )
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onSignedOut()
        }
    }
}
''',

    "app/src/main/java/com/virusx/app/ui/settings/SettingsScreen.kt": r'''package com.virusx.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.virusx.app.BuildConfig
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

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSuperAdminPanel: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = VirusXBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "الإعدادات والخصوصية",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }

            state.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = OnlineGreen,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VirusXSurface)
                        .padding(12.dp)
                )
            }

            // Super Admin Dashboard Entry (Only visible if role == 'super_admin')
            if (state.profile?.role == "super_admin") {
                Button(
                    onClick = onOpenSuperAdminPanel,
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXSecondaryWhisper),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = VirusXBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "فتح لوحة تحكم المشرف العام (Super Admin)",
                        color = VirusXBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 1. Profile & Phone Number Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VirusXSurface)
                    .border(1.dp, VirusXGlassBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = VirusXPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("الملف الشخصي ورقم الهاتف", style = MaterialTheme.typography.titleLarge)
                }

                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = viewModel::onDisplayNameChange,
                    label = { Text("الاسم المعروض") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.phoneNumber,
                    onValueChange = viewModel::onPhoneNumberChange,
                    label = { Text("رقم الهاتف") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.moodStatus,
                    onValueChange = viewModel::onMoodStatusChange,
                    label = { Text("الحالة المزاجية (مثال: 🎧 بيسمع مزيكا)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = viewModel::saveProfileInfo,
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حفظ تعديلات الملف الشخصي")
                }
            }

            // 2. Smart Lecture DND Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VirusXSurface)
                    .border(1.dp, VirusXGlassBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = VirusXSecondaryWhisper)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("وضع المحاضرة الذكي (Smart DND)", style = MaterialTheme.typography.titleLarge)
                    }
                    Switch(
                        checked = state.dndEnabled,
                        onCheckedChange = viewModel::onDndEnabledChange
                    )
                }

                Text(
                    text = "عند التفعيل يتم كتم الإشعارات وإرسال رد تلقائي، مع السماح بمرور التنبيه الصوتي فوراً إذا احتوت الرسالة على كلمة الطوارئ.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = state.dndAutoReply,
                    onValueChange = viewModel::onDndAutoReplyChange,
                    label = { Text("رسالة الرد التلقائي") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.dndEmergencyKeyword,
                    onValueChange = viewModel::onDndEmergencyKeywordChange,
                    label = { Text("كلمة الطوارئ لتخطي الكتم (تُحفظ بشكل مشفر وخاص)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = viewModel::saveSmartDndSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حفظ إعدادات وضع المحاضرة")
                }
            }

            // 3. Stealth Search Vault PIN Setup Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VirusXSurface)
                    .border(1.dp, VirusXGlassBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = VirusXSecondaryWhisper)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("خزنة المحادثات السرية (Stealth Search Vault)", style = MaterialTheme.typography.titleLarge)
                }

                Text(
                    text = "لا توجد أي أيقونة للخزنة في التطبيق. بعد تعيين الكود السري هنا، اكتب الكود مباشرة في شريط البحث الرئيسي لفتح محادثاتك المخفية فوراً.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Button(
                    onClick = viewModel::openVaultPinDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXSecondaryWhisper),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تعيين / تغيير كود الخزنة السري (PIN)", color = VirusXBackground, fontWeight = FontWeight.Bold)
                }
            }

            // 4. Sign Out Button
            Button(
                onClick = { viewModel.signOut(onSignedOut) },
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تسجيل الخروج")
            }

            // 5. Version Number (5 consecutive taps trigger redeem_admin_code dialog)
            BoxVersionTrigger(
                versionName = BuildConfig.VERSION_NAME,
                role = state.profile?.role ?: "user",
                onTap = viewModel::onVersionNumberTapped
            )
        }
    }

    if (state.showVaultPinDialog) {
        VaultPinSetupDialog(
            onDismiss = viewModel::closeVaultPinDialog,
            onSave = viewModel::saveVaultPin
        )
    }

    if (state.showAdminCodeDialog) {
        SecretAdminCodeDialog(
            onDismiss = viewModel::dismissAdminCodeDialog,
            onSubmit = viewModel::submitSecretAdminCode
        )
    }
}

@Composable
private fun BoxVersionTrigger(
    versionName: String,
    role: String,
    onTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onTap)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "VirusX إصدار $versionName",
            color = TextMuted,
            fontSize = 13.sp
        )
        Text(
            text = "الصلاحية الحالية: $role",
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun VaultPinSetupDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VirusXSurface,
        title = { Text("تعيين كود الخزنة السرية (PIN)", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("أدخل كود من 4 خانات أو أكثر") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(pin) },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary)
            ) {
                Text("حفظ الكود")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun SecretAdminCodeDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var secretCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VirusXSurface,
        title = { Text("ترقية المشرف العام السرية", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "أدخل كود الإشراف السري لتفعيل صلاحيات Super Admin عبر دالة redeem_admin_code:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = secretCode,
                    onValueChange = { secretCode = it },
                    label = { Text("Secret Admin Code") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(secretCode) },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXSecondaryWhisper)
            ) {
                Text("تفعيل الصلاحية", color = VirusXBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}
''',

    # =========================================================================
    # 12. NAVIGATION & MAIN ACTIVITY (Ready to compile & run)
    # =========================================================================
    "app/src/main/java/com/virusx/app/ui/navigation/VirusXNavHost.kt": r'''package com.virusx.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.virusx.app.data.repository.AuthRepository
import com.virusx.app.ui.auth.AuthScreen
import com.virusx.app.ui.home.HomeScreen
import com.virusx.app.ui.home.HomeViewModel
import com.virusx.app.ui.settings.SettingsScreen
import com.virusx.app.ui.theme.TextPrimary
import com.virusx.app.ui.theme.TextSecondary
import com.virusx.app.ui.theme.VirusXBackground
import com.virusx.app.ui.vault.VaultScreen

@Composable
fun VirusXNavHost(
    authRepository: AuthRepository
) {
    val navController = rememberNavController()
    val isAuthenticated by authRepository.isAuthenticatedFlow.collectAsState(
        initial = authRepository.getCurrentUserId() != null
    )

    val startDest = if (isAuthenticated) "home" else "auth"
    val sharedHomeViewModel: HomeViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        composable("auth") {
            AuthScreen(
                onAuthSuccess = {
                    sharedHomeViewModel.refreshAll()
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                viewModel = sharedHomeViewModel,
                onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                onOpenStories = { navController.navigate("stories") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenStealthVault = { navController.navigate("vault") }
            )
        }

        composable("vault") {
            VaultScreen(
                homeViewModel = sharedHomeViewModel,
                onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSuperAdminPanel = { navController.navigate("super_admin") },
                onSignedOut = {
                    navController.navigate("auth") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "chat/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            PlaceholderStageScreen(
                title = "غرفة المحادثة اللحظية ($chatId)",
                subtitle = "سيتم استكمال شاشة الشات اللحظي والهمسات والفويس نوت الذكي في Task 4",
                onBack = { navController.popBackStack() }
            )
        }

        composable("stories") {
            PlaceholderStageScreen(
                title = "الحالات التفاعلية (Interactive Stories)",
                subtitle = "سيتم استكمال نظام الحالات والتصويت والأسئلة المجهولة في Task 5",
                onBack = { navController.popBackStack() }
            )
        }

        composable("super_admin") {
            PlaceholderStageScreen(
                title = "لوحة تحكم المشرف العام (Super Admin)",
                subtitle = "سيتم استكمال لوحة الإدارة الشاملة واستعراض أرقام الهواتف والرسائل المحذوفة في Task 5",
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun PlaceholderStageScreen(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VirusXBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onBack) {
            Text("رجوع")
        }
    }
}
''',

    "app/src/main/java/com/virusx/app/MainActivity.kt": r'''package com.virusx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.virusx.app.data.repository.AuthRepository
import com.virusx.app.ui.navigation.VirusXNavHost
import com.virusx.app.ui.theme.VirusXTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VirusXTheme {
                VirusXNavHost(authRepository = authRepository)
            }
        }
    }
}
'''
}


def main():
    root = Path.cwd()
    print(f"[*] Writing Task 3 files into: {root}")
    for rel_path, content in FILES.items():
        file_path = root / rel_path
        file_path.parent.mkdir(parents=True, exist_ok=True)
        file_path.write_text(content.strip() + "\n", encoding="utf-8")
        print(f"  [+] Created: {rel_path}")
    print("\n[✓] Task 3 files generated successfully (13 files).")


if __name__ == "__main__":
    main()