<p align="center"><a href="BUILDING.md">简体中文</a> · <strong>English</strong></p>

# Build from source

> [!CAUTION]
> This is a **source** repository, not a mirror of third-party model files. Before downloading,
> using, packaging or distributing weights/fonts, check applicable rights and gated terms.
> The streaming model's redistribution terms and `embed.onnx` provenance remain unconfirmed.
> A locally built or public APK must not be labeled wholly MIT-licensed. **User acceptance and
> disclaimers do not replace upstream redistribution permission.** See the
> [release rights status](RELEASE-1.0.3.en.md) and [third-party notices](../THIRD_PARTY_NOTICES.md).

## Environment

- Android ARM64 with Termux; Android SDK Platform **35** (`android.jar`).
- JDK 21 (compiles Java with `--release 8`), Python 3, `curl`, `unzip`, `zipalign`,
  `apksigner`, `aapt2`, and `d8`. On Termux, package names include `openjdk-21`, `python`,
  `curl`, `unzip`, `aapt2`, `apksigner`, `d8`, and `aapt` (for `zipalign`).
- At least 2 GB of free space for intermediate APKs, extracted models and the signed app.

## 1. Prepare licensed libraries

Download/install the official Android SDK Platform 35 with SDK tools and export `ANDROID_HOME`,
or place its `android.jar` in `vendor/android.jar`. Obtain the Android AAR for sherpa-onnx **1.13.8**
from [upstream](https://github.com/k2-fsa/sherpa-onnx) and place it at
`vendor/sherpa-onnx-1.13.8.aar`; check the version and its own license.

The script fetches Shizuku 13.1.5 modules (including mandatory `aidl` and `shared`),
HiddenApiBypass 6.1, Kotlin 1.7.20 and the **desktop-test-only** JSON 20240303 JAR from Maven Central.
It compares downloaded/cached files with pinned `scripts/maven-sha256.txt`, then extracts
`vendor/classes.jar` from the supplied AAR. `build.sh` rechecks hashes and verifies extracted
compile JARs match their AARs. **Manual SDK, sherpa AAR and model files are not authenticated
by the Maven hash list**; verify provenance separately.

```bash
bash scripts/prepare-libraries.sh
```

`vendor/` is not tracked. Read the [third-party notices](../THIRD_PARTY_NOTICES.md) before
shipping binaries. `res/font/misans.ttf` is intentionally unnecessary: the open-source theme
uses the Android system sans-serif fallback. Do not commit an externally obtained MiSans TTF.

## 2. Supply models locally

The build script reads exactly these files under `models/` (paths relative to repository root):

```text
sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30/
  encoder.int8.onnx  decoder.onnx  joiner.int8.onnx  tokens.txt
sherpa-onnx-paraformer-zh-2023-09-14/
  model.int8.onnx  tokens.txt
sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12-int8/
  model.int8.onnx
sherpa-onnx-pyannote-segmentation-3-0/
  model.int8.onnx
embed.onnx
```

This is a list of **build inputs**, not permission to redistribute them. Review upstream sources
in [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) and obtain gated access where required.
The streaming conversion has no clear model license, and the exact origin of this project's embedding file
is unverified. If you cannot establish appropriate rights, **building, publishing, user confirmation
or a disclaimer does not grant them**. Public distribution carries unresolved upstream-rights risk.
Replacing weights with compatible properly licensed models requires validation of tensor shapes
and transcriber behavior, not merely renaming files.

## 3. Test and build

```bash
bash design-tools/check-all.sh
bash build.sh
```

Output: `dist/tingxiejian-v1.0.3-arm64-release.apk` (ARM64, target SDK 35). The script creates a
**local** signing identity under `build/` if absent; keep it private. A non-debuggable APK is not
an official Xiaomi/App Store-signed release. A fresh key cannot update a differently signed copy;
uninstalling the old app can erase local history. Export important results before changing signatures.

Checks cover pure-Java logic, export formatting, View wiring, visual/portal structure, cloud isolation,
Shizuku fallback, six island payload cases, APK classes/signature/ZIP, and nine STORED model entries.
They do **not** establish device-level recognition accuracy or that an OEM island rendered.

## Source portability

`scripts/prepare-libraries.sh` and the checks are self-contained. A clean checkout is **not a
self-contained APK build** until its SDK, sherpa AAR and rights-cleared model files are supplied.
No sibling `~/offline-transcriber` directory is needed. Downloaded libraries, model weights,
MiSans, APKs, private backups and signing secrets are ignored by Git. Before enabling public
artifact uploads in CI, use models with verified redistribution rights.
