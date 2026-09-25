package com.leitian.cfdashboard.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Cloudflare-ish palette */
object CfColors {
    val Border = Color(0xFFE0E0E0)
    val HeaderBg = Color(0xFFF8F8F8)
    val Link = Color(0xFF0051C3)
    val GreenBg = Color(0xFFE6F4EA)
    val GreenText = Color(0xFF137333)
    val GrayBg = Color(0xFFF0F0F0)
    val GrayText = Color(0xFF5F6368)
    val AreaBlue = Color(0xFF3B82F6)
    val BarPurple = Color(0xFF7C3AED)
    val GridDot = Color(0xFFD0D0D0)
    val NodeBorder = Color(0xFFCFCFCF)
}

@Composable
fun StatusPill(
    text: String,
    active: Boolean = true,
    modifier: Modifier = Modifier
) {
    val bg = if (active) CfColors.GreenBg else CfColors.GrayBg
    val fg = if (active) CfColors.GreenText else CfColors.GrayText
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = bg
    ) {
        Text(
            text,
            fontSize = 11.sp,
            color = fg,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SourceBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = CfColors.GrayBg
    ) {
        Text(
            text.ifBlank { "—" },
            fontSize = 11.sp,
            color = CfColors.GrayText,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun CfSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CfTable(
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    header: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, CfColors.Border, RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
    ) {
        if (header != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(CfColors.HeaderBg, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .padding(horizontal = 12.dp, vertical = if (dense) 6.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = header
            )
            HorizontalDivider(color = CfColors.Border, thickness = 1.dp)
        }
        content()
    }
}

@Composable
fun CfTableRow(
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    dense: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (dense) 7.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        if (showDivider) {
            HorizontalDivider(color = CfColors.Border, thickness = 1.dp)
        }
    }
}

@Composable
fun ProgressWithLabel(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterStart) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = CfColors.AreaBlue,
            trackColor = CfColors.GrayBg,
        )
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = CfColors.Link,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun TrendMetricCard(
    title: String,
    value: String,
    points: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = CfColors.AreaBlue,
    changePct: Double? = null,
    positiveIsGood: Boolean = true
) {
    Column(
        modifier
            .border(1.dp, CfColors.Border, RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            if (changePct != null) {
                Spacer(Modifier.width(6.dp))
                val up = changePct >= 0
                val good = up == positiveIsGood
                Text(
                    "${if (up) "↗" else "↘"} ${String.format("%.1f", kotlin.math.abs(changePct))}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (good) CfColors.GreenText else Color(0xFFD93025)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        AreaSparkline(
            points = points,
            lineColor = lineColor,
            fillColor = lineColor.copy(alpha = 0.18f),
            modifier = Modifier.fillMaxWidth().height(36.dp)
        )
    }
}

@Composable
fun AreaSparkline(
    points: List<Float>,
    lineColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(modifier.background(CfColors.HeaderBg, RoundedCornerShape(4.dp)))
        return
    }
    Canvas(modifier) {
        val maxV = (points.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val stepX = if (points.size <= 1) size.width else size.width / (points.size - 1)
        val linePath = Path()
        val fillPath = Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v / maxV) * size.height * 0.9f - size.height * 0.05f
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo((points.size - 1) * stepX, size.height)
        fillPath.close()
        drawPath(fillPath, fillColor)
        drawPath(linePath, lineColor, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
fun BarChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = CfColors.BarPurple
) {
    if (values.isEmpty()) {
        Box(modifier.background(CfColors.HeaderBg, RoundedCornerShape(4.dp)))
        return
    }
    Canvas(modifier) {
        val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val gap = 4.dp.toPx()
        val barW = ((size.width - gap * (values.size + 1)) / values.size).coerceAtLeast(2f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(
                color = CfColors.Border,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
                pathEffect = dash
            )
        }
        values.forEachIndexed { i, v ->
            val h = (v / maxV) * size.height * 0.9f
            val slotX = gap + i * (barW + gap)
            // 柱子比格子窄一点，视觉上更细，同时保持左右居中。
            val drawW = barW * 0.65f
            val x = slotX + (barW - drawW) / 2f
            drawRect(
                color = barColor,
                topLeft = Offset(x, size.height - h),
                size = androidx.compose.ui.geometry.Size(drawW, h)
            )
        }
    }
}

/** 图表图例里的一项：一个色点 + 版本号 + 这个版本在图里的总调用次数。 */
data class ChartLegendItem(val label: String, val color: Color, val total: Long)

@Composable
fun ChartLegendRow(items: List<ChartLegendItem>, formatCount: (Long) -> String, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(item.color, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${item.label}  ${formatCount(item.total)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 带坐标轴的调用次数柱状图：左侧数值刻度、底部时间刻度、柱子按所属版本上色（跟 ChartLegendRow 的颜色对应），
 * 部署切换的时间点画一条竖线并标注版本号——尽量贴近 Cloudflare 网页版"调用次数"图表的样子。
 */
@Composable
fun SegmentedInvocationChart(
    values: List<Float>,
    bucketKeys: List<String>,
    barColors: List<Color>,
    deploymentMarkers: List<Pair<Int, String>>,
    valueFormatter: (Float) -> String,
    modifier: Modifier = Modifier,
    fallbackColor: Color = CfColors.BarPurple
) {
    if (values.isEmpty()) {
        Box(modifier.background(CfColors.HeaderBg, RoundedCornerShape(4.dp)))
        return
    }
    val axisColorArgb = CfColors.GrayText.toArgb()
    val markerColorArgb = CfColors.Link.toArgb()
    Canvas(modifier) {
        val leftAxisWidth = 34.dp.toPx()
        val bottomAxisHeight = 16.dp.toPx()
        val chartWidth = size.width - leftAxisWidth
        val chartHeight = size.height - bottomAxisHeight
        if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

        val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val rawStep = maxV / 4f
        val exponent = kotlin.math.floor(kotlin.math.log10(rawStep.toDouble())).toInt()
        val magnitude = Math.pow(10.0, exponent.toDouble()).toFloat().coerceAtLeast(0.0001f)
        val residual = rawStep / magnitude
        val niceResidual = when {
            residual <= 1f -> 1f
            residual <= 2f -> 2f
            residual <= 5f -> 5f
            else -> 10f
        }
        val step = (niceResidual * magnitude).coerceAtLeast(1f)
        val niceMax = step * 4f

        val axisLabelPaint = Paint().apply {
            color = axisColorArgb
            textSize = 9.sp.toPx()
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        for (i in 0..4) {
            val y = chartHeight * (4 - i) / 4f
            drawLine(
                color = CfColors.Border,
                start = Offset(leftAxisWidth, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
                pathEffect = if (i == 0) null else dash
            )
            drawContext.canvas.nativeCanvas.drawText(
                valueFormatter(step * i),
                leftAxisWidth - 6.dp.toPx(),
                (y + 3.dp.toPx()).coerceAtMost(chartHeight),
                axisLabelPaint
            )
        }

        val gap = 3.dp.toPx()
        val barW = ((chartWidth - gap * (values.size + 1)) / values.size).coerceAtLeast(1.5f)
        fun slotX(i: Int) = leftAxisWidth + gap + i * (barW + gap)

        values.forEachIndexed { i, v ->
            val h = (v / niceMax) * chartHeight
            val drawW = barW * 0.65f
            val x = slotX(i) + (barW - drawW) / 2f
            drawRect(
                color = barColors.getOrElse(i) { fallbackColor },
                topLeft = Offset(x, chartHeight - h),
                size = androidx.compose.ui.geometry.Size(drawW, h)
            )
        }

        val markerLabelPaint = Paint().apply {
            color = markerColorArgb
            textSize = 9.sp.toPx()
            isAntiAlias = true
        }
        deploymentMarkers.forEach { (index, label) ->
            if (index !in values.indices) return@forEach
            val x = slotX(index) + barW / 2f
            drawLine(
                color = CfColors.Link,
                start = Offset(x, 0f),
                end = Offset(x, chartHeight),
                strokeWidth = 1.3f
            )
            drawContext.canvas.nativeCanvas.apply {
                save()
                rotate(-90f, x, chartHeight)
                drawText(label, x - 4.dp.toPx(), chartHeight - 4.dp.toPx(), markerLabelPaint)
                restore()
            }
        }

        val tickLabelPaint = Paint().apply {
            color = axisColorArgb
            textSize = 9.sp.toPx()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        listOf(0, values.size / 2, values.size - 1).distinct().forEach { i ->
            val x = slotX(i) + barW / 2f
            val label = formatBucketTickLabel(bucketKeys.getOrNull(i) ?: "")
            if (label.isNotEmpty()) {
                drawContext.canvas.nativeCanvas.drawText(label, x, size.height - 2.dp.toPx(), tickLabelPaint)
            }
        }
    }
}

/** "yyyy-MM-ddTHH:mm"（UTC）转成本地时区的 "MM/dd HH:mm"，供图表 x 轴用；解析失败就留空不画。 */
private fun formatBucketTickLabel(bucketKey: String): String {
    if (bucketKey.length < 16) return ""
    return try {
        val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val date = sdfIn.parse(bucketKey) ?: return ""
        SimpleDateFormat("MM/dd HH:mm", Locale.US).format(date)
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun DottedCanvasBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .border(1.dp, CfColors.Border, RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
    ) {
        Canvas(Modifier.matchParentSize()) {
            val step = 12.dp.toPx()
            var y = step
            while (y < size.height) {
                var x = step
                while (x < size.width) {
                    drawCircle(CfColors.GridDot, radius = 1.2f, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        content()
    }
}

@Composable
fun TopologyNode(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Column(
        modifier
            .border(1.dp, CfColors.NodeBorder, RoundedCornerShape(6.dp))
            .background(Color.White, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (badge != null) {
                Spacer(Modifier.width(6.dp))
                Surface(shape = CircleShape, color = CfColors.Link) {
                    Text(
                        badge,
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 危险操作二次确认弹窗（删除变量 / 删除绑定 / 删除 Worker / 删除 Cron 等通用）。
 * requireTypedName 不为空时，要求用户手动输入该名称才能确认，防误触。
 */
@Composable
fun CfConfirmDangerDialog(
    title: String,
    message: String,
    confirmText: String = "删除",
    requireTypedName: String? = null,
    loading: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var typed by remember { mutableStateOf("") }
    val canConfirm = requireTypedName == null || typed == requireTypedName
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                if (requireTypedName != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("请输入「$requireTypedName」以确认", fontSize = 12.sp, color = CfColors.GrayText)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = canConfirm && !loading,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") }
        }
    )
}

@Composable
fun MonoLinkText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = CfColors.Link,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
