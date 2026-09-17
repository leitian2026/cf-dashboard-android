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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.leitian.cfdashboard.ui.viewmodel.WriteState

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
                7 -> SettingsTab(
                    appName = appName,
                    detail = settingsDetail,
                    error = settingsError,
                    loading = tabLoading,
                    viewModel = viewModel,
                    onWorkerDeleted = onBack
                )
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
private fun SettingsTab(
    appName: String,
    detail: WorkerSettingsDetail?,
    error: String?,
    loading: Boolean,
    viewModel: MainViewModel,
    onWorkerDeleted: () -> Unit
) {
    val variableWriteState by viewModel.variableWriteState.collectAsState()
    val observabilityWriteState by viewModel.observabilityWriteState.collectAsState()
    val runtimeWriteState by viewModel.runtimeWriteState.collectAsState()
    val cronWriteState by viewModel.cronWriteState.collectAsState()
    val deleteWorkerState by viewModel.deleteWorkerState.collectAsState()
    val cronTriggers by viewModel.cronTriggers.collectAsState()

    // 变量新增/编辑弹窗状态：null = 不显示；Pair(原名或null, 是否secret) 用于编辑回填
    var editingVariable by remember { mutableStateOf<BindingItem?>(null) }
    var showAddVariable by remember { mutableStateOf(false) }
    var deleteTargetVariable by remember { mutableStateOf<String?>(null) }

    var showAddCron by remember { mutableStateOf(false) }
    var deleteTargetCron by remember { mutableStateOf<String?>(null) }

    var showDeleteWorker by remember { mutableStateOf(false) }

    // 写操作结果提示（成功/失败都用同一个轻量 Snackbar 式 AlertDialog，跟现有上传部署的反馈风格保持一致）
    WriteResultDialog(variableWriteState, onDismiss = { viewModel.clearVariableWriteState() })
    WriteResultDialog(observabilityWriteState, onDismiss = { viewModel.clearObservabilityWriteState() })
    WriteResultDialog(runtimeWriteState, onDismiss = { viewModel.clearRuntimeWriteState() })
    WriteResultDialog(cronWriteState, onDismiss = { viewModel.clearCronWriteState() })
    if (deleteWorkerState is WriteState.Error) {
        WriteResultDialog(deleteWorkerState, onDismiss = { viewModel.clearDeleteWorkerState() })
    }

    if (showAddVariable || editingVariable != null) {
        VariableEditDialog(
            editing = editingVariable,
            loading = variableWriteState is WriteState.Loading,
            onDismiss = { showAddVariable = false; editingVariable = null },
            onConfirm = { name, value, isSecret ->
                viewModel.upsertVariable(appName, editingVariable?.name, name, value, isSecret)
                showAddVariable = false; editingVariable = null
            }
        )
    }

    if (deleteTargetVariable != null) {
        CfConfirmDangerDialog(
            title = "删除变量",
            message = "确定要删除变量「${deleteTargetVariable}」吗？此操作不可撤销。",
            loading = variableWriteState is WriteState.Loading,
            onConfirm = { viewModel.deleteVariable(appName, deleteTargetVariable!!); deleteTargetVariable = null },
            onDismiss = { deleteTargetVariable = null }
        )
    }

    if (showAddCron) {
        CronAddDialog(
            loading = cronWriteState is WriteState.Loading,
            onDismiss = { showAddCron = false },
            onConfirm = { cron -> viewModel.addCronTrigger(appName, cron); showAddCron = false }
        )
    }

    if (deleteTargetCron != null) {
        CfConfirmDangerDialog(
            title = "删除 Cron 触发器",
            message = "确定要删除「${deleteTargetCron}」这个 Cron 触发器吗？",
            loading = cronWriteState is WriteState.Loading,
            onConfirm = { viewModel.deleteCronTrigger(appName, deleteTargetCron!!); deleteTargetCron = null },
            onDismiss = { deleteTargetCron = null }
        )
    }

    if (showDeleteWorker) {
        CfConfirmDangerDialog(
            title = "删除 Worker",
            message = "此操作会永久删除「$appName」，无法恢复。",
            requireTypedName = appName,
            loading = deleteWorkerState is WriteState.Loading,
            onConfirm = { viewModel.deleteWorker(appName, onSuccess = onWorkerDeleted) },
            onDismiss = { showDeleteWorker = false }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CfSectionTitle("设置") { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        if (detail == null && !loading) { Text("暂无设置数据", color = MaterialTheme.colorScheme.onSurfaceVariant); return }
        val d = detail ?: return

        // ---------------- Runtime variables and secrets ----------------
        CfSectionTitle("Runtime variables and secrets") {
            TextButton(onClick = { showAddVariable = true }) { Text("＋ 添加变量") }
        }
        CfTable(header = {
            Text("类型", Modifier.weight(0.8f), fontSize = 11.sp, color = CfColors.GrayText)
            Text("名称", Modifier.weight(1f), fontSize = 11.sp, color = CfColors.GrayText)
            Text("值", Modifier.weight(1.2f), fontSize = 11.sp, color = CfColors.GrayText)
            Text("操作", Modifier.weight(0.8f), fontSize = 11.sp, color = CfColors.GrayText)
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
                        Row(Modifier.weight(0.8f)) {
                            IconButton(onClick = { editingVariable = b }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, "编辑", Modifier.size(16.dp))
                            }
                            IconButton(onClick = { deleteTargetVariable = b.name }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // ---------------- 可观察性 ----------------
        CfSectionTitle("可观察性")
        var obsEnabled by remember(d) { mutableStateOf(d.observabilityEnabled) }
        var samplingText by remember(d) { mutableStateOf((d.headSamplingRate * 100).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }) }
        Column(Modifier.fillMaxWidth().border(1.dp, CfColors.Border, RoundedCornerShape(8.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Workers 日志 / 跟踪", Modifier.weight(1f), fontSize = 14.sp)
                Switch(
                    checked = obsEnabled,
                    onCheckedChange = {
                        obsEnabled = it
                        viewModel.updateObservability(appName, it, samplingText.toDoubleOrNull() ?: 100.0)
                    },
                    enabled = observabilityWriteState !is WriteState.Loading
                )
            }
            HorizontalDivider(color = CfColors.Border)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("采样比例", Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = samplingText,
                    onValueChange = { samplingText = it.filter { c -> c.isDigit() || c == '.' } },
                    modifier = Modifier.width(90.dp),
                    singleLine = true,
                    trailingIcon = { Text("%", Modifier.padding(end = 8.dp)) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { viewModel.updateObservability(appName, obsEnabled, samplingText.toDoubleOrNull() ?: 100.0) },
                    enabled = observabilityWriteState !is WriteState.Loading
                ) { Text("保存") }
            }
        }

        // ---------------- 运行时 ----------------
        CfSectionTitle("运行时")
        var compatDate by remember(d) { mutableStateOf(d.compatibilityDate.takeIf { it != "—" } ?: "") }
        var compatFlags by remember(d) { mutableStateOf(d.compatibilityFlags.joinToString(",")) }
        var placement by remember(d) { mutableStateOf(d.placementMode) }
        var placementMenu by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().border(1.dp, CfColors.Border, RoundedCornerShape(8.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("兼容性日期", Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = compatDate,
                    onValueChange = { compatDate = it },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.width(150.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("兼容性标志", Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = compatFlags,
                    onValueChange = { compatFlags = it },
                    placeholder = { Text("逗号分隔") },
                    modifier = Modifier.width(150.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("放置", Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    OutlinedButton(onClick = { placementMenu = true }) {
                        Text(if (placement == "smart") "Smart Placement" else "默认")
                        Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = placementMenu, onDismissRequest = { placementMenu = false }) {
                        DropdownMenuItem(text = { Text("默认") }, onClick = { placement = "off"; placementMenu = false })
                        DropdownMenuItem(text = { Text("Smart Placement") }, onClick = { placement = "smart"; placementMenu = false })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        viewModel.updateRuntimeSettings(
                            appName, compatDate,
                            compatFlags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            placement
                        )
                    },
                    enabled = runtimeWriteState !is WriteState.Loading
                ) {
                    if (runtimeWriteState is WriteState.Loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("保存运行时设置")
                }
            }
        }

        // ---------------- 触发事件：Cron ----------------
        CfSectionTitle("触发事件") {
            TextButton(onClick = { showAddCron = true }) { Text("＋ 添加 Cron") }
        }
        Column(Modifier.fillMaxWidth().border(1.dp, CfColors.Border, RoundedCornerShape(8.dp)).padding(12.dp)) {
            if (cronTriggers.isEmpty()) {
                Text("未配置 cron 触发器", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                cronTriggers.forEachIndexed { index, cron ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(cron, Modifier.weight(1f), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp)
                        IconButton(onClick = { deleteTargetCron = cron }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (index != cronTriggers.lastIndex) HorizontalDivider(color = CfColors.Border)
                }
            }
        }

        // ---------------- 常规问题 ----------------
        CfSectionTitle("常规问题")
        CfTable {
            CfTableRow(showDivider = false) {
                Text("名称", Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Cloudflare 不支持重命名已创建的 Worker，这里保持禁用态展示而不是做假的可编辑框
                OutlinedTextField(
                    value = appName,
                    onValueChange = {},
                    enabled = false,
                    singleLine = true,
                    modifier = Modifier.width(180.dp)
                )
            }
        }

        // ---------------- 危险区域 ----------------
        CfSectionTitle("危险区域")
        Column(
            Modifier.fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("删除 Worker", Modifier.weight(1f), fontSize = 14.sp)
                Button(
                    onClick = { showDeleteWorker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 通用：写操作成功/失败的轻量反馈弹窗 */
@Composable
private fun WriteResultDialog(state: WriteState, onDismiss: () -> Unit) {
    when (state) {
        is WriteState.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("成功") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("好") } }
        )
        is WriteState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("失败") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("好") } }
        )
        else -> {}
    }
}

/** 新增 / 编辑 变量或密钥 的弹窗 */
@Composable
private fun VariableEditDialog(
    editing: BindingItem?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, value: String, isSecret: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var value by remember { mutableStateOf(if (editing?.type == "secret_text") "" else (editing?.detail ?: "")) }
    var isSecret by remember { mutableStateOf(editing?.type == "secret_text") }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(if (editing == null) "添加变量" else "编辑变量") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    enabled = editing == null, // 编辑时名称不可改，改名等价于删旧建新，避免歧义
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(if (isSecret) "值（留空则不修改原密钥）" else "值") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSecret, onCheckedChange = { isSecret = it }, enabled = editing == null)
                    Text("加密存储为 Secret（不可逆，页面上不会再显示明文）", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), value, isSecret) },
                enabled = !loading && name.isNotBlank()
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } }
    )
}

/** 添加 Cron 触发器的弹窗 */
@Composable
private fun CronAddDialog(loading: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var cron by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("添加 Cron 触发器") },
        text = {
            Column {
                OutlinedTextField(
                    value = cron,
                    onValueChange = { cron = it },
                    placeholder = { Text("例如：0 0 * * *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text("标准 5 段 Cron 表达式：分 时 日 月 周", fontSize = 11.sp, color = CfColors.GrayText)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(cron.trim()) }, enabled = !loading && cron.isNotBlank()) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("添加")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } }
    )
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
