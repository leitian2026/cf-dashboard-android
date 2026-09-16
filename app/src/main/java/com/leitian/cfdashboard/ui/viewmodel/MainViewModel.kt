package com.leitian.cfdashboard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.leitian.cfdashboard.data.*
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

    private val _accountStats = MutableStateFlow<CloudflareApi.AccountStats?>(null)
    val accountStats: StateFlow<CloudflareApi.AccountStats?> = _accountStats.asStateFlow()

    private val _accountStatsError = MutableStateFlow<String?>(null)
    val accountStatsError: StateFlow<String?> = _accountStatsError.asStateFlow()

    private val _metrics = MutableStateFlow<CloudflareApi.WorkerMetrics?>(null)
    val metrics: StateFlow<CloudflareApi.WorkerMetrics?> = _metrics.asStateFlow()

    private val _scriptInfo = MutableStateFlow<CloudflareApi.ScriptInfo?>(null)
    val scriptInfo: StateFlow<CloudflareApi.ScriptInfo?> = _scriptInfo.asStateFlow()

    private val _metricsLoading = MutableStateFlow(false)
    val metricsLoading: StateFlow<Boolean> = _metricsLoading.asStateFlow()

    private val _metricsError = MutableStateFlow<String?>(null)
    val metricsError: StateFlow<String?> = _metricsError.asStateFlow()

    private val _createLoading = MutableStateFlow(false)
    val createLoading: StateFlow<Boolean> = _createLoading.asStateFlow()

    private val _createError = MutableStateFlow<String?>(null)
    val createError: StateFlow<String?> = _createError.asStateFlow()

    // 详情 Tab 数据
    private val _deployments = MutableStateFlow<List<DeploymentItem>>(emptyList())
    val deployments: StateFlow<List<DeploymentItem>> = _deployments.asStateFlow()
    private val _deploymentsError = MutableStateFlow<String?>(null)
    val deploymentsError: StateFlow<String?> = _deploymentsError.asStateFlow()

    private val _domains = MutableStateFlow<List<DomainItem>>(emptyList())
    val domains: StateFlow<List<DomainItem>> = _domains.asStateFlow()
    private val _domainsError = MutableStateFlow<String?>(null)
    val domainsError: StateFlow<String?> = _domainsError.asStateFlow()

    private val _accessApps = MutableStateFlow<List<AccessAppItem>>(emptyList())
    val accessApps: StateFlow<List<AccessAppItem>> = _accessApps.asStateFlow()
    private val _accessError = MutableStateFlow<String?>(null)
    val accessError: StateFlow<String?> = _accessError.asStateFlow()

    private val _settingsDetail = MutableStateFlow<WorkerSettingsDetail?>(null)
    val settingsDetail: StateFlow<WorkerSettingsDetail?> = _settingsDetail.asStateFlow()
    private val _settingsError = MutableStateFlow<String?>(null)
    val settingsError: StateFlow<String?> = _settingsError.asStateFlow()

    private val _tabLoading = MutableStateFlow(false)
    val tabLoading: StateFlow<Boolean> = _tabLoading.asStateFlow()

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
                loadAccountStats()
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
                loadAccountStats()
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
            if (result.success) _apps.value = result.data ?: emptyList()
            _isLoading.value = false
        }
    }

    fun loadAccountStats() {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _accountStatsError.value = null
            val result = CloudflareApi.getAccountStats(e, k, a)
            if (result.success) _accountStats.value = result.data
            else _accountStatsError.value = result.error
        }
    }

    fun loadDetail(scriptName: String) {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _metricsLoading.value = true
            _tabLoading.value = true
            _metrics.value = null
            _scriptInfo.value = null
            _metricsError.value = null

            val metricsResult = CloudflareApi.getWorkerMetrics(e, k, a, scriptName)
            if (metricsResult.success) _metrics.value = metricsResult.data
            else _metricsError.value = metricsResult.error

            val infoResult = CloudflareApi.getScriptInfo(e, k, a, scriptName)
            if (infoResult.success) _scriptInfo.value = infoResult.data

            // 并行拉四个 Tab 数据
            val dep = CloudflareDetailApi.listDeployments(e, k, a, scriptName)
            if (dep.success) _deployments.value = dep.data ?: emptyList()
            else _deploymentsError.value = dep.error

            val dom = CloudflareDetailApi.listDomains(e, k, a, scriptName)
            if (dom.success) _domains.value = dom.data ?: emptyList()
            else _domainsError.value = dom.error

            val acc = CloudflareDetailApi.listAccessApps(e, k, a)
            if (acc.success) _accessApps.value = acc.data ?: emptyList()
            else _accessError.value = acc.error

            val set = CloudflareDetailApi.getSettingsDetail(e, k, a, scriptName)
            if (set.success) _settingsDetail.value = set.data
            else _settingsError.value = set.error

            _metricsLoading.value = false
            _tabLoading.value = false
        }
    }

    fun clearDetail() {
        _metrics.value = null
        _scriptInfo.value = null
        _metricsError.value = null
        _deployments.value = emptyList()
        _domains.value = emptyList()
        _accessApps.value = emptyList()
        _settingsDetail.value = null
        _deploymentsError.value = null
        _domainsError.value = null
        _accessError.value = null
        _settingsError.value = null
    }

    fun clearCreateError() { _createError.value = null }

    fun createWorker(name: String, onSuccess: () -> Unit) {
        val e = email
        val k = apiKey
        val a = accountId
        if (e == null || k == null || a == null) {
            _createError.value = "未登录"
            return
        }
        viewModelScope.launch {
            _createLoading.value = true
            _createError.value = null
            val result = CloudflareApi.createWorker(e, k, a, name)
            if (result.success) {
                loadApps()
                onSuccess()
            } else {
                _createError.value = result.error ?: "创建失败"
            }
            _createLoading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokenStore.clear()
            email = null
            apiKey = null
            accountId = null
            _isLoggedIn.value = false
            _apps.value = emptyList()
            _accountStats.value = null
            clearDetail()
        }
    }
}

class MainViewModelFactory(private val tokenStore: TokenStore) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(tokenStore) as T
    }
}
