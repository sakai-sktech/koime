# GETTING STARTED — ビルドから初回入力まで

**日本語** | [English](GETTING-STARTED.en.md)

koetype はストア配布もバイナリ配布もしない。**自分でビルドして入れる**のが
正規の導入経路である（理由は README の思想節と THREAT-MODEL.md）。

## 0. 前提

- **端末**: Android 8.0（API 26）以上
- **OpenAI API キー**: <https://platform.openai.com> で自分で取得する。
  API の利用料金は OpenAI からあなたに直接請求される（このアプリの作者は
  一切関与しない）。キー取得・課金設定・上限設定の方法は OpenAI のドキュメントを
  参照。**キーの管理はパスワードマネージャを推奨**（§4 で効いてくる）。
- **ビルド環境**: JDK 17 / Android SDK（compileSdk 34。Android Studio を
  入れるか、コマンドラインツールで `platforms;android-34` を導入）/ adb

## 1. ビルド

```bash
git clone https://github.com/sakai-sktech/koetype.git
cd koetype
# SDK の場所を指定（Android Studio 利用なら自動生成される）
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug
```

## 2. インストールと IME 有効化

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

1. 端末の **設定 → システム → 言語と入力 → 画面キーボード** で
   **koetype** を有効化する。
2. このとき OS が「このキーボードは入力内容をすべて収集できる可能性がある」
   という趣旨の警告を出す。これは **IME というカテゴリ全体に対する Android の
   標準警告**である。koetype が何を送り何を送らないかはソースで確認できる
   （[THREAT-MODEL.md](../THREAT-MODEL.md) の該当行参照）。

## 3. マイク権限

ランチャーから **koetype** の設定画面を開き、「マイクを許可」から
RECORD_AUDIO 権限を付与する（キーボード上のマイクを初回タップしたときにも
権限要求が出る）。

## 4. API キーの設定

設定画面の API キー欄に自分のキーを入れて保存する。

**推奨ルート — パスワードマネージャ + Autofill**:
キー欄は Android 標準の Autofill Framework に対応している。Bitwarden /
1Password / Google パスワードマネージャ / KeePassDX 等に API キーを保存して
あれば、**キー欄をタップ → 自動入力から選ぶだけ**で入る。メールやチャットで
キーを自分宛てに送ったり、クリップボードを経由したりする必要はない。
保存時にはマネージャ側の「保存しますか」フローも発火する。

手で貼り付ける場合も、保存時に全角英数の半角化と空白除去が自動で走る
（スマホの IME 経由の入力では全角文字が混ざりやすく、それが原因の
不具合を実機で踏んだため — DD-009）。警告が出たらキーに不正な文字が
残っている。

モデルは既定で `gpt-4o-mini-transcribe`（`gpt-4o-transcribe` / `whisper-1` に
切替可）。言語ヒント（`ja` 等)を入れると転写が安定する。

## 5. 使う

1. 任意のアプリの入力欄でキーボードを koetype に切り替える
   （地球儀キー、または OS のキーボード切替）。
2. **マイクをタップ → 話す → もう一度タップ**。数秒後、転写テキストが
   カーソル位置に挿入される。録音上限は 5 分。
3. 補助キー:
   - **◀▶**: カーソル移動（長押しでリピート）
   - **全消し**: 直前の転写だけを取り消す。カーソル移動や手編集の後は
     安全のため効かなくなる（無関係のテキストを消さないための一致検証つき）
   - 空白 / ⌫ / ⏎ / ⚙（設定）

## 6. release ビルドを自分の鍵で署名する（常用するなら推奨）

常用するなら release ビルド推奨（debug ビルドには THREAT-MODEL に書いた
`adb run-as` 経路が残るため）。署名鍵は自分で作る。自己署名（いわゆる
オレオレ署名）で正しい理由と、この鍵が何を証明しているのかは
[SIGNING.md](SIGNING.md) にまとめてある:

```bash
mkdir -p signing
keytool -genkeypair -v \
  -keystore signing/my-release.jks \
  -alias koetype -keyalg RSA -keysize 2048 -validity 10000
```

リポジトリ直下に `keystore.properties` を作る（**この 2 つは絶対に commit
しない**。`.gitignore` が最初から塞いでいるのはそのため）:

```properties
storeFile=signing/my-release.jks
storePassword=（あなたのパスワード）
keyAlias=koetype
keyPassword=（あなたのパスワード）
```

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

鍵を失くすと同じ署名で更新できなくなる（再インストールが必要になる）ので、
鍵ファイルは自分のバックアップ体制に含めること。

## 7. うまくいかないとき

- 失敗は必ずキーボードの状態表示に理由が出る設計（無言で戻ることはない）。
  さらに追うなら: `adb logcat -s koetype AndroidRuntime`
- エミュレータで検証する場合の罠（ソフトキーボードが出ない、IME 再インストールで
  固まる等）は [2026-07-08_emulator-smoke-test.md](2026-07-08_emulator-smoke-test.md)
  の「踏んだ罠」に再現手順込みでまとめてある。
- 実機で「変換中がすぐ消えて何も入らない」系の過去事例と診断手順:
  [2026-07-09_real-device-bug-fix.md](2026-07-09_real-device-bug-fix.md)
