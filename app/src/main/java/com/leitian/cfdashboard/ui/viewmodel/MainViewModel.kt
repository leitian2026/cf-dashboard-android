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

    private val _metrics = MutableStateFlow<CloudflareApi.WorkerMetrics?>(null)
    val metrics: StateFlow<CloudflareApi.WorkerMetrics?> = _metrics.asStateFlow()

    private val _metricsLoading = MutableStateFlow(false)
    val metricsLoading: StateFlow<Boolean> = _metricsLoading.asStateFlow()

    private var email: String? = null
    private var apiKey: String? = null
    private var accountId: String? = null

    init {
        viewModelScope.launch {
            val (e, k, a) = tokenStore.getCredentials()
            if (!e.isNullOrBlank() && !k.isNullOrBlank() && !a.isNullOrBlank()) {
                email = e
                apiKey = k
                accountId = a
                _isLoggedIn.value = true
                loadApps()
            }
        }
    }

    fun login(emailInput: String, keyInput: String) {
        val e = emailInput.trim()
        val k = keyInput.trim()
        if (e.isBlank() || k.isBlank()) {
            _loginError.value = "请输入邮箱和 Global API Key"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _loginError.value = null
            val result = CloudflareApi.verifyGlobalKey(e, k)
            if (result.success && result.data != null) {
                email = e
                apiKey = k
                accountId = result.data.id
                tokenStore.save(e, k, result.data.id, result.data.name)
                _isLoggedIn.value = true
                loadApps()
            } else {
                _loginError.value = result.error ?: "登录失败"
            }
            _isLoading.value = false
        }
    }

    fun loadApps() {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = CloudflareApi.getApps(e, k, a)
            if (result.success) {
                _apps.value = result.data ?: emptyList()
            }
            _isLoading.value = false
        }
    }

    fun loadMetrics(scriptName: String) {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _metricsLoading.value = true
            _metrics.value = null
            val result = CloudflareApi.getWorkerMetrics(e, k, a, scriptName)
            if (result.success) {
                _metrics.value = result.data
            }
            _metricsLoading.value = false
        }
    }

    fun clearMetrics() {
        _metrics.value = null
    }

    fun logout() {
        viewModelScope.launch {
            tokenStore.clear()
            email = null
            apiKey = null
            accountId = null
            _isLoggedIn.value = false
            _apps.value = emptyList()
            _metrics.value = null
        }
    }
}

class MainViewModelFactory(private val tokenStore: TokenStore) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(tokenStore) as T
    }
}
