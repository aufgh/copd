package com.copdtrend.demo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.copdtrend.demo.ui.theme.Ink700
import com.copdtrend.demo.ui.theme.Mist100
import com.copdtrend.demo.ui.theme.Mist50
import com.copdtrend.demo.ui.theme.NumberLarge
import com.copdtrend.demo.ui.theme.riskColor

/**
 * 首页状态盘。
 *
 * 270 度弧 + 中间大号分值。等级文案用服务端下发的 label，
 * 颜色只是辅助 —— 文字本身已经说清了等级，色盲用户不受影响。
 */
@Composable
fun StatusDial(
    level: String,
    levelLabel: String,
    score: Int,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val color = riskColor(level)
    val fraction = (score.coerceIn(0, 100)) / 100f
    Box(
        modifier = modifier
            .size(216.dp)
            .semantics { contentDescription = "$levelLabel，趋势分 $score，$caption" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawArc(
                color = Ink700,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = 270f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(levelLabel, style = MaterialTheme.typography.titleMedium, color = color)
            Spacer(Modifier.height(2.dp))
            Text(score.toString(), style = NumberLarge, color = Mist100)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = Mist50)
        }
    }
}
