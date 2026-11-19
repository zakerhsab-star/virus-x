package com.virusx.app.util

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.virusx.app.data.remote.dto.MessageDto
import com.virusx.app.ui.components.AccountSwitchSupabaseEntryPoint
import dagger.hilt.android.EntryPointAccessors
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

object VirusXEffectsAndNotifications {
    private const val CHANNEL_MESSAGES = "virusx_messages_channel"
    private const val CHANNEL_EMERGENCY = "virusx_emergency_channel_v2"
    const val CHANNEL_MONITOR_BG = "virusx_bg_monitor_channel"

    @Volatile
    private var isSirenLooping = false
    private var activeOverlayView: View? = null

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
                "طوارئ السوبر أدمن العاجلة (تظهر فوق التطبيقات)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "نبضات الطوارئ والاهتزاز الإجباري العابر للتطبيقات"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val bgChannel = NotificationChannel(
                CHANNEL_MONITOR_BG,
                "حماية واستقبال طوارئ VirusX",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "يبقي استقبال تنبيهات الطوارئ والرسائل نشطاً في الخلفية"
                setShowBadge(false)
            }
            nm.createNotificationChannel(msgChannel)
            nm.createNotificationChannel(sirenChannel)
            nm.createNotificationChannel(bgChannel)
        }
    }

    fun startBackgroundMonitorService(context: Context) {
        runCatching {
            val intent = Intent(context.applicationContext, VirusXEmergencyMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(intent)
            } else {
                context.applicationContext.startService(intent)
            }
        }
    }

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    fun showGlobalEmergencyAlert(
        context: Context,
        title: String,
        body: String
    ) {
        val appCtx = context.applicationContext
        triggerForcePingSirenAndVibration(appCtx)

        Handler(Looper.getMainLooper()).post {
            if (canDrawOverlays(appCtx)) {
                showSystemWindowOverlay(appCtx, title, body)
            } else {
                runCatching {
                    val actIntent = Intent(appCtx, EmergencyAlertActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("alert_title", title)
                        putExtra("alert_body", body)
                    }
                    appCtx.startActivity(actIntent)
                }
            }

            showIncomingNotification(
                context = appCtx,
                title = title,
                body = body,
                isEmergency = true
            )
        }
    }

    private fun showSystemWindowOverlay(context: Context, title: String, body: String) {
        runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            activeOverlayView?.let { old ->
                runCatching { wm.removeView(old) }
                activeOverlayView = null
            }

            val density = context.resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density).toInt()

            val rootContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(22), dp(22), dp(22))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(24).toFloat()
                    setColor(AndroidColor.parseColor("#F5280911"))
                    setStroke(dp(2), AndroidColor.parseColor("#FF3B30"))
                }
            }

            val headerTv = TextView(context).apply {
                text = "🚨 تنبيه طوارئ عاجل!"
                setTextColor(AndroidColor.parseColor("#FF4757"))
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
            }

            val subHeaderTv = TextView(context).apply {
                text = title
                setTextColor(AndroidColor.parseColor("#FFD54F"))
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(10))
            }

            val bodyTv = TextView(context).apply {
                text = body
                setTextColor(AndroidColor.WHITE)
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(8), dp(6), dp(18))
            }

            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val openAppBtn = Button(context).apply {
                text = "🚀 فتح المحادثة الآن"
                setTextColor(AndroidColor.BLACK)
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(AndroidColor.parseColor("#00E676"))
                }
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setOnClickListener {
                    stopEmergencySirenAndDismissOverlay(context)
                    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    if (launch != null) context.startActivity(launch)
                }
            }

            val spacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(1))
            }

            val dismissBtn = Button(context).apply {
                text = "علم — إيقاف الإنذار"
                setTextColor(AndroidColor.WHITE)
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(AndroidColor.parseColor("#FF3B30"))
                }
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setOnClickListener {
                    stopEmergencySirenAndDismissOverlay(context)
                }
            }

            btnRow.addView(openAppBtn)
            btnRow.addView(spacer)
            btnRow.addView(dismissBtn)

            rootContainer.addView(headerTv)
            rootContainer.addView(subHeaderTv)
            rootContainer.addView(bodyTv)
            rootContainer.addView(btnRow)

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.90).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            wm.addView(rootContainer, params)
            activeOverlayView = rootContainer
        }
    }

    fun stopEmergencySirenAndDismissOverlay(context: Context) {
        isSirenLooping = false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.cancel()
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.cancel()
            }
        }
        Handler(Looper.getMainLooper()).post {
            activeOverlayView?.let { view ->
                runCatching {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.removeView(view)
                }
                activeOverlayView = null
            }
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

            val fullScreenIntent = if (isEmergency) {
                val alertAct = Intent(context, EmergencyAlertActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("alert_title", title)
                    putExtra("alert_body", body)
                }
                PendingIntent.getActivity(
                    context,
                    911,
                    alertAct,
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
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(if (isEmergency) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)

            if (pendingIntent != null) {
                builder.setContentIntent(pendingIntent)
            }
            if (fullScreenIntent != null) {
                builder.setFullScreenIntent(fullScreenIntent, true)
            }

            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
        }
    }

    fun triggerForcePingSirenAndVibration(context: Context) {
        isSirenLooping = true
        runCatching {
            val pattern = longArrayOf(0, 600, 180, 600, 180, 700, 180, 900, 200, 800)
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
        playSoundEffect("SIREN")
    }

    fun playSoundEffect(effectKey: String) {
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                when (effectKey.uppercase()) {
                    "SIREN" -> playSynthWave(
                        listOf(
                            880.0 to 250, 1320.0 to 250,
                            880.0 to 250, 1320.0 to 280,
                            980.0 to 250, 1480.0 to 320,
                            880.0 to 250, 1320.0 to 380
                        )
                    )
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
                val raw = (sin(angle) * 0.75 + sin(angle * 2.0) * 0.25) * env
                pcm[cursor++] = (raw * Short.MAX_VALUE * 0.88).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
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

class EmergencyAlertActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val title = intent?.getStringExtra("alert_title") ?: "🚨 تنبيه طوارئ VirusX"
        val body = intent?.getStringExtra("alert_body") ?: "استدعاء عاجل — افتح المحادثة فوراً!"
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(AndroidColor.parseColor("#B3000000"))
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(22), dp(22), dp(22))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(AndroidColor.parseColor("#F5280911"))
                setStroke(dp(2), AndroidColor.parseColor("#FF3B30"))
            }
        }

        val headerTv = TextView(this).apply {
            text = "🚨 تنبيه طوارئ عاجل!"
            setTextColor(AndroidColor.parseColor("#FF4757"))
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val subTv = TextView(this).apply {
            text = title
            setTextColor(AndroidColor.parseColor("#FFD54F"))
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(10))
        }

        val bodyTv = TextView(this).apply {
            text = body
            setTextColor(AndroidColor.WHITE)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(18))
        }

        val openBtn = Button(this).apply {
            text = "🚀 فتح المحادثة الآن"
            setTextColor(AndroidColor.BLACK)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(AndroidColor.parseColor("#00E676"))
            }
            setOnClickListener {
                VirusXEffectsAndNotifications.stopEmergencySirenAndDismissOverlay(this@EmergencyAlertActivity)
                val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                if (launch != null) startActivity(launch)
                finish()
            }
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(10))
        }

        val closeBtn = Button(this).apply {
            text = "علم — إيقاف الإنذار"
            setTextColor(AndroidColor.WHITE)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(AndroidColor.parseColor("#FF3B30"))
            }
            setOnClickListener {
                VirusXEffectsAndNotifications.stopEmergencySirenAndDismissOverlay(this@EmergencyAlertActivity)
                finish()
            }
        }

        card.addView(headerTv)
        card.addView(subTv)
        card.addView(bodyTv)
        card.addView(openBtn)
        card.addView(spacer)
        card.addView(closeBtn)
        outer.addView(card)
        setContentView(outer)
    }
}

class VirusXEmergencyMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        VirusXEffectsAndNotifications.ensureChannels(this)
        runCatching {
            val notif = NotificationCompat.Builder(this, VirusXEffectsAndNotifications.CHANNEL_MONITOR_BG)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("VirusX نشط في الخلفية")
                .setContentText("حماية واستقبال تنبيهات الطوارئ والرسائل العاجلة")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
            startForeground(7701, notif)
        }
        startPollingForEmergencyPings()
    }

    private fun startPollingForEmergencyPings() {
        if (monitorJob?.isActive == true) return
        val prefs = getSharedPreferences("virusx_bg_monitor_prefs", Context.MODE_PRIVATE)

        monitorJob = serviceScope.launch {
            val entryPoint = runCatching {
                EntryPointAccessors.fromApplication(applicationContext, AccountSwitchSupabaseEntryPoint::class.java)
            }.getOrNull() ?: return@launch

            var lastSeenMessageId = prefs.getString("last_bg_msg_id", null)

            while (isActive) {
                runCatching {
                    val latestList = entryPoint.postgrest().from("messages")
                        .select {
                            order("created_at", Order.DESCENDING)
                            limit(1)
                        }
                        .decodeList<MessageDto>()

                    val latest = latestList.firstOrNull()
                    if (latest != null) {
                        if (lastSeenMessageId == null) {
                            lastSeenMessageId = latest.id
                            prefs.edit().putString("last_bg_msg_id", latest.id).apply()
                        } else if (latest.id != lastSeenMessageId) {
                            lastSeenMessageId = latest.id
                            prefs.edit().putString("last_bg_msg_id", latest.id).apply()

                            val myUid = getSharedPreferences("virusx_current_user", Context.MODE_PRIVATE)
                                .getString("uid", null)
                            val isFromOther = myUid == null || latest.senderId != myUid
                            val raw = latest.content.orEmpty()

                            val isViewingSameChat = ActiveChatTracker.isChatScreenVisible &&
                                (ActiveChatTracker.currentOpenChatId == null || ActiveChatTracker.currentOpenChatId == latest.chatId)
                            if (isFromOther && !latest.isDeleted && !isViewingSameChat) {
                                if (raw.startsWith("[FORCE_PING]")) {
                                    val alertBody = raw.removePrefix("[FORCE_PING]").trim()
                                        .ifBlank { "🚨 استدعاء عاجل من المشرف العام — افتح المحادثة فوراً!" }
                                    VirusXEffectsAndNotifications.showGlobalEmergencyAlert(
                                        context = applicationContext,
                                        title = "من خارج التطبيق • تنبيه طوارئ VirusX",
                                        body = alertBody
                                    )
                                } else if (!raw.startsWith("[BADGE_ASSIGN]") && !raw.startsWith("[GAME_") && !raw.startsWith("[EVENT_")) {
                                    VirusXEffectsAndNotifications.showIncomingNotification(
                                        context = applicationContext,
                                        title = "رسالة جديدة في VirusX",
                                        body = raw.ifBlank { "📎 مرفق جديد" },
                                        isEmergency = raw.contains("@")
                                    )
                                }
                            }
                        }
                    }
                }
                delay(4500L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
