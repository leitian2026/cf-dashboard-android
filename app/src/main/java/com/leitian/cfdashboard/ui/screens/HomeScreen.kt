package com.leitian.cfdashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leitian.cfdashboard.data.CloudflareApi
import com.leitian.cfdashboard.data.NetworkLogging
import com.leitian.cfdashboard.data.SavedAccount
import com.leitian.cfdashboard.data.TokenStore
import com.leitian.cfdashboard.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

// 统一的"紧凑行"高度：账号折叠头、统计数字框、搜索框都对齐到这个高度。
// Worker/Pages 列表项因为要放两行文字，高度会略高于这个值，但比改动前矮很多。
private val CompactRowHeight = 40.dp

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
    // 展开/收起都是原地进行，不再跳转到别的页面。
    var expandedAppKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (!collapsedLoaded) {
            collapsedAccountIds = ArrayList(tokenStore.getCollapsedAccountIds())
            collapsedLoaded = true
        }
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
            TopAppBar(
                title = {
                    Column {
                        Text("Workers 和 Pages", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "构建和部署无服务器功能、站点和全栈应用程序。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
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
                    Button(
                        onClick = {
                            viewModel.clearCreateError()
                            selectedCreateAccountId = accounts.firstOrNull()?.accountId ?: ""
                            showCreateDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("创建", fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                    Text(
                                        acc.accountName.ifBlank { acc.email },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    PeriodStats(
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

                item {
                    CompactSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it }
                    )
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
                            onToggle = { expandedAppKey = if (expandedAppKey == key) null else key },
                            onCollapse = { expandedAppKey = null },
                            viewModel = viewModel
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
                                        onToggle = { expandedAppKey = if (expandedAppKey == key) null else key },
                                        onCollapse = { expandedAppKey = null },
                                        viewModel = viewModel
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(CompactRowHeight).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
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
            Text("$count 个", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun PeriodStats(requests: String, cpu: String, errors: String, workersCount: String) {
    Column {
        Text("今日（UTC）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("请求", requests, Modifier.weight(1f))
            StatCard("CPU 时间", cpu, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("错误", errors, Modifier.weight(1f))
            StatCard("Workers 数量", workersCount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier.height(CompactRowHeight),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(CompactRowHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        "搜索应用程序",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 一个 Worker/Pages 条目 + 它展开时的详情内容。跟账号折叠一样：点条目原地展开/收起，
 * 不跳转到新页面；展开的排版是标签栏 + 可左右划动的内容（见 WorkerDetailContent）。
 */
@Composable
private fun AppRow(
    app: CloudflareApi.AppItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCollapse: () -> Unit,
    viewModel: MainViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppListItem(app = app, expanded = expanded, onClick = onToggle)
        if (expanded) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                WorkerDetailContent(
                    appName = app.name,
                    isPages = app.isPages,
                    accountId = app.accountId,
                    viewModel = viewModel,
                    onWorkerDeleted = onCollapse
                )
            }
        }
    }
}

@Composable
private fun AppListItem(app: CloudflareApi.AppItem, expanded: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().height(CompactRowHeight).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE8F0FE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(13.dp))
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
