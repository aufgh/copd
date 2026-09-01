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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.copdtrend.demo.network.AssessmentDto
import com.copdtrend.demo.ui.MainViewModel
import com.copdtrend.demo.ui.components.GhostButton
import com.copdtrend.demo.ui.components.MetricRow
import com.copdtrend.demo.ui.components.Pill
import com.copdtrend.demo.ui.components.PrimaryButton
import com.copdtrend.demo.ui.components.ScreenScaffold
import com.copdtrend.demo.ui.components.SectionCard
import com.copdtrend.demo.ui.theme.Ink700
import com.copdtrend.demo.ui.theme.Mist100
import com.copdtrend.demo.ui.theme.Mist50
import com.copdtrend.demo.ui.theme.Mist70
import com.copdtrend.demo.ui.theme.NumberLarge
import com.copdtrend.demo.ui.theme.RiskAttention
import com.copdtrend.demo.ui.theme.RiskAttentionSoft
import com.copdtrend.demo.ui.theme.riskColor
import com.copdtrend.demo.ui.theme.riskContainerColor

/**
 * 结果页。
 *
 * 所有等级文案都用服务端下发的 level_label / 各种 *_label，
 * 客户端不做任何判定，也不出现"确诊""急性加重"这类字样。
 */
@Composable
fun ResultScreen(vm: MainViewModel, onHome: () -> Unit, onHistory: () -> Unit) {
    val state by vm.state.collectAsState()
    val dto = state.assessment

    ScreenScaffold(
        title = "本次结果",
        onBack = onHome,
        bottomBar = {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                PrimaryButton(text = "查看七天趋势", onClick = onHistory)
                Spacer(Modifier.height(10.dp))
                GhostButton(
                    text = "回到首页",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onHome,
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
            if (dto == null) {
                Text(
                    "还没有结果",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Mist70,
                )
                return@Column
            }
            ConclusionCard(dto)
            Spacer(Modifier.height(16.dp))
            MetricsCard(dto)
            Spacer(Modifier.height(16.dp))
            AdviceCard(dto)
            Spacer(Modifier.height(16.dp))
            QualityAndModelCard(dto)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConclusionCard(dto: AssessmentDto) {
    val risk = dto.risk ?: return
    val color = riskColor(risk.level)
    SectionCard(background = riskContainerColor(risk.level)) {
        Text(
            text = risk.levelLabel.ifBlank { risk.level },
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(risk.score.toString(), style = NumberLarge, color = Mist100)
            Text(
                text = "  / 100 趋势分",
                style = MaterialTheme.typography.bodyMedium,
                color = Mist70,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Text(
            text = "结果置信度 %.0f%%".format(risk.confidence * 100),
            style = MaterialTheme.typography.bodySmall,
            color = Mist50,
        )
        if (risk.reasons.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("判断依据", style = MaterialTheme.typography.titleSmall, color = Mist100)
            Spacer(Modifier.height(6.dp))
            risk.reasons.forEach { reason ->
                Text(
                    text = "· $reason",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist70,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun MetricsCard(dto: AssessmentDto) {
    val m = dto.metrics ?: return
    val base = m.baseline
    SectionCard {
        Text("本次指标", style = MaterialTheme.typography.titleMedium, color = Mist100)
        Spacer(Modifier.height(6.dp))
        MetricRow(
            label = "呼吸频率",
            value = "%.1f".format(m.respiratoryRate),
            unit = "次/分",
            // baseline.* 是与个人基线的差值（服务端算好的），不是均值，所以带正负号显示
            hint = base.respiratoryRate?.let {
                "较近 ${base.daysUsed} 天基线 %+.1f".format(it)
            } ?: "",
        )
        MetricRow(
            label = "呼吸节律",
            value = m.rhythmLabel.ifBlank { "%.2f".format(m.rhythmScore) },
        )
        MetricRow(
            label = "异常呼吸音可能性",
            value = "%.0f".format(m.abnormalSoundProb * 100),
            unit = "%",
        )
        MetricRow(
            label = "咳嗽次数",
            value = m.coughCount.toString(),
            unit = "次",
            hint = base.coughCount?.let {
                "较近 ${base.daysUsed} 天基线 %+.1f 次".format(it)
            } ?: "",
        )
        MetricRow(
            label = "咳嗽强度",
            value = "%.2f".format(m.coughIntensity),
            hint = m.coughTrendLabel,
        )
    }
}

@Composable
private fun AdviceCard(dto: AssessmentDto) {
    val risk = dto.risk ?: return
    if (risk.recommendations.isEmpty()) return
    SectionCard(background = Ink700) {
        Text("可以这样做", style = MaterialTheme.typography.titleMedium, color = Mist100)
        Spacer(Modifier.height(8.dp))
        risk.recommendations.forEach { item ->
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Text("·", style = MaterialTheme.typography.bodyLarge, color = Mist50)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium, color = Mist70)
            }
        }
    }
}

@Composable
private fun QualityAndModelCard(dto: AssessmentDto) {
    val quality = dto.audioQuality
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "录音与引擎",
                style = MaterialTheme.typography.titleMedium,
                color = Mist100,
                modifier = Modifier.weight(1f),
            )
            if (dto.model.engine == "demo") {
                Pill(text = "演示引擎", color = RiskAttention, container = RiskAttentionSoft)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (quality != null) {
            MetricRow(
                label = "呼吸音质量",
                value = quality.breathing.label.ifBlank { if (quality.breathing.ok) "可用" else "不足" },
                hint = quality.breathing.issues.joinToString("；"),
            )
            MetricRow(
                label = "咳嗽音质量",
                value = quality.cough.label.ifBlank { if (quality.cough.ok) "可用" else "不足" },
                hint = quality.cough.issues.joinToString("；"),
            )
        }
        MetricRow(
            label = "引擎版本",
            value = dto.model.version.ifBlank { dto.model.engine },
            hint = dto.model.inferenceMs?.let { "推理耗时 $it ms" } ?: "",
        )
        Text(
            text = "编号 ${dto.assessmentId}",
            style = MaterialTheme.typography.bodySmall,
            color = Mist50,
        )
    }
}
