# 听写间全面升级实施计划

> 执行方式：本会话持续实施；独立文件域使用 dispatching-parallel-agents，集成与共享 ASR/实时听写由主执行者负责；最终 fresh reviewer 审查整份差异。

**Goal:** 在保护已真机成功的超级岛路径和旧数据的前提下，实现任务包的安全、双入口、共享识别及整理稿升级。
**Architecture:** 保留 Java/Views 和手工构建链。音频输入统一进入 SpeechPipeline；确定性 VAD 划段和 StableTextTracker 驱动即时反馈，独立、有限队列的 finalizer 处理 VAD 语音段并以 streaming 降级。持久 session、模型包、密钥和整理验证各自保持轻量边界。
**Spec:** `task/CODEX_TASK.md` 及 `task/spec/01..10`。

## 约束

- package ID 不变；无 Compose/Kotlin 重写；无自动 push/merge/release。
- 超级岛 payload/channel/ID/timing、Shizuku 与 Manifest 关键声明保持 baseline；firewall 不能在缺少真机证据时猜测重写。
- 无 corpus 时不生成模型排名；无权重时构建如实标记；没有真机结果就不声称真机成功。
- 最终文字不被 AI 覆盖；模型缺失与非核心功能失败均安全降级。

## Milestones

1. [x] Baseline：记录 HEAD/clean tree、原 guards 与 Island checks；文件 hash characterization；无适用 AGENTS.md。
2. [x] Safety：release 显式长期签名、debug 独立签名；Keystore AES-GCM 与非逐字保存；持久 session 与 FGS timeout/resource cleanup。验收：安全行为测试及编译。
3. [x] Models：按能力准备、原子安装/哈希校验；machine-readable provenance；官方候选调查及 CER/实体/延迟/RTF/内存 harness。验收：损坏/替换测试、manifest validator 和 metrics 测试。
4. [x] ASR：纯 Java StableTextTracker/VAD/normalizer/hotwords 行为测试先失败再通过；共享 SpeechPipeline，独立 VAD final boundaries、异步 finalizer 与安全 fallback、diarization memory preflight。验收：pipeline fake recognizer 故障与边界行为测试和完整 Java/DEX 编译。
5. [x] Live/UI：点击时 microphone 权限、前台 AudioRecord、有限队列、生命周期停止/保存、PCM RMS 波形；首页双入口、MotionSpec、独立 haptic、降级。验收：线程/状态行为测试、XML guards、权限 guard、编译。
6. [x] Polish/Data：结构化 segment edits 与保护实体；保留 raw/final/polished，旧 History 与导出兼容；全文页切换和明确主动联网。验收：解析/实体/旧数据行为测试与编译。
7. [x] CI/Review：fast CI 不带大模型；release validation 严格 provenance/signing；现有 checks、所有新行为 tests、compile/DEX、独立 final audit、修复 findings，记录真机和模型 blocker。

## 共享接口与所有权

- 主执行者：Transcriber/SpeechPipeline、DictationController/Activity、MainActivity、Manifest、Motion/Wave、最终 build/CI 集成。
- Security：Cloud.java、SettingsActivity.java、SecureSecretStore、build.sh 和签名测试；不改其他源码。
- Session：LocalService、SessionRepository/SessionState、TingxiejianApp；不触及 notification/publisher 方法体。现有 Transcriber 签名保持兼容。
- Models：ModelManager/ModelPrep/ModelInstaller、assets model lock、benchmark tools；`ModelManager.ensure(Context,String)` 返回 File，ModelPrep 原方法仅准备 Core，optional 按需。
- Polish：History/TranscriptActivity/TranscriptPolisher、activity_transcript.xml；调用现有 `Cloud.chat(Context,String,String)`，不改 Cloud。
- Review/Island：只读受保护路径 characterization、独立审查与官方证据；不改受保护 Java。

## Review focus

进程死亡与旧 RUNNING、取消后迟到 result、录音权限撤销/离开前台、capture/finalizer 过载、模型损坏/缺失、Keystore 不可用与旧明文迁移、恶意/错误 AI JSON、旧 History 导出、长期签名 fail-closed、通知发布时序与全局 firewall 副作用。

## Ledger / Rulings

- 2026-10-01 Baseline HEAD `69085db`，原 check-all 全通过；完整 APK 缺 weights。
- Ruling: 用户已明确要求完整执行且不要停在计划，任务包是获授权规格；不重复添加规格/计划审批。
- Ruling: 在独立 feature branch 原工作区实施，避免复制大模型目录和签名输入；没有外部发布动作。
- Ruling: 优先实现独立确定性 energy VAD，记录其噪声场景局限；不假称已评测 neural VAD。
- Ruling: 先不下载来源未确定的 embed.onnx；构建模式明确区分不带模型的 dev 验证与真实 release。
- User clarification: 本地 ASR 精度升级优先调查 Qwen3-ASR 0.6B；选择需要依据 Android/sherpa 的真实接口与上游模型资料，不能伪称已有本机 CER 优势。文本 LLM 在转写过程中自动逐段轻量校正：有配置 API Key 即执行，无配置跳过并明确提示；不是后续“问 AI”。忠实稿和校正稿分别保存。自动发送的说明须在用户配置云端功能时明确展示。

- Completion: 源码能力及Cloud可执行验证完成，按用户限定做相关轻量检查和API36编译；OEM真机/用户corpus/正式release仍明确阻塞。详见 IMPLEMENTATION.md 与 LIVE-UPDATES.md。
- Supplement: 标准 Live Update 独立1500ms节流，复用Job，min26/target35保留；ColorOS16标准路由，不接私有SDK/Bubble。旧小米notify仅catch增量关闭失败重试，其余受保护路径保持。
