package com.virusx.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.virusx.app.ui.theme.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedAccountSlot(
    val email: String,
    val password: String,
    val displayName: String,
    val username: String
)

object AccountSwitcherStore {
    private const val PREF_NAME = "virusx_saved_accounts"
    private const val KEY_LIST = "accounts_json"

    fun getAccounts(context: Context): List<SavedAccountSlot> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LIST, null) ?: return emptyList()
        return runCatching { Json.decodeFromString<List<SavedAccountSlot>>(raw) }.getOrDefault(emptyList())
    }

    fun saveAccount(context: Context, slot: SavedAccountSlot) {
        val current = getAccounts(context).filterNot { it.email.equals(slot.email, ignoreCase = true) }.toMutableList()
        current.add(0, slot)
        val encoded = runCatching { Json.encodeToString(current) }.getOrNull() ?: return
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIST, encoded)
            .apply()
    }

    fun removeAccount(context: Context, email: String) {
        val current = getAccounts(context).filterNot { it.email.equals(email, ignoreCase = true) }
        val encoded = runCatching { Json.encodeToString(current) }.getOrNull() ?: "[]"
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIST, encoded)
            .apply()
    }
}

@Composable
fun AccountSwitcherCard(
    currentEmail: String? = null,
    currentDisplayName: String? = null,
    currentUsername: String? = null,
    onSwitchAccount: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var showAddForm by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf(AccountSwitcherStore.getAccounts(context)) }

    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newLabel by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, VirusXSecondaryWhisper.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable {
                accounts = AccountSwitcherStore.getAccounts(context)
                showDialog = true
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VirusXSurfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(VirusXPrimary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = VirusXSecondaryWhisper)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "تبديل الحساب السريع (Multi-Account)",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "انتقل بين حساباتك بضغطة واحدة (${accounts.size} محفوظ)",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                showAddForm = false
            },
            containerColor = VirusXSurface,
            title = {
                Text("🔄 تبديل الحساب السريع", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (accounts.isEmpty() && !showAddForm) {
                        Text(
                            text = "لا توجد حسابات إضافية محفوظة بعد. اضغط على (إضافة حساب للتبديل السريع) لحفظ حسابك الثاني والتبديل بينهما بضغطة واحدة.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    } else {
                        accounts.forEach { acc ->
                            val isActive = !currentEmail.isNullOrBlank() && acc.email.equals(currentEmail, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isActive) VirusXPrimary.copy(alpha = 0.22f) else VirusXSurfaceElevated)
                                    .border(
                                        1.dp,
                                        if (isActive) OnlineGreen else VirusXGlassBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        if (!isActive) {
                                            showDialog = false
                                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            clip.setPrimaryClip(android.content.ClipData.newPlainText("Password", acc.password))
                                            android.widget.Toast.makeText(context, "تم نسخ باسورد (${acc.displayName}) تلقائياً وتجهيز التبديل!", android.widget.Toast.LENGTH_LONG).show()
                                            onSwitchAccount(acc.email, acc.password)
                                        }
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(VirusXPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = acc.displayName.take(2).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(acc.displayName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(acc.email, color = TextSecondary, fontSize = 11.sp)
                                }
                                if (isActive) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "نشط", tint = OnlineGreen)
                                } else {
                                    IconButton(
                                        onClick = {
                                            AccountSwitcherStore.removeAccount(context, acc.email)
                                            accounts = AccountSwitcherStore.getAccounts(context)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = DangerRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (showAddForm) {
                        OutlinedTextField(
                            value = newLabel,
                            onValueChange = { newLabel = it },
                            label = { Text("اسم الحساب (مثال: حسابي الثاني)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newEmail,
                            onValueChange = { newEmail = it },
                            label = { Text("البريد الإلكتروني أو اسم المستخدم") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("كلمة المرور") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (newEmail.isNotBlank() && newPassword.isNotBlank()) {
                                    val slot = SavedAccountSlot(
                                        email = newEmail.trim(),
                                        password = newPassword,
                                        displayName = newLabel.ifBlank { newEmail.substringBefore("@") },
                                        username = newEmail.substringBefore("@")
                                    )
                                    AccountSwitcherStore.saveAccount(context, slot)
                                    accounts = AccountSwitcherStore.getAccounts(context)
                                    newEmail = ""
                                    newPassword = ""
                                    newLabel = ""
                                    showAddForm = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("حفظ الحساب في القائمة", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showAddForm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = VirusXSecondaryWhisper)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إضافة حساب للتبديل السريع", color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    showAddForm = false
                }) {
                    Text("إغلاق", color = TextSecondary)
                }
            }
        )
    }
}
