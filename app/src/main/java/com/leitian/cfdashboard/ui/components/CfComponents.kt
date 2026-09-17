package com.leitian.cfdashboard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
    content: @Composable RowScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        if (showDivider) HorizontalDivider(color = CfColors.Border, thickness = 1.dp)
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
            trackColor = CfColors.GrayBg
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
    lineColor: Color = CfColors.AreaBlue
) {
    Column(
        modifier
            .border(1.dp, CfColors.Border, RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp)
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
            drawLine(CfColors.Border, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = dash)
        }
        values.forEachIndexed { i, v ->
            val h = (v / maxV) * size.height * 0.9f
            val x = gap + i * (barW + gap)
            drawRect(barColor, Offset(x, size.height - h), androidx.compose.ui.geometry.Size(barW, h))
        }
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
            Text(
                title,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
