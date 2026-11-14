package com.virusx.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.virusx.app.data.remote.dto.MessageDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.remote.dto.StoryAskQuestionDto
import com.virusx.app.data.repository.AdminChatOverviewDto
import com.virusx.app.ui.theme.DangerRed
import com.virusx.app.ui.theme.OnlineGreen
import com.virusx.app.ui.theme.TextMuted
import com.virusx.app.ui.theme.TextPrimary
import com.virusx.app.ui.theme.TextSecondary
import com.virusx.app.ui.theme.VirusXBackground
import com.virusx.app.ui.theme.VirusXGlassBorder
import com.virusx.app.ui.theme.VirusXPrimary
import com.virusx.app.ui.theme.VirusXSecondaryWhisper
import com.virusx.app.ui.theme.VirusXSurface
import com.virusx.app.ui.theme.VirusXSurfaceElevated
import com.virusx.app.ui.theme.WarningAmber

@Composable
fun SuperAdminScreen(
    onBack: () -> Unit,
    viewModel: SuperAdminViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val userMap = remember(state.users) { state.users.associateBy { it.id } }
    val onlineCount = remember(state.users) { state.users.count { it.isOnline } }

    Scaffold(
        containerColor = VirusXBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VirusXSurface)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                    }
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = VirusXSecondaryWhisper)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "لوحة تحكم المطور (Super Admin)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                }

                IconButton(onClick = viewModel::refreshDashboard) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = VirusXSecondaryWhisper)
                }
            }

            state.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = OnlineGreen,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VirusXSurfaceElevated)
                        .clickable { viewModel.clearStatusMessage() }
                        .padding(12.dp)
                )
            }

            val tabs = listOf(
                "رادار المتصلين ($onlineCount/${state.users.size})",
                "المراقب المباشر (${state.allChats.size})",
                "الرسائل المحذوفة (${state.deletedMessages.size})",
                "كشف المجهول (${state.anonymousQuestions.size})",
                "التحديثات والأمان"
            )

            ScrollableTabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = VirusXSurface,
                contentColor = VirusXSecondaryWhisper,
                edgePadding = 12.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = {
                            Text(
                                text = title,
                                color = if (state.selectedTab == index) VirusXSecondaryWhisper else TextSecondary,
                                fontWeight = if (state.selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VirusXPrimary)
                }
            } else {
                when (state.selectedTab) {
                    0 -> AdminLiveRadarUsersTab(
                        users = state.users,
                        onlineCount = onlineCount,
                        onToggleBan = { viewModel.toggleBanUser(it) },
                        onMute24h = { viewModel.muteUser(it, 24L) },
                        onUnmute = { viewModel.muteUser(it, 0L) }
                    )
                    1 -> AdminLiveChatInspectorTab(
                        chats = state.allChats,
                        userMap = userMap,
                        onInspect = viewModel::inspectChat
                    )
                    2 -> AdminDeletedMessagesTab(
                        messages = state.deletedMessages,
                        userMap = userMap
                    )
                    3 -> AdminRevealQuestionsTab(
                        questions = state.anonymousQuestions,
                        revealedMap = state.revealedAskersMap,
                        onReveal = viewModel::revealQuestionAsker
                    )
                    4 -> AdminBroadcastAndSecurityTab(
                        onSendBroadcast = viewModel::sendBroadcast,
                        onPublishOta = viewModel::publishOtaUpdate,
                        onLockPromotion = viewModel::lockAdminCodePermanently
                    )
                }
            }
        }
    }

    state.inspectedChat?.let { targetChat ->
        AlertDialog(
            onDismissRequest = viewModel::closeChatInspector,
            containerColor = VirusXSurface,
            title = {
                Text(
                    text = "👁️ مراقبة: ${targetChat.name ?: if (targetChat.type == "group") "مجموعة" else "محادثة خاصة"}",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (state.inspectedMessages.isEmpty()) {
                    Text("لا توجد رسائل داخل هذه المحادثة بعد.", color = TextMuted)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.inspectedMessages, key = { it.id }) { m ->
                            val sender = userMap[m.senderId]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VirusXSurfaceElevated)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "${sender?.displayName ?: m.senderId} (@${sender?.username ?: "-"})",
                                    color = VirusXSecondaryWhisper,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = m.content ?: "[مرفق وسائط]",
                                    color = if (m.isDeleted) DangerRed else TextPrimary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = m.createdAt ?: "",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::closeChatInspector) {
                    Text("إغلاق", color = VirusXSecondaryWhisper)
                }
            }
        )
    }
}

