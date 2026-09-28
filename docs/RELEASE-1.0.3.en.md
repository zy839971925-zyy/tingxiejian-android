<p align="center"><a href="RELEASE-1.0.3.md">简体中文</a> · <strong>English</strong></p>

# Tingxiejian v1.0.3 · First-run guide and motion update

> [!CAUTION]
> **Read the third-party rights and disclaimer section before downloading.** The APK includes offline models
> and is not wholly MIT-licensed; some model redistribution rights remain unverified. In-app acceptance is a
> user-side acknowledgment, **not the publisher's permission from rights holders**.

> This document describes the already published v1.0.3 APK. Later bilingual documentation changes on `main`
> **do not change the Release tag or APK**.

## What's new

- Four native onboarding pages ending in Welcome; the start button expands into Home with back-navigation
  and interruption cleanup.
- Reversible motion among Home, Settings, Transcript and Chat. Respects system animation settings;
  adds an in-app **Reduce motion** preference.
- Better home-header behavior at large text sizes, more legible supporting copy, clearer scrolling/action-area
  separation and enlarged touch targets.
- A configured and enabled cloud-recognition mode warns on Home that audio goes to the user-selected endpoint;
  Settings changes refresh upon return.
- Confirmation before clearing chats; the Settings version reflects the installed package.
  See the [changelog](../CHANGELOG.en.md) (post-release translation; source tag remains unchanged).

## Artifact and integrity

Download from the [GitHub v1.0.3 Release](https://github.com/zy839971925-zyy/tingxiejian-android/releases/tag/v1.0.3).

| Item | Value |
| :--- | :--- |
| File | `tingxiejian-v1.0.3-arm64-release.apk` |
| Platform | Android 8.0+, ARM64 (`arm64-v8a`) |
| Package / version | `com.example.tingxiejian` · versionCode `103` · versionName `1.0.3` |
| SHA-256 | `7ad11ac88b27f3017dd5bf0a362aea3df4b031674bd10c2d630b9c73f9f3dad1` |
| Signing certificate SHA-256 | `7cfa494931e2cd6f14eb58f75fdb06a6acfc56bbe1a5ec4c929745aedc67e741` (same as the project's local 1.0.2 APK) |

The APK contains models for offline ASR, punctuation and anonymous speaker grouping; no first-run model
download is necessary. Optional cloud features are configured by the user. Compare the checksum using
`sha256sum tingxiejian-v1.0.3-arm64-release.apk`. Export important results before installing an update;
a differently signed version cannot replace an installed one in place.

## Verification limits

Source regression checks, local offline build, signature/DEX/model packaging validation and local file
SHA-256 passed. **Installation, screen-reader, large-font, ROM-specific animation and long-recording
performance acceptance on actual devices had not been completed for this release.** A successful build
does not prove operation on every device or that a HyperOS island appears.

## Third-party rights and disclaimer

Original project source is [MIT-licensed](../LICENSE); **the APK as a whole is not**. It bundles third-party
libraries (including sherpa-onnx) and model weights subject to their own copyrights/terms. The streaming
Chinese Zipformer conversion's redistribution terms remain unclear, and `embed.onnx` has unverified origin
and licensing. This project cannot grant rights on behalf of their owners.

The app displays component terms and requires an explicit checkbox before first transcription, but **neither
user consent nor an attached disclaimer gives the publisher redistribution rights**. Read the
[third-party components and known rights status](../THIRD_PARTY_NOTICES.md) and
[full disclaimer](../DISCLAIMER.en.md). The software is provided as-is: transcription accuracy, data
retention and operation on every device are not guaranteed. Back up important audio.

Rights holders with distribution concerns can [open an Issue](https://github.com/zy839971925-zyy/tingxiejian-android/issues);
we will review and remove disputed assets.
