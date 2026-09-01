package com.copdtrend.demo.network

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

/** 统一的调用结果。UI 只根据 code 决定行为，不解析文案。 */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val failure: ApiFailure) : ApiResult<Nothing>
}

data class ApiFailure(
    val code: String,
    val message: String,
    val hint: String = "",
    val needsRerecord: Boolean = false,
    val httpStatus: Int? = null,
    val isNetwork: Boolean = false,
) {
    /** 网络类和引擎类错误只需重传，不需要让用户重新录音。 */
    val canRetryUpload: Boolean get() = !needsRerecord
}

object ErrorCodes {
    const val NETWORK_UNREACHABLE = "NETWORK_UNREACHABLE"
    const val BAD_BASE_URL = "BAD_BASE_URL"
    const val PARSE_FAILED = "PARSE_FAILED"
    const val SCHEMA_MISMATCH = "SCHEMA_MISMATCH"
}

class ApiClient(private val baseUrlProvider: () -> String) {

    private val json = Json {
        ignoreUnknownKeys = true      // 服务端加字段不会击穿客户端
        isLenient = true
        coerceInputValues = true      // null 落到默认值，而不是抛异常
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ------------------------------------------------------------ 公共接口

    suspend fun health(): ApiResult<HealthDto> = get("health") { body ->
        json.decodeFromString(HealthDto.serializer(), body)
    }

    suspend fun getAssessment(assessmentId: String): ApiResult<AssessmentDto> =
        get("assessments/$assessmentId") { body ->
            json.decodeFromString(AssessmentDto.serializer(), body)
        }

    suspend fun history(userId: String, days: Int = 7): ApiResult<HistoryDto> =
        get("assessments", mapOf("user_id" to userId, "days" to days.toString())) { body ->
            json.decodeFromString(HistoryDto.serializer(), body)
        }

    suspend fun createAssessment(
        userId: String,
        clientAssessmentId: String,
        recordedAtIso: String,
        breathing: File,
        cough: File,
        scenario: String?,
        symptomScore: Int? = null,
        spo2: Int? = null,
        onProgress: (kind: String, fraction: Float) -> Unit = { _, _ -> },
    ): ApiResult<CreateAssessmentDto> = withContext(Dispatchers.IO) {
        val url = url("assessments") ?: return@withContext badBaseUrl()
        val wav = "audio/wav".toMediaType()

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("user_id", userId)
            .addFormDataPart("client_assessment_id", clientAssessmentId)
            .addFormDataPart("recorded_at", recordedAtIso)
            .addFormDataPart(
                "breathing_file", breathing.name,
                ProgressBody(breathing, wav) { onProgress("breathing", it) },
            )
            .addFormDataPart(
                "cough_file", cough.name,
                ProgressBody(cough, wav) { onProgress("cough", it) },
            )
        scenario?.takeIf { it.isNotBlank() }?.let { builder.addFormDataPart("demo_scenario", it) }
        symptomScore?.let { builder.addFormDataPart("symptom_score", it.toString()) }
        spo2?.let { builder.addFormDataPart("spo2", it.toString()) }

        val request = Request.Builder().url(url).post(builder.build()).build()
        execute(request) { body ->
            json.decodeFromString(CreateAssessmentDto.serializer(), body)
        }
    }

    // ------------------------------------------------------------ 内部实现

    private suspend fun <T> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        parse: (String) -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        val url = url(path, query) ?: return@withContext badBaseUrl()
        execute(Request.Builder().url(url).get().build(), parse)
    }

