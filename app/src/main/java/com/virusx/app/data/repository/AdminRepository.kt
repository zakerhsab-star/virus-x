package com.virusx.app.data.repository

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
        val mutedUntilIso = if (hours in Long.MIN_VALUE..0L) {
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
