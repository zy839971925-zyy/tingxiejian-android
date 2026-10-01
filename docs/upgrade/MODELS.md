# 模型包、官方来源与评测状态

2026-10-01 调查与本工作区验证。优先满足用户明确指定的 **Qwen3-ASR 0.6B int8 本地最终识别**；Streaming Zipformer 保留即时反馈，Qwen 缺失、设备内存不足或识别失败时由现有 Paraformer/Streaming 路径降级。这里不声称 Qwen 已在用户 Android 设备或语料上达到某个准确率，也没有模型排名。

## 已取得的权重与可复核来源

权重均放在已忽略的 `models/`，不进入源码提交。`assets/model-manifest.json` 是被 APK 打包的机器可读锁：逐文件 logical ID、APK filename、local_path、family、pack、来源、不可变 release asset ID、大小、实际 SHA-256、许可证及再分发状态。原始权重不受项目 MIT 授权覆盖。

| 包 | 本工作区取得的运行文件 | 状态 |
|---|---|---|
| `core-streaming` | 4 文件，167,360,920 字节 | 官方发布包下载完成，发布者 archive SHA 验证通过，逐文件 hash 已锁。原始模型 gated 且许可证未明确，**再分发 unresolved**。 |
| `accurate-finalizer` | Paraformer int8 + tokens，243,446,974 字节 | 官方包已下载、逐文件与 archive hash 已实际计算；此较早 release asset 没有发布者 digest，明确记录 computed。转换模型 card 声明 Apache-2.0。 |
| `punctuation` | CT-Transformer int8，75,519,198 字节 | 官方包下载完成，发布者 archive SHA 与逐文件 hash 通过。原作者 ModelScope API `License=Apache License 2.0`，查询日期已记录。 |
| `diarization` | 原 `diar-segmentation.onnx` / `diar-embedding.onnx` | 本工作区没有取得；未知 `embed.onnx` 来源/条款仍 unresolved，未猜测替代权重。关闭分人不要求它们存在。 |
| `qwen3-asr-0.6b-int8` | 6 文件，987,015,347 字节（约 941 MiB） | 官方 sherpa release 包已下载，发布者 archive SHA 与逐文件 hash 验证通过；原作者 Qwen model card 声明 Apache-2.0。 |

Qwen 的官方发布包：

