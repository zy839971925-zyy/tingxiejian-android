<p align="center"><a href="BUILDING.md">简体中文</a> · <strong>English</strong></p>

# Build from source

> [!CAUTION]
> This is a **source** repository, not a mirror of third-party model files. Before downloading,
> using, packaging or distributing weights/fonts, check applicable rights and gated terms.
> The streaming model's redistribution terms and `embed.onnx` provenance remain unconfirmed.
> A locally built or public APK must not be labeled wholly MIT-licensed. **User acceptance and
> disclaimers do not replace upstream redistribution permission.** See the
> [release rights status](RELEASE-1.0.4.en.md) and [third-party notices](../THIRD_PARTY_NOTICES.md).

## Environment

- Android ARM64 with Termux or Linux Cloud; Android SDK Platform **36** (compile `android.jar`), retaining minSdk 26 and targetSdk 35.
- JDK 21 (compiles Java with `--release 8`), Python 3, `curl`, `unzip`, `zipalign`,
  `apksigner`, `aapt2`, and `d8`. On Termux, package names include `openjdk-21`, `python`,
  `curl`, `unzip`, `aapt2`, `apksigner`, `d8`, and `aapt` (for `zipalign`).
- For full Qwen builds, allow at least 8 GB of free space for model weights, intermediate APKs and signed artifacts; compile-only does not copy models.

## 1. Prepare licensed libraries

Download/install the official Android SDK Platform 36 with SDK tools and export `ANDROID_HOME`,
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

Legacy model pack paths below are relative to `models/`; optional packs may be absent. Exact names and hashes are in the [model manifest](../assets/model-manifest.json):

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

## 3. Check, validate and build

```bash
bash design-tools/check-all.sh
bash tests/security/secret-check.sh
python3 tests/security/signing_check.py
python3 tests/security/package_check.py
python3 tests/security/build_cli_check.py
python3 tests/security/apk_signing_check.py
bash tests/security/cloud-check.sh
bash build.sh --compile-only
```

`--compile-only` links resources, compiles Java and produces DEX. It creates **no APK** and
makes no recognition/model-readiness claim. `bash build.sh --dev-no-models` creates an explicitly
named development validation APK without recognition weights; it is not a usable offline ASR build.

`bash build.sh` creates `dist/tingxiejian-v<version>-arm64-dev.apk`, with `debuggable=true` and
`-dev` in the generated version name. The source Manifest and package ID are retained. A separate
identity is generated under `build/dev-signing/`; old `build/debug-keystore.p12` identities are never
reused automatically. Keep the paired development key/password private. A new signature cannot
update an installed app with another signature; uninstalling can erase history, so export it first.

Full development builds require the complete core streaming pack. Complete available Paraformer,
punctuation, diarization and Qwen3-ASR packs are optional; partial optional packs are omitted.
The exact inputs and asset names are in `assets/model-manifest.json`. Qwen3-ASR assets use
`models/qwen3-asr-0.6b-int8/` with nested tokenizer files. Known model sizes/hashes are checked,
and only actually included packs are marked bundled in the APK manifest.

Release builds require an explicitly supplied long-term identity **outside the checkout**, including
its password files. Missing inputs fail before compilation. Use private file permissions (`chmod 600`)
and one password per file; never place passwords in command arguments, Git or environment values.

```bash
bash build.sh --release \
  --release-keystore /secure/tingxiejian-release.p12 \
  --release-store-password-file /secure/store.pass \
  --release-key-password-file /secure/key.pass \
  --release-key-alias tingxiejian-release \
  --release-cert-sha256 YOUR_EXPECTED_CERTIFICATE_SHA256
```

The pin accepts 64 hex digits, with optional colons, and is recommended. Equivalent path/alias/pin
inputs are `RELEASE_KEYSTORE`, `RELEASE_STORE_PASSWORD_FILE`, `RELEASE_KEY_PASSWORD_FILE`,
`RELEASE_KEY_ALIAS` and `RELEASE_CERT_SHA256`. Release output is
`dist/tingxiejian-v<version>-arm64-release.apk`; it remains non-debuggable. Release forbids
`--compile-only` and `--dev-no-models` and validates the provenance, hash, source revision and
redistribution status of **every included pack**. Unresolved provenance or rights fail closed;
these checks do not manufacture permission or publisher identity. The final signed certificate is
also compared with the validated identity.

The build verifies the signature, alignment, actual startup DEX definitions, UI/native libraries,
bundled manifest, and STORED model assets. Desktop checks do not establish recognition accuracy,
Keystore behavior on a device or whether an OEM island rendered.

Cloud API keys use Android Keystore AES-GCM in private preferences. Existing plaintext is migrated
only after encryption/authentication succeeds; failures preserve the old key and show a retry hint.
Settings saves a changed key on focus loss, leaving Settings or “Save and test”, rather than after
each character. Clearing removes both the encrypted and legacy values. Saving a configured key
enables automatic transcript text correction during transcription; the Settings disclosure explains
this before use. Faithful transcripts remain available; cloud audio recognition is a separate option.

## Source portability

`scripts/prepare-libraries.sh` and the checks are self-contained. A clean checkout is **not a
self-contained APK build** until its SDK, sherpa AAR and rights-cleared model files are supplied.
No sibling `~/offline-transcriber` directory is needed. Downloaded libraries, model weights,
MiSans, APKs, private backups and signing secrets are ignored by Git. Before enabling public
artifact uploads in CI, use models with verified redistribution rights.

## Qwen3 and the current package

Place the Qwen3 pack under `models/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/`, containing `conv_frontend.onnx`, `encoder.int8.onnx`, `decoder.int8.onnx` and `tokenizer/{vocab.json,merges.txt,tokenizer_config.json}`. See [model records](upgrade/MODELS.md) for provenance, locked revisions, sizes, hashes and limits. This development APK includes 13 model assets and no diarization pack. Packaging selects complete capabilities actually present; production mode additionally verifies provenance.
