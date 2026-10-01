# UI 优化与 APK 交付

用户授权全 UI 自主优化，参考 Apple HIG；本轮继续现有 diff，不重写工程。Workflow Ultra 用于拓宽边界（手机/横屏/大字/键盘/减少动效/听写生命周期），独立审查关键交互。

视觉：保留品牌绿；石墨灰/系统灰分组背景，清晰文字层级、16sp正文、统一圆角与留白。原生 Views、vector图标、单次可中断反馈；不加入模糊/持续装饰动画，不迁移Compose/Gradle。

执行：修复重复播放器轮询/后台播放、返回后主题不同步、IME和横屏cutout/padding、减少动效press；结果工具区随正文滚动保留ListView虚拟化；听写主要内容滚动且结束按钮可达；按钮role/键盘焦点/已选状态；聊天草稿和状态反馈；相关检查后完整开发签名APK（含可用模型）；按最新指令暂缓所有 Shizuku 高级功能，已撤出只读诊断实现。

边界：Apple的44pt仅为设计参考，Android点击区域至少48dp；不用Apple字体/商标。保护通知和ASR业务链路、min26 target35 compile36。用户已要求“完全自己优化”，无需另行设计批准。

验证：现有UI/portal/wiring/notification/island检查，新增可复现motion/insets回归，API35模拟器截图及小屏/深浅/大字/横屏/导航（若软件模拟器可启动）；模型与OEM显示只留真机。最终APK签名/zipalign/model manifest/dex/resources校验。
