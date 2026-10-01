# 标准实时状态与通知降级

2026-10-01。此功能补充已有 ASR / 实时听写升级，不替代这些工作。运行环境为 Linux Codex Cloud，无 HyperOS / ColorOS 真机。

## 实际链路

`startForeground(普通通知) → Job → 小米超级岛可尝试时使用原路径 → 失败/不支持时标准 Android Live Update → 同一 ID 的普通 FGS 通知`

- `Job.percent / stage / detail / eta / indeterminate` 继续是唯一业务进度；没有第二套 OEM 进度对象。
- 普通 FGS 通知从启动就存在。ID 11、初始小米 bootstrap ID 12、两个原频道、content/stop intent、固定 when、原 4 秒完成 linger 保留。OEM 提交成功不会调用 stopForeground 或删除 FGS 通知。
- `publishProgress` 只路由。小米使用原 `updateNotice/notify`；原 5 秒更新和 XMSF 220ms validation 不变。6 个受保护 Java 文件逐字未变。唯一原通知方法改动是 `notify` 的异常 catch 设置本次 `islandWanted=false`，防止错误后不断重试。characterization 从不可变 baseline 精确推导这一个允许增量，其他方法/字段仍需原 hash。
- 连续任务先建立合法FGS ID12，再清理上一任务linger的ID11，保证新focus仍按“新ID11”首次提交；不会为了OEM呈现而删除当前FGS。若本次最初能力不支持，保持向下路由至结束，避免中途把普通ID11更新伪装成首次focus。
- 标准路径的 `AndroidLiveUpdatePublisher` 使用自己的 1500ms 节流；legacy普通路径独立5000ms；阶段变化和首次完成可立即提交，detail/eta 更新也纳入治理。没有定时后台重发任务。
- 标准通知先 recover 原 FGS builder，保留图标、打开/停止动作、标题、详情、ETA、时间戳等；Android 16+ 增加 ProgressStyle、short critical text、ongoing、非 colorized、category PROGRESS。
- 系统未允许 promotion 时仍用同一个 ProgressStyle 通知，系统自然按普通通知呈现；没有第二套浮窗、Bubble 或 conversation。构造/提交异常回退原普通通知，本任务不再尝试 promotion；新任务清掉节流/失败状态。服务停止、销毁、超时仍取消两个现有 ID。
- `CloudFileTranscriber` 从 LocalService 的内嵌 CloudChunker 机械抽出，保留 120 秒分块、事件、取消检查、自动文本校正与 EOF result 流程；通知不参与 local/cloud 识别选择。

## SDK 与设置

compile 使用官方 Android SDK **36 r02** 的 android.jar，手工 aapt2 + javac + d8 保留；minSdk **26**、targetSdk **35** 不变。增加且仅增加标准非运行时权限 `POST_PROMOTED_NOTIFICATIONS`。之前 ASR 升级的 `RECORD_AUDIO` 继续仅由用户显式开始前台听写请求。

`ProgressStyle` / `canPostPromotedNotifications` / `hasPromotableCharacteristics` 是 API 36。原生 `Builder.setRequestPromotedOngoing` 在官方当前文档标为 **36.1**，基础 API36 jar 中没有，因此使用标准 extra `android.requestPromotedOngoing=true`，与两个固定 revision 的参考仓库一致。所有 API36 调用有 SDK guard；主要调用放在 Api36 内部类，旧系统不触发。没有反射补造不存在的接口。

新增“实时进度与系统状态”配置，`ui.live_updates` 默认开启，独立于原 `ui.island` 和 `ui.island_shizuku`。诊断状态区分 UNSUPPORTED / APP_DISABLED / NOTIFICATIONS_BLOCKED / CHANNEL_BLOCKED / SYSTEM_DISABLED / READY / API_UNAVAILABLE / PUBLISH_FAILED。READY 只表示可请求，不代表系统显示。系统入口使用官方 `android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS` + EXTRA_APP_PACKAGE；没有可解析 Activity 时使用原应用通知设置，仍捕获不提供入口的情况。

