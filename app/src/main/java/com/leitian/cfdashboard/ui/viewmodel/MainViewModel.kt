package com.leitian.cfdashboard.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.leitian.cfdashboard.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val tokenStore: TokenStore) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn: StateFlow<Boolean?> = _isLoggedIn.asStateFlow()

    // 已登录的所有账号——首页会把这些账号的 Workers/Pages 同时拉出来一起显示。
    private val _accounts = MutableStateFlow<List<SavedAccount>>(emptyList())
    val accounts: StateFlow<List<SavedAccount>> = _accounts.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _apps = MutableStateFlow<List<CloudflareApi.AppItem>>(emptyList())
    val apps: StateFlow<List<CloudflareApi.AppItem>> = _apps.asStateFlow()

    // 按账号分开统计——key 是 accountId，首页每个账号一张卡片区域。
    private val _accountStatsByAccount = MutableStateFlow<Map<String, CloudflareApi.AccountStats>>(emptyMap())
    val accountStatsByAccount: StateFlow<Map<String, CloudflareApi.AccountStats>> = _accountStatsByAccount.asStateFlow()

    private val _accountStatsErrorByAccount = MutableStateFlow<Map<String, String>>(emptyMap())
    val accountStatsErrorByAccount: StateFlow<Map<String, String>> = _accountStatsErrorByAccount.asStateFlow()

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

    private val _bindingWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val bindingWriteState: StateFlow<WriteState> = _bindingWriteState.asStateFlow()

    private val _kvNamespaces = MutableStateFlow<List<com.leitian.cfdashboard.data.KvNamespaceItem>>(emptyList())
    val kvNamespaces: StateFlow<List<com.leitian.cfdashboard.data.KvNamespaceItem>> = _kvNamespaces.asStateFlow()
    private val _r2Buckets = MutableStateFlow<List<com.leitian.cfdashboard.data.R2BucketItem>>(emptyList())
    val r2Buckets: StateFlow<List<com.leitian.cfdashboard.data.R2BucketItem>> = _r2Buckets.asStateFlow()
    private val _d1Databases = MutableStateFlow<List<com.leitian.cfdashboard.data.D1DatabaseItem>>(emptyList())
    val d1Databases: StateFlow<List<com.leitian.cfdashboard.data.D1DatabaseItem>> = _d1Databases.asStateFlow()

    private val _domainWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val domainWriteState: StateFlow<WriteState> = _domainWriteState.asStateFlow()
    private val _zones = MutableStateFlow<List<com.leitian.cfdashboard.data.ZoneItem>>(emptyList())
    val zones: StateFlow<List<com.leitian.cfdashboard.data.ZoneItem>> = _zones.asStateFlow()

    private val _queueWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val queueWriteState: StateFlow<WriteState> = _queueWriteState.asStateFlow()
    private val _queues = MutableStateFlow<List<com.leitian.cfdashboard.data.QueueItem>>(emptyList())
    val queues: StateFlow<List<com.leitian.cfdashboard.data.QueueItem>> = _queues.asStateFlow()

    private val _emailWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val emailWriteState: StateFlow<WriteState> = _emailWriteState.asStateFlow()
    private val _emailRules = MutableStateFlow<List<com.leitian.cfdashboard.data.EmailRoutingRuleItem>>(emptyList())
    val emailRules: StateFlow<List<com.leitian.cfdashboard.data.EmailRoutingRuleItem>> = _emailRules.asStateFlow()

    private val _deploymentWriteState = MutableStateFlow<WriteState>(WriteState.Idle)
    val deploymentWriteState: StateFlow<WriteState> = _deploymentWriteState.asStateFlow()

    sealed class UploadState {
        object Idle : UploadState()
        data class Selected(val uri: Uri, val fileName: String) : UploadState()
        object Loading : UploadState()
        data class Success(val message: String = "部署成功") : UploadState()
        data class Error(val message: String) : UploadState()
    }

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    sealed class DownloadState {
        object Idle : DownloadState()
        object Loading : DownloadState()
        data class Ready(val fileName: String, val bytes: ByteArray) : DownloadState()
        data class Success(val message: String) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    // "当前活跃账号"：详情页里各种读写操作（绑定、变量、域名、队列……）都是针对一个具体
    // Worker 的，而一个 Worker 只属于一个账号，所以这里维持单一套活跃凭据即可——
    // 进入详情页（loadDetail）或在首页选择账号创建 Worker 时，通过 selectAccount 切换到位。
    private var email: String? = null
    private var apiKey: String? = null
    private var accountId: String? = null
    private var accountName: String? = null

    // 当前详情页对应的 Worker/Pages 项目。loadDetail() 只记录这两个值、重置状态，
    // 不再一次性把 8 个标签的数据全部请求下来——具体某个标签的数据只在那个标签真正被
    // 点开时才去请求（见 loadTab / ensure* 系列函数），没点开的标签不联网。
    private var currentScriptName: String? = null
    private var currentIsPages: Boolean = false

    // 每种数据只在同一次详情页会话里成功加载一次；重复切换/重新展开同一个标签不会重复请求，
    // 只有 loadDetail()（换了个 Worker）或 forceReload()（写操作后主动刷新）会清掉重新拉。
    private val loadedDataTypes = mutableSetOf<String>()
    private val dataTypeJobs = mutableMapOf<String, Job>()

    // 某个标签正在展开时用户又快速切到别的标签：给 150ms 缓冲，真正停留够久的那个标签才发请求，
    // 划过的中间标签不会各自发一遍网络请求。
    private var loadTabJob: Job? = null

    init {
        viewModelScope.launch {
            val accounts = tokenStore.getAccounts()
            if (accounts.isNotEmpty()) {
                _accounts.value = accounts
                _isLoggedIn.value = true
                loadApps(); loadAccountStats()
            } else {
                _isLoggedIn.value = false
            }
        }
    }

    /** 把某个已登录账号设为当前活跃账号，供详情页/创建 Worker 等单账号操作使用。 */
    private fun selectAccount(targetAccountId: String): Boolean {
        val acc = _accounts.value.find { it.accountId == targetAccountId } ?: return false
        email = acc.email; apiKey = acc.apiKey; accountId = acc.accountId; accountName = acc.accountName
        return true
    }

    /** 添加一个账号登录（不会顶掉已经登录的其他账号）。同一个 account id 重复添加会覆盖旧凭据。 */
    fun addAccount(emailInput: String, keyInput: String, onResult: (Boolean) -> Unit = {}) {
        val e = emailInput.trim(); val k = keyInput.trim()
        if (e.isBlank() || k.isBlank()) { _loginError.value = "请输入邮箱和 Global API Key"; onResult(false); return }
        viewModelScope.launch {
            _isLoading.value = true; _loginError.value = null
            val result = CloudflareApi.verifyGlobalKey(e, k)
            if (result.success && result.data != null) {
                if (_accounts.value.any { it.accountId == result.data.id }) {
                    _loginError.value = "该账号已经登录过了"
                    _isLoading.value = false
                    onResult(false)
                    return@launch
                }
                val account = SavedAccount(e, k, result.data.id, result.data.name)
                tokenStore.addAccount(account)
                _accounts.value = tokenStore.getAccounts()
                _isLoggedIn.value = true
                loadApps(); loadAccountStats()
                _isLoading.value = false
                onResult(true)
            } else {
                _loginError.value = result.error ?: "登录失败"
                _isLoading.value = false
                onResult(false)
            }
        }
    }

    /** 移除单个已登录账号；如果移除的是最后一个账号，回到登录页。 */
    fun removeAccount(targetAccountId: String) {
        viewModelScope.launch {
            tokenStore.removeAccount(targetAccountId)
            val remaining = tokenStore.getAccounts()
            _accounts.value = remaining
            _apps.value = _apps.value.filterNot { it.accountId == targetAccountId }
            _accountStatsByAccount.value = _accountStatsByAccount.value - targetAccountId
            _accountStatsErrorByAccount.value = _accountStatsErrorByAccount.value - targetAccountId
            if (accountId == targetAccountId) { email = null; apiKey = null; accountId = null; accountName = null }
            if (remaining.isEmpty()) {
                _isLoggedIn.value = false
                _apps.value = emptyList(); clearDetail()
            }
        }
    }

    fun loadApps() {
        val accountsSnapshot = _accounts.value
        if (accountsSnapshot.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            // 每个账号的 Workers / Pages 各自独立请求，谁先回来就先把谁摆上去，
            // 不用等所有账号、所有接口都返回才一起显示。
            // 刷新时还没回来的部分，先拿上一次已有的数据垫着，避免列表先"掉一块"再补回来。
            val previous = _apps.value
            val workersByAccount = java.util.concurrent.ConcurrentHashMap<String, List<CloudflareApi.AppItem>>()
            val pagesByAccount = java.util.concurrent.ConcurrentHashMap<String, List<CloudflareApi.AppItem>>()
            fun publish() {
                val result = mutableListOf<CloudflareApi.AppItem>()
                for (acc in accountsSnapshot) {
                    val w = workersByAccount[acc.accountId] ?: previous.filter { it.accountId == acc.accountId && !it.isPages }
                    val p = pagesByAccount[acc.accountId] ?: previous.filter { it.accountId == acc.accountId && it.isPages }
                    // Cloudflare 接口本身不保证返回顺序稳定，这里固定按名字排序，
                    // 同一账号内 Workers 排在 Pages 前面，顺序就稳定下来了。
                    result += w.sortedBy { it.name.lowercase() }
                    result += p.sortedBy { it.name.lowercase() }
                }
                _apps.value = result
            }
            coroutineScope {
                for (acc in accountsSnapshot) {
                    launch {
                        val r = CloudflareApi.getWorkerScripts(acc.email, acc.apiKey, acc.accountId, acc.accountName)
                        workersByAccount[acc.accountId] = r.data ?: emptyList()
                        publish()
                    }
                    launch {
                        val r = CloudflareApi.getPagesProjects(acc.email, acc.apiKey, acc.accountId, acc.accountName)
                        pagesByAccount[acc.accountId] = r.data ?: emptyList()
                        publish()
                    }
                }
            }
            _isLoading.value = false
        }
    }

    fun loadAccountStats() {
        val accountsSnapshot = _accounts.value
        if (accountsSnapshot.isEmpty()) return
        viewModelScope.launch {
            // 逐个账号并发查询，各自的结果分开存，互不覆盖——一个账号查询失败不影响其他账号显示。
            val results = coroutineScope {
                accountsSnapshot.map { acc ->
                    async { acc to CloudflareApi.getAccountStats(acc.email, acc.apiKey, acc.accountId) }
                }.map { it.await() }
            }
            val statsMap = mutableMapOf<String, CloudflareApi.AccountStats>()
            val errorMap = mutableMapOf<String, String>()
            for ((acc, r) in results) {
                if (r.success && r.data != null) {
                    statsMap[acc.accountId] = r.data
                } else {
                    errorMap[acc.accountId] = r.error ?: "统计查询失败"
                }
            }
            _accountStatsByAccount.value = statsMap
            _accountStatsErrorByAccount.value = errorMap
        }
    }

    // accountId 默认取当前已经激活的账号——写操作后主动刷新数据时(比如回滚部署、
    // 增删自定义域名后)不用每次都重新传一遍，只有从首页第一次进入详情页时才需要显式传入。
    //
    // 这里只记录当前是哪个 Worker/Pages 项目、重置状态，不发任何网络请求——
    // 具体请求哪些数据完全由用户点开了哪个标签决定，见下面的 loadTab()。
    fun loadDetail(scriptName: String, isPages: Boolean = false, accountId: String = this.accountId ?: "") {
        if (accountId.isBlank() || !selectAccount(accountId)) {
            _metricsError.value = "账号信息丢失，请返回后重试"; return
        }
        if (email == null || apiKey == null) return
        // 换了个 Worker：把上一个 Worker 还没跑完的请求和"已加载"标记都清掉，
        // 不然新 Worker 的标签会误以为数据已经加载过、或者被旧请求的结果污染。
        loadTabJob?.cancel()
        dataTypeJobs.values.forEach { it.cancel() }
        dataTypeJobs.clear()
        loadedDataTypes.clear()
        currentScriptName = scriptName
        currentIsPages = isPages
        _metrics.value = null; _scriptInfo.value = null; _metricsError.value = null
        _deployments.value = emptyList(); _domains.value = emptyList(); _accessApps.value = emptyList()
        _settingsDetail.value = null; _deploymentsError.value = null; _domainsError.value = null
        _accessError.value = null; _settingsError.value = null; _cronTriggers.value = emptyList()
        _metricsLoading.value = false; _tabLoading.value = false
    }

    // 标签手风琴展开到 index 时调用（含应用重启后恢复到上次停留的标签）。
    // 只请求这个标签需要的数据；同一个 Worker 详情会话里，每种数据只会真正请求一次
    // （见 runOnce），来回切换标签、或者手指划过中间几个标签都不会重复发请求。
    fun loadTab(index: Int) {
        val scriptName = currentScriptName ?: return
        loadTabJob?.cancel()
        loadTabJob = viewModelScope.launch {
            // 快速连续切好几个标签时，只有真正停留住的那个会在 150ms 后走到这里发请求。
            delay(150)
            when (index) {
                0 -> { ensureScriptInfo(scriptName); ensureMetrics(scriptName); ensureDomains(scriptName) }
                1 -> { ensureMetrics(scriptName); ensureDeployments(scriptName) }
                2 -> ensureDeployments(scriptName)
                3 -> { ensureScriptInfo(scriptName); ensureSettings(scriptName) }
                4 -> ensureScriptInfo(scriptName)
                5 -> ensureDomains(scriptName)
                6 -> ensureAccess()
                7 -> { ensureSettings(scriptName); ensureCron(scriptName) }
            }
        }
    }

    /** type 这种数据在当前 Worker 详情会话里只会真正发起一次请求，正在跑或跑完了都直接跳过。 */
    private fun runOnce(type: String, block: suspend () -> Unit) {
        if (type in loadedDataTypes || dataTypeJobs.containsKey(type)) return
        dataTypeJobs[type] = viewModelScope.launch {
            try {
                block()
            } finally {
                loadedDataTypes += type
                dataTypeJobs.remove(type)
            }
        }
    }

    private fun ensureScriptInfo(scriptName: String) = runOnce("scriptInfo") {
        val e = email ?: return@runOnce; val k = apiKey ?: return@runOnce; val a = accountId ?: return@runOnce
        if (currentIsPages) {
            val r = CloudflareApi.getPagesProject(e, k, a, scriptName)
            if (r.success) _scriptInfo.value = r.data else _metricsError.value = r.error
            markPagesUnsupportedTabs()
        } else {
            val r = CloudflareApi.getScriptInfo(e, k, a, scriptName)
            if (r.success) {
                _scriptInfo.value = r.data
            } else {
                // 有些条目实际是 Pages 项目却出现在 Workers 列表里——兜底再试一次 Pages 接口。
                val pages = CloudflareApi.getPagesProject(e, k, a, scriptName)
                if (pages.success) {
                    currentIsPages = true
                    _scriptInfo.value = pages.data
                    markPagesUnsupportedTabs()
                } else {
                    _metricsError.value = r.error
                }
            }
        }
    }

    /** Pages 项目没有部署/域名/设置/Cron 这几个 Workers 专属接口，直接标记，不用真的去请求。 */
    private fun markPagesUnsupportedTabs() {
        _settingsError.value = "Pages 项目暂不支持 Workers 设置接口"
        _deploymentsError.value = "Pages 部署列表请使用 Cloudflare 控制台（当前版本未接 Pages Deployments API）"
        _domains.value = emptyList()
        loadedDataTypes += setOf("deployments", "domains", "settings", "cron")
    }

    private fun ensureMetrics(scriptName: String) = runOnce("metrics") {
        if (currentIsPages) return@runOnce
        val e = email ?: return@runOnce; val k = apiKey ?: return@runOnce; val a = accountId ?: return@runOnce
        _metricsLoading.value = true
        val r = CloudflareApi.getWorkerMetrics(e, k, a, scriptName)
        if (r.success) _metrics.value = r.data else _metricsError.value = r.error
        _metricsLoading.value = false
    }

    private fun ensureDeployments(scriptName: String) = runOnce("deployments") {
        if (currentIsPages) return@runOnce
        val e = email ?: return@runOnce; val k = apiKey ?: return@runOnce; val a = accountId ?: return@runOnce
        _tabLoading.value = true
        val r = CloudflareDetailApi.listDeployments(e, k, a, scriptName)
        if (r.success) _deployments.value = r.data ?: emptyList() else _deploymentsError.value = r.error
        _tabLoading.value = false
    }

    private fun ensureDomains(scriptName: String) = runOnce("domains") {
        if (currentIsPages) return@runOnce
        val e = email ?: return@runOnce; val k = apiKey ?: return@runOnce; val a = accountId ?: return@runOnce
        _tabLoading.value = true
        val r = CloudflareDetailApi.listDomains(e, k, a, scriptName)
        if (r.success) _domains.value = r.data ?: emptyList() else _domainsError.value = r.error
        _tabLoading.value = false
    }

    private fun ensureAccess() = runOnce("access") {
        if (currentIsPages) return@runOnce
        val e = email ?: return@runOnce; val k = apiKey ?: return@runOnce; val a = accountId ?: return@runOnce
        _tabLoading.value = true
        val r = CloudflareDetailApi.listAccessApps(e, k, a)
        if (r.success) _accessApps.value = r.data ?: emptyList() else _accessError.value = r.error
        _tabLoading.value = false
    }

    private fun ensureSettings(scriptName: String) = runOnce("settings") {
        if (currentIsPages) return@runOnce
        val e = email ?: return@runOnce; val k = apiKey ?: return@runOnce; val a = accountId ?: return@runOnce
        _tabLoading.value = true
        val r = CloudflareDetailApi.getSettingsDetail(e, k, a, scriptName)
        if (r.success) _settingsDetail.value = r.data else _settingsError.value = r.error
        _tabLoading.value = false
    }

    private fun ensureCron(scriptName: String) = runOnce("cron") {
        if (currentIsPages) return@runOnce
        val e = email ?: return@runOnce; val k = apiKey ?: return@runOnce; val a = accountId ?: return@runOnce
        val r = CloudflareDetailApi.listSchedules(e, k, a, scriptName)
        if (r.success) _cronTriggers.value = r.data ?: emptyList()
    }

    /**
     * 写操作（增删域名、回滚部署、上传新版本……）之后，重新拉某一种数据——但只对
     * 已经加载过（用户已经点开过对应标签）的类型生效：还没打开过的标签本来就没请求过，
     * 不用在这里提前把它拉过来，等用户真正点开时 ensure* 自然会请求到最新数据。
     */
    private fun forceReload(type: String) {
        if (type !in loadedDataTypes && type !in dataTypeJobs) return
        dataTypeJobs[type]?.cancel(); dataTypeJobs.remove(type)
        loadedDataTypes.remove(type)
        val scriptName = currentScriptName ?: return
        when (type) {
            "scriptInfo" -> ensureScriptInfo(scriptName)
            "metrics" -> ensureMetrics(scriptName)
            "deployments" -> ensureDeployments(scriptName)
            "domains" -> ensureDomains(scriptName)
            "access" -> ensureAccess()
            "settings" -> ensureSettings(scriptName)
            "cron" -> ensureCron(scriptName)
        }
    }

    private fun refreshSettings(scriptName: String) {
        val e = email ?: return; val k = apiKey ?: return; val a = accountId ?: return
        viewModelScope.launch {
            val set = CloudflareDetailApi.getSettingsDetail(e, k, a, scriptName)
            if (set.success) _settingsDetail.value = set.data else _settingsError.value = set.error
        }
    }

    /**
     * 手风琴同一时间只展开一个 Worker，但"收起旧的"和"展开新的"是两个独立 Compose 节点各自的
     * 生命周期事件——旧节点的 onDispose 有时会在新节点的 LaunchedEffect 之后才跑（尤其是切到
     * 列表里位置更靠前的另一个账号的 Worker 时）。而 currentScriptName/loadTabJob 这些状态是
     * ViewModel 单例共享的，如果旧节点的 dispose 晚到，会把刚展开的新 Worker 的状态/请求整个抹掉，
     * 表现就是"点另一个 Worker 后指标一直转圈、根本拉不到数据"。
     * 加一个 expectedScriptName 做校验：只有当共享状态确实还是"我"这个 Worker 的时候才真的清，
     * 如果已经被新 Worker 接管了，这次 dispose 就当没发生。
     */
    fun clearDetail(expectedScriptName: String? = null) {
        if (expectedScriptName != null && currentScriptName != expectedScriptName) return
        loadTabJob?.cancel(); loadTabJob = null
        dataTypeJobs.values.forEach { it.cancel() }; dataTypeJobs.clear()
        loadedDataTypes.clear()
        currentScriptName = null; currentIsPages = false
        _metrics.value = null; _scriptInfo.value = null; _metricsError.value = null
        _deployments.value = emptyList(); _domains.value = emptyList(); _accessApps.value = emptyList()
        _settingsDetail.value = null; _deploymentsError.value = null; _domainsError.value = null
        _accessError.value = null; _settingsError.value = null; _cronTriggers.value = emptyList()
        _uploadState.value = UploadState.Idle
        _variableWriteState.value = WriteState.Idle; _observabilityWriteState.value = WriteState.Idle
        _runtimeWriteState.value = WriteState.Idle; _cronWriteState.value = WriteState.Idle
        _deleteWorkerState.value = WriteState.Idle; _bindingWriteState.value = WriteState.Idle
        _domainWriteState.value = WriteState.Idle; _queueWriteState.value = WriteState.Idle
        _emailWriteState.value = WriteState.Idle; _deploymentWriteState.value = WriteState.Idle
        _kvNamespaces.value = emptyList(); _r2Buckets.value = emptyList(); _d1Databases.value = emptyList()
        _zones.value = emptyList(); _queues.value = emptyList(); _emailRules.value = emptyList()
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

    fun loadKvNamespaces() {
        val e = email; val k = apiKey; val a = accountId ?: return
        if (e == null || k == null) return
        viewModelScope.launch { val r = CloudflareDetailApi.listKvNamespaces(e, k, a); if (r.success) _kvNamespaces.value = r.data ?: emptyList() }
    }
    fun loadR2Buckets() {
        val e = email; val k = apiKey; val a = accountId ?: return
        if (e == null || k == null) return
        viewModelScope.launch { val r = CloudflareDetailApi.listR2Buckets(e, k, a); if (r.success) _r2Buckets.value = r.data ?: emptyList() }
    }
    fun loadD1Databases() {
        val e = email; val k = apiKey; val a = accountId ?: return
        if (e == null || k == null) return
        viewModelScope.launch { val r = CloudflareDetailApi.listD1Databases(e, k, a); if (r.success) _d1Databases.value = r.data ?: emptyList() }
    }

    fun addResourceBinding(scriptName: String, bindingName: String, type: String, resourceIdOrName: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _bindingWriteState.value = WriteState.Error("未登录"); return }
        if (bindingName.isBlank()) { _bindingWriteState.value = WriteState.Error("绑定名称不能为空"); return }
        val current = _settingsDetail.value?.bindings.orEmpty()
        viewModelScope.launch {
            _bindingWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.addResourceBinding(e, k, a, scriptName, current, bindingName, type, resourceIdOrName)
            if (result.success) { _bindingWriteState.value = WriteState.Success("已添加绑定"); refreshSettings(scriptName) }
            else _bindingWriteState.value = WriteState.Error(result.error ?: "添加失败")
        }
    }

    fun deleteResourceBinding(scriptName: String, name: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _bindingWriteState.value = WriteState.Error("未登录"); return }
        val current = _settingsDetail.value?.bindings.orEmpty()
        viewModelScope.launch {
            _bindingWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.deleteBinding(e, k, a, scriptName, current, name)
            if (result.success) { _bindingWriteState.value = WriteState.Success("已删除"); refreshSettings(scriptName) }
            else _bindingWriteState.value = WriteState.Error(result.error ?: "删除失败")
        }
    }

    fun loadZones() {
        val e = email; val k = apiKey; val a = accountId ?: return
        if (e == null || k == null) return
        viewModelScope.launch { val r = CloudflareDetailApi.listZones(e, k, a); if (r.success) _zones.value = r.data ?: emptyList() }
    }

    fun addCustomDomain(scriptName: String, hostname: String, zoneId: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _domainWriteState.value = WriteState.Error("未登录"); return }
        if (hostname.isBlank() || zoneId.isBlank()) { _domainWriteState.value = WriteState.Error("请填写主机名并选择 Zone"); return }
        viewModelScope.launch {
            _domainWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.addCustomDomain(e, k, a, scriptName, hostname, zoneId)
            if (result.success) { _domainWriteState.value = WriteState.Success("已添加域名"); forceReload("domains") }
            else _domainWriteState.value = WriteState.Error(result.error ?: "添加失败")
        }
    }

    fun deleteCustomDomain(scriptName: String, domainId: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _domainWriteState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _domainWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.deleteCustomDomain(e, k, a, domainId)
            if (result.success) { _domainWriteState.value = WriteState.Success("已删除"); forceReload("domains") }
            else _domainWriteState.value = WriteState.Error(result.error ?: "删除失败")
        }
    }

    fun loadQueues(scriptName: String) {
        val e = email; val k = apiKey; val a = accountId ?: return
        if (e == null || k == null) return
        viewModelScope.launch { val r = CloudflareDetailApi.listQueues(e, k, a, scriptName); if (r.success) _queues.value = r.data ?: emptyList() }
    }

    fun addQueueConsumer(scriptName: String, queueId: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _queueWriteState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _queueWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.addQueueConsumer(e, k, a, scriptName, queueId)
            if (result.success) { _queueWriteState.value = WriteState.Success("已添加"); loadQueues(scriptName) }
            else _queueWriteState.value = WriteState.Error(result.error ?: "添加失败")
        }
    }

    fun deleteQueueConsumer(scriptName: String, queueId: String, consumerId: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _queueWriteState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _queueWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.deleteQueueConsumer(e, k, a, queueId, consumerId)
            if (result.success) { _queueWriteState.value = WriteState.Success("已删除"); loadQueues(scriptName) }
            else _queueWriteState.value = WriteState.Error(result.error ?: "删除失败")
        }
    }

    fun loadEmailRules(scriptName: String, zoneId: String) {
        val e = email; val k = apiKey
        if (e == null || k == null) return
        viewModelScope.launch { val r = CloudflareDetailApi.listEmailRoutingRules(e, k, zoneId, scriptName); if (r.success) _emailRules.value = r.data ?: emptyList() }
    }

    fun addEmailRule(scriptName: String, zoneId: String, matchAddress: String) {
        val e = email; val k = apiKey
        if (e == null || k == null) { _emailWriteState.value = WriteState.Error("未登录"); return }
        if (matchAddress.isBlank() || zoneId.isBlank()) { _emailWriteState.value = WriteState.Error("请填写邮箱地址并选择 Zone"); return }
        viewModelScope.launch {
            _emailWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.addEmailRoutingRule(e, k, zoneId, scriptName, matchAddress)
            if (result.success) { _emailWriteState.value = WriteState.Success("已添加"); loadEmailRules(scriptName, zoneId) }
            else _emailWriteState.value = WriteState.Error(result.error ?: "添加失败")
        }
    }

    fun deleteEmailRule(scriptName: String, zoneId: String, ruleId: String) {
        val e = email; val k = apiKey
        if (e == null || k == null) { _emailWriteState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _emailWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.deleteEmailRoutingRule(e, k, zoneId, ruleId)
            if (result.success) { _emailWriteState.value = WriteState.Success("已删除"); loadEmailRules(scriptName, zoneId) }
            else _emailWriteState.value = WriteState.Error(result.error ?: "删除失败")
        }
    }

    fun rollbackDeployment(scriptName: String, versionId: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _deploymentWriteState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _deploymentWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.rollbackDeployment(e, k, a, scriptName, versionId)
            if (result.success) { _deploymentWriteState.value = WriteState.Success("已回滚"); forceReload("deployments") }
            else _deploymentWriteState.value = WriteState.Error(result.error ?: "回滚失败")
        }
    }

    fun upsertVariable(scriptName: String, originalName: String?, newName: String, newValue: String, isSecret: Boolean) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _variableWriteState.value = WriteState.Error("未登录"); return }
        if (newName.isBlank()) { _variableWriteState.value = WriteState.Error("变量名不能为空"); return }
        val current = _settingsDetail.value?.bindings.orEmpty()
        viewModelScope.launch {
            _variableWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.upsertVariable(e, k, a, scriptName, current, originalName, newName, newValue, isSecret)
            if (result.success) { _variableWriteState.value = WriteState.Success(if (originalName == null) "已添加变量" else "已保存"); refreshSettings(scriptName) }
            else _variableWriteState.value = WriteState.Error(result.error ?: "保存失败")
        }
    }

    fun deleteVariable(scriptName: String, name: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _variableWriteState.value = WriteState.Error("未登录"); return }
        val current = _settingsDetail.value?.bindings.orEmpty()
        viewModelScope.launch {
            _variableWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.deleteBinding(e, k, a, scriptName, current, name)
            if (result.success) { _variableWriteState.value = WriteState.Success("已删除"); refreshSettings(scriptName) }
            else _variableWriteState.value = WriteState.Error(result.error ?: "删除失败")
        }
    }

    fun updateObservability(scriptName: String, enabled: Boolean, samplingPercent: Double) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _observabilityWriteState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _observabilityWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.updateObservability(e, k, a, scriptName, enabled, samplingPercent)
            if (result.success) { _observabilityWriteState.value = WriteState.Success("已保存"); refreshSettings(scriptName) }
            else _observabilityWriteState.value = WriteState.Error(result.error ?: "保存失败")
        }
    }

    fun updateRuntimeSettings(scriptName: String, compatibilityDate: String, compatibilityFlags: List<String>, placementMode: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _runtimeWriteState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _runtimeWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.updateRuntimeSettings(e, k, a, scriptName, compatibilityDate, compatibilityFlags, placementMode)
            if (result.success) { _runtimeWriteState.value = WriteState.Success("已保存"); refreshSettings(scriptName) }
            else _runtimeWriteState.value = WriteState.Error(result.error ?: "保存失败")
        }
    }

    fun addCronTrigger(scriptName: String, cron: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _cronWriteState.value = WriteState.Error("未登录"); return }
        if (cron.isBlank()) { _cronWriteState.value = WriteState.Error("Cron 表达式不能为空"); return }
        val merged = _cronTriggers.value + cron
        viewModelScope.launch {
            _cronWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.updateSchedules(e, k, a, scriptName, merged)
            if (result.success) { _cronTriggers.value = merged; _cronWriteState.value = WriteState.Success("已添加") }
            else _cronWriteState.value = WriteState.Error(result.error ?: "保存失败")
        }
    }

    fun deleteCronTrigger(scriptName: String, cron: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _cronWriteState.value = WriteState.Error("未登录"); return }
        val remaining = _cronTriggers.value.filterNot { it == cron }
        viewModelScope.launch {
            _cronWriteState.value = WriteState.Loading
            val result = CloudflareDetailApi.updateSchedules(e, k, a, scriptName, remaining)
            if (result.success) { _cronTriggers.value = remaining; _cronWriteState.value = WriteState.Success("已删除") }
            else _cronWriteState.value = WriteState.Error(result.error ?: "删除失败")
        }
    }

    fun deleteWorker(scriptName: String, onSuccess: () -> Unit) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _deleteWorkerState.value = WriteState.Error("未登录"); return }
        viewModelScope.launch {
            _deleteWorkerState.value = WriteState.Loading
            val result = CloudflareDetailApi.deleteWorker(e, k, a, scriptName)
            if (result.success) { _deleteWorkerState.value = WriteState.Success("已删除"); loadApps(); onSuccess() }
            else _deleteWorkerState.value = WriteState.Error(result.error ?: "删除失败")
        }
    }

    fun clearCreateError() { _createError.value = null }
    fun clearUploadState() { _uploadState.value = UploadState.Idle }

    fun createWorker(accountId: String, name: String, onSuccess: () -> Unit) {
        if (!selectAccount(accountId)) { _createError.value = "请选择要在哪个账号下创建"; return }
        val e = email; val k = apiKey; val a = this.accountId
        if (e == null || k == null || a == null) { _createError.value = "未登录"; return }
        viewModelScope.launch {
            _createLoading.value = true; _createError.value = null
            val result = CloudflareApi.createWorker(e, k, a, name)
            if (result.success) { loadApps(); onSuccess() } else _createError.value = result.error ?: "创建失败"
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
        } catch (_: Exception) {}
        if (!fileName.endsWith(".js", ignoreCase = true) && !fileName.endsWith(".mjs", ignoreCase = true)) {
            _uploadState.value = UploadState.Error("仅支持上传 .js / .mjs 文件"); return
        }
        _uploadState.value = UploadState.Selected(uri, fileName)
    }

    fun confirmUploadScript(scriptName: String, context: Context) {
        val selected = _uploadState.value as? UploadState.Selected ?: return
        doUploadScript(scriptName, selected.uri, selected.fileName, context)
    }

    private fun doUploadScript(scriptName: String, uri: Uri, fileName: String, context: Context) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _uploadState.value = UploadState.Error("未登录"); return }
        viewModelScope.launch {
            _uploadState.value = UploadState.Loading
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) { _uploadState.value = UploadState.Error("无法读取文件或文件为空"); return@launch }
                val result = CloudflareApi.uploadWorkerScript(e, k, a, scriptName, fileName, bytes)
                if (result.success) {
                    _uploadState.value = UploadState.Success("部署成功：$fileName")
                    // 新版本上传后，脚本信息/部署列表/指标都可能变了，把这三个已加载过的标签强制刷新一遍；
                    // 没打开过的标签不用管，等用户真正点开的时候会自然去请求。
                    forceReload("scriptInfo"); forceReload("deployments"); forceReload("metrics")
                }
                else _uploadState.value = UploadState.Error(result.error ?: "上传失败")
            } catch (ex: Exception) {
                _uploadState.value = UploadState.Error(ex.message ?: "上传异常")
            }
        }
    }

    fun clearDownloadState() { _downloadState.value = DownloadState.Idle }

    /** 拉取 Worker 当前脚本源码；成功后进入 Ready 状态，由界面弹出"选择保存位置"的系统文件选择器 */
    fun downloadScript(scriptName: String) {
        val e = email; val k = apiKey; val a = accountId
        if (e == null || k == null || a == null) { _downloadState.value = DownloadState.Error("未登录"); return }
        viewModelScope.launch {
            _downloadState.value = DownloadState.Loading
            val result = CloudflareApi.downloadWorkerScript(e, k, a, scriptName)
            val data = result.data
            _downloadState.value = if (result.success && data != null) {
                DownloadState.Ready(data.first, data.second)
            } else {
                DownloadState.Error(result.error ?: "下载失败")
            }
        }
    }

    /** 用户在系统文件选择器里选好保存位置后，把已下载的字节写入该 Uri */
    fun saveDownloadedScript(uri: Uri, context: Context) {
        val ready = _downloadState.value as? DownloadState.Ready ?: return
        viewModelScope.launch {
            _downloadState.value = DownloadState.Loading
            try {
                val ok = context.contentResolver.openOutputStream(uri)?.use { it.write(ready.bytes) } != null
                _downloadState.value = if (ok) DownloadState.Success("已保存：${ready.fileName}")
                else DownloadState.Error("无法写入所选位置")
            } catch (ex: Exception) {
                _downloadState.value = DownloadState.Error(ex.message ?: "保存失败")
            }
        }
    }

    /** 退出全部账号。 */
    fun logout() {
        viewModelScope.launch {
            tokenStore.clear(); email = null; apiKey = null; accountId = null; accountName = null
            _accounts.value = emptyList()
            _isLoggedIn.value = false; _apps.value = emptyList()
            _accountStatsByAccount.value = emptyMap(); _accountStatsErrorByAccount.value = emptyMap()
            clearDetail()
        }
    }
}

class MainViewModelFactory(private val tokenStore: TokenStore) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(tokenStore) as T
}
