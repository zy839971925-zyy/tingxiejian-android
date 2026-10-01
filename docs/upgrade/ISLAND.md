# 受保护超级岛路径：baseline、现状与验证边界

2026-10-01 的受保护 baseline 是 `69085db345c6e317e0de6e9d28e9720e6b4190ed`，用户已确认此前版本在真实设备上成功显示超级岛。六个 Island/Shizuku Java 文件未变，channel/ID/220ms/5s/停止action/payload保留。补充标准Live Update后，旧notify只增加异常catch关闭本次岛重试，其余旧方法仍完整受baseline约束；业务调用前增加薄路由。详见 [LIVE-UPDATES.md](LIVE-UPDATES.md)。这里没有新的OEM真机结果。

## 实际成功路径

1. `LocalService.onStartCommand` 先创建前台占位通知。请求本地 Xiaomi 岛路径时使用 ID **12**；否则使用 ID **11**。占位通知实际调用 `buildNotification(..., false)`，使用普通静音频道 `transcribe_quiet_v2`。首次 focus 通知随后使用不同 ID，不能把占位通知当作岛通知更新。
2. worker 的进度事件驱动 `notify`。主线程不会打开 XMSF 门禁。仅在用户请求岛且 capability 允许时构建 `transcribe_island_v1` 的通知：频道为 DEFAULT，普通频道为 LOW；两者无声音、振动、灯光或 badge。主通知 ID 固定为 **11**；时间戳 `NOTIFY_WHEN` 在类初始化时固定。普通更新按 **5000ms** 节流，首个岛帧可以绕过节流；结束通知保留 **4000ms** 后关闭。
3. capability 要求 Xiaomi/Redmi/Poco、应用可读的焦点协议至少 V3、岛 feature 未明确关闭，以及可读的授权 Shizuku 门禁。焦点通知开关查询是诊断信息。`READY` 只代表可以尝试提交。
4. publisher 构建 FocusTemplateFactory.V3 JSON，附加 `miui.focus.param`、`miui.focus.pics` 和首次/更新浮动 flags，然后通过 `Notification.Builder.recoverBuilder(...).addExtras(...).build()` 重建并检查成品 extras。payload 上限 **3072 bytes**，timeout 以分钟为单位、上限 **720**；首次帧允许浮动，后续帧不再浮动。
5. 每一次岛通知提交都串行执行 gate → post → 等待 **220ms** → `finally` restore。首次帧可通过 callback 执行 `startForeground(11, focus)`；后续帧使用 `manager.notify(11, ...)`。成功或普通回退后取消 ID 12。gate/payload/提交失败使当前 run 停止继续尝试私有路径，回退普通通知，核心转写继续。
6. 停止 action 保留指向 `MainActivity.ACTION_STOP_JOB` 的 activity Intent URI；普通通知中的停止按钮使用 `LocalService.ACTION_CANCEL`。关闭服务取消 ID 11 和 ID 12。

## 防火墙现状与历史文档矛盾

当前 `ShizukuIslandBridge.setXmsfBlocked(true)` 读取 `OEM_DENY_3`（chain **9**）和 XMSF UID 原规则，先同步持久保存 `target_uid / old_rule / old_chain / pending`，随后**无条件调用 `setFirewallChainEnabled(9, true)`**，再给 XMSF UID 写 DENY（rule **2**）。原 chain 已启用且 XMSF 已 DENY 时拒绝覆盖；原 chain 关闭时仍会打开 chain，即使它包含其他 UID 的规则。

这是 shared chain 的全局状态变化，不是仅作用于 XMSF 的 UID 变化。打开 chain 可能让其他应用原本休眠的规则开始生效。恢复逻辑先恢复 XMSF UID rule 并读回，再恢复原 chain enabled 值并读回，最后才清除恢复记录。成功 arm 后还安排 **900ms** watchdog；部分 arm 失败也依赖 publisher 的 `finally` 修复。进程死亡后仅有持久记录，下一次 probe 尝试修复；Shizuku 不运行或授权失效时无法保证立即恢复。并发外部系统规则变更也没有版本比较保护。

