package com.copdtrend.demo.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.copdtrend.demo.ui.MainViewModel
import com.copdtrend.demo.ui.components.EmptyHistory
import com.copdtrend.demo.ui.components.GhostButton
import com.copdtrend.demo.ui.components.HistoryRow
import com.copdtrend.demo.ui.components.NoticeCard
import com.copdtrend.demo.ui.components.RiskBarChart
import com.copdtrend.demo.ui.components.ScreenScaffold
import com.copdtrend.demo.ui.components.SectionCard
import com.copdtrend.demo.ui.components.Sparkline
import com.copdtrend.demo.ui.theme.Mist100
import com.copdtrend.demo.ui.theme.Mist50
import com.copdtrend.demo.ui.theme.Mist70
import com.copdtrend.demo.ui.theme.RiskRecheck
import com.copdtrend.demo.ui.theme.RiskRecheckSoft
import com.copdtrend.demo.ui.theme.Teal

/**
 * 七天趋势页。
 *
 * 数据全部来自服务端 GET /api/v1/assessments?days=7 —— 客户端不存历史，
 * 所以换真模型后这一页不需要任何改动，基线口径也不会两边不一致。
 */
@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val items = state.history?.items.orEmpty()

    LaunchedEffect(Unit) { vm.loadHistory() }

    ScreenScaffold(
        title = "七天趋势",
        onBack = onBack,
        trailing = { GhostButton(text = "刷新", onClick = { vm.loadHistory() }) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            state.historyFailure?.let { failure ->
                NoticeCard(
                    title = failure.message,
                    message = failure.hint,
                    accent = RiskRecheck,
                    container = RiskRecheckSoft,
                    actionText = "重新加载",
                    onAction = { vm.loadHistory() },
                )
                Spacer(Modifier.height(16.dp))
            }

            if (items.isEmpty()) {
                if (state.historyLoading) {
                    Text("正在加载…", style = MaterialTheme.typography.bodyMedium, color = Mist70)
                } else {
                    EmptyHistory()
                }
            } else {
                SectionCard {
                    Text("趋势分", style = MaterialTheme.typography.titleMedium, color = Mist100)
                    Text(
                        text = "分值只用于观察变化幅度，不代表病情程度。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist50,
                    )
                    Spacer(Modifier.height(16.dp))
                    RiskBarChart(items = items)
                }
                Spacer(Modifier.height(16.dp))

                SectionCard {
                    Sparkline(
                        values = items.map { it.respiratoryRate },
                        accent = Teal,
                        label = "呼吸频率（次/分）",
                    )
                }
                Spacer(Modifier.height(16.dp))

                SectionCard {
                    Text("每日明细", style = MaterialTheme.typography.titleMedium, color = Mist100)
                    Spacer(Modifier.height(6.dp))
                    items.reversed().forEachIndexed { index, item ->
                        HistoryRow(item = item, isToday = index == 0)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
