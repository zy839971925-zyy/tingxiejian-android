<p align="center"><a href="RELEASE-1.0.4.md">简体中文</a> · <strong>English</strong></p>

# v1.0.4 · Local high-accuracy transcription and live progress (stable release)

APK version **1.0.4-dev**, versionCode **104**, ARM64, Android 8.0+, compileSdk 36 / minSdk 26 / targetSdk 35. The GitHub release is published as a **stable release** and is no longer marked pre-release; development signing and acceptance limits are described below.

## Download and verification

- [GitHub Release](https://github.com/zy839971925-zyy/tingxiejian-android/releases/tag/v1.0.4)
- APK: `tingxiejian-v1.0.4-arm64-dev.apk`
- Size: **1486302483 bytes** (about 1.49 GB)
- SHA-256: `a0b75988a4f385b7818e36265a4c8c994cca5d4928863e05d3ddf25af2dfa60c`
- Signing certificate SHA-256: `d27080e3be22f33461a69ce777dbb871436f4a6997ae9162dc8f81c72e4c3839`
- Matching `.sha256` file is attached; run `sha256sum -c tingxiejian-v1.0.4-arm64-dev.apk.sha256`.

The certificate matches the previously delivered 1.0.3-dev APK and can update that development signing series. It differs from the historical production identity; **export history before changing signing identities and do not simply uninstall the old app**. Default builds do not use production signing. This build ships with development signing and does not claim to satisfy long-term production signing and complete model-redistribution verification gates.

## Changes

- Shared local file/live-dictation pipeline: VAD, immediate draft, Qwen3-ASR 0.6B int8 / Paraformer segment refinement, punctuation, bounded queues and failure fallback.
- A configured API key enables automatic lightweight segment correction during transcription; missing key is explicitly skipped. Faithful and polished text are separate; “Ask AI” remains independent.
- Explicit foreground dictation start, pause and finish; backgrounding stops microphone capture. History/session persistence and interrupted-state diagnostics improved.
- Android 16 ProgressStyle / promoted ongoing notification: existing Xiaomi Super Island first, then standard Live Update and ordinary FGS notification. ColorOS 16 uses standard APIs only; Shizuku is not a global dependency.
- Native UI, safe areas, large text, touch and reduced-motion fixes; selected-layer search, TXT / SRT / JSON export mapping, asynchronous player preparation/cleanup and chat draft fixes.
- Physical-phone feedback fixes: discoverable model selection and status, explicit bundled Qwen preparation with progress and optional import, standalone onboarding configuration/authorization dialog, device-specific Super Island/Fluid Cloud/native notification labels, rectangular wide buttons instead of stretched ovals.
- Android Keystore key storage/migration, separate development/production signing, model provenance/hash manifest, inexpensive host regressions and CI.

## Bundled models and limits

The APK contains 13 model assets from core-streaming, accurate-finalizer, punctuation and qwen3-asr-0.6b-int8. The Qwen pack has six files totaling **987015347 bytes**, requiring no separate download; extraction consumes additional private storage. Native Qwen loading requires **3 GiB of currently available system memory**. Otherwise standard refinement is attempted and disclosed. Prepared files do not prove successful high-accuracy inference.

The diarization pack is not included; missing speaker models are reported and skipped without losing text. Streaming Zipformer redistribution terms remain unresolved. Historical `embed.onnx` provenance is unresolved and it is not included here. Project MIT does not cover third-party models, and user acknowledgement does not grant redistribution authorization. See [notices](../THIRD_PARTY_NOTICES.md), [disclaimer](../DISCLAIMER.en.md) and [model records](upgrade/MODELS.md).

## Verification and remaining device work

Resources/Java/DEX compilation, v2/v3 signing, zipalign, packaging/startup dependency checks, and host checks for model installation, notification fallback, Xiaomi preservation, layouts, guide, visuals, motion, search/export were run. See the [implementation record](upgrade/IMPLEMENTATION.md), [UI record](upgrade/ui/README.md) and [feedback fixes](upgrade/feedback/README.md).

Before release the maintainer exercised the main features of this build on physical devices and found no problems. OEM-specific surfaces still depend on the device and OS version: constructing or submitting a notification is **not** evidence that Super Island or Fluid Cloud was shown. No Android model accuracy benchmark was run in this round; Qwen accuracy, device memory and speed remain device- and audio-dependent.

Short checklist: select/prepare models and confirm actual refinement; grant/deny onboarding permissions and handle an unavailable Shizuku while remaining in the guide; check HyperOS Super Island plus stop/repeated-task cleanup; check ColorOS 16 standard live-update permission and ordinary-notification fallback, and ordinary notifications only on 14/15.
