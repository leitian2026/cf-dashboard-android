package com.leitian.cfdashboard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.leitian.cfdashboard.data.CloudflareApi
import com.leitian.cfdashboard.data.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val tokenStore: TokenStore) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _apps = MutableStateFlow<List<CloudflareApi.AppItem>>(emptyList())
    val apps: StateFlow<List<CloudflareApi.AppItem>> = _apps.asStateFlow()

    private var token: String? = null
    private var accountId: String? = null

    init {
        viewModelScope.launch {
            val (t, a) = tokenStore.getTokenAndAccount()
            if (!t.isNullOrBlank() && !a.isNullOrBlank()) {
                token = t
                accountId = a
                _isLoggedIn.value = true
                loadApps()
            }
        }
    }

    fun login(apiToken: String) {
        if (apiToken.isBlank()) {
            _loginError.value = "请输入 API Token"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _loginError.value = null
            val result = CloudflareApi.verifyToken(apiToken.trim())
            if (result.success && result.data != null) {
                token = apiToken.trim()
                accountId = result.data.id
                tokenStore.save(apiToken.trim(), result.data.id, result.data.name)
                _isLoggedIn.value = true
                loadApps()
            } else {
                _loginError.value = result.error ?: "登录失败"
            }
            _isLoading.value = false
        }
    }

    fun loadApps() {
        val t = token ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = CloudflareApi.getApps(t, a)
            if (result.success) {
                _apps.value = result.data ?: emptyList()
            }
            _isLoading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokenStore.clear()
            token = null
            accountId = null
            _isLoggedIn.value = false
            _apps.value = emptyList()
        }
    }
}

class MainViewModelFactory(private val tokenStore: TokenStore) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(tokenStore) as T
    }
}