    private fun <T> execute(request: Request, parse: (String) -> T): ApiResult<T> = try {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                try {
                    ApiResult.Ok(parse(body))
                } catch (e: Exception) {
                    ApiResult.Err(
                        ApiFailure(
                            code = ErrorCodes.PARSE_FAILED,
                            message = "服务端返回内容无法解析：${e.message}",
                            hint = "请确认服务端版本与 App 匹配。",
                            httpStatus = response.code,
                        )
                    )
                }
            } else {
                ApiResult.Err(parseError(response, body))
            }
        }
    } catch (e: IOException) {
        ApiResult.Err(
            ApiFailure(
                code = ErrorCodes.NETWORK_UNREACHABLE,
                message = "连不上服务器（${e.message ?: "网络异常"}）",
                hint = "请确认手机和电脑在同一个网络、服务端已启动、地址填的是电脑局域网 IP。",
                isNetwork = true,
            )
        )
    }

    private fun parseError(response: Response, body: String): ApiFailure {
        val dto = runCatching { json.decodeFromString(ErrorDto.serializer(), body) }.getOrNull()
        return if (dto != null && dto.message.isNotBlank()) {
            ApiFailure(
                code = dto.code,
                message = dto.message,
                hint = dto.hint,
                needsRerecord = dto.needsRerecord,
                httpStatus = response.code,
            )
        } else {
            ApiFailure(
                code = "HTTP_${response.code}",
                message = "服务端返回 ${response.code}",
                hint = "可以直接重试。",
                httpStatus = response.code,
            )
        }
    }

    private fun url(path: String, query: Map<String, String> = emptyMap()): HttpUrl? {
        val base = normalizeBaseUrl(baseUrlProvider()) ?: return null
        val builder = base.newBuilder().addPathSegments("api/v1/$path")
        query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    private fun <T> badBaseUrl(): ApiResult<T> = ApiResult.Err(
        ApiFailure(
            code = ErrorCodes.BAD_BASE_URL,
            message = "服务器地址不正确",
            hint = "格式示例：http://192.168.1.100:8000",
        )
    )

    companion object {
        /** 容错：允许用户只填 IP 或 IP:端口，自动补 http:// 并去掉尾部斜杠。 */
        fun normalizeBaseUrl(raw: String): HttpUrl? =
            withScheme(raw)?.toHttpUrlOrNull()

        fun isValidBaseUrl(raw: String): Boolean = normalizeBaseUrl(raw) != null

        /**
         * 清洗后的可显示地址。存设置时用它，避免把不可见字符原样存进 DataStore ——
         * 那样设置页看起来一切正常，却永远连不上。
         */
        fun canonicalBaseUrl(raw: String): String? {
            val candidate = withScheme(raw) ?: return null
            return if (candidate.toHttpUrlOrNull() != null) candidate else null
        }

        private fun withScheme(raw: String): String? {
            val cleaned = sanitizeAddress(raw)
            if (cleaned.isEmpty()) return null
            return if (cleaned.startsWith("http://", true) || cleaned.startsWith("https://", true)) cleaned
            else "http://$cleaned"
        }

        /**
         * 清洗手输地址。
         *
         * 中文输入法很容易带进全角冒号、全角数字、不换行空格、零宽字符 ——
         * 地址看上去完全正确，但解析出来的 host 是错的，表现为"连不上服务器"，
         * 现场极难定位。这里统一折叠成 ASCII 并去掉所有空白。
         */
        private fun sanitizeAddress(raw: String): String {
            val sb = StringBuilder(raw.length)
            for (ch in raw) {
                val mapped = when {
                    ch == '：' -> ':'
                    ch == '．' || ch == '。' -> '.'
                    ch == '／' -> '/'
                    ch == '－' || ch == '—' -> '-'
                    ch.code in 0xFF10..0xFF19 -> '0' + (ch.code - 0xFF10)   // 全角 0-9
                    else -> ch
                }
                // 0xA0 不换行空格、0x200B 零宽空格、0xFEFF BOM，Char.isWhitespace() 认不出来
                if (mapped.isWhitespace() || mapped.code == 0xA0 ||
                    mapped.code == 0x200B || mapped.code == 0xFEFF
                ) continue
                sb.append(mapped)
            }
            return sb.toString().trimEnd('/')
        }
    }
}

/** 带进度回调的文件请求体，用于真实的上传百分比（不是假装的动画）。 */
private class ProgressBody(
    private val file: File,
    private val type: MediaType,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType = type

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = file.length().coerceAtLeast(1L)
        var written = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                sink.write(buffer, 0, read)
                written += read
                onProgress((written.toDouble() / total).toFloat().coerceIn(0f, 1f))
            }
        }
        onProgress(1f)
    }
}