ColorOS 16 走标准 Android Live Update，是否渲染为流体云由系统决定。ColorOS 14/15 使用普通通知。没有 OPPO 私有 API、Seedling AAR、Root、LSPosed、通知监听器、Accessibility 或全局 Shizuku 依赖。标准 API 权限与形态满足也不保证 OEM 接纳；用户发起的长转写是否适合该 OEM 的实时活动资格仍需实机观察。

## 核对来源

- [Android Live Update 官方指南](https://developer.android.com/develop/ui/views/notifications/live-update)：ongoing、标题、标准样式、权限、非 colorized、非组摘要、频道非 MIN、OEM 额外条件。
- [ProgressStyle API](https://developer.android.com/reference/android/app/Notification.ProgressStyle)、[Builder API](https://developer.android.com/reference/android/app/Notification.Builder)、[NotificationManager API](https://developer.android.com/reference/android/app/NotificationManager)、[Settings API](https://developer.android.com/reference/android/provider/Settings)。指南与参考页对设置 action 命名有漂移，采用 reference/AOSP source 给出的 APP_NOTIFICATION_PROMOTION_SETTINGS，并解析后才启动。
- [FluidCapsule AospLiveUpdatePublisher](https://github.com/Venompool888/FluidCapsule/blob/528ac3c185a15512743af893ab0b76c028a6251f/app/src/main/java/io/github/venompool888/fluidcapsule/publisher/AospLiveUpdatePublisher.kt)，revision `528ac3c185a15512743af893ab0b76c028a6251f`。
- [deepseek-harness-mobile TaskMonitorService](https://github.com/Venompool888/deepseek-harness-mobile/blob/c6039fb195478d40cac38cfe41a9d60f1a07b378/app/src/main/java/cool/rin/deepseekremote/TaskMonitorService.kt)，revision `c6039fb195478d40cac38cfe41a9d60f1a07b378`。

只参考标准 API 使用方式，没有引入参考项目的后台监听/保活架构或复制其整个通知系统。

## 有效验证与边界

- `bash design-tools/live-update-check.sh`：29 行为断言，执行生产 publisher/capability/gate + 原 Job；测试 API35 未调用任何36方法、权限/频道/开关状态、ProgressStyle 对应真实 Job、原动作/时间戳保留、独立节流、系统拒绝时同通知降级、构造/提交故障不循环重试、后续任务重置。Android stand-ins 不是 SystemUI。
- `python3 design-tools/check-live-update.py`：版本、权限、调用链、启动 FGS 先于业务、Xiaomi → 标准顺序、独立开关/节流和 build target guards。
- Island characterization：6 文件、7 原通知方法（仅精确 catch 增量）、11 字段、原 Manifest 声明；9 mutation checks 和实际 bridge 的10个 host 场景。payload 六用例及原 gate guards 通过。
- API36 真实 android.jar 的资源链接、完整 Java 与 DEX 编译通过；没有为该补充功能重新下载模型、执行 native 推理或生成完整模型 APK。

**待 HyperOS / ColorOS 真机验证**：实际显示资格、展开/收起、弱权限条件、UI 在后台的持续展示与退出残留。这里不声称超级岛、流体云或 promoted chip 已在真实设备显示。

## 很短的真机清单

1. HyperOS：开启原超级岛与原 Shizuku 设置，转写/切后台；核对原首帧、约5秒更新、停止按钮和 FGS 通知仍在。
2. HyperOS：关闭/撤销 Shizuku 或使小米提交失败；确认无重复私有重试，Android16+ 尝试标准通知、旧系统普通通知，转写继续。
3. ColorOS16：允许通知和系统实时活动，启动 file local/cloud 各一项，观察流体云/状态芯片；关闭实时活动后普通 FGS 通知仍在。
4. 两种 ROM：成功、失败、取消、切后台、连续两任务；结束后两 ID 均消失，下一任务进度/动作正确，无旧残留。ColorOS14/15 只检查普通通知。
