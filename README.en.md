# koetype — An Android Keyboard You Type With Your Voice

[日本語](README.md) | **English**

koetype is a custom Android IME built specifically for voice input. Tap the microphone, speak, tap again, and the buffered recording is sent to a speech-to-text API. The resulting text is committed directly into the field you were typing in.

It is a small, from-scratch implementation of a Typeless-style buffered voice keyboard.

```text
[tap the microphone] → recording (tap again to stop)
      → send the m4a buffer to the STT API → commit the transcription into the active field
```

<img src="docs/assets/koetype-UI.jpg" alt="koetype keyboard UI — microphone in the center, cursor keys on both sides, and Undo at the upper right" width="480">

- Works in any app because it runs as a system keyboard
- Supports multiple languages through an optional language hint for Whisper-family models
- Lets you correct the insertion point and safely undo the latest transcription with ◀▶ cursor keys (hold to repeat) and the **↶ Undo** key
- Keeps the STT provider behind a small abstraction boundary, `SttEngine`; the current implementation uses the OpenAI transcription API

## Philosophy — Your key, your device, your voice

- **BYOK — Bring Your Own Key.** You obtain your own API key and enter it on your own device. No key is bundled with the app, and there is no server operated by the author. OpenAI bills you directly. You can see the cost, set limits, and revoke the key yourself. There is no invisible intermediary adding a margin or imposing an unexplained quota.

- **The path your voice takes is explicit.** koetype records only while you have deliberately activated the microphone. The audio is sent **directly** to OpenAI using your own key, then removed from the device after transcription. No third party—including the author—sits in the middle. Where your voice goes is determined by the provider you choose.

- **The friction of obtaining an API key is intentional.** koetype is for people who understand what an API key is and can obtain, fund, limit, and revoke one themselves. That is not an unfinished onboarding flow; it is part of the product boundary. See [DD-010](docs/DESIGN-DECISIONS.en.md).

- **There is little reason to surrender control when a personal application can now be assembled and rewritten in minutes.** With modern AI assistance, you can fork this codebase and turn it into your own voice-input tool without beginning from a blank screen. Convenience no longer requires handing somebody else your data and your key. Keep the data. Keep the key. Keep the steering wheel. koetype is a working base for doing exactly that.

- **An IME sits in a position where it could read everything you type.** This is not an ordinary Android app. A malicious keyboard could steal every keystroke you make. That is why I do not consider “trust the developer” to be an adequate security model for a keyboard. The only serious basis for trust is code you can inspect and a build you can produce and sign yourself. **Do not trust it blindly. Read it. Then rewrite it if you need to.** AI will help you do that. See [THREAT-MODEL.en.md](THREAT-MODEL.en.md), which also identifies the boundaries designed to be replaced.

## About this public snapshot

This repository is a snapshot of the **same codebase** I use in day-to-day work. Development continues in a private source-of-truth repository, and not every change made there is guaranteed to be synchronized here.

That also means my response to issues and pull requests may be limited. I would rather state that honestly than pretend this is a conventional community-maintained product.

What I do want is for this repository to become a base from which people can extend voice input within a scope they themselves control. That is why it is public.

## Privacy

- koetype does not collect or transmit ordinary keyboard input.
- The only content sent is audio that you explicitly record by tapping the microphone. It is sent directly to OpenAI using the API key you configured.
- There are no ads, analytics SDKs, or telemetry.

## Getting started

Read **[docs/GETTING-STARTED.en.md](docs/GETTING-STARTED.en.md)**. It covers the complete path from building and installing the app to enabling the IME, configuring your API key, and signing a release build with your own key.

**You do not need prior Android development experience.** When something is unfamiliar, give the guide itself to an AI assistant and use it as an executable set of instructions. It was written to support exactly that workflow. There is no good reason you should be unable to build your own copy. If my explanation leaves a gap, open an issue and point to the gap.

```bash
git clone https://github.com/sakai-sktech/koetype.git
cd koetype && ./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The API-key field supports Android's standard Autofill Framework. A key stored in Bitwarden, 1Password, Google Password Manager, KeePassDX, or another compatible manager can be filled without sending it to yourself by email or chat and without routing it through the clipboard.

This does not transfer responsibility for key management to koetype. It simply avoids obstructing the secure key-management system you already chose for yourself. See [DD-012](docs/DESIGN-DECISIONS.en.md).

## Documentation

| Document | What it covers |
|---|---|
| [THREAT-MODEL.en.md](THREAT-MODEL.en.md) | What koetype protects against, and what it explicitly does not |
| [docs/GETTING-STARTED.en.md](docs/GETTING-STARTED.en.md) | Complete setup procedure |
| [docs/DESIGN-DECISIONS.en.md](docs/DESIGN-DECISIONS.en.md) | Decision log DD-001 through DD-013—the reasons behind the current shape of the software |
| [docs/KEY-STORAGE-SECURITY.md](docs/KEY-STORAGE-SECURITY.md) | Where the API key is stored, what protects it, and how to frame the same questions for an iOS port *(Japanese)* |
| [docs/SIGNING.md](docs/SIGNING.md) | What Android signing proves and why a user-owned self-signed build is the intended design *(Japanese)* |
| [docs/PRODUCTIZATION.md](docs/PRODUCTIZATION.md) | The operational barriers a fork would need to cross before becoming a managed service *(Japanese)* |
| `docs/2026-07-08`, `07-09`, and `07-21` records | Implementation and verification logs: emulator traps, a real-device crash investigation, and the design record for additional keys *(Japanese)* |

The Japanese documents remain the source of truth where an English translation has not yet been published.

## Project structure

```text
app/src/main/java/dev/sakai/koetype/
  ime/        InputMethodService and keyboard UI
  audio/      Recording: MediaRecorder → m4a buffer
  stt/        SttEngine abstraction + OpenAI implementation
  settings/   Settings screen and permission flow
```

## Phase 2 — Where this base can go

- **Add an AI refinement layer.** Put an LLM after transcription for filler removal, cleanup, or domain-specific transformation into more precise text.
- **Move toward streaming.** Replace the buffered workflow with near-real-time transcription.
- **Add local STT.** A phone will eventually be able to perform this level of transcription entirely on-device. When that time comes, a local engine such as whisper.cpp can be placed behind `SttEngine`.
- Most importantly, **the person using the tool should be able to turn it into their own tool**. `SttEngine` exists as the replacement boundary that makes that possible.

## License

[MIT](LICENSE)
