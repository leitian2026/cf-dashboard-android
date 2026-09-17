package com.leitian.cfdashboard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leitian.cfdashboard.data.*
import com.leitian.cfdashboard.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerDetailScreen(
    appId: String,
    appName: String,
    isPages: Boolean = false,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("概述", "指标", "部署", "绑定", "Observability", "域", "Access", "设置")

    val metrics by viewModel.metrics.collectAsState()
    val scriptInfo by viewModel.scriptInfo.collectAsState()
    val metricsLoading by viewModel.metricsLoading.collectAsState()
    val metricsError by viewModel.metricsError.collectAsState()
    val tabLoading by viewModel.tabLoading.collectAsState()

    val deployments by viewModel.deployments.collectAsState()
    val deploymentsError by viewModel.deploymentsError.collectAsState()
    val domains by viewModel.domains.collectAsState()
    val domainsError by viewModel.domainsError.collectAsState()
    val accessApps by viewModel.accessApps.collectAsState()
    val accessError by viewModel.accessError.collectAsState()
    val settingsDetail by viewModel.settingsDetail.collectAsState()
    val settingsError by viewModel.settingsError.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }
    var showPagesUnsupported by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.onScriptSelected(it, context) }
    }

    LaunchedEffect(appName) { viewModel.loadDetail(appName) }
    DisposableEffect(Unit) { onDispose { viewModel.clearDetail() } }

    if (uploadState is MainViewModel.UploadState.Selected) {
        val selected = uploadState as MainViewModel.UploadState.Selected
        AlertDialog(
            onDismissRequest = { viewModel.clearUploadState() },
            title = { Text("确认上传") },
            text = { Text("确认上传 ${selected.fileName} 并部署到「$appName」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmUploadScript(appName, context)
                }) { Text("确认上传") }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appName, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("上传文件部署") },
                                onClick = {
                                    menuExpanded = false
                                    if (isPages) {
                                        showPagesUnsupported = true
                                    } else {
                                        // 只声明 JS 相关 MIME；保留 text/plain 兼容部分厂商把 .js 标成 plain
                                        filePicker.launch(arrayOf(
                                            "application/javascript",
                                            "text/javascript",
                                            "text/plain"
                                        ))
                                    }
                                }
                            )
                        }
                    }
                    if (uploadState is MainViewModel.UploadState.Loading) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp).padding(end = 12.dp),
                            strokeWidth = 2.dp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> OverviewTab(appName, scriptInfo, metrics, metricsLoading, metricsError)
                1 -> MetricsTab(metrics, metricsLoading, metricsError)
                2 -> DeploymentsTab(deployments, deploymentsError, tabLoading)
                3 -> BindingsTab(scriptInfo, settingsDetail)
                4 -> ObservabilityTab(scriptInfo)
                5 -> DomainsTab(domains, domainsError, tabLoading)
                6 -> AccessTab(accessApps, accessError, tabLoading)
                7 -> SettingsTab(settingsDetail, settingsError, tabLoading)
            }
        }
    }
}

