package com.virusx.app.util

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
