package com.virusx.app.ui.chat

import com.virusx.app.util.ActiveChatTracker

import android.Manifest
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.virusx.app.data.local.entity.MessageEntity
import com.virusx.app.data.remote.dto.ChatSyncSessionDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.ui.components.AccountSwitchSupabaseEntryPoint
import com.virusx.app.ui.theme.*
import com.virusx.app.util.VirusXEffectsAndNotifications
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val supabaseEntry = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, AccountSwitchSupabaseEntryPoint::class.java)
    }

    val state by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val activeSyncSession by viewModel.activeSyncSession.collectAsState()
    val typingUsers by viewModel.typingUsers.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val liveWaveform by viewModel.liveWaveform.collectAsState()
    val recordingDuration by viewModel.recordingDurationSeconds.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()

    var showPlusMenuSheet by remember { mutableStateOf(false) }
    var showSoundboardDialog by remember { mutableStateOf(false) }
    var showGamePickerDialog by remember { mutableStateOf(false) }
    var showBadgeManagerDialog by remember { mutableStateOf(false) }
    var showWhisperTargetDialog by remember { mutableStateOf(false) }
    var showStartSyncDialog by remember { mutableStateOf(false) }
    var showGroupMembersDialog by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var selectedMessageForActions by remember { mutableStateOf<MessageEntity?>(null) }
    var isStealthCalculatorActive by remember { mutableStateOf(false) }
    var showStickerDrawer by remember { mutableStateOf(false) }
    var emergencyAlertPopupText by remember { mutableStateOf<String?>(null) }

    // Request Notification Permission automatically on Android 13+
    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    DisposableEffect(chatId) {
        ActiveChatTracker.currentOpenChatId = chatId
        ActiveChatTracker.isChatScreenVisible = true
        onDispose {
            ActiveChatTracker.isChatScreenVisible = false
            if (ActiveChatTracker.currentOpenChatId == chatId) {
                ActiveChatTracker.currentOpenChatId = null
            }
        }
    }

    LaunchedEffect(state.currentUserId) {
        if (!state.currentUserId.isNullOrBlank()) {
            context.getSharedPreferences("virusx_current_user", Context.MODE_PRIVATE)
                .edit()
                .putString("uid", state.currentUserId)
                .apply()
        }
    }

    LaunchedEffect(Unit) {
        VirusXEffectsAndNotifications.ensureChannels(context)
        VirusXEffectsAndNotifications.startBackgroundMonitorService(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Real Presence Heartbeat + Lifecycle Observer (Fixes stuck "متصل الآن")
    var isScreenResumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isScreenResumed = true
                    coroutineScope.launch(Dispatchers.IO) {
                        runCatching {
                            supabaseEntry.postgrest().rpc(
                                "ping_user_presence",
                                buildJsonObject { put("_is_online", JsonPrimitive(true)) }
                            )
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isScreenResumed = false
                    coroutineScope.launch(Dispatchers.IO) {
                        runCatching {
                            supabaseEntry.postgrest().rpc(
                                "ping_user_presence",
                                buildJsonObject { put("_is_online", JsonPrimitive(false)) }
                            )
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Periodic 22-second heartbeat while active + verify peer real online timestamp
    var verifiedPeerOnline by remember { mutableStateOf(false) }
    LaunchedEffect(chatId, isScreenResumed, state.members, state.peerOnline) {
        while (true) {
            if (isScreenResumed) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        supabaseEntry.postgrest().rpc(
                            "ping_user_presence",
                            buildJsonObject { put("_is_online", JsonPrimitive(true)) }
                        )
                        supabaseEntry.postgrest().rpc(
                            "mark_chat_messages_read",
                            buildJsonObject { put("_chat_id", JsonPrimitive(chatId)) }
                        )
                    }
                }
            }
            // Check peer's real last_seen (< 45 seconds) so closed-internet friends never show stuck "متصل الآن"
            val otherPeer = state.members.firstOrNull { it.id != state.currentUserId }
            if (otherPeer != null && !state.isGroup) {
                val freshDto = withContext(Dispatchers.IO) {
                    runCatching {
                        supabaseEntry.postgrest().from("profiles")
                            .select { filter { eq("id", otherPeer.id) } }
                            .decodeSingleOrNull<ProfileDto>()
                    }.getOrNull()
                }
                if (freshDto != null) {
                    val recentEnough = runCatching {
                        val ls = freshDto.lastSeen
                        if (ls.isNullOrBlank()) true
                        else {
                            val instant = Instant.parse(ls)
                            ChronoUnit.SECONDS.between(instant, Instant.now()) <= 50L
                        }
                    }.getOrDefault(freshDto.isOnline)
                    verifiedPeerOnline = freshDto.isOnline && recentEnough
                } else {
                    verifiedPeerOnline = state.peerOnline
                }
            } else {
                verifiedPeerOnline = state.peerOnline
            }
            delay(20000L)
        }
    }

    // Local persistent states for Pinned Message, Admin-Only Lock, Promoted Admins, and Custom Badges
    val chatPrefs = remember(chatId) {
        context.getSharedPreferences("virusx_chat_meta_$chatId", Context.MODE_PRIVATE)
    }
    var pinnedMessageId by remember(chatId) {
        mutableStateOf(chatPrefs.getString("pinned_id", null))
    }
    var pinnedMessageText by remember(chatId) {
        mutableStateOf(chatPrefs.getString("pinned_text", null))
    }
    var isAdminOnlyChat by remember(chatId) {
        mutableStateOf(chatPrefs.getBoolean("admin_only", false))
    }
    var promotedAdminIds by remember(chatId) {
        mutableStateOf(chatPrefs.getStringSet("promoted_admins", emptySet()) ?: emptySet())
    }
    var customBadgesMap by remember(chatId) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }

    // Load custom badges from profiles
    LaunchedEffect(state.members) {
        val map = HashMap<String, String>()
        state.members.forEach { m ->
            if (!m.customBadge.isNullOrBlank()) {
                map[m.id] = m.customBadge
            }
        }
        if (map.isNotEmpty()) {
            customBadgesMap = customBadgesMap + map
        }
    }

    // Track last processed incoming message ID so we fire notifications, soundboard, and Force Ping only once
    var lastHandledMsgId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(messages) {
        val latest = messages.lastOrNull() ?: return@LaunchedEffect
        val raw = latest.content.orEmpty()

        // Always sync pinned/admin/badge metadata from latest messages
        messages.takeLast(15).forEach { m ->
            val txt = m.content.orEmpty()
            when {
                txt.startsWith("📌 [رسالة مثبتة]: ") -> {
                    val body = txt.removePrefix("📌 [رسالة مثبتة]: ").trim()
                    pinnedMessageId = m.replyToMessageId ?: m.id
                    pinnedMessageText = body
                    chatPrefs.edit()
                        .putString("pinned_id", pinnedMessageId)
                        .putString("pinned_text", pinnedMessageText)
                        .apply()
                }
                txt == "🔒 قام المشرف بقفل الدردشة (الإرسال للمشرفين فقط)" -> {
                    isAdminOnlyChat = true
                    chatPrefs.edit().putBoolean("admin_only", true).apply()
                }
                txt == "🔓 قام المشرف بفتح الدردشة لجميع الأعضاء" -> {
                    isAdminOnlyChat = false
                    chatPrefs.edit().putBoolean("admin_only", false).apply()
                }
                txt.startsWith("[BADGE_ASSIGN]") -> {
                    // Format: [BADGE_ASSIGN]|userId|badgeText
                    val parts = txt.split("|")
                    if (parts.size >= 3) {
                        val targetUid = parts[1]
                        val badgeVal = parts[2]
                        customBadgesMap = if (badgeVal.isBlank()) customBadgesMap - targetUid
                        else customBadgesMap + (targetUid to badgeVal)
                    }
                }
            }
        }

        if (lastHandledMsgId == null) {
            lastHandledMsgId = latest.id
            return@LaunchedEffect
        }

        if (latest.id != lastHandledMsgId) {
            lastHandledMsgId = latest.id
            val isFromOther = latest.senderId != state.currentUserId

            // 1. Mark message as delivered/read when received
            if (isFromOther) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        supabaseEntry.postgrest().rpc(
                            if (isScreenResumed) "mark_chat_messages_read" else "mark_chat_messages_delivered",
                            buildJsonObject { put("_chat_id", JsonPrimitive(chatId)) }
                        )
                    }
                }
            }

            // 2. Check for Super Admin Force Ping Siren Alert
            if (isFromOther && raw.startsWith("[FORCE_PING]")) {
                val alertBody = raw.removePrefix("[FORCE_PING]").trim().ifBlank { "🚨 استدعاء طوارئ عاجل من المشرف العام!" }
                if (!isScreenResumed || VirusXEffectsAndNotifications.canDrawOverlays(context)) {
                    VirusXEffectsAndNotifications.showGlobalEmergencyAlert(
                        context = context,
                        title = "🚨 استدعاء طوارئ من ${state.chatTitle}",
                        body = alertBody
                    )
                } else {
                    VirusXEffectsAndNotifications.triggerForcePingSirenAndVibration(context)
                    emergencyAlertPopupText = alertBody
                    if (isFromOther) {
                        VirusXEffectsAndNotifications.showIncomingNotification(
                            context = context,
                            title = "🚨 تنبيه طوارئ عاجل (${state.chatTitle})",
                            body = alertBody,
                            isEmergency = true
                        )
                    }
                }
            }
            // 3. Check for Interactive Soundboard Effect
            else if (raw.startsWith("[SOUNDBOARD:")) {
                val effectKey = raw.substringAfter("[SOUNDBOARD:").substringBefore("]").trim()
                VirusXEffectsAndNotifications.playSoundEffect(effectKey)
            }
            // 4. Fire Android System Notification if from another user (on mention, group message, or private chat)
            else if (isFromOther && !latest.isDeleted) {
                val senderName = state.members.firstOrNull { it.id == latest.senderId }?.displayName ?: state.chatTitle
                val isMentioned = raw.contains("@الكل") || raw.contains("@")
                if (!isScreenResumed || isMentioned || !state.isGroup) {
                    val notifTitle = if (isMentioned) "📢 ذكرك $senderName في ${state.chatTitle}"
                    else if (state.isGroup) "${state.chatTitle} • $senderName"
                    else senderName
                    VirusXEffectsAndNotifications.showIncomingNotification(
                        context = context,
                        title = notifTitle,
                        body = raw.ifBlank { "📎 مرفق جديد" },
                        isEmergency = isMentioned
                    )
                }
            }
        }
    }

    val isCurrentUserGroupAdmin = remember(state.currentUserId, state.chatCreatorId, promotedAdminIds) {
        val uid = state.currentUserId
        uid != null && (uid == state.chatCreatorId || promotedAdminIds.contains(uid))
    }

    val listState = rememberLazyListState()

    DisposableEffect(context, chatId) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (uri != null && uri.toString().contains(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())) {
                    runCatching {
                        val projection = arrayOf(
                            MediaStore.Images.Media.DISPLAY_NAME,
                            MediaStore.Images.Media.RELATIVE_PATH,
                            MediaStore.Images.Media.DATE_ADDED
                        )
                        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val name = cursor.getString(0).orEmpty().lowercase()
                                val path = cursor.getString(1).orEmpty().lowercase()
                                val addedSec = cursor.getLong(2)
                                val nowSec = System.currentTimeMillis() / 1000L
                                if ((name.contains("screenshot") || path.contains("screenshot")) &&
                                    kotlin.math.abs(nowSec - addedSec) <= 5L
                                ) {
                                    viewModel.reportScreenshotTaken()
                                }
                            }
                        }
                    }
                }
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        }
        onDispose {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }

    LaunchedEffect(chatId) {
        viewModel.initChat(chatId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // 100% TOUCH ISOLATION FOR STEALTH CALCULATOR:
    if (isStealthCalculatorActive) {
        StealthCalculatorOverlay(
            secretCode = "2000",
            onUnlock = { isStealthCalculatorActive = false }
        )
        return
    }

    // Parallel Live Speech Capture during Voice Recording so every voice note gets real transcript!
    var liveCapturedSpeech by remember { mutableStateOf("") }
    DisposableEffect(isRecording) {
        var recognizer: SpeechRecognizer? = null
        if (isRecording && SpeechRecognizer.isRecognitionAvailable(context)) {
            runCatching {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onError(error: Int) {}
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                liveCapturedSpeech = matches.first()
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!partial.isNullOrEmpty()) {
                                liveCapturedSpeech = partial.first()
                            }
                        }
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    }
                    startListening(intent)
                }
            }
        }
        onDispose {
            runCatching {
                recognizer?.stopListening()
                recognizer?.destroy()
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            liveCapturedSpeech = ""
            viewModel.startVoiceRecording()
        }
    }

    val speechPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.transcribeSpeechIntoInput()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.sendImageMessage(uri)
    }

    val memberMap = remember(state.members) { state.members.associateBy { it.id } }
    val messageMap = remember(messages) { messages.associateBy { it.id } }

    // Automatic @Mention detection (NO extra button needed - triggers as soon as user types @)
    val mentionQuery: String? = remember(state.inputText, state.isGroup) {
        if (!state.isGroup) null
        else {
            val lastWord = state.inputText.substringAfterLast(" ", state.inputText)
            if (lastWord.startsWith("@") && lastWord.isNotEmpty()) {
                lastWord.removePrefix("@").lowercase()
            } else null
        }
    }

    Scaffold(
        containerColor = VirusXBackground,
        topBar = {
            Column {
                ChatTopBar(
                    title = state.chatTitle,
                    isGroup = state.isGroup,
                    memberCount = state.members.size,
                    peerOnline = verifiedPeerOnline,
                    isRecordingAudio = isRecording,
                    typingUsers = typingUsers.values.toList(),
                    isAdminOnly = isAdminOnlyChat,
                    onBack = onBack,
                    onActivateStealth = {
                        showGroupMembersDialog = false
                        showStartSyncDialog = false
                        showWhisperTargetDialog = false
                        showPlusMenuSheet = false
                        selectedMessageForActions = null
                        isStealthCalculatorActive = true
                    },
                    onOpenGroupMembers = { showGroupMembersDialog = true },
                    onOpenSyncDialog = { showStartSyncDialog = true }
                )

                if (!pinnedMessageText.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VirusXSurfaceElevated)
                            .border(0.5.dp, VirusXSecondaryWhisper.copy(alpha = 0.4f))
                            .clickable {
                                val targetIdx = messages.indexOfFirst { it.id == pinnedMessageId }
                                if (targetIdx >= 0) {
                                    coroutineScope.launch { listState.animateScrollToItem(targetIdx) }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "رسالة مثبتة",
                            tint = WarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("رسالة مثبتة 📌", color = WarningAmber, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(
                                text = pinnedMessageText.orEmpty(),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isCurrentUserGroupAdmin || !state.isGroup) {
                            IconButton(
                                onClick = {
                                    pinnedMessageId = null
                                    pinnedMessageText = null
                                    chatPrefs.edit().remove("pinned_id").remove("pinned_text").apply()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إلغاء التثبيت", tint = TextSecondary, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            activeSyncSession?.let { session ->
                SyncMiniPlayerBar(
                    session = session,
                    onTogglePlayPause = viewModel::toggleSyncSessionPlayPause,
                    onSeekBackward = { viewModel.seekSyncSessionBy(-10.0) },
                    onSeekForward = { viewModel.seekSyncSessionBy(10.0) }
                )
            }

            state.statusBanner?.let { banner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OnlineGreen.copy(alpha = 0.2f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(banner, color = OnlineGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::clearError, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(15.dp))
                    }
                }
            }

            state.errorMessage?.let { err ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DangerRed.copy(alpha = 0.2f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(err, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::clearError, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == state.currentUserId
                    val senderProfile = memberMap[msg.senderId]
                    val whisperTargetProfile = msg.whisperTargetId?.let { memberMap[it] }
                    val repliedMessage = msg.replyToMessageId?.let { messageMap[it] }
                    val resolvedName = if (isMine) "${state.currentUserName} (أنت)"
                    else (senderProfile?.displayName ?: if (!state.isGroup) state.chatTitle else "عضو")
                    val senderBadge = customBadgesMap[msg.senderId] ?: senderProfile?.customBadge

                    MessageBubbleItem(
                        message = msg,
                        isMine = isMine,
                        isGroup = state.isGroup,
                        peerOnline = verifiedPeerOnline,
                        currentUserId = state.currentUserId,
                        currentUserName = state.currentUserName,
                        senderName = resolvedName,
                        senderBadge = senderBadge,
                        senderAvatarUrl = senderProfile?.avatarUrl,
                        whisperTargetName = whisperTargetProfile?.displayName,
                        repliedMessage = repliedMessage,
                        onOpenFullImage = { url -> fullScreenImageUrl = url },
                        onExpireSelfDestruct = { viewModel.expireSelfDestructMessage(msg.id) },
                        onToggleReaction = { emoji -> viewModel.toggleMessageReaction(msg.id, emoji) },
                        onUpdateGameMessage = { updatedGameContent ->
                            coroutineScope.launch(Dispatchers.IO) {
                                runCatching {
                                    supabaseEntry.postgrest().rpc(
                                        "update_interactive_game_state",
                                        buildJsonObject {
                                            put("_message_id", JsonPrimitive(msg.id))
                                            put("_new_content", JsonPrimitive(updatedGameContent))
                                        }
                                    )
                                }.onFailure {
                                    runCatching {
                                        supabaseEntry.postgrest().from("messages").update(
                                            buildJsonObject { put("content", JsonPrimitive(updatedGameContent)) }
                                        ) { filter { eq("id", msg.id) } }
                                    }
                                }
                            }
                        },
                        onSaveVoiceTranscript = { newTranscript ->
                            coroutineScope.launch(Dispatchers.IO) {
                                runCatching {
                                    supabaseEntry.postgrest().from("messages").update(
                                        buildJsonObject { put("voice_transcript", JsonPrimitive(newTranscript)) }
                                    ) { filter { eq("id", msg.id) } }
                                }
                            }
                        },
                        onSwipeReply = { viewModel.setReplyToMessage(msg) },
                        onLongPress = { selectedMessageForActions = msg }
                    )
                }
            }

            // Automatic @Mention Popup when user types '@'
            if (mentionQuery != null) {
                val filteredMembers = state.members.filter {
                    it.id != state.currentUserId &&
                        (it.username.lowercase().contains(mentionQuery) || it.displayName.lowercase().contains(mentionQuery))
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 170.dp)
                        .background(VirusXSurfaceElevated)
                        .border(0.5.dp, VirusXSecondaryWhisper)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(VirusXPrimary.copy(alpha = 0.22f))
                            .clickable {
                                val prefix = state.inputText.substringBeforeLast("@")
                                viewModel.onInputTextChange("$prefix@الكل ")
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📢 @الكل", color = WarningAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إشارة جماعية وتنبيه لكل أعضاء الجروب", color = TextSecondary, fontSize = 11.sp)
                    }

                    filteredMembers.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val prefix = state.inputText.substringBeforeLast("@")
                                    viewModel.onInputTextChange("$prefix@${member.username} ")
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarCircle(
                                displayName = member.displayName,
                                avatarUrl = member.avatarUrl,
                                sizeDp = 26
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(member.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("@${member.username}", color = VirusXSecondaryWhisper, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (showStickerDrawer) {
                StickerDrawerBar(
                    context = context,
                    onSendQuickSticker = { stickerText ->
                        viewModel.onInputTextChange(stickerText)
                        viewModel.sendTextMessage()
                        showStickerDrawer = false
                    },
                    onSendStickerUri = { uri ->
                        viewModel.sendImageMessage(uri)
                        showStickerDrawer = false
                    },
                    onImportStickerFromDevice = {
                        showStickerDrawer = false
                        imagePickerLauncher.launch("image/*")
                    }
                )
            }

            if (isAdminOnlyChat && state.isGroup && !isCurrentUserGroupAdmin) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VirusXSurface)
                        .border(0.5.dp, VirusXGlassBorder)
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔒 هذه المجموعة مقفلة حالياً — الإرسال متاح للمشرفين فقط",
                        color = WarningAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                CleanSingleLineComposer(
                    state = state,
                    isRecording = isRecording,
                    liveWaveform = liveWaveform,
                    recordingDuration = recordingDuration,
                    liveCapturedSpeech = liveCapturedSpeech,
                    showStickerDrawer = showStickerDrawer,
                    onToggleStickerDrawer = { showStickerDrawer = !showStickerDrawer },
                    onOpenPlusMenu = { showPlusMenuSheet = true },
                    onInputChange = viewModel::onInputTextChange,
                    onSendText = viewModel::sendTextMessage,
                    onReceiveKeyboardStickerUri = { uri -> viewModel.sendImageMessage(uri) },
                    onClearReply = { viewModel.setReplyToMessage(null) },
                    onCancelEdit = viewModel::cancelEditingMessage,
                    onClearWhisper = { viewModel.setWhisperTarget(null) },
                    onSelectPitchFilter = viewModel::setPitchFilter,
                    onToggleViewOnce = viewModel::toggleVoiceViewOnce,
                    onStartRecording = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            liveCapturedSpeech = ""
                            viewModel.startVoiceRecording()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onCancelRecording = {
                        liveCapturedSpeech = ""
                        viewModel.cancelVoiceRecording()
                    },
                    onSendVoice = {
                        val captured = liveCapturedSpeech.trim()
                        viewModel.stopAndSendVoiceNote()
                        if (captured.isNotBlank()) {
                            coroutineScope.launch(Dispatchers.IO) {
                                delay(1600L)
                                val lastVoice = messages.lastOrNull { it.senderId == state.currentUserId && it.messageType == "voice" }
                                if (lastVoice != null) {
                                    runCatching {
                                        supabaseEntry.postgrest().from("messages").update(
                                            buildJsonObject { put("voice_transcript", JsonPrimitive(captured)) }
                                        ) { filter { eq("id", lastVoice.id) } }
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    // Organized Plus (+) Bottom Sheet Menu (Replaces cluttered bottom chips!)
    if (showPlusMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlusMenuSheet = false },
            containerColor = VirusXSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "✨ الأدوات والمرفقات التفاعلية",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PlusMenuTile(
                        icon = "📷",
                        title = "صورة / ملف",
                        subtitle = "من المعرض",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showPlusMenuSheet = false
                            imagePickerLauncher.launch("image/*")
                        }
                    )
                    PlusMenuTile(
                        icon = "🔊",
                        title = "مؤثرات صوتية",
                        subtitle = "Soundboard حي",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showPlusMenuSheet = false
                            showSoundboardDialog = true
                        }
                    )
                    PlusMenuTile(
                        icon = "🎮",
                        title = "تحدي سريع",
                        subtitle = "X/O أو حجر-ورقة",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showPlusMenuSheet = false
                            showGamePickerDialog = true
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val timerLabel = state.destructTimerSeconds?.let { "${it}ث نشط" } ?: "إيقاف"
                    PlusMenuTile(
                        icon = "⏳",
                        title = "مؤقت التدمير",
                        subtitle = timerLabel,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.cycleDestructTimer()
                        }
                    )
                    if (state.isGroup) {
                        PlusMenuTile(
                            icon = "🔒",
                            title = "همسة سرية",
                            subtitle = state.whisperTargetUser?.displayName ?: "لعضو محدد",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showPlusMenuSheet = false
                                showWhisperTargetDialog = true
                            }
                        )
                    }
                    PlusMenuTile(
                        icon = "🎙️",
                        title = "إملاء صوتي",
                        subtitle = if (isTranscribing) "جاري الاستماع" else "تحويل صوت لنص",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showPlusMenuSheet = false
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) viewModel.transcribeSpeechIntoInput()
                            else speechPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }

                // Super Admin / Group Admin Exclusive Tactical Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PlusMenuTile(
                        icon = "🚨",
                        title = "نبضة طوارئ",
                        subtitle = if (VirusXEffectsAndNotifications.canDrawOverlays(context)) "تظهر فوق التطبيقات ✅" else "تفعيل الظهور بالخارج + إرسال",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showPlusMenuSheet = false
                            if (!VirusXEffectsAndNotifications.canDrawOverlays(context)) {
                                Toast.makeText(context, "فعّل خيار (الظهور فوق التطبيقات الأخرى) لتظهر نافذة الطوارئ فوق الواتساب والألعاب!", Toast.LENGTH_LONG).show()
                                VirusXEffectsAndNotifications.requestOverlayPermission(context)
                            }
                            viewModel.onInputTextChange("[FORCE_PING] 🚨 استدعاء عاجل من ${state.currentUserName} — افتح المحادثة فوراً!")
                            viewModel.sendTextMessage()
                        }
                    )
                    PlusMenuTile(
                        icon = "👑",
                        title = "ألقاب الأعضاء",
                        subtitle = "تخصيص الشارات",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showPlusMenuSheet = false
                            showBadgeManagerDialog = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    // Pro Soundboard Dialog
    if (showSoundboardDialog) {
        val effects = listOf(
            Triple("SIREN", "🚨 صافرة إنذار تكتيكية", "صوت إنذار قوي عند الجميع"),
            Triple("AIRHORN", "📢 بوق الحماس (Airhorn)", "تنبيه صاخب ومميز"),
            Triple("DRUMROLL", "🥁 درام رول تشويقي", "قرع طبول قبل مفاجأة"),
            Triple("SHOCK", "⚡ صدمة كهربائية", "مؤثر مفاجأة درامي"),
            Triple("VICTORY", "🏆 نغمة انتصار (Victory)", "احتفال بالفوز في التحدي"),
            Triple("GAMEOVER", "💀 انتهت المهمة (Game Over)", "مؤثر خسارة ساخر")
        )
        AlertDialog(
            onDismissRequest = { showSoundboardDialog = false },
            containerColor = VirusXSurface,
            title = { Text("🔊 لوحة المؤثرات الصوتية التفاعلية", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر مؤثراً صوتياً ليتم إرساله وتشغيله فوراً بصوت مسموع لدى أعضاء المحادثة:", color = TextSecondary, fontSize = 12.sp)
                    effects.forEach { (key, title, sub) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VirusXSurfaceElevated)
                                .border(1.dp, VirusXGlassBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    showSoundboardDialog = false
                                    VirusXEffectsAndNotifications.playSoundEffect(key)
                                    viewModel.onInputTextChange("[SOUNDBOARD:$key] $title")
                                    viewModel.sendTextMessage()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(sub, color = TextSecondary, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = OnlineGreen)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSoundboardDialog = false }) {
                    Text("إغلاق", color = TextSecondary)
                }
            }
        )
    }

    // In-Chat Pro Interactive Events & Games Hub Dialog
    if (showGamePickerDialog) {
        var customRouletteTitle by remember { mutableStateOf("قرعة الجروب السريعة") }
        var imposterCustomWord by remember { mutableStateOf("") }
        var selectedAccused by remember { mutableStateOf(state.members.firstOrNull()?.displayName ?: state.currentUserName) }

        val imposterPresets = remember {
            listOf(
                "أكلات" to listOf("كشري", "شاورما", "بيتزا", "ملوخية", "محشي", "فلافل", "برجر"),
                "أفلام وألعاب" to listOf("باتمان", "سبايدرمان", "جاتا", "فيفا", "ببجي", "ماينكرافت"),
                "أماكن" to listOf("الأهرامات", "المطار", "الجامعة", "المستشفى", "السينما", "الملعب")
            )
        }

        AlertDialog(
            onDismissRequest = { showGamePickerDialog = false },
            containerColor = VirusXSurface,
            title = {
                Text("🎮 مركز الفعاليات والألعاب الجماعية الحية", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 470.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Dominoes Table
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0D2E22))
                            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(14.dp))
                            .clickable {
                                showGamePickerDialog = false
                                val hostId = state.currentUserId ?: "host"
                                val initStr = buildInitialDominoGameString(hostId, state.currentUserName)
                                viewModel.onInputTextChange(initStr)
                                viewModel.sendTextMessage()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🁣", fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("طاولة الدومينو الاحترافية (Dominoes)", color = Color(0xFFFFD54F), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Text("توزيع 7 قطع سرية لكل لاعب وسحب من السوق وطاولة حية", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    // 2. The Imposter (Spyfall)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1F1124))
                            .border(1.dp, Color(0xFFFF6B81), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🕵️‍♂️", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("لعبة الأمبوستر (الجاسوس السري)", color = Color(0xFFFF6B81), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                Text("توزيع كلمة سرية للجميع ما عدا الأمبوستر + تصويت حي", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                        OutlinedTextField(
                            value = imposterCustomWord,
                            onValueChange = { imposterCustomWord = it },
                            placeholder = { Text("اكتب كلمة سرية خاصة (أو اتركها عشوائية)...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            imposterPresets.forEach { (cat, words) ->
                                Button(
                                    onClick = {
                                        showGamePickerDialog = false
                                        val chosenWord = imposterCustomWord.trim().ifBlank { words.random() }
                                        val uid = state.currentUserId ?: "u1"
                                        val uname = state.currentUserName.replace(",", "").replace(":", "").ifBlank { "مؤسس" }
                                        viewModel.onInputTextChange("[GAME_IMPOSTER]|$cat|$chosenWord||$uid:$uname|LOBBY|")
                                        viewModel.sendTextMessage()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text("بدء ($cat)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 3. Live Roulette Wheel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(VirusXSurfaceElevated)
                            .border(1.dp, WarningAmber, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎡", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("عجلة الروليت والقرعة الفيزيائية", color = WarningAmber, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                Text("عجلة دوارة بفيزياء تبطيء حقيقية لاختيار فائز عشوائي", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = customRouletteTitle,
                                onValueChange = { customRouletteTitle = it },
                                label = { Text("عنوان القرعة", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    showGamePickerDialog = false
                                    val uid = state.currentUserId ?: "u1"
                                    val uname = state.currentUserName.replace(",", "").replace(":", "").ifBlank { "عضو" }
                                    val cleanTitle = customRouletteTitle.replace("|", " ").ifBlank { "قرعة الحظ" }
                                    viewModel.onInputTextChange("[EVENT_ROULETTE]|$cleanTitle|$uid:$uname|LOBBY|0|")
                                    viewModel.sendTextMessage()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                            ) {
                                Text("إنشاء 🎡", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                            }
                        }
                    }

                    // 4. Word Bomb
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF28120D))
                            .border(1.dp, DangerRed, RoundedCornerShape(14.dp))
                            .clickable {
                                showGamePickerDialog = false
                                val deadline = System.currentTimeMillis() / 1000L + 18L
                                val host = state.currentUserName.ifBlank { "اللاعب 1" }
                                viewModel.onInputTextChange("[GAME_BOMB]|م ر|$host|$deadline|0|ACTIVE|")
                                viewModel.sendTextMessage()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💣", fontSize = 26.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تحدي القنبلة الموقوتة بالكلمات (Word Bomb)", color = WarningAmber, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Text("كون كلمة بالحرفين المطلوبين قبل انفجار العداد التنازلي!", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    // 5. Hot Seat / Courtroom
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1A1528))
                            .border(1.dp, Color(0xFFA29BFE), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚖️", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("محكمة الجروب وكرسي الاعتراف", color = Color(0xFFA29BFE), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                Text("اختر عضواً لوضعه في قفص الاستجواب المباشر أمام الجروب", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.members) { m ->
                                FilterChip(
                                    selected = selectedAccused == m.displayName,
                                    onClick = { selectedAccused = m.displayName },
                                    label = { Text(m.displayName, fontSize = 11.sp) }
                                )
                            }
                        }
                        Button(
                            onClick = {
                                showGamePickerDialog = false
                                val accused = selectedAccused.replace("|", " ").ifBlank { "عضو" }
                                viewModel.onInputTextChange("[EVENT_HOTSEAT]|$accused||||WAITING_Q")
                                viewModel.sendTextMessage()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("⚖️ استدعاء ($selectedAccused) لكرسي الاعتراف", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    // 6 & 7. Classic Quick X/O & RPS Duels
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                showGamePickerDialog = false
                                val host = state.currentUserName.ifBlank { "اللاعب 1" }
                                viewModel.onInputTextChange("[GAME_XO]|---------|X|$host|ACTIVE")
                                viewModel.sendTextMessage()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("❌⭕ إكس أو", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                showGamePickerDialog = false
                                val host = state.currentUserName.ifBlank { "اللاعب 1" }
                                viewModel.onInputTextChange("[GAME_RPS]|$host|?|?|?|WAITING")
                                viewModel.sendTextMessage()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🪨📄✂️ حجر ورقة", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGamePickerDialog = false }) {
                    Text("إغلاق", color = TextSecondary)
                }
            }
        )
    }

    // Super Admin Custom Badges Manager Dialog
    if (showBadgeManagerDialog) {
        var selectedMember by remember { mutableStateOf(state.members.firstOrNull()) }
        var badgeInput by remember { mutableStateOf("") }
        val presetBadges = listOf("👑 المطور", "🛡️ Super Admin", "⚡ VIP", "🔥 نخبة", "🎯 القائد")

        AlertDialog(
            onDismissRequest = { showBadgeManagerDialog = false },
            containerColor = VirusXSurface,
            title = { Text("👑 إدارة الألقاب والشارات المميزة", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("اختر العضو وحدد اللقب الذي سيظهر بجانب اسمه فوق كل رسائله:", color = TextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.members) { m ->
                            val isSel = selectedMember?.id == m.id
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    selectedMember = m
                                    badgeInput = customBadgesMap[m.id] ?: m.customBadge.orEmpty()
                                },
                                label = { Text(m.displayName, fontSize = 12.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = badgeInput,
                        onValueChange = { badgeInput = it },
                        label = { Text("اكتب اللقب (أو اتركه فارغاً لمسحه)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(presetBadges) { preset ->
                            AssistChip(
                                onClick = { badgeInput = preset },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = selectedMember
                        if (target != null) {
                            val cleanBadge = badgeInput.trim()
                            customBadgesMap = if (cleanBadge.isBlank()) customBadgesMap - target.id
                            else customBadgesMap + (target.id to cleanBadge)

                            coroutineScope.launch(Dispatchers.IO) {
                                runCatching {
                                    supabaseEntry.postgrest().rpc(
                                        "set_user_custom_badge",
                                        buildJsonObject {
                                            put("_target_user_id", JsonPrimitive(target.id))
                                            put("_badge", JsonPrimitive(cleanBadge))
                                        }
                                    )
                                }
                            }
                            viewModel.onInputTextChange("[BADGE_ASSIGN]|${target.id}|$cleanBadge")
                            viewModel.sendTextMessage()
                            showBadgeManagerDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                ) {
                    Text("حفظ وتعميم اللقب", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBadgeManagerDialog = false }) {
                    Text("إلغاء", color = TextSecondary)
                }
            }
        )
    }

    // Force Ping Emergency Alert Popup Dialog
    emergencyAlertPopupText?.let { alertMsg ->
        AlertDialog(
            onDismissRequest = { emergencyAlertPopupText = null },
            containerColor = Color(0xFF280A12),
            title = {
                Text("🚨 تنبيه طوارئ عاجل!", color = DangerRed, fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Text(alertMsg, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            },
            confirmButton = {
                Button(
                    onClick = { emergencyAlertPopupText = null },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("علم — إيقاف الإنذار", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    fullScreenImageUrl?.let { imgUrl ->
        Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imgUrl,
                    contentDescription = "عرض الصورة كاملة",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = { saveImageUrlToGallery(context, imgUrl) },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(OnlineGreen.copy(alpha = 0.85f))
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "حفظ الصورة في المعرض", tint = Color.Black)
                    }

                    IconButton(
                        onClick = { fullScreenImageUrl = null },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(VirusXSurface)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextPrimary)
                    }
                }
            }
        }
    }

    if (showGroupMembersDialog && state.isGroup) {
        GroupMembersDialog(
            members = state.members,
            currentUserId = state.currentUserId,
            creatorId = state.chatCreatorId,
            promotedAdminIds = promotedAdminIds,
            isAdminOnly = isAdminOnlyChat,
            searchQuery = state.memberSearchQuery,
            searchResults = state.memberSearchResults,
            onSearchChange = viewModel::onMemberSearchChange,
            onAddMember = viewModel::addMemberToGroup,
            onRemoveMember = viewModel::removeMemberFromGroup,
            onTogglePromoteAdmin = { targetMember ->
                val updated = promotedAdminIds.toMutableSet()
                if (updated.contains(targetMember.id)) {
                    updated.remove(targetMember.id)
                    Toast.makeText(context, "تم إلغاء إشراف ${targetMember.displayName}", Toast.LENGTH_SHORT).show()
                } else {
                    updated.add(targetMember.id)
                    Toast.makeText(context, "تم ترقية ${targetMember.displayName} لمشرف في الجروب ⭐", Toast.LENGTH_SHORT).show()
                }
                promotedAdminIds = updated
                chatPrefs.edit().putStringSet("promoted_admins", updated).apply()
            },
            onToggleAdminOnlyMode = { enabled ->
                isAdminOnlyChat = enabled
                chatPrefs.edit().putBoolean("admin_only", enabled).apply()
                val notice = if (enabled) "🔒 قام المشرف بقفل الدردشة (الإرسال للمشرفين فقط)"
                else "🔓 قام المشرف بفتح الدردشة لجميع الأعضاء"
                viewModel.onInputTextChange(notice)
                viewModel.sendTextMessage()
            },
            onDismiss = { showGroupMembersDialog = false }
        )
    }

    if (showWhisperTargetDialog) {
        val otherMembers = state.members.filter { it.id != state.currentUserId }
        AlertDialog(
            onDismissRequest = { showWhisperTargetDialog = false },
            containerColor = VirusXSurface,
            title = { Text("🔒 إرسال همسة سرية داخل الجروب", color = TextPrimary) },
            text = {
                if (otherMembers.isEmpty()) {
                    Text("لا يوجد أعضاء آخرون في المجموعة حالياً.", color = TextSecondary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("اختر العضو الذي سيشاهد محتوى الهمسة:", color = TextSecondary, fontSize = 13.sp)
                        otherMembers.forEach { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VirusXSurfaceElevated)
                                    .clickable {
                                        viewModel.setWhisperTarget(member)
                                        showWhisperTargetDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarCircle(displayName = member.displayName, avatarUrl = member.avatarUrl, sizeDp = 30)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(member.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("@${member.username}", color = VirusXSecondaryWhisper, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWhisperTargetDialog = false }) {
                    Text("إغلاق", color = TextSecondary)
                }
            }
        )
    }

    if (showStartSyncDialog) {
        StartSyncSessionDialog(
            onDismiss = { showStartSyncDialog = false },
            onStart = { url, type ->
                viewModel.startSyncSession(url, type)
                showStartSyncDialog = false
            }
        )
    }

    selectedMessageForActions?.let { targetMsg ->
        val isMine = targetMsg.senderId == state.currentUserId
        val canDeleteForEveryone = isMine || isCurrentUserGroupAdmin

        val seenByMembers = remember(targetMsg, state.members, messages) {
            if (!state.isGroup) emptyList()
            else {
                val targetIdx = messages.indexOfFirst { it.id == targetMsg.id }
                val activeAfterIds = if (targetIdx >= 0) {
                    messages.drop(targetIdx + 1).map { it.senderId }.toSet()
                } else emptySet()
                state.members.filter { m ->
                    m.id != targetMsg.senderId && (targetMsg.status == "read" || activeAfterIds.contains(m.id))
                }
            }
        }

        AlertDialog(
            onDismissRequest = { selectedMessageForActions = null },
            containerColor = VirusXSurface,
            title = { Text("خيارات الرسالة والتفاعل", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!targetMsg.isDeleted) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(VirusXSurfaceElevated)
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf("❤️", "😂", "😮", "😢", "🔥", "👍").forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 22.sp,
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.toggleMessageReaction(targetMsg.id, emoji)
                                            selectedMessageForActions = null
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }

                    if (state.isGroup) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VirusXSurfaceElevated)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "👁️ تمت المشاهدة بواسطة (${seenByMembers.size}):",
                                color = VirusXSecondaryWhisper,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            if (seenByMembers.isEmpty()) {
                                Text("لم تتم المشاهدة من الأعضاء بعد", color = TextMuted, fontSize = 11.sp)
                            } else {
                                seenByMembers.forEach { reader ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        AvatarCircle(displayName = reader.displayName, avatarUrl = reader.avatarUrl, sizeDp = 22)
                                        Text(reader.displayName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("• ✓✓ قرأها", color = OnlineGreen, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            viewModel.setReplyToMessage(targetMsg)
                            selectedMessageForActions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = VirusXSecondaryWhisper)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("رد مقتبس على الرسالة", color = TextPrimary, modifier = Modifier.weight(1f))
                    }

                    if (!targetMsg.content.isNullOrBlank() && !targetMsg.isDeleted) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("VirusX Message", targetMsg.content))
                                Toast.makeText(context, "تم نسخ النص إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                                selectedMessageForActions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = VirusXSecondaryWhisper)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("نسخ نص الرسالة", color = TextPrimary, modifier = Modifier.weight(1f))
                        }
                    }

                    if (!targetMsg.isDeleted) {
                        TextButton(
                            onClick = {
                                val forwardText = "↪️ [رسالة محوّلة]: ${targetMsg.content ?: targetMsg.mediaUrl.orEmpty()}"
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Forwarded", forwardText))
                                viewModel.onInputTextChange(forwardText)
                                Toast.makeText(context, "تم تجهيز الرسالة لإعادة التوجيه", Toast.LENGTH_SHORT).show()
                                selectedMessageForActions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = OnlineGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إعادة توجيه الرسالة (Forward)", color = TextPrimary, modifier = Modifier.weight(1f))
                        }
                    }

                    if (!targetMsg.isDeleted && (isCurrentUserGroupAdmin || !state.isGroup)) {
                        TextButton(
                            onClick = {
                                val preview = targetMsg.content ?: "📷 مرفق مثبت"
                                pinnedMessageId = targetMsg.id
                                pinnedMessageText = preview
                                chatPrefs.edit()
                                    .putString("pinned_id", targetMsg.id)
                                    .putString("pinned_text", preview)
                                    .apply()
                                viewModel.onInputTextChange("📌 [رسالة مثبتة]: $preview")
                                viewModel.sendTextMessage()
                                selectedMessageForActions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = null, tint = WarningAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تثبيت الرسالة في أعلى المحادثة 📌", color = WarningAmber, modifier = Modifier.weight(1f))
                        }
                    }

                    if (isMine && !targetMsg.isDeleted && targetMsg.messageType == "text") {
                        TextButton(
                            onClick = {
                                viewModel.startEditingMessage(targetMsg)
                                selectedMessageForActions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = VirusXSecondaryWhisper)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تعديل نص الرسالة", color = TextPrimary, modifier = Modifier.weight(1f))
                        }
                    }

                    if (canDeleteForEveryone && !targetMsg.isDeleted) {
                        TextButton(
                            onClick = {
                                viewModel.deleteMessageForEveryone(targetMsg.id)
                                selectedMessageForActions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isMine) "حذف الرسالة لدى الجميع" else "حذف الرسالة كمشرف (Admin Delete)",
                                color = DangerRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForActions = null }) {
                    Text("إلغاء", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun PlusMenuTile(
    icon: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(VirusXSurfaceElevated)
            .border(1.dp, VirusXGlassBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        Text(subtitle, color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CleanSingleLineComposer(
    state: ChatUiState,
    isRecording: Boolean,
    liveWaveform: List<Float>,
    recordingDuration: Int,
    liveCapturedSpeech: String,
    showStickerDrawer: Boolean,
    onToggleStickerDrawer: () -> Unit,
    onOpenPlusMenu: () -> Unit,
    onInputChange: (String) -> Unit,
    onSendText: () -> Unit,
    onReceiveKeyboardStickerUri: (Uri) -> Unit,
    onClearReply: () -> Unit,
    onCancelEdit: () -> Unit,
    onClearWhisper: () -> Unit,
    onSelectPitchFilter: (String) -> Unit,
    onToggleViewOnce: () -> Unit,
    onStartRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSendVoice: () -> Unit
) {
    var showVoiceFilterMenu by remember { mutableStateOf(false) }
    val filterLabels = mapOf(
        "normal" to "طبيعي",
        "deep" to "عميق (هكر)",
        "robot" to "روبوت آلي",
        "chipmunk" to "سنجاب",
        "ghost" to "شبح مرعب",
        "fast" to "سريع"
    )

    // Official Compose Rich Content Receiver so Gboard / Samsung Keyboard stickers (.webp, .png, .gif) work directly!
    val keyboardStickerReceiver = remember {
        ReceiveContentListener { transferableContent: TransferableContent ->
            var stickerUri: Uri? = null
            val remaining = transferableContent.consume { item: ClipData.Item ->
                val uri = item.uri
                if (uri != null && stickerUri == null) {
                    stickerUri = uri
                    true
                } else {
                    false
                }
            }
            stickerUri?.let { onReceiveKeyboardStickerUri(it) }
            remaining
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VirusXSurface)
            .border(0.5.dp, VirusXGlassBorder)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Active Indicators (Edit / Reply / Whisper / Self-Destruct Timer)
        state.editingMessage?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VirusXPrimary.copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = VirusXSecondaryWhisper, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("جاري تعديل الرسالة...", color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onCancelEdit, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }

        state.replyToMessage?.let { replyMsg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VirusXSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = VirusXPrimary, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "رد على: ${replyMsg.content ?: "مرفق"}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearReply, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }
        }

        state.whisperTargetUser?.let { targetUser ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VirusXWhisperSurface)
                    .border(1.dp, VirusXSecondaryWhisper, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = VirusXSecondaryWhisper, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "همسة سرية إلى: ${targetUser.displayName}",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearWhisper, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }

        if (isRecording) {
            // Organized Recording Bar with a single Voice Filter Dropdown button
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (liveCapturedSpeech.isNotBlank()) {
                    Text(
                        text = "📝 تفريغ حي: $liveCapturedSpeech",
                        color = OnlineGreen,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(VirusXSurfaceElevated)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = onCancelRecording, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "إلغاء", tint = DangerRed)
                    }

                    Text("🔴 ${recordingDuration}ث", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                    // Single Clean Dropdown Button for Voice Changers
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(VirusXPrimary.copy(alpha = 0.25f))
                                .border(1.dp, VirusXSecondaryWhisper, RoundedCornerShape(12.dp))
                                .clickable { showVoiceFilterMenu = true }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎛️ ${filterLabels[state.selectedPitchFilter] ?: "طبيعي"} ▼",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        DropdownMenu(
                            expanded = showVoiceFilterMenu,
                            onDismissRequest = { showVoiceFilterMenu = false },
                            modifier = Modifier.background(VirusXSurfaceElevated)
                        ) {
                            filterLabels.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (state.selectedPitchFilter == key) "✓ $label" else label,
                                            color = if (state.selectedPitchFilter == key) OnlineGreen else TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    onClick = {
                                        onSelectPitchFilter(key)
                                        showVoiceFilterMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // View-once compact toggle
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (state.voiceViewOnce) WarningAmber else VirusXSurface)
                            .clickable { onToggleViewOnce() }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "1️⃣",
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val bars = if (liveWaveform.isEmpty()) List(12) { 0.25f } else liveWaveform.takeLast(14)
                        bars.forEach { amp ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height((amp * 20).dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(VirusXSecondaryWhisper)
                            )
                        }
                    }

                    IconButton(
                        onClick = onSendVoice,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(OnlineGreen)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال الفويس", tint = Color.Black)
                    }
                }
            }
        } else {
            // SINGLE CLEAN ROW: [+] Button | Input Field (with Sticker button & Keyboard Sticker Receiver) | Mic/Send Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onOpenPlusMenu,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(VirusXSurfaceElevated)
                        .border(1.dp, if (state.destructTimerSeconds != null) WarningAmber else VirusXGlassBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "الأدوات والمرفقات",
                        tint = if (state.destructTimerSeconds != null) WarningAmber else VirusXSecondaryWhisper
                    )
                }

                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = onInputChange,
                    placeholder = {
                        val hint = if (state.destructTimerSeconds != null) "رسالة مؤقتة (${state.destructTimerSeconds}ث)..."
                        else "اكتب رسالتك أو @منشن..."
                        Text(hint, color = TextMuted, fontSize = 13.sp)
                    },
                    trailingIcon = {
                        IconButton(onClick = onToggleStickerDrawer) {
                            Icon(
                                imageVector = Icons.Default.EmojiEmotions,
                                contentDescription = "الملصقات",
                                tint = if (showStickerDrawer) WarningAmber else TextSecondary
                            )
                        }
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = VirusXSurfaceElevated,
                        unfocusedContainerColor = VirusXSurfaceElevated,
                        focusedBorderColor = VirusXPrimary,
                        unfocusedBorderColor = VirusXGlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .contentReceiver(keyboardStickerReceiver)
                )

                if (state.inputText.isNotBlank()) {
                    IconButton(
                        onClick = onSendText,
                        enabled = !state.isSending,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(VirusXPrimary, VirusXSecondaryWhisper)))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال", tint = TextPrimary)
                    }
                } else {
                    IconButton(
                        onClick = onStartRecording,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(VirusXPrimary)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "تسجيل رسالة صوتية", tint = TextPrimary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MessageBubbleItem(
    message: MessageEntity,
    isMine: Boolean,
    isGroup: Boolean,
    peerOnline: Boolean,
    currentUserId: String?,
    currentUserName: String,
    senderName: String,
    senderBadge: String?,
    senderAvatarUrl: String?,
    whisperTargetName: String?,
    repliedMessage: MessageEntity?,
    onOpenFullImage: (String) -> Unit,
    onExpireSelfDestruct: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onUpdateGameMessage: (String) -> Unit,
    onSaveVoiceTranscript: (String) -> Unit,
    onSwipeReply: () -> Unit,
    onLongPress: () -> Unit
) {
    val rawContent = message.content.orEmpty()

    // Hide internal badge sync messages from cluttering chat
    if (rawContent.startsWith("[BADGE_ASSIGN]")) {
        return
    }

    if (rawContent.startsWith("[SCREENSHOT_ALERT]")) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rawContent.removePrefix("[SCREENSHOT_ALERT]").trim(),
                color = WarningAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(WarningAmber.copy(alpha = 0.14f))
                    .border(1.dp, WarningAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        return
    }

    val destructSec: Int = message.destructSeconds ?: 0
    var remainingSeconds by remember(message.id, destructSec, message.isDeleted) {
        mutableIntStateOf(if (!message.isDeleted && destructSec > 0) destructSec else 0)
    }
    LaunchedEffect(message.id, destructSec, message.isDeleted) {
        if (!message.isDeleted && destructSec > 0) {
            var left: Int = destructSec
            while (left > 0) {
                remainingSeconds = left
                delay(1000L)
                left -= 1
            }
            remainingSeconds = 0
            onExpireSelfDestruct()
        }
    }

    val reactionData = remember(message.reactionsJson) {
        val emojis = ArrayList<String>()
        val counts = ArrayList<Int>()
        val mineFlags = ArrayList<Boolean>()
        runCatching {
            val raw: String? = message.reactionsJson
            if (!raw.isNullOrBlank()) {
                val obj = Json.parseToJsonElement(raw).jsonObject
                for ((k, v) in obj) {
                    val arr = v.jsonArray
                    if (arr.isNotEmpty()) {
                        emojis.add(k)
                        counts.add(arr.size)
                        val hasMe = arr.any { elem -> elem.jsonPrimitive.content == currentUserId }
                        mineFlags.add(hasMe)
                    }
                }
            }
        }
        Triple(emojis, counts, mineFlags)
    }

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val isWhisper = message.whisperTargetId != null
    val canReadWhisper = !isWhisper || isMine || message.whisperTargetId == currentUserId
    val hasMentionAlert = rawContent.contains("@الكل") ||
        (!currentUserId.isNullOrBlank() && rawContent.contains("@"))

    val isTransparentSticker = !message.isDeleted && canReadWhisper && (
        rawContent.startsWith("[STICKER]") ||
            (message.messageType == "image" && message.mediaUrl?.lowercase()?.contains(".webp") == true)
        )

    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(dragOffset) > 60f) onSwipeReply()
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> dragOffset += dragAmount }
                )
            },
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
        ) {
            if (!isMine) {
                AvatarCircle(
                    displayName = senderName,
                    avatarUrl = senderAvatarUrl,
                    sizeDp = 32
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Column(
                modifier = Modifier
                    .widthIn(min = 96.dp, max = 315.dp)
                    .clip(bubbleShape)
                    .then(
                        when {
                            isTransparentSticker -> Modifier.background(Color.Transparent)
                            rawContent.startsWith("[FORCE_PING]") -> Modifier
                                .background(Color(0xFF3A0D18))
                                .border(1.5.dp, DangerRed, bubbleShape)
                            isWhisper -> Modifier
                                .background(VirusXWhisperSurface)
                                .border(1.5.dp, VirusXSecondaryWhisper, bubbleShape)
                            isMine -> Modifier.background(SentMessageGradient)
                            hasMentionAlert -> Modifier
                                .background(ReceivedBubbleColor)
                                .border(1.2.dp, WarningAmber.copy(alpha = 0.7f), bubbleShape)
                            else -> Modifier
                                .background(ReceivedBubbleColor)
                                .border(1.dp, VirusXGlassBorder, bubbleShape)
                        }
                    )
                    .combinedClickable(
                        onClick = {
                            val urlToOpen = message.mediaUrl
                            if (message.messageType == "image" && !urlToOpen.isNullOrBlank() && !message.isDeleted && canReadWhisper) {
                                onOpenFullImage(urlToOpen)
                            }
                        },
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 13.dp, vertical = 9.dp)
            ) {
                // Sender Header + Custom Super Admin Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = if (isMine) (if (senderName.contains("أنت")) senderName else "أنت") else senderName,
                        color = if (isMine) TextPrimary.copy(alpha = 0.9f) else VirusXSecondaryWhisper,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    if (!senderBadge.isNullOrBlank()) {
                        Text(
                            text = senderBadge,
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(WarningAmber)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                if (isWhisper) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(WhisperMessageGradient)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (canReadWhisper) "همسة سرية إلى ${whisperTargetName ?: "عضو"}" else "همسة مشفرة لعضو آخر",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                }

                if (repliedMessage != null) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .padding(7.dp)
                    ) {
                        Text("↩️ رد على رسالة:", color = VirusXSecondaryWhisper, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (repliedMessage.isDeleted) "رسالة محذوفة" else (repliedMessage.content ?: "مرفق"),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                }

                when {
                    message.isDeleted -> {
                        Text(
                            text = rawContent.ifBlank { "🚫 تم حذف هذه الرسالة" },
                            color = TextMuted,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp
                        )
                    }
                    !canReadWhisper -> {
                        Text(
                            text = "🔒 محتوى هذه الهمسة مشفر ولا يظهر إلا للمرسل والمستقبل المحدد.",
                            color = TextSecondary,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp
                        )
                    }
                    rawContent.startsWith("[STICKER]") -> {
                        Text(
                            text = rawContent.removePrefix("[STICKER]").trim(),
                            fontSize = 54.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    rawContent.startsWith("[SOUNDBOARD:") -> {
                        val effectKey = rawContent.substringAfter("[SOUNDBOARD:").substringBefore("]").trim()
                        val label = rawContent.substringAfter("]").trim().ifBlank { "🔊 مؤثر صوتي تفاعلي" }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable { VirusXEffectsAndNotifications.playSoundEffect(effectKey) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = OnlineGreen)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("اضغط لإعادة تشغيل المؤثر الصوتي 🔊", color = OnlineGreen, fontSize = 10.sp)
                            }
                        }
                    }
                    rawContent.startsWith("[GAME_XO]|") -> {
                        InChatTicTacToeCard(
                            rawState = rawContent,
                            currentUserName = currentUserName.ifBlank { "لاعب" },
                            onUpdateState = onUpdateGameMessage
                        )
                    }
                    rawContent.startsWith("[EVENT_ROULETTE]|") -> {
                        InChatRouletteWheelCard(
                            rawState = rawContent,
                            currentUserId = currentUserId ?: "u",
                            currentUserName = currentUserName.ifBlank { "لاعب" },
                            onUpdateState = onUpdateGameMessage
                        )
                    }
                    rawContent.startsWith("[GAME_IMPOSTER]|") -> {
                        InChatImposterGameCard(
                            rawState = rawContent,
                            currentUserId = currentUserId ?: "u",
                            currentUserName = currentUserName.ifBlank { "لاعب" },
                            onUpdateState = onUpdateGameMessage
                        )
                    }
                    rawContent.startsWith("[GAME_BOMB]|") -> {
                        InChatWordBombCard(
                            rawState = rawContent,
                            currentUserName = currentUserName.ifBlank { "لاعب" },
                            onUpdateState = onUpdateGameMessage
                        )
                    }
                    rawContent.startsWith("[EVENT_HOTSEAT]|") -> {
                        InChatHotSeatCourtCard(
                            rawState = rawContent,
                            currentUserName = currentUserName.ifBlank { "لاعب" },
                            onUpdateState = onUpdateGameMessage
                        )
                    }
                    rawContent.startsWith("[GAME_DOMINO]|") -> {
                        InChatDominoTableCard(
                            rawState = rawContent,
                            currentUserId = currentUserId ?: "u",
                            currentUserName = currentUserName.ifBlank { "لاعب" },
                            onUpdateState = onUpdateGameMessage
                        )
                    }
                    rawContent.startsWith("[GAME_RPS]|") -> {
                        InChatRpsDuelCard(
                            rawState = rawContent,
                            currentUserName = currentUserName.ifBlank { "لاعب" },
                            onUpdateState = onUpdateGameMessage
                        )
                    }
                    message.messageType == "image" -> {
                        val imgUrl = message.mediaUrl
                        if (!imgUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = imgUrl,
                                contentDescription = "صورة أو ملصق",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = if (isTransparentSticker) 170.dp else 260.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenFullImage(imgUrl) }
                            )
                            if (!isTransparentSticker) {
                                Text(
                                    text = "🔍 اضغط لعرض الصورة كاملة أو حفظها",
                                    color = VirusXSecondaryWhisper,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }
                    }
                    message.messageType == "voice" || !message.voiceWaveformJson.isNullOrBlank() || (message.voiceDurationSeconds ?: 0) > 0 || rawContent.contains("رسالة صوتية") -> {
                        AdvancedVoiceNotePlayer(
                            message = message,
                            isMine = isMine,
                            onSaveTranscript = onSaveVoiceTranscript
                        )
                    }
                    else -> {
                        val styledText = remember(rawContent) {
                            buildAnnotatedString {
                                val tokens = rawContent.split(" ")
                                tokens.forEachIndexed { idx, word ->
                                    if (word.startsWith("@") && word.length > 1) {
                                        withStyle(
                                            SpanStyle(
                                                color = WarningAmber,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        ) {
                                            append(word)
                                        }
                                    } else {
                                        append(word)
                                    }
                                    if (idx < tokens.lastIndex) append(" ")
                                }
                            }
                        }
                        Text(
                            text = styledText,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )
                    }
                }

                // 3-State Read/Delivered/Sent Receipt Indicator
                if (!message.isDeleted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (remainingSeconds > 0) {
                            Text(
                                text = "⏳ ${remainingSeconds}ث",
                                color = WarningAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!message.editedAt.isNullOrBlank()) {
                            Text(
                                text = "(معدّلة)",
                                color = TextPrimary.copy(alpha = 0.65f),
                                fontSize = 10.sp
                            )
                        }
                        if (isMine) {
                            val isRead = message.status == "read"
                            val isDelivered = message.status == "delivered" || peerOnline
                            val tickSymbol = when {
                                isRead -> "✓✓"
                                isDelivered -> "✓✓"
                                else -> "✓"
                            }
                            val tickLabel = when {
                                isRead -> "مقروءة"
                                isDelivered -> "وصلت"
                                else -> "أُرسلت"
                            }
                            val tickColor = when {
                                isRead -> Color(0xFF00E676) // Glowing bright neon green/cyan when read
                                isDelivered -> Color.White.copy(alpha = 0.72f) // Two grey/white ticks when delivered
                                else -> Color.White.copy(alpha = 0.45f) // Single dim tick when offline
                            }
                            Text(
                                text = "$tickSymbol $tickLabel",
                                color = tickColor,
                                fontWeight = if (isRead) FontWeight.ExtraBold else FontWeight.Normal,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            if (isMine) {
                Spacer(modifier = Modifier.width(6.dp))
                AvatarCircle(
                    displayName = senderName,
                    avatarUrl = senderAvatarUrl,
                    sizeDp = 32
                )
            }
        }

        val (rEmojis, rCounts, rMine) = reactionData
        if (rEmojis.isNotEmpty() && !message.isDeleted) {
            FlowRow(
                modifier = Modifier.padding(top = 3.dp, start = 40.dp, end = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rEmojis.indices.forEach { idx ->
                    val emoji = rEmojis[idx]
                    val count = rCounts[idx]
                    val reactedByMe = rMine[idx]
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (reactedByMe) VirusXPrimary.copy(alpha = 0.35f) else VirusXSurfaceElevated)
                            .border(1.dp, if (reactedByMe) VirusXSecondaryWhisper else VirusXGlassBorder, CircleShape)
                            .clickable { onToggleReaction(emoji) }
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emoji, fontSize = 12.sp)
                        if (count > 1) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("$count", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InChatTicTacToeCard(
    rawState: String,
    currentUserName: String,
    onUpdateState: (String) -> Unit
) {
    var isLocalBotMode by remember { mutableStateOf(false) }
    var botLevel by remember { mutableStateOf("MEDIUM") }
    var showBotSelector by remember { mutableStateOf(false) }
    var localState by remember(rawState) { mutableStateOf(rawState) }

    val activeState = if (isLocalBotMode) localState else rawState
    val parts = activeState.split("|")
    val board = (parts.getOrNull(1) ?: "---------").padEnd(9, '-')
    val turn = parts.getOrNull(2) ?: "X"
    val lastActor = parts.getOrNull(3) ?: ""
    val status = parts.getOrNull(4) ?: "ACTIVE"

    fun checkWinner(b: String): String? {
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (l in lines) {
            val c = b[l[0]]
            if (c != '-' && c == b[l[1]] && c == b[l[2]]) return c.toString()
        }
        if (!b.contains('-')) return "DRAW"
        return null
    }

    fun pickBestBotMove(b: String, level: String): Int {
        val empty = b.indices.filter { b[it] == '-' }
        if (empty.isEmpty()) return -1
        if (level == "EASY") return empty.random()

        // Check immediate win for O
        for (idx in empty) {
            val test = StringBuilder(b).also { it.setCharAt(idx, 'O') }.toString()
            if (checkWinner(test) == "O") return idx
        }
        // Check immediate block against X
        for (idx in empty) {
            val test = StringBuilder(b).also { it.setCharAt(idx, 'X') }.toString()
            if (checkWinner(test) == "X") return idx
        }
        if (level == "MEDIUM") return empty.random()

        // PRO: Center -> Corners -> Edges tactical priority
        if (4 in empty) return 4
        val corners = listOf(0, 2, 6, 8).filter { it in empty }
        if (corners.isNotEmpty()) return corners.random()
        return empty.random()
    }

    LaunchedEffect(activeState, isLocalBotMode) {
        if (isLocalBotMode && status == "ACTIVE" && turn == "O") {
            delay(550L)
            val botIdx = pickBestBotMove(board, botLevel)
            if (botIdx >= 0) {
                val nextBoard = StringBuilder(board).also { it.setCharAt(botIdx, 'O') }.toString()
                val win = checkWinner(nextBoard)
                val botLabel = when (botLevel) {
                    "EASY" -> "🤖 البوت (سهل)"
                    "MEDIUM" -> "🤖 البوت (متوسط)"
                    else -> "🤖 البوت (حريف)"
                }
                val nextStatus = when (win) {
                    "O" -> {
                        VirusXEffectsAndNotifications.playSoundEffect("GAMEOVER")
                        "WIN:$botLabel"
                    }
                    "DRAW" -> "DRAW"
                    else -> "ACTIVE"
                }
                localState = "[GAME_XO]|$nextBoard|X|$botLabel|$nextStatus"
                if (nextStatus != "ACTIVE") onUpdateState(localState)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.32f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎮 تحدي X/O المباشر", color = WarningAmber, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            if (!isLocalBotMode && status == "ACTIVE" && !showBotSelector) {
                Text(
                    text = "🤖 ضد البوت ▼",
                    color = OnlineGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(VirusXSurfaceElevated)
                        .border(1.dp, OnlineGreen, RoundedCornerShape(8.dp))
                        .clickable { showBotSelector = true }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }

        if (showBotSelector && !isLocalBotMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("EASY" to "🟢 سهل", "MEDIUM" to "🟡 متوسط", "PRO" to "🔴 حريف").forEach { (k, label) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VirusXSurfaceElevated)
                            .border(1.dp, WarningAmber, RoundedCornerShape(8.dp))
                            .clickable {
                                botLevel = k
                                isLocalBotMode = true
                                showBotSelector = false
                            }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val statusText = when {
            status.startsWith("WIN:") -> "🏆 الفائز: ${status.removePrefix("WIN:")}!"
            status == "DRAW" -> "🤝 تعادل بين الطرفين!"
            isLocalBotMode && turn == "O" -> "🤖 البوت يفكر..."
            else -> "الدور الآن على: $turn (آخر حركة: $lastActor)"
        }
        Text(statusText, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

        for (r in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0..2) {
                    val idx = r * 3 + c
                    val cell = board[idx]
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(VirusXSurfaceElevated)
                            .border(1.dp, VirusXGlassBorder, RoundedCornerShape(10.dp))
                            .clickable(enabled = status == "ACTIVE" && cell == '-' && (!isLocalBotMode || turn == "X")) {
                                val nextBoard = StringBuilder(board).also { it.setCharAt(idx, turn.first()) }.toString()
                                val win = checkWinner(nextBoard)
                                val nextStatus = when (win) {
                                    "X", "O" -> {
                                        VirusXEffectsAndNotifications.playSoundEffect("VICTORY")
                                        "WIN:$currentUserName ($win)"
                                    }
                                    "DRAW" -> "DRAW"
                                    else -> "ACTIVE"
                                }
                                val nextTurn = if (turn == "X") "O" else "X"
                                val nextStr = "[GAME_XO]|$nextBoard|$nextTurn|$currentUserName|$nextStatus"
                                localState = nextStr
                                if (!isLocalBotMode || nextStatus != "ACTIVE") {
                                    onUpdateState(nextStr)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (cell == '-') "" else cell.toString(),
                            color = if (cell == 'X') OnlineGreen else WarningAmber,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InChatRpsDuelCard(
    rawState: String,
    currentUserName: String,
    onUpdateState: (String) -> Unit
) {
    // Format: [GAME_RPS]|p1Name|p1Move|p2Name|p2Move
    val parts = rawState.split("|")
    val p1Name = parts.getOrNull(1) ?: "اللاعب 1"
    val p1Move = parts.getOrNull(2) ?: "?"
    val p2Name = parts.getOrNull(3) ?: "?"
    val p2Move = parts.getOrNull(4) ?: "?"

    val bothPlayed = p1Move != "?" && p2Move != "?"
    val moves = listOf("🪨" to "حجر", "📄" to "ورقة", "✂️" to "مقص")

    fun computeWinner(): String {
        if (p1Move == p2Move) return "🤝 تعادل! كلاكما اختار $p1Move"
        val p1Wins = (p1Move == "🪨" && p2Move == "✂️") ||
            (p1Move == "📄" && p2Move == "🪨") ||
            (p1Move == "✂️" && p2Move == "📄")
        return if (p1Wins) "🏆 فاز $p1Name ($p1Move يهزم $p2Move)"
        else "🏆 فاز $p2Name ($p2Move يهزم $p1Move)"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.32f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🪨📄✂️ تحدي حجر - ورقة - مقص", color = OnlineGreen, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)

        if (bothPlayed) {
            Text("$p1Name: $p1Move  ⚡  $p2Name: $p2Move", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(computeWinner(), color = WarningAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else {
            val statusLine = when {
                p1Move == "?" -> "في انتظار حركة $p1Name..."
                p2Move == "?" -> "✅ $p1Name اختار حركته سراً! في انتظار المتحدي الثاني..."
                else -> ""
            }
            Text(statusLine, color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                moves.forEach { (emoji, label) ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(VirusXSurfaceElevated)
                            .border(1.dp, VirusXSecondaryWhisper, RoundedCornerShape(10.dp))
                            .clickable {
                                if (p1Move == "?") {
                                    onUpdateState("[GAME_RPS]|$currentUserName|$emoji|?|?")
                                } else if (p2Move == "?") {
                                    VirusXEffectsAndNotifications.playSoundEffect("VICTORY")
                                    onUpdateState("[GAME_RPS]|$p1Name|$p1Move|$currentUserName|$emoji")
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(emoji, fontSize = 22.sp)
                        Text(label, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedVoiceNotePlayer(
    message: MessageEntity,
    isMine: Boolean,
    onSaveTranscript: (String) -> Unit
) {
    val context = LocalContext.current
    val expiredViewOnce = message.voiceViewOnce && message.voicePlayed && !isMine
    var showTranscript by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var localTranscript by remember(message.voiceTranscript) {
        mutableStateOf(message.voiceTranscript.orEmpty())
    }
    var isLiveRecognizing by remember { mutableStateOf(false) }

    val totalSec = (message.voiceDurationSeconds ?: 8).coerceAtLeast(1)
    var currentPosMs by remember { mutableStateOf(0L) }
    var totalDurationMs by remember { mutableStateOf(totalSec * 1000L) }

    // Map selected voice filter to actual pitch & speed multiplier so Robot / Deep / Chipmunk / Ghost work 100%!
    val (filterSpeedMultiplier, filterPitch) = remember(message.voicePitchFilter) {
        when (message.voicePitchFilter?.lowercase()) {
            "deep" -> 0.92f to 0.65f
            "robot" -> 1.05f to 0.76f
            "chipmunk" -> 1.15f to 1.65f
            "ghost" -> 0.82f to 0.55f
            "fast" -> 1.35f to 1.05f
            else -> 1.0f to 1.0f
        }
    }

    val exoPlayer = remember(message.mediaUrl) {
        ExoPlayer.Builder(context).build().apply {
            val url = message.mediaUrl
            if (!url.isNullOrBlank()) {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            runCatching {
                exoPlayer.stop()
                exoPlayer.release()
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val dur = exoPlayer.duration
            if (dur > 0) totalDurationMs = dur
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (exoPlayer.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                isPlaying = false
                exoPlayer.seekTo(0L)
                currentPosMs = 0L
            }
            delay(200L)
        }
    }

    fun startOnDemandVoiceToText() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            localTranscript = "⚠️ محرك التعرف الصوتي من جوجل غير مفعل على هذا الهاتف."
            return
        }
        isLiveRecognizing = true
        runCatching {
            // Play voice note softly so Android SpeechRecognizer captures and transcribes its words if transcript was empty
            if (!isPlaying && !message.mediaUrl.isNullOrBlank()) {
                exoPlayer.playbackParameters = PlaybackParameters(1.0f, 1.0f)
                exoPlayer.seekTo(0L)
                exoPlayer.play()
                isPlaying = true
            }
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isLiveRecognizing = false
                }
                override fun onError(error: Int) {
                    isLiveRecognizing = false
                }
                override fun onResults(results: Bundle?) {
                    isLiveRecognizing = false
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!list.isNullOrEmpty()) {
                        val text = list.first()
                        localTranscript = text
                        onSaveTranscript(text)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!list.isNullOrEmpty()) {
                        localTranscript = list.first()
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            sr.startListening(intent)
        }.onFailure {
            isLiveRecognizing = false
        }
    }

    fun formatSec(ms: Long): String {
        val sec = (ms / 1000L).toInt().coerceAtLeast(0)
        val m = sec / 60
        val s = sec % 60
        return String.format(java.util.Locale.US, "%d:%02d", m, s)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = {
                    if (expiredViewOnce) return@IconButton
                    if (isPlaying) {
                        exoPlayer.pause()
                        isPlaying = false
                    } else {
                        exoPlayer.playbackParameters = PlaybackParameters(
                            (playbackSpeed * filterSpeedMultiplier).coerceIn(0.5f, 2.5f),
                            filterPitch
                        )
                        exoPlayer.play()
                        isPlaying = true
                    }
                },
                enabled = !expiredViewOnce,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f))
            ) {
                Icon(
                    imageVector = when {
                        expiredViewOnce -> Icons.Default.Lock
                        isPlaying -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = "تشغيل/إيقاف",
                    tint = TextPrimary
                )
            }

            Slider(
                value = (currentPosMs.toFloat() / totalDurationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f),
                onValueChange = { fraction ->
                    val target = (fraction * totalDurationMs).toLong()
                    currentPosMs = target
                    exoPlayer.seekTo(target)
                },
                enabled = !expiredViewOnce,
                colors = SliderDefaults.colors(
                    thumbColor = WarningAmber,
                    activeTrackColor = VirusXSecondaryWhisper,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
            )

            Text(
                text = "${playbackSpeed}x",
                color = WarningAmber,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable {
                        playbackSpeed = when (playbackSpeed) {
                            1.0f -> 1.5f
                            1.5f -> 2.0f
                            else -> 1.0f
                        }
                        exoPlayer.playbackParameters = PlaybackParameters(
                            (playbackSpeed * filterSpeedMultiplier).coerceIn(0.5f, 2.5f),
                            filterPitch
                        )
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Force LTR mark (\u200E) so timer always displays cleanly as 0:04 / 0:08
            val filterBadge = if (!message.voicePitchFilter.isNullOrBlank() && message.voicePitchFilter != "normal") {
                " • 🎛️ ${message.voicePitchFilter}"
            } else ""
            Text(
                text = "\u200E${formatSec(currentPosMs)} / ${formatSec(totalDurationMs)}$filterBadge",
                color = TextPrimary.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = when {
                    isLiveRecognizing -> "🎙️ جاري تحويل الصوت لنص..."
                    showTranscript -> "🔼 إخفاء النص"
                    else -> "📝 تحويل الفويس لكتابة (أ ب)"
                },
                color = OnlineGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable {
                        showTranscript = !showTranscript
                        if (showTranscript && localTranscript.isBlank()) {
                            startOnDemandVoiceToText()
                        }
                    }
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }

        if (showTranscript && !expiredViewOnce) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.32f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (localTranscript.isNotBlank()) {
                    Text(
                        text = "📝 النص: $localTranscript",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = if (isLiveRecognizing) "🎙️ استمع وجاري كتابة كلمات الفويس الآن..."
                        else "اضغط (🔄 إعادة الاستماع والتفريغ) لتحويل صوت الفويس إلى نص مكتوب:",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "🔄 بدء التفريغ الصوتي التلقائي الآن",
                        color = WarningAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { startOnDemandVoiceToText() }
                    )
                }
            }
        }
    }
}

private fun saveImageUrlToGallery(context: Context, imageUrl: String) {
    runCatching {
        val request = DownloadManager.Request(Uri.parse(imageUrl))
            .setTitle("VirusX Image")
            .setDescription("جاري حفظ الصورة في المعرض...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_PICTURES,
                "VirusX/VirusX_${System.currentTimeMillis()}.jpg"
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "جاري حفظ الصورة في المعرض (Pictures/VirusX) 📥", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "تعذر حفظ الصورة: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AvatarCircle(
    displayName: String,
    avatarUrl: String?,
    sizeDp: Int = 34
) {
    val initials = remember(displayName) {
        val clean = displayName.replace("(أنت)", "").trim()
        val parts = clean.split(" ").filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            clean.length >= 2 -> clean.take(2).uppercase()
            clean.isNotEmpty() -> clean.take(1).uppercase()
            else -> "VX"
        }
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(VirusXPrimary, VirusXSecondaryWhisper)))
            .border(1.dp, VirusXGlassBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (sizeDp * 0.36f).sp
            )
        }
    }
}

@Composable
private fun StickerDrawerBar(
    context: Context,
    onSendQuickSticker: (String) -> Unit,
    onSendStickerUri: (Uri) -> Unit,
    onImportStickerFromDevice: () -> Unit
) {
    // Automatically scan WhatsApp Stickers folder if accessible on device
    val whatsappStickers = remember {
        val paths = listOf(
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers"),
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Stickers"),
            File(Environment.getExternalStorageDirectory(), "Pictures/VirusX")
        )
        paths.flatMap { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles { f -> f.extension.lowercase() in listOf("webp", "png", "jpg") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.take(25)
                    ?: emptyList()
            } else emptyList()
        }
    }

    val expressiveStickers = remember {
        listOf("🔥😎", "😂💀", "🫡⚡", "❤️✨", "👑🦁", "🎯🚀", "🤫🔒", "🎉🥳", "👀🍿", "🤝💯")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VirusXSurfaceElevated)
            .border(0.5.dp, VirusXSecondaryWhisper.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎭 مكتبة الملصقات وملصقات الكيبورد/واتساب",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = "📥 اختيار ملصق من الجهاز",
                color = OnlineGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(OnlineGreen.copy(alpha = 0.15f))
                    .clickable { onImportStickerFromDevice() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        if (whatsappStickers.isNotEmpty()) {
            Text("ملصقات واتساب المكتشفة على هاتفك:", color = VirusXSecondaryWhisper, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(whatsappStickers) { file ->
                    AsyncImage(
                        model = file,
                        contentDescription = "ملصق واتساب",
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VirusXSurface)
                            .clickable { onSendStickerUri(Uri.fromFile(file)) }
                            .padding(4.dp)
                    )
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(expressiveStickers) { st ->
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VirusXSurface)
                        .border(1.dp, VirusXGlassBorder, RoundedCornerShape(14.dp))
                        .clickable { onSendQuickSticker("[STICKER] $st") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = st, fontSize = 24.sp)
                }
            }
        }
        Text(
            text = "✅ تم تفعيل استقبال ملصقات الكيبورد (Gboard) مباشرة داخل مربع الكتابة!",
            color = OnlineGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StealthCalculatorOverlay(
    secretCode: String = "2000",
    onUnlock: () -> Unit
) {
    BackHandler(enabled = true) {}

    var display by remember { mutableStateOf("0") }
    var expression by remember { mutableStateOf("") }
    var prevOperand by remember { mutableStateOf<Double?>(null) }
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var resetDisplayOnNextDigit by remember { mutableStateOf(false) }

    fun formatResult(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        }
    }

    fun handleInput(btn: String) {
        when (btn) {
            "C" -> {
                display = "0"
                expression = ""
                prevOperand = null
                pendingOp = null
                resetDisplayOnNextDigit = false
            }
            "⌫" -> {
                display = if (display.length > 1) display.dropLast(1) else "0"
            }
            "±" -> {
                if (display != "0") {
                    display = if (display.startsWith("-")) display.drop(1) else "-$display"
                }
            }
            "%" -> {
                val v = display.toDoubleOrNull() ?: 0.0
                display = formatResult(v / 100.0)
                resetDisplayOnNextDigit = true
            }
            "+", "-", "×", "÷" -> {
                val currentVal = display.toDoubleOrNull() ?: 0.0
                if (prevOperand != null && pendingOp != null && !resetDisplayOnNextDigit) {
                    val prev = prevOperand ?: 0.0
                    val computed = when (pendingOp) {
                        "+" -> prev + currentVal
                        "-" -> prev - currentVal
                        "×" -> prev * currentVal
                        "÷" -> if (currentVal != 0.0) prev / currentVal else 0.0
                        else -> currentVal
                    }
                    prevOperand = computed
                    display = formatResult(computed)
                } else {
                    prevOperand = currentVal
                }
                pendingOp = btn
                expression = "${formatResult(prevOperand ?: 0.0)} $btn"
                resetDisplayOnNextDigit = true
            }
            "=" -> {
                if (display == secretCode && pendingOp == null) {
                    onUnlock()
                    return
                }
                val currentVal = display.toDoubleOrNull() ?: 0.0
                val prev = prevOperand
                val op = pendingOp
                if (prev != null && op != null) {
                    val result = when (op) {
                        "+" -> prev + currentVal
                        "-" -> prev - currentVal
                        "×" -> prev * currentVal
                        "÷" -> if (currentVal != 0.0) prev / currentVal else 0.0
                        else -> currentVal
                    }
                    expression = "${formatResult(prev)} $op ${formatResult(currentVal)} ="
                    display = formatResult(result)
                    prevOperand = null
                    pendingOp = null
                    resetDisplayOnNextDigit = true
                }
            }
            "." -> {
                if (resetDisplayOnNextDigit) {
                    display = "0."
                    resetDisplayOnNextDigit = false
                } else if (!display.contains(".")) {
                    display += "."
                }
            }
            else -> {
                if (display == "0" || resetDisplayOnNextDigit) {
                    display = btn
                    resetDisplayOnNextDigit = false
                } else if (display.length < 12) {
                    display += btn
                }
            }
        }
    }

    val buttonRows = listOf(
        listOf("C", "±", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "⌫", "=")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D12))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Calculator", color = Color(0xFF8A94A6), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("DEG", color = Color(0xFF4E5D78), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            if (expression.isNotEmpty()) {
                Text(
                    text = expression,
                    color = Color(0xFF8A94A6),
                    fontSize = 22.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = display,
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            buttonRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { label ->
                        val isOperator = label in listOf("÷", "×", "-", "+", "=")
                        val isTopAction = label in listOf("C", "±", "%")
                        val bgColor = when {
                            label == "=" -> Color(0xFFFF9F0A)
                            isOperator -> Color(0xFF2B3548)
                            isTopAction -> Color(0xFF1E2634)
                            else -> Color(0xFF151A24)
                        }
                        val txtColor = when {
                            label == "=" -> Color.White
                            isOperator -> Color(0xFFFF9F0A)
                            isTopAction -> Color(0xFF64D2FF)
                            else -> Color.White
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(62.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(bgColor)
                                .clickable { handleInput(label) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = txtColor, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    isGroup: Boolean,
    memberCount: Int,
    peerOnline: Boolean,
    isRecordingAudio: Boolean,
    typingUsers: List<String>,
    isAdminOnly: Boolean,
    onBack: () -> Unit,
    onActivateStealth: () -> Unit,
    onOpenGroupMembers: () -> Unit,
    onOpenSyncDialog: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VirusXSurface)
            .statusBarsPadding()
            .border(0.5.dp, VirusXGlassBorder)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "رجوع",
                tint = TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = isGroup, onClick = onOpenGroupMembers)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isAdminOnly && isGroup) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🔒", fontSize = 12.sp)
                }
            }
            val subtitle = when {
                isRecordingAudio -> "🎤 جاري تسجيل مقطع صوتي..."
                typingUsers.isNotEmpty() -> "✍️ ${typingUsers.joinToString(", ")} يكتب الآن..."
                isGroup -> "$memberCount أعضاء • اضغط للإدارة وإضافة أصدقاء"
                peerOnline -> "🟢 متصل الآن"
                else -> "غير متصل"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isRecordingAudio -> DangerRed
                    typingUsers.isNotEmpty() -> VirusXSecondaryWhisper
                    peerOnline -> OnlineGreen
                    isGroup -> VirusXSecondaryWhisper
                    else -> TextSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onActivateStealth) {
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = "وضع التمويه الذكي (آلة حاسبة)",
                tint = WarningAmber
            )
        }

        if (isGroup) {
            IconButton(onClick = onOpenGroupMembers) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "إدارة وأعضاء المجموعة",
                    tint = OnlineGreen
                )
            }
        }

        IconButton(onClick = onOpenSyncDialog) {
            Icon(
                imageVector = Icons.Default.Podcasts,
                contentDescription = "تشغيل متزامن مشترك",
                tint = VirusXSecondaryWhisper
            )
        }
    }
}

@Composable
private fun GroupMembersDialog(
    members: List<ProfileDto>,
    currentUserId: String?,
    creatorId: String?,
    promotedAdminIds: Set<String>,
    isAdminOnly: Boolean,
    searchQuery: String,
    searchResults: List<ProfileDto>,
    onSearchChange: (String) -> Unit,
    onAddMember: (ProfileDto) -> Unit,
    onRemoveMember: (ProfileDto) -> Unit,
    onTogglePromoteAdmin: (ProfileDto) -> Unit,
    onToggleAdminOnlyMode: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val isMeAdmin = currentUserId != null && (currentUserId == creatorId || promotedAdminIds.contains(currentUserId))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VirusXSurface,
        title = {
            Text("أعضاء وإدارة المجموعة (${members.size}/50)", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isMeAdmin) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VirusXSurfaceElevated)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🔒 قفل الدردشة للمشرفين فقط", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("منع الأعضاء من الكتابة وقت الإعلانات المهمة", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isAdminOnly,
                            onCheckedChange = onToggleAdminOnlyMode
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("ابحث لإضافة صديق (بالاسم / @ / الهاتف)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (searchResults.isNotEmpty()) {
                    Text("نتائج البحث (اضغط للإضافة):", color = OnlineGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    searchResults.forEach { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VirusXSurfaceElevated)
                                .clickable { onAddMember(candidate) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarCircle(displayName = candidate.displayName, avatarUrl = candidate.avatarUrl, sizeDp = 32)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(candidate.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("@${candidate.username} • ${candidate.phoneNumber ?: ""}", color = VirusXSecondaryWhisper, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.PersonAdd, contentDescription = "إضافة", tint = OnlineGreen)
                        }
                    }
                }

                Text("الأعضاء الحاليون:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                members.forEach { member ->
                    val isMemberCreator = member.id == creatorId
                    val isMemberPromoted = promotedAdminIds.contains(member.id)
                    val roleBadge = when {
                        isMemberCreator -> " 👑 (المؤسس)"
                        isMemberPromoted -> " ⭐ (مشرف)"
                        else -> ""
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VirusXSurfaceElevated)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarCircle(displayName = member.displayName, avatarUrl = member.avatarUrl, sizeDp = 34)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${member.displayName}$roleBadge",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text("@${member.username}", color = TextMuted, fontSize = 11.sp)
                        }
                        if (isMeAdmin && member.id != currentUserId && !isMemberCreator) {
                            IconButton(
                                onClick = { onTogglePromoteAdmin(member) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "ترقية لمشرف",
                                    tint = if (isMemberPromoted) WarningAmber else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onRemoveMember(member) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.PersonRemove, contentDescription = "إزالة", tint = DangerRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("تم", color = VirusXSecondaryWhisper, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SyncMiniPlayerBar(
    session: ChatSyncSessionDto,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit
) {
    val context = LocalContext.current
    val syncPlayer = remember(session.mediaUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(session.mediaUrl))
            prepare()
        }
    }

    DisposableEffect(syncPlayer) {
        onDispose {
            runCatching {
                syncPlayer.stop()
                syncPlayer.release()
            }
        }
    }

    LaunchedEffect(session.isPlaying, session.positionSeconds) {
        val targetMs = (session.positionSeconds * 1000).toLong()
        if (kotlin.math.abs(syncPlayer.currentPosition - targetMs) > 2500L) {
            syncPlayer.seekTo(targetMs)
        }
        if (session.isPlaying && !syncPlayer.isPlaying) {
            syncPlayer.play()
        } else if (!session.isPlaying && syncPlayer.isPlaying) {
            syncPlayer.pause()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VirusXWhisperSurface)
            .border(1.dp, VirusXSecondaryWhisper.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Podcasts, contentDescription = null, tint = VirusXSecondaryWhisper, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("المشغل المتزامن المباشر (${session.mediaType})", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(session.mediaUrl, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onSeekBackward, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Replay10, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (session.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = VirusXSecondaryWhisper
            )
        }
        IconButton(onClick = onSeekForward, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Forward10, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun StartSyncSessionDialog(
    onDismiss: () -> Unit,
    onStart: (String, String) -> Unit
) {
    var mediaUrl by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf("audio") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VirusXSurface,
        title = { Text("🎧 بدء جلسة استماع/مشاهدة متزامنة", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "أدخل رابط مباشر لملف صوتى (MP3/M4A) لتشغيله بشكل متزامن مع جميع أعضاء المحادثة:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = mediaUrl,
                    onValueChange = { mediaUrl = it },
                    label = { Text("رابط المقطع (URL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mediaType == "audio", onClick = { mediaType = "audio" }, label = { Text("صوت (Audio)") })
                    FilterChip(selected = mediaType == "video", onClick = { mediaType = "video" }, label = { Text("فيديو (Video)") })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (mediaUrl.isNotBlank()) onStart(mediaUrl, mediaType) },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary)
            ) {
                Text("تشغيل للجميع")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}
