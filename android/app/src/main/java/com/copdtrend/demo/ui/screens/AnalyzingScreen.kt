package com.copdtrend.demo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.copdtrend.demo.ui.MainViewModel
import com.copdtrend.demo.ui.components.GhostButton
import com.copdtrend.demo.ui.components.LabeledProgress
import com.copdtrend.demo.ui.components.NoticeCard
import com.copdtrend.demo.ui.components.ScreenScaffold
import com.copdtrend.demo.ui.components.SectionCard
import com.copdtrend.demo.ui.theme.Ink600
import com.copdtrend.demo.ui.theme.Ink900
import com.copdtrend.demo.ui.theme.Mist100
import com.copdtrend.demo.ui.theme.Mist50
import com.copdtrend.demo.ui.theme.Mist70
import com.copdtrend.demo.ui.theme.RiskRecheck
import com.copdtrend.demo.ui.theme.RiskRecheckSoft
import com.copdtrend.demo.ui.theme.Teal

/**
 * 上传与分析页。
 *
 * 上传百分比是 OkHttp 写入回调给的真实进度；
 * 阶段列表来自服务端返回的 stage / stage_label，服务端加阶段这里自动多一行。
 *
 * 失败时区分两类：needs_rerecord 为 false 只需重传（录音还在），
 * 为 true 才让用户回去重录 —— 这是计划里明确要求的行为。
 */
@Composable
fun AnalyzingScreen(
    vm: MainViewModel,
    onDone: () -> Unit,
    onGiveUp: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val assessment = state.assessment

    // 分析进行中拦住系统返回键：这一页被 pop 掉后，等结果的 LaunchedEffect 会被取消，
    // 结果出来了也不会跳转到结果页。分析结束（成功或失败）后返回键走回首页。
    BackHandler { if (!state.analyzing) onGiveUp() }

    LaunchedEffect(assessment?.status) {
        if (assessment?.isSucceeded == true) onDone()
    }

    ScreenScaffold(title = "正在分析", onBack = null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "正在分析这两段录音",
                style = MaterialTheme.typography.headlineSmall,
                color = Mist100,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "分析在你自己的电脑上完成，录音不会离开局域网。",
                style = MaterialTheme.typography.bodyMedium,
                color = Mist70,
            )
            Spacer(Modifier.height(20.dp))

            SectionCard {
                LabeledProgress(label = "上传呼吸音", fraction = state.uploadBreathing)
                Spacer(Modifier.height(14.dp))
                LabeledProgress(label = "上传咳嗽音", fraction = state.uploadCough)
            }
            Spacer(Modifier.height(16.dp))

            SectionCard {
                Text("服务端处理", style = MaterialTheme.typography.titleSmall, color = Mist100)
                Spacer(Modifier.height(12.dp))
                if (state.serverStages.isEmpty()) {
                    StageLine(label = "等待服务端接收", done = false, active = true)
                } else {
                    state.serverStages.forEachIndexed { index, row ->
                        val isCurrent = row.key == state.currentStage
                        StageLine(
                            label = row.label,
                            done = !isCurrent || row.key == "done",
                            active = isCurrent && row.key != "done",
                        )
                        if (index != state.serverStages.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
                if (state.serverProgress > 0) {
                    Spacer(Modifier.height(14.dp))
                    LabeledProgress(label = "整体进度", fraction = state.serverProgress / 100f)
                }
            }

            val failure = state.failure
            if (failure != null) {
                Spacer(Modifier.height(16.dp))
                NoticeCard(
                    title = failure.message,
                    message = failure.hint.ifBlank {
                        if (failure.needsRerecord) "请返回重新录制这两段声音。"
                        else "录音还在手机里，可以直接重传，不需要重新录。"
                    },
                    accent = RiskRecheck,
                    container = RiskRecheckSoft,
                    actionText = if (failure.canRetryUpload) "重试上传" else "返回重新录音",
                    onAction = if (failure.canRetryUpload) {
                        { vm.retryUpload() }
                    } else {
                        onGiveUp
                    },
                )
                Spacer(Modifier.height(10.dp))
                GhostButton(text = "回到首页", onClick = onGiveUp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 阶段行。用文字 + 圆点双通道表示状态，不依赖颜色。 */
@Composable
private fun StageLine(label: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (done || active) Teal else Ink600),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (done) "✓" else if (active) "…" else "",
                style = MaterialTheme.typography.labelSmall,
                color = Ink900,
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done || active) Mist100 else Mist50,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
