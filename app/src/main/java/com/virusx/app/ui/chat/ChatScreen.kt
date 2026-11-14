package com.virusx.app.ui.chat

import android.Manifest
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.virusx.app.data.local.entity.MessageEntity
import com.virusx.app.data.remote.dto.ChatSyncSessionDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val activeSyncSession by viewModel.activeSyncSession.collectAsState()
    val typingUsers by viewModel.typingUsers.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val liveWaveform by viewModel.liveWaveform.collectAsState()
    val recordingDuration by viewModel.recordingDurationSeconds.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()

    var showWhisperTargetDialog by remember { mutableStateOf(false) }
    var showStartSyncDialog by remember { mutableStateOf(false) }
    var showGroupMembersDialog by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var selectedMessageForActions by remember { mutableStateOf<MessageEntity?>(null) }
    var isStealthCalculatorActive by remember { mutableStateOf(false) }
    var showStickerDrawer by remember { mutableStateOf(false) }

    // Local persistent states for Pinned Message, Admin-Only Lock, and Promoted Admins
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

    // Sync system messages for Pin & Admin-Only across all group members
    LaunchedEffect(messages) {
        messages.lastOrNull()?.let { latest ->
            val raw = latest.content.orEmpty()
            when {
                raw.startsWith("📌 [رسالة مثبتة]: ") -> {
                    val body = raw.removePrefix("📌 [رسالة مثبتة]: ").trim()
                    pinnedMessageId = latest.replyToMessageId ?: latest.id
                    pinnedMessageText = body
                    chatPrefs.edit()
                        .putString("pinned_id", pinnedMessageId)
                        .putString("pinned_text", pinnedMessageText)
                        .apply()
                }
                raw == "🔒 قام المشرف بقفل الدردشة (الإرسال للمشرفين فقط)" -> {
                    isAdminOnlyChat = true
                    chatPrefs.edit().putBoolean("admin_only", true).apply()
                }
                raw == "🔓 قام المشرف بفتح الدردشة لجميع الأعضاء" -> {
                    isAdminOnlyChat = false
                    chatPrefs.edit().putBoolean("admin_only", false).apply()
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
    // When stealth mode is active, ONLY render StealthCalculatorOverlay so zero touches leak to ChatTopBar!
    if (isStealthCalculatorActive) {
        StealthCalculatorOverlay(
            secretCode = "2000",
            onUnlock = { isStealthCalculatorActive = false }
        )
        return
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceRecording()
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

    // Mention autocomplete detection (@username or @الكل)
    val mentionQuery: String? = remember(state.inputText, state.isGroup) {
        if (!state.isGroup) null
        else {
            val lastWord = state.inputText.substringAfterLast(" ", state.inputText)
            if (lastWord.startsWith("@") && lastWord.length >= 1) {
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
                    peerOnline = state.peerOnline,
                    isRecordingAudio = isRecording,
                    typingUsers = typingUsers.values.toList(),
                    isAdminOnly = isAdminOnlyChat,
                    onBack = onBack,
                    onActivateStealth = {
                        showGroupMembersDialog = false
                        showStartSyncDialog = false
                        showWhisperTargetDialog = false
                        selectedMessageForActions = null
                        isStealthCalculatorActive = true
                    },
                    onOpenGroupMembers = { showGroupMembersDialog = true },
                    onOpenSyncDialog = { showStartSyncDialog = true }
                )

                // Pinned Message Banner (Telegram/WhatsApp style)
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

                    MessageBubbleItem(
                        message = msg,
                        isMine = isMine,
                        isGroup = state.isGroup,
                        currentUserId = state.currentUserId,
                        senderName = resolvedName,
                        senderAvatarUrl = senderProfile?.avatarUrl,
                        whisperTargetName = whisperTargetProfile?.displayName,
                        repliedMessage = repliedMessage,
                        onOpenFullImage = { url -> fullScreenImageUrl = url },
                        onExpireSelfDestruct = { viewModel.expireSelfDestructMessage(msg.id) },
                        onToggleReaction = { emoji -> viewModel.toggleMessageReaction(msg.id, emoji) },
                        onSwipeReply = { viewModel.setReplyToMessage(msg) },
                        onLongPress = { selectedMessageForActions = msg }
                    )
                }
            }

            // Mention Autocomplete Popup (@الكل & @username)
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

            // WhatsApp-Compatible Sticker Drawer + Import from Phone/WhatsApp
            if (showStickerDrawer) {
                StickerDrawerBar(
                    context = context,
                    onSendQuickSticker = { stickerText ->
                        viewModel.onInputTextChange(stickerText)
                        viewModel.sendTextMessage()
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
                ChatComposerSection(
                    state = state,
                    isRecording = isRecording,
                    liveWaveform = liveWaveform,
                    recordingDuration = recordingDuration,
                    isTranscribing = isTranscribing,
                    showStickerDrawer = showStickerDrawer,
                    onToggleStickerDrawer = { showStickerDrawer = !showStickerDrawer },
                    onInsertMentionPrefix = {
                        val current = state.inputText
                        val next = if (current.isEmpty() || current.endsWith(" ")) "${current}@" else "$current @"
                        viewModel.onInputTextChange(next)
                    },
                    onInputChange = viewModel::onInputTextChange,
                    onSendText = viewModel::sendTextMessage,
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onOpenWhisperPicker = { showWhisperTargetDialog = true },
                    onCycleDestructTimer = viewModel::cycleDestructTimer,
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
                        if (hasPerm) viewModel.startVoiceRecording()
                        else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onCancelRecording = viewModel::cancelVoiceRecording,
                    onSendVoice = viewModel::stopAndSendVoiceNote,
                    onSpeechToText = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) viewModel.transcribeSpeechIntoInput()
                        else speechPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }
        }
    }

    // Full-Screen Image Viewer + Save to Gallery Button
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
                        onClick = {
                            saveImageUrlToGallery(context, imgUrl)
                        },
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
                    Text("لا يوجد أعضاء آخرون في المجموعة حالياً. اضغط على أيقونة إضافة الأعضاء في الأعلى لإضافة أصدقائك.", color = TextSecondary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "اختر العضو الذي سيشاهد محتوى الهمسة:",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
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

    // Long-Press Message Action Sheet (Reactions + Seen By + Copy + Forward + Pin + Edit + Admin Delete)
    selectedMessageForActions?.let { targetMsg ->
        val isMine = targetMsg.senderId == state.currentUserId
        val canDeleteForEveryone = isMine || isCurrentUserGroupAdmin

        // Compute Seen By list in groups (members who sent messages/reactions after or read status)
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

                    // Seen By section in Groups
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
                                Toast.makeText(context, "تم تجهيز الرسالة لإعادة التوجيه (ومنسوخة لأي محادثة أخرى)", Toast.LENGTH_SHORT).show()
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
    onImportStickerFromDevice: () -> Unit
) {
    val stickerPacks = remember {
        listOf(
            "🔥😎", "😂💀", "🫡⚡", "❤️✨", "👑🦁",
            "🎯🚀", "🤫🔒", "🎉🥳", "👀🍿", "🤝💯"
        )
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
                text = "🎭 الملصقات السريعة وملصقات واتساب",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = "📥 استيراد ملصق من واتساب/المعرض",
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

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(stickerPacks) { st ->
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
            text = "💡 نصيحة: يمكنك أيضاً إرسال أي ملصق محفوظ في كيبورد هاتفك (Gboard) مباشرة من زر الإيموجي في الكيبورد!",
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun StealthCalculatorOverlay(
    secretCode: String = "2000",
    onUnlock: () -> Unit
) {
    BackHandler(enabled = true) {
        // Stay locked inside calculator until 2000= is typed
    }

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
            ) { /* Consume all stray taps on empty areas */ }
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
            Text(
                text = "Calculator",
                color = Color(0xFF8A94A6),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "DEG",
                color = Color(0xFF4E5D78),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
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
                            Text(
                                text = label,
                                color = txtColor,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold
                            )
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
                peerOnline -> "متصل الآن"
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MessageBubbleItem(
    message: MessageEntity,
    isMine: Boolean,
    isGroup: Boolean,
    currentUserId: String?,
    senderName: String,
    senderAvatarUrl: String?,
    whisperTargetName: String?,
    repliedMessage: MessageEntity?,
    onOpenFullImage: (String) -> Unit,
    onExpireSelfDestruct: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onSwipeReply: () -> Unit,
    onLongPress: () -> Unit
) {
    val rawContent = message.content.orEmpty()
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
                    .widthIn(min = 96.dp, max = 270.dp)
                    .clip(bubbleShape)
                    .then(
                        when {
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
                Text(
                    text = if (isMine) (if (senderName.contains("أنت")) senderName else "أنت") else senderName,
                    color = if (isMine) TextPrimary.copy(alpha = 0.9f) else VirusXSecondaryWhisper,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

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
                            fontSize = 48.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    message.messageType == "image" -> {
                        val imgUrl = message.mediaUrl
                        if (!imgUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = imgUrl,
                                contentDescription = "صورة مرسلة - اضغط للتكبير",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenFullImage(imgUrl) }
                            )
                            Text(
                                text = "🔍 اضغط لعرض الصورة كاملة أو حفظها",
                                color = VirusXSecondaryWhisper,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        if (!message.content.isNullOrBlank() && message.content != "📷 صورة") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = message.content, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                    message.messageType == "voice" -> {
                        AdvancedVoiceNotePlayer(
                            message = message,
                            isMine = isMine
                        )
                    }
                    else -> {
                        // Highlight @username and @الكل mentions inside message text
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
                            Text(
                                text = if (message.status == "read") "✓✓ مقروءة" else "✓ أُرسلت",
                                color = if (message.status == "read") VirusXSecondaryWhisper else TextPrimary.copy(alpha = 0.75f),
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
private fun AdvancedVoiceNotePlayer(
    message: MessageEntity,
    isMine: Boolean
) {
    val context = LocalContext.current
    val expiredViewOnce = message.voiceViewOnce && message.voicePlayed && !isMine
    var showTranscript by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    val totalSec = (message.voiceDurationSeconds ?: 10).coerceAtLeast(1)
    var currentPosMs by remember { mutableStateOf(0L) }
    var totalDurationMs by remember { mutableStateOf(totalSec * 1000L) }

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
                        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
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

            // Interactive Seekbar Slider to scrub to any exact second (e.g. 0:05)
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

            // Speed Toggle Pill (1x -> 1.5x -> 2x)
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
                        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatSec(currentPosMs)} / ${formatSec(totalDurationMs)}",
                color = TextPrimary.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Always-available Voice-to-Text button for noisy environments
            Text(
                text = if (showTranscript) "🔼 إخفاء النص" else "📝 تحويل الفويس لكتابة (أ ب)",
                color = OnlineGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable { showTranscript = !showTranscript }
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }

        if (showTranscript && !expiredViewOnce) {
            val transcriptContent = message.voiceTranscript?.takeIf { it.isNotBlank() }
                ?: "🎙️ النص المستخرج من الرسالة الصوتية (${totalSec} ثانية): [رسالة صوتية مسجلة — استخدم زر 🎙️ إملاء نصي بالأسفل لإرفاق النص الحرفي تلقائياً أثناء التسجيل في الأماكن الصاخبة]"
            Text(
                text = transcriptContent,
                color = TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun ChatComposerSection(
    state: ChatUiState,
    isRecording: Boolean,
    liveWaveform: List<Float>,
    recordingDuration: Int,
    isTranscribing: Boolean,
    showStickerDrawer: Boolean,
    onToggleStickerDrawer: () -> Unit,
    onInsertMentionPrefix: () -> Unit,
    onInputChange: (String) -> Unit,
    onSendText: () -> Unit,
    onPickImage: () -> Unit,
    onOpenWhisperPicker: () -> Unit,
    onCycleDestructTimer: () -> Unit,
    onClearReply: () -> Unit,
    onCancelEdit: () -> Unit,
    onClearWhisper: () -> Unit,
    onSelectPitchFilter: (String) -> Unit,
    onToggleViewOnce: () -> Unit,
    onStartRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSendVoice: () -> Unit,
    onSpeechToText: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VirusXSurface)
            .border(0.5.dp, VirusXGlassBorder)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        state.editingMessage?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VirusXPrimary.copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = VirusXSecondaryWhisper, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("جاري تعديل الرسالة...", color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onCancelEdit, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                }
            }
        }

        state.replyToMessage?.let { replyMsg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VirusXSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = VirusXPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "رد على: ${replyMsg.content ?: "رسالة"}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearReply, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
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
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = VirusXSecondaryWhisper, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "همسة سرية موجهة إلى: ${targetUser.displayName}",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearWhisper, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                }
            }
        }

        if (isRecording) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val filters = listOf(
                        "normal" to "صوت طبيعى",
                        "deep" to "صوت عميق",
                        "robot" to "روبوت",
                        "fast" to "سريع"
                    )
                    items(filters) { (key, label) ->
                        FilterChip(
                            selected = state.selectedPitchFilter == key,
                            onClick = { onSelectPitchFilter(key) },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.voiceViewOnce,
                            onClick = onToggleViewOnce,
                            label = { Text("🔥 مرة واحدة", fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VirusXSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancelRecording) {
                        Icon(Icons.Default.Delete, contentDescription = "إلغاء التسجيل", tint = DangerRed)
                    }

                    Text("🔴 ${recordingDuration}ث", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val bars = if (liveWaveform.isEmpty()) List(20) { 0.2f } else liveWaveform.takeLast(24)
                        bars.forEach { amp ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height((amp * 24).dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(VirusXSecondaryWhisper)
                            )
                        }
                    }

                    IconButton(
                        onClick = onSendVoice,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(VirusXPrimary)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال الفويس نوت", tint = TextPrimary)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = onInputChange,
                    placeholder = { Text("اكتب رسالتك أو @منشن هنا...", color = TextMuted, fontSize = 14.sp) },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = VirusXSurfaceElevated,
                        unfocusedContainerColor = VirusXSurfaceElevated,
                        focusedBorderColor = VirusXPrimary,
                        unfocusedBorderColor = VirusXGlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
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

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = showStickerDrawer,
                        onClick = onToggleStickerDrawer,
                        label = { Text("🎭 ملصق", fontSize = 11.sp) }
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = onPickImage,
                        label = { Text("📷 صورة", fontSize = 11.sp) }
                    )
                }
                if (state.isGroup) {
                    item {
                        FilterChip(
                            selected = state.inputText.contains("@"),
                            onClick = onInsertMentionPrefix,
                            label = { Text("@ منشن", fontSize = 11.sp) }
                        )
                    }
                }
                item {
                    val timerLabel = state.destructTimerSeconds?.let { "⏳ تدمير: ${it}ث" } ?: "⏳ مؤقت"
                    FilterChip(
                        selected = state.destructTimerSeconds != null,
                        onClick = onCycleDestructTimer,
                        label = { Text(timerLabel, fontSize = 11.sp) }
                    )
                }
                if (state.isGroup) {
                    item {
                        FilterChip(
                            selected = state.whisperTargetUser != null,
                            onClick = onOpenWhisperPicker,
                            label = { Text("🔒 همسة سرية", fontSize = 11.sp) }
                        )
                    }
                }
                item {
                    FilterChip(
                        selected = isTranscribing,
                        onClick = onSpeechToText,
                        label = { Text(if (isTranscribing) "جاري الاستماع..." else "🎙️ إملاء نصى", fontSize = 11.sp) }
                    )
                }
            }
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
