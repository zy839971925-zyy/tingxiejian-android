# Signing and cloud-secret implementation record

Status: host behavior checks and development APK verification passed on 2026-10-01. Android Keystore/device behavior is not yet device-tested. No release has been created or published.

## Cloud credentials

`SecureSecretStore` uses a non-exportable Android Keystore AES-256 key (`com.example.tingxiejian.cloud.api.v1`) and AES-GCM with a random 96-bit nonce, 128-bit authentication tag and package/schema-bound associated data. The private `cloud` preferences contain only the versioned ciphertext (`apiKey.aesgcm.v1`) after successful migration. Provider, URL and model names remain ordinary non-secret preferences.

`SecretMigration` is an Android-independent durable-replacement policy. It encrypts **and authenticates the new envelope before** committing ciphertext and deleting legacy `apiKey` in one preferences transaction. Encryption/decryption/commit failure preserves the legacy value and reports a visible storage error; it never persists a newly entered key as plaintext. A failed legacy migration can still use its existing key in memory, so the failure does not destroy the user's configured credential. Existing unreadable ciphertext fails closed and never resurrects a possibly stale legacy key. Explicit clearing removes both ciphertext and legacy entries, including unreadable ciphertext.

Android `SharedPreferences.commit()` updates its in-memory map even when the disk write fails. The adapter therefore snapshots the previous secret entries and restores the in-memory values after a failed commit. A failure is returned to the UI rather than claiming the change was saved. Actual disk/Keystore fault behavior still needs device verification.

Settings excludes the key field from saved view state and autofill. The key TextWatcher only marks an in-memory edit as dirty; persistence happens on focus loss, leaving Settings, selecting a preset with a changed key, or the explicit “保存并测试连接” action. Unchanged secrets are not rewritten. Metadata edits have a separate 500 ms debounce. An untouched empty field after a failed decrypt cannot silently clear the stored credential. Explicit clearing also turns off optional cloud ASR.

Configured text AI runs automatically during transcription, as the user requested. Settings states that transcript text will be sent in segments to the configured endpoint, faithful text is retained, and audio is sent only when cloud ASR is separately enabled. No key means text correction is skipped with a prompt. Requests capture the endpoint and bearer token together under the same lock as settings writes, preventing a provider change from sending the new provider's key to an old endpoint. A token echoed by an untrusted response is redacted before it reaches UI/errors/transcript consumers. Clearing stops subsequently created requests; a previously captured/in-flight request can still finish.

Stable consumer APIs: existing `Cloud.chat(...)`, `Cloud.asr(...)`, `Cloud.apiKey(Context)`, `Cloud.configured(Context)`, and `Cloud.hasAsr(Context)` remain usable. `Cloud.save(...)` now returns success; added `saveMetadata(...)`, `clear(Context)` and `secretStatus(Context)` support safe Settings feedback.

## Build identities and packaging

Default `bash build.sh` is a development build. Only its generated Manifest copy has `debuggable=true` and a `-dev` version-name suffix. Package/application ID, source Manifest, protected Xiaomi/Shizuku metadata and existing declarations are preserved by this change. Development outputs use `-arm64-dev.apk` and a newly generated independent `build/dev-signing/` identity; the old `build/debug-keystore.p12` is never reused automatically.

`--release` requires explicit external keystore, alias, store-password-file and key-password-file inputs. All signing files must resolve outside the checkout, be nonempty regular files and have private permissions. Password files contain one nonempty line. Missing/invalid inputs fail before compilation. Password values never appear in command arguments or tool output. Optional `--release-cert-sha256` accepts case-insensitive hex with optional colons; mismatches reject the identity. After signing, the APK's actual certificate is compared with the validated certificate, closing an input-file-change race.

A real build exposed that apksigner caches password-file streams: passing the same file twice reaches EOF for the key password. `scripts/sign-apk.sh` uses apksigner's already-read store-password fallback when both paths refer to the same file; a real signed fixture and certificate comparison cover this case.

Release packaging runs `scripts/validate-model-provenance.py --release --pack ...` for **every actual included pack**. Unknown license/source/revision/hash/redistribution fails closed. Core streaming redistribution remains unresolved, so a currently full release must not succeed merely because the weights are present. No release signing identity was supplied in this session, and no production release APK is claimed.

