package com.leitian.cfdashboard.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.leitian.cfdashboard.data.AccessAppItem
import com.leitian.cfdashboard.data.BindingItem
import com.leitian.cfdashboard.data.CloudflareApi
import com.leitian.cfdashboard.data.CloudflareDetailApi
import com.leitian.cfdashboard.data.DeploymentItem
import com.leitian.cfdashboard.data.DomainItem
import com.leitian.cfdashboard.data.ScriptInfo
import com.leitian.cfdashboard.data.TokenStore
import com.leitian.cfdashboard.data.WorkerMetrics
import com.leitian.cfdashboard.data.WorkerSettingsDetail
import com.leitian.cfdashboard.data.WorkerSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainViewModel(private val tokenStore: TokenStore) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _workers = MutableStateFlow<List<WorkerSummary>>(emptyList())
    val workers: StateFlow<List<WorkerSummary>> = _workers.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _listError = MutableStateFlow<String?>(null)
    val listError: StateFlow<String?> = _listError.asStateFlow()

    private val _scriptInfo = MutableStateFlow<ScriptInfo?>(null)
    val scriptInfo: StateFlow<ScriptInfo?> = _scriptInfo.asStateFlow()

    private val _metrics = MutableStateFlow<WorkerMetrics?>(null)
    val metrics: StateFlow<WorkerMetrics?> = _metrics.asStateFlow()

    private val _metricsLoading = MutableStateFlow(false)
    val metricsLoading: StateFlow<Boolean> = _metricsLoading.asStateFlow()

    private val _tabLoading = MutableStateFlow(false)
    val tabLoading: StateFlow<Boolean> = _tabLoading.asStateFlow()

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

    // 各类写操作独立状态，避免互相覆盖
    private val _variableWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val variableWriteState: StateFlow<WriteState> = _variableWriteState.asStateFlow()

    private val _observabilityWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val observabilityWriteState: StateFlow<WriteState> = _observabilityWriteState.asStateFlow()

    private val _runtimeWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val runtimeWriteState: StateFlow<WriteState> = _runtimeWriteState.asStateFlow()

    private val _cronWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val cronWriteState: StateFlow<WriteState> = _cronWriteState.asStateFlow()

    private val _deleteWorkerState = MutableStateFlow<WriteState>(WriteState.Idle)
    val deleteWorkerState: StateFlow<WriteState> = _deleteWorkerState.asStateFlow()

    private val _cronTriggers = MutableStateFlow<List<String>>(emptyList())
    val cronTriggers: StateFlow<List<String>> = _cronTriggers.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    sealed class UploadState {
        object Idle : UploadState()
        object Loading : UploadState()
        data class Success(val message: String) : UploadState()
        data class Error(val message: String) : UploadState()
    }

    fun clearVariableWriteState() { _variableWriteState.value = WriteState.Idle }
    fun clearObservabilityWriteState() { _observabilityWriteState.value = WriteState.Idle }
    fun clearRuntimeWriteState() { _runtimeWriteState.value = WriteState.Idle }
    fun clearCronWriteState() { _cronWriteState.value = WriteState.Idle }
    fun clearDeleteWorkerState() { _deleteWorkerState.value = WriteState.Idle }
    fun clearUploadState() { _uploadState.value = UploadState.Idle }

    private var email: String? = null
    private var apiKey: String? = null
    private var accountId: String? = null

    init {
        val e = tokenStore.email
        val k = tokenStore.apiKey
        val a = tokenStore.accountId
        if (!e.isNullOrBlank() && !k.isNullOrBlank() && !a.isNullOrBlank()) {
            email = e
            apiKey = k
            accountId = a
            _isLoggedIn.value = true
            refreshWorkers()
        }
    }

    fun login(emailInput: String, keyInput: String, accountInput: String) {
        viewModelScope.launch {
            _loginError.value = null
            _loading.value = true
            val result = CloudflareApi.verifyCredentials(emailInput, keyInput, accountInput)
            _loading.value = false
            if (result.success) {
                tokenStore.save(emailInput, keyInput, accountInput)
                email = emailInput
                apiKey = keyInput
                accountId = accountInput
                _isLoggedIn.value = true
                refreshWorkers()
            } else {
                _loginError.value = result.error ?: "登录失败"
            }
        }
    }

    fun logout() {
        tokenStore.clear()
        email = null
        apiKey = null
        accountId = null
        _isLoggedIn.value = false
        _workers.value = emptyList()
        clearDetail()
    }

    fun refreshWorkers() {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _loading.value = true
            _listError.value = null
            val result = CloudflareApi.listWorkers(e, k, a)
            _loading.value = false
            if (result.success) {
                _workers.value = result.data ?: emptyList()
            } else {
                _listError.value = result.error
            }
        }
    }

    fun loadDetail(scriptName: String) {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _metricsLoading.value = true
            _tabLoading.value = true
            _deploymentsError.value = null
            _domainsError.value = null
            _accessError.value = null
            _settingsError.value = null

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

            val cron = CloudflareDetailApi.listSchedules(e, k, a, scriptName)
            if (cron.success) _cronTriggers.value = cron.data ?: emptyList()

            _metricsLoading.value = false
            _tabLoading.value = false
        }
    }

    /** 仅刷新设置 Tab（写操作成功后调用，避免整页重新拉取） */
    private fun refreshSettings(scriptName: String) {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            val set = CloudflareDetailApi.getSettingsDetail(e, k, a, scriptName)
            if (set.success) _settingsDetail.value = set.data
            val cron = CloudflareDetailApi.listSchedules(e, k, a, scriptName)
            if (cron.success) _cronTriggers.value = cron.data ?: emptyList()
        }
    }

    fun clearDetail() {
        _scriptInfo.value = null
        _metrics.value = null
        _deployments.value = emptyList()
        _domains.value = emptyList()
        _accessApps.value = emptyList()
        _settingsDetail.value = null
        _cronTriggers.value = emptyList()
        _deploymentsError.value = null
        _domainsError.value = null
        _accessError.value = null
        _settingsError.value = null
        _variableWriteState.value = WriteState.Idle
        _observabilityWriteState.value = WriteState.Idle
        _runtimeWriteState.value = WriteState.Idle
        _cronWriteState.value = WriteState.Idle
        _deleteWorkerState.value = WriteState.Idle
    }

    fun loadMetrics(scriptName: String) {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _metricsLoading.value = true
            val result = CloudflareApi.getMetrics(e, k, a, scriptName)
            _metricsLoading.value = false
            if (result.success) _metrics.value = result.data
        }
    }

    // ========== 变量 / 绑定写操作 ==========

    fun addOrUpdateBinding(
        scriptName: String,
        name: String,
        type: String,
        value: String,
        existingBindings: List<BindingItem>
    ) {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _variableWriteState.value = WriteState.Loading
            // 整体覆盖式：构建完整 bindings 数组
            val arr = JSONArray()
            var replaced = false
            for (b in existingBindings) {
                if (b.name == name) {
                    val o = JSONObject()
                    o.put("name", name)
                    o.put("type", type)
                    when (type) {
                        "plain_text", "secret_text" -> o.put("text", value)
                        "kv_namespace" -> o.put("namespace_id", value)
                        "r2_bucket" -> o.put("bucket_name", value)
                        "d1" -> o.put("id", value)
                        "service" -> o.put("service", value)
                        else -> o.put("text", value)
                    }
                    arr.put(o)
                    replaced = true
                } else {
                    try {
                        arr.put(JSONObject(b.rawJson))
                    } catch (_: Exception) {
                        val o = JSONObject()
                        o.put("name", b.name)
                        o.put("type", b.type)
                        arr.put(o)
                    }
                }
            }
            if (!replaced) {
                val o = JSONObject()
                o.put("name", name)
                o.put("type", type)
                when (type) {
                    "plain_text", "secret_text" -> o.put("text", value)
                    "kv_namespace" -> o.put("namespace_id", value)
                    "r2_bucket" -> o.put("bucket_name", value)
                    "d1" -> o.put("id", value)
                    "service" -> o.put("service", value)
                    else -> o.put("text", value)
                }
                arr.put(o)
            }
            val body = JSONObject().put("bindings", arr).toString()
            val result = CloudflareDetailApi.patchScriptSettings(e, k, a, scriptName, body)
            if (result.success) {
                _variableWriteState.value = WriteState.Success("已保存")
                refreshSettings(scriptName)
            } else {
                _variableWriteState.value = WriteState.Error(result.error ?: "保存失败")
            }
        }
    }

    fun deleteBinding(scriptName: String, name: String, existingBindings: List<BindingItem>) {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _variableWriteState.value = WriteState.Loading
            val arr = JSONArray()
            for (b in existingBindings) {
                if (b.name == name) continue
                try {
                    arr.put(JSONObject(b.rawJson))
                } catch (_: Exception) {
                    val o = JSONObject()
                    o.put("name", b.name)
                    o.put("type", b.type)
                    arr.put(o)
                }
            }
            val body = JSONObject().put("bindings", arr).toString()
            val result = CloudflareDetailApi.patchScriptSettings(e, k, a, scriptName, body)
            if (result.success) {
                _variableWriteState.value = WriteState.Success("已删除")
                refreshSettings(scriptName)
            } else {
                _variableWriteState.value = WriteState.Error(result.error ?: "删除失败")
            }
        }
    }

    // ========== 可观察性 ==========

    fun updateObservability(scriptName: String, enabled: Boolean, samplingRate: Double) {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _observabilityWriteState.value = WriteState.Loading
            val obs = JSONObject()
                .put("enabled", enabled)
                .put("head_sampling_rate", JSONObject().put("value", samplingRate))
            val body = JSONObject().put("observability", obs).toString()
            val result = CloudflareDetailApi.patchScriptSettings(e, k, a, scriptName, body)
            if (result.success) {
                _observabilityWriteState.value = WriteState.Success("已更新")
                refreshSettings(scriptName)
            } else {
                _observabilityWriteState.value = WriteState.Error(result.error ?: "更新失败")
            }
        }
    }

    // ========== 运行时设置 ==========

    fun updateRuntimeSettings(
        scriptName: String,
        compatibilityDate: String?,
        usageModel: String?,
        logpush: Boolean?
    ) {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _runtimeWriteState.value = WriteState.Loading
            val obj = JSONObject()
            if (compatibilityDate != null) obj.put("compatibility_date", compatibilityDate)
            if (usageModel != null) obj.put("usage_model", usageModel)
            if (logpush != null) obj.put("logpush", logpush)
            val result = CloudflareDetailApi.patchScriptSettings(e, k, a, scriptName, obj.toString())
            if (result.success) {
                _runtimeWriteState.value = WriteState.Success("已更新")
                refreshSettings(scriptName)
            } else {
                _runtimeWriteState.value = WriteState.Error(result.error ?: "更新失败")
            }
        }
    }

    // ========== Cron ==========

    fun updateCronTriggers(scriptName: String, crons: List<String>) {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _cronWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.updateSchedules(e, k, a, scriptName, crons)
            if (result.success) {
                _cronWriteState.value = WriteState.Success("Cron 已保存")
                _cronTriggers.value = crons
            } else {
                _cronWriteState.value = WriteState.Error(result.error ?: "保存失败")
            }
        }
    }

    // ========== 危险操作：删除 Worker ==========

    fun deleteWorker(scriptName: String) {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _deleteWorkerState.value = WriteState.Loading
            val result = CloudflareDetailApi.deleteWorker(e, k, a, scriptName)
            if (result.success) {
                _deleteWorkerState.value = WriteState.Success("已删除")
                refreshWorkers()
            } else {
                _deleteWorkerState.value = WriteState.Error(result.error ?: "删除失败")
            }
        }
    }

    // ========== 上传脚本 ==========

    fun uploadScript(context: Context, scriptName: String, uri: Uri) {
        val e = email ?: return
        val k = apiKey ?: return
        val a = accountId ?: return
        viewModelScope.launch {
            _uploadState.value = UploadState.Loading
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: run {
                        _uploadState.value = UploadState.Error("无法读取文件")
                        return@launch
                    }
                val result = CloudflareApi.uploadScript(e, k, a, scriptName, bytes)
                if (result.success) {
                    _uploadState.value = UploadState.Success("上传成功")
                    loadDetail(scriptName)
                    refreshWorkers()
                } else {
                    _uploadState.value = UploadState.Error(result.error ?: "上传失败")
                }
            } catch (ex: Exception) {
                _uploadState.value = UploadState.Error(ex.message ?: "上传异常")
            }
        }
    }

    fun getFileName(context: Context, uri: Uri): String {
        var name = "script.js"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx) ?: name
            }
        }
        return name
    }
}

class MainViewModelFactory(private val tokenStore: TokenStore) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(tokenStore) as T
    }
}
