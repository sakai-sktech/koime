# 実機バグ修正記録 — 2026-07-09（変換中→即初期表示・挿入なし）

## 症状（実機）

Ubuntu 開発機から実機へ adb インストール、キーボード登録まで OK。
テキストボックスで録音→停止すると「変換中」が一瞬出た後すぐ初期表示に戻り、
テキストは挿入されず、エラーも出ない。

## 診断の筋道

症状の形からコード上の到達可能経路を絞った:

- 失敗パス（onFailure）は**エラーメッセージを表示したまま**になる設計
  → 「初期表示に戻る」とは一致しない
- 一致する経路は2つ: ①成功したが commitText が空振り
  ②**転写コルーチンの未捕捉例外でプロセスが死に、IME が再起動して初期描画**

`OpenAiSttEngine` が IOException しか catch していないことに着目。
OkHttp の `Request.Builder().header()` はヘッダに載らない文字（非ASCII）を渡すと
**ネットワークに出る前に IllegalArgumentException を即 throw** する。
「一瞬で」という時間感覚とも一致する。

## 再現（エミュレータ・確定）

prefs に `sk-テストkey` を直接注入して録音→停止:

```
FATAL EXCEPTION: main
java.lang.IllegalArgumentException: Unexpected char 0x30c6 at 10 in Authorization value
    at okhttp3.Headers$Companion.checkValue(Headers.kt:450)
    at dev.sakai.koetype.stt.OpenAiSttEngine$transcribe$2.invokeSuspend(OpenAiSttEngine.kt:49)
Process dev.sakai.koetype (pid 3516) has died
```

→ **実機側の API キーに全角文字等が混入していた可能性が極めて高い**
（スマホでの手打ち・IME変換・貼り付け時に混ざるのが定番）。

## 修正（DD-009・三層防御）

1. **入口**: 設定保存時に `normalizeApiKey`（全角英数→半角、空白除去）。
   残った不正文字は保存拒否 + Toast 警告。
2. **境界**: `OpenAiSttEngine` は Throwable を全部 `Result.failure` に畳む
   （CancellationException のみ再throw）。キーの文字検査も転写前に実施し
   「APIキーに不正な文字（全角など）」を即返す。
3. **最後の砦**: serviceScope に CoroutineExceptionHandler。何が漏れても
   エラー表示に変換し、Log.e("koetype") に必ず残す。
4. commitText 空振り（InputConnection null）も無言で idle に戻さず
   「挿入先を見失いました」を表示。

## 回帰確認（エミュレータ）

- 全角混入キー: クラッシュせず（FATAL=0）「APIキーに不正な文字」表示、UI 復帰 ✓
- ASCII 偽キー: 従来どおり 401 の message を表示 ✓

## 再テスト結果（2026-07-09・実機）

**成功。** APIキー貼り直し（正規化経由）後、実音声の日本語が句読点込みで
ChatGPT アプリの入力欄に挿入され、そのまま実会話に使用できた。
（証跡スクリーンショットは私的な会話画面のため非公開。）
観察: 言い淀み（「自作の**な**声で」）がそのまま転写される — フェーズ2
（AI 推敲）の実例サンプルとして採取。

## 実機での再テスト手順

1. `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. **koetype 設定で API キーを一度クリアして貼り付け直し → 保存**
   （保存時に自動で正規化される。警告が出たらキーに不正文字が残っている）
3. 録音→停止。今回からは失敗時に必ず画面に理由が出る。
   さらに調べる場合: `adb logcat -s koetype AndroidRuntime`
