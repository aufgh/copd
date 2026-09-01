package com.copdtrend.demo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ColorScheme = darkColorScheme(
    primary = Teal,
    onPrimary = Ink900,
    primaryContainer = TealSoft,
    onPrimaryContainer = Mist100,
    secondary = Mist70,
    onSecondary = Ink900,
    background = Ink900,
    onBackground = Mist100,
    surface = Ink800,
    onSurface = Mist100,
    surfaceVariant = Ink700,
    onSurfaceVariant = Mist70,
    outline = Ink600,
    outlineVariant = Ink700,
    error = RiskRecheck,
    onError = Ink900,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * 全局主题。所有页面只从 MaterialTheme 或本文件的辅助函数取颜色，
 * 不在页面里写死色值，后期换配色一处改完。
 */
@Composable
fun CopdTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** 风险等级 -> 主色。三处 UI（首页状态盘、结果页结论块、历史图）共用。 */
fun riskColor(level: String): Color = when (level) {
    "stable" -> RiskStable
    "attention" -> RiskAttention
    "recheck" -> RiskRecheck
    else -> Mist50
}

/** 风险等级 -> 半透明背景色。 */
fun riskContainerColor(level: String): Color = when (level) {
    "stable" -> RiskStableSoft
    "attention" -> RiskAttentionSoft
    "recheck" -> RiskRecheckSoft
    else -> Ink700
}
