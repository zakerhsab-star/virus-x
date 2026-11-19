package com.virusx.app.util

import android.content.Context

/**
 * Central state tracker to prevent:
 * 1. Notifications/vibrations while the user is actively viewing the same chat.
 * 2. Finished Roulette games re-spinning and re-playing audio on chat reopen.
 * 3. Self-destructed messages reappearing or restarting their countdown on chat reopen.
 */
object ActiveChatTracker {
    @Volatile
    var currentOpenChatId: String? = null

    @Volatile
    var isChatScreenVisible: Boolean = false

    private const val PREFS_NAME = "virusx_runtime_state_prefs"
    private const val KEY_SEEN_ROULETTES = "seen_roulette_hashes"
    private const val KEY_EXPIRED_MSGS = "expired_message_ids"

    fun hasSeenFinishedGame(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_SEEN_ROULETTES, emptySet()) ?: emptySet()
        return set.contains(key)
    }

    fun markGameFinishedSeen(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(KEY_SEEN_ROULETTES, emptySet()) ?: emptySet()).toMutableSet()
        if (current.size > 120) {
            current.clear()
        }
        current.add(key)
        prefs.edit().putStringSet(KEY_SEEN_ROULETTES, current).apply()
    }

    fun isMessageExpired(context: Context, messageId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_EXPIRED_MSGS, emptySet()) ?: emptySet()
        return set.contains(messageId)
    }

    fun markMessageExpired(context: Context, messageId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(KEY_EXPIRED_MSGS, emptySet()) ?: emptySet()).toMutableSet()
        if (current.size > 300) {
            current.clear()
        }
        current.add(messageId)
        prefs.edit().putStringSet(KEY_EXPIRED_MSGS, current).apply()
    }
}
