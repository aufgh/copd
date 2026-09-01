package com.copdtrend.demo.audio

import android.media.MediaPlayer
import java.io.File

/**
 * 录音试听。用 MediaPlayer 就够了 —— 我们写出来的是标准 44 字节头的 PCM WAV。
 *
 * 只保留一个实例，播新的自动停旧的，避免用户连点两次听到两条声音重叠。
 */
class AudioPlayer {

    private var player: MediaPlayer? = null
    private var playingPath: String? = null

    /** 正在播放的文件路径；UI 用它决定哪条录音显示"停止"。 */
    val currentPath: String? get() = playingPath

    /**
     * 播放指定文件。若该文件正在播放则停止（点第二次即停）。
     * @return true 表示已开始播放，false 表示这次调用是停止
     */
    fun toggle(file: File, onFinished: () -> Unit): Boolean {
        if (playingPath == file.absolutePath && player?.isPlaying == true) {
            stop()
            return false
        }
        stop()
        val mp = MediaPlayer()
        return try {
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                stop()
                onFinished()
            }
            mp.setOnErrorListener { _, _, _ ->
                stop()
                onFinished()
                true
            }
            mp.prepare()
            mp.start()
            player = mp
            playingPath = file.absolutePath
            true
        } catch (e: Exception) {
            runCatching { mp.release() }
            player = null
            playingPath = null
            onFinished()
            false
        }
    }

    fun stop() {
        val mp = player ?: run {
            playingPath = null
            return
        }
        runCatching { if (mp.isPlaying) mp.stop() }
        runCatching { mp.reset() }
        runCatching { mp.release() }
        player = null
        playingPath = null
    }
}
