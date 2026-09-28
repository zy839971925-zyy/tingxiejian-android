# Build from source / 源码构建

> This is a **source** repository, not a mirror of third-party model files. Before downloading,
> using, packaging or distributing weights/fonts, check the applicable rights and gated terms.
> In particular the streaming model's redistribution license and `embed.onnx` provenance remain
> unconfirmed. The existing private APK must not be reuploaded as an MIT GitHub Release.

## Environment

- Android ARM64 with Termux; Android SDK platform **35** (`android.jar`).
- JDK 21 (compiles Java source with `--release 8`), Python 3, `curl`, `unzip`, `zipalign`,
  `apksigner`, `aapt2`, and `d8`. On Termux, package names include `openjdk-21`, `python`,
  `curl`, `unzip`, `aapt2`, `apksigner`, `d8` and `aapt` (for `zipalign`).
- At least 2 GB free for intermediate APKs, unpacked models and the signed app.

## 1. Prepare licensed libraries

Download/install the official Android SDK Platform 35 using Android SDK tools and export
`ANDROID_HOME`, or manually place its `android.jar` at `vendor/android.jar`.
Obtain the Android AAR for sherpa-onnx **1.13.8** from the
[upstream project](https://github.com/k2-fsa/sherpa-onnx) and place it at
`vendor/sherpa-onnx-1.13.8.aar`. Check the AAR version and its own license.
The script fetches the Shizuku 13.1.5 modules (including mandatory `aidl` and `shared`),
HiddenApiBypass 6.1, Kotlin 1.7.20 and the **desktop-test-only** JSON 20240303 JAR from Maven
Central, checks each downloaded/cached file against the pinned `scripts/maven-sha256.txt`, and
extracts `vendor/classes.jar` from the AAR you supplied. `build.sh` rechecks hashes and verifies
that every extracted compile JAR matches its AAR. Manually supplied SDK, sherpa AAR and models
are **not** authenticated by those Maven checks; confirm their provenance separately.

```bash
bash scripts/prepare-libraries.sh
```

`vendor/` is not tracked. Review the [third-party notices](../THIRD_PARTY_NOTICES.md) before
shipping binaries. `res/font/misans.ttf` is intentionally not required: the open-source theme
uses Android's system sans-serif fallback. Do not commit an externally obtained MiSans TTF.

## 2. Supply models locally

The build script reads exactly these files under `models/` (all paths relative to repository root):

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

The list describes **build inputs**, not permission to redistribute them. Review the linked
upstream sources in `THIRD_PARTY_NOTICES.md`; obtain gated access where required. The streaming
conversion has no clear model license and the exact origin of this project's embedding file is
unverified. If you cannot establish appropriate rights, **do not create or publish a binary
containing those models**. Replacing them with properly licensed compatible models requires
validation of tensor shapes and transcriber behavior, not just renaming files.

## 3. Test and build

```bash
bash design-tools/check-all.sh
bash build.sh
```

Output: `dist/tingxiejian-v1.0-arm64-release.apk` (ARM64; target SDK 35). The build script
creates a **local** signing identity under `build/` if absent. Keep it private. The APK is
non-debuggable, but that alone does not constitute official Xiaomi/App Store signing.
A newly generated identity cannot update a copy signed with a different key; uninstalling an
existing app can erase its local history. Back up your data before changing signatures.

The checks cover native pure-Java logic, export formatting, View wiring, visual/portal structure, cloud isolation,
Shizuku fallbacks, six payload cases, APK classes/signature/ZIP and the nine stored model
entries. They do **not** confirm OEM island rendering or on-device recognition accuracy.

## Source portability

`scripts/prepare-libraries.sh` and the checks are self-contained. A clean checkout is **not a
self-contained APK build** until its SDK, sherpa AAR and rights-cleared model files are supplied.
No sibling
`~/offline-transcriber` directory is needed. All downloaded libraries, model weights,
MiSans, APKs, private backups and signing secrets are ignored by Git. For a reproducible
upstream CI pipeline, substitute models with verified redistributable assets before enabling
public artifact uploads.
