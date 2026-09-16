package com.leitian.cfdashboard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

    LaunchedEffect(appName) { viewModel.loadDetail(appName) }
    DisposableEffect(Unit) { onDispose { viewModel.clearDetail() } }

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
                    IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "更多") }
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
                Text("最后一个 24 小时", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("部署", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        if (!loading && items.isEmpty() && error == null) {
            Text("暂无部署记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items.forEach { d ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("域", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        if (!loading && items.isEmpty() && error == null) {
            Text("暂无自定义域名", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items.forEach { d ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Access", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Text(
            "账号级 Access Applications（不一定绑定当前 Worker）",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        if (!loading && items.isEmpty() && error == null) {
            Text("暂无 Access 应用", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items.forEach { a ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(b.type, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(b.detail.take(24), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
    }
}

@Composable
private fun BindingsTab(scriptInfo: CloudflareApi.ScriptInfo?, settings: WorkerSettingsDetail?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("绑定", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        val bindings = settings?.bindings
        if (bindings.isNullOrEmpty()) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Bindings 数量", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("${scriptInfo?.bindingCount ?: 0}", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
            }
        } else {
            bindings.forEach { b ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(b.type, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(b.detail.take(28), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Workers Logs")
                    StatusChip(if (scriptInfo?.logsEnabled == true) "已启用" else "已禁用", scriptInfo?.logsEnabled == true)
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Workers Traces")
                    StatusChip(if (scriptInfo?.tracesEnabled == true) "已启用" else "已禁用", scriptInfo?.tracesEnabled == true)
                }
            }
        }
    }
}

@Composable
private fun BindingDiagram(appName: String, bindingCount: Int, logsEnabled: Boolean, tracesEnabled: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BindingChip("绑定  $bindingCount")
                    BindingChip("Workers  —")
                    BindingChip("Queues  —")
                }
                Text("→", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                Card(
                    Modifier.weight(1.2f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("◇  $appName", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3B82F6)))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Observability", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Workers Logs", fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            StatusChip(if (logsEnabled) "已启用" else "已禁用", logsEnabled)
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Workers Traces", fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            StatusChip(if (tracesEnabled) "已启用" else "已禁用", tracesEnabled)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BindingChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Text(text, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

@Composable
private fun StatusChip(text: String, enabled: Boolean) {
    val bg = if (enabled) Color(0xFFE6F4EA) else Color(0xFFF5F5F5)
    val fg = if (enabled) Color(0xFF137333) else Color(0xFF666666)
    Surface(shape = RoundedCornerShape(12.dp), color = bg) {
        Text(text, fontSize = 11.sp, color = fg, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun MetricCard(title: String, value: String, points: List<Float>, lineColor: Color) {
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
                SimpleLineChart(points, lineColor, Modifier.fillMaxWidth().height(80.dp))
            } else {
                Box(
                    Modifier.fillMaxWidth().height(80.dp).background(Color(0xFFFAFAFA), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无调用数据", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun SimpleLineChart(points: List<Float>, lineColor: Color, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val maxY = points.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val minY = 0f
    val rangeY = (maxY - minY).coerceAtLeast(1f)
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val stepX = if (points.size > 1) width / (points.size - 1) else width
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - ((value - minY) / rangeY) * height * 0.85f - height * 0.05f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(fillPath, lineColor.copy(alpha = 0.12f))
        drawPath(path, lineColor, style = Stroke(width = 2.5f))
    }
}
