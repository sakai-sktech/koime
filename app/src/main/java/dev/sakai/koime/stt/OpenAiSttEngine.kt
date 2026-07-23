package dev.sakai.koime.stt

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI transcription API (/v1/audio/transcriptions) の SttEngine 実装。
 * API キーは呼び出し時に provider から取り直す（設定変更を即反映するため）。
 */
class OpenAiSttEngine(private val apiKeyProvider: () -> String) : SttEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun transcribe(request: SttRequest): Result<String> =
        withContext(Dispatchers.IO) {
            val apiKey = apiKeyProvider()
            if (apiKey.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("APIキー未設定"))
            }
            // HTTP ヘッダに載らない文字が混ざっていると OkHttp が即 throw する
            // （実機で全角文字混入によるプロセス死を確認 — DD-009）。先に弾いて短文で返す。
            if (apiKey.any { it.code !in 0x21..0x7E }) {
                return@withContext Result.failure(
                    IllegalArgumentException("APIキーに不正な文字（全角など）— 設定で入れ直してください")
                )
            }

            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file", request.audio.name,
                    request.audio.asRequestBody("audio/mp4".toMediaType())
                )
                .addFormDataPart("model", request.model)
                .addFormDataPart("response_format", "json")
                .apply {
                    if (request.languageHint.isNotEmpty()) {
                        addFormDataPart("language", request.languageHint)
                    }
                }
                .build()

            // SttEngine の契約: 失敗は必ず Result.failure。ここから例外を漏らすと
            // IME プロセスごと落ちる（DD-009）ので、キャンセル以外は全部畳む。
            try {
                val httpRequest = Request.Builder()
                    .url("https://api.openai.com/v1/audio/transcriptions")
                    .header("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Result.success(JSONObject(bodyText).optString("text", "").trim())
                    } else {
                        // エラー本文から message だけ抜く。リクエスト情報は載せない。
                        val message = runCatching {
                            JSONObject(bodyText).getJSONObject("error").getString("message")
                        }.getOrDefault("HTTP ${response.code}")
                        Result.failure(IOException(message))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Result.failure(IOException(e.message ?: "接続失敗"))
            } catch (e: Throwable) {
                Log.e("koime", "transcribe failed: ${e.javaClass.simpleName}", e)
                Result.failure(IOException("${e.javaClass.simpleName}: ${e.message ?: "?"}"))
            }
        }
}
