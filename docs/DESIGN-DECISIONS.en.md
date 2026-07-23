# koetype Design Decision Log

[日本語](DESIGN-DECISIONS.md) | **English**

> Format: what was decided / why / alternatives considered / trade-offs accepted.
>
> The goal is that a future development session can reconstruct *why the code looks this way* from this log alone.

## DD-001: Write it from scratch instead of forking an existing project — 2026-07-08

- **Decision:** Implement the IME, recording layer, and STT client from scratch. `j3soon/whisper-to-input`, FUTO Voice Input, and `woheller69/whisperIME` were used only as design references.
- **Why:** Avoid inheriting licensing constraints and begin phase 2—AI refinement and streaming—with a codebase whose entire shape is understood. A buffered MVP is small enough that a clean implementation is realistic.
- **Alternative considered:** Fork `whisper-to-input`. That would begin from working code, but understanding another person's architecture and determining the safe modification surface was judged likely to cost as much as, or more than, writing the small MVP directly.
- **Trade-off accepted:** Give up the stability of an established codebase and encounter IME-specific traps directly, including fullscreen extract mode and the `InputConnection` lifecycle.

## DD-002: Use a buffered workflow for the MVP — record, stop, transcribe once, insert — 2026-07-08

- **Decision:** Do not begin with real-time streaming. A tap starts recording; another tap stops it; the complete audio file is then sent to the STT API.
- **Why:** Stable WebSocket and PCM streaming on mobile introduces network interruption, battery, partial-result, and text-revision problems. Those problems are orthogonal to the MVP question: *does this work as an IME?* Typeless itself also presents a buffered-feeling interaction.
- **Alternative considered:** Streaming transcription through the OpenAI Realtime API. Preserved as a phase-2 candidate.
- **Trade-off accepted:** There is a delay of several seconds between the end of speech and insertion. Long recordings are constrained by the 25 MB upload limit and API timeouts, so the MVP protects itself with a recording-duration limit.

## DD-003: Record with MediaRecorder into m4a/AAC — 2026-07-08

- **Decision:** Use `MediaRecorder` with an MPEG-4 container, AAC audio, 16 kHz mono, and write the recording into `cacheDir`.
- **Why:** This is dramatically simpler than `AudioRecord` plus custom WAV construction. The audio is already compressed, upload size is smaller, and the OpenAI transcription endpoint accepts m4a.
- **Alternative considered:** `AudioRecord` with PCM/WAV. That will be needed for streaming or a local whisper.cpp engine, so this decision is explicitly allowed to change in phase 2.
- **Trade-off accepted:** Raw PCM is not retained. Replacing cloud transcription with a local engine may therefore require changing the recording layer as well as the STT layer.

## DD-004: Put STT behind the SttEngine interface; implement OpenAI first — 2026-07-08

- **Decision:** Define `SttEngine.transcribe(...): Result<String>` as the provider boundary. The first implementation calls OpenAI's `/v1/audio/transcriptions` endpoint with multipart upload through OkHttp. The model can be selected from `gpt-4o-transcribe`, `gpt-4o-mini-transcribe`, and `whisper-1`.
- **Why:** A cloud API gives the MVP mature multilingual accuracy with no inference load on the device. The interface leaves room for local whisper.cpp or another provider later.
- **Alternatives considered:** Begin with local whisper.cpp, which would bring NDK builds, model distribution, and device-load questions into the MVP; or support local and cloud engines from day one, which would roughly double the initial implementation surface.
- **Trade-off accepted:** koetype does not work offline, and API usage is paid by the user through the user's own key.

## DD-005: Store the API key as plaintext in SharedPreferences(MODE_PRIVATE) — 2026-07-08

- **Decision:** Store the key entered in the settings screen in app-private `SharedPreferences`. Do not use `EncryptedSharedPreferences`.
- **Why:** `androidx.security-crypto` has moved toward deprecation and Keystore-dependent implementations create device-specific failure modes. App-private storage is not readable by other applications on a non-rooted Android device.
- **Alternatives considered:** `EncryptedSharedPreferences`, with its deprecation and compatibility risks; or a custom Keystore implementation, judged excessive for this owner-operated personal tool.
- **Trade-off accepted:** A rooted device, a compromised device, or a debug build accessible through `adb run-as` can expose the plaintext value. The `adb backup` path is closed by `allowBackup="false"`; see the threat model. That is accepted under the personal-device, personal-key assumption and reinforced by the rule that the key never appears in logs or exception messages.

