# UI、动画与 APK 交付

2026-10-01。按用户 Workflow Ultra 完成代码问题建模、官方规范核对、独立只读审查和定向验证；用户最终要求先交付动画/bug修复 APK，Shizuku 高级功能暂缓。所有此前 ASR/通知改动保留；未重写工程。

## 修改

- 保留品牌绿，统一石墨文字、浅灰分组背景和深色调色板；28/19/16/13sp层级，清晰卡片、原生 ripple/vector图标。
- 原生按钮/Portal转场沿用现有阻尼曲线；提供自然/轻快节奏，减少动效与系统动画关闭始终优先。RMS驱动听写反馈平滑跟随，没有闲置脉冲/持续装饰动画。
- 修复减少动效仍缩放、禁用按钮缩放、ViewPropertyAnimator复用stagger延迟，以及历史行入场中途点击后透明/偏移残留；点击、键盘和辅助功能打开转场都规范化来源状态。
- 系统栏/IME适配保留原留白且不累积，补横屏cutout；控制器在decor attach后重申深浅图标。深色波形使用主题token。
- 结果工具区成为可滚动ListView header，保留虚拟化和原header返回映射；听写内容区整体滚动、结束控件独立，返回改为带描述的48dp图标；时间列可扩展。
- 自定义按钮有键盘焦点与Button无障碍role；选项表达selected状态。聊天泡泡可选中文本，窗口内宽度适应分屏；等待中禁用发送/快捷问题，已有草稿不会被快捷问题覆盖。
- 修复主页导出SRT/JSON互换：共享Exporter.Format，保留旧持久化kind语义，统一菜单/MIME/后缀/实际内容；结果操作补成功/失败反馈。播放器prepareAsync有实例守卫，使用单一ticker，暂停/完成/后台/销毁清理。返回主页时主题同步。
- 当前稿支持关键词筛选与高亮（literal、大小写不敏感、保留原文），只影响显示；复制/导出仍使用完整所选稿。
- 不增加Shizuku高级功能、后台组件或权限；小米受保护代码未修改。

## 资料与依赖取舍

查阅 [Apple motion](https://developer.apple.com/design/human-interface-guidelines/motion)、[accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)、[layout](https://developer.apple.com/design/human-interface-guidelines/layout)，原始JSON保存在环境research目录。遵循短而准确的反馈、空间连续性、大字体、减少动效和对比度；Android触控区域至少48dp，未复制Apple字体/商标。

核对用户指定的 [motion-web](https://github.com/feitangyuan/motion-web)：它是Three.js/Canvas/WebGL/CSS的Web技能与案例，仓库标注CC BY-NC 4.0。仅参考编排思路，未复制其代码/资产或将WebGL嵌入原生产品。核对 [Android spring](https://developer.android.com/develop/ui/views/animations/spring-animation)、AndroidX DynamicAnimation、Material motion以及[Lottie](https://github.com/airbnb/lottie-android)：本应用现有SpringCurve/RingView/Portal已经有阻尼与可中断原生实现，此轮没有添加新AAR、资源merge或迁移Compose/Gradle。

[UI/UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) 已安装到用户本地skill目录，固定研究revision `09170eec67eefd46a7ae85de61b40c194020f997`。使用其减少动效/中断/触控/原生无障碍建议；数据库未提供匹配的原生Views实现规则，平台实现以Android官方API与本仓库为准。

## 实际验证

- ui-motion-check：11行为断言（减少/禁用/系统关闭、延迟与中断恢复、安全区/IME/旧版守卫）；原始Motion/UiTheme执行同一回归失败，证明测试能捕获原bug。host stand-ins不冒充设备。
- 导出菜单/保存kind/MIME/后缀/实际TXT-SRT-JSON内容；关键词literal/Unicode/空输入等行为通过。
- 现有DictationActivity事件/权限生命周期、export、wiring/first-run/visual/portal与git diff检查通过；两种主题小文字与主要按钮对比度检查通过。
- Live Update29行为与版本/生命周期/设置调用链通过；Island6完整文件、7方法/11字段和mutation/bridge保护检查通过。
- 独立审查发现导出对调、播放器轮询、主题回返、草稿覆盖及入场中断问题；修复后只读复核未发现新增P1，另一个独立Java fixture复现并确认入场中断恢复。
- 完整APK真实aapt2+Java+DEX+打包签名校验；min26/target35/compile36。完整包包含13个存储式模型asset、arm64库、资源与DEX；不是无模型预览包。签名为独立开发身份，不能覆盖其他证书签名安装。

## 设备证据边界

尝试已有API35 x86_64软件模拟器，启动并使用无模型开发预览包与合成历史fixture，仅取得首次引导截图；随后Google Play services ANR、睡眠黑屏与截屏超时，使首页/深色/小屏/横屏截图和帧率证据不足。最终完整APK没有做模型推理或真实设备安装验收。不得称为全页面视觉验收/60fps；HyperOS/ColorOS呈现仍待真机。

真机短清单：深浅主题回返；连续点击历史/返回；自然/轻快/减少动效+系统动画0；暂停/播放/切后台；TXT/SRT/JSON分别导出；小屏/字体2×/横屏/聊天键盘；听写暂停、结束、离页；原超级岛及标准通知fallback。

## 交付文件

- APK：`dist/tingxiejian-v1.0.3-arm64-dev.apk`，1486294291 bytes（约1.49GB，内置Qwen等模型）。
- SHA-256：`c7a1437c9bff2f0ec6c66b8c2f756ca3daefc6b36b4c8c3e9b354a3f41c286d4`。校验文件在相邻 `.apk.sha256`。
- Android 8.0+，arm64-v8a，开发签名；与现有安装证书不同则不能覆盖，先备份历史。完整机器记录见 `apk-receipt.json`。
