# 10 — Acceptance / Done When

任务结束前至少确认：

## Core
- 原文件转写仍可用。
- History/TXT/SRT/JSON 不回归。
- 默认 offline-first；云端仍 opt-in。
- “问 AI”不回归。
- package ID 不变。

## Island
- 无关代码未重构。
- Manifest 关键声明未误删。
- Island/payload checks 重跑。
- 若 firewall gate 有改动，存在行为测试。
- 未真机验证时明确说明。

## ASR
- Streaming endpoint 不再是不可逆最终切段来源。
- VAD 有明确角色。
- Finalizer 可安全降级。
- Hotword architecture 存在。
- ITN/normalization 存在或有明确可测实现。
- Diarization 有 memory guard。

## Live
- 有“实时听写”入口。
- 点击时才请求 microphone。
- permission denied 不崩。
- AudioRecord 正确 start/stop/release。
- capture queue 有界。
- Stable Prefix 有行为测试。
- Finalizer 异步且不阻塞下一句。
- 停止后结果进入 History。

## Polish
- Final 永远保留。
- 有忠实稿/整理稿。
- protected entities 有验证。
- AI/parse 失败不破坏原文。

## UI
- Reduce Motion 有效。
- Haptic 与 Reduce Motion 解耦。
- partial 更新不明显阻塞主线程。
- 不做 Compose 重写。

## Security / Build
- release signing 不静默生成新 release identity。
- secret 不再纯明文 prefs。
- model provenance machine-readable。
- unresolved rights/provenance 不会被 release validation 伪装通过。

## Final audit
重新检查：线程/资源泄漏、race、process death、旧数据、权限扩大、model fallback、Island 回归、测试假阳性、文档/代码漂移。

最终报告包含：实际完成、关键架构、模型/benchmark 状态、实时听写、AI 整理、UI/Motion、Island 改动、权限变化、security/signing/provenance、运行命令与真实结果、真机待验、blocker、`git diff --stat`、主要文件。