## DD-006: Choose the toolchain that was already locally available — 2026-07-08

- **Decision:** Gradle 8.2, AGP 8.2.0, Kotlin 1.9.20, compileSdk 34, minSdk 26, and targetSdk 34.
- **Why:** Every dependency was already present in the local Gradle and Android SDK caches, so the project could be built without large downloads. AGP 8.2 matches JDK 17 and compileSdk 34 was installed. minSdk 26 keeps the required MediaRecorder and IME APIs straightforward.
- **Alternative considered:** Move immediately to the newest AGP and compileSdk. The MVP did not require newer APIs, so the download and compatibility work would not have answered an MVP question.
- **Trade-off accepted:** If the project is ever submitted to Google Play, the target SDK will need to move forward. When that work is done, the toolchain should be upgraded as one coordinated unit rather than one component at a time.

## DD-007: Use classic Views, not Compose, and keep dependencies minimal — 2026-07-08

- **Decision:** Build the keyboard UI with XML layouts and ordinary Android Views. The only external runtime dependencies are kotlinx-coroutines and OkHttp; JSON handling uses Android's bundled `org.json`.
- **Why:** `InputMethodService.onCreateInputView()` is a View-oriented API. Hosting Compose inside an IME introduces lifecycle-owner work that is not justified by two small screens: the keyboard and settings.
- **Alternative considered:** Compose, possibly for the settings screen in a later phase.
- **Trade-off accepted:** View-based UI code is more verbose, but the screen count is small and the runtime surface remains obvious.

## DD-008: Disable fullscreen extract mode — 2026-07-08

- **Decision:** Make `onEvaluateFullscreenMode()` always return `false`.
- **Why:** Emulator testing on a wide display showed Android entering fullscreen extract mode when the IME opened, completely hiding the host application. Voice input depends on preserving the context of *where* the transcription will be inserted. Hiding the application is therefore actively harmful.
- **Alternative considered:** Keep Android's default behavior and allow fullscreen mode in landscape layouts.
- **Trade-off accepted:** On an unusually short display, the keyboard may cover the input field. Revisit only if that becomes a real-device problem.

## DD-009: Catch failure in three layers — normalize at entry, return Result at the boundary, catch once more at the scope — 2026-07-09

- **Decision:**
  1. Normalize the API key when settings are saved: convert full-width Latin characters to ASCII, remove whitespace, and reject remaining non-ASCII characters.
  2. Require every `SttEngine` implementation to convert every `Throwable` except `CancellationException` into `Result.failure`.
  3. Attach a `CoroutineExceptionHandler` to the IME service scope so that anything escaping the engine becomes visible status text rather than a dead IME process.
- **Why:** The first real-device test showed “processing” followed immediately by the idle UI with no inserted text. Reproduction on the emulator revealed a full-width character (`0x30C6`) in the API key. OkHttp's `header()` threw `IllegalArgumentException`; the exception escaped; the **entire IME process died and restarted**. The restart looked like a harmless return to the initial state, giving the user no diagnostic signal at all. Silent crashes invite the wrong diagnosis.
- **Alternatives considered:** Catch everything only inside the engine, which would preserve an invalid key and fail repeatedly without explaining why; or validate only at entry, which would leave future boundary exceptions capable of killing the process. The three layers are not redundant—they have different jobs.
- **Trade-off accepted:** `catch(Throwable)` can conceal programming defects. It is permitted only with mandatory `Log.e("koetype", ...)` output so the actual cause remains available through logcat.

## DD-010: Keep koetype out of the managed-service business and fix BYOK as its design position — 2026-07-21

- **Decision:** koetype will remain a BYOK personal tool: the user owns the API key and accepts responsibility for it. The project will not become a managed service in which an operator stores the provider key on the user's behalf. A separate [PRODUCTIZATION.md](PRODUCTIZATION.md) records the barriers that a fork would have to cross before becoming such a service.
- **Why:** The core value of koetype is that the user keeps a hand on the steering wheel. Holding your own key is not a defect. It gives direct visibility into cost, removes the author's infrastructure from the audio path, and preserves freedom to replace the provider. Turning it into a service is not a small UI improvement; it transfers variable cost, abuse risk, and responsibility for voice data to an operator. That is a structural change incompatible with the position of an owner-operated OSS tool.
- **Alternative considered:** A two-tier design—free BYOK plus a paid managed tier. `SttEngine` makes the two technically compatible, but the managed tier still creates the same operational responsibilities, so it is rejected. See PRODUCTIZATION §7.
- **Trade-off accepted:** koetype reaches only people willing and able to cross the initial barrier of obtaining an API key. That is accepted as deliberate audience selection.

