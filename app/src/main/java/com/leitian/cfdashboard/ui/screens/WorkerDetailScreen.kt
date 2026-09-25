package com.leitian.cfdashboard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leitian.cfdashboard.data.*
import com.leitian.cfdashboard.ui.components.*
import com.leitian.cfdashboard.ui.viewmodel.MainViewModel
import com.leitian.cfdashboard.ui.viewmodel.WriteState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Worker 详情内容：内嵌展示在首页列表条目下方（点条目原地展开，不再跳转页面）。
 * 顶部是可横向滑动的标签栏，下面用 HorizontalPager 承载内容——点标签或左右划都能切换。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun WorkerDetailContent(
    appName: String,
    isPages: Boolean = false,
    accountId: String,
    viewModel: MainViewModel,
    onWorkerDeleted: () -> Unit,
    initialTabIndex: Int = 0,
    onTabChanged: (Int) -> Unit = {}
) {
    val tabs = listOf("概述", "指标", "部署", "绑定", "Observability", "域", "Access", "设置")
    val pagerState = rememberPagerState(
        initialPage = initialTabIndex.coerceIn(0, tabs.size - 1),
        pageCount = { tabs.size }
    )
    val pagerScope = rememberCoroutineScope()
    // 标签页切换（点标签或左右划）都记下来，回调给外面持久化，下次重启停在同一页。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onTabChanged(it) }
    }
    // 所有标签页共用的统一高度（取目前见过的最高值），配合下面 Pager 内容里的 heightIn(min=...) 使用。
    var maxPageHeightPx by remember { mutableStateOf(0) }
    val pagerHeightDensity = LocalDensity.current

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
    val downloadState by viewModel.downloadState.collectAsState()

    var showPagesUnsupported by remember { mutableStateOf(false) }
    var showPagesDownloadUnsupported by remember { mutableStateOf(false) }
    val context = LocalContext.current

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

    LaunchedEffect(appName, accountId) { viewModel.loadDetail(appName, isPages, accountId) }
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

    Column(Modifier.fillMaxWidth()) {
        // 原顶部栏的上传/下载动作，现在放在展开区域顶部（不再有独立页面和返回箭头）。
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            if (uploadState is MainViewModel.UploadState.Loading || downloadState is MainViewModel.DownloadState.Loading) {
                CircularProgressIndicator(Modifier.size(18.dp).padding(end = 8.dp), strokeWidth = 2.dp)
            }
            IconButton(onClick = {
                if (isPages) showPagesUnsupported = true
                else filePicker.launch(arrayOf("application/javascript", "text/javascript", "text/plain"))
            }) {
                Icon(Icons.Default.FileUpload, "上传文件部署")
            }
            IconButton(onClick = {
                if (isPages) showPagesDownloadUnsupported = true
                else viewModel.downloadScript(appName)
            }) {
                Icon(Icons.Default.FileDownload, "下载代码")
            }
        }

        // 标签栏：全部标签一次性铺开显示（自动换行，不用横滑也不会被遮住），
        // 点击或左右划动 Pager 都能切换，两者互相联动。
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = pagerState.currentPage == index
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { pagerScope.launch { pagerState.animateScrollToPage(index) } }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        title,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider(color = CfColors.Border)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            beyondViewportPageCount = 1 // 预先合成相邻标签页，划动更顺，不会等到划一半才现算
        ) { page ->
            // 8 个标签内容高度差异很大（比如"指标"矮、"设置"很高），
            // Pager 默认按当前页高度自适应，划动时两页高度不一致会出现一边高一边低、错位的问题。
            // 这里记录目前见过的最高高度，统一当作所有标签的高度（只会变高不会变矮，也不会裁切内容），
            // 划动过程中容器高度稳定，就不会再错位、也更流畅。
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = with(pagerHeightDensity) { maxPageHeightPx.toDp() })
                    .onSizeChanged { size -> if (size.height > maxPageHeightPx) maxPageHeightPx = size.height }
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    when (page) {
                        0 -> OverviewTab(appName, scriptInfo, metrics, metricsLoading, metricsError, domains)
                        1 -> MetricsTab(metrics, metricsLoading, metricsError, deployments)
                        2 -> DeploymentsTab(deployments, deploymentsError, tabLoading, appName, viewModel)
                        3 -> BindingsTab(scriptInfo, settingsDetail, appName, viewModel)
                        4 -> ObservabilityTab(scriptInfo)
                        5 -> DomainsTab(domains, domainsError, tabLoading, appName, viewModel)
                        6 -> AccessTab(accessApps, accessError, tabLoading)
                        7 -> SettingsTab(
                            appName = appName,
                            detail = settingsDetail,
                            error = settingsError,
                            loading = tabLoading,
                            viewModel = viewModel,
                            onWorkerDeleted = onWorkerDeleted
                        )
                    }
                }
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
    }
}

