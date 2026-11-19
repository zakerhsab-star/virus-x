package com.virusx.app

import com.virusx.app.util.VirusXEffectsAndNotifications

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VirusXApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        VirusXEffectsAndNotifications.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val messagesChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "الرسائل والمحادثات",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "إشعارات الرسائل الفورية والهمسات"
            enableVibration(true)
        }

        val emergencySound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val emergencyChannel = NotificationChannel(
            CHANNEL_EMERGENCY_DND,
            "طوارئ وضع المحاضرة الذكي",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "تنبيهات الرسائل التي تحتوي على كلمة الطوارئ أثناء تفعيل وضع عدم الإزعاج"
            enableVibration(true)
            setBypassDnd(true)
            setSound(emergencySound, audioAttributes)
        }

        val broadcastChannel = NotificationChannel(
            CHANNEL_BROADCASTS,
            "التنبيهات العامة والإدارية",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "إشعارات إدارة التطبيق والتنبيهات العامة"
            enableVibration(true)
        }

        manager.createNotificationChannels(
            listOf(messagesChannel, emergencyChannel, broadcastChannel)
        )
    }

    companion object {
        const val CHANNEL_MESSAGES = "virusx_messages_channel"
        const val CHANNEL_EMERGENCY_DND = "virusx_emergency_dnd_channel"
        const val CHANNEL_BROADCASTS = "virusx_broadcasts_channel"
    }
}