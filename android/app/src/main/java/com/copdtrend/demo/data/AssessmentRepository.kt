package com.copdtrend.demo.data

import com.copdtrend.demo.audio.QualityChecker
import com.copdtrend.demo.domain.RecordingStep
import com.copdtrend.demo.network.ApiClient
import com.copdtrend.demo.network.ApiFailure
import com.copdtrend.demo.network.ApiResult
import com.copdtrend.demo.network.AssessmentDto
import com.copdtrend.demo.network.AudioRequirementsDto
import com.copdtrend.demo.network.CreateAssessmentDto
import com.copdtrend.demo.network.HealthDto
import com.copdtrend.demo.network.HistoryDto
import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 唯一的数据入口。
 *
 * 三件事：录音文件放哪、阈值从哪来、一次评估怎么走完。
 * 历史与基线都在服务端，客户端不存业务数据 —— 换真模型时这一层不用动。
 */
class AssessmentRepository(
    private val api: ApiClient,
    private val settings: SettingsStore,
    private val audioDir: File,
) {

    private val _health = MutableStateFlow<HealthDto?>(null)
    val health: StateFlow<HealthDto?> = _health.asStateFlow()

    private val _requirements = MutableStateFlow(AudioRequirementsDto())
    val requirements: StateFlow<AudioRequirementsDto> = _requirements.asStateFlow()

    /** 质检器随阈值一起更新。服务端调阈值，客户端下次连上就跟着变。 */
    val checker: QualityChecker get() = QualityChecker(_requirements.value)

    // ---------------------------------------------------------------- 文件

    init {
        audioDir.mkdirs()
    }

    /** 录音落地路径。固定两个文件名，重录直接覆盖，不会堆垃圾。 */
    fun recordingFile(step: RecordingStep): File {
        val name = when (step) {
            RecordingStep.BREATHING -> "breathing.wav"
            RecordingStep.COUGH -> "cough.wav"
        }
        return File(audioDir, name)
    }

    fun clearRecordings() {
        RecordingStep.entries.forEach { recordingFile(it).delete() }
    }

    // ---------------------------------------------------------------- 接口

    suspend fun refreshHealth(): ApiResult<HealthDto> {
        val result = api.health()
        if (result is ApiResult.Ok) {
            _health.value = result.value
            _requirements.value = result.value.audioRequirements
        }
        return result
    }

    /** 新一轮评估的幂等键。上传失败重试时必须复用同一个值。 */
    fun newClientAssessmentId(): String = UUID.randomUUID().toString()

    suspend fun upload(
        clientAssessmentId: String,
        breathing: File,
        cough: File,
        symptomScore: Int? = null,
        spo2: Int? = null,
        onProgress: (kind: String, fraction: Float) -> Unit,
    ): ApiResult<CreateAssessmentDto> = api.createAssessment(
        userId = settings.userIdSnapshot,
        clientAssessmentId = clientAssessmentId,
        recordedAtIso = OffsetDateTime.now().toString(),
        breathing = breathing,
        cough = cough,
        scenario = settings.scenarioSnapshot.takeIf { it.isNotBlank() },
        symptomScore = symptomScore,
        spo2 = spo2,
        onProgress = onProgress,
    )

    /**
     * 轮询直到 succeeded / failed。
     *
     * 每次拿到快照都回调一次，分析页因此显示的是服务端真实阶段，
     * 不是本地假装的进度动画。轮询期间的单次网络抖动不算失败，
     * 连续失败到 [MAX_POLL_FAILURES] 次才放弃。
     */
    suspend fun poll(
        assessmentId: String,
        firstDelayMs: Long,
        onSnapshot: (AssessmentDto) -> Unit,
    ): ApiResult<AssessmentDto> {
        delay(firstDelayMs.coerceIn(200L, 3_000L))
        var failures = 0
        var waited = firstDelayMs
        var lastError: ApiResult.Err? = null

        while (waited < POLL_TIMEOUT_MS) {
            when (val result = api.getAssessment(assessmentId)) {
                is ApiResult.Ok -> {
                    failures = 0
                    onSnapshot(result.value)
                    if (result.value.isFinished) return result
                }

                is ApiResult.Err -> {
                    lastError = result
                    failures++
                    if (failures >= MAX_POLL_FAILURES) return result
                }
            }
            delay(POLL_INTERVAL_MS)
            waited += POLL_INTERVAL_MS
        }
        return lastError ?: ApiResult.Err(
            ApiFailure(
                code = "POLL_TIMEOUT",
                message = "分析用时超过预期",
                hint = "可以重新提交一次，录音不需要重录。",
            )
        )
    }

    suspend fun history(days: Int = 7): ApiResult<HistoryDto> =
        api.history(settings.userIdSnapshot, days)

    private companion object {
        const val POLL_INTERVAL_MS = 700L
        const val POLL_TIMEOUT_MS = 90_000L
        const val MAX_POLL_FAILURES = 4
    }
}