因此以下历史表述与当前 baseline 不一致：

| 文档 | 与当前代码矛盾的表述 |
| --- | --- |
| `docs/REVIEW-2026-09-28.md` 与 `.zh-CN.md` | 新 run 不打开 inactive chain，恢复不改 chain；当前代码会打开并恢复 chain。 |
| `docs/ARCHITECTURE.md` 与 `.en.md` | 当前实现不会激活原本关闭的 shared chain；当前代码无此限制。 |

这些历史叙述不能作为当前版本的安全证明。本次没有将其所描述的行为猜测性移植进已真机成功的路径。无法从桌面测试证明禁止启用 chain 后，用户当前 HyperOS 的岛路径仍会成功，故遵循 `spec/01-protected-island.md` 的先 isolation/tests/documentation 规则，保留受保护实现。

后续独立修复建议：默认仅在 chain 已启用时临时修改 XMSF UID rule，不打开 shared chain；单独提供明确、默认关闭的 legacy/experimental compatibility 选择，并显示其他应用连接/推送及恢复限制。新恢复记录需要区分 default 与 legacy（旧 pending 记录仍可恢复旧 chain），新 default 恢复不得覆盖 shared chain。先对两个分支、部分失败、授权丢失、watchdog、进程死亡和外部规则竞争做隔离行为测试，再在用户当前 HyperOS 设备验证首帧/更新/取消以及推送恢复。该方案在本次**未实现、未真机验证**。

## 已建立的 characterization

`design-tools/island-characterization.py` 对以下证据分别检查：

- 六个受保护 Java 文件的完整 SHA-256 与 `docs/upgrade/baseline.json` 和 baseline source snapshots 一致。
- `LocalService` snapshot 的完整 hash 固定；允许服务持久化/生命周期代码演进，但 `ensureForeground / notify / createChannel / stopActionUri / updateNotice / buildNotification` 两个 overload 共七个方法，以及十一项 channel/ID/timing/首帧字段保持准确的声明/方法 hash。
- 通过 XML parser 验证 package、完整 Shizuku provider（包括 authorities、exported、enabled、multiprocess 与 permission）、完整 queries、所有 baseline permissions 和 application metadata。允许新增 RECORD_AUDIO 权限和 activity；禁止其他新增权限。检查会忽略 XML 缩进，不能被只保留源码字符串的无效声明蒙混通过。
- checker 自身的敏感性验证覆盖九个 ID/channel/节流/频道选择/authority/provider permission/queries/metadata/Shizuku permission 变异。
- host Android/Shizuku/Binder fakes 直接编译运行实际的 `ShizukuIslandBridge` 与 `XiaomiXmsfValidationGate`，测试十个场景：关闭 chain 的 legacy 成功及对其他 UID 既有 DENY 的影响、已启用 chain/原 rule 保存、已被阻断时拒绝、各授权前置失败、commit 失败不写系统、部分 arm 的写异常与读回异常、恢复失败重试、关闭设置后仍修复旧 pending、跨实例 probe 互斥，以及 watchdog 恢复。

行为测试精确记录 baseline 的全局 chain 副作用，**不将其解释为安全结论**。host fakes 无法验证 OEM Binder ABI、Android NotificationManager、SystemUI 展示、真实权限恢复、后台执行或设备推送；LocalService 的选择性 hash 证明保护区域字节保留，不能证明新增生命周期调用点正确。

运行命令：

```sh
python3 design-tools/island-characterization.py
python3 design-tools/check-island-gate.py
bash design-tools/island-payload-check.sh
```

上述 checks 在 2026-10-01 的本次 characterization 中通过。原 structural guard 与原 payload builder 的六组真实 JDK 执行结果都保留；本次无真机测试，不声称本次升级的 UI 或录音路径已经在 HyperOS 显示岛。
