package com.virusx.app.util

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.virusx.app.ui.components.AccountSwitchSupabaseEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class VirusXFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveAndSyncFcmToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val chatId = data["chat_id"]
        val type = data["type"] ?: "message"
        val title = data["title"] ?: message.notification?.title ?: "VirusX"
        val body = data["body"] ?: message.notification?.body ?: "رسالة جديدة"

        val isViewingSameChat = ActiveChatTracker.isChatScreenVisible &&
            !chatId.isNullOrBlank() &&
            ActiveChatTracker.currentOpenChatId == chatId

        if (isViewingSameChat && type != "force_ping") return

        if (type == "force_ping" || body.startsWith("[FORCE_PING]")) {
            val cleanBody = body.removePrefix("[FORCE_PING]").trim()
                .ifBlank { "🚨 استدعاء عاجل — افتح المحادثة فوراً!" }
            VirusXEffectsAndNotifications.showGlobalEmergencyAlert(
                context = applicationContext,
                title = title,
                body = cleanBody
            )
        } else {
            VirusXEffectsAndNotifications.showIncomingNotification(
                context = applicationContext,
                title = title,
                body = body,
                isEmergency = body.contains("@")
            )
        }
    }

    companion object {
        private const val PREFS = "virusx_fcm_prefs"
        private const val KEY_TOKEN = "fcm_token"

        fun syncCurrentToken(context: Context) {
            runCatching {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        saveAndSyncFcmToken(context.applicationContext, token)
                    }
                }
            }
        }

        fun saveAndSyncFcmToken(context: Context, token: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TOKEN, token)
                .apply()

            val uid = context.getSharedPreferences("virusx_current_user", Context.MODE_PRIVATE)
                .getString("uid", null) ?: return

            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val ep = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        AccountSwitchSupabaseEntryPoint::class.java
                    )
                    ep.postgrest().from("profiles").update(
                        buildJsonObject {
                            put("fcm_token", JsonPrimitive(token))
                        }
                    ) {
                        filter { eq("id", uid) }
                    }
                }
            }
        }
    }
}