@Composable
private fun OverviewTab(
    appName: String,
    scriptInfo: CloudflareApi.ScriptInfo?,
    metrics: CloudflareApi.WorkerMetrics?,
    metricsLoading: Boolean,
    metricsError: String?
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val domain = scriptInfo?.subdomain ?: "$appName.workers.dev"
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🌐", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(domain, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                    Text("Automatic deployment on upload.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        BindingDiagram(appName, scriptInfo?.bindingCount ?: 0, scriptInfo?.logsEnabled == true, scriptInfo?.tracesEnabled == true)
        MetricsSummary(metrics, metricsLoading, metricsError)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetricsTab(metrics: CloudflareApi.WorkerMetrics?, metricsLoading: Boolean, metricsError: String?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("指标", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFF0F0F0)) {
                Text("今日（UTC）", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.weight(1f))
            if (metricsLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (metricsError != null) Text(metricsError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        MetricsSummary(metrics, metricsLoading, metricsError)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetricsSummary(metrics: CloudflareApi.WorkerMetrics?, metricsLoading: Boolean, metricsError: String?) {
    val m = metrics
    val reqValue = when {
        metricsLoading && m == null -> "..."
        m != null -> CloudflareApi.formatCount(m.totalRequests)
        else -> "0"
    }
    val cpuValue = when {
        metricsLoading && m == null -> "..."
        m != null -> CloudflareApi.formatCpu(m.cpuTimeMs)
        else -> "0 ms"
    }
    val errValue = when {
        metricsLoading && m == null -> "..."
        m != null -> m.totalErrors.toString()
        else -> "0"
    }
    MetricCard("调用次数", reqValue, m?.requestPoints ?: emptyList(), Color(0xFF3B82F6))
    MetricCard("CPU 时间", cpuValue, m?.cpuPoints ?: emptyList(), Color(0xFF3B82F6))
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("错误", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(errValue, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Spacer(Modifier.height(12.dp))
            if (m != null && m.errorPoints.isNotEmpty()) {
                SimpleLineChart(m.errorPoints, Color(0xFFEF4444), Modifier.fillMaxWidth().height(40.dp))
            } else {
                Box(Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFFAFAFA), RoundedCornerShape(4.dp)))
            }
        }
    }
}

@Composable
private fun DeploymentsTab(items: List<DeploymentItem>, error: String?, loading: Boolean) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("部署", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        if (error != null) {
            item { Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }
        if (!loading && items.isEmpty() && error == null) {
            item { Text("暂无部署记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(items, key = { it.id }) { d ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.createdOn, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        if (d.isLatest) {
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFE6F4EA)) {
                                Text("当前", fontSize = 11.sp, color = Color(0xFF137333),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("来源: ${d.source} · 作者: ${d.authorEmail}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (d.message.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(d.message, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Version: ${d.versionId}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DomainsTab(items: List<DomainItem>, error: String?, loading: Boolean) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("域", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        if (error != null) {
            item { Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }
        if (!loading && items.isEmpty() && error == null) {
            item { Text("暂无自定义域名", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(items, key = { it.id }) { d ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(d.hostname, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(4.dp))
                    Text("环境: ${d.environment} · 服务: ${d.service}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AccessTab(items: List<AccessAppItem>, error: String?, loading: Boolean) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Access", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        item {
            Text(
                "账号级 Access Applications（不一定绑定当前 Worker）",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (error != null) {
            item { Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }
        if (!loading && items.isEmpty() && error == null) {
            item { Text("暂无 Access 应用", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(items, key = { it.id }) { a ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(a.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("${a.domain} · ${a.type}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(detail: WorkerSettingsDetail?, error: String?, loading: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("设置", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        if (detail == null && !loading) {
            Text("暂无设置数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        detail ?: return

        SettingsRow("兼容性日期", detail.compatibilityDate)
        SettingsRow("用量模型", detail.usageModel)
        SettingsRow("Placement", detail.placementMode)
        SettingsRow("Logpush", if (detail.logpush) "已启用" else "已禁用")
        if (detail.tags.isNotEmpty()) {
            SettingsRow("Tags", detail.tags.joinToString(", "))
        }

        Text("Bindings (${detail.bindings.size})", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        detail.bindings.forEach { b ->
            Text("${b.name} (${b.type})", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, fontSize = 14.sp)
    }
}

@Composable
private fun BindingsTab(scriptInfo: CloudflareApi.ScriptInfo?, settingsDetail: WorkerSettingsDetail?) {
    val bindings = settingsDetail?.bindings.orEmpty()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("绑定", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        if (bindings.isEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("暂无绑定", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(bindings, key = { it.name }) { b ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(b.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(b.type, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ObservabilityTab(scriptInfo: CloudflareApi.ScriptInfo?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Observability", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Logs", Modifier.weight(1f), fontSize = 14.sp)
                    Text(if (scriptInfo?.logsEnabled == true) "已启用" else "未启用", fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Traces", Modifier.weight(1f), fontSize = 14.sp)
                    Text(if (scriptInfo?.tracesEnabled == true) "已启用" else "未启用", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun BindingDiagram(appName: String, bindingCount: Int, logs: Boolean, traces: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("绑定关系", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFE8F0FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("W", fontWeight = FontWeight.Bold, color = Color(0xFF1A73E8))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(appName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("$bindingCount bindings · Logs: ${if (logs) "on" else "off"} · Traces: ${if (traces) "on" else "off"}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, points: List<Float>, color: Color) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Spacer(Modifier.height(12.dp))
            if (points.isNotEmpty()) {
                SimpleLineChart(points, color, Modifier.fillMaxWidth().height(40.dp))
            } else {
                Box(Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFFAFAFA), RoundedCornerShape(4.dp)))
            }
        }
    }
}

@Composable
private fun SimpleLineChart(points: List<Float>, color: Color, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    Canvas(modifier) {
        val maxV = points.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v / maxV) * size.height * 0.9f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}
