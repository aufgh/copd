package com.copdtrend.demo.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 单一深色方向：近黑底 + 单一强调色，风险三色只用于风险语义，不做装饰。
 * 只做深色一套 —— 演示投屏更稳，也不会出现半成品的浅色配色。
 */

// 背景与表面
val Ink900 = Color(0xFF0E1116) // 页面背景
val Ink850 = Color(0xFF12171F) // 分区背景
val Ink800 = Color(0xFF161C26) // 卡面
val Ink700 = Color(0xFF1F2632) // 次级卡面
val Ink600 = Color(0xFF2B3441) // 描边

// 文字
val Mist100 = Color(0xFFEDF1F7)
val Mist70 = Color(0xFFA7B1C0)
val Mist50 = Color(0xFF7A8695)
val Mist30 = Color(0xFF5A6472)

// 强调色
val Teal = Color(0xFF4FD1C5)
val TealSoft = Color(0xFF2C7A7B)
val TealGlow = Color(0x334FD1C5)

// 风险语义色
val RiskStable = Color(0xFF4FD1C5)
val RiskAttention = Color(0xFFF6AD55)
val RiskRecheck = Color(0xFFFC8181)

val RiskStableSoft = Color(0x2E4FD1C5)
val RiskAttentionSoft = Color(0x2EF6AD55)
val RiskRecheckSoft = Color(0x2EFC8181)
