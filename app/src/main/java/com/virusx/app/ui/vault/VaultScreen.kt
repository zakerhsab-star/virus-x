package com.virusx.app.ui.vault

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.virusx.app.ui.home.HomeViewModel
import com.virusx.app.ui.theme.TextMuted
import com.virusx.app.ui.theme.TextPrimary
import com.virusx.app.ui.theme.TextSecondary
import com.virusx.app.ui.theme.VirusXBackground
import com.virusx.app.ui.theme.VirusXGlassBorder
import com.virusx.app.ui.theme.VirusXSecondaryWhisper
import com.virusx.app.ui.theme.VirusXSurface
import com.virusx.app.ui.theme.WhisperMessageGradient

@Composable
fun VaultScreen(
    homeViewModel: HomeViewModel,
    onOpenChat: (String) -> Unit,
    onBack: () -> Unit
) {
    val vaultChats by homeViewModel.vaultChats.collectAsState()

    Scaffold(
        containerColor = VirusXBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "إغلاق الخزنة",
                        tint = TextPrimary
                    )
                }
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = VirusXSecondaryWhisper,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "خزنة المحادثات السرية (Stealth Vault)",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "هذه المحادثات مخفية تماماً من الشاشة الرئيسية والبحث العادي",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (vaultChats.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد محادثات مخفية حالياً.\nاضغط مطولاً على أي محادثة في الشاشة الرئيسية لإخفائها هنا.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(vaultChats, key = { it.id }) { chat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(VirusXSurface)
                                .border(1.dp, VirusXGlassBorder, RoundedCornerShape(18.dp))
                                .clickable { onOpenChat(chat.id) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(WhisperMessageGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chat.displayTitle.take(1).uppercase(),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chat.displayTitle,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = chat.lastMessagePreview ?: "محادثة مشفرة",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }

                            IconButton(
                                onClick = { homeViewModel.toggleChatVaultState(chat.id, false) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "إظهار في القائمة الرئيسية",
                                    tint = VirusXSecondaryWhisper
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
