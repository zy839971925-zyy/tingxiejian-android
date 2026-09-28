<p align="center"><strong>简体中文</strong> · <a href="UI-MOTION.en.md">English</a></p>

# 界面与动效决策（源码审查，非实机验收）

## 目标与边界

动效优先传递**来源、方向、状态与反馈**，不追求每次点按都炫技。高频选择文件、进度更新、键盘输入和消息滚动保持直接响应；偶发的首次引导与任务完成才有一次性强化反馈。系统文件选择器、通知权限弹窗、分享面板及设备的预测返回属于系统窗口，无法承诺由应用实现逐帧连续的“一镜到底”。

| 路径 | 进场 / 退出 | 降低动效或中断 |
| --- | --- | --- |
| 首次引导 → 首页 | 按钮中心圆形扩展为整页颜色，首页遮罩退场，内容依次出现 | 直接切换；首页遮罩超时清理，页面状态保留 |
| 引导分页 | 28dp 内定向位移，前页短退、后页缓入；终页图标仅弹动一次 | 立即换页；停止时复位透明度与位置 |
| 设置 → 引导 | 轻量窗口侧向进入，原方向退回 | 关闭动画时立即切换 |
| 首页 → 设置 / 全文 → 对话 | 原生共享元素容器扩展，内容稍后进入；返回反向收回到源控件 | 新路线直接切换；路线中途改变设置仍完成已启动的回程 |
| 首页四种状态 | 短退 + 稍延迟进入，模型准备/完成图标在卡片落定后反馈 | 取消上次动画并归一化所有面板；切换时不触发特效 |
| 第三方条款 | 正反向短淡入 / 淡出；确认框阻止误触 | 动画关闭立即完成操作 |
| 对话消息 | 仅新消息一次性进入；历史载入不重放 | 动画关闭立即显示 |

## 1.0.3 界面审查结论

| 审查项 | 处理 | 边界 |
| --- | --- | --- |
| 首页大字体与层级 | 固定 66dp 标题栏改为最小 66dp、随文字自然增长；固定操作区加浅色表面和细分界 | 仍需 200% 字体及小屏实机验收 |
| 说明文字 | 12sp 的 Caption / Meta 调整到 13sp；设置模型按钮、云配置输入及对话输入增大至至少 48dp | 触控宽度与实际高对比显示需设备测量 |
| 默认离线与可选云识别 | 首页从设置返回后同步配置；开启有效云端识别时说明录音将发送至已配置服务 | 各系统选择器由平台控制，不做伪造过渡 |
| 动效频率 | 首次引导与完成态保留一次性图标反馈；消息历史、进度数值和文本输入不加循环或字字跳动 | 低端机帧时间仍需录屏测量 |

## 实现选择

- 本项目是 **Java + Android Views + aapt/javac 原生构建**，不是网页。GSAP SplitText 属于 Web/DOM 生态：为几行标题加入 WebView 和 JavaScript 会增加启动、可访问性及维护成本。首次标题因此采用两个原生 TextView 的错峰进入，并作为一个完整标题播报，不拆中文字符、不循环播放。
- 现有 `PortalTransition`、`Motion` 和平台 `ViewAnimationUtils` 足以覆盖页面容器、一次性图标、快速状态变化；继续使用可取消的原生属性动画，避免叠加两套转场所有权。
- 调研候选：AndroidX DynamicAnimation 提供真正可追踪的弹簧；MotionLayout 擅长大型单页多状态场景；MaterialContainerTransform 提供 Material 容器变化；Lottie 适合品牌插画，GSAP 适合网页文字。当前工程已具备自绘图标及原生转场，添加这些依赖对现有 516MB 模型包和离线首帧并无确定收益，故没有为了库而引库。Rive 尚未验证于本工程的构建工具链，不能写成已集成。
- 动画总开关遵从 Android `ValueAnimator.areAnimatorsEnabled()`，额外提供“减少动效”用户设置，偏好保存在设备本地。

## 依据与限制

- Android 官方：[Activity 共享元素转场](https://developer.android.com/develop/ui/views/animations/transitions/start-activity)、[ValueAnimator.areAnimatorsEnabled](https://developer.android.com/reference/android/animation/ValueAnimator)、[MotionLayout](https://developer.android.com/develop/ui/views/animations/motionlayout)。
- 设计参考：[Apple Human Interface Guidelines · Motion](https://developer.apple.com/design/human-interface-guidelines/motion)、[Microsoft Fluent 2 · Motion](https://fluent2.microsoft.design/motion)（连续性、自然响应与适度时长）、[GSAP SplitText](https://gsap.com/docs/v3/Plugins/SplitText/)（完整可读标题）、项目内 `ui-animation` Skill。
- 静态/构建检查覆盖结构、路径和安装包打包，不证明机型实测。仍需在设备上分别以深/浅色、200% 字号、系统动画 0x、应用减少动效开/关，逐项测试按键/手势返回、连续点击、切后台、横竖屏与低端机帧时间；未运行前不声称“苹果级”观感或全部路径零闪烁。
