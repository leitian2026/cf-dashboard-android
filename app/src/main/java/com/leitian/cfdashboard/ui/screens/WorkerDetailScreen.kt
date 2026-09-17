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
