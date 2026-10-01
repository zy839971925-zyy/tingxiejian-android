# 升级实施与验证记录

2026-10-01。基线 `69085db345c6e317e0de6e9d28e9720e6b4190ed`，分支 `codex/comprehensive-upgrade`。继续已有升级并补充通知能力，没有从头重写；入口 [CODEX_TASK.md](task/CODEX_TASK.md)。无适用 AGENTS.md。

## 完成的工程

| 范围 | 实际实现 | 主要模块 |
|---|---|---|
| ASR | 文件/麦克风共用 core；独立 RMS VAD pre/post-roll、stable prefix、稳定段 ID；低能量但已有即时文字时按≤25秒区间复核兜底，避免EOF空结果；流式反馈 + 有界异步复核；失败保留即时稿 | RecognitionPipeline、SpeechPipeline、Transcriber |
| Qwen | 官方0.6B int8六文件已下载校验；128维、16kHz、2线程、512 context、256 new tokens；可用RAM<3GiB时降级Paraformer/streaming；支持SAF原子导入 | ModelManager、ModelInstaller、SafModelImporter |
| AI校正 | 已配置Key/接口时在转写途中自动逐段请求；无Key明确跳过；严格JSON/原文/ID/实体/内容保护；raw/final/polished分层；Ask AI独立 | TranscriptPolisher、PolishValidator、SegmentPolishQueue、CloudTranscript |
| 实时听写 | 显式点击才请求麦克风，前台AudioRecord、暂停/停止、真实RMS、有界FIFO；末帧/慢构造stop/旧句复核清除新句的问题已修；结果入History | MicrophoneAudioSource、DictationController、DictationActivity |
| 数据/导出 | 忠实稿/整理稿切换；旧History非破坏读取；所选层复制/分享/TXT/SRT/JSON；选择器重建恢复格式和层；原子同ID更新 | History、TranscriptActivity、Exporter |
| 生命周期 | 持久状态、重启恢复INTERRUPTED、Android15 timeout及时stopSelf、取消后禁止迟到成功 | SessionState/Ledger/Repository、LocalService、TingxiejianApp |
| 安全/构建 | Keystore AES-GCM和安全迁移；非逐字保存Key；URL/token原子快照；HTTP总deadline和主动中断；独立dev身份、release外部长期签名及provenance fail-closed | SecureSecretStore、SecretMigration、Cloud、build.sh、scripts |
| UI | 保留Views/Portal；RMS波形；文本tint只作用于变化的语句；减少动效与触觉独立 | Motion、MotionSpec、DictationActivity |
| 状态通知 | 原小米 → API36标准Live Update → 同ID普通FGS；独立状态、节流、开关、诊断；不接私有OPPO API/Bubble | AndroidLiveUpdateCapability/Publisher、NotificationUpdateGate |
| 结构/CI | 将CloudChunker机械移到CloudFileTranscriber，LocalService减少约114行；fast CI无需大模型 | CloudFileTranscriber、.github/workflows/fast-ci.yml |

标准通知用于现有文件转写FGS；实时听写仍由可见Activity持有，离开结束采集，不新增麦克风FGS。

## 取舍与未验证项

- Qwen是offline分段finalizer，Zipformer继续即时增量。官方公共样本桌面smoke之前已完成，本次通知补充没有重跑推理。没有用户corpus或Android CER/latency/RTF/PSS实测，不宣称精度排名。见 [MODELS.md](MODELS.md) 与 [benchmark说明](../../benchmark/README.md)。
- RMS VAD是energy分段；ITN采用保守宽度/空格规范化，不猜数字。噪声、耳语、快语/长句边界和token截断待真实语料。
- 校正允许集目前较保守：标点/空白与独立口癖；多数改词/语序重排会被拒绝，规则保护不是完整语义证明。忠实稿不可改写。
- 校正worker1、pending4；队满跳过部分段并提示。请求20秒总deadline；结束额外最多等30秒，seal后禁止迟到写。原生decode不能保证立即中断，取消先结束服务，等待在途native结束才释放资源。
- Predictive back / AndroidX transition seeking需要更换现有手工Portal控制与引入依赖，本轮保留稳定Portal，后续单独评估。
- 旋转/离开听写页面会结束并保存，结果在History；新页面不自动恢复detach的旧listener，不声称后台持续录音或恢复原生计算。
- 发布仍阻塞：无真正长期release签名；streaming权重再分发unresolved、未知embedding不伪造来源。严格release验证会拒绝；模型/密钥/私有音频仍忽略。

## 真实验证与证据边界

原baseline check-all全通过；升级整合在当前补充修改前还有一次check-all全通过，日志 `/workspace/android-dev/logs/task-integrated-checks.log`。最终按用户要求只运行相关轻量验证，**旧日志不冒充当前全项目验收**。

- 当前通知相关：Live Update29行为；Job/native logic413；payload6、gate guards7；Island characterization6文件/7方法/11字段、9 mutation、真实bridge10 host场景；wiring1104、first-run、cloud/audit source guards、docs/XML。
- 当前已有功能收尾：pipeline43、bounded polish/audio队列；microphone189 +3敏感mutation；实际DictationActivity事件顺序/前台权限；真实HTTP6（含中断、trickle总deadline、无效Key控制字符不外泄）。这些均不运行模型。
- 之前已有证据：session101、polish71、模型installer26及manager并发/capability、manifest9、benchmark9、secret migration12、签名policy11/CLI8/pack6/实际签名fixture1。各专题有范围说明，没有为通知层重复无关测试。
- 当前真实API36 jar下 `bash build.sh --compile-only` 资源链接、完整Java、DEX通过；保留原annotation/deprecation warnings。不生成当前安装包，不声称OEM/native设备验收；之前无模型dev APK不能作为含最新补充的产物。

独立审查复现末帧、慢构造stop、旧句复核擦除新句三项，修复后转为实际类+stand-ins回归。通知层由主执行者核对官方API、SDKjar、生产publisher行为和baseline保护；不同意见一致不是设备证据。

详见 [LIVE-UPDATES.md](LIVE-UPDATES.md)、[ISLAND.md](ISLAND.md)、[SESSIONS.md](SESSIONS.md)、[SECURITY.md](SECURITY.md)、[POLISH.md](POLISH.md)。HyperOS / ColorOS呈现均为**待真机验证**。完整差异统计及主要文件清单在本目录 `diffstat.txt`，包含已有升级和本次补充。

## 后续 UI 与 APK 收尾

本轮继续优化了主题、动效中断、可达布局、稿件搜索及导出/播放/聊天交互，并生成含模型的开发签名 APK。详细范围、独立审查和设备证据在 [UI 优化记录](ui/README.md)；Shizuku 高级能力按用户最新要求暂缓。

## 1.0.4 用户真机反馈

模型状态与内置准备、引导内授权弹窗、设备文案和椭圆按钮修复见 [反馈修复记录](feedback/README.md)。
