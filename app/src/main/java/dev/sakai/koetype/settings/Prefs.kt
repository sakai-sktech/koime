package dev.sakai.koetype.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * 設定の単一アクセス点。API キーはアプリ専用領域(MODE_PRIVATE)に保存し、
 * ログ・例外メッセージには決して載せない（DD-005）。
 */
object Prefs {
    const val DEFAULT_MODEL = "gpt-4o-mini-transcribe"

    private const val FILE = "koetype_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_LANGUAGE = "language"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun apiKey(context: Context): String =
        prefs(context).getString(KEY_API_KEY, "").orEmpty().trim()

    fun model(context: Context): String =
        prefs(context).getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifEmpty { DEFAULT_MODEL }

    fun languageHint(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, "").orEmpty().trim()

    fun save(context: Context, apiKey: String, model: String, language: String) {
        prefs(context).edit()
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_MODEL, model)
            .putString(KEY_LANGUAGE, language.trim())
            .apply()
    }

    /**
     * API キー入力の正規化（Generous Input）。スマホ IME 経由の入力で混ざりがちな
     * 全角英数記号を半角へ変換し、空白類を除去する。
     */
    fun normalizeApiKey(raw: String): String = raw
        .map { c -> if (c.code in 0xFF01..0xFF5E) (c.code - 0xFF01 + 0x21).toChar() else c }
        .filterNot { it.isWhitespace() }
        .joinToString("")

    /** 正規化後もヘッダに載らない文字が残っていれば false（Rigorous Output）。 */
    fun isValidApiKey(key: String): Boolean = key.all { it.code in 0x21..0x7E }
}
