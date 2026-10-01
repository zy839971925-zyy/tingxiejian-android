# 03 — 统一 ASR Pipeline

现状问题：Streaming endpoint/20s 强制切段先形成 Piece，再由旧 Paraformer 按这些边界复核；早期边界错误会限制最终准确率。

目标架构：

`AudioSource → PCM → VAD/Speech Segmentation → StreamingRecognizer → FinalRecognizer → Hotwords → ITN/Normalization → Punctuation → Optional Diarization → Transcript`

关键原则：
- 文件转写与实时听写共用 recognition core，仅 AudioSource 不同；
- Streaming 负责即时反馈，不拥有最终文本决定权；
- VAD 是一等公民，不完全依赖 streaming endpoint；
- Finalizer 使用独立语音段和合理 pre/post-roll；
- Finalizer 失败时保存 streaming/standard 结果，不丢任务；
- segment 有稳定 ID；
- 不为了抽象而做重型 Clean Architecture。

建议轻量接口/模块：
- AudioSource / FileAudioSource / MicrophoneAudioSource
- VadEngine
- StreamingRecognizer
- FinalRecognizer
- RecognitionPipeline
- StableTextTracker
- HotwordManager
- TextNormalizer

命名可按现有代码风格调整。
