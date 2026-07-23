# エミュレータ実走記録 — 2026-07-08（MVP 初回スモークテスト）

環境: AVD `Pixel_Fold_API_36`（ヘッドレス `-no-window -no-audio`、KVM、ホスト=開発機）。
ビルド: `./gradlew assembleDebug`（Gradle 8.2 / AGP 8.2.0、初回 17 秒・一発グリーン）。

## 検証できたこと（事実）

| 動線 | 結果 |
|---|---|
| APK インストール → IME 有効化・選択（adb `ime enable/set`） | ✓ |
| 設定画面の描画・APIキー/モデル/言語の保存と永続化（shared_prefs 実物確認） | ✓ |
| RECORD_AUDIO 権限状態の表示（`pm grant` 後「許可済み ✓」） | ✓ |
| キーボード表示（ダークUI・状態表示・マイク・下段キー） | ✓ |
| マイクタップ → 録音開始（赤UI・「録音中」・OS のマイク使用中インジケータ点灯） | ✓（`-no-audio` でも MediaRecorder は動作、無音を録る） |
| 停止 → m4a を OpenAI へアップロード → 401 → エラー本文の message を状態表示 | ✓（偽キーで全パイプライン通し） |
| `commitText` 挿入（空白キー×2 → "  "）と `KEYCODE_DEL` 削除（→ " "） | ✓（uiautomator dump で実測） |
| 転写失敗後の UI 復帰（マイクがアイドルに戻る） | ✓ |

## 未検証（実機での手動テスト項目）

- **実音声での転写成功パス**（本物の APIキー + 実マイク）→ commitText への流し込み
- 多言語（ja/en 混在）の実用精度、言語ヒントの効き
- LINE/Gmail 等の他アプリでの挿入挙動、実機でのキーボード切替動線

## 踏んだ罠（再現手順込み・次回への申し送り）

1. **Pixel Fold の screencap は要ディスプレイ指定**: `adb exec-out screencap -p` は
   複数ディスプレイ警告が PNG に混入して壊れる。
   → `adb shell dumpsys SurfaceFlinger --display-id` で ID を取り
   `screencap -p -d <id> /sdcard/s.png && adb pull`。
2. **エミュレータはハードキーボード扱いでソフトキーボードが出ない**:
   `adb shell settings put secure show_ime_with_hard_keyboard 1` が必要。
3. **現用 IME の APK を再インストールすると show リクエストが STATUS_TIMEOUT で固まる**
   （logcat の ImeTracker で確認）。→ `adb shell ime reset` → `ime enable` → `ime set` で復旧。
   `am force-stop` は既定 IME をシステム標準に戻すので注意。
4. **横長画面で全画面エクストラクトモードに入りアプリが隠れる** → DD-008 で恒久対応
   （`onEvaluateFullscreenMode() = false`）。

## スクリーンショット

作業時の一時領域のみに保存（必要な知見は本ファイルに言語化済みのため未収蔵）。
