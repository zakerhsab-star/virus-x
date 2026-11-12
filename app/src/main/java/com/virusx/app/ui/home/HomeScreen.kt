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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.virusx.app.data.local.entity.ChatEntity
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.ui.theme.OnlineGreen
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
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var selectedChatForVaultDialog by remember { mutableStateOf<ChatEntity?>(null) }

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
        containerColor = VirusXBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateGroupDialog = true },
                containerColor = VirusXPrimary,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Default.GroupAdd, contentDescription = "إنشاء جروب جديد")
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

            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "VirusX",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = state.currentUserProfile?.let {
                            "متصل كـ ${it.displayName} (@${it.username})"
                        } ?: "مراسلة فورية مشفرة",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onOpenStories) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesomeMotion,
                            contentDescription = "الحالات التفاعلية",
                            tint = VirusXSecondaryWhisper
                        )
                    }
                    IconButton(onClick = viewModel::refreshAll) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تحديث",
                            tint = TextSecondary
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stealth Search Bar (Searches by Name, Username, Phone OR unlocks Stealth Vault on PIN match)
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "ابحث بالاسم، اليوزرنيم، أو رقم الهاتف...",
                        color = TextMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = VirusXPrimary
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "مسح",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = VirusXSurface,
                    unfocusedContainerColor = VirusXSurface,
                    focusedBorderColor = VirusXPrimary,
                    unfocusedBorderColor = VirusXGlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Global Admin Broadcast Banner
            state.latestBroadcast?.let { broadcast ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(VirusXSurfaceElevated)
                        .border(1.dp, VirusXGlassBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = VirusXSecondaryWhisper,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = broadcast.title,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = broadcast.body,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Directory Search Results (Name / Username / Phone Number)
                if (state.searchQuery.isNotBlank() && state.searchedUsers.isNotEmpty()) {
                    item {
                        Text(
                            text = "نتائج البحث عن الأعضاء (${state.searchedUsers.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = VirusXSecondaryWhisper,
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
                        text = "المحادثات النشطة (اضغط مطولاً لإخفاء المحادثة في الخزنة السرية)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if (filteredChats.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد محادثات ظاهرة حالياً.\nابحث عن صديق بالاسم أو اليوزرنيم أو رقم الهاتف لبدء الدردشة.",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(filteredChats, key = { it.id }) { chat ->
                        ChatRowItem(
                            chat = chat,
                            onClick = { onOpenChat(chat.id) },
                            onLongClick = { selectedChatForVaultDialog = chat }
                        )
                    }
                }
            }
        }
    }

    // Long-press dialog to move chat into Stealth Vault
    selectedChatForVaultDialog?.let { targetChat ->
        AlertDialog(
            onDismissRequest = { selectedChatForVaultDialog = null },
            containerColor = VirusXSurface,
            title = {
                Text("إخفاء المحادثة في الخزنة السرية", color = TextPrimary)
            },
            text = {
                Text(
                    "هل تريد إخفاء محادثة \"${targetChat.displayTitle}\" تماماً من القائمة الرئيسية؟ يمكنك فتحها لاحقاً بكتابة الـ PIN السري داخل شريط البحث.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleChatVaultState(targetChat.id, true)
                        selectedChatForVaultDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إخفاء الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedChatForVaultDialog = null }) {
                    Text("إلغاء", color = TextSecondary)
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
private fun UserSearchResultCard(
    profile: ProfileDto,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VirusXSurfaceElevated)
            .border(1.dp, VirusXGlassBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(SentMessageGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = profile.displayName.take(1).uppercase(),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.displayName,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@${profile.username}",
                    color = VirusXSecondaryWhisper,
                    fontSize = 13.sp
                )
                if (!profile.phoneNumber.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = profile.phoneNumber,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "بدء محادثة",
            tint = VirusXPrimary
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRowItem(
    chat: ChatEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(VirusXSurface)
            .border(1.dp, VirusXGlassBorder, RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(SentMessageGradient),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chat.displayTitle.take(1).uppercase(),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            if (chat.peerOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(OnlineGreen)
                        .border(2.dp, VirusXSurface, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.displayTitle,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (chat.type == "group") "مجموعة" else "خاص",
                    color = VirusXSecondaryWhisper,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = chat.lastMessagePreview ?: "لا توجد رسائل بعد",
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VirusXSurface,
        title = { Text("إنشاء مجموعة جديدة (حتى 50 عضو)", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("اسم المجموعة") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (groupName.isNotBlank()) onCreate(groupName) },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary)
            ) {
                Text("إنشاء")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}
