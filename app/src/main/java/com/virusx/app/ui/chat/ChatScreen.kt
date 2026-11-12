package com.virusx.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.virusx.app.data.local.entity.MessageEntity
import com.virusx.app.data.remote.dto.ChatSyncSessionDto
import com.virusx.app.ui.theme.DangerRed
import com.virusx.app.ui.theme.OnlineGreen
import com.virusx.app.ui.theme.ReceivedBubbleColor
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
import com.virusx.app.ui.theme.VirusXWhisperSurface
import com.virusx.app.ui.theme.WhisperMessageGradient

@Composable
fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val activeSyncSession by viewModel.activeSyncSession.collectAsState()
    val typingUsers by viewModel.typingUsers.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val liveWaveform by viewModel.liveWaveform.collectAsState()
    val recordingDuration by viewModel.recordingDurationSeconds.collectAsState()
    val playingMessageId by viewModel.currentlyPlayingMessageId.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()

    var showWhisperTargetDialog by remember { mutableStateOf(false) }
    var showStartSyncDialog by remember { mutableStateOf(false) }
    var selectedMessageForActions by remember { mutableStateOf<MessageEntity?>(null) }

    val listState = rememberLazyListState()

    LaunchedEffect(chatId) {
        viewModel.initChat(chatId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecording()
        }
    }

    val speechPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.transcribeSpeechIntoInput()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.sendImageMessage(uri)
        }
    }

    val memberMap = remember(state.members) { state.members.associateBy { it.id } }
    val messageMap = remember(messages) { messages.associateBy { it.id } }

    Scaffold(
        containerColor = VirusXBackground,
        topBar = {
            ChatTopBar(
                title = state.chatTitle,
                isGroup = state.isGroup,
                memberCount = state.members.size,
                peerOnline = state.peerOnline,
                typingUsers = typingUsers.values.toList(),
                onBack = onBack,
                onOpenSyncDialog = { showStartSyncDialog = true }
            )
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

            state.errorMessage?.let { err ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DangerRed.copy(alpha = 0.2f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = err,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = viewModel::clearError, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == state.currentUserId
                    val senderProfile = memberMap[msg.senderId]
                    val whisperTargetProfile = msg.whisperTargetId?.let { memberMap[it] }
                    val repliedMessage = msg.replyToMessageId?.let { messageMap[it] }

                    MessageBubbleItem(
                        message = msg,
                        isMine = isMine,
                        isGroup = state.isGroup,
                        currentUserId = state.currentUserId,
                        senderName = senderProfile?.displayName ?: "عضو",
                        whisperTargetName = whisperTargetProfile?.displayName,
                        repliedMessage = repliedMessage,
                        isPlayingVoice = playingMessageId == msg.id,
                        onPlayVoice = { viewModel.togglePlayVoiceNote(msg) },
                        onSwipeReply = { viewModel.setReplyToMessage(msg) },
                        onLongPress = { selectedMessageForActions = msg }
                    )
                }
            }

            ChatComposerSection(
                state = state,
                isRecording = isRecording,
                liveWaveform = liveWaveform,
                recordingDuration = recordingDuration,
                isTranscribing = isTranscribing,
                onInputChange = viewModel::onInputTextChange,
                onSendText = viewModel::sendTextMessage,
                onPickImage = { imagePickerLauncher.launch("image/*") },
                onOpenWhisperPicker = { showWhisperTargetDialog = true },
                onClearReply = { viewModel.setReplyToMessage(null) },
                onClearWhisper = { viewModel.setWhisperTarget(null) },
                onSelectPitchFilter = viewModel::setPitchFilter,
                onToggleViewOnce = viewModel::toggleVoiceViewOnce,
                onStartRecording = {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        viewModel.startVoiceRecording()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onCancelRecording = viewModel::cancelVoiceRecording,
                onSendVoice = viewModel::stopAndSendVoiceNote,
                onSpeechToText = {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        viewModel.transcribeSpeechIntoInput()
                    } else {
                        speechPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }
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
                        Text(
                            text = "اختر العضو الذي سيشاهد محتوى الهمسة (باقي الأعضاء سيرون إشعار همسة مشفرة فقط):",
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
                                Text(
                                    text = member.displayName,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "@${member.username}",
                                    color = VirusXSecondaryWhisper,
                                    fontSize = 12.sp
                                )
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
        AlertDialog(
            onDismissRequest = { selectedMessageForActions = null },
            containerColor = VirusXSurface,
            title = { Text("خيارات الرسالة", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.setReplyToMessage(targetMsg)
                            selectedMessageForActions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = VirusXSecondaryWhisper)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("رد على هذه الرسالة", color = TextPrimary)
                    }

                    if (isMine && !targetMsg.isDeleted) {
                        TextButton(
                            onClick = {
                                viewModel.deleteMessageForEveryone(targetMsg.id)
                                selectedMessageForActions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حذف الرسالة لدى الجميع", color = DangerRed)
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
private fun ChatTopBar(
    title: String,
    isGroup: Boolean,
    memberCount: Int,
    peerOnline: Boolean,
    typingUsers: List<String>,
    onBack: () -> Unit,
    onOpenSyncDialog: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VirusXSurface)
            .border(0.5.dp, VirusXGlassBorder)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "رجوع",
                tint = TextPrimary
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when {
                typingUsers.isNotEmpty() -> "✍️ ${typingUsers.joinToString(", ")} يكتب الآن..."
                isGroup -> "$memberCount أعضاء في المجموعة"
                peerOnline -> "متصل الآن"
                else -> "غير متصل"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    typingUsers.isNotEmpty() -> VirusXSecondaryWhisper
                    peerOnline -> OnlineGreen
                    else -> TextSecondary
                }
            )
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
        Icon(
            imageVector = Icons.Default.Podcasts,
            contentDescription = null,
            tint = VirusXSecondaryWhisper,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "المشغل المتزامن المباشر (${session.mediaType})",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = session.mediaUrl,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleItem(
    message: MessageEntity,
    isMine: Boolean,
    isGroup: Boolean,
    currentUserId: String?,
    senderName: String,
    whisperTargetName: String?,
    repliedMessage: MessageEntity?,
    isPlayingVoice: Boolean,
    onPlayVoice: () -> Unit,
    onSwipeReply: () -> Unit,
    onLongPress: () -> Unit
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val isWhisper = message.whisperTargetId != null
    val canReadWhisper = !isWhisper || isMine || message.whisperTargetId == currentUserId

    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(dragOffset) > 60f) {
                            onSwipeReply()
                        }
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffset += dragAmount
                    }
                )
            },
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 295.dp)
                .clip(bubbleShape)
                .then(
                    when {
                        isWhisper -> Modifier
                            .background(VirusXWhisperSurface)
                            .border(1.5.dp, VirusXSecondaryWhisper, bubbleShape)
                        isMine -> Modifier.background(SentMessageGradient)
                        else -> Modifier
                            .background(ReceivedBubbleColor)
                            .border(1.dp, VirusXGlassBorder, bubbleShape)
                    }
                )
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                )
                .padding(12.dp)
        ) {
            if (isGroup && !isMine) {
                Text(
                    text = senderName,
                    color = VirusXSecondaryWhisper,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
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
                        text = if (canReadWhisper) {
                            "همسة سرية إلى ${whisperTargetName ?: "عضو"}"
                        } else {
                            "همسة مشفرة لعضو آخر"
                        },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (repliedMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "↩️ رد على رسالة:",
                        color = VirusXSecondaryWhisper,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (repliedMessage.isDeleted) "رسالة محذوفة" else (repliedMessage.content ?: "مرفق"),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            when {
                message.isDeleted -> {
                    Text(
                        text = "🚫 تم حذف هذه الرسالة",
                        color = TextMuted,
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp
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
                message.messageType == "image" -> {
                    if (!message.mediaUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = message.mediaUrl,
                            contentDescription = "صورة مرسلة",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    if (!message.content.isNullOrBlank() && message.content != "📷 صورة") {
                        Text(text = message.content, color = TextPrimary, fontSize = 14.sp)
                    }
                }
                message.messageType == "voice" -> {
                    VoiceNoteBubbleContent(
                        message = message,
                        isMine = isMine,
                        isPlaying = isPlayingVoice,
                        onPlayToggle = onPlayVoice
                    )
                }
                else -> {
                    Text(
                        text = message.content ?: "",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }
            }

            if (isMine && !message.isDeleted) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (message.status == "read") "✓✓ مقروءة" else "✓ أُرسلت",
                    color = if (message.status == "read") VirusXSecondaryWhisper else TextPrimary.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun VoiceNoteBubbleContent(
    message: MessageEntity,
    isMine: Boolean,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit
) {
    val expiredViewOnce = message.voiceViewOnce && message.voicePlayed && !isMine
    var showTranscript by remember { mutableStateOf(false) }

    val waveformBars = remember(message.voiceWaveformJson) {
        message.voiceWaveformJson
            ?.split(",")
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: List(22) { 0.35f }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onPlayToggle,
                enabled = !expiredViewOnce,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.25f))
            ) {
                Icon(
                    imageVector = when {
                        expiredViewOnce -> Icons.Default.Lock
                        isPlaying -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    tint = TextPrimary
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                waveformBars.take(24).forEach { amp ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((amp.coerceIn(0.15f, 1f) * 26).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isPlaying) VirusXSecondaryWhisper else TextPrimary.copy(alpha = 0.8f))
                    )
                }
            }

            Text(
                text = "${message.voiceDurationSeconds ?: 0}ث",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!message.voicePitchFilter.isNullOrBlank() && message.voicePitchFilter != "normal") {
                Text(
                    text = "🎚️ فلتر: ${message.voicePitchFilter}",
                    color = VirusXSecondaryWhisper,
                    fontSize = 10.sp
                )
            }
            if (message.voiceViewOnce) {
                Text(
                    text = if (expiredViewOnce) "🔒 تم الاستماع (مرة واحدة)" else "🔥 استماع مرة واحدة",
                    color = if (expiredViewOnce) TextMuted else VirusXSecondaryWhisper,
                    fontSize = 10.sp
                )
            }
            if (!message.voiceTranscript.isNullOrBlank() && !expiredViewOnce) {
                Text(
                    text = if (showTranscript) "إخفاء النص ▲" else "عرض النص (أ ب) ▼",
                    color = VirusXSecondaryWhisper,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier.clickable { showTranscript = !showTranscript }
                )
            }
        }

        if (showTranscript && !message.voiceTranscript.isNullOrBlank() && !expiredViewOnce) {
            Text(
                text = "📝 ${message.voiceTranscript}",
                color = TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(6.dp)
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
    onInputChange: (String) -> Unit,
    onSendText: () -> Unit,
    onPickImage: () -> Unit,
    onOpenWhisperPicker: () -> Unit,
    onClearReply: () -> Unit,
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
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                        "normal" to "صوت طبيعي",
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

                    Text(
                        text = "🔴 ${recordingDuration}ث",
                        color = DangerRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

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
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.isGroup) {
                    IconButton(onClick = onOpenWhisperPicker, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "همسة لعضو",
                            tint = VirusXSecondaryWhisper
                        )
                    }
                }

                IconButton(onClick = onPickImage, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "إرسال صورة",
                        tint = TextSecondary
                    )
                }

                IconButton(onClick = onSpeechToText, modifier = Modifier.size(38.dp)) {
                    if (isTranscribing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = VirusXSecondaryWhisper)
                    } else {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "تحويل الصوت لنص",
                            tint = VirusXSecondaryWhisper
                        )
                    }
                }

                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = onInputChange,
                    placeholder = { Text("اكتب رسالتك...", color = TextMuted, fontSize = 14.sp) },
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
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(VirusXPrimary, VirusXSecondaryWhisper)))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال", tint = TextPrimary)
                    }
                } else {
                    IconButton(
                        onClick = onStartRecording,
                        modifier = Modifier
                            .size(44.dp)
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
                    text = "أدخل رابط مباشر لملف صوتي (MP3/M4A) لتشغيله بشكل متزامن مع جميع أعضاء المحادثة:",
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
                    FilterChip(
                        selected = mediaType == "audio",
                        onClick = { mediaType = "audio" },
                        label = { Text("صوت (Audio)") }
                    )
                    FilterChip(
                        selected = mediaType == "video",
                        onClick = { mediaType = "video" },
                        label = { Text("فيديو (Video)") }
                    )
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
