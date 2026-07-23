package dev.sakai.koetype.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * MediaRecorder による m4a(AAC 16kHz mono) バッファ録音（DD-003）。
 * start → stop で1ファイル完結。ファイルは cacheDir に置かれ、
 * 呼び出し側が転写後に削除する責務を持つ（CLAUDE.md 不変条件）。
 */
class VoiceRecorder(
    private val context: Context,
    private val onLimitReached: () -> Unit,
) {
    companion object {
        /** API 側の 25MB / タイムアウト制約への防御（DD-002）。 */
        const val MAX_DURATION_MS = 5 * 60 * 1000
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    /** 録音開始。RECORD_AUDIO 権限は呼び出し側で確認済みであること。 */
    fun start() {
        check(recorder == null) { "already recording" }
        val file = File.createTempFile("koetype_", ".m4a", context.cacheDir)
        val r = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        try {
            // VOICE_RECOGNITION: 通話向け加工を避けた STT 向きの音源
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(16000)
            r.setAudioChannels(1)
            r.setAudioEncodingBitRate(64000)
            r.setMaxDuration(MAX_DURATION_MS)
            r.setOutputFile(file.absolutePath)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    onLimitReached()
                }
            }
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            file.delete()
            throw e
        }
        recorder = r
        outputFile = file
    }

    /**
     * 録音を確定して音声ファイルを返す。
     * 短すぎて何も録れなかった等で確定できないときは null（ファイルは削除済み）。
     */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        val file = outputFile
        outputFile = null
        return try {
            r.stop()
            file
        } catch (e: RuntimeException) {
            file?.delete()
            null
        } finally {
            r.release()
        }
    }

    /** 録音を破棄する（転写しない）。 */
    fun cancel() {
        stop()?.delete()
    }
}
