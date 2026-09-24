package com.leitian.cfdashboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.leitian.cfdashboard.data.NetworkLogging
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

    // 冷启动时把上次保存的日志开关状态恢复出来，早于任何网络请求即可生效。
    LaunchedEffect(Unit) { NetworkLogging.restore(context) }

    // null：仍在读本地凭据，不创建 NavHost，避免冷启动先闪登录页
    if (isLoggedIn == null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn == true) "home" else "login"
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
        // 从首页"添加账号"进来的登录页：登录成功或取消都是回到首页（popBackStack），
        // 不会像冷启动那个 "login" 一样清空返回栈。
        composable("login_add") {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onAppClick = { accountId, id, name, isPages ->
                    navController.navigate("detail/$accountId/$id/$name/$isPages")
                },
                onAddAccount = { navController.navigate("login_add") },
                onLogout = {
                    viewModel.logout()
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "detail/{accountId}/{appId}/{appName}/{isPages}",
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("appId") { type = NavType.StringType },
                navArgument("appName") { type = NavType.StringType },
                navArgument("isPages") { type = NavType.BoolType }
            )
        ) { entry ->
            val accountId = entry.arguments?.getString("accountId") ?: ""
            val appId = entry.arguments?.getString("appId") ?: ""
            val appName = entry.arguments?.getString("appName") ?: ""
            val isPages = entry.arguments?.getBoolean("isPages") ?: false
            WorkerDetailScreen(
                appId = appId,
                appName = appName,
                isPages = isPages,
                accountId = accountId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
