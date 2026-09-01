package com.copdtrend.demo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.copdtrend.demo.ui.MainViewModel
import com.copdtrend.demo.ui.components.EmptyHistory
import com.copdtrend.demo.ui.components.GhostButton
import com.copdtrend.demo.ui.components.NoticeCard
import com.copdtrend.demo.ui.components.Pill
import com.copdtrend.demo.ui.components.PrimaryButton
import com.copdtrend.demo.ui.components.RiskBarChart
import com.copdtrend.demo.ui.components.SectionCard
import com.copdtrend.demo.ui.components.StatusDial
import com.copdtrend.demo.ui.theme.Ink700
import com.copdtrend.demo.ui.theme.Mist100
import com.copdtrend.demo.ui.theme.Mist50
import com.copdtrend.demo.ui.theme.Mist70
import com.copdtrend.demo.ui.theme.RiskAttention
import com.copdtrend.demo.ui.theme.RiskAttentionSoft
import com.copdtrend.demo.ui.theme.RiskRecheck
import com.copdtrend.demo.ui.theme.RiskRecheckSoft
import com.copdtrend.demo.ui.theme.Teal
import com.copdtrend.demo.ui.theme.TealGlow
import com.copdtrend.demo.ui.components.ScreenScaffold

/**
 * 首页。三件事说清楚：今天的状态、最近七天的走势、开始下一次检测。
 * 页面不出现任何"诊断""确诊"字样，底部固定免责声明由 ScreenScaffold 提供。
 */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val latest = state.latestHistory

    LaunchedEffect(Unit) {
        vm.refreshHealth()
        vm.loadHistory()
    }

    ScreenScaffold(
        title = "呼吸趋势",
        trailing = { GhostButton(text = "设置", onClick = onSettings) },
        bottomBar = {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                PrimaryButton(
                    text = "开始今天的检测",
                    enabled = true,
                    onClick = onStart,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            ConnectionStatus(vm = vm)
            Spacer(Modifier.height(18.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StatusDial(
                    level = latest?.riskLevel ?: "unknown",
                    levelLabel = latest?.riskLevelLabel?.ifBlank { latest.riskLevel } ?: "暂无数据",
                    score = latest?.riskScore ?: 0,
                    caption = if (latest != null) "趋势分" else "先做一次检测",
                )
            }
            Spacer(Modifier.height(18.dp))

            SevenDayCard(vm = vm, onHistory = onHistory)
            Spacer(Modifier.height(16.dp))
            TodayFacts(vm = vm)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 连接状态。
 *
 * 演示当天最容易出问题的就是"手机连不上电脑"，所以把它放在首屏第一眼的位置，
 * 并且把服务端返回的 hint 直接显示出来（IP、同一网络、防火墙）。
 * engine 为 demo 时显示"演示引擎"标记 —— 截图里能看出这不是真实模型的结论。
 */
@Composable
private fun ConnectionStatus(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val health = state.health
    val failure = state.healthFailure

    if (health != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(text = "已连接服务器", color = Teal, container = TealGlow)
            if (state.isDemoEngine) {
                Pill(text = "演示引擎", color = RiskAttention, container = RiskAttentionSoft)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = health.modelVersion.ifBlank { health.engine },
                style = MaterialTheme.typography.bodySmall,
                color = Mist50,
            )
        }
    } else {
        NoticeCard(
            title = "未连接到分析服务",
            // 把真正用到的地址和错误码显示出来：现场排错时一眼就能看出
            // 是地址填错了、服务没起，还是手机不在同一个网络。
            message = buildString {
                append(failure?.message ?: "正在连接…")
                failure?.hint?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
                append("\n当前地址：").append(state.baseUrl)
                failure?.code?.takeIf { it.isNotBlank() }?.let { append("  [").append(it).append(']') }
            },
            accent = RiskRecheck,
            container = RiskRecheckSoft,
            actionText = "重试连接",
            onAction = { vm.refreshHealth() },
        )
    }
}

@Composable
private fun SevenDayCard(vm: MainViewModel, onHistory: () -> Unit) {
    val state by vm.state.collectAsState()
    val items = state.history?.items.orEmpty()

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("近七天趋势", style = MaterialTheme.typography.titleMedium, color = Mist100)
                Text(
                    text = "柱高为趋势分，越高表示越需要关注",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist50,
                )
            }
            GhostButton(text = "详情", onClick = onHistory)
        }
        Spacer(Modifier.height(18.dp))
        if (items.isEmpty()) {
            EmptyHistory()
        } else {
            RiskBarChart(items = items)
        }
    }
}

@Composable
private fun TodayFacts(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val latest = state.latestHistory ?: return

    SectionCard(background = Ink700) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "最近一次：${latest.date}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Mist100,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "呼吸 %.1f 次/分  ·  咳嗽 %d 次  ·  录音%s".format(
                        latest.respiratoryRate,
                        latest.coughCount,
                        latest.qualityLabel.ifBlank { "—" },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist70,
                )
            }
        }
    }
}
