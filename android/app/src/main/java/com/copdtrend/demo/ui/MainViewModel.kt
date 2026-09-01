package com.copdtrend.demo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.copdtrend.demo.CopdApp
import com.copdtrend.demo.audio.AudioPlayer
import com.copdtrend.demo.audio.AudioRecorder
import com.copdtrend.demo.audio.QualityReport
import com.copdtrend.demo.domain.DEFAULT_BASE_URL
import com.copdtrend.demo.domain.DEFAULT_USER_ID
import com.copdtrend.demo.domain.RecordingStep
import com.copdtrend.demo.domain.SERVER_STAGE_FALLBACK
import com.copdtrend.demo.network.ApiFailure
import com.copdtrend.demo.network.ApiResult
import com.copdtrend.demo.network.AssessmentDto
import com.copdtrend.demo.network.ErrorDto
import com.copdtrend.demo.network.HealthDto
import com.copdtrend.demo.network.HistoryDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 全局 UI 状态。整个演示只有一个 ViewModel（Activity 作用域），
 * 录音结果需要跨页面（呼吸页 -> 咳嗽页 -> 分析页）传递，
 * 用一份状态比在导航参数里搬文件路径更不容易出错。
 */
data class UiState(
    val health: HealthDto? = null,
    val healthFailure: ApiFailure? = null,
    val healthLoading: Boolean = false,
    val baseUrl: String = DEFAULT_BASE_URL,
    val userId: String = DEFAULT_USER_ID,
    val scenario: String = "",
    // 录音
    val step: RecordingStep = RecordingStep.BREATHING,
    val isRecording: Boolean = false,
    val level: Float = 0f,
    val elapsedMs: Long = 0L,
    val breathing: QualityReport? = null,
    val cough: QualityReport? = null,
    val recordError: String? = null,
    // 分析
    val analyzing: Boolean = false,
    val uploadBreathing: Float = 0f,
    val uploadCough: Float = 0f,
    val serverStages: List<StageRow> = emptyList(),
    val currentStage: String = "",
    val serverProgress: Int = 0,
    val assessment: AssessmentDto? = null,
    val failure: ApiFailure? = null,
    // 历史
    val history: HistoryDto? = null,
    val historyLoading: Boolean = false,
    val historyFailure: ApiFailure? = null,
) {
    val canAnalyze: Boolean get() = breathing?.ok == true && cough?.ok == true
    val latestHistory get() = history?.items?.lastOrNull()
    val isDemoEngine: Boolean get() = health?.isDemo ?: true
    val serverReachable: Boolean get() = health != null
}

/** 分析页的一行阶段。label 来自服务端，新增阶段不需要改客户端。 */
data class StageRow(val key: String, val label: String)

class MainViewModel(app: CopdApp) : ViewModel() {

    private val repo = app.repository
    private val settings = app.settings
    private val recorder = AudioRecorder()

    /** 试听播放器。UI 直接用，不进状态流（播放状态变化太频繁）。 */
    val player = AudioPlayer()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var clientAssessmentId: String? = null
    private var recordJob: Job? = null
    private var submitJob: Job? = null

    init {
        viewModelScope.launch {
            settings.baseUrl.collect { v -> _state.update { it.copy(baseUrl = v) } }
        }
        viewModelScope.launch {
            settings.userId.collect { v -> _state.update { it.copy(userId = v) } }
        }
        viewModelScope.launch {
            settings.scenario.collect { v -> _state.update { it.copy(scenario = v) } }
        }
        refreshHealth()
        loadHistory()
    }

    override fun onCleared() {
        player.stop()
        recorder.requestStop()
        super.onCleared()
    }

    // ------------------------------------------------------------ 连接与设置

    fun refreshHealth() {
        viewModelScope.launch {
            // 冷启动时 DataStore 还没读出用户存过的地址，先等一下再发请求
            settings.awaitReady()
            _state.update { it.copy(healthLoading = true) }
            when (val result = repo.refreshHealth()) {
                is ApiResult.Ok -> _state.update {
                    it.copy(health = result.value, healthFailure = null, healthLoading = false)
                }

                is ApiResult.Err -> _state.update {
                    it.copy(healthFailure = result.failure, healthLoading = false)
                }
            }
        }
    }

    fun setBaseUrl(value: String) {
        viewModelScope.launch {
            settings.setBaseUrl(value)
            refreshHealth()
            loadHistory()
        }
    }

    fun setUserId(value: String) {
        viewModelScope.launch {
            settings.setUserId(value)
            loadHistory()
        }
    }

    /** 空字符串表示"跟随服务端默认"。 */
    fun setScenario(value: String) {
        viewModelScope.launch { settings.setScenario(value) }
    }

    // ------------------------------------------------------------ 录音

    fun prepareStep(step: RecordingStep) {
        player.stop()
        _state.update { it.copy(step = step, recordError = null, level = 0f, elapsedMs = 0L) }
    }

