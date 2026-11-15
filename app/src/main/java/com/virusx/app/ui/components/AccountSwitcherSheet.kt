package com.virusx.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccountSwitchSupabaseEntryPoint {
    fun auth(): Auth
    fun postgrest(): Postgrest
}

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
        val current = getAccounts(context).filterNot {
            it.email.equals(slot.email, ignoreCase = true) ||
                it.username.equals(slot.username, ignoreCase = true)
        }.toMutableList()
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

private suspend fun resolveLoginEmails(postgrest: Postgrest, rawIdentifier: String): List<String> {
    val clean = rawIdentifier.trim().removePrefix("@").lowercase()
    val candidates = LinkedHashSet<String>()
    if (clean.contains("@")) {
        candidates.add(clean)
    } else {
        // 1. Check if profiles table has an email column or username match
        runCatching {
            val rows = postgrest.from("profiles")
                .select { filter { eq("username", clean) } }
                .decodeList<JsonObject>()
            rows.firstOrNull()?.get("email")?.jsonPrimitive?.content?.takeIf { it.contains("@") }?.let {
                candidates.add(it)
            }
        }
        // 2. Standard VirusX synthetic email domains used when registering by username
        candidates.add("$clean@virusx.app")
        candidates.add("$clean@virusx.local")
        candidates.add("$clean@gmail.com")
    }
    return candidates.toList()
}

@Composable
fun AccountSwitcherCard(
    currentEmail: String? = null,
    currentDisplayName: String? = null,
    currentUsername: String? = null,
    onSwitchAccount: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, AccountSwitchSupabaseEntryPoint::class.java)
    }

    var showDialog by remember { mutableStateOf(false) }
    var showAddForm by remember { mutableStateOf(false) }
    var isSwitching by remember { mutableStateOf(false) }
    var switchError by remember { mutableStateOf<String?>(null) }
    var accounts by remember { mutableStateOf(AccountSwitcherStore.getAccounts(context)) }

    val activeSessionEmail = remember(showDialog) {
        runCatching { entryPoint.auth().currentUserOrNull()?.email }.getOrNull() ?: currentEmail
    }

    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newLabel by remember { mutableStateOf("") }

    fun performRealAccountSwitch(slot: SavedAccountSlot) {
        if (isSwitching) return
        isSwitching = true
        switchError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val auth = entryPoint.auth()
                    val postgrest = entryPoint.postgrest()
                    val emailsToTry = resolveLoginEmails(postgrest, slot.email)

                    // Mark previous user offline before switching
                    runCatching {
                        postgrest.rpc("ping_user_presence", kotlinx.serialization.json.buildJsonObject {
                            put("_is_online", kotlinx.serialization.json.JsonPrimitive(false))
                        })
                    }
                    runCatching { auth.signOut() }

                    var lastErr: Throwable? = null
                    var loggedIn = false
                    for (candidateEmail in emailsToTry) {
                        val attempt = runCatching {
                            auth.signInWith(Email) {
                                email = candidateEmail
                                password = slot.password
                            }
                        }
                        if (attempt.isSuccess) {
                            loggedIn = true
                            break
                        } else {
                            lastErr = attempt.exceptionOrNull()
                        }
                    }
                    if (!loggedIn) {
                        throw (lastErr ?: IllegalStateException("بيانات الدخول غير صحيحة"))
                    }
                }
            }

            isSwitching = false
            result.onSuccess {
                showDialog = false
                Toast.makeText(context, "✅ تم التبديل إلى حساب (${slot.displayName}) بنجاح!", Toast.LENGTH_SHORT).show()
                onSwitchAccount(slot.email, slot.password)
                // Restart main activity cleanly so all ViewModels reload the new user's chats
                val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                if (launch != null) {
                    context.startActivity(launch)
                    (context as? Activity)?.finish()
                }
            }.onFailure { err ->
                switchError = "فشل الدخول (${err.localizedMessage?.take(60) ?: "تأكد من الإيميل/اليوزر وكلمة المرور"})"
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, VirusXSecondaryWhisper.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable {
                accounts = AccountSwitcherStore.getAccounts(context)
                switchError = null
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
                    text = "انتقل بين حساباتك فوراً بضغطة واحدة (${accounts.size} محفوظ)",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSwitching) {
                    showDialog = false
                    showAddForm = false
                }
            },
            containerColor = VirusXSurface,
            title = {
                Text("🔄 تبديل الحساب المباشر", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isSwitching) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = OnlineGreen, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("جاري تبديل الجلسة وفتح الحساب...", color = OnlineGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    switchError?.let { err ->
                        Text(
                            text = "⚠️ $err",
                            color = DangerRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (accounts.isEmpty() && !showAddForm) {
                        Text(
                            text = "أضف حساباتك هنا مرة واحدة، وبعدها اضغط على أي حساب ليتم تسجيل الدخول إليه مباشرةً دون إعادة كتابة البيانات.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    } else {
                        accounts.forEach { acc ->
                            val isActive = !activeSessionEmail.isNullOrBlank() &&
                                (acc.email.equals(activeSessionEmail, ignoreCase = true) ||
                                    activeSessionEmail.startsWith("${acc.username.lowercase()}@"))
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
                                    .clickable(enabled = !isSwitching && !isActive) {
                                        performRealAccountSwitch(acc)
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
                                    Text(
                                        text = if (isActive) "${acc.email} • (الحساب الحالي)" else "${acc.email} • اضغط للدخول الفوري",
                                        color = if (isActive) OnlineGreen else TextSecondary,
                                        fontSize = 11.sp
                                    )
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
                            label = { Text("اسم تمييز الحساب (مثال: حسابي الثاني)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newEmail,
                            onValueChange = { newEmail = it },
                            label = { Text("البريد الإلكتروني أو الـ Username") },
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (newEmail.isNotBlank() && newPassword.isNotBlank()) {
                                        val cleanId = newEmail.trim()
                                        val slot = SavedAccountSlot(
                                            email = cleanId,
                                            password = newPassword,
                                            displayName = newLabel.ifBlank { cleanId.substringBefore("@") },
                                            username = cleanId.substringBefore("@").removePrefix("@")
                                        )
                                        AccountSwitcherStore.saveAccount(context, slot)
                                        accounts = AccountSwitcherStore.getAccounts(context)
                                        newEmail = ""
                                        newPassword = ""
                                        newLabel = ""
                                        showAddForm = false
                                        performRealAccountSwitch(slot)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("حفظ ودخول الآن", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (newEmail.isNotBlank() && newPassword.isNotBlank()) {
                                        val cleanId = newEmail.trim()
                                        val slot = SavedAccountSlot(
                                            email = cleanId,
                                            password = newPassword,
                                            displayName = newLabel.ifBlank { cleanId.substringBefore("@") },
                                            username = cleanId.substringBefore("@").removePrefix("@")
                                        )
                                        AccountSwitcherStore.saveAccount(context, slot)
                                        accounts = AccountSwitcherStore.getAccounts(context)
                                        newEmail = ""
                                        newPassword = ""
                                        newLabel = ""
                                        showAddForm = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("حفظ فقط", color = TextPrimary, fontSize = 12.sp)
                            }
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
                TextButton(
                    onClick = {
                        showDialog = false
                        showAddForm = false
                    },
                    enabled = !isSwitching
                ) {
                    Text("إغلاق", color = TextSecondary)
                }
            }
        )
    }
}
