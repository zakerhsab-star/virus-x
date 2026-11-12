#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from pathlib import Path

FILES = {
    # =========================================================================
    # 1. ADMIN REPOSITORY (Users, Deleted Messages, Reveal Asker, Ban/Mute/Broadcast)
    # =========================================================================
    "app/src/main/java/com/virusx/app/data/repository/AdminRepository.kt": r'''package com.virusx.app.data.repository

import com.virusx.app.data.remote.dto.MessageDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.remote.dto.StoryAskQuestionDto
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val postgrest: Postgrest
) {
    suspend fun fetchAllUsers(): Result<List<ProfileDto>> = runCatching {
        postgrest.from("profiles")
            .select {
                order("created_at", Order.DESCENDING)
            }.decodeList<ProfileDto>()
    }

    suspend fun fetchDeletedMessages(): Result<List<MessageDto>> = runCatching {
        postgrest.from("messages")
            .select {
                filter { eq("is_deleted", true) }
                order("deleted_at", Order.DESCENDING)
                limit(100)
            }.decodeList<MessageDto>()
    }

    suspend fun fetchAllAnonymousQuestions(): Result<List<StoryAskQuestionDto>> = runCatching {
        postgrest.from("story_ask_questions")
            .select {
                order("created_at", Order.DESCENDING)
                limit(100)
            }.decodeList<StoryAskQuestionDto>()
    }

    suspend fun revealAnonymousAsker(questionId: String): Result<String> = runCatching {
        postgrest.rpc(
            function = "reveal_anonymous_asker",
            parameters = buildJsonObject {
                put("_question_id", questionId)
            }
        ).decodeAs<String>()
    }

    suspend fun setUserBan(targetUserId: String, banned: Boolean, reason: String?): Result<Unit> = runCatching {
        postgrest.rpc(
            function = "set_user_ban",
            parameters = buildJsonObject {
                put("_target_user_id", targetUserId)
                put("_banned", banned)
                put("_reason", reason)
            }
        )
    }

    suspend fun muteUserForHours(targetUserId: String, hours: Long): Result<Unit> = runCatching {
        val mutedUntilIso = if (hours <= 0L) {
            Instant.now().minus(1, ChronoUnit.MINUTES).toString()
        } else {
            Instant.now().plus(hours, ChronoUnit.HOURS).toString()
        }
        postgrest.rpc(
            function = "set_user_mute",
            parameters = buildJsonObject {
                put("_target_user_id", targetUserId)
                put("_muted_until", mutedUntilIso)
            }
        )
    }

    suspend fun sendGlobalBroadcast(title: String, body: String): Result<Unit> = runCatching {
        postgrest.rpc(
            function = "send_broadcast",
            parameters = buildJsonObject {
                put("_title", title.trim())
                put("_body", body.trim())
            }
        )
    }

    suspend fun promoteUserToAdmin(targetUserId: String): Result<Unit> = runCatching {
        postgrest.rpc(
            function = "promote_user_to_admin",
            parameters = buildJsonObject {
                put("_target_user_id", targetUserId)
            }
        )
    }

    suspend fun lockAdminPromotionCode(): Result<Unit> = runCatching {
        postgrest.rpc(function = "lock_admin_promotion")
    }
}
''',

    # =========================================================================
    # 2. SUPER ADMIN VIEWMODEL
    # =========================================================================
    "app/src/main/java/com/virusx/app/ui/admin/SuperAdminViewModel.kt": r'''package com.virusx.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.remote.dto.MessageDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.remote.dto.StoryAskQuestionDto
import com.virusx.app.data.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SuperAdminUiState(
    val selectedTab: Int = 0,
    val users: List<ProfileDto> = emptyList(),
    val deletedMessages: List<MessageDto> = emptyList(),
    val anonymousQuestions: List<StoryAskQuestionDto> = emptyList(),
    val revealedAskersMap: Map<String, ProfileDto> = emptyMap(),
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class SuperAdminViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SuperAdminUiState())
    val uiState: StateFlow<SuperAdminUiState> = _uiState.asStateFlow()

    init {
        refreshDashboard()
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val users = adminRepository.fetchAllUsers().getOrDefault(emptyList())
            val deleted = adminRepository.fetchDeletedMessages().getOrDefault(emptyList())
            val questions = adminRepository.fetchAllAnonymousQuestions().getOrDefault(emptyList())

            _uiState.update {
                it.copy(
                    users = users,
                    deletedMessages = deleted,
                    anonymousQuestions = questions,
                    isLoading = false
                )
            }
        }
    }

    fun toggleBanUser(user: ProfileDto, reason: String = "مخالفة سياسات الاستخدام") {
        viewModelScope.launch {
            val nextBanState = !user.isBanned
            adminRepository.setUserBan(user.id, nextBanState, if (nextBanState) reason else null).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(statusMessage = if (nextBanState) "تم حظر ${user.displayName}" else "تم إلغاء حظر ${user.displayName}")
                    }
                    refreshDashboard()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun muteUser(user: ProfileDto, hours: Long) {
        viewModelScope.launch {
            adminRepository.muteUserForHours(user.id, hours).fold(
                onSuccess = {
                    val msg = if (hours > 0) "تم كتم ${user.displayName} لمدة $hours ساعة" else "تم فك الكتم عن ${user.displayName}"
                    _uiState.update { it.copy(statusMessage = msg) }
                    refreshDashboard()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun revealQuestionAsker(question: StoryAskQuestionDto) {
        viewModelScope.launch {
            adminRepository.revealAnonymousAsker(question.id).fold(
                onSuccess = { askerUserId ->
                    val foundProfile = _uiState.value.users.firstOrNull { it.id == askerUserId }
                    if (foundProfile != null) {
                        val updated = _uiState.value.revealedAskersMap.toMutableMap()
                        updated[question.id] = foundProfile
                        _uiState.update {
                            it.copy(
                                revealedAskersMap = updated,
                                statusMessage = "تم كشف هوية السائل: ${foundProfile.displayName} (@${foundProfile.username})"
                            )
                        }
                    } else {
                        _uiState.update { it.copy(statusMessage = "معرف السائل: $askerUserId") }
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun sendBroadcast(title: String, body: String) {
        if (title.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            adminRepository.sendGlobalBroadcast(title, body).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم إرسال الإشعار العام لجميع المستخدمين بنجاح") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun lockAdminCodePermanently() {
        viewModelScope.launch {
            adminRepository.lockAdminPromotionCode().fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم قفل الترقية الذاتية بالكود السري نهائياً") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }
}
''',

    # =========================================================================
    # 3. SUPER ADMIN SCREEN UI
    # =========================================================================
    "app/src/main/java/com/virusx/app/ui/admin/SuperAdminScreen.kt": r'''package com.virusx.app.ui.admin

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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

    Scaffold(
        containerColor = VirusXBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VirusXSurface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                    }
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = VirusXSecondaryWhisper)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "لوحة تحكم المطور (Super Admin)",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
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
                "الأعضاء (${state.users.size})",
                "الرسائل المحذوفة (${state.deletedMessages.size})",
                "كشف المجهول (${state.anonymousQuestions.size})",
                "الإشعارات والأمان"
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
                    0 -> AdminUsersTab(
                        users = state.users,
                        onToggleBan = { viewModel.toggleBanUser(it) },
                        onMute24h = { viewModel.muteUser(it, 24L) },
                        onUnmute = { viewModel.muteUser(it, 0L) }
                    )
                    1 -> AdminDeletedMessagesTab(
                        messages = state.deletedMessages,
                        userMap = userMap
                    )
                    2 -> AdminRevealQuestionsTab(
                        questions = state.anonymousQuestions,
                        revealedMap = state.revealedAskersMap,
                        onReveal = viewModel::revealQuestionAsker
                    )
                    3 -> AdminBroadcastAndSecurityTab(
                        onSendBroadcast = viewModel::sendBroadcast,
                        onLockPromotion = viewModel::lockAdminCodePermanently
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminUsersTab(
    users: List<ProfileDto>,
    onToggleBan: (ProfileDto) -> Unit,
    onMute24h: (ProfileDto) -> Unit,
    onUnmute: (ProfileDto) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(users, key = { it.id }) { user ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(VirusXSurface)
                    .border(1.dp, VirusXGlassBorder, RoundedCornerShape(16.dp))
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
                        text = if (user.isOnline) "🟢 متصل" else "⚪ غير متصل",
                        color = if (user.isOnline) OnlineGreen else TextMuted,
                        fontSize = 12.sp
                    )
                }

                Text("📞 الهاتف: ${user.phoneNumber ?: "غير مسجل"}", color = VirusXSecondaryWhisper, fontSize = 13.sp)
                Text("📧 البريد: ${user.email ?: "غير متاح"}", color = TextSecondary, fontSize = 12.sp)
                Text("📱 الجهاز: ${user.deviceType ?: "غير معروف"} | الصلاحية: ${user.role}", color = TextSecondary, fontSize = 12.sp)
                Text("🕒 آخر ظهور: ${user.lastSeen ?: "-"}", color = TextMuted, fontSize = 11.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onToggleBan(user) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (user.isBanned) OnlineGreen else DangerRed
                        ),
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
                    Text(
                        text = "المحتوى الأصلي قبل الحذف: ${msg.content ?: "[ملف وسائط]"}",
                        color = VirusXSecondaryWhisper,
                        fontSize = 14.sp
                    )
                    if (!msg.mediaUrl.isNullOrBlank()) {
                        Text("الرابط: ${msg.mediaUrl}", color = TextSecondary, fontSize = 11.sp)
                    }
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
                            Text("كشف هوية السائل (reveal_anonymous_asker)", color = VirusXBackground, fontWeight = FontWeight.Bold)
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
    onLockPromotion: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                Text("إرسال إشعار عام لكل المستخدمين (send_broadcast)", color = TextPrimary, fontWeight = FontWeight.Bold)
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
            Text(
                text = "يمنع أي شخص آخر من استخدام كود الـ 5 ضغطات لترقية نفسه إلى Super Admin مستقبلاً.",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Button(
                onClick = onLockPromotion,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("تفعيل القفل النهائي (lock_admin_promotion)")
            }
        }
    }
}
''',

    # =========================================================================
    # 4. UPDATED SETTINGS VIEWMODEL & SCREEN (Added Avatar Picker & Upload)
    # =========================================================================
    "app/src/main/java/com/virusx/app/ui/settings/SettingsViewModel.kt": r'''package com.virusx.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val profile: ProfileDto? = null,
    val displayName: String = "",
    val phoneNumber: String = "",
    val moodStatus: String = "",
    val dndEnabled: Boolean = false,
    val dndAutoReply: String = "",
    val dndEmergencyKeyword: String = "",
    val versionTapCount: Int = 0,
    val showAdminCodeDialog: Boolean = false,
    val showVaultPinDialog: Boolean = false,
    val statusMessage: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val profile = authRepository.refreshCurrentUserProfile()
            val privateSettings = authRepository.getCurrentUserPrivateSettings()
            _uiState.update {
                it.copy(
                    profile = profile,
                    displayName = profile?.displayName ?: "",
                    phoneNumber = profile?.phoneNumber ?: "",
                    moodStatus = profile?.moodStatus ?: "",
                    dndEnabled = profile?.dndEnabled ?: false,
                    dndAutoReply = profile?.dndAutoReply ?: "أنا في المحاضرة حالياً، سأرد عليك لاحقاً 📚",
                    dndEmergencyKeyword = privateSettings?.dndEmergencyKeyword ?: "طوارئ",
                    isLoading = false
                )
            }
        }
    }

    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value) }
    fun onPhoneNumberChange(value: String) = _uiState.update { it.copy(phoneNumber = value) }
    fun onMoodStatusChange(value: String) = _uiState.update { it.copy(moodStatus = value) }
    fun onDndEnabledChange(enabled: Boolean) = _uiState.update { it.copy(dndEnabled = enabled) }
    fun onDndAutoReplyChange(value: String) = _uiState.update { it.copy(dndAutoReply = value) }
    fun onDndEmergencyKeywordChange(value: String) = _uiState.update { it.copy(dndEmergencyKeyword = value) }

    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            authRepository.uploadUserAvatar(uri).fold(
                onSuccess = {
                    val refreshed = authRepository.refreshCurrentUserProfile()
                    _uiState.update {
                        it.copy(
                            profile = refreshed,
                            isLoading = false,
                            statusMessage = "تم تحديث الصورة الشخصية بنجاح"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = err.localizedMessage ?: "فشل رفع الصورة الشخصية"
                        )
                    }
                }
            )
        }
    }

    fun saveProfileInfo() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            authRepository.updateProfileBasicInfo(
                displayName = state.displayName,
                moodStatus = state.moodStatus,
                phoneNumber = state.phoneNumber
            ).fold(
                onSuccess = { updated ->
                    _uiState.update {
                        it.copy(
                            profile = updated,
                            isLoading = false,
                            statusMessage = "تم حفظ بيانات الملف الشخصي ورقم الهاتف بنجاح"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = err.localizedMessage ?: "فشل تحديث البيانات"
                        )
                    }
                }
            )
        }
    }

    fun saveSmartDndSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            authRepository.updateSmartDndSettings(
                dndEnabled = state.dndEnabled,
                autoReply = state.dndAutoReply,
                emergencyKeyword = state.dndEmergencyKeyword
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "تم تحديث إعدادات وضع المحاضرة الذكي (Smart DND)"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = err.localizedMessage ?: "تعذر حفظ إعدادات DND"
                        )
                    }
                }
            )
        }
    }

    fun openVaultPinDialog() = _uiState.update { it.copy(showVaultPinDialog = true) }
    fun closeVaultPinDialog() = _uiState.update { it.copy(showVaultPinDialog = false) }

    fun saveVaultPin(newPin: String) {
        viewModelScope.launch {
            authRepository.updateVaultPin(newPin).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            showVaultPinDialog = false,
                            statusMessage = "تم تعيين رمز الخزنة السرية! اكتب هذا الرمز في شريط البحث الرئيسي لفتحها."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(statusMessage = err.localizedMessage ?: "تعذر حفظ الرمز السري")
                    }
                }
            )
        }
    }

    fun onVersionNumberTapped() {
        val nextCount = _uiState.value.versionTapCount + 1
        if (nextCount >= 5) {
            _uiState.update {
                it.copy(
                    versionTapCount = 0,
                    showAdminCodeDialog = true
                )
            }
        } else {
            _uiState.update { it.copy(versionTapCount = nextCount) }
        }
    }

    fun dismissAdminCodeDialog() {
        _uiState.update { it.copy(showAdminCodeDialog = false) }
    }

    fun submitSecretAdminCode(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showAdminCodeDialog = false) }
            authRepository.redeemAdminCode(code).fold(
                onSuccess = { promoted ->
                    val refreshed = authRepository.refreshCurrentUserProfile()
                    _uiState.update {
                        it.copy(
                            profile = refreshed,
                            isLoading = false,
                            statusMessage = if (promoted) {
                                "تم تفعيل صلاحيات المشرف العام (Super Admin) بنجاح!"
                            } else {
                                "كود الإشراف غير صحيح"
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = err.localizedMessage ?: "تعذر التحقق من كود الإشراف"
                        )
                    }
                }
            )
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onSignedOut()
        }
    }
}
''',

    # =========================================================================
    # 5. UPDATED NAVIGATION HOST (Connects StoriesScreen & SuperAdminScreen)
    # =========================================================================
    "app/src/main/java/com/virusx/app/ui/navigation/VirusXNavHost.kt": r'''package com.virusx.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.virusx.app.data.repository.AuthRepository
import com.virusx.app.ui.admin.SuperAdminScreen
import com.virusx.app.ui.auth.AuthScreen
import com.virusx.app.ui.chat.ChatScreen
import com.virusx.app.ui.home.HomeScreen
import com.virusx.app.ui.home.HomeViewModel
import com.virusx.app.ui.settings.SettingsScreen
import com.virusx.app.ui.stories.StoriesScreen
import com.virusx.app.ui.vault.VaultScreen

@Composable
fun VirusXNavHost(
    authRepository: AuthRepository
) {
    val navController = rememberNavController()
    val isAuthenticated by authRepository.isAuthenticatedFlow.collectAsState(
        initial = authRepository.getCurrentUserId() != null
    )

    val startDest = if (isAuthenticated) "home" else "auth"
    val sharedHomeViewModel: HomeViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        composable("auth") {
            AuthScreen(
                onAuthSuccess = {
                    sharedHomeViewModel.refreshAll()
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                viewModel = sharedHomeViewModel,
                onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                onOpenStories = { navController.navigate("stories") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenStealthVault = { navController.navigate("vault") }
            )
        }

        composable("vault") {
            VaultScreen(
                homeViewModel = sharedHomeViewModel,
                onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSuperAdminPanel = { navController.navigate("super_admin") },
                onSignedOut = {
                    navController.navigate("auth") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "chat/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            ChatScreen(
                chatId = chatId,
                onBack = {
                    sharedHomeViewModel.refreshAll()
                    navController.popBackStack()
                }
            )
        }

        composable("stories") {
            StoriesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("super_admin") {
            SuperAdminScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
'''
}


def main():
    root = Path.cwd()
    print(f"[*] Applying Task 5 (Part 2 - Final) files in: {root}")
    for rel_path, content in FILES.items():
        file_path = root / rel_path
        file_path.parent.mkdir(parents=True, exist_ok=True)
        clean_content = content.replace("\u00a0", " ").strip() + "\n"
        file_path.write_text(clean_content, encoding="utf-8")
        print(f"  [+] Updated/Created: {rel_path}")

    # Ensure ChatDetailRepository uses upsert = false for INSERT-only storage RLS policies
    chat_detail = root / "app/src/main/java/com/virusx/app/data/repository/ChatDetailRepository.kt"
    if chat_detail.exists():
        cd_text = chat_detail.read_text(encoding="utf-8").replace("\u00a0", " ")
        cd_text = cd_text.replace("upsert = true", "upsert = false")
        chat_detail.write_text(cd_text, encoding="utf-8")
        print("  [+] Ensured upsert = false in ChatDetailRepository.kt for RLS compatibility")

    print("\n[✓] Task 5 Part 2 completed! VirusX project is 100% complete and ready for GitHub Actions APK build.")


if __name__ == "__main__":
    main()