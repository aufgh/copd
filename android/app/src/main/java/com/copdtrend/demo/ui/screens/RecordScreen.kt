package com.copdtrend.demo.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.copdtrend.demo.audio.QualityReport
import com.copdtrend.demo.audio.QualityVerdict
import com.copdtrend.demo.domain.RecordingStep
import com.copdtrend.demo.ui.MainViewModel
import com.copdtrend.demo.ui.components.GhostButton
import com.copdtrend.demo.ui.components.MetricRow
import com.copdtrend.demo.ui.components.NoticeCard
import com.copdtrend.demo.ui.components.PrimaryButton
import com.copdtrend.demo.ui.components.RecordVisual
import com.copdtrend.demo.ui.components.ScreenScaffold
import com.copdtrend.demo.ui.components.SectionCard
import com.copdtrend.demo.ui.components.StepDots
import com.copdtrend.demo.ui.theme.Mist100
import com.copdtrend.demo.ui.theme.Mist50
import com.copdtrend.demo.ui.theme.Mist70
import com.copdtrend.demo.ui.theme.RiskAttention
import com.copdtrend.demo.ui.theme.RiskAttentionSoft
import com.copdtrend.demo.ui.theme.RiskRecheck
import com.copdtrend.demo.ui.theme.RiskRecheckSoft
import com.copdtrend.demo.ui.theme.RiskStable
import com.copdtrend.demo.ui.theme.RiskStableSoft
import com.copdtrend.demo.ui.theme.Teal

/**
 * 录音页。呼吸和咳嗽复用同一个页面，差异只有文案、时长和是否显示呼吸引导圆。
 *
 * 录完立刻做本地粗筛（阈值来自服务端），不合格就直接在这一页拦下来重录，
 * 不让用户白等一次上传。
 */
@Composable
fun RecordScreen(
    vm: MainViewModel,
    step: RecordingStep,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        if (granted) vm.startRecording(step)
    }

    LaunchedEffect(step) { vm.prepareStep(step) }
    DisposableEffect(step) {
        onDispose {
            vm.stopRecording()
            vm.player.stop()
        }
    }

    val report = if (step == RecordingStep.BREATHING) state.breathing else state.cough
    val requirements = state.health?.audioRequirements
    val targetMs = when (step) {
        RecordingStep.BREATHING -> requirements?.breathingTargetMs?.toLong() ?: 20_000L
        RecordingStep.COUGH -> requirements?.coughTargetMs?.toLong() ?: 8_000L
    }
    val minMs = when (step) {
        RecordingStep.BREATHING -> requirements?.breathingMinMs?.toLong() ?: 15_000L
        RecordingStep.COUGH -> requirements?.coughMinMs?.toLong() ?: 2_500L
    }

    ScreenScaffold(
        title = step.title,
        onBack = onBack,
        trailing = { StepDots(current = step.index, total = step.total) },
        bottomBar = {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                when {
                    state.isRecording -> PrimaryButton(
                        text = "结束录音",
                        onClick = { vm.stopRecording() },
                    )

                    report != null && report.ok -> {
                        PrimaryButton(
                            text = if (step == RecordingStep.BREATHING) "下一步：咳嗽录音" else "开始分析",
                            onClick = onNext,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GhostButton(
                                text = "试听",
                                modifier = Modifier.weight(1f),
                                onClick = { vm.togglePlayback(step) {} },
                            )
                            GhostButton(
                                text = "重录",
                                modifier = Modifier.weight(1f),
                                onClick = { startWithPermission(hasPermission, permissionLauncher, vm, step) },
                            )
                        }
                    }

                    else -> PrimaryButton(
                        text = if (report == null) "开始录音" else "重新录音",
                        onClick = { startWithPermission(hasPermission, permissionLauncher, vm, step) },
                    )
                }
            }
        },
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when (step) {
                    RecordingStep.BREATHING -> "跟着圆圈缓慢呼吸"
                    RecordingStep.COUGH -> "自然咳嗽 3 声"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = Mist100,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (step) {
                    RecordingStep.BREATHING -> "圆圈变大时吸气，变小时呼气；手机离口鼻约 15 厘米"
                    RecordingStep.COUGH -> "每声之间稍作停顿，不需要用力"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Mist70,
            )
            Spacer(Modifier.height(20.dp))

            RecordVisual(
                level = state.level,
                elapsedMs = state.elapsedMs,
                targetMs = targetMs,
                minMs = minMs,
                isRecording = state.isRecording,
                showBreathingGuide = step == RecordingStep.BREATHING,
                accent = Teal,
            )
            Spacer(Modifier.height(20.dp))

            if (permissionDenied && !hasPermission) {
                NoticeCard(
                    title = "需要麦克风权限",
                    message = "请在系统设置里允许本应用使用麦克风，然后回到这一页重试。",
                    accent = RiskRecheck,
                    container = RiskRecheckSoft,
                )
                Spacer(Modifier.height(12.dp))
            }

            state.recordError?.let { message ->
                NoticeCard(
                    title = "录音没有成功",
                    message = message,
                    accent = RiskRecheck,
                    container = RiskRecheckSoft,
                )
                Spacer(Modifier.height(12.dp))
            }

            report?.let { QualityCard(it) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 本地质检结果卡片。展示原始指标，演示时可以直接讲"我们检查了什么"。 */
@Composable
private fun QualityCard(report: QualityReport) {
    val (accent, container) = when (report.verdict) {
        QualityVerdict.GOOD -> RiskStable to RiskStableSoft
        QualityVerdict.USABLE -> RiskAttention to RiskAttentionSoft
        QualityVerdict.REJECTED -> RiskRecheck to RiskRecheckSoft
    }
    SectionCard(background = container) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "录音质量：${report.label}",
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${report.outcome.durationMs / 1000} 秒",
                style = MaterialTheme.typography.bodyMedium,
                color = Mist70,
            )
        }
        if (report.issues.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            report.issues.forEach { issue ->
                Text(
                    text = "· $issue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist100,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        MetricRow(
            label = "平均音量",
            value = "%.1f".format(report.outcome.rmsDbfs),
            unit = "dBFS",
        )
        MetricRow(
            label = "削波比例",
            value = "%.2f".format(report.outcome.clipRatio * 100),
            unit = "%",
            hint = if (report.outcome.clipRatio > 0.02f) "音量过大导致波形被削平" else "",
        )
        Text(
            text = "采样 ${report.outcome.sampleRate / 1000} kHz 单声道，与服务端要求一致",
            style = MaterialTheme.typography.bodySmall,
            color = Mist50,
        )
    }
}

private fun startWithPermission(
    hasPermission: Boolean,
    launcher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>,
    vm: MainViewModel,
    step: RecordingStep,
) {
    if (hasPermission) vm.startRecording(step)
    else launcher.launch(Manifest.permission.RECORD_AUDIO)
}