- URL：<https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2>
- GitHub release asset ID：`390698077`；压缩包 **878,702,423 字节**；发布时间 `2026-04-07T09:52:28Z`。
- 发布者 SHA-256 与本工作区计算相同：`393f8a14e2f5fb96746aaab342997a40641001fbd5bf9592a080a8329178ee96`。
- 原作者 [Qwen/Qwen3-ASR-0.6B model card](https://huggingface.co/Qwen/Qwen3-ASR-0.6B/blob/5eb144179a02acc5e5ba31e748d22b0cf3e303b0/README.md)，观察 revision `5eb144179a02acc5e5ba31e748d22b0cf3e303b0`。转换包 README 指向原作者和 [Wasser1462/Qwen3-ASR-onnx](https://github.com/Wasser1462/Qwen3-ASR-onnx/tree/0829a577ee741e408e65763dfe01ff4fe75408e9)。这是源模型 card 的观察 revision，**没有凭空声称转换作者使用了这个精确训练权重 revision**。
- 权重机械格式转换沿用已声明的 Apache-2.0 模型授权；未发现转换权重新增限制。转换工具仓库本身没有 LICENSE，**没有复制或再授权其工具代码**；不能把 sherpa 运行时 Apache-2.0 当模型许可证。分发时仍需保留上游模型 attribution 与 Apache-2.0 notices。
- [官方 sherpa Qwen 模型说明](https://k2-fsa.github.io/sherpa/onnx/qwen3-asr/pretrained.html)与[转换说明](https://k2-fsa.github.io/sherpa/onnx/qwen3-asr/export.html)，本次读取 docs repo revision `c02f72ca1540163a54019e845127fa52d5de175b`。

## 实际 Android/sherpa 接口及资源成本

本项目取得的 `sherpa-onnx-1.13.8.aar` 的 `classes.jar` 用 `javap` 实际检查过，存在 `OfflineQwen3AsrModelConfig` 与 `OfflineModelConfig.setQwen3Asr(...)`。ONNX 图也实际读取过，不是根据模型名猜配置。

| 相对目录中的文件 | 字节 | 配置 |
|---|---:|---|
| `conv_frontend.onnx` | 44,148,281 | `qwen.setConvFrontend(...)` |
| `encoder.int8.onnx` | 182,491,662 | `qwen.setEncoder(...)` |
| `decoder.int8.onnx` | 755,914,231 | `qwen.setDecoder(...)` |
| `tokenizer/vocab.json` | 2,776,833 | `qwen.setTokenizer(packDir + "/tokenizer")` |
| `tokenizer/merges.txt` | 1,671,853 | 同上 |
| `tokenizer/tokenizer_config.json` | 12,487 | 同上 |

必须 `FeatureConfig.setFeatureDim(128)`、sample rate 16000，并 `OfflineRecognizerConfig.setFeatConfig(...)`；默认 80 维配置不适合该导出。Qwen 不需要 `tokens.txt`，`OfflineModelConfig.setTokens("")`。建议保留官方默认 `maxTotalLen=512`、`maxNewTokens=128`、`temperature=1e-6`、`topP=0.8`、`seed=42`、CPU provider、3 threads。热词接口 `setHotwords(...)` 为 UTF-8 ASCII 逗号分隔列表。这是 Qwen 上下文提示，不应宣称与 transducer 的加权 beam hotword 等价。

实际 ONNX：opset 17；frontend 为 float32，encoder/decoder 的动态 int8 使用 `DynamicQuantizeLinear`/`MatMulInteger`。decoder 有 28 层 float32 KV 输入，各为 `[batch, max_total_len, 8, 128]`；512 context 下仅双 KV 基础缓存约 **112 MiB**，还需权重、ORT arenas、中间激活、tokenizer、音频和并存 streaming 模型。只用压缩包或 0.6B 参数数估算 RAM 会漏掉大量开销。

本工作区在 **Linux x86_64 CPU** 用官方 Python `sherpa-onnx==1.13.8` 实际加载这个包并识别发布包的公开 `ja1.wav`，得到非空日文结果。一次观测：load 2.952 秒，decode 2.113 秒，音频 5.08 秒，进程 peak RSS **1,516,167,168 字节**，3 threads / 512 context / 128 new tokens / 128 features。原始观测在忽略的 `build/model-research/qwen-desktop-smoke.json`。这是 **单样本桌面可运行性检查，不是 Android PSS、用户语料 CER、App RTF 或性能排名**；输出也不是完美无误。

因此 Android 加载前建议至少约 2 GiB available memory 的保守 preflight，3 GiB 可用时更有余量；这是工程阈值，不能保证避免 native OOM。128/512 参数会限制每段可识别长度；按 VAD/有限片段 finalizer 运行，长句需真机检查是否截断。不要把训练框架/vLLM 的高并发吞吐宣传当手机 latency。原作者的 Python streaming 当前依赖 vLLM，本项目 sherpa Qwen 是 **offline finalizer**，不能宣传它具有原生在线增量推理。

## 候选比较（没有本 App 准确率排名）

| 候选 | 模式 / Android 证据 | 体积与加载复杂度 | 语言 / 热词 | 许可及延时判断 |
|---|---|---|---|---|
| 当前 Streaming Zipformer zh `2025-06-30` int8 | Online transducer；当前项目 AAR `OnlineRecognizer` 已使用 | 实得约 160 MiB 的 4 运行文件；缓存式增量解码 | 中文；modified beam search 的 transducer contextual hotwords | 原模型 gated、license 未确定；低延时反馈候选，具体手机延时未测 |
| 当前 Paraformer zh `2023-09-14` int8 | sherpa OfflineParaformer API，现有 finalizer | 实得约 232 MiB；非自回归 bounded segment，较少 tokenizer/KV 复杂度 | 中文；此 sherpa Paraformer 路径没有经证明的通用 hotword 加权支持 | 转换 card Apache-2.0；保留轻量降级，不承诺准确率提升 |
| Offline Zipformer CTC zh `2025-07-03` int8 | [官方模型页](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-ctc/icefall/zipformer.html)提供真实 arm64 Android VAD/simulated-streaming APK、AAR 有 OfflineZipformerCtc 配置 | 官方列出 model 約 350 MiB + tokens；本工作区未下载 | 中文；CTC greedy 路径不能承诺 transducer hotword 效果 | HF 转换 card 没有 license metadata，来源/再分发仍待核实；适合另做 Android CER/RTF 比较 |
| **Qwen3-ASR 0.6B int8** | 实际 AAR config + 官方 Java example + 本次桌面权重加载；真机待验 | 实得运行 941 MiB，三 ORT session + tokenizer + KV；本次桌面 RSS 約 1.41 GiB | 原作者 30 语言 + 22 中文方言；本地 hotword context 提示 | 原作者 Apache-2.0；自回归 finalizer，手机端可能明显增加 finalize latency，必须测量 |
| FunASR Nano int8 `2025-12-30` | [官方模型页](https://k2-fsa.github.io/sherpa/onnx/funasr-nano/pretrained.html)链接真实 Android VAD APK，AAR 有 OfflineFunAsrNano 配置 | 官方目录約 948 MiB ONNX + 16 MiB tokenizer；encoder_adaptor/llm/embedding 三模型；本工作区未下载 | 官方此导出主要中文/英文/日文与方言；不把原始 card 更广语言标签当导出承诺；context 热词须实测 | [原作者 card](https://huggingface.co/FunAudioLLM/Fun-ASR-Nano-2512) Apache-2.0，观察 revision `272c57b82523ada6fd87095e955f8e29100979ab`；导出 exact provenance 尚未独立锁定；大模型 finalizer 候选 |

这个表是 **兼容性与测量优先级**，不是准确率/速度评分。用户指定的 Qwen 被实际下载、完整校验和可运行性检查；没有为了泛化选择而另下载 1.7B 或其他 GB 候选。官方 Aishell/Wenetspeech/vLLM benchmark 不等于用户设备或此 App 的结果。

## 安装与故障语义

`ModelPrep.prepare/ready/totalBytes/doneBytes` 只操作 Core/Streaming 4 文件。`ModelManager.ensure(context,filename)` 按能力取得单文件；Finalizer、标点和分人只在明确使用该能力时准备。

`availableQwen` 提供导入包或 bundled 包的快速可用性；`readyQwen` 是安装状态检查，**不冒充每次完整 hash 审计**。`ensureQwen` 在首次 native 使用前校验字节/哈希，随后缓存文件路径/大小/mtime/预期 hash 指纹；应用私有目录不交给外部写入。升级 manifest 的 hash 会使缓存失效。

显式用户导入 `importQwen(context,ImportSource,Progress)` 接收受信 manifest 中的相对文件名。单文件执行 `.partial → expected size/SHA-256 → fsync → ATOMIC_MOVE`；完整包先把全部文件验证到独立 `.partial-*` 目录，再提升为不可变 `version-UUID`，最后原子替换很小的 `active` marker。失败清理新 partial，旧 marker/旧 generation 保留；已打开的 recognizer 不会因新导入被修改。来源不是 App 自动联网下载；网络仍只由明确配置的 `Cloud.java` 执行。

导入临时目录与最终目录在相同私有 volume，不能原子 rename 时 fail closed。旧 generation 会保留以免破坏活跃 native reader；反复导入需额外存储，此轮没有后台删除仍被使用的 generation。导入的只有规定 6 文件，公开 test_wavs 不打入 APK。

## 验证入口与边界

```sh
bash design-tools/model-logic-check.sh
python3 -m unittest discover -s design-tools -p test_model_manifest.py
python3 -m unittest discover -s benchmark -p 'test_*.py'
python3 scripts/validate-model-provenance.py
python3 scripts/validate-model-provenance.py --release --pack qwen3-asr-0.6b-int8 --pack accurate-finalizer --pack punctuation
```

最后一个命令真实校验所选现有文件并通过。Release packaging 应重复指定**实际包括的包**；没有 `--pack` 时校验 manifest 中所有 `bundled:true` 历史文件。加入 `core-streaming` 因 gated/未知再分发权会失败；未知 embed 和缺失分人权重也不能被悄悄当 verified。开发验证 APK 可以清楚标记来源未解决；它不因此获得发布授权。

行为测试覆盖错 hash（相同长度）、过短/过长、read 中断、partial 清理、路径越界、包中某个文件失败保留旧 active、原子升级保留旧 reader、fresh hash 发现损坏、按能力只准备 core、optional 缺失保持 core 可用、显式 verified pack 导入。Schema/provenance validator 不将 `"needle" in source` 当安全证明。

[benchmark/README.md](../../benchmark/README.md)与 schema/score CLI 已建立，可用脱敏用户 corpus 比较 CER、专业词/热词错误、数字错误、first partial、finalization、RTF、peak memory、load time，支持规定 12 类场景。当前没有用户 corpus/Android 真机观察，只有 synthetic scorer tests 与上述公开样本桌面 smoke，因此**用户场景 benchmark 未完成**。