## DD-011: Add keys without adding another row — cursor keys and safe undo — 2026-07-21

- **Decision:**
  1. Place ◀ and ▶ cursor keys in the dead space to the left and right of the microphone. Implement hold-to-repeat immediately: a 400 ms delay followed by 50 ms repeats, sent with `KEYCODE_DPAD_LEFT/RIGHT` through `sendDownUpKeyEvents`.
  2. Place the **全消し** key at the right edge of the status row. Despite the Japanese label, it does **not** clear the whole field. It removes only the transcription most recently committed by koetype, and only when the text immediately before the cursor still matches exactly. If the active input field changes, the undo candidate is discarded.
- **Why:** Because transcription is inserted at the cursor, correcting the insertion point and discarding one bad transcription are fundamental voice-input operations. At the same time, every additional row makes more of the host application disappear; DD-008 makes that cost explicit. The microphone row and status row already contained unused horizontal space, so the functionality could be added at zero height cost. Exact-match validation prevents the IME from deleting unrelated text after cursor movement or manual edits.
- **Alternatives considered:** Add another key row, rejected because of height; make “clear” erase the entire field, rejected because an IME has no right to delete text it did not write; or provide single-tap arrows only, rejected because moving ten characters should not require ten taps.
- **Trade-off accepted:** Undo works only immediately after transcription. Even inserting one manual space causes the exact match to fail and disables the operation. Safety wins, and the status area explains why nothing was deleted.

## DD-012: Open the API-key field to Android Autofill — Removing accidental friction does not weaken the intentional barrier — 2026-07-22

- **Decision:** Remove `android:importantForAutofill="no"`, add suitable `autofillHints`, and allow password managers using Android's standard Autofill Framework to fill the API-key field directly. No vendor-specific SDK or direct integration with a password-manager service will be added.
- **Why:** The deliberate barrier in koetype is that the user must understand, obtain, fund, and manage an API key. Typing a long secret by hand or accidentally pasting a newline does nothing to test that literacy. Those are forms of **accidental friction**, not useful selection. Removing them strengthens the BYOK position by optimizing for people who already keep secrets in their own password-management system. It also removes the clipboard path and its formatting and exposure risks.
- **Alternatives considered:**
  1. Direct vendor APIs. Personal mobile vaults generally do not expose a simple public retrieval API; server products such as 1Password Connect merely move the “where does the authentication token live?” problem one level outward. Per-vendor maintenance also crosses the project boundary defined by DD-010.
  2. GitHub Secrets or authenticator-app integration. GitHub Secrets are write-only from the relevant workflow perspective, while authenticator apps expose time-based one-time passwords rather than arbitrary secret retrieval.
  3. Keep Autofill disabled. There was no recorded security reason for blocking the user's existing secret-management system.
- **Trade-off accepted:** The password manager may display a “save this value?” prompt. That is not only acceptable but desirable. Autofill places the selected manager inside the trust boundary, but it is a party the user already selected and chose to trust. Real-device behavior still needs to be checked with actual managers installed.

## DD-013: Keep the SDK current even without Google Play — Not because of a deadline, but as maintenance hygiene — 2026-07-22

- **Decision:** Do not publish koetype through Google Play. There is no store or binary distribution at all; building it yourself is the only installation path, the same policy stated in GETTING-STARTED and SIGNING.md. Nevertheless, continue to update compileSdk, targetSdk, and the Gradle/AGP/Kotlin toolchain. The next build-system change should move compileSdk and targetSdk to 36 and upgrade the toolchain as a coordinated unit, following the “move everything together” principle from DD-006. Then verify the complete IME flow on an API 36 emulator: enable the IME, grant microphone permission, use Autofill, and move the app through foreground/background transitions while recording. After that, aim for roughly one maintenance pass per year.
- **Why:** Google Play target-SDK deadlines disappear when the app is not submitted to Play. The operating system does not reject installation merely because the target SDK is a few generations old. But three practical reasons remain:
  1. **The user's OS keeps moving.** Older target levels increasingly run through compatibility paths that receive less testing. IMEs are particularly exposed to behavior switches such as edge-to-edge enforcement, predictive back, and microphone privacy changes. If raising targetSdk breaks something, leaving it low only postpones the same collision with a future OS change. It is better to observe the break on your own schedule.
  2. **Toolchains rot.** The longer the pause, the wider and more painful the eventual coordinated jump becomes.
  3. **Future local STT creates native requirements.** A whisper.cpp implementation may require a newer NDK and AGP to support devices using 16 KB memory pages. That device reality exists independently of Google Play.

  The SDK-version question itself was initially missed and surfaced only through discussion with ChatGPT; the full conversation is preserved in the private source-of-truth repository. The lesson is recorded explicitly: **“Not on Play” does not mean “safe to freeze the SDK forever.”**
