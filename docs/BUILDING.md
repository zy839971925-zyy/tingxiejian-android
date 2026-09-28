<p align="center"><strong>简体中文</strong> · <a href="BUILDING.en.md">English</a></p>

# 从源码构建

> [!CAUTION]
> 这里是**源码仓库**，不提供第三方模型权重。下载、使用、打包或分发模型与字体前，
> 必须核实各自许可和受限访问条件。流式模型的再分发条款与 `embed.onnx` 的来源尚未核实。
> 本地构建或公开发布的 APK **不能整体标为 MIT**；用户同意和免责声明均不能替代上游再分发许可。
> 见[发布权利说明](RELEASE-1.0.3.md)与[第三方清单](../THIRD_PARTY_NOTICES.zh-CN.md)。

## 构建环境

- Android ARM64 + Termux；Android SDK Platform **35**（`android.jar`）。
- JDK 21（`--release 8` 编译 Java）、Python 3、`curl`、`unzip`、`zipalign`、
  `apksigner`、`aapt2`、`d8`。Termux 软件包包括 `openjdk-21`、`python`、`curl`、
  `unzip`、`aapt2`、`apksigner`、`d8`，以及提供 `zipalign` 的 `aapt`。
- 至少 **2 GB** 可用空间，以容纳中间 APK、解包模型及签名后的产物。

## 1. 准备有权使用的依赖库

从官方 Android SDK 工具安装 Platform 35 并设置 `ANDROID_HOME`，或将 `android.jar`
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

构建脚本恰好读取 `models/` 下这些文件（相对仓库根目录）：

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

## 3. 检查与构建

```bash
bash design-tools/check-all.sh
bash build.sh
```

输出 `dist/tingxiejian-v1.0.3-arm64-release.apk`（ARM64，目标 SDK 35）。
脚本首次运行会在 `build/` 下创建**本机签名身份**；请妥善保管。
APK 不可调试，不意味着获得小米／应用商店官方签名。新密钥签出的 APK 无法直接覆盖
另一密钥签出的安装包；卸载旧版可能删除本地历史，换签名前先导出重要记录。

检查覆盖纯 Java 逻辑、导出格式、View 连线、视觉／转场结构、云端隔离、Shizuku 回退、
6 种岛载荷情况、APK 类／签名／ZIP，以及 9 个 STORED 模型条目。
**这些检查不能证明**实机识别准确或 OEM 超级岛确实显示。

## 源码可移植性

`scripts/prepare-libraries.sh` 与检查脚本自身可运行，但干净克隆**不是可直接构建的完整 APK 工程**：
仍需提供 SDK、sherpa AAR 和有权使用的模型。无需相邻的 `~/offline-transcriber` 目录。
下载的库、模型、MiSans、APK、私有备份与签名机密均受 Git 忽略规则保护。
若将来启用公开 CI 产物上传，应先换用再分发权利已核实的模型。
