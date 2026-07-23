# API キーはどこに・どう保存されるか — koetype のストレージセキュリティ

koetype は BYOK（ユーザー自身の API キーを持ち込む）設計のため、
「キーは端末のどこに・どんな形式で置かれ、何から守られているのか」を
利用者・フォーク者が自分で検証できるように、ここに全部書いておく。

記述は 2026-07 時点の Android の仕組みに基づく。コードの現物は
`app/src/main/java/dev/sakai/koetype/settings/Prefs.kt` と
`app/src/main/AndroidManifest.xml`、`app/src/main/res/xml/data_extraction_rules.xml`
を参照（この文書より現物が正）。

## TL;DR

- キーと設定は**アプリ専用領域に平文 XML** で保存される。アプリ自身は暗号化しない。
- 守っているのは Android の多層防御：アプリサンドボックス（他アプリ・USB コピーから）、
  `allowBackup="false"` と `dataExtractionRules`（クラウドバックアップ・端末間移行から）、
  端末暗号化（端末盗難から）。
- **USB ケーブルでファイルコピーできる程度では抜けない。**
- 唯一の注意: **debug ビルド＋USB デバッグ ON の検証機**だけは `adb run-as` で
  抜ける。他者に配るのは release ビルド（debuggable でない）なので塞がっている。
- root 化された端末では守れない。これは Android のセキュリティモデルの外。

## 1. 保存の実体

`Prefs.kt` は `SharedPreferences`（`MODE_PRIVATE`）を使う。実機上の実体：

- **場所**: `/data/data/dev.sakai.koetype/shared_prefs/koetype_prefs.xml`
  （アプリ専用の内部ストレージ。共有ストレージ `/sdcard` ではない）
- **形式**: 平文の XML

```xml
<map>
    <string name="api_key">sk-proj-xxxx...</string>
    <string name="model">gpt-4o-mini-transcribe</string>
    <string name="language">ja</string>
</map>
```

「アプリが暗号化していない」ことを隠さず明記する。防御は下の多層構造が担う。

## 2. シナリオ別の防御

### ① USB ケーブルでファイルコピー（MTP）→ 届かない

PC から見えるのは共有ストレージ（DCIM・Download 等）だけ。
`/data/data/` は MTP のプロトコル上そもそも公開されない領域。

### ② adb で pull → 通常は不可

adb シェルは `shell` ユーザーで動き、`/data/data/dev.sakai.koetype` は
アプリ固有の Linux UID が所有する mode 700 のディレクトリなので
Permission denied になる。しかも adb 自体が
「開発者オプション有効化 → USB デバッグ ON → ロック解除状態で RSA 指紋承認」
という関門の先にある。

### ③ ⚠️ debug ビルド＋USB デバッグ ON → ここだけ抜ける

`app-debug.apk` は debuggable なので、承認済み adb から
`adb shell run-as dev.sakai.koetype` でアプリ本人になりすまして
prefs XML を読める。**開発検証機（debug 版＋USB デバッグ ON）だけは、
ロック解除済み端末＋ケーブルで抜ける状態**だと認識しておく。

release ビルドは debuggable でないため `run-as` が拒否され、この経路は塞がる。
配布は必ず release で行う。

### ④ バックアップ・端末間移行経由の流出 → 二段で塞ぐ

マニフェストの `android:allowBackup="false"` で、従来の `adb backup` と
Google のクラウドバックアップ対象から外している。

ただし Android 12（API 31）以降をターゲットとするアプリでは、端末間移行
（device-to-device transfer）時に `allowBackup` が無視される仕様がある。
そのため koetype は `android:dataExtractionRules` を追加し、クラウドバックアップと
端末間移行の両方について `root` / `file` / `database` / `sharedpref` / `external`
の全ドメインを明示的に除外している。

つまり「`allowBackup=false` だから大丈夫」で止めず、現在の Android の分岐まで
コードで塞いでいる。トレードオフとして、機種変更時にキーと設定は引き継がれない。
BYOK の利用者はキーをパスワードマネージャ等で自分の管理体制に置き、
新端末では Autofill で再入力すればよい（DD-012）。

### ⑤ 端末ごと盗まれた・分解された → 端末暗号化が守る

