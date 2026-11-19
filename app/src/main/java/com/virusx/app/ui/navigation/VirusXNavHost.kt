package com.virusx.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
        startDestination = startDest,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(280)
            ) + fadeIn(animationSpec = tween(240))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(260)
            ) + fadeOut(animationSpec = tween(200))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(260)
            ) + fadeIn(animationSpec = tween(220))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(260)
            ) + fadeOut(animationSpec = tween(200))
        }
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
