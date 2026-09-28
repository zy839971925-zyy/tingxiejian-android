<p align="center"><strong>简体中文</strong> · <a href="CONTRIBUTING.md">English</a></p>

# 参与贡献

感谢帮助完善听写间。改动越聚焦，越容易核对与验证。

1. 先开 Issue，说明机型／Android API 版本、预期行为和**脱敏后的**复现路径。
   不要上传原始录音、完整转写、API Key 或未经处理的私有诊断文件。
2. 保障**无 Shizuku、无云端配置、无网络**时仍可本地转写。应用发起的 HTTP 请求应集中在 `Cloud.java`。
3. 优先为修复添加能在修复前失败、修复后通过的回归检查；提交前运行
   `bash design-tools/check-all.sh`。若已合法取得构建依赖／模型，也请运行 `bash build.sh`
   并在 ARM64 真机上测试。构建通过**不等于**设备验收。
4. 保留现有的 `need(id)` 检查、异步失败保护、真实进度语义和可选通知的回退。
   动效须尊重系统禁用动画与大字体；参见[界面动效文档](docs/UI-MOTION.md)。
5. 只提交可按 MIT 授权的**原创工作**；改编 Apache-2.0 代码时保留署名及其条款。
   不要将模型权重、MiSans、SDK 二进制、发行 APK、签名材料或 vendor 库加入 Git 历史。
   请先阅读[第三方清单](THIRD_PARTY_NOTICES.zh-CN.md)。

提交贡献表示同意以本项目的 MIT 条款提供你的原创部分；明确标注的上游改编部分仍遵循其原许可。
如需中英双语文档，请同时更新对应语言版本或在 PR 中注明尚待翻译之处。
