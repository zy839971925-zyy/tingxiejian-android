# 01 — 超级岛保护边界

当前超级岛/HyperOS 路径已经在用户真实设备上成功工作，视为 regression-sensitive subsystem。

优先保护：
- `ShizukuIslandBridge.java`
- `XiaomiIslandPublisher.java`
- `XiaomiIslandCapability.java`
- `XiaomiIslandPayloadBuilder.java`
- `XiaomiXmsfValidationGate.java`
- `IslandNotification.java`
- `LocalService` 中 Island 发布路径
- notification channel / notification ID
- XMSF gate timing / focus payload
- Manifest 中 Shizuku provider、authorities、queries、metadata、相关权限

规则：
- 禁止因为“整洁/抽象/统一”而重构上述代码。
- 不随手改 payload、通知结构、ID、channel、timing。
- Manifest 清理不得误删相关声明。
- Island/Shizuku 用户设置不得被重置。

此前发现：`ShizukuIslandBridge` 可能在 OEM shared chain 原本关闭时执行 `setFirewallChainEnabled(..., true)`，存在全局副作用风险，且与项目历史审查文档存在矛盾。

处理方式：
1. 先跑现有 Island checks 并建立 characterization baseline。
2. 完整理解当前成功路径。
3. 仅对 firewall gate 做独立、小范围修复；不要联动改 payload/timing。
4. 优先设计 safe default + 明确的 legacy/experimental compatibility path，而不是一刀切破坏已验证路径。
5. 如无法证明改动不会导致当前 HyperOS 路径回归，宁可先做 isolation/tests/documentation，不凭猜测重写。
6. 修改后跑完全相同的 Island tests；无法真机验证时必须明确写“未真机验证”。
