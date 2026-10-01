# 09 — 测试与 CI

保留现有 `design-tools` structural guards，但关键逻辑必须有行为测试。

优先测试：
- StableTextTracker
- VAD segment merge / overlap
- finalizer fallback
- protected entity validation
- structured polish output parsing
- session state transitions
- model pack state/install
- secure secret migration
- firewall/Island state machine（若修改）
- old History compatibility

不要把 `"needle" in source` 当安全证明。

建立 GitHub Actions fast CI（不需要 500MB 模型）：
- pure Java/Python logic tests
- existing source guards
- XML/docs guards
- model manifest/schema
- cloud/security guards
- island characterization
- diff/style sanity

完整 APK/model validation 单独作为 release validation。

若环境满足，实际 build APK；若缺 SDK/权重/合法模型等，准确报告 unavailable，不伪造通过。
