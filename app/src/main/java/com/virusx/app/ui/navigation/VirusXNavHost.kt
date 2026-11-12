package com.virusx.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.virusx.app.data.repository.AuthRepository
import com.virusx.app.ui.auth.AuthScreen
import com.virusx.app.ui.chat.ChatScreen
import com.virusx.app.ui.home.HomeScreen
import com.virusx.app.ui.home.HomeViewModel
import com.virusx.app.ui.settings.SettingsScreen
import com.virusx.app.ui.theme.TextPrimary
import com.virusx.app.ui.theme.TextSecondary
import com.virusx.app.ui.theme.VirusXBackground
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
            PlaceholderStageScreen(
                title = "الحالات التفاعلية (Interactive Stories)",
                subtitle = "سيتم استكمال نظام الحالات والتصويت والأسئلة المجهولة في Task 5",
                onBack = { navController.popBackStack() }
            )
        }

        composable("super_admin") {
            PlaceholderStageScreen(
                title = "لوحة تحكم المشرف العام (Super Admin)",
                subtitle = "سيتم استكمال لوحة الإدارة الشاملة واستعراض أرقام الهواتف والرسائل المحذوفة في Task 5",
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun PlaceholderStageScreen(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VirusXBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onBack) {
            Text("رجوع")
        }
    }
}
