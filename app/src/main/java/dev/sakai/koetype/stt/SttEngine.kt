package dev.sakai.koetype.stt

import java.io.File

/** 転写リクエスト。languageHint は ISO-639-1（空文字なら自動判定）。 */
data class SttRequest(
    val audio: File,
    val model: String,
    val languageHint: String,
)

/**
 * STT プロバイダ境界（DD-004）。UI/IME 側はこのインターフェースだけを知る。
 * 失敗時の Result.failure のメッセージは、そのままキーボードの状態表示に
 * 出せる短文にすること（秘匿情報を含めない）。
 */
interface SttEngine {
    suspend fun transcribe(request: SttRequest): Result<String>
}
