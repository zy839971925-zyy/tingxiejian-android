<p align="center"><strong>简体中文</strong> · <a href="BUILDING.en.md">English</a></p>

# 从源码构建

> [!CAUTION]
> 这里是**源码仓库**，不提供第三方模型权重。下载、使用、打包或分发模型与字体前，
> 必须核实各自许可和受限访问条件。流式模型的再分发条款与 `embed.onnx` 的来源尚未核实。
> 本地构建或公开发布的 APK **不能整体标为 MIT**；用户同意和免责声明均不能替代上游再分发许可。
> 见[发布权利说明](RELEASE-1.0.4.md)与[第三方清单](../THIRD_PARTY_NOTICES.zh-CN.md)。

## 构建环境

- Android ARM64 + Termux 或 Linux Cloud；Android SDK Platform **36**（compile `android.jar`），minSdk 26、targetSdk 35 保留。
- JDK 21（`--release 8` 编译 Java）、Python 3、`curl`、`unzip`、`zipalign`、
  `apksigner`、`aapt2`、`d8`。Termux 软件包包括 `openjdk-21`、`python`、`curl`、
  `unzip`、`aapt2`、`apksigner`、`d8`，以及提供 `zipalign` 的 `aapt`。
- 完整 Qwen 构建建议至少 **8 GB** 可用空间，以容纳权重、中间 APK 和签名产物；仅编译不复制模型。

## 1. 准备有权使用的依赖库

从官方 Android SDK 工具安装 Platform 36 并设置 `ANDROID_HOME`，或将 API 36 的 `android.jar`
放在 `vendor/android.jar`。从 [sherpa-onnx 上游](https://github.com/k2-fsa/sherpa-onnx)
取得 Android AAR **1.13.8**，放在 `vendor/sherpa-onnx-1.13.8.aar`；核对版本及其许可。

依赖准备脚本从 Maven Central 获取 Shizuku 13.1.5 模块（包括必需的 `aidl`、`shared`）、
HiddenApiBypass 6.1、Kotlin 1.7.20 和**仅供桌面测试**的 JSON 20240303 JAR，
对照 `scripts/maven-sha256.txt` 校验下载及缓存文件，再从提供的 AAR 解出
`vendor/classes.jar`。`build.sh` 复核哈希并检查编译 JAR 与其 AAR 是否一致。
**人工提供的 SDK、sherpa AAR 与模型不受 Maven 清单来源验证**，须另行核实。

```bash
bash scripts/prepare-libraries.sh
```

`vendor/` 不纳入 Git。分发二进制前先阅读[第三方清单](../THIRD_PARTY_NOTICES.zh-CN.md)。
开源主题不依赖 `res/font/misans.ttf`，而使用 Android 系统无衬线字体；
请勿将另行取得的 MiSans TTF 提交到仓库。

## 2. 在本机提供模型

模型目录在 `models/` 下；下列是传统包路径（相对 `models/`，可选包允许缺失）。精确文件名/哈希以 [model-manifest.json](../assets/model-manifest.json) 为准：

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

这是**构建输入清单**，不是再分发许可。请从
[第三方清单](../THIRD_PARTY_NOTICES.zh-CN.md)核对来源，必要时取得受限访问资格。
流式模型转换产物没有明确模型许可，项目所用 embedding 文件的确切来源未核实。
若不能取得相应权利，**构建、公开发布、用户确认或免责声明都不会创造这种权利**；
公开分发存在未解决的上游权利风险。替换为获得许可的兼容模型还须验证张量形状和转写行为，
不能只改文件名。

## 3. 检查、验证与构建

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

`--compile-only` 只验证资源链接、Java 编译与 DEX，**不生成 APK**，也不表示模型已就绪。
`bash build.sh --dev-no-models` 生成明确命名的开发验证 APK，不含识别权重，不能作为可用的离线转写包。

默认 `bash build.sh` 输出 `dist/tingxiejian-v<version>-arm64-dev.apk`；生成副本中
`debuggable=true`、版本名称带 `-dev`，源码 Manifest 与 package ID 保留。
独立开发身份在 `build/dev-signing/` 创建；不会自动复用旧 `build/debug-keystore.p12`。
请保管配套 key/password。不同签名不能直接更新同一安装；卸载可能删除历史，换签名前先导出。

完整开发包只要求完整 Core Streaming；已有的完整 Paraformer、标点、分人、Qwen3-ASR 包按需打包，
不完整的可选包会省略。精确路径与 asset 名见 `assets/model-manifest.json`；Qwen3-ASR 使用
`models/qwen3-asr-0.6b-int8/`，包括 tokenizer 子目录。已知 size/hash 会验证；APK 清单只将实际打包条目标为 bundled。

Release 必须显式提供**仓库外**的长期签名身份与密码文件；缺失时在编译前拒绝。
文件权限须私有（`chmod 600`），每个密码文件仅一行密码；不要把密码值放入命令参数、Git 或环境变量。

```bash
bash build.sh --release \
  --release-keystore /secure/tingxiejian-release.p12 \
  --release-store-password-file /secure/store.pass \
  --release-key-password-file /secure/key.pass \
  --release-key-alias tingxiejian-release \
  --release-cert-sha256 YOUR_EXPECTED_CERTIFICATE_SHA256
```

建议固定 certificate SHA-256；支持 64 个十六进制字符，可带冒号。
也可用 `RELEASE_KEYSTORE`、`RELEASE_STORE_PASSWORD_FILE`、`RELEASE_KEY_PASSWORD_FILE`、
`RELEASE_KEY_ALIAS`、`RELEASE_CERT_SHA256` 提供相同的路径/alias/pin。
Release 输出 `dist/tingxiejian-v<version>-arm64-release.apk`，保持不可调试，禁止组合
`--compile-only` 或 `--dev-no-models`。每个**实际打包模型包**都必须通过来源、revision、hash、
再分发状态检查；来源或权利未解决时拒绝构建。验证不能创造许可或官方签名身份。
签名后还会复核实际证书与已验证身份一致。

构建检查签名、对齐、真实 DEX 启动类、UI/native 库、打包清单及 STORED 模型 assets。
桌面测试不能证明实机识别准确、实机 Keystore 可用或 OEM 超级岛显示。

云端 API Key 通过 Android Keystore AES-GCM 存于私有 preferences；旧明文仅在加密并认证成功后删除，
失败保留原值并显示重试提示。改变 Key 后，在离开输入框、离开设置页或“保存并测试”时保存，
不会每输入一字就持久化。清空同时删除加密和旧值。保存配置后转写过程中会自动分段发送文本轻度校正，
设置页明确说明；忠实稿保留。云端音频识别为另一个独立选项。

## 源码可移植性

`scripts/prepare-libraries.sh` 与检查脚本自身可运行，但干净克隆**不是可直接构建的完整 APK 工程**：
仍需提供 SDK、sherpa AAR 和有权使用的模型。无需相邻的 `~/offline-transcriber` 目录。
下载的库、模型、MiSans、APK、私有备份与签名机密均受 Git 忽略规则保护。
若将来启用公开 CI 产物上传，应先换用再分发权利已核实的模型。

## Qwen3 与当前安装包

Qwen3 包放在 `models/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/`，包含 `conv_frontend.onnx`、`encoder.int8.onnx`、`decoder.int8.onnx` 及 `tokenizer/{vocab.json,merges.txt,tokenizer_config.json}`。来源、锁定版本、文件大小、SHA-256 与使用边界见 [模型记录](upgrade/MODELS.md)。本次开发 APK 包含 13 个模型资源，未包含分人包；构建自动选择实际存在的完整能力包，正式模式同时核实来源。
