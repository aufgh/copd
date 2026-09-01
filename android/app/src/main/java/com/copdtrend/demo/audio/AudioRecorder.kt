package com.copdtrend.demo.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** 削波判定阈值：0.997 满量程，与服务端 audio.py 一致。 */
private const val CLIP_LEVEL = 32_670

/** 一次录音的结果与质检原始指标。 */
data class RecordOutcome(
    val file: File,
    val durationMs: Long,
    val rmsDbfs: Float,
    val peakDbfs: Float,
    val clipRatio: Float,
    val sampleRate: Int = WavIo.SAMPLE_RATE,
)

class RecorderUnavailableException(message: String) : Exception(message)

/**
 * AudioRecord 采集 16 kHz 单声道 16 位 PCM。
 *
 * 音源优先 UNPROCESSED（不走自动增益和降噪，呼吸音这类弱信号更真实），
 * 设备不支持时退回 VOICE_RECOGNITION，最后退回 MIC。
 */
class AudioRecorder {

    @Volatile
    private var stopRequested = false

    @Volatile
    var isRecording: Boolean = false
        private set

    fun requestStop() {
        stopRequested = true
    }

    @SuppressLint("MissingPermission") // 调用方负责在录音前确认 RECORD_AUDIO 已授权
    suspend fun record(
        target: File,
        maxDurationMs: Long,
        onLevel: (level: Float, elapsedMs: Long) -> Unit,
    ): RecordOutcome = withContext(Dispatchers.IO) {
        stopRequested = false
        val minBuffer = AudioRecord.getMinBufferSize(
            WavIo.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            throw RecorderUnavailableException("当前设备不支持 16 kHz 单声道录音")
        }
        // 缓冲区放大一些，避免慢设备丢帧；单次读取取 100 ms
        val bufferSize = max(minBuffer * 2, WavIo.SAMPLE_RATE / 5 * 2)
        val chunkShorts = WavIo.SAMPLE_RATE / 10

        val recorder = openRecorder(bufferSize)
        val pcm = ByteArrayOutputStream(bufferSize * 8)
        val shorts = ShortArray(chunkShorts)

        var totalSamples = 0L
        var sumSquares = 0.0
        var peak = 0
        var clipped = 0L
        var smoothed = 0f

        try {
            recorder.startRecording()
            isRecording = true
            while (!stopRequested) {
                if (!currentCoroutineContext().isActive) break
                val read = recorder.read(shorts, 0, chunkShorts)
                if (read <= 0) {
                    if (read == AudioRecord.ERROR_INVALID_OPERATION) break
                    continue
                }

                var chunkSquares = 0.0
                for (i in 0 until read) {
                    val sample = shorts[i].toInt()
                    val magnitude = abs(sample)
                    if (magnitude > peak) peak = magnitude
                    // 阈值与服务端保持一致（0.997 满量程），否则两边对"削波"的判断会不一样
                    if (magnitude >= CLIP_LEVEL) clipped++
                    val normalized = sample / 32768.0
                    chunkSquares += normalized * normalized
                    pcm.write(sample and 0xFF)
                    pcm.write((sample shr 8) and 0xFF)
                }
                sumSquares += chunkSquares
                totalSamples += read

                // EMA 平滑，直接用瞬时 RMS 画波形会非常抖
                val chunkRms = sqrt(chunkSquares / read).toFloat()
                val level = (chunkRms * 6f).coerceIn(0f, 1f)
                smoothed += (level - smoothed) * 0.25f

                val elapsedMs = totalSamples * 1000 / WavIo.SAMPLE_RATE
                onLevel(smoothed, elapsedMs)
                if (elapsedMs >= maxDurationMs) break
            }
        } finally {
            isRecording = false
            runCatching { recorder.stop() }
            recorder.release()
        }

        val bytes = pcm.toByteArray()
        WavIo.writePcmAsWav(target, bytes)

        val rms = if (totalSamples > 0) sqrt(sumSquares / totalSamples) else 0.0
        RecordOutcome(
            file = target,
            durationMs = WavIo.durationMs(bytes.size),
            rmsDbfs = toDbfs(rms),
            peakDbfs = toDbfs(peak / 32768.0),
            clipRatio = if (totalSamples > 0) (clipped.toDouble() / totalSamples).toFloat() else 0f,
        )
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(bufferSize: Int): AudioRecord {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        for (source in sources) {
            val candidate = runCatching {
                AudioRecord(
                    source,
                    WavIo.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            }.getOrNull()
            if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED) {
                return candidate
            }
            candidate?.release()
        }
        throw RecorderUnavailableException("无法打开麦克风，请检查是否有其他应用正在占用录音")
    }

    private fun toDbfs(value: Double): Float =
        (20.0 * log10(max(value, 1e-9))).toFloat()
}
