package com.virusx.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

object VirusXEffectsAndNotifications {
    private const val CHANNEL_MESSAGES = "virusx_messages_channel"
    private const val CHANNEL_EMERGENCY = "virusx_emergency_channel"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val msgChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "رسائل ومنشن VirusX",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات الرسائل الجديدة والإشارات (@منشن)"
                enableVibration(true)
            }
            val sirenChannel = NotificationChannel(
                CHANNEL_EMERGENCY,
                "تنبيهات الطوارئ (Super Admin)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "نبضات الطوارئ والاهتزاز الإجباري"
                enableVibration(true)
            }
            nm.createNotificationChannel(msgChannel)
            nm.createNotificationChannel(sirenChannel)
        }
    }

    fun showIncomingNotification(
        context: Context,
        title: String,
        body: String,
        isEmergency: Boolean = false
    ) {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        runCatching {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = if (launchIntent != null) {
                PendingIntent.getActivity(
                    context,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else null

            val builder = NotificationCompat.Builder(
                context,
                if (isEmergency) CHANNEL_EMERGENCY else CHANNEL_MESSAGES
            )
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            if (pendingIntent != null) {
                builder.setContentIntent(pendingIntent)
            }

            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
        }
    }

    fun triggerForcePingSirenAndVibration(context: Context) {
        // 1. Strong Tactical Vibration Pattern (5 seconds)
        runCatching {
            val pattern = longArrayOf(0, 500, 180, 500, 180, 600, 180, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, -1)
                }
            }
        }
        // 2. Synthesized High-Tech Siren Sound
        playSoundEffect("SIREN")
    }

    fun playSoundEffect(effectKey: String) {
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                when (effectKey.uppercase()) {
                    "SIREN" -> playSynthWave(listOf(880.0 to 240, 1320.0 to 240, 880.0 to 240, 1320.0 to 300, 980.0 to 250, 1480.0 to 350))
                    "AIRHORN" -> playSynthWave(listOf(480.0 to 160, 480.0 to 160, 480.0 to 520))
                    "DRUMROLL" -> playSynthWave(listOf(180.0 to 90, 200.0 to 90, 220.0 to 90, 250.0 to 90, 300.0 to 120, 620.0 to 380))
                    "SHOCK" -> playSynthWave(listOf(1500.0 to 80, 600.0 to 80, 1650.0 to 110, 420.0 to 280))
                    "VICTORY" -> playSynthWave(listOf(523.25 to 140, 659.25 to 140, 783.99 to 160, 1046.50 to 400))
                    "GAMEOVER" -> playSynthWave(listOf(600.0 to 200, 540.0 to 200, 480.0 to 220, 360.0 to 500))
                    else -> {
                        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
                        delay(400)
                        tg.release()
                    }
                }
            }
        }
    }

    private fun playSynthWave(notes: List<Pair<Double, Int>>) {
        val sampleRate = 22050
        val totalMs = notes.sumOf { it.second }
        val totalSamples = (sampleRate * totalMs) / 1000
        val pcm = ShortArray(totalSamples)
        var cursor = 0

        for ((freq, durMs) in notes) {
            val count = (sampleRate * durMs) / 1000
            for (i in 0 until count) {
                if (cursor >= totalSamples) break
                val env = if (i < count * 0.1) (i / (count * 0.1))
                else if (i > count * 0.85) ((count - i) / (count * 0.15))
                else 1.0
                val angle = 2.0 * PI * i * freq / sampleRate
                // Add slight harmonic for rich pro soundboard feel
                val raw = (sin(angle) * 0.75 + sin(angle * 2.0) * 0.25) * env
                pcm[cursor++] = (raw * Short.MAX_VALUE * 0.85).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep(totalMs.toLong() + 120L)
        track.stop()
        track.release()
    }
}
