# 08 — 数据、模型包与轻量架构

## ModelManager / Model Pack

当前首次准备全部模型应改为按能力：
- Core/Streaming
- Accurate/Finalizer
- Punctuation/Normalization
- Diarization
- Optional High Accuracy

关闭 diarization 时不强制准备分人模型。

为 optional 高精度模型按需下载/导入预留能力；模型安装使用：
`.partial → size/hash verify → atomic promote`。
失败不能损坏已有模型。

建立 machine-readable model manifest/lock：
- logical id
- filename
- family
- source/revision
- expected SHA-256（无法计算时不要编造）
- license
- redistribution status
- pack

未核实来源/再分发权必须明确 unresolved，release validation 不得当 verified。

## Session state

不要只依赖 static LocalService/Bus。
增加轻量持久 session：
`RUNNING / FINALIZING / POLISHING / DONE / CANCELLED / INTERRUPTED / FAILED`。

process death 后至少能诚实标为 INTERRUPTED；本轮不要求 resumable neural inference。

## History

支持 raw/final/polished 和模型/词库元数据，同时保持旧 JSON backward-compatible。

## 轻量拆分

随着功能增长逐步提取：RecognitionPipeline、DictationController、ModelManager、SessionRepository、HotwordRepository、SecureSecretStore、TranscriptPolisher、MotionSpec 等。

不要制造几十层 abstraction。
