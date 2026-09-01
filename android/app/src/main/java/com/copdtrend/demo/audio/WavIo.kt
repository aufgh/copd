package com.copdtrend.demo.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * 最小可用的 WAV 读写。
 *
 * 规格与服务端锁死一致：单声道、16 位 PCM、16 kHz。
 * 现在就统一，避免将来接真模型时因为采样率不一致返工。
 */
object WavIo {

    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    private const val HEADER_SIZE = 44

    /** 把 PCM 字节写成完整的 WAV 文件。 */
    @Throws(IOException::class)
    fun writePcmAsWav(
        target: File,
        pcm: ByteArray,
        sampleRate: Int = SAMPLE_RATE,
        channels: Int = CHANNELS,
        bitsPerSample: Int = BITS_PER_SAMPLE,
    ) {
        target.parentFile?.mkdirs()
        target.outputStream().buffered().use { out ->
            out.write(header(pcm.size, sampleRate, channels, bitsPerSample))
            out.write(pcm)
            out.flush()
        }
    }

    private fun header(
        dataSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val out = ByteArrayOutputStream(HEADER_SIZE)

        fun ascii(text: String) = out.write(text.toByteArray(Charsets.US_ASCII))
        fun le32(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
        }
        fun le16(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }

        ascii("RIFF")
        le32(36 + dataSize)
        ascii("WAVE")
        ascii("fmt ")
        le32(16)             // fmt chunk 大小
        le16(1)              // PCM
        le16(channels)
        le32(sampleRate)
        le32(byteRate)
        le16(blockAlign)
        le16(bitsPerSample)
        ascii("data")
        le32(dataSize)
        return out.toByteArray()
    }

    /** 由 PCM 字节数反推时长，用于本地质检。 */
    fun durationMs(
        pcmBytes: Int,
        sampleRate: Int = SAMPLE_RATE,
        channels: Int = CHANNELS,
        bitsPerSample: Int = BITS_PER_SAMPLE,
    ): Long {
        val bytesPerMs = sampleRate.toLong() * channels * bitsPerSample / 8 / 1000
        if (bytesPerMs <= 0) return 0
        return pcmBytes / bytesPerMs
    }
}
