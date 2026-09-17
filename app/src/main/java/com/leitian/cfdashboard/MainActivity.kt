package com.leitian.cfdashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.leitian.cfdashboard.data.TokenStore
import com.leitian.cfdashboard.ui.AppNavigation
import com.leitian.cfdashboard.ui.theme.CFTheme
import com.leitian.cfdashboard.ui.viewmodel.MainViewModel
import com.leitian.cfdashboard.ui.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {

    private val tokenStore by lazy { TokenStore(applicationContext) }
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(tokenStore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前调用
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // ViewModel 在 Activity 级创建；凭据读完（非 null）再放行系统闪屏
        splashScreen.setKeepOnScreenCondition {
            viewModel.isLoggedIn.value == null
        }

        enableEdgeToEdge()
        setContent {
            CFTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}
