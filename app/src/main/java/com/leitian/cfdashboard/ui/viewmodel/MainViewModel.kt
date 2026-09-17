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

    // ---- 设置 Tab 写操作状态（变量增删改 / 可观察性 / 运行时 / Cron 各自独立，避免互相覆盖弹窗） ----
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

    // ---- 绑定 Tab 写操作 + 资源选择器数据 ----
    private val _bindingWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val bindingWriteState: StateFlow<WriteState> = _bindingWriteState.asStateFlow()

    private val _kvNamespaces = MutableStateFlow<List<com.leitian.cfdashboard.data.KvNamespaceItem>>(emptyList())
    val kvNamespaces: StateFlow<List<com.leitian.cfdashboard.data.KvNamespaceItem>> = _kvNamespaces.asStateFlow()
    private val _r2Buckets = MutableStateFlow<List<com.leitian.cfdashboard.data.R2BucketItem>>(emptyList())
    val r2Buckets: StateFlow<List<com.leitian.cfdashboard.data.R2BucketItem>> = _r2Buckets.asStateFlow()
    private val _d1Databases = MutableStateFlow<List<com.leitian.cfdashboard.data.D1DatabaseItem>>(emptyList())
    val d1Databases: StateFlow<List<com.leitian.cfdashboard.data.D1DatabaseItem>> = _d1Databases.asStateFlow()

    // ---- 域 Tab 写操作 + Zone 选择器 ----
    private val _domainWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val domainWriteState: StateFlow<WriteState> = _domainWriteState.asStateFlow()
    private val _zones = MutableStateFlow<List<com.leitian.cfdashboard.data.ZoneItem>>(emptyList())
    val zones: StateFlow<List<com.leitian.cfdashboard.data.ZoneItem>> = _zones.asStateFlow()

    // ---- 触发事件：Queues / 邮件路由 ----
    private val _queueWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val queueWriteState: StateFlow<WriteState> = _queueWriteState.asStateFlow()
    private val _queues = MutableStateFlow<List<com.leitian.cfdashboard.data.QueueItem>>(emptyList())
    val queues: StateFlow<List<com.leitian.cfdashboard.data.QueueItem>> = _queues.asStateFlow()

    private val _emailWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val emailWriteState: StateFlow<WriteState> = _emailWriteState.asStateFlow()
    private val _emailRules = MutableStateFlow<List<com.leitian.cfdashboard.data.EmailRoutingRuleItem>>(emptyList())
    val emailRules: StateFlow<List<com.leitian.cfdashboard.data.EmailRoutingRuleItem>> = _emailRules.asStateFlow()

    // ---- 部署回滚 ----
    private val _deploymentWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val deploymentWriteState: StateFlow<WriteState> = _deploymentWriteState.asStateFlow()

    // 上传部署状态机：Idle → Selected → Loading → Success/Error
    sealed class UploadState {
        object Idle : UploadState()
        /** 已选中文件，等待用户确认 */
        data class Selected(val uri: Uri, val fileName: String) : UploadState()
        object Loading : UploadState()
        data class Success(val message: String = "部署成功") : UploadState()
        data class Error(val message: String) : UploadState()
    }

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

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
            else _settingsError.value = set.error
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
        _cronTriggers.value = emptyList()
        _uploadState.value = UploadState.Idle
        _variableWriteState.value = WriteState.Idle
        _observabilityWriteState.value = WriteState.Idle
        _runtimeWriteState.value = WriteState.Idle
        _cronWriteState.value = WriteState.Idle
        _deleteWorkerState.value = WriteState.Idle
        _bindingWriteState.value = WriteState.Idle
        _domainWriteState.value = WriteState.Idle
        _queueWriteState.value = WriteState.Idle
        _emailWriteState.value = WriteState.Idle
        _deploymentWriteState.value = WriteState.Idle
        _kvNamespaces.value = emptyList()
        _r2Buckets.value = emptyList()
        _d1Databases.value = emptyList()
        _zones.value = emptyList()
        _queues.value = emptyList()
        _emailRules.value = emptyList()
    }

    fun clearVariableWriteState() { _variableWriteState.value = WriteState.Idle }
    fun clearObservabilityWriteState() { _observabilityWriteState.value = WriteState.Idle }
    fun clearRuntimeWriteState() { _runtimeWriteState.value = WriteState.Idle }
    fun clearCronWriteState() { _cronWriteState.value = WriteState.Idle }
    fun clearDeleteWorkerState() { _deleteWorkerState.value = WriteState.Idle }
    fun clearBindingWriteState() { _bindingWriteState.value = WriteState.Idle }
    fun clearDomainWriteState() { _domainWriteState.value = WriteState.Idle }
    fun clearQueueWriteState() { _queueWriteState.value = WriteState.Idle }
    fun clearEmailWriteState() { _emailWriteState.value = WriteState.Idle }
    fun clearDeploymentWriteState() { _deploymentWriteState.value = WriteState.Idle }

    // ---------------------------------------------------------------------
    // 绑定 Tab：资源选择器数据 + 新增/删除
    // ---------------------------------------------------------------------

    fun loadKvNamespaces() {
        val e = email; val k = apiKey; val a = accountId ?: return
        if (e == null || k == null) return
        viewModelScope.launch {
            val r = CloudflareDetailApi.listKvNamespaces(e, k, a)
            if (r.success) _kvNamespaces.value = r.data ?: emptyList()
        }
    }

    fun loadR2Buckets() {
        val e = email; val k = apiKey; val a = accountId ?: return
        if (e == null || k == null) return
        viewModelScope.launch {
            val r = CloudflareDetailApi.listR2Buckets(e, k, a)
            if (r.success) _r2Buckets.value = r.data ?: emptyList()
        }
    }

    fun loadD1Databases() {
        val e = email; val k = apiKey; val a = accountId ?: return
        if (e == null || k == null) return
        viewModelScope.launch {
            val r = CloudflareDetailApi.listD1Databases(e, k, a)
            if (r.success) _d1Databases.value = r.data ?: emptyList()
        }
    }

    /** 新增一个资源绑定（KV / R2 / D1 / Service） */
    fun addResourceBinding(scriptName: String, bindingName: String, type: String, resourceIdOrName: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _bindingWriteState.value = WriteState.Error("未登录"); return }
        if (bindingName.isBlank()) { _bindingWriteState.value = WriteState.Error("绑定名称不能为空"); return }
        val current = _settingsDetail.value?.bindings.orEmpty()
        viewModelScope.launch {
            _bindingWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.addResourceBinding(e, k, a, scriptName, current, bindingName, type, resourceIdOrName)
            if (result.success) {
                _bindingWriteState.value = WriteState.Success("已添加绑定")
                refreshSettings(scriptName)
            } else {
                _bindingWriteState.value = WriteState.Error(result.error ?: "添加失败")
            }
        }
    }

    /** 删除任意类型的绑定（变量/密钥/KV/R2/D1/Service 通用，按名称删） */
    fun deleteResourceBinding(scriptName: String, name: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _bindingWriteState.value = WriteState.Error("未登录"); return }
        val current = _settingsDetail.value?.bindings.orEmpty()
        viewModelScope.launch {
            _bindingWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.deleteBinding(e, k, a, scriptName, current, name)
            if (result.success) {
                _bindingWriteState.value = WriteState.Success("已删除")
                refreshSettings(scriptName)
            } else {
                _bindingWriteState.value = WriteState.Error(result.error ?: "删除失败")
            }
        }
    }

    // ---------------------------------------------------------------------
    // 域 Tab：Zone 选择器 + 自定义域名新增/删除
    // ---------------------------------------------------------------------

    fun loadZones() {
        val e = email; val k = apiKey; val a = accountId ?: return
        if (e == null || k == null) return
        viewModelScope.launch {
            val r = CloudflareDetailApi.listZones(e, k, a)
            if (r.success) _zones.value = r.data ?: emptyList()
        }
    }

    fun addCustomDomain(scriptName: String, hostname: String, zoneId: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _domainWriteState.value = WriteState.Error("未登录"); return }
        if (hostname.isBlank() || zoneId.isBlank()) { _domainWriteState.value = WriteState.Error("请填写主机名并选择 Zone"); return }
        viewModelScope.launch {
            _domainWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.addCustomDomain(e, k, a, scriptName, hostname, zoneId)
            if (result.success) {
                _domainWriteState.value = WriteState.Success("已添加域名")
                loadDetail(scriptName)
            } else {
                _domainWriteState.value = WriteState.Error(result.error ?: "添加失败")
            }
        }
    }

    fun deleteCustomDomain(scriptName: String, domainId: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _domainWriteState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _domainWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.deleteCustomDomain(e, k, a, domainId)
            if (result.success) {
                _domainWriteState.value = WriteState.Success("已删除")
                loadDetail(scriptName)
            } else {
                _domainWriteState.value = WriteState.Error(result.error ?: "删除失败")
            }
        }
    }

    // ---------------------------------------------------------------------
    // 触发事件：Queues 消费者
    // ---------------------------------------------------------------------

    fun loadQueues(scriptName: String) {
        val e = email; val k = apiKey; val a = accountId ?: return
        if (e == null || k == null) return
        viewModelScope.launch {
            val r = CloudflareDetailApi.listQueues(e, k, a, scriptName)
            if (r.success) _queues.value = r.data ?: emptyList()
        }
    }

    fun addQueueConsumer(scriptName: String, queueId: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _queueWriteState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _queueWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.addQueueConsumer(e, k, a, scriptName, queueId)
            if (result.success) {
                _queueWriteState.value = WriteState.Success("已添加")
                loadQueues(scriptName)
            } else {
                _queueWriteState.value = WriteState.Error(result.error ?: "添加失败")
            }
        }
    }

    fun deleteQueueConsumer(scriptName: String, queueId: String, consumerId: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _queueWriteState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _queueWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.deleteQueueConsumer(e, k, a, queueId, consumerId)
            if (result.success) {
                _queueWriteState.value = WriteState.Success("已删除")
                loadQueues(scriptName)
            } else {
                _queueWriteState.value = WriteState.Error(result.error ?: "删除失败")
            }
        }
    }

    // ---------------------------------------------------------------------
    // 触发事件：邮件路由规则
    // ---------------------------------------------------------------------

    fun loadEmailRules(scriptName: String, zoneId: String) {
        val e = email; val k = apiKey
        if (e == null || k == null) return
        viewModelScope.launch {
            val r = CloudflareDetailApi.listEmailRoutingRules(e, k, zoneId, scriptName)
            if (r.success) _emailRules.value = r.data ?: emptyList()
        }
    }

    fun addEmailRule(scriptName: String, zoneId: String, matchAddress: String) {
        val e = email; val k = apiKey
        if (e == null || k == null) { _emailWriteState.value = WriteState.Error("未登录"); return }
        if (matchAddress.isBlank() || zoneId.isBlank()) { _emailWriteState.value = WriteState.Error("请填写邮箱地址并选择 Zone"); return }
        viewModelScope.launch {
            _emailWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.addEmailRoutingRule(e, k, zoneId, scriptName, matchAddress)
            if (result.success) {
                _emailWriteState.value = WriteState.Success("已添加")
                loadEmailRules(scriptName, zoneId)
            } else {
                _emailWriteState.value = WriteState.Error(result.error ?: "添加失败")
            }
        }
    }

    fun deleteEmailRule(scriptName: String, zoneId: String, ruleId: String) {
        val e = email; val k = apiKey
        if (e == null || k == null) { _emailWriteState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _emailWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.deleteEmailRoutingRule(e, k, zoneId, ruleId)
            if (result.success) {
                _emailWriteState.value = WriteState.Success("已删除")
                loadEmailRules(scriptName, zoneId)
            } else {
                _emailWriteState.value = WriteState.Error(result.error ?: "删除失败")
            }
        }
    }

    // ---------------------------------------------------------------------
    // 部署：回滚
    // ---------------------------------------------------------------------

    fun rollbackDeployment(scriptName: String, versionId: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _deploymentWriteState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _deploymentWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.rollbackDeployment(e, k, a, scriptName, versionId)
            if (result.success) {
                _deploymentWriteState.value = WriteState.Success("已回滚")
                loadDetail(scriptName)
            } else {
                _deploymentWriteState.value = WriteState.Error(result.error ?: "回滚失败")
            }
        }
    }

    /** 新增或编辑一个 Runtime Variable / Secret */
    fun upsertVariable(
        scriptName: String,
        originalName: String?,
        newName: String,
        newValue: String,
        isSecret: Boolean
    ) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _variableWriteState.value = WriteState.Error("未登录"); return }
        if (newName.isBlank()) { _variableWriteState.value = WriteState.Error("变量名不能为空"); return }
        val current = _settingsDetail.value?.bindings.orEmpty()
        viewModelScope.launch {
            _variableWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.upsertVariable(
                e, k, a, scriptName, current, originalName, newName, newValue, isSecret
            )
            if (result.success) {
                _variableWriteState.value = WriteState.Success(if (originalName == null) "已添加变量" else "已保存")
                refreshSettings(scriptName)
            } else {
                _variableWriteState.value = WriteState.Error(result.error ?: "保存失败")
            }
        }
    }

    /** 删除一个变量 / 绑定 */
    fun deleteVariable(scriptName: String, name: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _variableWriteState.value = WriteState.Error("未登录"); return }
        val current = _settingsDetail.value?.bindings.orEmpty()
        viewModelScope.launch {
            _variableWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.deleteBinding(e, k, a, scriptName, current, name)
            if (result.success) {
                _variableWriteState.value = WriteState.Success("已删除")
                refreshSettings(scriptName)
            } else {
                _variableWriteState.value = WriteState.Error(result.error ?: "删除失败")
            }
        }
    }

    /** 可观察性：日志/跟踪开关 + 采样比例 */
    fun updateObservability(scriptName: String, enabled: Boolean, samplingPercent: Double) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _observabilityWriteState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _observabilityWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.updateObservability(e, k, a, scriptName, enabled, samplingPercent)
            if (result.success) {
                _observabilityWriteState.value = WriteState.Success("已保存")
                refreshSettings(scriptName)
            } else {
                _observabilityWriteState.value = WriteState.Error(result.error ?: "保存失败")
            }
        }
    }

    /** 运行时设置：兼容日期 / 兼容性标志 / placement */
    fun updateRuntimeSettings(
        scriptName: String,
        compatibilityDate: String,
        compatibilityFlags: List<String>,
        placementMode: String
    ) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _runtimeWriteState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _runtimeWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.updateRuntimeSettings(
                e, k, a, scriptName, compatibilityDate, compatibilityFlags, placementMode
            )
            if (result.success) {
                _runtimeWriteState.value = WriteState.Success("已保存")
                refreshSettings(scriptName)
            } else {
                _runtimeWriteState.value = WriteState.Error(result.error ?: "保存失败")
            }
        }
    }

    /** 新增一个 Cron 触发器（整体覆盖提交，自动带上已有的） */
    fun addCronTrigger(scriptName: String, cron: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _cronWriteState.value = WriteState.Error("未登录"); return }
        if (cron.isBlank()) { _cronWriteState.value = WriteState.Error("Cron 表达式不能为空"); return }
        val merged = _cronTriggers.value + cron
        viewModelScope.launch {
            _cronWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.updateSchedules(e, k, a, scriptName, merged)
            if (result.success) {
                _cronTriggers.value = merged
                _cronWriteState.value = WriteState.Success("已添加")
            } else {
                _cronWriteState.value = WriteState.Error(result.error ?: "保存失败")
            }
        }
    }

    /** 删除一个 Cron 触发器 */
    fun deleteCronTrigger(scriptName: String, cron: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _cronWriteState.value = WriteState.Error("未登录"); return }
        val remaining = _cronTriggers.value.filterNot { it == cron }
        viewModelScope.launch {
            _cronWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.updateSchedules(e, k, a, scriptName, remaining)
            if (result.success) {
                _cronTriggers.value = remaining
                _cronWriteState.value = WriteState.Success("已删除")
            } else {
                _cronWriteState.value = WriteState.Error(result.error ?: "删除失败")
            }
        }
    }

    /** 删除 Worker（危险操作，成功后由调用方负责导航返回列表页） */
    fun deleteWorker(scriptName: String, onSuccess: () -> Unit) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _deleteWorkerState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _deleteWorkerState.value = WriteState.Loading
            val result = CloudflareDetailApi.deleteWorker(e, k, a, scriptName)
            if (result.success) {
                _deleteWorkerState.value = WriteState.Success("已删除")
                loadApps()
                onSuccess()
            } else {
                _deleteWorkerState.value = WriteState.Error(result.error ?: "删除失败")
            }
        }
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

    /**
     * 用户选中文件后调用：只解析文件名，进入 Selected 状态，弹出确认对话框。
     * 不立即上传。
     */
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
        } catch (_: Exception) { /* 保持默认名 */ }
        // App 层兜底：仅允许 .js（应对各厂商 MIME 识别不一致）
        if (!fileName.endsWith(".js", ignoreCase = true) &&
            !fileName.endsWith(".mjs", ignoreCase = true)
        ) {
            _uploadState.value = UploadState.Error("仅支持上传 .js / .mjs 文件")
            return
        }
        _uploadState.value = UploadState.Selected(uri, fileName)
    }

    /**
     * 用户在确认对话框点「确认」后调用：真正开始上传。
     */
    fun confirmUploadScript(scriptName: String, context: Context) {
        val selected = _uploadState.value as? UploadState.Selected ?: return
        doUploadScript(scriptName, selected.uri, selected.fileName, context)
    }

    /**
     * 真正执行上传并部署 Worker 脚本
     */
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
                    // 刷新详情（部署列表等）
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
        }
    }
}

class MainViewModelFactory(private val tokenStore: TokenStore) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(tokenStore) as T
    }
}
