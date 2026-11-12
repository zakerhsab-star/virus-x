#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from pathlib import Path

FILES = {
    # =========================================================================
    # 1. AUTH REPOSITORY (Supabase 2.6.1 Avatar Upload + RLS-safe unique path)
    # =========================================================================
    "app/src/main/java/com/virusx/app/data/repository/AuthRepository.kt": r'''package com.virusx.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.virusx.app.data.local.dao.ChatDao
import com.virusx.app.data.local.dao.MessageDao
import com.virusx.app.data.local.dao.ProfileDao
import com.virusx.app.data.local.entity.toEntity
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.remote.dto.UserPrivateSettingsDto
import com.virusx.app.util.PinHashUtil
import com.virusx.app.util.VaultPreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

enum class SignUpResult {
    AUTHENTICATED,
    AWAITING_EMAIL_CONFIRMATION
}

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val storage: Storage,
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
            val existing = postgrest.from("profiles")
                .select {
                    filter { eq("phone_number", cleanPhone) }
                }.decodeList<ProfileDto>()
            existing.isEmpty()
        }
    }

    suspend fun isUsernameAvailable(username: String): Boolean {
        val cleanUsername = username.trim().lowercase()
        return try {
            postgrest.rpc(
                function = "is_username_available",
                parameters = buildJsonObject {
                    put("_username", cleanUsername)
                }
            ).decodeAs<Boolean>()
        } catch (e: Exception) {
            val existing = postgrest.from("profiles")
                .select {
                    filter { eq("username", cleanUsername) }
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
    ): Result<SignUpResult> = runCatching {
        val normalizedUsername = username.trim().lowercase()
        val normalizedPhone = phoneNumber.trim()
        val normalizedName = displayName.trim()

        if (!isPhoneAvailable(normalizedPhone)) {
            throw IllegalStateException("رقم الهاتف مسجل بالفعل لحساب آخر")
        }

        if (!isUsernameAvailable(normalizedUsername)) {
            throw IllegalStateException("اسم المستخدم (@$normalizedUsername) مستخدم بالفعل")
        }

        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            this.data = buildJsonObject {
                put("username", normalizedUsername)
                put("display_name", normalizedName)
                put("phone_number", normalizedPhone)
            }
        }

        val userId = getCurrentUserId()
        if (userId == null) {
            SignUpResult.AWAITING_EMAIL_CONFIRMATION
        } else {
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
            SignUpResult.AUTHENTICATED
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        val userId = getCurrentUserId()
            ?: throw IllegalStateException("يرجى تفعيل حسابك عبر الرابط المرسل إلى بريدك الإلكتروني أولاً")
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

    suspend fun uploadUserAvatar(imageUri: Uri): Result<String> = runCatching {
        val userId = getCurrentUserId() ?: throw IllegalStateException("غير مسجل الدخول")

        val compressedBytes = withContext(Dispatchers.IO) {
            compressAvatarToJpeg(imageUri)
        }

        val objectPath = "$userId/avatar_${System.currentTimeMillis()}.jpg"
        val bucket = storage.from("avatars")
        bucket.upload(path = objectPath, data = compressedBytes, upsert = false)

        val resolvedUrl = runCatching {
            bucket.createSignedUrl(path = objectPath, expiresIn = 365.days)
        }.getOrElse {
            bucket.publicUrl(objectPath)
        }

        postgrest.from("profiles").update(
            buildJsonObject {
                put("avatar_url", resolvedUrl)
            }
        ) {
            filter { eq("id", userId) }
        }

        refreshCurrentUserProfile()
        resolvedUrl
    }

    private fun compressAvatarToJpeg(uri: Uri): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("تعذر فتح الصورة المختارة")
        val original = inputStream.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalArgumentException("صيغة الصورة غير مدعومة")

        val maxDim = 512
        val scale = minOf(
            maxDim.toFloat() / original.width,
            minOf(maxDim.toFloat() / original.height, 1.0f)
        )
        val targetW = (original.width * scale).toInt().coerceAtLeast(1)
        val targetH = (original.height * scale).toInt().coerceAtLeast(1)
        val resized = if (scale < 1.0f) Bitmap.createScaledBitmap(original, targetW, targetH, true) else original

        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
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
    # 2. STORIES REPOSITORY (24h expires_at filter + Supabase 2.6.1 upload)
    # =========================================================================
    "app/src/main/java/com/virusx/app/data/repository/StoriesRepository.kt": r'''package com.virusx.app.data.repository

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
        bucket.upload(path = objectPath, data = bytes, upsert = false)

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
''',

    # =========================================================================
    # 3. STORIES VIEWMODEL
    # =========================================================================
    "app/src/main/java/com/virusx/app/ui/stories/StoriesViewModel.kt": r'''package com.virusx.app.ui.stories

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.repository.StoriesRepository
import com.virusx.app.data.repository.StoryWithDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoriesUiState(
    val stories: List<StoryWithDetails> = emptyList(),
    val selectedIndex: Int = 0,
    val currentUserId: String? = null,
    val isLoading: Boolean = false,
    val showCreateDialog: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class StoriesViewModel @Inject constructor(
    private val storiesRepository: StoriesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoriesUiState())
    val uiState: StateFlow<StoriesUiState> = _uiState.asStateFlow()

    init {
        loadStories()
    }

    fun loadStories() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    currentUserId = storiesRepository.getCurrentUserId()
                )
            }
            storiesRepository.fetchActiveStories().fold(
                onSuccess = { list ->
                    _uiState.update { state ->
                        val safeIdx = if (list.isEmpty()) 0 else state.selectedIndex.coerceIn(0, list.lastIndex)
                        state.copy(
                            stories = list,
                            selectedIndex = safeIdx,
                            isLoading = false
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = err.localizedMessage ?: "تعذر تحميل الحالات"
                        )
                    }
                }
            )
        }
    }

    fun selectStoryIndex(index: Int) {
        _uiState.update { it.copy(selectedIndex = index) }
    }

    fun openCreateDialog() = _uiState.update { it.copy(showCreateDialog = true) }
    fun closeCreateDialog() = _uiState.update { it.copy(showCreateDialog = false) }
    fun clearMessage() = _uiState.update { it.copy(statusMessage = null) }

    fun publishTextStory(text: String, bgHex: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showCreateDialog = false) }
            storiesRepository.createTextStory(text, bgHex).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم نشر الحالة النصية بنجاح") }
                    loadStories()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun publishMediaStory(uri: Uri, caption: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showCreateDialog = false) }
            storiesRepository.createMediaStory(uri, caption).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم نشر الصورة في الحالات بنجاح") }
                    loadStories()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun publishPollStory(question: String, option1: String, option2: String, bgHex: String) {
        if (question.isBlank() || option1.isBlank() || option2.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showCreateDialog = false) }
            storiesRepository.createPollStory(question, listOf(option1, option2), bgHex).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم نشر حالة التصويت بنجاح") }
                    loadStories()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun publishAskStory(prompt: String, bgHex: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showCreateDialog = false) }
            storiesRepository.createAskStory(prompt, bgHex).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم نشر صندوق الأسئلة المجهولة بنجاح") }
                    loadStories()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun publishCountdownStory(title: String, targetIso: String, bgHex: String) {
        if (title.isBlank() || targetIso.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showCreateDialog = false) }
            storiesRepository.createCountdownStory(title, targetIso, bgHex).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم نشر العداد التنازلي بنجاح") }
                    loadStories()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun voteOnPoll(pollId: String, optionIndex: Int) {
        viewModelScope.launch {
            storiesRepository.voteOnPoll(pollId, optionIndex).fold(
                onSuccess = { loadStories() },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun submitAnonymousQuestion(storyId: String, question: String) {
        viewModelScope.launch {
            storiesRepository.sendAnonymousQuestion(storyId, question).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "✅ تم إرسال سؤالك بشكل مجهول تماماً!") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun deleteStory(storyId: String) {
        viewModelScope.launch {
            storiesRepository.deleteStory(storyId).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم حذف الحالة") }
                    loadStories()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }
}
''',

    # =========================================================================
    # 4. STORIES SCREEN UI
    # =========================================================================
    "app/src/main/java/com/virusx/app/ui/stories/StoriesScreen.kt": r'''package com.virusx.app.ui.stories

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.virusx.app.data.repository.StoryWithDetails
import com.virusx.app.ui.theme.DangerRed
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
import com.virusx.app.ui.theme.WhisperMessageGradient
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun StoriesScreen(
    onBack: () -> Unit,
    viewModel: StoriesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = VirusXBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                    }
                    Text("الحالات التفاعلية (24 ساعة)", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }

                Button(
                    onClick = viewModel::openCreateDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حالة جديدة")
                }
            }

            state.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = OnlineGreen,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(VirusXSurface)
                        .clickable { viewModel.clearMessage() }
                        .padding(10.dp)
                )
            }

            if (state.isLoading && state.stories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VirusXPrimary)
                }
            } else if (state.stories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "لا توجد حالات نشطة حالياً.\nاضغط على \"حالة جديدة\" لمشاركة نص، صورة، تصويت، أو سؤال مجهول!",
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(state.stories) { index, item ->
                        val isSelected = index == state.selectedIndex
                        val name = item.author?.displayName ?: "عضو"
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { viewModel.selectStoryIndex(index) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) VirusXSecondaryWhisper else VirusXGlassBorder,
                                        shape = CircleShape
                                    )
                                    .background(SentMessageGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!item.author?.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = item.author?.avatarUrl,
                                        contentDescription = name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = name.take(1).uppercase(),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = name.take(10),
                                color = if (isSelected) VirusXSecondaryWhisper else TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                val activeItem = state.stories.getOrNull(state.selectedIndex)
                if (activeItem != null) {
                    ActiveStoryViewerCard(
                        item = activeItem,
                        isMine = activeItem.story.userId == state.currentUserId,
                        onVote = { pollId, idx -> viewModel.voteOnPoll(pollId, idx) },
                        onSendAsk = { storyId, q -> viewModel.submitAnonymousQuestion(storyId, q) },
                        onDelete = { viewModel.deleteStory(activeItem.story.id) }
                    )
                }
            }
        }
    }

    if (state.showCreateDialog) {
        CreateInteractiveStoryDialog(
            onDismiss = viewModel::closeCreateDialog,
            onPublishText = viewModel::publishTextStory,
            onPublishMedia = viewModel::publishMediaStory,
            onPublishPoll = viewModel::publishPollStory,
            onPublishAsk = viewModel::publishAskStory,
            onPublishCountdown = viewModel::publishCountdownStory
        )
    }
}

