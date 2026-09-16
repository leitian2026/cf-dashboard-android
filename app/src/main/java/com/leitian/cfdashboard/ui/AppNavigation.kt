package com.leitian.cfdashboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.leitian.cfdashboard.data.TokenStore
import com.leitian.cfdashboard.ui.screens.HomeScreen
import com.leitian.cfdashboard.ui.screens.LoginScreen
import com.leitian.cfdashboard.ui.screens.WorkerDetailScreen
import com.leitian.cfdashboard.ui.viewmodel.MainViewModel
import com.leitian.cfdashboard.ui.viewmodel.MainViewModelFactory

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val tokenStore = TokenStore(context)
    val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(tokenStore))
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) "home" else "login"
    ) {
        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onAppClick = { id, name ->
                    navController.navigate("detail/$id/$name")
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "detail/{appId}/{appName}",
            arguments = listOf(
                navArgument("appId") { type = NavType.StringType },
                navArgument("appName") { type = NavType.StringType }
            )
        ) { entry ->
            val appId = entry.arguments?.getString("appId") ?: ""
            val appName = entry.arguments?.getString("appName") ?: ""
            WorkerDetailScreen(
                appId = appId,
                appName = appName,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
