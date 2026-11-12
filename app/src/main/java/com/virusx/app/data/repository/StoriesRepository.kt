package com.virusx.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.remote.dto.StoryAskQuestionAnonymizedDto
import com.virusx.app.data.remote.dto.StoryCountdownDto
import com.virusx.app.data.remote.dto.StoryDto
import com.virusx.app.data.remote.dto.StoryPollDto
import com.virusx.app.data.remote.dto.StoryPollVoteDto
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

data class StoryWithDetails(
    val story: StoryDto,
    val author: ProfileDto?,
    val poll: StoryPollDto? = null,
    val pollVotes: List<StoryPollVoteDto> = emptyList(),
    val countdown: StoryCountdownDto? = null,
    val anonymousQuestions: List<StoryAskQuestionAnonymizedDto> = emptyList()
)

@Singleton
class StoriesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val storage: Storage
) {
    fun getCurrentUserId(): String? = auth.currentUserOrNull()?.id

    suspend fun fetchActiveStories(): Result<List<StoryWithDetails>> = runCatching {
        val currentUserId = getCurrentUserId()
        val nowIso = Instant.now().toString()
        val stories = postgrest.from("stories")
            .select {
                filter { gt("expires_at", nowIso) }
                order("created_at", Order.DESCENDING)
            }.decodeList<StoryDto>()

        if (stories.isEmpty()) return@runCatching emptyList()

        val authorIds = stories.map { it.userId }.distinct()
        val storyIds = stories.map { it.id }

        val authors = runCatching {
            postgrest.from("profiles")
                .select { filter { isIn("id", authorIds) } }
                .decodeList<ProfileDto>()
        }.getOrDefault(emptyList()).associateBy { it.id }

        val polls = runCatching {
            postgrest.from("story_polls")
                .select { filter { isIn("story_id", storyIds) } }
                .decodeList<StoryPollDto>()
        }.getOrDefault(emptyList())
        val pollByStory = polls.associateBy { it.storyId }

        val pollIds = polls.map { it.id }
        val votes = if (pollIds.isNotEmpty()) {
            runCatching {
                postgrest.from("story_poll_votes")
                    .select { filter { isIn("poll_id", pollIds) } }
                    .decodeList<StoryPollVoteDto>()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val votesByPoll = votes.groupBy { it.pollId }

        val countdowns = runCatching {
            postgrest.from("story_countdowns")
                .select { filter { isIn("story_id", storyIds) } }
                .decodeList<StoryCountdownDto>()
        }.getOrDefault(emptyList()).associateBy { it.storyId }

        val myAskStoryIds = stories.filter { it.userId == currentUserId && it.storyType == "ask" }.map { it.id }
        val anonymizedQuestions = if (myAskStoryIds.isNotEmpty()) {
            runCatching {
                postgrest.from("story_ask_questions_anonymized")
                    .select {
                        filter { isIn("story_id", myAskStoryIds) }
                        order("created_at", Order.DESCENDING)
                    }.decodeList<StoryAskQuestionAnonymizedDto>()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val questionsByStory = anonymizedQuestions.groupBy { it.storyId }

        stories.map { story ->
            val poll = pollByStory[story.id]
            StoryWithDetails(
                story = story,
                author = authors[story.userId],
                poll = poll,
                pollVotes = poll?.let { votesByPoll[it.id] }.orEmpty(),
                countdown = countdowns[story.id],
                anonymousQuestions = questionsByStory[story.id].orEmpty()
            )
        }
    }

    suspend fun createTextStory(textContent: String, backgroundColorHex: String): Result<Unit> = runCatching {
        val userId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")
        postgrest.from("stories").insert(
            buildJsonObject {
                put("user_id", userId)
                put("story_type", "text")
                put("text_content", textContent.trim())
                put("background_color", backgroundColorHex)
            }
        )
    }

    suspend fun createMediaStory(imageUri: Uri, caption: String?): Result<Unit> = runCatching {
        val userId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")
        val bytes = withContext(Dispatchers.IO) { compressStoryImage(imageUri) }
        val objectPath = "$userId/story_${UUID.randomUUID()}.jpg"
        val bucket = storage.from("stories")
        bucket.upload(path = objectPath, data = bytes) { upsert = false }

        val mediaUrl = runCatching {
            bucket.createSignedUrl(path = objectPath, expiresIn = 2.days)
        }.getOrElse {
            bucket.publicUrl(objectPath)
        }

        postgrest.from("stories").insert(
            buildJsonObject {
                put("user_id", userId)
                put("story_type", "media")
                put("media_url", mediaUrl)
                if (!caption.isNullOrBlank()) {
                    put("text_content", caption.trim())
                }
            }
        )
    }

    suspend fun createPollStory(
        question: String,
        options: List<String>,
        backgroundColorHex: String
    ): Result<Unit> = runCatching {
        val userId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")
        val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanOptions.size < 2) {
            throw IllegalArgumentException("يجب إدخال خيارين على الأقل للتصويت")
        }

        val insertedStory = postgrest.from("stories").insert(
            buildJsonObject {
                put("user_id", userId)
                put("story_type", "poll")
                put("text_content", question.trim())
                put("background_color", backgroundColorHex)
            }
        ) {
            select()
        }.decodeSingle<StoryDto>()

        postgrest.from("story_polls").insert(
            buildJsonObject {
                put("story_id", insertedStory.id)
                put("question", question.trim())
                put("options", JsonArray(cleanOptions.map { JsonPrimitive(it) }))
            }
        )
    }

    suspend fun createAskStory(promptQuestion: String, backgroundColorHex: String): Result<Unit> = runCatching {
        val userId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")
        postgrest.from("stories").insert(
            buildJsonObject {
                put("user_id", userId)
                put("story_type", "ask")
                put("text_content", promptQuestion.trim())
                put("background_color", backgroundColorHex)
            }
        )
    }

    suspend fun createCountdownStory(
        title: String,
        targetDatetimeIso: String,
        backgroundColorHex: String
    ): Result<Unit> = runCatching {
        val userId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")
        val insertedStory = postgrest.from("stories").insert(
            buildJsonObject {
                put("user_id", userId)
                put("story_type", "countdown")
                put("text_content", title.trim())
                put("background_color", backgroundColorHex)
            }
        ) {
            select()
        }.decodeSingle<StoryDto>()

        postgrest.from("story_countdowns").insert(
            buildJsonObject {
                put("story_id", insertedStory.id)
                put("title", title.trim())
                put("target_datetime", targetDatetimeIso.trim())
            }
        )
    }

    suspend fun voteOnPoll(pollId: String, optionIndex: Int): Result<Unit> = runCatching {
        val userId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")
        postgrest.from("story_poll_votes").upsert(
            StoryPollVoteDto(
                pollId = pollId,
                userId = userId,
                optionIndex = optionIndex
            )
        )
    }

    suspend fun sendAnonymousQuestion(storyId: String, questionText: String): Result<Unit> = runCatching {
        val userId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")
        if (questionText.isBlank()) throw IllegalArgumentException("اكتب سؤالك أولاً")
        postgrest.from("story_ask_questions").insert(
            buildJsonObject {
                put("story_id", storyId)
                put("asker_id", userId)
                put("question_text", questionText.trim())
            }
        )
    }

    suspend fun deleteStory(storyId: String): Result<Unit> = runCatching {
        postgrest.from("stories").delete {
            filter { eq("id", storyId) }
        }
    }

    private fun compressStoryImage(uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("تعذر قراءة الصورة")
        val original = stream.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalArgumentException("صيغة الصورة غير مدعومة")

        val maxDim = 1280
        val scale = minOf(
            maxDim.toFloat() / original.width,
            minOf(maxDim.toFloat() / original.height, 1.0f)
        )
        val w = (original.width * scale).toInt().coerceAtLeast(1)
        val h = (original.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1.0f) Bitmap.createScaledBitmap(original, w, h, true) else original

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return out.toByteArray()
    }
}
