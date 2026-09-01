package com.copdtrend.demo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 层级靠字号和字重拉开，不靠分割线和阴影堆叠。
 *
 * 关键细节：所有会跳动的数字（呼吸频率、倒计时、咳嗽次数）都用
 * NumberLarge / NumberMedium，里面开了 "tnum"（等宽数字）。
 * 不开的话数字变化时整行宽度会抖，录音页倒计时尤其明显。
 */
private val Tabular = "tnum"

val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.6.sp,
    ),
)

/** 大号指标数字，等宽，用于结果页与状态盘。 */
val NumberLarge = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 44.sp,
    lineHeight = 48.sp,
    fontFeatureSettings = Tabular,
    letterSpacing = (-1).sp,
)

/** 中号指标数字，等宽，用于卡片与倒计时。 */
val NumberMedium = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 26.sp,
    lineHeight = 30.sp,
    fontFeatureSettings = Tabular,
)

/** 小号等宽数字，用于表格化的历史列表。 */
val NumberSmall = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = Tabular,
)
