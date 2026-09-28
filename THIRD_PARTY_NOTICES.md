<p align="center"><a href="THIRD_PARTY_NOTICES.zh-CN.md">简体中文</a> · <strong>English</strong></p>

# Third-party components and licensing

The root [`LICENSE`](LICENSE) covers this project's **original code and documentation**. It does
not relicense upstream code, model weights, libraries, Android SDK files or fonts. These notices
are relevant when building or distributing an APK; review the upstream licenses yourself before
redistribution. The source repository deliberately omits the binaries listed below.

| Component | Upstream / declared terms | Use / distribution note |
| --- | --- | --- |
| Xiaomi-SuperIsland-Playground | [source](https://github.com/HuberHaYu/Xiaomi-SuperIsland-Playground), Apache-2.0 | Payload structure and capability/gate implementation informed the `XiaomiIsland*` and `XiaomiXmsfValidationGate` adapters. Adapted portions retain Apache-2.0 obligations; see [`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt). |
| sherpa-onnx, Android AAR and native runtime | [source](https://github.com/k2-fsa/sherpa-onnx), Apache-2.0 | ASR/diarization runtime. Obtain binaries from upstream; do not infer model licenses from runtime license. |
| AndroidHiddenApiBypass 6.1 | [source](https://github.com/LSPosed/AndroidHiddenApiBypass), Apache-2.0 | Optional capability detection / Binder access; this does **not** install LSPosed. |
| Shizuku API 13.1.5 (api, provider, aidl, shared) | [source](https://github.com/RikkaApps/Shizuku-API), MIT, © 2021 RikkaW | Used for optional privileged island experiments. Keep its copyright and permission notice in binary distributions. |
| Kotlin standard library | [source](https://github.com/JetBrains/kotlin), Apache-2.0 | Runtime dependency of sherpa-onnx. |
| Android SDK platform and `org.json` desktop test JAR | Android SDK [terms](https://developer.android.com/studio/terms); [JSON-java](https://github.com/stleary/JSON-java) license | Build/test inputs, not project-authored files; do not commit SDK or downloaded JARs. |
| MiSans font (`res/font/misans.ttf` in the private build) | [Xiaomi's license](https://hyperos.mi.com/font/en/download/), **not MIT/OFL** | The previously distributed local APK included it. The public source substitutes the Android system font and excludes the font file. Do not publish/relicense this TTF as a source asset. |
| Streaming Chinese Zipformer (`2025-06-30`) | [converted model](https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30), [upstream gated model](https://huggingface.co/yuekai/icefall-asr-multi-zh-hans-zipformer-large) | The conversion has no clear license statement; upstream requires acceptance of conditions. Bundled in the release APK only so offline users need no download; every user must accept the in-app third-party terms first. Redistribution rights are **not** granted by this project. |
| Paraformer Chinese model | [model page](https://huggingface.co/csukuangfj/sherpa-onnx-paraformer-zh-2023-09-14), Apache-2.0 as declared upstream | Bundled model in local APK; follow upstream notices. |
| CT-Transformer punctuation model | [sherpa documentation](https://github.com/k2-fsa/sherpa/blob/master/docs/source/onnx/punctuation/pretrained_models.rst), upstream ModelScope Apache-2.0 | Preserve model attribution and verify exact conversion terms. |
| pyannote segmentation 3.0 | [model page](https://huggingface.co/pyannote/segmentation-3.0), MIT as declared upstream | Gated/download conditions may apply; verify permissions for your account and retain notices. |
| Speaker embedding (`embed.onnx`) | Exact origin/terms **not verified** | Bundled only in the release APK (never in source) so the diarization feature works offline; users accept the in-app terms first. No rights are claimed by this project. |

Shizuku's API is MIT licensed (© 2021 RikkaW). Its full copyright, permission and warranty
text is included in [`licenses/Shizuku-API-MIT.txt`](licenses/Shizuku-API-MIT.txt) and bundled
in locally built APKs, as is [`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt).
The `IslandRecorder` GPL project was consulted during research but **no GPL code was included**.

## Xiaomi-specific caveat

Island payload submission, permissions and Shizuku network-rule readback do not constitute Xiaomi
platform authorization. Optional XMSF firewall operations can affect other apps; recovery is
best-effort if the service/process dies. The offline transcription path does not depend on Shizuku.

## APK distribution policy

The root MIT license covers original source and documentation only. A released **APK is not an
MIT artifact**: it bundles independently licensed libraries and model weights.

The APK ships **every model the pipeline needs pre-placed**, so users without a suitable network
still get a working offline app. In exchange, the app gates the first transcription behind a
**read-and-confirm** dialog (`Consent.java` + `res/values/strings.xml`) that lists every bundled
third-party component and its terms; the user must tick an explicit "personal use" checkbox
before transcription can start. The APK also carries
[`licenses/DISCLAIMER.txt`](licenses/DISCLAIMER.txt) (see [`DISCLAIMER.md`](DISCLAIMER.md)), which:

- states that some bundled weights (`Streaming Chinese Zipformer`, `embed.onnx`) have
  **unverified redistribution terms** and are provided AS IS for the recipient's own use and
  technical evaluation;
- grants **no redistribution rights** and offers removal on a rights holder's request;
- disclaims warranties, including crash-free operation or correct transcripts.

> [!CAUTION]
> These notices and user acceptance **cannot substitute for the publisher obtaining lawful
> redistribution permission**; public distribution still carries unresolved rights risk.

Do not attach model weights or the font to the *source* repository, and do not label any APK or
model bundle "MIT". Third-party components inside the APK keep their owners' copyrights as
listed above.
