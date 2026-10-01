# 1.0.4 真机反馈修复

范围：只处理用户报告的模型选择/准备入口、引导跳到应用设置、拉伸椭圆按钮，以及按设备区分实时进度名称。Workflow Ultra 重点核实“缺少模型”与“已内置但状态不清”这两个不同解释；不扩大设置动效、Shizuku 能力或 OEM 发布实现。

证据：已交付 1.0.3 APK 中有完整 Qwen3-ASR 0.6B int8 的 6 个文件，共 987015347 字节。首次核心模型准备不包含可选 Qwen 包；Qwen 在复核时按需提取，默认 local_finalizer=qwen3。旧页面未展示内置/提取状态且导入按钮更显眼，模型选择行没有方向提示。其导入按钮使用 bg_circle_soft 的 oval，在全宽布局中拉伸。首次引导直接构造 SettingsActivity Intent，确实越出了引导。

修改：模型行显示“当前模型 / 点击切换”，弹出单选列表。另列 Qwen 内置/已准备/缺失状态，提供无需下载的内置准备及节流进度，导入仍为可选。共用已有原子 ModelInstaller；损坏导入保留旧代。保留至少 3 GiB 可用系统内存守卫，文件已准备不等于已完成原生加载或实际采用 Qwen。

首次引导使用单独 RealtimeSetupDialog。通知授权经用户点击触发；Shizuku 兼容方式独立征求同意并调用现有授权入口，系统授权弹窗结束仍在引导。未启动 Shizuku 时只经另一个明确按钮打开其管理器。关闭/返回/旋转不进入应用设置，页面编号保留。Shizuku 监听仅在小米弹窗显示期间注册，关闭移除。系统权限页是用户选择管理权限时的系统界面，不是应用设置页。

RealtimeDeviceProfile 只决定文案，不决定 OEM 能力。小米/Redmi/POCO 显示超级岛，仍用原 capability 检测；OPPO 显示 ColorOS 流体云并说明 Android 16 标准通道/系统自行决定呈现，低版本只有普通通知。其它设备显示 Android 原生通知；不把 OnePlus/realme 的品牌推定为 ColorOS ROM。非小米设置隐藏整组小米/Shizuku 控件，不运行相关探测。标准开关沿用 live_updates；小米开关仍是 island / island_shizuku，没有互相授权。

按钮改为带 ripple 的圆角矩形，换行可增长，至少 52dp 点击高度。不改其它用途正确的圆形图标控件。

验证：新布局/导航检查先出现 4 个预期失败，修复后通过；设备文案/版本界限 19 个 JVM 断言；现有 ModelInstaller/ModelManager 检查含内置、未准备、缺失、导入失败、真实字节进度与不阻塞 UI 的场景；wiring、首次引导、视觉、Portal、motion、Live Update 和 Island characterization 通过。完整构建/签名/zipalign/模型清单与 DEX 校验通过，日志见 /workspace/android-dev/logs/feedback-v104-build.log。没有宣称真机引导已验收，也没有重新运行模型推理或重型全项目测试。

待真机：
1. 设置→首次使用与权限指南→第 3 页；配置只弹窗，返回/拒绝/允许授权后仍在原引导页，跳过可进入主页。
2. 模型行可切换 Qwen/Paraformer，重进设置保持选择；Qwen 明确显示已内置，准备完成后可录音检查实际采用模型/内存不足回退提示。
3. HyperOS：原超级岛与 Shizuku 授权/拒绝/未启动路径；停止、完成和连续任务无残留，普通前台通知保持。
4. ColorOS：16 的标准实时活动权限开/关与普通通知回退；14/15 仅普通通知，无 Shizuku 控件。均待 HyperOS / ColorOS 真机验证。
