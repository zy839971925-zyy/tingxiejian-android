# 听写间 Codex 升级任务包

这是给 Codex 的工程任务包，用于继续修改 `tingxiejian-android`。

使用方式：

1. 将本目录放到项目根目录，或把整个 ZIP 提供给 Codex。
2. 给 Codex 的聊天提示只需：

   > 阅读 `CODEX_TASK.md`，结合当前仓库、既有上下文、AGENTS.md 和我的 Workflow Skills，完整执行任务。不要只给计划，实际修改、测试、审查并完成所有当前环境可完成的工作。

3. `CODEX_TASK.md` 是入口；`spec/` 中是按主题拆分的详细约束。

原则：Codex 应按需读取 spec，不需要一次把全部文件装入上下文。
