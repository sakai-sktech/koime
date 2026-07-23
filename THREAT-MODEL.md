# koetype 脅威モデル — 何から守り、何は守らないか

**日本語** | [English](THREAT-MODEL.en.md)

koetype の API キーは端末内に**平文で**保存される（詳細は
[docs/KEY-STORAGE-SECURITY.md](docs/KEY-STORAGE-SECURITY.md)）。
これは手抜きではなく設計判断（DD-005）だが、その判断が成立するのは
**前提と守備範囲を明文化した場合だけ**なので、この文書がそれを担う。

## 前提

**API キーと端末の所有者は同一人物 — あなた自身。**

koetype には運営者・代理人が存在しない。守るべき相手は「あなたの端末に
アクセスしようとする第三者」であって、「あなた自身」や「運営者」ではない。
この前提が崩れる構成（キーを他人に配る、共用端末に入れる、サービスとして
運用する）は、このモデルの外にある（その場合は
[docs/PRODUCTIZATION.md](docs/PRODUCTIZATION.md) を読むこと）。

## 守るもの（資産）

1. あなたの API キー（漏れれば他人があなたの課金で API を使える）
2. あなたの音声（録音データ）
3. 転写されたテキスト（入力内容そのもの）

## スコープ内 — 設計で守っている脅威

| 脅威 | 防御 |
|---|---|
| 他アプリによるキー・設定の読み取り | アプリサンドボックス（専用領域 + MODE_PRIVATE）。非 root 端末では他アプリから読めない |
| USB ケーブル / MTP でのファイル抜き取り | `/data/data` は MTP に公開されない |
| バックアップ・端末間移行によるキー流出 | `allowBackup="false"` に加え、Android 12+ の `dataExtractionRules` でクラウドバックアップと device-to-device transfer の全ドメインを明示的に除外 |
| 端末の盗難・分解 | Android 10 以降を搭載して新規出荷された端末は FBE 必須。古い端末からのアップグレード機では FDE の場合もあるが、いずれも端末ロック資格情報なしのオフライン読み出しを防ぐ層になる |
| 通信の傍受 | OpenAI への TLS のみ。他の通信先は存在しない |
| 音声の残留 | 録音は cacheDir のみ・転写後に即削除。永続化しない |
| ログ・クラッシュレポートへのキー混入 | キーをログ・例外メッセージに載せない規約 + 全角混入等は入口で正規化・拒否（DD-009） |
| IME による入力内容の吸い上げ（このアプリ自身への疑い） | 収集コードが存在しないことを**ソースで確認できる**。読むべきは `KoetypeImeService.kt`（commitText 経路）と `OpenAiSttEngine.kt`（唯一の通信先） |

## スコープ外 — 設計上、守らないと宣言する脅威

| 脅威 | 理由 |
|---|---|
| root 化端末・root を取った悪意あるアプリ | サンドボックスの外側。アプリ層では防げない（Keystore 暗号化でも本質的に防げない — KEY-STORAGE-SECURITY §4） |
| ロック解除済み端末への物理アクセス | 端末を渡した相手は設定画面を開ける。端末ロックの運用はユーザーの責任範囲 |
| debug ビルド + USB デバッグ ON の検証機 | `adb run-as` で読める。配布は release ビルドで行うこと（KEY-STORAGE-SECURITY §2-③） |
| OpenAI 側でのデータ取り扱い | 送信先はあなたが選んだプロバイダ。OpenAI は現時点で `/v1/audio/transcriptions` について「学習利用なし・abuse monitoring retention なし・application state retention なし」と案内している。ただし規約とエンドポイント仕様は変わるので、[一次情報](https://platform.openai.com/docs/models/default-usage-policies-by-endpoint)を自分で確認すること |
| パスワードマネージャ自体の侵害 | Autofill（DD-012）はあなたが自分で選んで信頼しているマネージャを信頼境界に含める。その選定はユーザーの責任範囲 |

## 硬化したい人へ — 差し替え境界

「この守備範囲では足りない」と考えるなら、フォークして差し替える境界は
2 点に絞ってある:

- **保管**: `settings/Prefs.kt` — Android Keystore による暗号化保存に
  差し替えるならここ（トレードオフは KEY-STORAGE-SECURITY §4）
- **送信先**: `stt/SttEngine.kt` — 別プロバイダやローカル whisper.cpp
  （端末外に一切出さない構成）に差し替えるならここ

UI・IME 層はどちらの変更にも影響されない設計になっている（DD-004）。
