package com.copdtrend.demo.audio

import com.copdtrend.demo.domain.RecordingStep
import com.copdtrend.demo.network.AudioRequirementsDto

/** 本地粗筛结论。REJECTED 才拦截用户，USABLE 只提示。 */
enum class QualityVerdict(val label: String) {
    GOOD("良好"),
    USABLE("可用"),
    REJECTED("需重录"),
}

data class QualityReport(
    val verdict: QualityVerdict,
    val issues: List<String>,
    val outcome: RecordOutcome,
) {
    val ok: Boolean get() = verdict != QualityVerdict.REJECTED
    val label: String get() = verdict.label
}

/**
 * 本地粗筛。阈值全部来自服务端 /health 下发的 audio_requirements，
 * 客户端不写死数字 —— 服务端调阈值时两边不会漂移。
 *
 * 这里只做"明显不合格"的拦截（空录音、太短、太轻、削波），
 * 真正的判定仍然由服务端做，客户端只是省掉一次白跑的上传。
 */
class QualityChecker(private val requirements: AudioRequirementsDto) {

    fun check(step: RecordingStep, outcome: RecordOutcome): QualityReport {
        val minMs = when (step) {
            RecordingStep.BREATHING -> requirements.breathingMinMs
            RecordingStep.COUGH -> requirements.coughMinMs
        }
        val issues = mutableListOf<String>()
        var verdict = QualityVerdict.GOOD

        if (outcome.durationMs <= 0) {
            return QualityReport(
                verdict = QualityVerdict.REJECTED,
                issues = listOf("没有采集到声音，请检查麦克风权限后重录"),
                outcome = outcome,
            )
        }

        if (outcome.durationMs < minMs) {
            issues += "录音时长不足 ${minMs / 1000} 秒（本次 ${outcome.durationMs / 1000} 秒）"
            verdict = QualityVerdict.REJECTED
        }

        if (outcome.rmsDbfs < requirements.minRmsDbfs) {
            issues += "音量偏低，请把手机靠近一些再录一次"
            verdict = QualityVerdict.REJECTED
        } else if (outcome.rmsDbfs < requirements.warnRmsDbfs) {
            issues += "音量偏小，结果可能不够稳定"
            if (verdict == QualityVerdict.GOOD) verdict = QualityVerdict.USABLE
        }

        if (outcome.clipRatio > requirements.maxClipRatio) {
            issues += "声音过大出现削波，请把手机稍微拿远一点"
            verdict = QualityVerdict.REJECTED
        }

        return QualityReport(verdict = verdict, issues = issues, outcome = outcome)
    }

    /** 呼吸音建议录到目标时长；用于录音页的进度环。 */
    fun targetMs(step: RecordingStep): Long = when (step) {
        RecordingStep.BREATHING -> requirements.breathingTargetMs.toLong()
        RecordingStep.COUGH -> requirements.coughTargetMs.toLong()
    }

    fun minMs(step: RecordingStep): Long = when (step) {
        RecordingStep.BREATHING -> requirements.breathingMinMs.toLong()
        RecordingStep.COUGH -> requirements.coughMinMs.toLong()
    }
}