Development packaging requires the full core-streaming pack and includes only complete optional Paraformer/punctuation/diarization/Qwen packs. Known size/hash mismatches are rejected, model source/asset paths cannot escape their roots, Qwen tokenizer subdirectories are preserved, and each APK's bundled flags describe its actual assets. Model assets remain ZIP_STORED for `AssetManager.openFd`. No partial optional pack is represented as ready.

`--compile-only` validates resources/Java/DEX and creates no APK. `--dev-no-models` produces a named development validation APK with `assets/DEV_NO_MODELS.txt` and zero model assets; it does not claim usable ASR. Neither mode is accepted for release. A portable `build/.build-lock` directory and PID prevent two builds from deleting or replacing one another's intermediate APKs; normal exits clean it up. A killed build may leave a stale lock that must be checked before removing it.

## Evidence

Meaningful red results were observed before each policy implementation:

- Legacy migration: `migration removes plaintext only after encryption` failed against the unimplemented policy.
- External signing inputs: missing-input rejection failed against the permissive policy stub.
- Pack selection: missing-core rejection failed against the permissive selector stub.
- Actual apksigner: same-file passwords reproduced `end of file reached` with a generated keystore and a real compiled fixture APK.
- HTTP provider switch: the old endpoint received the new provider's key before request snapshots were added.
- HTTP error: a provider-echoed bearer token reached the error hint before redaction.

Fresh checks after implementation:

| Command | Actual result |
| --- | --- |
| `bash tests/security/secret-check.sh` | 12 checks passed; real AES-GCM round trip/tampering, legacy migration, durable failures, unchanged key, explicit clearing |
| `bash tests/security/cloud-check.sh` | 6 checks passed; real loopback HTTP: endpoint/token snapshot, token redaction, empty-key zero requests, interrupt disconnect, total deadline against trickle, invalid-header key rejection |
| `python3 tests/security/signing_check.py` | 11 tests passed; generated real keytool identity, external paths/symlinks, private permissions, empty/wrong passwords, alias and pin checks |
| `python3 tests/security/build_cli_check.py` | 8 tests passed; fail-closed release flags and isolated concurrent-build output preservation |
| `python3 tests/security/apk_signing_check.py` | 1 integration test passed; real compiled APK signed and verified, certificate equals exported keystore certificate, source password unchanged |
| `python3 tests/security/package_check.py` | 6 tests passed; missing core, complete/partial optional pack, hash/size corruption and escaped model paths |
| `python3 design-tools/check-cloud.py` | Existing cloud isolation guards passed |
| `bash build.sh --compile-only` | Resource linking + all-current Java compilation + DEX passed before later shared UI integration |
| `bash build.sh --dev-no-models` | Fresh resource/Java/DEX + v2/v3 signature + alignment + ZIP + 8 actual DEX startup definitions passed after Cloud/security changes |
| `aapt2 dump xmltree --file AndroidManifest.xml dist/tingxiejian-v1.0.3-arm64-dev-no-models.apk` | Actual APK showed package `com.example.tingxiejian`, `1.0.3-dev`, `debuggable=true`, preserved Shizuku declarations and `allowBackup=false` |
| `git diff --check` | Passed at security handoff |

The current validation APK is about 13 MB and uses development certificate SHA-256 `d27080e3be22f33461a69ce777dbb871436f4a6997ae9162dc8f81c72e4c3839`. This is a local development identity, not a publisher certificate.

A mid-edit integrated build initially encountered 21 missing LocalService helper symbols; the lifecycle owner completed them, and subsequent full builds passed. An intermediate `check-all.sh` run passed cloud/island/docs and all other checks except an old first-run guard that hardcoded the former release filename. The root integrator owns that guard update and final full-suite evidence; this document does not silently count that earlier suite as passing.

## Remaining device checks

- Existing plaintext-key migration on Android 26 and a target-35 device; inspect private preferences before/after without publishing the key.
- Locked/unavailable/invalidated Keystore and disk-write failure: key remains available/preserved, UI shows a useful hint, untouched Settings never clears unreadable ciphertext.
- Focus loss, backgrounding, process death and clear behavior; no key in the Activity saved-state Bundle/autofill, no per-character disk writes.
- Configured automatic text correction, no-key prompt/fallback and provider switch while a request is active; faithful text is retained and in-flight completion does not revive a cancelled session.
- Updating an existing installation using its deliberately supplied long-term certificate, with history retained; no differently signed development install is claimed to update it.

No encryption/hardware-backed Keystore guarantee, accuracy benchmark, production signature or new real-device Island result is inferred from these host checks.
