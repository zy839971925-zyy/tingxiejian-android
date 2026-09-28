<p align="center"><strong>简体中文</strong> · <a href="THIRD_PARTY_NOTICES.md">English</a></p>

# 第三方组件与许可

根目录的 [`LICENSE`](LICENSE) 只覆盖项目的**原创源码和文档**，不重新授权上游代码、
模型权重、依赖库、Android SDK 文件或字体。构建、分发 APK 时须自行阅读上游许可。
源码仓库有意不纳入下表所列二进制资产。

| 组件 | 来源／上游声明的条款 | 使用与分发提醒 |
| :--- | :--- | :--- |
| Xiaomi-SuperIsland-Playground | [源码](https://github.com/HuberHaYu/Xiaomi-SuperIsland-Playground)，Apache-2.0 | `XiaomiIsland*` 和 `XiaomiXmsfValidationGate` 的载荷及能力／门禁实现参考了该项目；改编部分保留 Apache-2.0 义务，见 [`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt)。 |
| sherpa-onnx、Android AAR 与原生运行库 | [源码](https://github.com/k2-fsa/sherpa-onnx)，Apache-2.0 | 识别与分人引擎；从上游获取二进制，**不能把引擎许可当成模型许可**。 |
| AndroidHiddenApiBypass 6.1 | [源码](https://github.com/LSPosed/AndroidHiddenApiBypass)，Apache-2.0 | 仅用于可选能力探测／Binder 访问；**不会安装 LSPosed**。 |
| Shizuku API 13.1.5（api、provider、aidl、shared） | [源码](https://github.com/RikkaApps/Shizuku-API)，MIT，© 2021 RikkaW | 可选特权岛实验；二进制分发需保留版权和许可文本。 |
| Kotlin 标准库 | [源码](https://github.com/JetBrains/kotlin)，Apache-2.0 | sherpa-onnx 的运行时依赖。 |
| Android SDK Platform 与 `org.json` 桌面测试 JAR | Android SDK [条款](https://developer.android.com/studio/terms)；[JSON-java](https://github.com/stleary/JSON-java) 许可 | 构建／测试输入，并非项目原创；勿提交 SDK 或下载的 JAR。 |
| MiSans 字体（私有构建中的 `res/font/misans.ttf`） | [小米字体许可](https://hyperos.mi.com/font/en/download/)，**不是 MIT/OFL** | 先前在本机分发的 APK 中出现过；公开源码改用 Android 系统字体，不包含该文件。不得将 TTF 作为开源资源发布或重新授权。 |
| 中文流式 Zipformer（`2025-06-30`） | [转换模型](https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30)、[需要接受条件的上游模型](https://huggingface.co/yuekai/icefall-asr-multi-zh-hans-zipformer-large) | 转换产物没有明确许可声明，上游要求接受条件。Release APK 为离线使用打包；首次转写要求用户确认第三方条款。**项目不授予模型再分发权**。 |
| Paraformer 中文模型 | [模型页](https://huggingface.co/csukuangfj/sherpa-onnx-paraformer-zh-2023-09-14)，上游声明 Apache-2.0 | 本地 APK 随包提供；保留上游声明。 |
| CT-Transformer 标点模型 | [sherpa 文档](https://github.com/k2-fsa/sherpa/blob/master/docs/source/onnx/punctuation/pretrained_models.rst)，上游 ModelScope 声明 Apache-2.0 | 保留署名并核实转换产物条款。 |
| pyannote segmentation 3.0 | [模型页](https://huggingface.co/pyannote/segmentation-3.0)，上游声明 MIT | 可能有受限下载条件；核实账号权限并保留许可文本。 |
| 声纹嵌入模型 `embed.onnx` | **确切来源和条款未核实** | 仅打包在 Release APK 中以支持离线分人，不在源码仓库；首次使用要求确认条款。本项目不主张其权利。 |

Shizuku API 的 MIT 许可（© 2021 RikkaW）全文收录在
[`licenses/Shizuku-API-MIT.txt`](licenses/Shizuku-API-MIT.txt) 并随本地构建的 APK 打包；
[`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt) 亦同。
曾在研究中参考 GPL 项目 `IslandRecorder`，**未包含其 GPL 代码**。

## 小米专属能力的限制

提交岛载荷、取得权限或通过 Shizuku 读回网络规则**不等于**获得小米平台授权。
可选的 XMSF 防火墙操作可能影响其他应用；服务或进程中断后只能尽力恢复。
离线转写不依赖 Shizuku。

## APK 分发边界

根目录 MIT 许可只涵盖原创源码与文档；Release **APK 不整体适用 MIT**，
它包含独立许可的依赖和权重。

APK 预先打包整个流水线所需模型，因而无网络时也能运行离线功能。
应用以 [`Consent.java`](src/com/example/tingxiejian/Consent.java) 和
[`res/values/strings.xml`](res/values/strings.xml) 的**阅读并确认**对话框约束首次转写：
列出第三方组件及其条款，用户须主动勾选“个人使用”后才能开始。
APK 还包含 [`licenses/DISCLAIMER.txt`](licenses/DISCLAIMER.txt)（见 [`DISCLAIMER.md`](DISCLAIMER.md)），声明：

- `Streaming Chinese Zipformer` 和 `embed.onnx` 的**再分发条款未核实**，按原样随包提供供接收者个人使用及技术评估；
- **不授予再分发权**，权利人有异议可请求移除；
- 不保证应用不崩溃或转写准确等。

> [!CAUTION]
> 这些声明和用户的使用确认**不能代替发布者取得合法再分发许可**；公开发布仍有未解决的权利风险。

不要将模型权重或字体提交到*源码*仓库，也不要称 APK 或模型包为“MIT”。
APK 内第三方组件仍保有上表所述原权利人的版权。
