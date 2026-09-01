package com.copdtrend.demo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.copdtrend.demo.network.HistoryItemDto
import com.copdtrend.demo.ui.theme.Ink700
import com.copdtrend.demo.ui.theme.Mist100
import com.copdtrend.demo.ui.theme.Mist50
import com.copdtrend.demo.ui.theme.Mist70
import com.copdtrend.demo.ui.theme.NumberSmall
import com.copdtrend.demo.ui.theme.riskColor

/**
 * 七天风险分柱状图。
 *
 * 无障碍：颜色不是唯一信息通道 —— 柱高表示分值、柱下有日期、
 * 整个图有一句汇总 contentDescription，读屏用户能听到趋势。
 */
@Composable
fun RiskBarChart(
    items: List<HistoryItemDto>,
    modifier: Modifier = Modifier,
    highlightLast: Boolean = true,
) {
    if (items.isEmpty()) return
    val summary = items.joinToString("，") {
        "${it.date.takeLast(5)} ${it.riskLevelLabel.ifBlank { it.riskLevel }} ${it.riskScore} 分"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "近 ${items.size} 天趋势：$summary" }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            items.forEachIndexed { index, item ->
                val isLast = highlightLast && index == items.lastIndex
                val color = riskColor(item.riskLevel)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = item.riskScore.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLast) Mist100 else Mist50,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((10 + item.riskScore.coerceIn(0, 100)).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isLast) color else color.copy(alpha = 0.55f))
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.forEach { item ->
                Text(
                    text = item.date.takeLast(5),
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist50,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 折线小图，用于呼吸频率这类连续指标。 */
@Composable
fun Sparkline(
    values: List<Float>,
    accent: Color,
    modifier: Modifier = Modifier,
    label: String = "",
) {
    if (values.size < 2) return
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 0.01f } ?: 1f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "$label 从 ${"%.1f".format(values.first())} 变化到 ${"%.1f".format(values.last())}"
            }
    ) {
        if (label.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist70,
                    modifier = Modifier.weight(1f),
                )
                Text("%.1f".format(values.last()), style = NumberSmall, color = Mist100)
            }
            Spacer(Modifier.height(10.dp))
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
            val pad = 8f
            fun yFor(v: Float) = size.height - pad - (v - min) / span * (size.height - pad * 2)

            val path = Path()
            values.forEachIndexed { index, value ->
                val x = index * stepX
                val y = yFor(value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = accent,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            values.forEachIndexed { index, value ->
                drawCircle(
                    color = accent,
                    radius = if (index == values.lastIndex) 5.dp.toPx() else 2.5f.dp.toPx(),
                    center = Offset(index * stepX, yFor(value)),
                )
            }
        }
    }
}

/** 历史列表的一行。左侧色条 + 日期 + 等级 + 两个关键指标。 */
@Composable
fun HistoryRow(item: HistoryItemDto, isToday: Boolean) {
    val color = riskColor(item.riskLevel)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .padding(vertical = 10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.date.takeLast(5) + if (isToday) "（今天）" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = Mist100,
            )
            Text(
                text = item.riskLevelLabel.ifBlank { item.riskLevel } +
                    "  ·  录音${item.qualityLabel.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                color = Mist50,
            )
        }
        Text("%.1f".format(item.respiratoryRate), style = NumberSmall, color = Mist70)
        Text(" 次/分", style = MaterialTheme.typography.bodySmall, color = Mist50)
        Spacer(Modifier.width(12.dp))
        Text("${item.coughCount}", style = NumberSmall, color = Mist70)
        Text(" 次咳嗽", style = MaterialTheme.typography.bodySmall, color = Mist50)
    }
}

/** 空历史占位。 */
@Composable
fun EmptyHistory(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Ink700),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "还没有历史记录",
            style = MaterialTheme.typography.bodyMedium,
            color = Mist50,
        )
    }
}
