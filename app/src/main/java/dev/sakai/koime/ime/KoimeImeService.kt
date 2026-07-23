package dev.sakai.koime.ime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import dev.sakai.koime.R
import dev.sakai.koime.audio.VoiceRecorder
import dev.sakai.koime.settings.PermissionActivity
import dev.sakai.koime.settings.Prefs
import dev.sakai.koime.settings.SettingsActivity
import dev.sakai.koime.stt.OpenAiSttEngine
import dev.sakai.koime.stt.SttEngine
import dev.sakai.koime.stt.SttRequest
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// カーソルキーの長押しリピート: 発動までの猶予と連射間隔
private const val REPEAT_INITIAL_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 50L

/**
 * バッファ方式の音声入力 IME（DD-002）。
 * マイクタップで録音開始 → 再タップで停止 → 転写 → commitText で挿入。
 */
class KoimeImeService : InputMethodService() {

    // 最後の砦: 転写コルーチンから何が漏れても IME プロセスは殺さない（DD-009）
    private val crashGuard = CoroutineExceptionHandler { _, e ->
        Log.e("koime", "unhandled exception in serviceScope", e)
        mainHandler.post {
            transcribing = false
            setRecordingUi(false)
            statusText?.text = getString(R.string.status_error_network, e.javaClass.simpleName)
        }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + crashGuard)
    private lateinit var recorder: VoiceRecorder
    private val stt: SttEngine by lazy { OpenAiSttEngine { Prefs.apiKey(this) } }

    private var statusText: TextView? = null
    private var micButton: Button? = null
    private var transcribing = false

    // 「全消し」の対象 = 最後に commitText した転写。取り消し済み・入力欄切替で null に戻す
    private var lastCommit: String? = null

    override fun onCreate() {
        super.onCreate()
        // 上限到達時は手動停止と同じ動線に流す（リスナーは生成スレッド=メインに届く）
        recorder = VoiceRecorder(this) { if (recorder.isRecording) stopAndTranscribe() }
    }

    // 横画面/大画面でも全画面エクストラクトに入らない（アプリを隠すと音声入力の文脈が見えない）
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        statusText = view.findViewById(R.id.statusText)
        micButton = view.findViewById(R.id.micButton)

        micButton?.setOnClickListener { onMicTapped() }
        view.findViewById<Button>(R.id.backspaceButton).setOnClickListener {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        view.findViewById<Button>(R.id.enterButton).setOnClickListener {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        view.findViewById<Button>(R.id.spaceButton).setOnClickListener {
            currentInputConnection?.commitText(" ", 1)
        }
        view.findViewById<Button>(R.id.globeButton).setOnClickListener { switchKeyboard() }
        view.findViewById<Button>(R.id.settingsButton).setOnClickListener { openSettings() }
        view.findViewById<Button>(R.id.undoButton).setOnClickListener { undoLastCommit() }
        setupRepeatKey(view.findViewById(R.id.arrowLeftButton)) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
        }
        setupRepeatKey(view.findViewById(R.id.arrowRightButton)) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
        }

        setStatus(R.string.status_idle)
        return view
    }

    private fun onMicTapped() {
        if (transcribing) return
        if (recorder.isRecording) {
            stopAndTranscribe()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setStatus(R.string.status_no_permission)
            startActivity(Intent(this, PermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return
        }
        if (Prefs.apiKey(this).isEmpty()) {
            setStatus(R.string.status_no_api_key)
            return
        }
        try {
            recorder.start()
        } catch (e: Exception) {
            statusText?.text = getString(R.string.status_error_network, e.message ?: "recorder")
            return
        }
        setRecordingUi(true)
    }

    private fun stopAndTranscribe() {
        val audio = recorder.stop()
        setRecordingUi(false)
        if (audio == null) {
            setStatus(R.string.status_idle)
            return
        }
        transcribing = true
        setStatus(R.string.status_transcribing)
        val request = SttRequest(audio, Prefs.model(this), Prefs.languageHint(this))
        serviceScope.launch {
            val result = try {
                stt.transcribe(request)
            } finally {
                // 転写中に IME が destroy されても録音を cacheDir に残さない
                audio.delete()
            }
            transcribing = false
            result.fold(
                onSuccess = { text ->
                    if (text.isEmpty()) {
                        setStatus(R.string.status_empty_result)
                    } else {
                        val ic = currentInputConnection
                        if (ic == null) {
                            // 黙って idle に戻ると「何も起きなかった」ように見える。原因を出す。
                            setStatus(R.string.status_no_target)
                        } else {
                            ic.commitText(text, 1)
                            lastCommit = text
                            setStatus(R.string.status_idle)
                        }
                    }
                },
                onFailure = { e ->
                    statusText?.text = getString(R.string.status_error_network, e.message ?: "?")
                },
            )
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // 別の入力欄に移ったら、前の欄への転写は取り消し対象から外す
        if (!restarting) lastCommit = null
    }

    /**
     * 「全消し」キー: 直前の転写を丸ごと取り消す。カーソル移動や手編集の後は
     * 削除範囲がズレるので、カーソル直前のテキストが転写と一致する場合だけ消す。
     */
    private fun undoLastCommit() {
        val ic = currentInputConnection
        val last = lastCommit
        if (ic == null || last.isNullOrEmpty()) {
            setStatus(R.string.status_undo_unavailable)
            return
        }
        val before = ic.getTextBeforeCursor(last.length, 0)?.toString()
        if (before == last) {
            ic.deleteSurroundingText(last.length, 0)
            lastCommit = null
            setStatus(R.string.status_undone)
        } else {
            setStatus(R.string.status_undo_unavailable)
        }
    }

    /** 長押しで一定間隔リピートするキー（カーソル移動用）。action はメインスレッドで呼ぶ。 */
    private fun setupRepeatKey(button: Button, action: () -> Unit) {
        var repeater: Runnable? = null
        button.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    action()
                    repeater = object : Runnable {
                        override fun run() {
                            action()
                            mainHandler.postDelayed(this, REPEAT_INTERVAL_MS)
                        }
                    }.also { mainHandler.postDelayed(it, REPEAT_INITIAL_DELAY_MS) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    repeater?.let(mainHandler::removeCallbacks)
                    repeater = null
                    if (event.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun setRecordingUi(recording: Boolean) {
        micButton?.setBackgroundResource(
            if (recording) R.drawable.bg_mic_recording else R.drawable.bg_mic_idle
        )
        micButton?.text = getString(if (recording) R.string.key_mic_stop else R.string.key_mic)
        if (recording) setStatus(R.string.status_recording)
    }

    private fun setStatus(resId: Int) {
        statusText?.text = getString(resId)
    }

    private fun switchKeyboard() {
        val switched = if (Build.VERSION.SDK_INT >= 28) {
            switchToPreviousInputMethod()
        } else {
            false
        }
        if (!switched) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // キーボードが閉じたら録り捨てる（裏で録音を続けない）
        if (recorder.isRecording) {
            recorder.cancel()
            setRecordingUi(false)
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        recorder.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
