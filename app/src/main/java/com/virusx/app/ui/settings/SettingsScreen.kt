package com.virusx.app.ui.settings

import com.virusx.app.ui.theme.VirusXThemeAppearanceCard

import com.virusx.app.ui.components.AccountSwitcherCard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.virusx.app.BuildConfig
import com.virusx.app.ui.theme.DangerRed
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

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSuperAdminPanel: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadAvatar(uri)
        }
    }

    Scaffold(
        containerColor = VirusXBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "الإعدادات والخصوصية",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }

            VirusXThemeAppearanceCard()

            state.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = OnlineGreen,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VirusXSurface)
                        .padding(12.dp)
                )
            }

            if (state.profile?.role == "super_admin") {
                AccountSwitcherCard()
                Button(
                    onClick = onOpenSuperAdminPanel,
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXSecondaryWhisper),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = VirusXBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "فتح لوحة تحكم المشرف العام (Super Admin)",
                        color = VirusXBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 1. Profile, Avatar & Phone Number Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VirusXSurface)
                    .border(1.dp, VirusXGlassBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = VirusXPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("الملف الشخصي ورقم الهاتف", style = MaterialTheme.typography.titleLarge)
                }

                // Avatar Picker Box
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(SentMessageGradient)
                        .border(2.dp, VirusXSecondaryWhisper, CircleShape)
                        .clickable { avatarPickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (!state.profile?.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = state.profile?.avatarUrl,
                            contentDescription = "الصورة الشخصية",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "تغيير الصورة",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Text(
                    text = "اضغط على الدائرة لتغيير صورتك الشخصية",
                    color = VirusXSecondaryWhisper,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = viewModel::onDisplayNameChange,
                    label = { Text("الاسم المعروض") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.phoneNumber,
                    onValueChange = viewModel::onPhoneNumberChange,
                    label = { Text("رقم الهاتف") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.moodStatus,
                    onValueChange = viewModel::onMoodStatusChange,
                    label = { Text("الحالة المزاجية (مثال: 🎧 بيسمع مزيكا)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = viewModel::saveProfileInfo,
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حفظ تعديلات الملف الشخصي")
                }
            }

            // 2. Smart Lecture DND Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VirusXSurface)
                    .border(1.dp, VirusXGlassBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = VirusXSecondaryWhisper)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("وضع المحاضرة الذكي (Smart DND)", style = MaterialTheme.typography.titleLarge)
                    }
                    Switch(
                        checked = state.dndEnabled,
                        onCheckedChange = viewModel::onDndEnabledChange
                    )
                }

                Text(
                    text = "عند التفعيل يتم كتم الإشعارات وإرسال رد تلقائي، مع السماح بمرور التنبيه الصوتي فوراً إذا احتوت الرسالة على كلمة الطوارئ.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = state.dndAutoReply,
                    onValueChange = viewModel::onDndAutoReplyChange,
                    label = { Text("رسالة الرد التلقائي") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.dndEmergencyKeyword,
                    onValueChange = viewModel::onDndEmergencyKeywordChange,
                    label = { Text("كلمة الطوارئ لتخطي الكتم (تُحفظ بشكل مشفر وخاص)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = viewModel::saveSmartDndSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حفظ إعدادات وضع المحاضرة")
                }
            }

            // 3. Stealth Search Vault PIN Setup Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VirusXSurface)
                    .border(1.dp, VirusXGlassBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = VirusXSecondaryWhisper)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("خزنة المحادثات السرية (Stealth Search Vault)", style = MaterialTheme.typography.titleLarge)
                }

                Text(
                    text = "لا توجد أي أيقونة للخزنة في التطبيق. بعد تعيين الكود السري هنا، اكتب الكود مباشرة في شريط البحث الرئيسي لفتح محادثاتك المخفية فوراً.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Button(
                    onClick = viewModel::openVaultPinDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXSecondaryWhisper),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تعيين / تغيير كود الخزنة السري (PIN)", color = VirusXBackground, fontWeight = FontWeight.Bold)
                }
            }

            // 4. Sign Out Button
            Button(
                onClick = { viewModel.signOut(onSignedOut) },
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تسجيل الخروج")
            }

            // 5. Version Number (5 consecutive taps trigger redeem_admin_code dialog)
            BoxVersionTrigger(
                versionName = BuildConfig.VERSION_NAME,
                role = state.profile?.role ?: "user",
                onTap = viewModel::onVersionNumberTapped
            )
        }
    }

    if (state.showVaultPinDialog) {
        VaultPinSetupDialog(
            onDismiss = viewModel::closeVaultPinDialog,
            onSave = viewModel::saveVaultPin
        )
    }

    if (state.showAdminCodeDialog) {
        SecretAdminCodeDialog(
            onDismiss = viewModel::dismissAdminCodeDialog,
            onSubmit = viewModel::submitSecretAdminCode
        )
    }
}

@Composable
private fun BoxVersionTrigger(
    versionName: String,
    role: String,
    onTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onTap)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "VirusX إصدار $versionName",
            color = TextMuted,
            fontSize = 13.sp
        )
        Text(
            text = "الصلاحية الحالية: $role",
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun VaultPinSetupDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VirusXSurface,
        title = { Text("تعيين كود الخزنة السرية (PIN)", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("أدخل كود من 4 خانات أو أكثر") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(pin) },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary)
            ) {
                Text("حفظ الكود")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun SecretAdminCodeDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var secretCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VirusXSurface,
        title = { Text("ترقية المشرف العام السرية", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "أدخل كود الإشراف السري لتفعيل صلاحيات Super Admin عبر دالة redeem_admin_code:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = secretCode,
                    onValueChange = { secretCode = it },
                    label = { Text("Secret Admin Code") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(secretCode) },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXSecondaryWhisper)
            ) {
                Text("تفعيل الصلاحية", color = VirusXBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}
