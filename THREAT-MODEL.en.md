# KoIME Threat Model — What It Protects, and What It Does Not

[日本語](THREAT-MODEL.md) | **English**

KoIME stores its API key in **plaintext inside the app's private storage**. See [KEY-STORAGE-SECURITY.md](docs/KEY-STORAGE-SECURITY.md) for the detailed Japanese explanation.

That is not an omission that has been quietly ignored. It is an explicit design decision, DD-005. But a decision like that is defensible only when its assumptions and protection boundary are stated plainly. This document does that.

## Assumption

**The API key and the device have the same owner: you.**

KoIME has no operator and no agent acting on the user's behalf. The party the design is intended to defend against is a third party trying to gain access to your device—not you, and not an application operator holding a key for you.

Any deployment that breaks this assumption is outside this threat model: distributing one key to other people, installing it on a shared device, or operating KoIME as a managed service. For those cases, read [PRODUCTIZATION.md](docs/PRODUCTIZATION.md).

## Assets being protected

1. Your API key—if it leaks, somebody else can use the API on your bill
2. Your voice recording
3. The transcribed text—the content you are entering

## In scope — Threats the design addresses

| Threat | Defense |
|---|---|
| Another app reading the key or settings | Android application sandbox: app-private storage with `MODE_PRIVATE`. On a non-rooted device, other apps cannot read it. |
| Extracting files over USB or MTP | `/data/data` is not exposed through MTP. |
| Key leakage through backup or device migration | `allowBackup="false"`, plus Android 12+ `dataExtractionRules` that explicitly exclude every storage domain from both cloud backup and device-to-device transfer. |
| Device theft or physical extraction | Devices launched with Android 10 or later are required to use file-based encryption. Older devices upgraded from earlier Android versions may use full-disk encryption instead. Either layer is intended to prevent offline extraction without the device lock credential. |
| Network interception | TLS communication to OpenAI only. The app has no other network destination. |
| Recorded audio remaining on the device | Recording is written only to `cacheDir` and deleted immediately after transcription. It is not persisted as user data. |
| API key appearing in logs or crash reports | Project rule: never include the key in logs or exception messages. Full-width characters, whitespace, and other malformed input are normalized or rejected at the entry boundary; see DD-009. |
| Suspicion that the IME itself is collecting typed content | You can **verify the absence of collection code in the source**. The two files to inspect first are `KoimeImeService.kt`, which contains the `commitText` path, and `OpenAiSttEngine.kt`, which contains the only network destination. |

## Out of scope — Threats the design explicitly does not claim to solve

| Threat | Why it is out of scope |
|---|---|
| A rooted device or a malicious app with root privileges | That attacker is outside the Android application sandbox. Application-layer encryption does not fundamentally solve a fully compromised device; Android Keystore encryption does not change that fact. See KEY-STORAGE-SECURITY §4. |
| Physical access to an already-unlocked device | Somebody holding an unlocked device can open the settings screen. Device-lock operation belongs to the user. |
| A debug build on a test device with USB debugging enabled | `adb run-as` can read debug-app private storage. Use a release build for regular use or distribution; see KEY-STORAGE-SECURITY §2-③. |
| OpenAI's handling of submitted API data | OpenAI is the provider selected by the user. OpenAI currently lists `/v1/audio/transcriptions` as not used for training, with no abuse-monitoring retention and no application-state retention. Policies and endpoint-specific controls can change, so verify the current [first-party data-control table](https://platform.openai.com/docs/models/default-usage-policies-by-endpoint) yourself. |
| Compromise of the password manager | Autofill (DD-012) places the password manager you selected inside your trust boundary. Choosing and operating that manager remains your responsibility. |

## Hardening the design — The intended replacement boundaries

When this protection boundary is not sufficient for you, the fork points are deliberately limited to two places:

- **Storage:** `settings/Prefs.kt` — replace this boundary if you want to encrypt the stored value with Android Keystore. Read KEY-STORAGE-SECURITY §4 first; encryption adds trade-offs rather than magic.
- **Destination:** `stt/SttEngine.kt` — replace this boundary to use another provider or a local engine such as whisper.cpp, including a design in which audio never leaves the device.

Neither change requires the UI or IME layer to be redesigned. That separation is the point of DD-004.
