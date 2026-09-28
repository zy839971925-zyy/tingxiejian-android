<p align="center"><a href="DISCLAIMER.md">简体中文</a> · <strong>English</strong></p>

# Disclaimer

> The APK also includes [`licenses/DISCLAIMER.txt`](licenses/DISCLAIMER.txt).
> Read the [third-party notices](THIRD_PARTY_NOTICES.md) alongside this document.

## 1. Scope of the license

The root [`LICENSE`](LICENSE) (MIT) covers **only this project's original source code and
written documentation**. It neither grants nor changes rights to any third-party library,
Android SDK material, model weights or font. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
for the component list and links to upstream terms.

## 2. The APK is not a wholly MIT-licensed artifact

The GitHub Release APK is a **binary bundle** containing sherpa-onnx, Kotlin,
HiddenApiBypass, Shizuku API and other third-party runtimes and model weights.
The APK as a whole **is not distributed under the project's MIT license**.
Each component keeps its original copyright and applicable terms.

## 3. Known status of model weights

| Asset | Known upstream statement | Note |
| :--- | :--- | :--- |
| Chinese Paraformer | Apache-2.0 declared upstream / ModelScope | Bundled; retain upstream notices |
| CT-Transformer punctuation | Upstream ModelScope Apache-2.0 | Converted asset; review conversion terms |
| pyannote segmentation 3.0 | MIT (CNRS) | A LICENSE is present upstream; gated access may apply |
| Streaming Chinese Zipformer (2025-06-30) | **Redistribution terms unclear** | Converted from icefall multi_zh-hans; upstream requires acceptance of conditions |
| Speaker embedding (`embed.onnx`) | **Provenance and terms unverified** | No rights are claimed for an unidentified source |

The weights with unclear or unverified terms are included in the APK **AS IS** for a recipient's
personal use and technical evaluation; **this statement does not grant any redistribution rights**.
All models are bundled in the Release APK—there is **no model download** on first launch.
Before first transcription, the app shows a read-and-confirm dialog listing components and terms,
with an explicit checkbox. **User acceptance and this disclaimer cannot replace the publisher
obtaining valid redistribution permission.** If you are a rights holder with concerns, contact us
via Issues so the affected asset can be reviewed and removed.

## 4. No warranty

To the fullest extent permitted by applicable law, the software is provided **“AS IS”**, without
express or implied warranties, including merchantability, fitness for a particular purpose or
non-infringement. The authors are not liable for direct or indirect loss, including **lost audio
or data, incorrect transcripts, app crashes, device issues, battery use or heat**.

Back up important recordings yourself. Export transcripts before updating. Operation is not
guaranteed on every device, HyperOS/MIUI version or audio codec.

## 5. Boundaries of optional features

- **Cloud transcription:** requires a user-configured endpoint and key. Once enabled, audio is
  sent to that third-party service; review its policies before use. Chat is a separately initiated action.
- **Shizuku island:** entirely optional, not required for local recognition. OEM/device support
  varies. These operations **do not constitute official Xiaomi/HyperOS authorization**. If the
  shared OEM firewall chain was originally off, the app falls back to ordinary notifications
  rather than activating global rules that could affect other apps. Other XMSF rule operations
  may still affect connectivity; restoration is best-effort after interruption.
- **Diagnostics and logs:** private to the app by default; not automatically published to shared storage.

## 6. Contact

For bugs, questions and rights objections, use
[GitHub Issues](https://github.com/zy839971925-zyy/tingxiejian-android/issues).
**Do not include private recordings, keys or unredacted diagnostics in a public Issue.**