- **Alternatives considered:**
  1. Freeze the project for as long as it still runs. With only OkHttp and coroutines, that looks cheap today, but it merely defers compatibility decay and increases the eventual jump.
  2. Upgrade immediately to satisfy the next Play deadline. Because koetype is not on Play, that date creates no obligation and no reason to rush.
- **Trade-off accepted:** Pay for periodic upgrades and full verification even when no new API is needed. A fixed rhythm—roughly yearly, coordinated, and tested end to end—reduces both decision overhead and the size of each future jump.

## DD-014: Unify the UI on basic English only — de-localization as the conclusion of renaming 全消し — 2026-07-23

- **Decision:** Every UI string on the keyboard and the settings screen becomes basic English (TAP 🎤 & SPEAK, UNDO, SPACE, NOTHING TO UNDO, …). There are no per-locale resources; a single default resource file is the whole catalog. The full string catalog, including the Japanese originals, is maintained in [LOCALIZATION.md](LOCALIZATION.md). Future localization happens by adding `values-<lang>/strings.xml`, listed in the README as a phase-2 direction.
- **Why:** The trigger was the naming problem of the 全消し key. The implementation performs “undo the most recent transcription only” (`undoLastCommit`, guarded by exact-match validation), yet the label advertised exactly the whole-field clearing that DD-011 had rejected as overreach. The problem surfaced during translation, when the English documentation needed the disclaimer “Despite the Japanese label, it does **not** clear the whole field” — translation acted as a lint for the bad name. While evaluating the Japanese rename candidate 取消し, two observations tipped the decision: the keyboard surface is almost entirely symbols (🎤 ⌫ ⏎ ⚙ ◀▶) plus a few short words, and the BYOK audience defined in DD-010 — people who can obtain an API key on an English-language platform — can read basic English by definition. Going English-only makes the label and its feedback share one vocabulary (UNDO ↔ UNDONE / NOTHING TO UNDO), shows every user the same labels regardless of device language, and avoids creating a localization matrix that would then have to be maintained.
- **Alternatives considered:** (1) Rename to 取消し — the best move within Japanese, but a Japanese-only UI does not serve non-Japanese readers of a public repository. (2) The “↶ Undo” label from PR #2 — an English label over Japanese status messages merely relocates the vocabulary mismatch across languages; it was merged and then superseded by this decision. (3) Full localization via `values-en/` — creates a language × string maintenance matrix that is excessive for an owner-operated tool. The catalog in LOCALIZATION.md preserves the option of reintroducing it later.
- **Trade-off accepted:** The author's own daily UI is now English. The Japanese status messages' finer-grained guidance is constrained to basic-English phrasing. The screenshot (assets/koetype-UI.jpg) still shows the old 全消し label and will be retaken at the next on-device verification.

## DD-015: Pull requests to this repository are a first-class entry point; consistency with the source of truth is guaranteed at session start — 2026-07-23

- **Decision:** Pull requests merged directly into this repository are never rolled back; they stay in the history as the record of the contribution. Development happens in a private source-of-truth repository (see “About this public snapshot” in the README). Merged changes flow **back into the source of truth first, and then return through subsequent snapshot syncs**. The invariant is that the public HEAD and the source-of-truth staging tree must match at the moment the author starts working.
- **Why:** The trigger was a real near-miss: the English-translation PR #1 and the backup-boundary PR #2 were merged here directly, and the private source of truth briefly fell behind with nothing in place to detect it. Rolling back merged PRs would erase the record of a contribution, which is dishonest. Forbidding *unreconciled* divergence — rather than divergence itself — lets external contributions and one-way snapshot development coexist.
- **Alternatives considered:** Closing PRs and re-implementing them in the source of truth (erases the contributor's record); making the public repository the source of truth (incompatible with keeping some private implementations out of the snapshot). Both rejected.
- **Trade-off accepted:** A response to a PR may arrive as a later sync commit rather than an immediate follow-up commit, and there can be a lag before a merged change is reconciled. This is the same constraint already stated honestly in the README: responsiveness here is limited by design.