@Composable
private fun ActiveStoryViewerCard(
    item: StoryWithDetails,
    isMine: Boolean,
    onVote: (String, Int) -> Unit,
    onSendAsk: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    val bgColor = remember(item.story.backgroundColor) {
        runCatching {
            Color(android.graphics.Color.parseColor(item.story.backgroundColor ?: "#1E1B4B"))
        }.getOrElse { VirusXSurface }
    }

    var askInput by remember(item.story.id) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .border(1.dp, VirusXGlassBorder, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = item.author?.displayName ?: "عضو VirusX",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "@${item.author?.username ?: "user"}",
                    color = VirusXSecondaryWhisper,
                    fontSize = 12.sp
                )
            }

            if (isMine) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف الحالة", tint = DangerRed)
                }
            }
        }

        when (item.story.storyType) {
            "media" -> {
                if (!item.story.mediaUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.story.mediaUrl,
                        contentDescription = "صورة الحالة",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
                if (!item.story.textContent.isNullOrBlank()) {
                    Text(
                        text = item.story.textContent,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            "poll" -> {
                val poll = item.poll
                Text(
                    text = "📊 ${poll?.question ?: item.story.textContent.orEmpty()}",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                if (poll != null) {
                    val totalVotes = item.pollVotes.size.coerceAtLeast(1)
                    poll.options.forEachIndexed { idx, optionLabel ->
                        val count = item.pollVotes.count { it.optionIndex == idx }
                        val ratio = if (item.pollVotes.isEmpty()) 0f else count.toFloat() / totalVotes

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .border(1.dp, VirusXGlassBorder, RoundedCornerShape(14.dp))
                                .clickable { onVote(poll.id, idx) }
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(optionLabel, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("${(ratio * 100).toInt()}% ($count)", color = VirusXSecondaryWhisper, fontSize = 12.sp)
                            }
                            LinearProgressIndicator(
                                progress = { ratio },
                                color = VirusXSecondaryWhisper,
                                trackColor = VirusXSurfaceElevated,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }
            }

            "ask" -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(WhisperMessageGradient)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🕵️ اسألني أي شيء (مجهول الهوية):",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = item.story.textContent ?: "اكتب سؤالك بسرية تامة...",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (!isMine) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = askInput,
                                onValueChange = { askInput = it },
                                placeholder = { Text("اكتب سؤالك المجهول هنا...") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (askInput.isNotBlank()) {
                                        onSendAsk(item.story.id, askInput)
                                        askInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(VirusXPrimary)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال", tint = TextPrimary)
                            }
                        }
                    }
                }

                if (isMine) {
                    Text(
                        text = "الأسئلة الواردة لك (${item.anonymousQuestions.size}):",
                        color = VirusXSecondaryWhisper,
                        fontWeight = FontWeight.Bold
                    )
                    item.anonymousQuestions.forEach { q ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(12.dp)
                        ) {
                            Text("❓ ${q.questionText}", color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }

            "countdown" -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, VirusXSecondaryWhisper, RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = VirusXSecondaryWhisper, modifier = Modifier.size(36.dp))
                    Text(
                        text = item.countdown?.title ?: item.story.textContent.orEmpty(),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "الموعد المستهدف: ${item.countdown?.targetDatetime ?: ""}",
                        color = VirusXSecondaryWhisper,
                        fontSize = 13.sp
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.story.textContent.orEmpty(),
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateInteractiveStoryDialog(
    onDismiss: () -> Unit,
    onPublishText: (String, String) -> Unit,
    onPublishMedia: (Uri, String?) -> Unit,
    onPublishPoll: (String, String, String, String) -> Unit,
    onPublishAsk: (String, String) -> Unit,
    onPublishCountdown: (String, String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf("text") }
    var textValue by remember { mutableStateOf("") }
    var option1 by remember { mutableStateOf("") }
    var option2 by remember { mutableStateOf("") }
    var selectedBg by remember { mutableStateOf("#1E1B4B") }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        pickedImageUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VirusXSurface,
        title = { Text("نشر حالة تفاعلية جديدة", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val types = listOf(
                        "text" to "نص",
                        "media" to "صورة",
                        "poll" to "تصويت",
                        "ask" to "سؤال مجهول",
                        "countdown" to "عداد"
                    )
                    itemsIndexed(types) { _, (key, label) ->
                        FilterChip(
                            selected = selectedType == key,
                            onClick = { selectedType = key },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                when (selectedType) {
                    "media" -> {
                        Button(
                            onClick = { imageLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = VirusXSurfaceElevated),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (pickedImageUri != null) "تم اختيار الصورة ✓" else "اختر صورة من المعرض")
                        }
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("تعليق على الصورة (اختياري)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "poll" -> {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("سؤال التصويت") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = option1,
                            onValueChange = { option1 = it },
                            label = { Text("الخيار الأول") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = option2,
                            onValueChange = { option2 = it },
                            label = { Text("الخيار الثاني") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "ask" -> {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("عنوان صندوق الأسئلة (مثال: اسألني أي سؤال بصراحة)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "countdown" -> {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("عنوان الحدث (مثال: امتحان الخوارزميات)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "سيتم ضبط العداد تلقائياً لمدة 24 ساعة من الآن.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    else -> {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("اكتب نص الحالة...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedType) {
                        "media" -> pickedImageUri?.let { onPublishMedia(it, textValue) }
                        "poll" -> onPublishPoll(textValue, option1, option2, selectedBg)
                        "ask" -> onPublishAsk(textValue, selectedBg)
                        "countdown" -> {
                            val targetIso = Instant.now().plus(24, ChronoUnit.HOURS).toString()
                            onPublishCountdown(textValue, targetIso, selectedBg)
                        }
                        else -> onPublishText(textValue, selectedBg)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary)
            ) {
                Text("نشر الآن")
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
    # 5. CUSTOM ADAPTIVE VECTOR APP ICON (VX Shield)
    # =========================================================================
    "app/src/main/res/drawable/ic_launcher_background.xml": r'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108"
                android:type="linear">
                <item android:offset="0" android:color="#161D2F" />
                <item android:offset="0.5" android:color="#0B0E14" />
                <item android:offset="1" android:color="#090D16" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
''',

    "app/src/main/res/drawable/ic_launcher_foreground.xml": r'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="M54,24 L80,39 L80,69 L54,84 L28,69 L28,39 Z"
        android:fillColor="#151A24"
        android:strokeWidth="2.2">
        <aapt:attr name="android:strokeColor">
            <gradient
                android:startX="28"
                android:startY="24"
                android:endX="80"
                android:endY="84"
                android:type="linear">
                <item android:offset="0" android:color="#6366F1" />
                <item android:offset="1" android:color="#06B6D4" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="M34.5,43.5 L40.5,43.5 L46,59.5 L51.5,43.5 L57,43.5 L48.5,66 L43.5,66 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="34"
                android:startY="43"
                android:endX="57"
                android:endY="66"
                android:type="linear">
                <item android:offset="0" android:color="#818CF8" />
                <item android:offset="1" android:color="#6366F1" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="M58,43.5 L63.5,43.5 L66.5,51 L69.5,43.5 L75,43.5 L69.5,54.5 L75.5,66 L70,66 L66.5,58 L63,66 L57.5,66 L63.5,54.5 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="57"
                android:startY="43"
                android:endX="75"
                android:endY="66"
                android:type="linear">
                <item android:offset="0" android:color="#06B6D4" />
                <item android:offset="1" android:color="#8B5CF6" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
''',

    "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml": r'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
''',

    "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml": r'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''
}


def main():
    root = Path.cwd()
    print(f"[*] Applying Task 5 (Part 1) files in: {root}")
    for rel_path, content in FILES.items():
        file_path = root / rel_path
        file_path.parent.mkdir(parents=True, exist_ok=True)
        clean_content = content.replace("\u00a0", " ").strip() + "\n"
        file_path.write_text(clean_content, encoding="utf-8")
        print(f"  [+] Updated/Created: {rel_path}")

    # Link custom app icon in AndroidManifest.xml
    manifest_path = root / "app/src/main/AndroidManifest.xml"
    if manifest_path.exists():
        m_txt = manifest_path.read_text(encoding="utf-8")
        m_txt = m_txt.replace('android:icon="@android:drawable/sym_def_app_icon"', 'android:icon="@mipmap/ic_launcher"')
        m_txt = m_txt.replace('android:roundIcon="@android:drawable/sym_def_app_icon"', 'android:roundIcon="@mipmap/ic_launcher_round"')
        manifest_path.write_text(m_txt, encoding="utf-8")
        print("  [+] Linked custom VirusX icon in AndroidManifest.xml")

    print("\n[✓] Task 5 Part 1 completed successfully.")


if __name__ == "__main__":
    main()