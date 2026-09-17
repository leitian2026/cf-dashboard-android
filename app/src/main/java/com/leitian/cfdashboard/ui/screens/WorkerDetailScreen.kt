package com.leitian.cfdashboard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leitian.cfdashboard.data.*
import com.leitian.cfdashboard.ui.components.*
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
                TextButton(onClick = { viewModel.confirmUploadScript(appName, context) }) { Text("确认上传") }
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
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("上传文件部署") },
                                onClick = {
                                    menuExpanded = false
                                    if (isPages) showPagesUnsupported = true
                                    else filePicker.launch(arrayOf("application/javascript", "text/javascript", "text/plain"))
                                }
                            )
                        }
                    }
                    if (uploadState is MainViewModel.UploadState.Loading) {
                        CircularProgressIndicator(Modifier.size(20.dp).padding(end = 12.dp), strokeWidth = 2.dp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
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
                            Text(title, fontSize = 13.sp, fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    )
                }
            }
            when (selectedTab) {
                0 -> OverviewTab(appName, scriptInfo, metrics, metricsLoading, metricsError, domains)
                1 -> MetricsTab(metrics, metricsLoading, metricsError, deployments)
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
    metricsError: String?,
    domains: List<DomainItem>
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val domain = scriptInfo?.subdomain ?: "$appName.workers.dev"
        Column(Modifier.fillMaxWidth().border(1.dp, CfColors.Border, RoundedCornerShape(8.dp)).padding(14.dp)) {
            Text(domain, color = CfColors.Link, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text("Automatic deployment on upload.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CfSectionTitle("绑定关系")
        DottedCanvasBackground(Modifier.fillMaxWidth().height(160.dp)) {
            Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TopologyNode("域", badge = domains.size.coerceAtLeast(1).toString())
                    TopologyNode("Workers", badge = "1")
                    TopologyNode("绑定", badge = (scriptInfo?.bindingCount ?: 0).toString())
                }
                Canvas(Modifier.width(40.dp).fillMaxHeight()) {
                    val cy = size.height / 2
                    drawLine(CfColors.NodeBorder, Offset(0f, cy), Offset(size.width, cy), 2f)
                }
                Column(horizontalAlignment = Alignment.End) {
                    TopologyNode(appName, subtitle = "Worker")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(if (scriptInfo?.logsEnabled == true) "Logs 已启用" else "Logs 未启用", active = scriptInfo?.logsEnabled == true)
                        StatusPill(if (scriptInfo?.tracesEnabled == true) "Traces 已启用" else "Traces 未启用", active = scriptInfo?.tracesEnabled == true)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("指标 · 过去24小时", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            if (metricsLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        if (metricsError != null) Text(metricsError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        MetricSummaryRow(metrics, metricsLoading)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetricSummaryRow(metrics: CloudflareApi.WorkerMetrics?, loading: Boolean) {
    val m = metrics
    val req = when { loading && m == null -> "…"; m != null -> CloudflareApi.formatCount(m.totalRequests); else -> "0" }
    val cpu = when { loading && m == null -> "…"; m != null -> CloudflareApi.formatCpu(m.cpuTimeMs); else -> "0 ms" }
    val err = when { loading && m == null -> "…"; m != null -> m.totalErrors.toString(); else -> "0" }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrendMetricCard("调用次数", req, m?.requestPoints ?: emptyList(), Modifier.width(160.dp))
        TrendMetricCard("CPU 时间", cpu, m?.cpuPoints ?: emptyList(), Modifier.width(160.dp))
        TrendMetricCard("错误", err, m?.errorPoints ?: emptyList(), Modifier.width(160.dp), lineColor = Color(0xFFEF4444))
    }
}

@Composable
private fun MetricsTab(
    metrics: CloudflareApi.WorkerMetrics?,
    metricsLoading: Boolean,
    metricsError: String?,
    deployments: List<DeploymentItem>
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, CfColors.Border), color = Color.White) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("所有已部署版本", fontSize = 13.sp)
                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                }
            }
            Surface(shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, CfColors.Border), color = Color.White) {
                Text("过去24小时", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            Spacer(Modifier.weight(1f))
            if (metricsLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (metricsError != null) Text(metricsError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        MetricSummaryRow(metrics, metricsLoading)
        CfSectionTitle("可用部署")
        AvailableDeploymentsTable(deployments)
        CfSectionTitle("调用次数")
        Column(Modifier.fillMaxWidth().border(1.dp, CfColors.Border, RoundedCornerShape(8.dp)).padding(12.dp)) {
            var showBar by remember { mutableStateOf(true) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(CfColors.BarPurple, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    "过去24小时 · ${metrics?.let { CloudflareApi.formatCount(it.totalRequests) } ?: "0"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "柱状",
                    fontSize = 11.sp,
                    color = if (showBar) CfColors.Link else CfColors.GrayText,
                    fontWeight = if (showBar) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .border(1.dp, if (showBar) CfColors.Link else CfColors.Border, RoundedCornerShape(6.dp))
                        .background(if (showBar) CfColors.Link.copy(alpha = 0.12f) else Color.White, RoundedCornerShape(6.dp))
                        .clickable { showBar = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "曲线",
                    fontSize = 11.sp,
                    color = if (!showBar) CfColors.Link else CfColors.GrayText,
                    fontWeight = if (!showBar) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .border(1.dp, if (!showBar) CfColors.Link else CfColors.Border, RoundedCornerShape(6.dp))
                        .background(if (!showBar) CfColors.Link.copy(alpha = 0.12f) else Color.White, RoundedCornerShape(6.dp))
                        .clickable { showBar = false }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            if (showBar) {
                BarChart(values = metrics?.requestPoints ?: emptyList(), modifier = Modifier.fillMaxWidth().height(180.dp))
            } else {
                AreaSparkline(
                    points = metrics?.requestRatePoints ?: emptyList(),
                    lineColor = CfColors.BarPurple,
                    fillColor = CfColors.BarPurple.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AvailableDeploymentsTable(items: List<DeploymentItem>) {
    CfTable(header = {
        Text("版本 ID", Modifier.weight(1.2f), fontSize = 11.sp, color = CfColors.GrayText)
        Text("已部署", Modifier.weight(1.4f), fontSize = 11.sp, color = CfColors.GrayText)
        Text("流量", Modifier.weight(0.8f), fontSize = 11.sp, color = CfColors.GrayText)
    }) {
        if (items.isEmpty()) {
            Text("暂无部署", Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val shown = items.take(5)
            shown.forEachIndexed { index, d ->
                CfTableRow(showDivider = index != shown.lastIndex) {
                    MonoLinkText(d.versionId, Modifier.weight(1.2f))
                    Column(Modifier.weight(1.4f)) {
                        Text(d.createdOn, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (d.isLatest) { Spacer(Modifier.height(2.dp)); StatusPill("当前") }
                    }
                    Box(Modifier.weight(0.8f)) {
                        if (d.isLatest) ProgressWithLabel(1f, "100%") else ProgressWithLabel(0f, "0%")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeploymentsTab(items: List<DeploymentItem>, error: String?, loading: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CfSectionTitle("可用部署") { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
            Spacer(Modifier.height(8.dp))
            AvailableDeploymentsTable(items)
        }
        item {
            CfSectionTitle(title = "版本历史记录", subtitle = "按时间倒序显示最近的部署与配置变更。", trailing = {
                Icon(Icons.Default.Refresh, "刷新", Modifier.size(20.dp))
            })
        }
        if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        if (!loading && items.isEmpty() && error == null) item { Text("暂无部署记录", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        item {
            CfTable {
                items.forEachIndexed { index, d ->
                    CfTableRow(showDivider = index != items.lastIndex) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MonoLinkText(d.versionId)
                                if (d.isLatest) { Spacer(Modifier.width(8.dp)); StatusPill("当前") }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(d.message.ifBlank { "已手动部署" }, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SourceBadge(d.source)
                                Spacer(Modifier.width(8.dp))
                                Text("操作人 ${d.authorEmail}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(d.createdOn, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.MoreVert, "更多", tint = CfColors.GrayText, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        item { Text("显示 1-${items.size}/${items.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            CfSectionTitle("最近构建")
            Text("此 Worker 还没有构建", Modifier.fillMaxWidth().border(1.dp, CfColors.Border, RoundedCornerShape(8.dp)).padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DomainsTab(items: List<DomainItem>, error: String?, loading: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { CfSectionTitle("域") { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) } }
        if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        item {
            CfTable(header = {
                Text("主机名", Modifier.weight(1.4f), fontSize = 11.sp, color = CfColors.GrayText)
                Text("环境", Modifier.weight(0.8f), fontSize = 11.sp, color = CfColors.GrayText)
                Text("服务", Modifier.weight(1f), fontSize = 11.sp, color = CfColors.GrayText)
            }) {
                if (!loading && items.isEmpty() && error == null) {
                    Text("暂无自定义域名", Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    items.forEachIndexed { index, d ->
                        CfTableRow(showDivider = index != items.lastIndex) {
                            Text(d.hostname, Modifier.weight(1.4f), color = CfColors.Link, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(d.environment, Modifier.weight(0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(d.service, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessTab(items: List<AccessAppItem>, error: String?, loading: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CfSectionTitle(title = "Access", subtitle = "账号级 Access Applications（不一定绑定当前 Worker）") {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        item {
            CfTable(header = {
                Text("名称", Modifier.weight(1.2f), fontSize = 11.sp, color = CfColors.GrayText)
                Text("域名", Modifier.weight(1.2f), fontSize = 11.sp, color = CfColors.GrayText)
                Text("类型", Modifier.weight(0.7f), fontSize = 11.sp, color = CfColors.GrayText)
            }) {
                if (!loading && items.isEmpty() && error == null) {
                    Text("暂无 Access 应用", Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    items.forEachIndexed { index, a ->
                        CfTableRow(showDivider = index != items.lastIndex) {
                            Text(a.name, Modifier.weight(1.2f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(a.domain, Modifier.weight(1.2f), fontSize = 12.sp, color = CfColors.Link, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(a.type, Modifier.weight(0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(detail: WorkerSettingsDetail?, error: String?, loading: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CfSectionTitle("设置") { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        if (detail == null && !loading) { Text("暂无设置数据", color = MaterialTheme.colorScheme.onSurfaceVariant); return }
        val d = detail ?: return
        CfSectionTitle("Runtime variables and secrets")
        CfTable(header = {
            Text("类型", Modifier.weight(0.8f), fontSize = 11.sp, color = CfColors.GrayText)
            Text("名称", Modifier.weight(1f), fontSize = 11.sp, color = CfColors.GrayText)
            Text("值", Modifier.weight(1.2f), fontSize = 11.sp, color = CfColors.GrayText)
        }) {
            val vars = d.bindings.filter { it.type == "plain_text" || it.type == "secret_text" }
            if (vars.isEmpty()) {
                Text("暂无变量", Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                vars.forEachIndexed { index, b ->
                    CfTableRow(showDivider = index != vars.lastIndex) {
                        Text(if (b.type == "secret_text") "密钥" else "变量", Modifier.weight(0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(b.name, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(b.detail.ifBlank { "—" }, Modifier.weight(1.2f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        CfSectionTitle("可观察性")
        Column(Modifier.fillMaxWidth().border(1.dp, CfColors.Border, RoundedCornerShape(8.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Workers 日志 / 跟踪", Modifier.weight(1f), fontSize = 14.sp)
                StatusPill(if (d.observabilityEnabled) "已启用" else "未启用", active = d.observabilityEnabled)
            }
            Text("采样比例: ${(d.headSamplingRate * 100).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CfSectionTitle("运行时")
        Column(Modifier.fillMaxWidth().border(1.dp, CfColors.Border, RoundedCornerShape(8.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("兼容性日期: ${d.compatibilityDate}", fontSize = 13.sp)
            Text("兼容性标志: ${d.compatibilityFlags.joinToString(", ").ifBlank { "—" }}", fontSize = 13.sp)
            Text("放置: ${if (d.placementMode == "smart") "Smart Placement" else "默认"}", fontSize = 13.sp)
            Text("Usage Model: ${d.usageModel}", fontSize = 13.sp)
            Text("Logpush: ${if (d.logpush) "已启用" else "未启用"}", fontSize = 13.sp)
        }
        CfSectionTitle("触发事件")
        Column(Modifier.fillMaxWidth().border(1.dp, CfColors.Border, RoundedCornerShape(8.dp)).padding(12.dp)) {
            if (d.cronTriggers.isEmpty()) {
                Text("未配置 cron 触发器", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                d.cronTriggers.forEach { Text(it, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp) }
            }
        }
        CfSectionTitle("常规问题")
        Text("名称: 不可重命名", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BindingsTab(scriptInfo: CloudflareApi.ScriptInfo?, settingsDetail: WorkerSettingsDetail?) {
    val bindings = settingsDetail?.bindings.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { CfSectionTitle(title = "绑定", subtitle = "将资源连接到此 Worker。") }
        item {
            DottedCanvasBackground(Modifier.fillMaxWidth().height(140.dp)) {
                Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                    TopologyNode("Worker", subtitle = scriptInfo?.subdomain?.takeIf { it.isNotBlank() } ?: "script")
                    Canvas(Modifier.width(48.dp).height(2.dp)) {
                        drawLine(CfColors.NodeBorder, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f)
                    }
                    TopologyNode(
                        if (bindings.isEmpty()) "无绑定" else bindings.first().type,
                        subtitle = if (bindings.isEmpty()) null else bindings.first().name,
                        badge = if (bindings.isEmpty()) null else bindings.size.toString()
                    )
                }
            }
        }
        item {
            CfSectionTitle("已连接绑定")
            CfTable(header = {
                Text("类型", Modifier.weight(0.9f), fontSize = 11.sp, color = CfColors.GrayText)
                Text("名称", Modifier.weight(1f), fontSize = 11.sp, color = CfColors.GrayText)
                Text("值", Modifier.weight(1.2f), fontSize = 11.sp, color = CfColors.GrayText)
            }) {
                if (bindings.isEmpty()) {
                    Text("暂无绑定", Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    bindings.forEachIndexed { index, b ->
                        CfTableRow(showDivider = index != bindings.lastIndex) {
                            Text(b.type, Modifier.weight(0.9f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(b.name, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(b.detail.ifBlank { "—" }, Modifier.weight(1.2f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ObservabilityTab(scriptInfo: CloudflareApi.ScriptInfo?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CfSectionTitle("Observability")
        CfTable {
            CfTableRow {
                Text("Workers Logs", Modifier.weight(1f), fontSize = 14.sp)
                StatusPill(if (scriptInfo?.logsEnabled == true) "已启用" else "未启用", active = scriptInfo?.logsEnabled == true)
            }
            CfTableRow(showDivider = false) {
                Text("Workers Traces", Modifier.weight(1f), fontSize = 14.sp)
                StatusPill(if (scriptInfo?.tracesEnabled == true) "已启用" else "未启用", active = scriptInfo?.tracesEnabled == true)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