    fun startRecording(step: RecordingStep) {
        if (_state.value.isRecording) return
        player.stop()
        val target = repo.recordingFile(step)
        val maxMs = repo.checker.targetMs(step)
        _state.update {
            val cleared = when (step) {
                RecordingStep.BREATHING -> it.copy(breathing = null)
                RecordingStep.COUGH -> it.copy(cough = null)
            }
            cleared.copy(
                step = step,
                isRecording = true,
                level = 0f,
                elapsedMs = 0L,
                recordError = null,
            )
        }
        recordJob = viewModelScope.launch {
            try {
                val outcome = recorder.record(target, maxMs) { level, elapsed ->
                    _state.update { it.copy(level = level, elapsedMs = elapsed) }
                }
                val report = repo.checker.check(step, outcome)
                _state.update {
                    val withReport = when (step) {
                        RecordingStep.BREATHING -> it.copy(breathing = report)
                        RecordingStep.COUGH -> it.copy(cough = report)
                    }
                    withReport.copy(isRecording = false, level = 0f)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isRecording = false, level = 0f, recordError = e.message ?: "录音失败")
                }
            }
        }
    }

    fun stopRecording() = recorder.requestStop()

    fun togglePlayback(step: RecordingStep, onFinished: () -> Unit) {
        player.toggle(repo.recordingFile(step), onFinished)
    }

    fun fileFor(step: RecordingStep) = repo.recordingFile(step)

    /** 回首页时清掉本轮录音与结果，避免下一轮看到上一轮的残留。 */
    fun resetSession() {
        player.stop()
        recorder.requestStop()
        submitJob?.cancel()
        clientAssessmentId = null
        _state.update {
            it.copy(
                step = RecordingStep.BREATHING,
                isRecording = false,
                level = 0f,
                elapsedMs = 0L,
                breathing = null,
                cough = null,
                recordError = null,
                analyzing = false,
                uploadBreathing = 0f,
                uploadCough = 0f,
                serverStages = emptyList(),
                currentStage = "",
                serverProgress = 0,
                assessment = null,
                failure = null,
            )
        }
    }

    // ------------------------------------------------------------ 上传与分析

    /** 开始一轮新的分析。会生成新的幂等键。 */
    fun startAnalysis() {
        if (!_state.value.canAnalyze) return
        clientAssessmentId = repo.newClientAssessmentId()
        _state.update {
            it.copy(
                analyzing = true,
                uploadBreathing = 0f,
                uploadCough = 0f,
                serverStages = emptyList(),
                currentStage = "",
                serverProgress = 0,
                assessment = null,
                failure = null,
            )
        }
        submit()
    }

    /**
     * 重试上传。刻意复用同一个 client_assessment_id ——
     * 服务端按它去重，弱网重传不会产生第二条记录，用户也不用重新录音。
     */
    fun retryUpload() {
        if (clientAssessmentId == null) return
        _state.update {
            it.copy(analyzing = true, failure = null, uploadBreathing = 0f, uploadCough = 0f)
        }
        submit()
    }

    private fun submit() {
        val clientId = clientAssessmentId ?: return
        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            val created = repo.upload(
                clientAssessmentId = clientId,
                breathing = repo.recordingFile(RecordingStep.BREATHING),
                cough = repo.recordingFile(RecordingStep.COUGH),
            ) { kind, fraction ->
                _state.update {
                    if (kind == "breathing") it.copy(uploadBreathing = fraction)
                    else it.copy(uploadCough = fraction)
                }
            }

            when (created) {
                is ApiResult.Err -> {
                    _state.update { it.copy(analyzing = false, failure = created.failure) }
                    return@launch
                }

                is ApiResult.Ok -> {
                    _state.update { it.copy(uploadBreathing = 1f, uploadCough = 1f) }
                    trackStage(
                        created.value.stage,
                        created.value.stageLabel,
                        created.value.progress,
                    )
                    awaitResult(created.value.assessmentId, created.value.pollAfterMs)
                }
            }
        }
    }

    private suspend fun awaitResult(assessmentId: String, firstDelayMs: Long) {
        val polled = repo.poll(assessmentId, firstDelayMs) { snapshot ->
            trackStage(snapshot.stage, snapshot.stageLabel, snapshot.progress)
            _state.update { it.copy(assessment = snapshot) }
        }
        when (polled) {
            is ApiResult.Ok -> {
                val dto = polled.value
                _state.update {
                    it.copy(
                        analyzing = false,
                        assessment = dto,
                        failure = if (dto.isSucceeded) null else dto.error.toFailure(),
                    )
                }
                if (dto.isSucceeded) loadHistory()
            }

            is ApiResult.Err ->
                _state.update { it.copy(analyzing = false, failure = polled.failure) }
        }
    }

    /**
     * 记录服务端阶段。用"见到就追加"的方式维护列表，
     * 真模型接入后即使多出新的阶段，这里也会自动展示，不需要发版。
     */
    private fun trackStage(key: String, label: String, progress: Int) {
        if (key.isBlank()) return
        val text = label.ifBlank { SERVER_STAGE_FALLBACK[key] ?: key }
        _state.update { st ->
            val rows =
                if (st.serverStages.any { it.key == key }) st.serverStages
                else st.serverStages + StageRow(key, text)
            st.copy(serverStages = rows, currentStage = key, serverProgress = progress)
        }
    }

    // ------------------------------------------------------------ 历史

    fun loadHistory(days: Int = 7) {
        viewModelScope.launch {
            settings.awaitReady()
            _state.update { it.copy(historyLoading = true) }
            when (val result = repo.history(days)) {
                is ApiResult.Ok -> _state.update {
                    it.copy(history = result.value, historyFailure = null, historyLoading = false)
                }

                is ApiResult.Err -> _state.update {
                    it.copy(historyFailure = result.failure, historyLoading = false)
                }
            }
        }
    }

    class Factory(private val app: CopdApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(app) as T
    }
}

/** 服务端错误体 -> UI 错误模型。needs_rerecord 决定是"重试上传"还是"重新录音"。 */
private fun ErrorDto?.toFailure(): ApiFailure? {
    val dto = this ?: return null
    return ApiFailure(
        code = dto.code,
        message = dto.message.ifBlank { "分析未完成" },
        hint = dto.hint,
        needsRerecord = dto.needsRerecord,
    )
}
