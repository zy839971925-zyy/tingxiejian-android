# 02 — 严重工程问题

这些问题优先于视觉 polish。

## Release signing

当前正式 APK 不能继续依赖 `build/` 下自动生成的签名身份。

目标：
- dev/debug 可以自动生成独立临时 key，并明确是 dev build；
- release 必须显式提供长期 release keystore，缺失 fail closed；
- 不把 key/password 提交 Git；
- 能固定/校验 release certificate SHA-256 更好；
- README/BUILDING 同步。

## Cloud secret

API Key 不再以普通 SharedPreferences 明文长期保存。

目标：
- Android Keystore + authenticated encryption（如 AES-GCM）；
- provider/baseUrl/model 等非秘密仍可普通保存；
- 旧明文 key 安全迁移，成功后删除旧值；失败不能丢 key；
- 清空配置同时清 secret；
- 不要每输入一个字符就持续持久化 secret。

## Foreground service / lifecycle

针对 targetSdk 35 重新核对官方规则：
- 当前文件转写 FGS 类型与 timeout；
- 必须处理 timeout/cancel/stopSelf，不能留下假 RUNNING；
- session 状态真实反映 INTERRUPTED/FAILED。

不要为了实时听写提前扩大后台录音权限；第一版实时听写只要求 App 前台工作。

## Diarization 内存

当前长录音整段 `float[]` 可能占用巨大 Java heap。

至少：
- memory preflight；
- 内存不足安全跳过分人、保留文字；
- 如果当前 sherpa API 合适，再研究 chunk/sliding diarization；
- 不把 OOM catch 当资源管理。
