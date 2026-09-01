package com.copdtrend.demo.ui.components

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.copdtrend.demo.ui.theme.Ink700
import com.copdtrend.demo.ui.theme.Mist50
import com.copdtrend.demo.ui.theme.Mist70
import com.copdtrend.demo.ui.theme.NumberMedium
import com.copdtrend.demo.ui.theme.TealGlow

private const val BARS = 28

/**
 * 录音可视化：外圈倒计时环 + 呼吸引导圆 + 中间对称波形。
 *
 * 波形数据来自 AudioRecorder 每 100 ms 一次的 RMS（已做 EMA 平滑），
 * 所以它反映的是真实输入电平，不是装饰动画 —— 用户对着麦克风说话能立刻看到反应。
 *
 * 系统"关闭动画"时（开发者选项或无障碍设置把动画时长调为 0），
 * 呼吸引导圆不再脉动，只显示静态圆，避免给前庭敏感用户造成不适。
 */
@Composable
fun RecordVisual(
    level: Float,
    elapsedMs: Long,
    targetMs: Long,
    minMs: Long,
    isRecording: Boolean,
    showBreathingGuide: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }

    val levels = remember { mutableStateListOf<Float>().apply { repeat(BARS) { add(0.04f) } } }
    LaunchedEffect(level, isRecording) {
        if (isRecording) pushLevel(levels, level) else pushLevel(levels, 0.04f)
    }

    val progress = if (targetMs > 0) (elapsedMs.toFloat() / targetMs).coerceIn(0f, 1f) else 0f
    val remainSec = ((targetMs - elapsedMs).coerceAtLeast(0L) / 1000f).toInt()
    val reachedMin = elapsedMs >= minMs

    val guideScale = if (showBreathingGuide && isRecording && animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "breath")
        transition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                // 4 秒吸、4 秒呼，和引导文案一致
                animation = tween(durationMillis = 4_000),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breathScale",
        ).value
    } else {
        0.92f
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .semantics {
                    contentDescription = if (isRecording) {
                        "正在录音，已录 ${elapsedMs / 1000} 秒"
                    } else {
                        "未开始录音"
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 10.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                drawCircle(color = Ink700, radius = radius, style = Stroke(strokeWidth))
                if (progress > 0f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    )
                }
                drawCircle(color = TealGlow, radius = radius * 0.74f * guideScale)
            }

            Canvas(
                modifier = Modifier
                    .size(160.dp, 96.dp)
                    .padding(horizontal = 4.dp)
            ) {
                val slot = size.width / BARS
                val barWidth = slot * 0.55f
                val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
                levels.forEachIndexed { index, value ->
                    val height = (size.height * value).coerceIn(barWidth, size.height)
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(
                            x = index * slot + (slot - barWidth) / 2f,
                            y = center.y - height / 2f,
                        ),
                        size = Size(barWidth, height),
                        cornerRadius = radius,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = if (isRecording) "剩余 $remainSec 秒" else "已录 ${elapsedMs / 1000} 秒",
            style = NumberMedium,
            color = Mist70,
        )
        Text(
            text = when {
                !isRecording -> " "
                reachedMin -> "已达到最短时长，可以随时结束"
                else -> "请保持，达到最短时长后即可结束"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Mist50,
        )
    }
}

/** 波形向左滚动一格。列表长度固定，不会随时间增长。 */
private fun pushLevel(levels: SnapshotStateList<Float>, value: Float) {
    levels.removeAt(0)
    levels.add(value.coerceIn(0.04f, 1f))
}
