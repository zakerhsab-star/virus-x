package com.virusx.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.virusx.app.ui.theme.DangerRed
import com.virusx.app.ui.theme.SentMessageGradient
import com.virusx.app.ui.theme.TextPrimary
import com.virusx.app.ui.theme.TextSecondary
import com.virusx.app.ui.theme.VirusXBackground
import com.virusx.app.ui.theme.VirusXGlassBorder
import com.virusx.app.ui.theme.VirusXPrimary
import com.virusx.app.ui.theme.VirusXSecondaryWhisper
import com.virusx.app.ui.theme.VirusXSurface
import com.virusx.app.ui.theme.VirusXSurfaceElevated

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onAuthSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VirusXBackground)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(24.dp))
                .background(VirusXSurface)
                .border(1.dp, VirusXGlassBorder, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SentMessageGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "VirusX Logo",
                    tint = TextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "VirusX",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )

            Text(
                text = if (state.isSignUpMode) "إنشاء حساب جديد في مجتمع الأصدقاء" else "تسجيل الدخول للمتابعة",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(visible = state.isSignUpMode) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = viewModel::onDisplayNameChange,
                        label = { Text("الاسم الكامل (Display Name)") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = authFieldColors()
                    )

                    OutlinedTextField(
                        value = state.username,
                        onValueChange = viewModel::onUsernameChange,
                        label = { Text("اسم المستخدم (Username)") },
                        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = authFieldColors()
                    )

                    OutlinedTextField(
                        value = state.phoneNumber,
                        onValueChange = viewModel::onPhoneNumberChange,
                        label = { Text("رقم الهاتف (إجباري)") },
                        placeholder = { Text("01xxxxxxxxx") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = authFieldColors()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("البريد الإلكتروني") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = authFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("كلمة المرور") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = authFieldColors()
            )

            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.errorMessage ?: "",
                    color = DangerRed,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            if (state.isSignUpMode) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VirusXSurfaceElevated)
                        .border(1.dp, VirusXGlassBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = VirusXSecondaryWhisper,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "إشعار الشفافية والأمان: هذه المنصة خاصة ومُدارة تقنياً وتخضع للإشراف الإداري لضمان أمان جميع المستخدمين ومنع الإساءة.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = viewModel::submit,
                enabled = !state.isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = TextPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = if (state.isSignUpMode) "إنشاء الحساب الآن" else "تسجيل الدخول",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = viewModel::toggleMode) {
                Text(
                    text = if (state.isSignUpMode) {
                        "لديك حساب بالفعل؟ تسجيل الدخول"
                    } else {
                        "مستخدم جديد؟ إنشاء حساب"
                    },
                    color = VirusXSecondaryWhisper
                )
            }
        }
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VirusXPrimary,
    unfocusedBorderColor = VirusXGlassBorder,
    focusedLabelColor = VirusXPrimary,
    unfocusedLabelColor = TextSecondary,
    cursorColor = VirusXPrimary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
