package com.copdtrend.demo.domain

/**
 * 领域模型与常量。
 *
 * 风险等级的 key 与服务端完全一致（stable / attention / recheck）；
 * 展示文案优先用服务端返回的 level_label，本地文案只作为兜底，
 * 这样以后服务端改文案不需要发版。
 */
enum class RiskLevel(val key: String, val fallbackLabel: String) {
    STABLE("stable", "状态平稳"),
    ATTENTION("attention", "建议关注"),
    RECHECK("recheck", "建议复测"),
    UNKNOWN("unknown", "暂无数据");

    companion object {
        fun from(key: String?): RiskLevel =
            entries.firstOrNull { it.key == key } ?: UNKNOWN
    }
}

/** 两步录音流程里的当前步骤。 */
enum class RecordingStep(val index: Int, val total: Int, val title: String) {
    BREATHING(1, 2, "呼吸音录制"),
    COUGH(2, 2, "咳嗽音录制"),
}

/** 分析页展示的阶段。前两项是客户端本地事件，其余由服务端 stage 驱动。 */
enum class LocalStage(val key: String, val label: String) {
    QUALITY("local_quality", "检查录音质量"),
    UPLOAD_BREATHING("local_upload_breathing", "上传呼吸音"),
    UPLOAD_COUGH("local_upload_cough", "上传咳嗽音"),
}

/**
 * 服务端 stage 的兜底文案表。
 * 真模型接入后如果新增了阶段，服务端会同时返回 stage_label，
 * 客户端优先用它；这张表只在服务端没给 label 时兜底，因此不会因为
 * 新增阶段而需要改客户端。
 */
val SERVER_STAGE_FALLBACK: Map<String, String> = mapOf(
    "queued" to "已接收，排队中",
    "quality_check" to "检查录音质量",
    "feature_extraction" to "提取声学特征",
    "inference" to "生成趋势结果",
    "done" to "分析完成",
    "failed" to "分析未完成",
)

const val DISCLAIMER = "本结果仅用于健康趋势观察，不构成医学诊断。"

const val DEFAULT_BASE_URL = "http://192.168.1.100:8000"

/** 匿名用户编号：不含姓名，只是一个稳定的标识。 */
const val DEFAULT_USER_ID = "anon-0001"
