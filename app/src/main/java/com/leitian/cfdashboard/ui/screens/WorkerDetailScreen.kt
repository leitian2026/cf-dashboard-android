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
import com.leitian.cfdashboard.data.CloudflareApi
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

    LaunchedEffect(appName) {
        viewModel.loadDetail(appName)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearDetail() }
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
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreVert, "更多")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
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

            // 根据选中的 Tab 显示不同内容（之前的问题：只有概述内容，点其他 Tab 没切换）
            when (selectedTab) {
                0 -> OverviewTab(
                    appName = appName,
                    scriptInfo = scriptInfo,
                    metrics = metrics,
                    metricsLoading = metricsLoading,
                    metricsError = metricsError
                )
                1 -> MetricsTab(
                    metrics = metrics,
                    metricsLoading = metricsLoading,
                    metricsError = metricsError
                )
                2 -> PlaceholderTab("部署", "此处显示该 Worker 的部署历史与版本信息。\n（需对接 deployments API）")
                3 -> BindingsTab(scriptInfo = scriptInfo)
                4 -> ObservabilityTab(scriptInfo = scriptInfo)
                5 -> PlaceholderTab("域", "此处显示绑定到该 Worker 的自定义域名与路由。\n（需对接 routes / domains API）")
                6 -> PlaceholderTab("Access", "Cloudflare Access 相关配置。")
                7 -> PlaceholderTab("设置", "Worker 基础设置、兼容性日期、用量模型等。")
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
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
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

        BindingDiagram(
            appName = appName,
            bindingCount = scriptInfo?.bindingCount ?: 0,
            logsEnabled = scriptInfo?.logsEnabled ?: false,
            tracesEnabled = scriptInfo?.tracesEnabled ?: false
        )

        // 概述里也放一份最近 24h 指标摘要
        MetricsSummary(metrics, metricsLoading, metricsError)

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetricsTab(
    metrics: CloudflareApi.WorkerMetrics?,
    metricsLoading: Boolean,
    metricsError: String?
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("指标", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFF0F0F0)) {
                Text("最后一个 24 小时", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.weight(1f))
            if (metricsLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }

        if (metricsError != null) {
            Text(metricsError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        MetricsSummary(metrics, metricsLoading, metricsError)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetricsSummary(
    metrics: CloudflareApi.WorkerMetrics?,
    metricsLoading: Boolean,
    metricsError: String?
) {
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
                Box(
                    Modifier.fillMaxWidth().height(40.dp)
                        .background(Color(0xFFFAFAFA), RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
private fun BindingsTab(scriptInfo: CloudflareApi.ScriptInfo?) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("绑定", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Bindings 数量", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${scriptInfo?.bindingCount ?: 0}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "从 /workers/scripts/{name}/settings 读取的真实绑定数量。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ObservabilityTab(scriptInfo: CloudflareApi.ScriptInfo?) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
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
                    StatusChip(
                        if (scriptInfo?.logsEnabled == true) "已启用" else "已禁用",
                        scriptInfo?.logsEnabled == true
                    )
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Workers Traces")
                    StatusChip(
                        if (scriptInfo?.tracesEnabled == true) "已启用" else "已禁用",
                        scriptInfo?.tracesEnabled == true
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderTab(title: String, desc: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            desc,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun BindingDiagram(
    appName: String,
    bindingCount: Int,
    logsEnabled: Boolean,
    tracesEnabled: Boolean
) {
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
                    Modifier.fillMaxWidth().height(80.dp)
                        .background(Color(0xFFFAFAFA), RoundedCornerShape(4.dp)),
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
