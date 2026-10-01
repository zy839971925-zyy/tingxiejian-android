# Codex Task — 听写间全面升级

继续开发当前 `tingxiejian-android`。你已经拥有该项目此前的 Codex 上下文；结合当前 working tree、仓库文档、适用的 `AGENTS.md` 和用户的 Workflow Skills，以 Ultra 深度实际执行，不要让我重复背景。

先建立 baseline，再做简洁 milestone plan，然后继续真实修改；不要在计划阶段停下。采用 `inspect → small coherent change → test → repair → next`。所有结论以当前代码和真实验证结果为准，不伪造真机、benchmark、许可或超级岛结果。

## 目标

把当前以“导入录音转写”为主的 App 升级为：

- 实时听写 + 文件转写双入口；
- 二者共享统一 SpeechPipeline；
- Streaming 负责低延迟反馈，强 Finalizer 负责最终准确率；
- 加入 VAD、Stable Prefix、热词、ITN/文本规范化；
- 支持可选 AI 轻度整理，但忠实转写永远保留；
- UI/Motion 更连贯、更高级；
- 保持 offline-first、旧数据、导出、云问答、超级岛等现有能力不回归。

## 绝对约束

1. **超级岛是已在真实设备验证成功的受保护子系统。** 除非修复明确 bug 必须修改，否则不要重构 Shizuku/XMSF/Xiaomi Island 路径、payload、notification channel/ID/timing、Manifest 相关声明。涉及它的改动必须先 characterization，再最小修改，再回归验证。详见 `spec/01-protected-island.md`。
2. 不改 package/application ID。
3. 保持 Java + Android Views；不迁移 Compose/Kotlin，不做无关大重构。
4. 不自动 push / merge main / create Release。
5. 新能力必须可降级：Finalizer、标点、分人、AI 整理、超级岛失败都不能拖死核心转写。

## 必做范围

按需阅读并执行：

- `spec/01-protected-island.md`
- `spec/02-critical-engineering.md`
- `spec/03-asr-pipeline.md`
- `spec/04-live-dictation.md`
- `spec/05-ai-polish.md`
- `spec/06-ui-motion.md`
- `spec/07-models-benchmark.md`
- `spec/08-data-architecture.md`
- `spec/09-testing-ci.md`
- `spec/10-acceptance.md`

## 执行要求

开始前：
- `git status`、记录 HEAD；
- 阅读与本任务相关的现有文档和代码；
- 跑现有 `bash design-tools/check-all.sh` 及 Island 相关检查，记录 baseline pass/fail/unavailable。

过程中：
- 每个 milestone 有可观察 acceptance criteria；
- 验证失败先修；
- 不要一次写几千行最后才测试；
- 不要用源码字符串检查替代关键状态机的行为测试；
- 对模型、Android API、sherpa-onnx 能力等时效性事实，优先查官方一手资料。

结束前：
- fresh audit：线程/资源、race、process death、旧 History、权限、fallback、Island、signing、secret、model provenance、测试假阳性；
- 当前范围内能修的继续修，不只列 TODO。

最终汇报：实际完成内容、ASR pipeline、实时听写、模型选择/未完成 benchmark、AI 整理保护、UI/Motion、超级岛是否改动、Manifest 权限变化、signing/security/provenance、测试与构建真实结果、真机待验项、blocker、`git diff --stat`、主要修改文件。