@Composable
private fun MetricSummaryRow(metrics: CloudflareApi.WorkerMetrics?, loading: Boolean) {
    val m = metrics
    val req = when { loading && m == null -> "…"; m != null -> CloudflareApi.formatCount(m.totalRequests); else -> "0" }
    val sub = when { loading && m == null -> "…"; m != null -> CloudflareApi.formatCount(m.totalSubrequests); else -> "0" }
    val err = when { loading && m == null -> "…"; m != null -> m.totalErrors.toString(); else -> "0" }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrendMetricCard(
            "调用次数", req, m?.requestPoints ?: emptyList(), Modifier.width(160.dp),
            changePct = m?.requestsChangePct, positiveIsGood = true
        )
        TrendMetricCard(
            "子请求", sub, m?.subrequestPoints ?: emptyList(), Modifier.width(160.dp),
            changePct = m?.subrequestsChangePct, positiveIsGood = true
        )
        TrendMetricCard(
            "错误", err, m?.errorPoints ?: emptyList(), Modifier.width(160.dp), lineColor = Color(0xFFEF4444),
            changePct = m?.errorsChangePct, positiveIsGood = false
        )
    }
}

@Composable
private fun MetricsTab(
    metrics: CloudflareApi.WorkerMetrics?,
    metricsLoading: Boolean,
    metricsError: String?,
    deployments: List<DeploymentItem>
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
        AvailableDeploymentsTable(deployments, metrics, onlyCurrent = true)
        CfSectionTitle("调用次数")
        Column(Modifier.fillMaxWidth().border(1.dp, CfColors.Border, RoundedCornerShape(8.dp)).padding(12.dp)) {
            var showBar by remember { mutableStateOf(true) }
            val segments = remember(metrics?.bucketKeys, deployments) {
                buildDeploymentSegments(deployments, metrics?.bucketKeys ?: emptyList(), metrics?.requestPoints ?: emptyList())
            }
            if (segments.legend.size > 1) {
                ChartLegendRow(segments.legend, CloudflareApi::formatCount, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(CfColors.BarPurple, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "过去24小时 · ${metrics?.let { CloudflareApi.formatCount(it.totalRequests) } ?: "0"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
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
                SegmentedInvocationChart(
                    values = metrics?.requestPoints ?: emptyList(),
                    bucketKeys = metrics?.bucketKeys ?: emptyList(),
                    barColors = segments.barColors,
                    deploymentMarkers = segments.markers,
                    valueFormatter = { CloudflareApi.formatCount(it.toLong()) },
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
            } else {
                AreaSparkline(
                    points = metrics?.requestRatePoints ?: emptyList(),
                    lineColor = CfColors.BarPurple,
                    fillColor = CfColors.BarPurple.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            }
        }
    }
}

private val DeploymentChartPalette = listOf(
    CfColors.BarPurple,
    Color(0xFFEA580C),
    Color(0xFF0EA5E9),
    Color(0xFF16A34A),
    Color(0xFFCA8A04)
)

private data class DeploymentSegments(
    val barColors: List<Color>,
    val legend: List<ChartLegendItem>,
    val markers: List<Pair<Int, String>>
)

private fun parseUtc(pattern: String, value: String): Long? = try {
    SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)?.time
} catch (e: Exception) {
    null
}

/**
 * 把"调用次数"图表的每根柱子按当时生效的部署版本上色，同时算出图例（每个版本的颜色+总量）
 * 和需要画竖线标记的部署时间点——数据来源就是已经在拉的部署列表 + 指标的时间桶，不需要额外接口
 * （Cloudflare 公开的 GraphQL 分析接口本身不提供"这次调用是哪个版本处理的"这种按版本拆分的数据，
 * 这里是用"这个时间点当时哪个版本在线"来近似还原网页版图表的分段效果）。
 */
private fun buildDeploymentSegments(
    deployments: List<DeploymentItem>,
    bucketKeys: List<String>,
    values: List<Float>
): DeploymentSegments {
    if (bucketKeys.isEmpty()) return DeploymentSegments(emptyList(), emptyList(), emptyList())

    val sortedDeploys = deployments
        .mapNotNull { d -> parseUtc("yyyy-MM-dd HH:mm:ss", d.createdOn)?.let { d to it } }
        .sortedBy { it.second }

    if (sortedDeploys.isEmpty()) {
        return DeploymentSegments(List(bucketKeys.size) { CfColors.BarPurple }, emptyList(), emptyList())
    }

    val bucketTimes = bucketKeys.map { parseUtc("yyyy-MM-dd'T'HH:mm", it) }
    val colorFor = linkedMapOf<String, Color>()
    var nextColor = 0
    fun colorForVersion(versionId: String): Color =
        colorFor.getOrPut(versionId) { DeploymentChartPalette[nextColor++ % DeploymentChartPalette.size] }

    val barColors = ArrayList<Color>(bucketKeys.size)
    val totals = linkedMapOf<String, Long>()
    bucketTimes.forEachIndexed { i, t ->
        val active = if (t == null) sortedDeploys.last().first
            else sortedDeploys.lastOrNull { it.second <= t }?.first ?: sortedDeploys.first().first
        barColors.add(colorForVersion(active.versionId))
        totals[active.versionId] = (totals[active.versionId] ?: 0L) + (values.getOrNull(i)?.toLong() ?: 0L)
    }

    // 只标出真正落在这段时间范围内的部署时间点，比所有采样点都早的部署（一直在线，没有切换）不用画。
    val markers = sortedDeploys.mapNotNull { (d, t) ->
        val idx = bucketTimes.indexOfFirst { it != null && it >= t }
        if (idx <= 0) null else idx to "Deployed ${d.versionId}"
    }

    val legend = totals.entries.sortedByDescending { it.value }.map { (versionId, total) ->
        ChartLegendItem(versionId, colorFor[versionId] ?: CfColors.BarPurple, total)
    }

    return DeploymentSegments(barColors, legend, markers)
}

@Composable
private fun AvailableDeploymentsTable(
    items: List<DeploymentItem>,
    metrics: CloudflareApi.WorkerMetrics? = null,
    onlyCurrent: Boolean = false
) {
    val shown = if (onlyCurrent) items.filter { it.isLatest }.ifEmpty { items.take(1) } else items.take(5)
    CfTable(header = {
        Text("版本 ID", Modifier.weight(1.2f), fontSize = 11.sp, color = CfColors.GrayText)
        Text("已部署", Modifier.weight(1.4f), fontSize = 11.sp, color = CfColors.GrayText)
        Text("流量", Modifier.weight(0.8f), fontSize = 11.sp, color = CfColors.GrayText)
    }) {
        if (shown.isEmpty()) {
            Text("暂无部署", Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
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
    // 手机屏幕横向放不下网页版那么多列，把"请求/秒、错误率、中值 CPU"折到表格下面单独一行，
    // 信息跟网页版一致，只是排版换成上下堆叠。只有一个版本（onlyCurrent）时这几个数字才等于整体指标。
    if (onlyCurrent && metrics != null && shown.isNotEmpty()) {
        val reqPerSec = metrics.totalRequests / 86400.0
        val errRate = if (metrics.totalRequests > 0) metrics.totalErrors.toDouble() / metrics.totalRequests * 100.0 else 0.0
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("请求/秒 ${String.format(Locale.US, "%.1f", reqPerSec)}", fontSize = 11.sp, color = CfColors.GrayText)
            Text("错误率 ${String.format(Locale.US, "%.1f", errRate)}%", fontSize = 11.sp, color = CfColors.GrayText)
            Text("中值 CPU ${String.format(Locale.US, "%.2f", metrics.cpuTimeMs)} ms", fontSize = 11.sp, color = CfColors.GrayText)
        }
    }
}

@Composable
private fun DeploymentsTab(items: List<DeploymentItem>, error: String?, loading: Boolean, appName: String, viewModel: MainViewModel) {
    val deploymentWriteState by viewModel.deploymentWriteState.collectAsState()
    var rollbackTarget by remember { mutableStateOf<DeploymentItem?>(null) }

    WriteResultDialog(deploymentWriteState, onDismiss = { viewModel.clearDeploymentWriteState() })

    if (rollbackTarget != null) {
        CfConfirmDangerDialog(
            title = "回滚部署",
            message = "确定要回滚到版本「${rollbackTarget!!.versionId}」并将其设为 100% 流量吗？",
            confirmText = "回滚",
            loading = deploymentWriteState is WriteState.Loading,
            onConfirm = { viewModel.rollbackDeployment(appName, rollbackTarget!!.versionId); rollbackTarget = null },
            onDismiss = { rollbackTarget = null }
        )
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CfSectionTitle("可用部署") { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
        Spacer(Modifier.height(8.dp))
        AvailableDeploymentsTable(items)
        CfSectionTitle(title = "版本历史记录", subtitle = "按时间倒序显示最近的部署与配置变更，点右侧按钮可回滚。", trailing = {
            Icon(Icons.Default.Refresh, "刷新", Modifier.size(20.dp))
        })
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        if (!loading && items.isEmpty() && error == null) Text("暂无部署记录", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
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
                    if (!d.isLatest) {
                        TextButton(onClick = { rollbackTarget = d }, enabled = deploymentWriteState !is WriteState.Loading) {
                            Text("回滚", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Text("显示 1-${items.size}/${items.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CfSectionTitle("最近构建")
        Text("此 Worker 还没有构建", Modifier.fillMaxWidth().border(1.dp, CfColors.Border, RoundedCornerShape(8.dp)).padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DomainsTab(items: List<DomainItem>, error: String?, loading: Boolean, appName: String, viewModel: MainViewModel) {
    val domainWriteState by viewModel.domainWriteState.collectAsState()
    val zones by viewModel.zones.collectAsState()
    var showAddDomain by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DomainItem?>(null) }

    WriteResultDialog(domainWriteState, onDismiss = { viewModel.clearDomainWriteState() })

    if (showAddDomain) {
        LaunchedEffect(Unit) { viewModel.loadZones() }
        AddDomainDialog(
            zones = zones,
            loading = domainWriteState is WriteState.Loading,
            onDismiss = { showAddDomain = false },
            onConfirm = { hostname, zoneId ->
                viewModel.addCustomDomain(appName, hostname, zoneId)
                showAddDomain = false
            }
        )
    }

    if (deleteTarget != null) {
        CfConfirmDangerDialog(
            title = "删除域名",
            message = "确定要删除自定义域名「${deleteTarget!!.hostname}」吗？",
            loading = domainWriteState is WriteState.Loading,
            onConfirm = { viewModel.deleteCustomDomain(appName, deleteTarget!!.id); deleteTarget = null },
            onDismiss = { deleteTarget = null }
        )
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CfSectionTitle("域") {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { showAddDomain = true }) { Text("＋ 添加域名") }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        CfTable(header = {
            Text("主机名", Modifier.weight(1.4f), fontSize = 11.sp, color = CfColors.GrayText)
            Text("环境", Modifier.weight(0.8f), fontSize = 11.sp, color = CfColors.GrayText)
            Text("服务", Modifier.weight(1f), fontSize = 11.sp, color = CfColors.GrayText)
            Text("", Modifier.weight(0.5f))
        }) {
            if (!loading && items.isEmpty() && error == null) {
                Text("暂无自定义域名", Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                items.forEachIndexed { index, d ->
                    CfTableRow(showDivider = index != items.lastIndex) {
                        Text(d.hostname, Modifier.weight(1.4f), color = CfColors.Link, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(d.environment, Modifier.weight(0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(d.service, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { deleteTarget = d }, modifier = Modifier.weight(0.5f).size(32.dp)) {
                            Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddDomainDialog(
    zones: List<ZoneItem>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (hostname: String, zoneId: String) -> Unit
) {
    var hostname by remember { mutableStateOf("") }
    var selectedZone by remember { mutableStateOf<ZoneItem?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("添加自定义域名") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = hostname,
                    onValueChange = { hostname = it },
                    label = { Text("主机名") },
                    placeholder = { Text("例如：api.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedZone?.name ?: if (zones.isEmpty()) "加载 Zone 中…" else "选择 Zone", Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        zones.forEach { z ->
                            DropdownMenuItem(text = { Text(z.name) }, onClick = { selectedZone = z; menuExpanded = false })
                        }
                    }
                }
                Text("域名要归属于上面选的 Zone 才能挂载成功。", fontSize = 11.sp, color = CfColors.GrayText)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(hostname.trim(), selectedZone?.id ?: "") },
                enabled = !loading && hostname.isNotBlank() && selectedZone != null
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("添加")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } }
    )
}

@Composable
private fun AccessTab(items: List<AccessAppItem>, error: String?, loading: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CfSectionTitle(title = "Access", subtitle = "账号级 Access Applications（不一定绑定当前 Worker）") {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
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

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

        // ---------------- 触发事件：Queues ----------------
        val queues by viewModel.queues.collectAsState()
        val queueWriteState by viewModel.queueWriteState.collectAsState()
        var showAddQueue by remember { mutableStateOf(false) }
        var deleteTargetQueue by remember { mutableStateOf<QueueItem?>(null) }
        LaunchedEffect(appName) { viewModel.loadQueues(appName) }
        WriteResultDialog(queueWriteState, onDismiss = { viewModel.clearQueueWriteState() })

        if (showAddQueue) {
            QueuePickDialog(
                queues = queues.filter { it.consumerId == null },
                loading = queueWriteState is WriteState.Loading,
                onDismiss = { showAddQueue = false },
                onConfirm = { queueId -> viewModel.addQueueConsumer(appName, queueId); showAddQueue = false }
            )
        }
        if (deleteTargetQueue != null) {
            CfConfirmDangerDialog(
                title = "移除 Queue 消费者",
                message = "确定要把此 Worker 从队列「${deleteTargetQueue!!.name}」的消费者中移除吗？",
                loading = queueWriteState is WriteState.Loading,
                onConfirm = {
                    viewModel.deleteQueueConsumer(appName, deleteTargetQueue!!.id, deleteTargetQueue!!.consumerId!!)
                    deleteTargetQueue = null
                },
                onDismiss = { deleteTargetQueue = null }
            )
        }
        val connectedQueues = queues.filter { it.consumerId != null }
        Column(Modifier.fillMaxWidth().border(1.dp, CfColors.Border, RoundedCornerShape(8.dp)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Queues", Modifier.weight(1f), fontSize = 14.sp)
                TextButton(onClick = { showAddQueue = true }) { Text("＋ 添加") }
            }
            if (connectedQueues.isEmpty()) {
                Text("未配置队列消费者", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                connectedQueues.forEach { q ->
                    HorizontalDivider(color = CfColors.Border)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(q.name, Modifier.weight(1f), fontSize = 13.sp)
                        IconButton(onClick = { deleteTargetQueue = q }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, "移除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // ---------------- 触发事件：邮件路由 ----------------
        val emailZones by viewModel.zones.collectAsState()
        val emailRules by viewModel.emailRules.collectAsState()
        val emailWriteState by viewModel.emailWriteState.collectAsState()
        var showAddEmail by remember { mutableStateOf(false) }
        var emailZoneForList by remember { mutableStateOf<ZoneItem?>(null) }
        var deleteTargetEmail by remember { mutableStateOf<EmailRoutingRuleItem?>(null) }
        WriteResultDialog(emailWriteState, onDismiss = { viewModel.clearEmailWriteState() })

        if (showAddEmail) {
            LaunchedEffect(Unit) { viewModel.loadZones() }
            AddEmailRuleDialog(
                zones = emailZones,
                loading = emailWriteState is WriteState.Loading,
                onDismiss = { showAddEmail = false },
                onConfirm = { zoneId, address ->
                    viewModel.addEmailRule(appName, zoneId, address)
                    emailZoneForList = emailZones.find { it.id == zoneId }
                    showAddEmail = false
                }
            )
        }
        if (deleteTargetEmail != null && emailZoneForList != null) {
            CfConfirmDangerDialog(
                title = "删除邮件路由规则",
                message = "确定要删除转发到「${deleteTargetEmail!!.matchValue}」的规则吗？",
                loading = emailWriteState is WriteState.Loading,
                onConfirm = {
                    viewModel.deleteEmailRule(appName, emailZoneForList!!.id, deleteTargetEmail!!.id)
                    deleteTargetEmail = null
                },
                onDismiss = { deleteTargetEmail = null }
            )
        }
        Column(Modifier.fillMaxWidth().border(1.dp, CfColors.Border, RoundedCornerShape(8.dp)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("邮件触发器", Modifier.weight(1f), fontSize = 14.sp)
                TextButton(onClick = { showAddEmail = true }) { Text("＋ 添加") }
            }
            if (emailZoneForList == null) {
                Text("没有路由规则向此 Worker 发送电子邮件（添加规则时需要选择所属 Zone）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (emailRules.isEmpty()) {
                Text("此 Zone 下没有转发到当前 Worker 的规则", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                emailRules.forEach { r ->
                    HorizontalDivider(color = CfColors.Border)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.matchValue, Modifier.weight(1f), fontSize = 13.sp)
                        IconButton(onClick = { deleteTargetEmail = r }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
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

/** 选一个尚未连接的 Queue 作为消费者 */
@Composable
private fun QueuePickDialog(queues: List<QueueItem>, loading: Boolean, onDismiss: () -> Unit, onConfirm: (queueId: String) -> Unit) {
    var selected by remember { mutableStateOf<QueueItem?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("添加 Queue 消费者") },
        text = {
            Column {
                Box {
                    OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selected?.name ?: if (queues.isEmpty()) "没有可用的 Queue" else "选择 Queue", Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        queues.forEach { q ->
                            DropdownMenuItem(text = { Text(q.name) }, onClick = { selected = q; menuExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected!!.id) }, enabled = !loading && selected != null) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("添加")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } }
    )
}

/** 添加邮件路由规则：选 Zone + 填匹配的收件地址 */
@Composable
private fun AddEmailRuleDialog(zones: List<ZoneItem>, loading: Boolean, onDismiss: () -> Unit, onConfirm: (zoneId: String, address: String) -> Unit) {
    var selectedZone by remember { mutableStateOf<ZoneItem?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("添加邮件触发器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedZone?.name ?: "选择 Zone", Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        zones.forEach { z -> DropdownMenuItem(text = { Text(z.name) }, onClick = { selectedZone = z; menuExpanded = false }) }
                    }
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("匹配的收件地址") },
                    placeholder = { Text("例如：hello@example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedZone?.id ?: "", address.trim()) },
                enabled = !loading && selectedZone != null && address.isNotBlank()
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("添加")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } }
    )
}

@Composable
private fun BindingsTab(scriptInfo: CloudflareApi.ScriptInfo?, settingsDetail: WorkerSettingsDetail?, appName: String, viewModel: MainViewModel) {
    val bindings = settingsDetail?.bindings.orEmpty()
    val bindingWriteState by viewModel.bindingWriteState.collectAsState()
    val kvNamespaces by viewModel.kvNamespaces.collectAsState()
    val r2Buckets by viewModel.r2Buckets.collectAsState()
    val d1Databases by viewModel.d1Databases.collectAsState()

    var showAddBinding by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    WriteResultDialog(bindingWriteState, onDismiss = { viewModel.clearBindingWriteState() })

    if (deleteTarget != null) {
        CfConfirmDangerDialog(
            title = "删除绑定",
            message = "确定要删除绑定「${deleteTarget}」吗？",
            loading = bindingWriteState is WriteState.Loading,
            onConfirm = { viewModel.deleteResourceBinding(appName, deleteTarget!!); deleteTarget = null },
            onDismiss = { deleteTarget = null }
        )
    }

    if (showAddBinding) {
        AddBindingDialog(
            kvNamespaces = kvNamespaces,
            r2Buckets = r2Buckets,
            d1Databases = d1Databases,
            loading = bindingWriteState is WriteState.Loading,
            onLoadKv = { viewModel.loadKvNamespaces() },
            onLoadR2 = { viewModel.loadR2Buckets() },
            onLoadD1 = { viewModel.loadD1Databases() },
            onDismiss = { showAddBinding = false },
            onConfirm = { name, type, resource ->
                viewModel.addResourceBinding(appName, name, type, resource)
                showAddBinding = false
            }
        )
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CfSectionTitle(title = "绑定", subtitle = "将资源连接到此 Worker。") {
            TextButton(onClick = { showAddBinding = true }) { Text("＋ 添加绑定") }
        }
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
        CfSectionTitle("已连接绑定")
        CfTable(header = {
            Text("类型", Modifier.weight(0.9f), fontSize = 11.sp, color = CfColors.GrayText)
            Text("名称", Modifier.weight(1f), fontSize = 11.sp, color = CfColors.GrayText)
            Text("值", Modifier.weight(1.2f), fontSize = 11.sp, color = CfColors.GrayText)
            Text("", Modifier.weight(0.4f))
        }) {
            if (bindings.isEmpty()) {
                Text("暂无绑定", Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                bindings.forEachIndexed { index, b ->
                    CfTableRow(showDivider = index != bindings.lastIndex) {
                        Text(b.type, Modifier.weight(0.9f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(b.name, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(b.detail.ifBlank { "—" }, Modifier.weight(1.2f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { deleteTarget = b.name }, modifier = Modifier.weight(0.4f).size(32.dp)) {
                            Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

/** 添加资源绑定：先选类型，再从对应资源列表里选一个 */
@Composable
private fun AddBindingDialog(
    kvNamespaces: List<KvNamespaceItem>,
    r2Buckets: List<R2BucketItem>,
    d1Databases: List<D1DatabaseItem>,
    loading: Boolean,
    onLoadKv: () -> Unit,
    onLoadR2: () -> Unit,
    onLoadD1: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (bindingName: String, type: String, resourceIdOrName: String) -> Unit
) {
    var bindingName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("kv_namespace") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var resourceMenuExpanded by remember { mutableStateOf(false) }
    var selectedResourceId by remember { mutableStateOf("") }
    var selectedResourceLabel by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") }

    LaunchedEffect(selectedType) {
        selectedResourceId = ""; selectedResourceLabel = ""
        when (selectedType) {
            "kv_namespace" -> onLoadKv()
            "r2_bucket" -> onLoadR2()
            "d1" -> onLoadD1()
        }
    }

    val typeLabel = mapOf("kv_namespace" to "KV 命名空间", "r2_bucket" to "R2 存储桶", "d1" to "D1 数据库", "service" to "Service")

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("添加绑定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = bindingName,
                    onValueChange = { bindingName = it },
                    label = { Text("绑定名称（代码里用这个名字访问）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { typeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(typeLabel[selectedType] ?: selectedType, Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        typeLabel.forEach { (type, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { selectedType = type; typeMenuExpanded = false })
                        }
                    }
                }
                when (selectedType) {
                    "service" -> OutlinedTextField(
                        value = serviceName,
                        onValueChange = { serviceName = it },
                        label = { Text("目标 Worker 名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    else -> {
                        val options: List<Pair<String, String>> = when (selectedType) {
                            "kv_namespace" -> kvNamespaces.map { it.id to it.title }
                            "r2_bucket" -> r2Buckets.map { it.name to it.name }
                            "d1" -> d1Databases.map { it.id to it.name }
                            else -> emptyList()
                        }
                        Box {
                            OutlinedButton(onClick = { resourceMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(selectedResourceLabel.ifBlank { if (options.isEmpty()) "加载中…" else "选择资源" }, Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = resourceMenuExpanded, onDismissRequest = { resourceMenuExpanded = false }) {
                                options.forEach { (id, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        selectedResourceId = id; selectedResourceLabel = label; resourceMenuExpanded = false
                                    })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val resourceValue = if (selectedType == "service") serviceName.trim() else selectedResourceId
            TextButton(
                onClick = { onConfirm(bindingName.trim(), selectedType, resourceValue) },
                enabled = !loading && bindingName.isNotBlank() && resourceValue.isNotBlank()
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("添加")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } }
    )
}

@Composable
private fun ObservabilityTab(scriptInfo: CloudflareApi.ScriptInfo?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    }
}
