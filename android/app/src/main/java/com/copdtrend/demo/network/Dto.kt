package com.copdtrend.demo.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 与服务端 schema v1.0 对齐的响应模型。
 *
 * 两条容错原则，让服务端换真模型后客户端不用改：
 * 1. Json 解析开了 ignoreUnknownKeys，服务端新增字段不会导致解析失败。
 * 2. 每个字段都有默认值，服务端省略字段也不会崩。
 */

@Serializable
data class ScenarioDto(
    val key: String = "",
    val label: String = "",
)

@Serializable
data class AudioRequirementsDto(
    @SerialName("sample_rate") val sampleRate: Int = 16_000,
    val channels: Int = 1,
    @SerialName("bits_per_sample") val bitsPerSample: Int = 16,
    @SerialName("breathing_target_ms") val breathingTargetMs: Int = 20_000,
    @SerialName("breathing_min_ms") val breathingMinMs: Int = 15_000,
    @SerialName("cough_min_ms") val coughMinMs: Int = 2_500,
    @SerialName("cough_target_ms") val coughTargetMs: Int = 8_000,
    @SerialName("min_rms_dbfs") val minRmsDbfs: Float = -45f,
    @SerialName("warn_rms_dbfs") val warnRmsDbfs: Float = -38f,
    @SerialName("max_clip_ratio") val maxClipRatio: Float = 0.02f,
    @SerialName("max_upload_mb") val maxUploadMb: Int = 25,
)

@Serializable
data class HealthDto(
    val status: String = "ok",
    @SerialName("schema_version") val schemaVersion: String = "1.0",
    @SerialName("supported_schema_versions") val supportedSchemaVersions: List<String> = emptyList(),
    val engine: String = "demo",
    @SerialName("model_version") val modelVersion: String = "",
    @SerialName("demo_scenario") val demoScenario: String? = null,
    @SerialName("available_scenarios") val availableScenarios: List<ScenarioDto> = emptyList(),
    @SerialName("audio_requirements") val audioRequirements: AudioRequirementsDto = AudioRequirementsDto(),
) {
    val isDemo: Boolean get() = engine == "demo"
}

@Serializable
data class ErrorDto(
    val code: String = "INTERNAL_ERROR",
    val message: String = "",
    val hint: String = "",
    val field: String? = null,
    @SerialName("needs_rerecord") val needsRerecord: Boolean = false,
)

@Serializable
data class QualityItemDto(
    val ok: Boolean = true,
    val label: String = "",
    @SerialName("sample_rate") val sampleRate: Int = 0,
    val channels: Int = 1,
    @SerialName("duration_ms") val durationMs: Int = 0,
    @SerialName("rms_dbfs") val rmsDbfs: Float = 0f,
    @SerialName("peak_dbfs") val peakDbfs: Float = 0f,
    @SerialName("clip_ratio") val clipRatio: Float = 0f,
    val issues: List<String> = emptyList(),
)

@Serializable
data class AudioQualityDto(
    val breathing: QualityItemDto = QualityItemDto(),
    val cough: QualityItemDto = QualityItemDto(),
    @SerialName("overall_label") val overallLabel: String = "",
)

@Serializable
data class BaselineDto(
    @SerialName("days_used") val daysUsed: Int = 0,
    @SerialName("respiratory_rate") val respiratoryRate: Float? = null,
    @SerialName("cough_count") val coughCount: Float? = null,
    @SerialName("cough_intensity") val coughIntensity: Float? = null,
)

@Serializable
data class MetricsDto(
    @SerialName("respiratory_rate") val respiratoryRate: Float = 0f,
    @SerialName("rhythm_score") val rhythmScore: Float = 0f,
    @SerialName("rhythm_label") val rhythmLabel: String = "",
    @SerialName("abnormal_sound_prob") val abnormalSoundProb: Float = 0f,
    @SerialName("cough_count") val coughCount: Int = 0,
    @SerialName("cough_intensity") val coughIntensity: Float = 0f,
    @SerialName("cough_trend_label") val coughTrendLabel: String = "",
    val baseline: BaselineDto = BaselineDto(),
)

@Serializable
data class RiskDto(
    val level: String = "stable",
    @SerialName("level_label") val levelLabel: String = "",
    val score: Int = 0,
    val confidence: Float = 0f,
    val reasons: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
)

@Serializable
data class ModelInfoDto(
    val engine: String = "demo",
    val version: String = "",
    val scenario: String? = null,
    @SerialName("inference_ms") val inferenceMs: Int? = null,
)

@Serializable
data class AssessmentDto(
    @SerialName("schema_version") val schemaVersion: String = "1.0",
    @SerialName("assessment_id") val assessmentId: String = "",
    @SerialName("client_assessment_id") val clientAssessmentId: String? = null,
    @SerialName("user_id") val userId: String = "",
    val status: String = "queued",
    val stage: String = "queued",
    @SerialName("stage_label") val stageLabel: String = "",
    val progress: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("completed_at") val completedAt: String? = null,
    val model: ModelInfoDto = ModelInfoDto(),
    @SerialName("audio_quality") val audioQuality: AudioQualityDto? = null,
    val metrics: MetricsDto? = null,
    val risk: RiskDto? = null,
    val error: ErrorDto? = null,
) {
    val isFinished: Boolean get() = status == "succeeded" || status == "failed"
    val isSucceeded: Boolean get() = status == "succeeded"
}

@Serializable
data class CreateAssessmentDto(
    @SerialName("schema_version") val schemaVersion: String = "1.0",
    @SerialName("assessment_id") val assessmentId: String = "",
    @SerialName("client_assessment_id") val clientAssessmentId: String? = null,
    val status: String = "queued",
    val stage: String = "queued",
    @SerialName("stage_label") val stageLabel: String = "",
    val progress: Int = 0,
    @SerialName("poll_after_ms") val pollAfterMs: Long = 600,
    val deduplicated: Boolean = false,
)

@Serializable
data class HistoryItemDto(
    val date: String = "",
    @SerialName("assessment_id") val assessmentId: String = "",
    @SerialName("risk_level") val riskLevel: String = "stable",
    @SerialName("risk_level_label") val riskLevelLabel: String = "",
    @SerialName("risk_score") val riskScore: Int = 0,
    @SerialName("respiratory_rate") val respiratoryRate: Float = 0f,
    @SerialName("cough_count") val coughCount: Int = 0,
    @SerialName("quality_label") val qualityLabel: String = "",
    val source: String = "seed",
)

@Serializable
data class HistoryDto(
    @SerialName("schema_version") val schemaVersion: String = "1.0",
    @SerialName("user_id") val userId: String = "",
    val days: Int = 7,
    val items: List<HistoryItemDto> = emptyList(),
)