Android 10 以降を搭載して**新規出荷された端末**はファイルベース暗号化（FBE）が必須。
Android 9 以前から Android 10〜12 へ更新された一部端末では、旧来の
フルディスク暗号化（FDE）が使われる場合がある。Android 13 では FDE サポート自体が
廃止されている。

方式は端末世代で異なるが、いずれも `/data` のオフライン読み出しを端末ロック資格情報と
暗号鍵で防ぐ層である。PIN を知らない第三者がフラッシュメモリを直接読んでも、
平文の prefs XML がそのまま見える構造ではない。

### ⑥ root 化・悪意あるアプリに root を取られた → 守れない

root はサンドボックスの外側なので、平文 XML がそのまま読める。
これはアプリ側では防げず、Android のセキュリティモデル全体の限界。
（後述の Keystore 暗号化でも、root はアプリの文脈で復号 API を呼べるため
本質的には防げない。）

## 3. 相場観

「アプリ専用領域に平文＋サンドボックス＋端末暗号化＋backup/D2D 無効」は、
世の中の多くのアプリがログイントークンやセッション Cookie を
保存しているのと同じ水準。特別に弱くも強くもない、業界の標準線。

## 4. さらに固める選択肢と、いま採らない理由

Android Keystore（TEE）で AES 鍵を作り、キーを暗号化してから保存する方式がある。
冷静に評価すると、追加で防げるのは「端末暗号化を突破されて `/data` の平文は取られたが
TEE は無事」という狭いシナリオのみで、root を取られたケースは結局防げない。

なお定番だった Jetpack の `EncryptedSharedPreferences`（androidx.security-crypto）は
2024 年に deprecated になっており、やるなら Keystore 直叩きになる。
費用対効果から現時点では採らず、論点として寝かせてある。

## 5. 運用上の実践結論

- **常用する release ビルドは、現在の脅威モデルの範囲で十分固い。**
- 気をつけるのは自分の開発検証機だけ：debug 版が入った端末で
  USB デバッグを常時 ON にしたまま、ロック解除状態で他人にケーブルを
  繋がせない。これは実装ではなく運用の話。

## 6. iOS に移植する人へ — 答えではなく「問いの立て方」を持っていく

このドキュメントの Android 向けの結論を iOS にそのまま写すと事故る。
移植すべきは以下の 4 つの問いで、答えはプラットフォームごとに変わる：

1. **どこに置かれるか** — その保存 API の実体はファイルか、専用ストアか
2. **何経由で端末の外に出るか** — ケーブル接続・バックアップ・クラウド同期
3. **at-rest 暗号化は何に紐づくか** — 端末ロック資格情報か、それ以外か
4. **root / jailbreak で何が変わるか** — 守れる線と守れない線はどこか

具体的に効いてくる Android との違い：

- iOS の `UserDefaults`（SharedPreferences 相当）は平文 plist だが、
  **Finder / iCloud バックアップにデフォルトで含まれる**。Android の
  `allowBackup="false"` + `dataExtractionRules` に相当する遮断を明示しない限り、
  「平文でも端末から出ない」という本書②〜④の前提が成立しない。
- iOS では最初から **Keychain** を使うのが正解になりやすい。Android で
  「Keystore 直叩きは費用対効果が薄い」と評価した計算が、iOS では
  「プラットフォーム標準の置き場が最初から暗号化ストア」なので逆転する。
  `ThisDeviceOnly` 系の属性でバックアップ移行からも外せる。
- パスワードマネージャ連携（本プロジェクトの DD-012 に相当）は、iOS では
  AutoFill（`textContentType = .password`）が対応物。
  「ベンダー個別連携ではなく OS 標準機構に乗る」という判断は両 OS で共通。

## 関連文書

- `docs/DESIGN-DECISIONS.md` — DD-005（ログにキーを載せない）、DD-009（入口検証）、
  DD-012（Autofill 開放と、偶発的摩擦≠意図したハードルの原則）
- `GETTING-STARTED.md` — ビルド手順と、自分の鍵での release 署名
- `docs/PRODUCTIZATION.md` — キーを運営者側に置く構成（マネージド）に伴う責任の整理
