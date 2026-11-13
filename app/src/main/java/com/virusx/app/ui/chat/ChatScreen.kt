package com.virusx.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.virusx.app.data.local.entity.MessageEntity
import com.virusx.app.data.remote.dto.ChatSyncSessionDto
import com.virusx.app.data.remote.dto.ProfileDto
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
import com.virusx.app.ui.theme.WarningAmber
import com.virusx.app.ui.theme.WhisperMessageGradient
import kotlinx.coroutines.delay
import org.json.JSONObject

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
    var showGroupMembersDialog by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var selectedMessageForActions by remember { mutableStateOf<MessageEntity?>(null) }

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
                                    kotlin.math.abs(nowSec - addedSec) in 0L..5L
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
                onOpenGroupMembers = { showGroupMembersDialog = true },
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
                        onOpenFullImage = { url -> fullScreenImageUrl = url },
                        onExpireSelfDestruct = { viewModel.expireSelfDestructMessage(msg.id) },
                        onToggleReaction = { emoji -> viewModel.toggleMessageReaction(msg.id, emoji) },
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

    fullScreenImageUrl?.let { imgUrl ->
        Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { fullScreenImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imgUrl,
                    contentDescription = "عرض الصورة كاملة",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
                IconButton(
                    onClick = { fullScreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(VirusXSurface)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextPrimary)
                }
            }
        }
    }

    if (showGroupMembersDialog && state.isGroup) {
        GroupMembersDialog(
            members = state.members,
            currentUserId = state.currentUserId,
            creatorId = state.chatCreatorId,
            searchQuery = state.memberSearchQuery,
            searchResults = state.memberSearchResults,
            onSearchChange = viewModel::onMemberSearchChange,
            onAddMember = viewModel::addMemberToGroup,
            onRemoveMember = viewModel::removeMemberFromGroup,
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
        AlertDialog(
            onDismissRequest = { selectedMessageForActions = null },
            containerColor = VirusXSurface,
            title = { Text("خيارات وتفاعل سريع", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                    TextButton(
                        onClick = {
                            viewModel.setReplyToMessage(targetMsg)
                            selectedMessageForActions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = VirusXSecondaryWhisper)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("رد مقتبس على الرسالة", color = TextPrimary)
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
                            Text("تعديل نص الرسالة", color = TextPrimary)
                        }
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when {
                typingUsers.isNotEmpty() -> "✍️ ${typingUsers.joinToString(", ")} يكتب الآن..."
                isGroup -> "$memberCount أعضاء • اضغط لإضافة أصدقاء"
                peerOnline -> "متصل الآن"
                else -> "غير متصل"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    typingUsers.isNotEmpty() -> VirusXSecondaryWhisper
                    peerOnline -> OnlineGreen
                    isGroup -> VirusXSecondaryWhisper
                    else -> TextSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isGroup) {
            IconButton(onClick = onOpenGroupMembers) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "إضافة أعضاء للمجموعة",
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
    searchQuery: String,
    searchResults: List<ProfileDto>,
    onSearchChange: (String) -> Unit,
    onAddMember: (ProfileDto) -> Unit,
    onRemoveMember: (ProfileDto) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VirusXSurface,
        title = {
            Text("أعضاء المجموعة (${members.size}/50)", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VirusXSurfaceElevated)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (member.id == creatorId) "${member.displayName} (المشرف)" else member.displayName,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text("@${member.username}", color = TextMuted, fontSize = 11.sp)
                        }
                        if (currentUserId == creatorId && member.id != currentUserId) {
                            IconButton(onClick = { onRemoveMember(member) }, modifier = Modifier.size(28.dp)) {
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
        if (kotlin.math.abs(syncPlayer.currentPosition - targetMs) !in 0L..2500L) {
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
    whisperTargetName: String?,
    repliedMessage: MessageEntity?,
    isPlayingVoice: Boolean,
    onPlayVoice: () -> Unit,
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

    val destructSec = message.destructSeconds
    var remainingSeconds by remember(message.id, destructSec, message.isDeleted) {
        mutableIntStateOf(if (!message.isDeleted && destructSec != null && destructSec != 0) destructSec else 0)
    }
    LaunchedEffect(message.id, destructSec, message.isDeleted) {
        if (!message.isDeleted && destructSec != null && destructSec != 0) {
            var left = destructSec
            while (left != 0) {
                remainingSeconds = left
                delay(1000L)
                left--
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
            val raw = message.reactionsJson
            if (!raw.isNullOrBlank()) {
                val obj = JSONObject(raw)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = obj.optJSONArray(k)
                    if (arr != null && arr.length() != 0) {
                        emojis.add(k)
                        counts.add(arr.length())
                        val hasMe = (0 until arr.length()).any { idx -> arr.getString(idx) == currentUserId }
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
                        if (kotlin.math.abs(dragOffset) !in 0f..60f) onSwipeReply()
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> dragOffset += dragAmount }
                )
            },
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 96.dp, max = 285.dp)
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
            if (isGroup && !isMine) {
                Text(
                    text = senderName,
                    color = VirusXSecondaryWhisper,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
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
                            text = "🔍 اضغط لعرض الصورة كاملة",
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
                    VoiceNoteBubbleContent(
                        message = message,
                        isMine = isMine,
                        isPlaying = isPlayingVoice,
                        onPlayToggle = onPlayVoice
                    )
                }
                else -> {
                    Text(
                        text = rawContent,
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
                    if (remainingSeconds != 0) {
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

        val (rEmojis, rCounts, rMine) = reactionData
        if (rEmojis.isNotEmpty() && !message.isDeleted) {
            FlowRow(
                modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
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
                        if (count != 1) {
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
                Text("🎛️ فلتر: ${message.voicePitchFilter}", color = VirusXSecondaryWhisper, fontSize = 10.sp)
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
                    placeholder = { Text("اكتب رسالتك هنا...", color = TextMuted, fontSize = 14.sp) },
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
                        selected = false,
                        onClick = onPickImage,
                        label = { Text("📷 صورة", fontSize = 11.sp) }
                    )
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
