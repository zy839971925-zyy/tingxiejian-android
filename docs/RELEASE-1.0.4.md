<p align="center"><strong>简体中文</strong> · <a href="RELEASE-1.0.4.en.md">English</a></p>

# v1.0.4 · 本地高精度转写与实时进度测试版

安装包版本 **1.0.4-dev**，versionCode **104**；ARM64，Android 8.0+，compileSdk 36 / minSdk 26 / targetSdk 35。GitHub Release 标记为 **pre-release**，开发签名和设备验收边界如下。

## 下载与校验

- [GitHub Release](https://github.com/zy839971925-zyy/tingxiejian-android/releases/tag/v1.0.4)
- APK：`tingxiejian-v1.0.4-arm64-dev.apk`
- 大小：**1486302483 bytes**（约 1.49 GB）
- SHA-256：`a0b75988a4f385b7818e36265a4c8c994cca5d4928863e05d3ddf25af2dfa60c`
- 签名证书 SHA-256：`d27080e3be22f33461a69ce777dbb871436f4a6997ae9162dc8f81c72e4c3839`
- 同名 `.sha256` 文件随 Release 提供，可运行 `sha256sum -c tingxiejian-v1.0.4-arm64-dev.apk.sha256`。

该证书与此前交付的 1.0.3-dev APK 相同，可覆盖更新这个开发签名系列。它与历史正式版签名不同，不保证覆盖安装；**更换签名前先导出历史，不要直接卸载旧版**。默认构建不会使用正式发布身份，本测试版不宣称通过长期签名与全部模型再分发核实的正式发布门槛。

## 本次变化

- 共享本地文件/实时听写流水线：VAD、即时稿、Qwen3-ASR 0.6B int8 / Paraformer 分段高精度复核、标点、受限队列与失败降级。
- API Key 已配置时在转写途中自动轻度校正文段，无 Key 明确跳过；忠实稿与整理稿独立保留。“问 AI”仍是单独功能。
- 前台实时听写支持显式开始、暂停、结束；切后台停止麦克风。完善历史/会话状态、结果持久化与中断诊断。
- Android 16 ProgressStyle / promoted ongoing notification 接入；小米超级岛优先，失败继续标准通道和普通 FGS 通知。ColorOS 16 只走标准 API；Shizuku 不成为全局依赖。
- 原生 UI、安全区、大字体、触控和减少动效修复；结果层搜索、TXT / SRT / JSON 导出映射、播放器异步准备/清理与对话草稿交互修复。
- 根据实际手机反馈：模型选择增加方向提示与当前状态；明确 Qwen 已内置，新增准备进度，导入降为可选；引导内独立配置/授权弹窗；按设备显示超级岛/流体云/原生通知；长按钮不再拉伸成椭圆。
- Android Keystore 保护 API Key 与安全迁移；开发/正式签名分离、模型来源与哈希清单、低成本主机回归检查和 CI。

## 内置模型与限制

本 APK 内置 13 个模型资源，包含 core-streaming、accurate-finalizer、punctuation 和 qwen3-asr-0.6b-int8。Qwen 包 6 个文件共 **987015347 bytes**，无需另外下载；提取到私有目录会占用额外空间。Qwen 原生加载需至少 **3 GiB 当前可用系统内存**，不足时尝试 Paraformer 并提示。已准备文件不等于实际完成高精度推理。

本次不包含分人模型包；缺失时跳过分人并保留文本。流式 Zipformer 的再分发条款仍待核实，历史 `embed.onnx` 来源核实项未解决且不在此 APK。第三方模型不适用项目 MIT，用户确认条款不构成发布者的再分发授权。见[第三方清单](../THIRD_PARTY_NOTICES.zh-CN.md)、[免责声明](../DISCLAIMER.md)及[来源记录](upgrade/MODELS.md)。

## 验证与待测

实际运行了资源/Java/DEX 构建、签名 v2/v3、zipalign、模型打包与启动依赖检查，以及模型安装、通知回退、小米保护、布局绑定、引导、视觉、动效、搜索/导出等主机检查。记录见[实施总结](upgrade/IMPLEMENTATION.md)、[UI 记录](upgrade/ui/README.md)及[反馈修复](upgrade/feedback/README.md)。

本版**未进行 HyperOS / ColorOS 真机验收**，也没有把构造/提交通知称为成功显示超级岛或流体云。Qwen 准确率、设备内存/速度与完整听写体验需使用实际设备和音频验证。

短清单：模型切换/准备并确认实际复核；引导弹窗授权允许/拒绝/未启动后仍留在引导；HyperOS 原超级岛及停止/连续任务清理；ColorOS 16 标准实时活动开/关与普通通知回退，14/15 仅普通通知。
