# 04 — App 内实时听写

这不是系统级 IME，只做 App 内实时听写。

首页形成两个清晰入口：
- 实时听写
- 导入录音

## 权限

- 保持 microphone `required=false`；
- 只在用户第一次点击“实时听写”时请求 `RECORD_AUDIO`；
- 首次启动不索权；
- 拒绝权限不能崩；
- 第一版离开前台可安全暂停/结束，不为后台录音提前扩大 FGS 权限。

## 采集与线程

使用 `AudioRecord`：

`AudioRecord → bounded ring buffer/queue → ASR worker → UI state`

采集线程只做轻量 PCM 搬运；禁止在采集回调内跑大模型、LLM、重 IO 或复杂 UI。

必须正确处理 start/stop/release、mic unavailable、初始化失败、Activity 生命周期、异常中断。

## Stable Prefix

实时文字分为：
- committed/stable text
- unstable suffix

使用多次 partial 的稳定性/common-prefix 等确定性策略，避免整句持续回滚。

不要每字 pop；partial UI 做合理 throttle。

## 异步 Finalizer

一句 VAD segment 结束后异步 finalization；用户可以继续说下一句。

Finalizer 不能阻塞 capture/streaming。

使用 bounded finalization queue；设备过载时优先：
1. 不丢音频；
2. 保持 streaming；
3. finalization 可延迟。
