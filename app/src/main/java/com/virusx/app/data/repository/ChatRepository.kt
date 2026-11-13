package com.virusx.app.data.repository

import com.virusx.app.data.local.dao.ChatDao
import com.virusx.app.data.local.dao.ProfileDao
import com.virusx.app.data.local.entity.ChatEntity
import com.virusx.app.data.local.entity.toDto
import com.virusx.app.data.local.entity.toEntity
import com.virusx.app.data.remote.dto.AppConfigDto
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
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

        val myMemberships = postgrest.from("chat_members")
            .select {
                filter { eq("user_id", currentUserId) }
            }.decodeList<ChatMemberDto>()

        if (myMemberships.isEmpty()) return@runCatching
        val chatIds = myMemberships.map { it.chatId }

        val chats = postgrest.from("chats")
            .select {
                filter { isIn("id", chatIds) }
            }.decodeList<ChatDto>()

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

        val rpcChatId = runCatching {
            postgrest.rpc(
                function = "create_direct_chat",
                parameters = buildJsonObject {
                    put("_peer_id", targetProfile.id)
                }
            ).decodeAs<String>()
        }.getOrNull()

        if (!rpcChatId.isNullOrBlank()) {
            syncUserChats()
            return@runCatching rpcChatId
        }

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

        val rpcGroupId = runCatching {
            postgrest.rpc(
                function = "create_group_chat",
                parameters = buildJsonObject {
                    put("_name", groupName.trim())
                    put("_member_ids", JsonArray(memberUserIds.map { JsonPrimitive(it) }))
                }
            ).decodeAs<String>()
        }.getOrNull()

        if (!rpcGroupId.isNullOrBlank()) {
            syncUserChats()
            return@runCatching rpcGroupId
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

    suspend fun fetchAppConfig(): AppConfigDto? = runCatching {
        postgrest.from("app_config")
            .select {
                filter { eq("id", 1) }
                limit(1)
            }.decodeSingleOrNull<AppConfigDto>()
    }.getOrNull()
}
