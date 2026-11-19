package com.virusx.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.virusx.app.data.local.entity.ChatEntity
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.ui.components.VirusXShimmerChatListPlaceholder
import com.virusx.app.ui.components.VirusXSpringMessageEntrance
import com.virusx.app.ui.theme.DangerRed
import com.virusx.app.ui.theme.OnlineGreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenChat: (String) -> Unit,
    onOpenStories: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStealthVault: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val visibleChats by viewModel.visibleChats.collectAsState()
    val otaProgress by viewModel.otaDownloadProgress.collectAsState()
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var selectedChatForVaultDialog by remember { mutableStateOf<ChatEntity?>(null) }

    val colors = MaterialTheme.colorScheme
    val subtleBorder = colors.onSurface.copy(alpha = 0.07f)
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.openStealthVaultEvent) {
        if (state.openStealthVaultEvent) {
            viewModel.consumeStealthVaultEvent()
            onOpenStealthVault()
        }
    }

    LaunchedEffect(state.navigateToChatId) {
        val targetChatId = state.navigateToChatId
        if (targetChatId != null) {
            viewModel.consumeNavigateToChat()
            onOpenChat(targetChatId)
        }
    }

    state.maintenanceConfig?.let { maint ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Rounded.Build, contentDescription = null, tint = colors.primary, modifier = Modifier.size(56.dp))
                Text("تحديث وصيانة النظام", style = MaterialTheme.typography.headlineMedium, color = colors.onSurface, fontWeight = FontWeight.Bold)
                Text(
                    text = maint.maintenanceMessage.ifBlank { "التطبيق تحت الصيانة حالياً، سنعود قريباً." },
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = viewModel::refreshAll,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("إعادة المحاولة", color = Color.White)
                }
            }
        }
        return
    }

    val filteredChats = remember(visibleChats, state.searchQuery) {
        if (state.searchQuery.isBlank()) {
            visibleChats
        } else {
            val q = state.searchQuery.trim()
            visibleChats.filter {
                it.displayTitle.contains(q, ignoreCase = true) ||
                    (it.peerPhone?.contains(q, ignoreCase = true) == true)
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateGroupDialog = true },
                containerColor = colors.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.GroupAdd, contentDescription = "إنشاء جروب جديد")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Modern Clean Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VirusX",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.onSurface
                    )
                    Text(
                        text = state.currentUserProfile?.let {
                            "${it.displayName} • @${it.username}"
                        } ?: "مراسلة فورية مشفرة",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeaderActionIcon(
                        icon = Icons.Rounded.AutoAwesomeMotion,
                        contentDescription = "الحالات",
                        tint = colors.primary,
                        onClick = onOpenStories
                    )
                    HeaderActionIcon(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "تحديث",
                        tint = colors.onSurfaceVariant,
                        onClick = viewModel::refreshAll
                    )
                    HeaderActionIcon(
                        icon = Icons.Rounded.Settings,
                        contentDescription = "الإعدادات",
                        tint = colors.onSurface,
                        onClick = onOpenSettings
                    )
                }
            }

            if (state.isRefreshing && visibleChats.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    color = colors.primary,
                    trackColor = colors.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(CircleShape)
                )
            }

            state.errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DangerRed.copy(alpha = 0.14f))
                        .border(1.dp, DangerRed.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = err,
                        color = DangerRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = viewModel::clearError, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Pill-Shaped Modern Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "ابحث بالاسم، اليوزرنيم، أو رقم الهاتف...",
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = colors.primary
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "مسح",
                                tint = colors.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.55f),
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Global Admin Broadcast Banner
            state.latestBroadcast?.let { broadcast ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.primary.copy(alpha = 0.10f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.primary.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Campaign,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = broadcast.title,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = broadcast.body,
                                color = colors.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (state.isRefreshing && visibleChats.isEmpty()) {
                VirusXShimmerChatListPlaceholder(itemCount = 6)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.searchQuery.isNotBlank() && state.searchedUsers.isNotEmpty()) {
                        item {
                            Text(
                                text = "نتائج البحث (${state.searchedUsers.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(state.searchedUsers, key = { "user_${it.id}" }) { user ->
                            UserSearchResultCard(
                                profile = user,
                                onClick = { viewModel.startDirectChatWithUser(user) }
                            )
                        }
                    }

                    item {
                        Text(
                            text = "المحادثات",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    if (filteredChats.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "لا توجد محادثات حالياً.\nابحث عن صديق بالاسم أو اليوزرنيم أو رقم الهاتف لبدء الدردشة.",
                                    color = colors.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        items(filteredChats, key = { it.id }) { chat ->
                            VirusXSpringMessageEntrance {
                                ChatRowItem(
                                    chat = chat,
                                    onClick = { onOpenChat(chat.id) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedChatForVaultDialog = chat
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // In-App OTA Update Dialog
    state.availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { if (!update.forceUpdate) viewModel.dismissUpdateDialog() },
            containerColor = colors.surface,
            shape = RoundedCornerShape(22.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = colors.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تحديث جديد (${update.latestVersionName})", color = colors.onSurface, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = update.releaseNotes.ifBlank { "يتوفر إصدار جديد من VirusX يتضمن تحسينات وإصلاحات هامة." },
                        color = colors.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    if (otaProgress != null) {
                        LinearProgressIndicator(
                            progress = { otaProgress ?: 0f },
                            color = colors.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "جاري تحميل التحديث (${((otaProgress ?: 0f) * 100).toInt()}%)...",
                            color = colors.primary,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::startOtaUpdate,
                    enabled = otaProgress == null,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("تحديث الآن", color = Color.White)
                }
            },
            dismissButton = {
                if (!update.forceUpdate && otaProgress == null) {
                    TextButton(onClick = viewModel::dismissUpdateDialog) {
                        Text("لاحقاً", color = colors.onSurfaceVariant)
                    }
                }
            }
        )
    }

    selectedChatForVaultDialog?.let { targetChat ->
        AlertDialog(
            onDismissRequest = { selectedChatForVaultDialog = null },
            containerColor = colors.surface,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("إخفاء المحادثة في الخزنة السرية", color = colors.onSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "هل تريد إخفاء محادثة \"${targetChat.displayTitle}\" من القائمة الرئيسية؟ يمكنك فتحها لاحقاً بكتابة الـ PIN السري في شريط البحث.",
                    color = colors.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleChatVaultState(targetChat.id, true)
                        selectedChatForVaultDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إخفاء الآن", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedChatForVaultDialog = null }) {
                    Text("إلغاء", color = colors.onSurfaceVariant)
                }
            }
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { groupName ->
                viewModel.createNewGroupChat(groupName, emptyList())
                showCreateGroupDialog = false
            }
        )
    }
}

@Composable
private fun HeaderActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.size(40.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun UserSearchResultCard(
    profile: ProfileDto,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.onSurface.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(colors.primary, colors.secondary))),
                contentAlignment = Alignment.Center
            ) {
                if (!profile.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = profile.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = profile.displayName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "@${profile.username}",
                        color = colors.primary,
                        fontSize = 12.5.sp
                    )
                    if (!profile.phoneNumber.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.Phone,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = profile.phoneNumber,
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "بدء محادثة",
                tint = colors.primary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRowItem(
    chat: ChatEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.onSurface.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .then(
                        if (chat.peerOnline) {
                            Modifier
                                .border(2.dp, OnlineGreen, CircleShape)
                                .padding(3.dp)
                        } else {
                            Modifier
                        }
                    )
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(colors.primary, colors.secondary))),
                contentAlignment = Alignment.Center
            ) {
                if (!chat.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = chat.avatarUrl,
                        contentDescription = chat.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = chat.displayTitle.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(13.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.displayTitle,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (chat.type == "group") "مجموعة" else "خاص",
                        color = colors.primary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.primary.copy(alpha = 0.11f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = chat.lastMessagePreview ?: "لا توجد رسائل بعد",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(22.dp),
        title = { Text("إنشاء مجموعة جديدة", color = colors.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("اسم المجموعة") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (groupName.isNotBlank()) onCreate(groupName) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text("إنشاء", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = colors.onSurfaceVariant)
            }
        }
    )
}
