# LOCALIZATION — UI String Catalog / UI 文字列台帳

The koetype UI is intentionally **English-only, written at a basic-English level**
(roughly junior-high-school vocabulary). The reasoning is recorded in DD-014:
the keyboard surface is mostly symbols (🎤 ⌫ ⏎ ⚙ ◀ ▶) plus a handful of short
words, and the BYOK audience — people who can obtain an API key on an
English-language platform — can read them by definition. One default resource
file means there is no localization matrix to maintain.

koetype の UI は意図的に**中学英語のみ**。理由は DD-014 に記録してある。
キーボード面はほぼ記号で、残りは短い英単語だけ。BYOK の対象者（英語のプラット
フォームで API キーを取得できる人）は定義上これを読める。デフォルトリソース
1 ファイルだけなら、ローカライズのマトリクス保守は発生しない。

## Adding your own language / 母語表示にしたい場合

Android resolves strings by device locale. To localize, create
`app/src/main/res/values-<lang>/strings.xml` (e.g. `values-ja/`, `values-ko/`)
containing the same resource names with translated values. Untranslated names
fall back to the English default automatically. The Japanese column below is a
ready-made draft for `values-ja/`.

端末の言語に合わせたければ `values-<lang>/strings.xml` を足すだけ。訳さなかった
文字列は自動で英語デフォルトにフォールバックする。下表の日本語列は、そのまま
`values-ja/` の下書きとして使える（英語化前に実機で使われていた原文）。

## Catalog / 台帳

Resource names are the stable IDs — code references only these. `%1$s` is a
format placeholder and must be kept.

### Keyboard / キーボード面

| Resource | English (default) | 日本語（原文・values-ja 下書き） |
|---|---|---|
| `status_idle` | TAP 🎤 & SPEAK | 🎤 をタップして話す |
| `status_recording` | RECORDING… TAP ⏹ TO STOP | 録音中… タップで停止 |
| `status_transcribing` | TRANSCRIBING… | 変換中… |
| `status_no_api_key` | NO API KEY — SET IT IN ⚙ | APIキー未設定 — ⚙ から設定してください |
| `status_no_permission` | MIC PERMISSION NEEDED — TAP TO ALLOW | マイク権限が必要です — タップして許可 |
| `status_error_network` | NETWORK ERROR: %1$s | 通信エラー: %1$s |
| `status_no_target` | LOST THE TEXT FIELD — TAP IT AGAIN | 挿入先を見失いました — 入力欄をタップし直してください |
| `status_empty_result` | (SILENCE OR NOT RECOGNIZED) | （無音または認識できませんでした） |
| `status_undone` | UNDONE ✓ | 直前の転写を取り消しました |
| `status_undo_unavailable` | NOTHING TO UNDO | 取り消せる転写がありません |
| `key_space` | SPACE | 空白 |
| `key_undo` | UNDO | 取消し（実機で使われていた旧ラベルは「全消し」— 経緯は DD-014） |

Symbol keys (`key_mic` 🎤, `key_mic_stop` ⏹, `key_backspace` ⌫, `key_enter` ⏎,
`key_globe` 🌐, `key_settings` ⚙, `key_arrow_left` ◀, `key_arrow_right` ▶)
are language-neutral and normally need no translation.

### Settings screen / 設定画面

| Resource | English (default) | 日本語（原文） |
|---|---|---|
| `settings_title` | koetype Settings | koetype 設定 |
| `pref_api_key_label` | OpenAI API Key | OpenAI API キー |
| `pref_api_key_hint` | sk-… | sk-… |
| `pref_model_label` | Transcription model | 転写モデル |
| `pref_language_label` | Language hint (blank = auto-detect) | 言語ヒント（空欄=自動判定） |
| `pref_language_hint` | e.g. ja, en (ISO-639-1) | 例: ja, en（ISO-639-1） |
| `btn_save` | Save | 保存 |
| `btn_enable_ime` | 1. Enable the keyboard (system settings) | 1. キーボードを有効化（システム設定） |
| `btn_pick_ime` | 2. Switch keyboard | 2. キーボードを切り替える |
| `btn_grant_mic` | Grant microphone permission | マイク権限を許可 |
| `mic_granted` | Microphone permission: granted ✓ | マイク権限: 許可済み ✓ |
| `saved_toast` | Saved | 保存しました |
| `error_api_key_chars` | Invalid characters remain in the API key (full-width, etc.). Paste it again | APIキーに使えない文字が残っています（全角文字など）。貼り付けし直してください |

## Style rules / 表記ルール

- Key labels are UPPERCASE (physical-keyboard convention: SHIFT, ENTER).
  Status messages on the keyboard are also uppercase for glanceability.
  Settings-screen labels use normal sentence case — there is room to read.
- Keep vocabulary at basic-English level. If a status message needs a
  dictionary, shorten it.
- Keep the label and its feedback in the same vocabulary: the key says UNDO,
  so its status replies say UNDONE / NOTHING TO UNDO. Do not reintroduce a
  second term for the same operation (this is how 全消し happened — DD-014).
