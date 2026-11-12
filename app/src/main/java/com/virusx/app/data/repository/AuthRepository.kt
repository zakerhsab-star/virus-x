package com.virusx.app.data.repository

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
