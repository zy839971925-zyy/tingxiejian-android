# 07 — 模型与 Benchmark

不要凭印象直接换模型；以官方资料 + 本项目 benchmark 决定。

重点调查 Android/sherpa-onnx 实际可运行候选：
- 当前 Streaming Zipformer
- 当前 Paraformer
- 新 Offline Zipformer/CTC 候选
- Qwen3-ASR 0.6B int8
- FunASR Nano int8
- 其他有明确 Android 支持、许可可接受的候选

优先一手来源：sherpa-onnx 官方、模型原作者/model card、Android 官方。

记录：
- streaming/offline
- Android/sherpa compatibility
- model size
- 内存/加载复杂度
- hotword 支持
- 语言支持
- license / redistribution
- 预期 latency class

官方 benchmark 不能当成本 App 实测结果。

## Benchmark harness

支持用户以后放脱敏 corpus；不要提交私密录音。

至少可统计：
- CER
- 专业词/热词错误
- 数字错误
- first partial latency
- finalization latency
- RTF
- peak memory
- model load time

测试类别支持：安静普通话、自然口语、快速、远距离、噪声、中英混合、科技术语、人名地名、数字日期金额、长句、口音/方言、连续实时输入。

没有 corpus 时只搭 harness/schema/metrics，不编造模型排名。