@Composable
private fun AdminLiveRadarUsersTab(
    users: List<ProfileDto>,
    onlineCount: Int,
    onToggleBan: (ProfileDto) -> Unit,
    onMute24h: (ProfileDto) -> Unit,
    onUnmute: (ProfileDto) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OnlineGreen.copy(alpha = 0.14f))
                    .border(1.dp, OnlineGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Public, contentDescription = null, tint = OnlineGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "رادار النشاط المباشر: $onlineCount متصل الآن من إجمالى ${users.size} عضو",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        items(users, key = { it.id }) { user ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(VirusXSurface)
                    .border(
                        width = 1.dp,
                        color = if (user.isOnline) OnlineGreen.copy(alpha = 0.5f) else VirusXGlassBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${user.displayName} (@${user.username})",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (user.isOnline) "🟢 متصل الآن" else "⚪ غير متصل",
                        color = if (user.isOnline) OnlineGreen else TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Text("📞 الهاتف: ${user.phoneNumber ?: "غير مسجل"}", color = VirusXSecondaryWhisper, fontSize = 13.sp)
                Text("📧 البريد: ${user.email ?: "غير متاح"}", color = TextSecondary, fontSize = 12.sp)
                Text("📱 نوع الجهاز: ${user.deviceType ?: "غير معروف"} | الصلاحية: ${user.role}", color = TextSecondary, fontSize = 12.sp)
                Text("🕒 آخر نشاط: ${user.lastSeen ?: "الآن"}", color = TextMuted, fontSize = 11.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onToggleBan(user) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (user.isBanned) OnlineGreen else DangerRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (user.isBanned) "فك الحظر" else "حظر", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (user.mutedUntil.isNullOrBlank()) onMute24h(user) else onUnmute(user)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (user.mutedUntil.isNullOrBlank()) "كتم 24س" else "فك الكتم", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminLiveChatInspectorTab(
    chats: List<AdminChatOverviewDto>,
    userMap: Map<String, ProfileDto>,
    onInspect: (AdminChatOverviewDto) -> Unit
) {
    if (chats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد محادثات أو مجموعات مسجلة حالياً.", color = TextMuted)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chats, key = { it.id }) { chat ->
                val creator = chat.createdBy?.let { userMap[it] }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VirusXSurface)
                        .border(1.dp, VirusXGlassBorder, RoundedCornerShape(16.dp))
                        .clickable { onInspect(chat) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (chat.type == "group") "👥 مجموعة: ${chat.name ?: "بدون اسم"}" else "💬 محادثة خاصة",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "المنشئ: ${creator?.displayName ?: "عضو"} | عدد الأعضاء: ${chat.memberCount}",
                            color = VirusXSecondaryWhisper,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "تاريخ الإنشاء: ${chat.createdAt ?: "-"}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Icon(Icons.Default.Visibility, contentDescription = "فحص المحادثة", tint = VirusXSecondaryWhisper)
                }
            }
        }
    }
}

@Composable
private fun AdminDeletedMessagesTab(
    messages: List<MessageDto>,
    userMap: Map<String, ProfileDto>
) {
    if (messages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد رسائل محذوفة مسجلة حالياً.", color = TextMuted)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val sender = userMap[msg.senderId]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VirusXSurface)
                        .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "المرسل: ${sender?.displayName ?: msg.senderId} (@${sender?.username ?: "-"})",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Text("المحتوى: ${msg.content ?: "[ملف وسائط]"}", color = VirusXSecondaryWhisper, fontSize = 14.sp)
                    Text("تاريخ الحذف: ${msg.deletedAt ?: msg.createdAt ?: "-"}", color = TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AdminRevealQuestionsTab(
    questions: List<StoryAskQuestionDto>,
    revealedMap: Map<String, ProfileDto>,
    onReveal: (StoryAskQuestionDto) -> Unit
) {
    if (questions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد أسئلة مجهولة في الحالات حالياً.", color = TextMuted)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(questions, key = { it.id }) { q ->
                val revealedProfile = revealedMap[q.id]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VirusXSurface)
                        .border(1.dp, VirusXGlassBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("❓ السؤال: ${q.questionText}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    if (revealedProfile != null) {
                        Text(
                            text = "🕵️ الهوية المكشوفة: ${revealedProfile.displayName} (@${revealedProfile.username}) | هاتف: ${revealedProfile.phoneNumber ?: "-"}",
                            color = OnlineGreen,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    } else {
                        Button(
                            onClick = { onReveal(q) },
                            colors = ButtonDefaults.buttonColors(containerColor = VirusXSecondaryWhisper)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = VirusXBackground, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("كشف هوية السائل", color = VirusXBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminBroadcastAndSecurityTab(
    onSendBroadcast: (String, String) -> Unit,
    onPublishOta: (String, String, String, String, Boolean, Boolean) -> Unit,
    onLockPromotion: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var verCode by remember { mutableStateOf("5") }
    var verName by remember { mutableStateOf("1.0.4") }
    var apkUrl by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("تحديث جديد يتضمن إضافات وتحسينات في الأداء") }
    var forceUpdate by remember { mutableStateOf(false) }
    var maintMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(VirusXSurface)
                .border(1.dp, VirusXSecondaryWhisper.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🚀 نشر تحديث تلقائي للتطبيق (OTA Update)", color = VirusXSecondaryWhisper, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = verCode,
                    onValueChange = { verCode = it },
                    label = { Text("رقم الإصدار (Code)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = verName,
                    onValueChange = { verName = it },
                    label = { Text("اسم الإصدار") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = apkUrl,
                onValueChange = { apkUrl = it },
                label = { Text("رابط تحميل ملف APK المباشر") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات التحديث") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("تحديث إجباري (Force Update)", color = TextPrimary, fontSize = 13.sp)
                Switch(checked = forceUpdate, onCheckedChange = { forceUpdate = it })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("وضع الصيانة المؤقت (Maintenance)", color = TextPrimary, fontSize = 13.sp)
                Switch(checked = maintMode, onCheckedChange = { maintMode = it })
            }
            Button(
                onClick = { onPublishOta(verCode, verName, apkUrl, notes, forceUpdate, maintMode) },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXSecondaryWhisper),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("نشر التحديث الآن لكل الأجهزة", color = VirusXBackground, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(VirusXSurface)
                .border(1.dp, VirusXGlassBorder, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = VirusXSecondaryWhisper)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إرسال إشعار عام لكل المستخدمين", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان الإشعار العام") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("نص الإشعار") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    onSendBroadcast(title, body)
                    title = ""
                    body = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إرسال الإشعار الآن")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(VirusXSurface)
                .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = DangerRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("قفل كود الترقية السري نهائياً", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onLockPromotion,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("تفعيل القفل النهائي")
            }
        }
    }
}
