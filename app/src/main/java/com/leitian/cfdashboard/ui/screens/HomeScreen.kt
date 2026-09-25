package com.leitian.cfdashboard.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leitian.cfdashboard.data.CloudflareApi
import com.leitian.cfdashboard.data.NetworkLogging
import com.leitian.cfdashboard.data.SavedAccount
import com.leitian.cfdashboard.data.TokenStore
import com.leitian.cfdashboard.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 统一的"紧凑行"高度：账号折叠头、搜索框都对齐到这个高度。
private val CompactRowHeight = 40.dp
// 统计数字卡片比紧凑行略高一点，好放下图标，看起来更有 App 的质感而不是网页表格。
private val StatCardHeight = 48.dp

// 每种统计指标一个强调色，图标用小色块装饰，弱化"纯文字表格"的网页感。
private val StatRequestsColor = Color(0xFF0F6E56)
private val StatCpuColor = Color(0xFF534AB7)
private val StatErrorColor = Color(0xFF993C1D)
private val StatWorkersColor = Color(0xFF185FA5)

// 卡片统一用的轻微投影，浅色主题下能和背景拉开一点层次。
private val CardElevation = 1.5.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onAddAccount: () -> Unit,
    onLogout: () -> Unit
) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statsByAccount by viewModel.accountStatsByAccount.collectAsState()
    val statsErrorByAccount by viewModel.accountStatsErrorByAccount.collectAsState()
    val createLoading by viewModel.createLoading.collectAsState()
    val createError by viewModel.createError.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    // 只登录了一个账号时，界面上不需要额外的账号标签/选择器，保持原来单账号时的简洁样子。
    val multiAccount = accounts.size > 1

    var searchQuery by remember { mutableStateOf("") }
    // 搜索框放到顶栏里，跟账号登录图标同一排；点搜索图标后顶栏标题切换成输入框。
    var searchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // 点击搜索图标展开输入框后，自动把光标定位进去并弹出键盘，不用用户再点一下。
    LaunchedEffect(searchActive) {
        if (searchActive) {
            delay(80)
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newWorkerName by remember { mutableStateOf("") }
    var showAccountsMenu by remember { mutableStateOf(false) }
    var createAccountMenuExpanded by remember { mutableStateOf(false) }
    var selectedCreateAccountId by remember { mutableStateOf("") }

    // 网络请求详细日志开关：默认关闭，遇到问题时手动打开，用完记得关掉
    // （响应体里可能带账户信息，别一直开着）。
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDebugMenu by remember { mutableStateOf(false) }
    var verboseLogging by remember { mutableStateOf(NetworkLogging.enabled) }
    LaunchedEffect(Unit) {
        NetworkLogging.enabledFlow(context).collect { verboseLogging = it }
    }

    // 已折叠的账号 id。持久化到 DataStore：重启 App 后保持上次的折叠/展开状态。
    // rememberSaveable 作为运行期的工作副本（进详情页再返回、旋转屏幕不会丢），
    // 冷启动时只从 DataStore 读一次，之后每次点击都写回去。
    val tokenStore = remember { TokenStore(context) }
    var collapsedAccountIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var collapsedLoaded by rememberSaveable { mutableStateOf(false) }
    // 当前展开详情的 Worker（同一时间只展开一个，跟点击进详情页一次只能看一个是一样的效果）。
    // 展开/收起都是原地进行，不再跳转到别的页面；跟账号折叠一样持久化到 DataStore，
    // 重启 App 后保持上次展开的那一个（如果它还在列表里）。
    var expandedAppKey by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedAppKeyLoaded by rememberSaveable { mutableStateOf(false) }
    // Worker 详情里停留的标签页下标：改成整个 App 唯一一份状态（不再按 Worker 分别记），
    // 不管展开哪个 Worker，看到的都是同一个"当前展开到哪个标签"的状态——
    // 比如上次在别的 Worker 里点开了"指标"，换一个 Worker 展开，也是直接停在"指标"。
    // null 表示"没有任何标签展开"；持久化到 DataStore 时用 -1 表示 null。
    var detailTabIndex by rememberSaveable { mutableStateOf<Int?>(0) }
    var detailTabIndexLoaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!collapsedLoaded) {
            collapsedAccountIds = ArrayList(tokenStore.getCollapsedAccountIds())
            collapsedLoaded = true
        }
        if (!expandedAppKeyLoaded) {
            expandedAppKey = tokenStore.getExpandedAppKey()
            expandedAppKeyLoaded = true
        }
        if (!detailTabIndexLoaded) {
            detailTabIndex = tokenStore.getDetailTabIndex().let { if (it < 0) null else it }
            detailTabIndexLoaded = true
        }
    }

    fun setExpandedAppKey(key: String?) {
        expandedAppKey = key
        scope.launch { tokenStore.setExpandedAppKey(key) }
    }

    fun setDetailTabIndex(index: Int?) {
        detailTabIndex = index
        scope.launch { tokenStore.setDetailTabIndex(index ?: -1) }
    }

    val filtered by remember(apps, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) apps
            else apps.filter {
                it.name.contains(searchQuery, true) ||
                    it.subtitle.contains(searchQuery, true) ||
                    it.domain.contains(searchQuery, true) ||
                    it.accountName.contains(searchQuery, true)
            }
        }
    }

    // 按账号分组（分组内保持原有顺序），组的顺序跟随已登录账号的顺序。
    val groupedApps = remember(filtered) { filtered.groupBy { it.accountId } }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!createLoading) {
                    showCreateDialog = false
                    newWorkerName = ""
                    viewModel.clearCreateError()
                }
            },
            title = { Text("创建 Worker") },
            text = {
                Column {
                    if (multiAccount) {
                        val selectedAccount = accounts.find { it.accountId == selectedCreateAccountId } ?: accounts.firstOrNull()
                        Text("创建到账号", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Box {
                            OutlinedButton(
                                onClick = { createAccountMenuExpanded = true },
                                enabled = !createLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    selectedAccount?.accountName?.takeIf { it.isNotBlank() } ?: selectedAccount?.email ?: "选择账号",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            DropdownMenu(expanded = createAccountMenuExpanded, onDismissRequest = { createAccountMenuExpanded = false }) {
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.accountName.ifBlank { acc.email }, fontSize = 13.sp) },
                                        onClick = {
                                            selectedCreateAccountId = acc.accountId
                                            createAccountMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    OutlinedTextField(
                        value = newWorkerName,
                        onValueChange = { newWorkerName = it },
                        label = { Text("Worker 名称") },
                        placeholder = { Text("my-worker") },
                        singleLine = true,
                        enabled = !createLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (createError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(createError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "名称只能包含字母、数字、下划线和短横线",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val accId = selectedCreateAccountId.ifBlank { accounts.firstOrNull()?.accountId ?: "" }
                        viewModel.createWorker(accId, newWorkerName) {
                            showCreateDialog = false
                            newWorkerName = ""
                            viewModel.clearCreateError()
                        }
                    },
                    enabled = !createLoading && newWorkerName.isNotBlank() && accounts.isNotEmpty()
                ) {
                    if (createLoading) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("创建")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        newWorkerName = ""
                        viewModel.clearCreateError()
                    },
                    enabled = !createLoading
                ) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
          // Surface 的 shadowElevation 让顶栏跟下面的内容区分出一条真实投影，
          // 而不是跟背景齐平的一整块网页布局。
          Surface(shadowElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
            TopAppBar(
                title = {
                    if (searchActive) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "搜索 Workers 和 Pages",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester)
                            )
                        }
                    } else {
                        Text("Workers 和 Pages", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                actions = {
                    if (searchActive) {
                        IconButton(onClick = {
                            searchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                        }
                    } else {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "搜索 Workers 和 Pages",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            IconButton(onClick = { showAccountsMenu = true }) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "账号",
                                    tint = if (multiAccount) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(expanded = showAccountsMenu, onDismissRequest = { showAccountsMenu = false }) {
                                Text(
                                    "已登录账号",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.width(220.dp)
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(acc.accountName.ifBlank { acc.email }, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                    Text(acc.email, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                IconButton(
                                                    onClick = { viewModel.removeAccount(acc.accountId) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "移除账号", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {}
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("添加账号") },
                                    leadingIcon = { Icon(Icons.Default.Add, null) },
                                    onClick = { showAccountsMenu = false; onAddAccount() }
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showDebugMenu = true }) {
                                Icon(
                                    Icons.Default.BugReport,
                                    contentDescription = "调试",
                                    tint = if (verboseLogging) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(expanded = showDebugMenu, onDismissRequest = { showDebugMenu = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.width(240.dp)
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text("详细网络日志", fontSize = 13.sp)
                                                Text(
                                                    "排查问题时打开，完整请求/响应会打到 Logcat",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Switch(
                                                checked = verboseLogging,
                                                onCheckedChange = { checked ->
                                                    verboseLogging = checked
                                                    scope.launch { NetworkLogging.setEnabled(context, checked) }
                                                }
                                            )
                                        }
                                    },
                                    onClick = {}
                                )
                            }
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Outlined.Logout, contentDescription = "退出全部账号")
                        }
                        IconButton(
                            onClick = {
                                viewModel.loadApps()
                                viewModel.loadAccountStats()
                            },
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新")
                            }
                        }
                        IconButton(
                            onClick = {
                                viewModel.clearCreateError()
                                selectedCreateAccountId = accounts.firstOrNull()?.accountId ?: ""
                                showCreateDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "创建", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
          }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (multiAccount) {
                    // 多账号：所有账号的"今日"统计并排放在同一行里，各占一列，
                    // 而不是像之前那样一个账号一整块、上下堆叠。
                    item(key = "stats-row") {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            accounts.forEach { acc ->
                                val accStats = statsByAccount[acc.accountId]
                                val accError = statsErrorByAccount[acc.accountId]
                                Column(Modifier.weight(1f)) {
                                    PeriodStats(
                                        header = "${acc.accountName.ifBlank { acc.email }} · 今日",
                                        requests = accStats?.let { CloudflareApi.formatCount(it.requests) } ?: "—",
                                        cpu = accStats?.let { CloudflareApi.formatCpu(it.cpuTimeMs) } ?: "—",
                                        errors = accStats?.errors?.toString() ?: "—",
                                        workersCount = apps.count { !it.isPages && it.accountId == acc.accountId }.toString()
                                    )
                                    if (accError != null) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "统计提示: $accError",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(accounts, key = { "stats:${it.accountId}" }) { acc ->
                        val accStats = statsByAccount[acc.accountId]
                        val accError = statsErrorByAccount[acc.accountId]
                        Column {
                            PeriodStats(
                                header = "今日（UTC）",
                                requests = accStats?.let { CloudflareApi.formatCount(it.requests) } ?: "—",
                                cpu = accStats?.let { CloudflareApi.formatCpu(it.cpuTimeMs) } ?: "—",
                                errors = accStats?.errors?.toString() ?: "—",
                                workersCount = apps.count { !it.isPages && it.accountId == acc.accountId }.toString()
                            )
                            if (accError != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "统计提示: $accError",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                if (isLoading && apps.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (filtered.isEmpty()) {
                    item {
                        Text(
                            if (searchQuery.isNotBlank()) "没有匹配的应用" else "暂无 Workers 或 Pages",
                            Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (!multiAccount) {
                    // 单账号：不需要分组标题，保持原来的简洁列表。
                    items(filtered, key = { "${it.accountId}:${it.id}:${it.isPages}" }) { app ->
                        val key = "${app.accountId}:${app.id}:${app.isPages}"
                        AppRow(
                            app = app,
                            expanded = expandedAppKey == key,
                            onToggle = { setExpandedAppKey(if (expandedAppKey == key) null else key) },
                            onCollapse = { setExpandedAppKey(null) },
                            viewModel = viewModel,
                            selectedTab = detailTabIndex,
                            onTabSelected = ::setDetailTabIndex
                        )
                    }
                } else {
                    val searching = searchQuery.isNotBlank()
                    accounts.forEach { acc ->
                        val groupApps = groupedApps[acc.accountId].orEmpty()
                        // 搜索时只显示有匹配结果的账号，并强制展开，避免命中的结果被折叠藏起来。
                        if (searching && groupApps.isEmpty()) return@forEach
                        val expanded = searching || acc.accountId !in collapsedAccountIds

                        item(key = "group:${acc.accountId}") {
                            AccountGroupHeader(
                                name = acc.accountName.ifBlank { acc.email },
                                email = acc.email,
                                count = groupApps.size,
                                expanded = expanded,
                                toggleEnabled = !searching,
                                onClick = {
                                    val updated = ArrayList(
                                        if (acc.accountId in collapsedAccountIds) collapsedAccountIds - acc.accountId
                                        else collapsedAccountIds + acc.accountId
                                    )
                                    collapsedAccountIds = updated
                                    scope.launch { tokenStore.setCollapsedAccountIds(updated) }
                                }
                            )
                        }
                        if (expanded) {
                            if (groupApps.isEmpty()) {
                                item(key = "empty:${acc.accountId}") {
                                    Text(
                                        "暂无 Workers 或 Pages",
                                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(groupApps, key = { "${it.accountId}:${it.id}:${it.isPages}" }) { app ->
                                    val key = "${app.accountId}:${app.id}:${app.isPages}"
                                    AppRow(
                                        app = app,
                                        expanded = expandedAppKey == key,
                                        onToggle = { setExpandedAppKey(if (expandedAppKey == key) null else key) },
                                        onCollapse = { setExpandedAppKey(null) },
                                        viewModel = viewModel,
                                        selectedTab = detailTabIndex,
                                        onTabSelected = ::setDetailTabIndex
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (isLoading && apps.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AccountGroupHeader(
    name: String,
    email: String,
    count: Int,
    expanded: Boolean,
    toggleEnabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(enabled = toggleEnabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation)
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 用首字母头像代替通用人形图标，几个账号之间更容易一眼区分。
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            // 名称和邮箱不同时，合并成一行显示（省略号截断），避免变成两行撑高整个框。
            Text(
                if (email.isNotBlank() && email != name) "$name（$email）" else name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("$count 个", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "折叠" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PeriodStats(header: String?, requests: String, cpu: String, errors: String, workersCount: String) {
    Column {
        if (header != null) {
            Text(header, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Icons.Outlined.SwapVert, StatRequestsColor, "请求", requests, Modifier.weight(1f))
            StatCard(Icons.Outlined.Bolt, StatCpuColor, "CPU 时间", cpu, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Icons.Outlined.ErrorOutline, StatErrorColor, "错误", errors, Modifier.weight(1f))
            StatCard(Icons.Outlined.Dns, StatWorkersColor, "Workers 数量", workersCount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier.height(StatCardHeight),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    value,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = tint
                )
            }
        }
    }
}

/**
 * 一个 Worker/Pages 条目 + 它展开时的详情内容。跟账号折叠一样：点条目原地展开/收起，
 * 不跳转到新页面，展开/收起都带一个平滑的高度动画；展开的排版是竖排的手风琴标签
 * （概述/指标/部署…，见 WorkerDetailContent）。上传/下载脚本的按钮就放在这一条折叠条上
 * （只在展开时显示），点了之后原地弹出确认/结果对话框，不需要再进到某个标签页里找。
 */
@Composable
private fun AppRow(
    app: CloudflareApi.AppItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCollapse: () -> Unit,
    viewModel: MainViewModel,
    selectedTab: Int? = null,
    onTabSelected: (Int?) -> Unit = {}
) {
    // 上传/下载相关的状态、系统文件选择器、确认/结果弹窗，都只在这一条"展开着"的时候才挂载——
    // 全部 Worker 共用同一份 viewModel 状态，如果每一行都订阅，收起的那些行也会一起弹出对话框。
    var onUploadClick: () -> Unit = {}
    var onDownloadClick: () -> Unit = {}
    var actionsBusy = false

    if (expanded) {
        val context = LocalContext.current
        val uploadState by viewModel.uploadState.collectAsState()
        val downloadState by viewModel.downloadState.collectAsState()
        var showPagesUnsupported by remember { mutableStateOf(false) }
        var showPagesDownloadUnsupported by remember { mutableStateOf(false) }

        val filePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let { viewModel.onScriptSelected(it, context) }
        }

        // 下载脚本时，把内容存到用户在系统文件选择器里挑选的位置
        val saveLocationPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/javascript")
        ) { uri: Uri? ->
            if (uri != null) viewModel.saveDownloadedScript(uri, context)
            else viewModel.clearDownloadState()
        }

        // 脚本下载完成（DownloadState.Ready）后，立即弹出"选择保存位置"的系统对话框
        LaunchedEffect(downloadState) {
            val ready = downloadState as? MainViewModel.DownloadState.Ready
            if (ready != null) saveLocationPicker.launch(ready.fileName)
        }

        if (uploadState is MainViewModel.UploadState.Selected) {
            val selected = uploadState as MainViewModel.UploadState.Selected
            AlertDialog(
                onDismissRequest = { viewModel.clearUploadState() },
                title = { Text("确认上传") },
                text = { Text("确认上传 ${selected.fileName} 并部署到「${app.name}」吗？") },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmUploadScript(app.name, context) }) { Text("确认上传") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearUploadState() }) { Text("取消") }
                }
            )
        }

        if (uploadState is MainViewModel.UploadState.Success || uploadState is MainViewModel.UploadState.Error) {
            val isSuccess = uploadState is MainViewModel.UploadState.Success
            val message = when (val s = uploadState) {
                is MainViewModel.UploadState.Success -> s.message
                is MainViewModel.UploadState.Error -> s.message
                else -> ""
            }
            AlertDialog(
                onDismissRequest = { viewModel.clearUploadState() },
                title = { Text(if (isSuccess) "部署成功" else "部署失败") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearUploadState() }) { Text("确定") }
                }
            )
        }

        if (showPagesUnsupported) {
            AlertDialog(
                onDismissRequest = { showPagesUnsupported = false },
                title = { Text("暂不支持") },
                text = { Text("Pages 项目部署流程较复杂，当前版本仅支持 Workers 单文件脚本上传。") },
                confirmButton = {
                    TextButton(onClick = { showPagesUnsupported = false }) { Text("知道了") }
                }
            )
        }

        if (showPagesDownloadUnsupported) {
            AlertDialog(
                onDismissRequest = { showPagesDownloadUnsupported = false },
                title = { Text("暂不支持") },
                text = { Text("Pages 项目没有单一脚本文件，当前版本仅支持下载 Workers 的脚本代码。") },
                confirmButton = {
                    TextButton(onClick = { showPagesDownloadUnsupported = false }) { Text("知道了") }
                }
            )
        }

        if (downloadState is MainViewModel.DownloadState.Success || downloadState is MainViewModel.DownloadState.Error) {
            val isSuccess = downloadState is MainViewModel.DownloadState.Success
            val message = when (val s = downloadState) {
                is MainViewModel.DownloadState.Success -> s.message
                is MainViewModel.DownloadState.Error -> s.message
                else -> ""
            }
            AlertDialog(
                onDismissRequest = { viewModel.clearDownloadState() },
                title = { Text(if (isSuccess) "下载完成" else "下载失败") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearDownloadState() }) { Text("确定") }
                }
            )
        }

        onUploadClick = {
            if (app.isPages) showPagesUnsupported = true
            else filePicker.launch(arrayOf("application/javascript", "text/javascript", "text/plain"))
        }
        onDownloadClick = {
            if (app.isPages) showPagesDownloadUnsupported = true
            else viewModel.downloadScript(app.name)
        }
        actionsBusy = uploadState is MainViewModel.UploadState.Loading || downloadState is MainViewModel.DownloadState.Loading
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppListItem(
            app = app,
            expanded = expanded,
            onClick = onToggle,
            showActions = expanded,
            actionsBusy = actionsBusy,
            onUploadClick = onUploadClick,
            onDownloadClick = onDownloadClick
        )
        // 用展开动画代替直接 if 判断显示/隐藏：卡片是从条目下方原地、渐渐撑开高度出现的，
        // 而不是点一下就整块"跳"出来；收起时同理是慢慢收回去。
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = CardElevation)
            ) {
                WorkerDetailContent(
                    appName = app.name,
                    isPages = app.isPages,
                    accountId = app.accountId,
                    viewModel = viewModel,
                    onWorkerDeleted = onCollapse,
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected
                )
            }
        }
    }
}

@Composable
private fun AppListItem(
    app: CloudflareApi.AppItem,
    expanded: Boolean,
    onClick: () -> Unit,
    showActions: Boolean = false,
    actionsBusy: Boolean = false,
    onUploadClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {}
) {
    // Pages 用紫色系图标、Worker 用蓝色系图标，跟"今日"卡片一样靠颜色而不是纯文字区分类型。
    val (icon, tint) = if (app.isPages) Icons.Outlined.Language to Color(0xFF534AB7)
                        else Icons.Outlined.Description to Color(0xFF185FA5)
    Card(
        Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(10.dp))
            // 只显示 Worker/Pages 名称，跟账号折叠头一样只占一行，高度也对齐到 CompactRowHeight。
            Text(
                app.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // 上传/下载脚本的入口就放在这条折叠条上，只在这个 Worker 展开时才显示，
            // 收起状态下不占地方，也不会跟一堆折叠箭头混在一起分不清是干什么用的。
            if (showActions) {
                if (actionsBusy) {
                    CircularProgressIndicator(Modifier.size(16.dp).padding(end = 6.dp), strokeWidth = 2.dp)
                }
                IconButton(onClick = onUploadClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.FileUpload, contentDescription = "上传文件部署", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDownloadClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.FileDownload, contentDescription = "下载代码", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(32.dp))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "折叠" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
