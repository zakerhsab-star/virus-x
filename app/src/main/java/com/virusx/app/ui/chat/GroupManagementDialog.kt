package com.virusx.app.ui.chat

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.virusx.app.ui.components.AccountSwitchSupabaseEntryPoint
import com.virusx.app.ui.theme.DangerRed
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Composable
fun GroupManagementDialog(
    chatId: String,
    currentGroupName: String,
    currentUserId: String,
    onDismiss: () -> Unit,
    onGroupRenamed: (String) -> Unit,
    onGroupRemovedOrLeft: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nameInput by remember(currentGroupName) { mutableStateOf(currentGroupName) }
    var isBusy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "إدارة الجروب",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("اسم الجروب") },
                    singleLine = true,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val trimmed = nameInput.trim()
                        if (trimmed.isBlank()) return@Button
                        isBusy = true
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                runCatching {
                                    val ep = EntryPointAccessors.fromApplication(
                                        context.applicationContext,
                                        AccountSwitchSupabaseEntryPoint::class.java
                                    )
                                    ep.postgrest().from("chats").update(
                                        buildJsonObject {
                                            put("name", JsonPrimitive(trimmed))
                                        }
                                    ) {
                                        filter { eq("id", chatId) }
                                    }
                                }
                            }
                            isBusy = false
                            res.onSuccess {
                                Toast.makeText(context, "تم تحديث اسم الجروب", Toast.LENGTH_SHORT).show()
                                onGroupRenamed(trimmed)
                                onDismiss()
                            }.onFailure { e ->
                                Toast.makeText(context, "تعذر تعديل الاسم: ${e.localizedMessage?.take(50)}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isBusy && nameInput.trim().isNotBlank() && nameInput.trim() != currentGroupName,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حفظ الاسم الجديد", fontWeight = FontWeight.SemiBold)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                OutlinedButton(
                    onClick = {
                        if (currentUserId.isBlank()) return@OutlinedButton
                        isBusy = true
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                runCatching {
                                    val ep = EntryPointAccessors.fromApplication(
                                        context.applicationContext,
                                        AccountSwitchSupabaseEntryPoint::class.java
                                    )
                                    ep.postgrest().from("chat_members").delete {
                                        filter {
                                            eq("chat_id", chatId)
                                            eq("user_id", currentUserId)
                                        }
                                    }
                                }
                            }
                            isBusy = false
                            res.onSuccess {
                                Toast.makeText(context, "تمت مغادرة الجروب", Toast.LENGTH_SHORT).show()
                                onDismiss()
                                onGroupRemovedOrLeft()
                            }.onFailure { e ->
                                Toast.makeText(context, "تعذرت المغادرة: ${e.localizedMessage?.take(50)}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مغادرة الجروب", fontWeight = FontWeight.SemiBold)
                }

                if (!confirmDelete) {
                    Button(
                        onClick = { confirmDelete = true },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.16f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حذف الجروب نهائياً", color = DangerRed, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "تأكيد حذف الجروب وجميع رسائله نهائياً؟",
                            color = DangerRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    isBusy = true
                                    scope.launch {
                                        val res = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val ep = EntryPointAccessors.fromApplication(
                                                    context.applicationContext,
                                                    AccountSwitchSupabaseEntryPoint::class.java
                                                )
                                                runCatching {
                                                    ep.postgrest().from("messages").delete {
                                                        filter { eq("chat_id", chatId) }
                                                    }
                                                }
                                                runCatching {
                                                    ep.postgrest().from("chat_members").delete {
                                                        filter { eq("chat_id", chatId) }
                                                    }
                                                }
                                                ep.postgrest().from("chats").delete {
                                                    filter { eq("id", chatId) }
                                                }
                                            }
                                        }
                                        isBusy = false
                                        res.onSuccess {
                                            Toast.makeText(context, "تم حذف الجروب نهائياً", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                            onGroupRemovedOrLeft()
                                        }.onFailure { e ->
                                            Toast.makeText(context, "تعذر حذف الجروب: ${e.localizedMessage?.take(50)}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isBusy) "جاري الحذف..." else "نعم، احذف", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { confirmDelete = false },
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("تراجع")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("إغلاق")
            }
        }
    )
}
