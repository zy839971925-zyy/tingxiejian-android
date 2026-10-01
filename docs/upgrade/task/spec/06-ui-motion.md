# 06 — UI / Motion

保留 Java + Android Views，不迁移 Compose/Kotlin，不重做整个视觉语言。

基于现有 `Motion`、`PortalTransition`、`WaveView`、`RingView`、shared element 升级。

目标：统一 motion system，强调来源、方向、连续性和状态反馈，而不是到处加动画。

## Motion Spec

统一管理：
- duration/tokens
- spring/physics 场景
- deterministic fade/crossfade 场景
- enter/exit
- reduced motion

真正需要“弹性”的交互可用 physics-based animation；纯文本/淡入不必都 spring。

## 实时听写

首页入口自然展开为 listening surface：
- mic/listening orb
- 真实 PCM energy/RMS 驱动波形（不做随机假波形）
- timer
- stable + unstable text
- pause/stop
- finalizing state

文本：
- 不逐字 pop；
- unstable suffix 自然更新；
- sentence commit 只做轻微 alpha/color；
- finalizer 只对变化区域轻微 crossfade；
- 完成后 listening surface 自然转为 transcript/result。

## Predictive Back

评估 AndroidX progress-driven predictive back / transition seeking。
如果当前构建链能以合理成本稳定接入，则实现；如果需要大面积替换稳定 PortalTransition，则保留现有方案并记录后续工作，不能为了它破坏整体。

## Reduced Motion / Haptic

当前二者不应绑定：减少动效不应自动关闭触觉。
拆为独立 preference。

TalkBack 不持续朗读 partial，只在稳定提交或用户操作时合理反馈。
