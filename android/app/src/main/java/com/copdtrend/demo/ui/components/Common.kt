package com.copdtrend.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.copdtrend.demo.domain.DISCLAIMER
import com.copdtrend.demo.ui.theme.Ink600
import com.copdtrend.demo.ui.theme.Ink700
import com.copdtrend.demo.ui.theme.Ink800
import com.copdtrend.demo.ui.theme.Ink850
import com.copdtrend.demo.ui.theme.Ink900
import com.copdtrend.demo.ui.theme.Mist100
import com.copdtrend.demo.ui.theme.Mist50
import com.copdtrend.demo.ui.theme.Mist70
import com.copdtrend.demo.ui.theme.NumberSmall

/**
 * 页面骨架：渐变底 + 顶栏 + 内容 + 底部固定免责声明。
 *
 * 免责声明放在骨架里而不是每个页面各写一遍，保证"每页都有、不会漏"。
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    bottomBar: @Composable (() -> Unit)? = null,
    showDisclaimer: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ink900, Ink850, Ink900)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            AppTopBar(title = title, onBack = onBack, trailing = trailing)
            Box(modifier = Modifier.weight(1f)) { content() }
            bottomBar?.invoke()
            if (showDisclaimer) DisclaimerBar()
        }
    }
}

@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "返回" },
                contentAlignment = Alignment.Center,
            ) {
                Text("←", color = Mist70, style = MaterialTheme.typography.headlineSmall)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Mist100,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
        Spacer(Modifier.width(8.dp))
    }
}

/** 合规红线：固定文案，不可被页面覆盖。 */
@Composable
fun DisclaimerBar() {
    Text(
        text = DISCLAIMER,
        style = MaterialTheme.typography.bodySmall,
        color = Mist50,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink900)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

/** 统一卡片。深色底 + 极淡描边，不用阴影（深色下阴影只会变脏）。 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    background: Color = Ink800,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = background,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Ink600, RoundedCornerShape(20.dp))
                .padding(18.dp),
            content = content,
        )
    }
}

/** 主按钮。高度 56 dp，触控目标远大于 48 dp 下限。 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Ink900,
            disabledContainerColor = Ink700,
            disabledContentColor = Mist50,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 次级按钮：只有描边，避免和主按钮抢视觉。 */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (enabled) Ink600 else Ink700, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Mist100 else Mist50,
        )
    }
}

/** 小标签。风险色只用在这里和图表里，不做装饰性使用。 */
@Composable
fun Pill(
    text: String,
    color: Color,
    container: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 指标行：左标题右数字，数字等宽，跳动时不抖。 */
@Composable
fun MetricRow(
    label: String,
    value: String,
    unit: String = "",
    hint: String = "",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Mist70)
            if (hint.isNotBlank()) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = Mist50)
            }
        }
        Text(value, style = NumberSmall, color = Mist100)
        if (unit.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.bodySmall, color = Mist50)
        }
    }
}

/**
 * 提示条。错误、警告、说明共用一个形状：
 * 左侧一条竖色条 + 标题 + 说明 +（可选）一个动作。
 */
@Composable
fun NoticeCard(
    title: String,
    message: String,
    accent: Color,
    container: Color,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    SectionCard(background = container) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Mist100)
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = Mist70)
                }
            }
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            GhostButton(text = actionText, onClick = onAction)
        }
    }
}

/** 带百分比的细进度条，用于真实的上传进度。 */
@Composable
fun LabeledProgress(
    label: String,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = Mist70,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${(fraction.coerceIn(0f, 1f) * 100).toInt()}%",
                style = NumberSmall,
                color = Mist70,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Ink700,
        )
    }
}

/** 两步流程的步骤点。第 n / 共 m 步，同时给读屏用户完整描述。 */
@Composable
fun StepDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { contentDescription = "第 $current 步，共 $total 步" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 1..total) {
            Box(
                modifier = Modifier
                    .size(if (i == current) 22.dp else 6.dp, 6.dp)
                    .clip(CircleShape)
                    .background(if (i <= current) MaterialTheme.colorScheme.primary else Ink600)
                    .alpha(if (i <= current) 1f else 0.7f)
            )
        }
    }
}

