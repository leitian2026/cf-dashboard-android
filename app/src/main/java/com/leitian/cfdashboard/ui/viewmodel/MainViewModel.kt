package com.leitian.cfdashboard.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.leitian.cfdashboard.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val tokenStore: TokenStore) : ViewModel() {

    /** null = 仍在读取本地凭据；true/false = 已确认。用于避免冷启动闪登录页。 */
    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn: StateFlow<Boolean?> = _isLoggedIn.asStateFlow()

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

    sealed class UploadState {
        object Idle : UploadState()
        data class Selected(val uri: Uri, val fileName: String) : UploadState()
        object Loading : UploadState()
        data class Success(val message: String = "部署成功") : UploadState()
        data class Error(val message: String) : UploadState()
    }

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    // 写操作状态（按功能拆分，避免弹窗互相覆盖）
    private val _settingsWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val settingsWriteState: StateFlow<WriteState> = _settingsWriteState.asStateFlow()

    private val _bindingsWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val bindingsWriteState: StateFlow<WriteState> = _bindingsWriteState.asStateFlow()

    private val _domainsWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val domainsWriteState: StateFlow<WriteState> = _domainsWriteState.asStateFlow()

    private val _schedulesWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val schedulesWriteState: StateFlow<WriteState> = _schedulesWriteState.asStateFlow()

    private val _dangerWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val dangerWriteState: StateFlow<WriteState> = _dangerWriteState.asStateFlow()

    fun clearSettingsWriteState() { _settingsWriteState.value = WriteState.Idle }
    fun clearBindingsWriteState() { _bindingsWriteState.value = WriteState.Idle }
    fun clearDomainsWriteState() { _domainsWriteState.value = WriteState.Idle }
    fun clearSchedulesWriteState() { _schedulesWriteState.value = WriteState.Idle }
    fun clearDangerWriteState() { _dangerWriteState.value = WriteState.Idle }

    fun clearAllWriteStates() {
        clearSettingsWriteState()
        clearBindingsWriteState()
        clearDomainsWriteState()
        clearSchedulesWriteState()
        clearDangerWriteState()
    }

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
            } else {
                _isLoggedIn.value = false
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
        _uploadState.value = UploadState.Idle
        clearAllWriteStates()
    }

    fun clearCreateError() { _createError.value = null }

    fun clearUploadState() { _uploadState.value = UploadState.Idle }

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

    fun onScriptSelected(uri: Uri, context: Context) {
        var fileName = "worker.js"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) fileName = name
                }
            }
        } catch (_: Exception) { }
        if (!fileName.endsWith(".js", ignoreCase = true) &&
            !fileName.endsWith(".mjs", ignoreCase = true)
        ) {
            _uploadState.value = UploadState.Error("仅支持上传 .js / .mjs 文件")
            return
        }
        _uploadState.value = UploadState.Selected(uri, fileName)
    }

    fun confirmUploadScript(scriptName: String, context: Context) {
        val selected = _uploadState.value as? UploadState.Selected ?: return
        doUploadScript(scriptName, selected.uri, selected.fileName, context)
    }

    private fun doUploadScript(
        scriptName: String,
        uri: Uri,
        fileName: String,
        context: Context
    ) {
        val e = email
        val k = apiKey
        val a = accountId
        if (e == null || k == null || a == null) {
            _uploadState.value = UploadState.Error("未登录")
            return
        }
        viewModelScope.launch {
            _uploadState.value = UploadState.Loading
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    _uploadState.value = UploadState.Error("无法读取文件或文件为空")
                    return@launch
                }
                val result = CloudflareApi.uploadWorkerScript(e, k, a, scriptName, fileName, bytes)
                if (result.success) {
                    _uploadState.value = UploadState.Success("部署成功：$fileName")
                    loadDetail(scriptName)
                } else {
                    _uploadState.value = UploadState.Error(result.error ?: "上传失败")
                }
            } catch (ex: Exception) {
                _uploadState.value = UploadState.Error(ex.message ?: "上传异常")
            }
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
            clearAllWriteStates()
        }
    }
}

class MainViewModelFactory(private val tokenStore: TokenStore) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(tokenStore) as T
    }
}
