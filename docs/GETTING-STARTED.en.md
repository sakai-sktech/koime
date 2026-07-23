# GETTING STARTED — From Source Code to Your First Voice Input

[日本語](GETTING-STARTED.md) | **English**

koetype is not distributed through an app store or as a prebuilt binary, and this guide assumes that you will **build and install your own copy**. That is the intended installation path, not a temporary inconvenience. The reasons are explained in the philosophy section of the README and in the threat model.

## 0. Prerequisites

- **Device:** Android 8.0 / API 26 or later
- **Your own OpenAI API key:** obtain one through <https://platform.openai.com>. OpenAI bills API usage directly to you; the author of koetype is not involved in billing, limits, or account operation. Follow OpenAI's current first-party documentation to create the key and configure spending controls. A password manager is strongly recommended; it becomes useful in section 4.
- **Build environment:** JDK 17, Android SDK with `platforms;android-34`, and adb. Installing Android Studio is the easiest route, but the command-line SDK tools are sufficient.

## 1. Build

```bash
git clone https://github.com/sakai-sktech/koetype.git
cd koetype

# Point Gradle to your Android SDK.
# Android Studio usually creates this file automatically.
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew assembleDebug
```

## 2. Install and enable the IME

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

1. On the device, open **Settings → System → Languages & input → On-screen keyboard**. The exact labels vary by Android vendor.
2. Enable **koetype**.
3. Android will display a warning explaining that a keyboard may be able to collect everything you type. This is the standard operating-system warning for the entire IME category. It should not be dismissed as meaningless: a keyboard really does occupy that trust position. What koetype sends—and what it does not send—can be verified in the source and is summarized in [THREAT-MODEL.en.md](../THREAT-MODEL.en.md).

## 3. Grant microphone permission

Open the **koetype** settings screen from the launcher and select the microphone-permission action to grant `RECORD_AUDIO`.

Android will also request the permission the first time you tap the microphone on the keyboard if it has not already been granted.

## 4. Configure your API key

Enter your own key in the API-key field and save it.

### Recommended path — Password manager + Android Autofill

The API-key field supports Android's standard Autofill Framework. Store the key in Bitwarden, 1Password, Google Password Manager, KeePassDX, or another compatible password manager. Then tap the field and select the saved value from Autofill.

There is no need to email the key to yourself, send it through a chat application, or route it through the clipboard. Depending on the manager, Android may also offer to save the value when you finish.

### Manual entry or paste

When the value is saved, koetype normalizes full-width Latin characters to ASCII and removes whitespace. This exists because a real-device test exposed a failure caused by full-width characters introduced through smartphone text input; see DD-009.

If the settings screen still warns about invalid characters, inspect the key rather than repeatedly retrying the API call.

The default transcription model is `gpt-4o-mini-transcribe`. You can also select `gpt-4o-transcribe` or `whisper-1`. An ISO 639-1 language hint such as `ja` or `en` can improve transcription stability and latency when the spoken language is known.

Model availability and API behavior can change. The current OpenAI transcription API reference is:

<https://platform.openai.com/docs/api-reference/audio/createTranscription>

## 5. Use koetype

1. Focus a text field in any app and switch the active keyboard to **koetype** using the globe key or Android's keyboard switcher.
2. **Tap the microphone → speak → tap it again.** After the buffered audio is transcribed, the text is inserted at the current cursor position. The current recording limit is five minutes.
3. Supporting keys:
   - **◀ ▶** — move the cursor; hold to repeat
   - **↶ Undo — Undo Last Dictation** — removes only the most recent voice-input result as one unit. It works only while the text immediately before the cursor still matches exactly what koetype committed. If you move the cursor or edit the text, the operation is disabled rather than risking deletion of unrelated content.
   - Space, Backspace, Enter, and ⚙ Settings

## 6. Sign a release build with your own key — Recommended for regular use

A release build is recommended for daily use. A debug build leaves the `adb run-as` path described in the threat model.

Create your own signing key. This is not a workaround for the lack of a store certificate; in a user-owned application, your own key is the correct trust anchor. The Japanese [SIGNING.md](SIGNING.md) explains what Android signing proves and why this design is intentional.

```bash
mkdir -p signing
keytool -genkeypair -v \
  -keystore signing/my-release.jks \
  -alias koetype -keyalg RSA -keysize 2048 -validity 10000
```

Create `keystore.properties` in the repository root:

```properties
storeFile=signing/my-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=koetype
keyPassword=YOUR_KEY_PASSWORD
```

**Never commit either the keystore or `keystore.properties`.** The repository's `.gitignore` excludes them for that reason.

Build and install:

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Android permits an app to be updated only by a package signed with the same key. If a debug build is already installed and the release build uses a different signature, uninstall the debug build before installing the release build. Uninstalling removes the app's settings, so be prepared to configure the API key again.

If you lose your signing key, you cannot issue an update under the same application identity. Include the keystore in your own backup plan.

## 7. Troubleshooting

- koetype is designed to show a reason in the keyboard status area whenever transcription fails; it should not silently return to the idle state. To investigate further:

  ```bash
  adb logcat -s koetype AndroidRuntime
  ```

- Emulator-specific traps—including a missing software keyboard and an IME that appears to freeze after reinstall—are documented with reproduction steps in [2026-07-08_emulator-smoke-test.md](2026-07-08_emulator-smoke-test.md) *(Japanese)*.
- A previous real-device failure that appeared as “processing disappears immediately and no text is inserted,” together with the diagnostic path, is documented in [2026-07-09_real-device-bug-fix.md](2026-07-09_real-device-bug-fix.md) *(Japanese)*.
