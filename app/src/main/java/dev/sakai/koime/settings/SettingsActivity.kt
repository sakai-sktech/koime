package dev.sakai.koime.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import dev.sakai.koime.R

/** API キー・モデル・言語ヒントの設定と、IME 有効化/権限許可への導線。 */
class SettingsActivity : Activity() {

    private lateinit var apiKeyInput: EditText
    private lateinit var modelGroup: RadioGroup
    private lateinit var languageInput: EditText
    private lateinit var grantMicButton: Button

    private val modelToId = mapOf(
        "gpt-4o-mini-transcribe" to R.id.modelMiniTranscribe,
        "gpt-4o-transcribe" to R.id.model4oTranscribe,
        "whisper-1" to R.id.modelWhisper1,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        apiKeyInput = findViewById(R.id.apiKeyInput)
        modelGroup = findViewById(R.id.modelGroup)
        languageInput = findViewById(R.id.languageInput)
        grantMicButton = findViewById(R.id.grantMicButton)

        apiKeyInput.setText(Prefs.apiKey(this))
        languageInput.setText(Prefs.languageHint(this))
        modelGroup.check(modelToId[Prefs.model(this)] ?: R.id.modelMiniTranscribe)

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val apiKey = Prefs.normalizeApiKey(apiKeyInput.text.toString())
            if (!Prefs.isValidApiKey(apiKey)) {
                Toast.makeText(this, R.string.error_api_key_chars, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val model = modelToId.entries
                .firstOrNull { it.value == modelGroup.checkedRadioButtonId }?.key
                ?: Prefs.DEFAULT_MODEL
            Prefs.save(this, apiKey, model, languageInput.text.toString())
            apiKeyInput.setText(apiKey)
            // この Activity は保存後も finish しないため、明示 commit で
            // パスワードマネージャの保存フローを発火させる（DD-012）
            getSystemService(AutofillManager::class.java)?.commit()
            Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show()
        }

        grantMicButton.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        findViewById<Button>(R.id.enableImeButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<Button>(R.id.pickImeButton).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
    }

    override fun onResume() {
        super.onResume()
        updateMicButton()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        updateMicButton()
    }

    private fun updateMicButton() {
        val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        grantMicButton.isEnabled = !granted
        grantMicButton.setText(if (granted) R.string.mic_granted else R.string.btn_grant_mic)
    }
}
