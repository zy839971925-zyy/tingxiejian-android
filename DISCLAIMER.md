# 免责声明 / Disclaimer

> 英文摘要见每节末尾。APK 内也随包附带 [`licenses/DISCLAIMER.txt`](licenses/DISCLAIMER.txt)。

## 1. 许可范围

仓库根目录的 [`LICENSE`](LICENSE)（MIT）**只覆盖本项目的原创源代码与文档**。
它不转授、不改变任何第三方库、Android SDK、模型权重或字体的许可。
第三方清单与各自条款见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

*The MIT license covers only original project source code and documentation. It
does not relicense third-party libraries, SDKs, model weights or fonts.*

## 2. APK 不是 MIT 产物

GitHub Release 中提供的 APK 是**二进制打包物**，其中包含第三方运行库（sherpa-onnx、
Kotlin、HiddenApiBypass、Shizuku API 等）与模型权重。APK 整体**不以 MIT 发布**，
各组件保留原始许可与版权声明。

*The released APK is a binary bundle; it is not MIT-licensed as a whole.*

## 3. 模型权重的已知状态

| 资产 | 已知条款 | 说明 |
| --- | --- | --- |
| Paraformer 中文模型 | Apache-2.0（上游 ModelScope 声明） | 随包提供，保留上游声明 |
| CT-Transformer 标点模型 | 上游 ModelScope Apache-2.0 | 转换产物，条款按上游 |
| pyannote segmentation 3.0 | MIT（CNRS） | 仓库内附 LICENSE |
| Streaming Chinese Zipformer (2025-06-30) | **再分发条款不明确** | 由 icefall multi_zh-hans 转换；上游模型需接受条件 |
| Speaker embedding (`embed.onnx`) | **来源与条款未核实** | 无法确认出处，故不作任何权利声明 |

上述"条款不明确/未核实"的权重以**原样**随 APK 提供，仅供个人使用与技术评估；本声明
**不授予任何再分发许可**。Release APK 内含全部模型，**无需联网下载**；首次转写前，
应用会弹出**阅读并确认**对话框列出上述组件与条款，勾选同意后才能开始。
若你是相关权利人并有异议，请开 Issue 联系，将删除对应资产。

*Unverified weights are provided AS IS for the original author's own use and
evaluation. No redistribution rights are granted. Rights holders may request
removal via an issue.*

## 4. 无担保

在适用法律允许的最大范围内，软件按"原样"提供，不附带任何明示或默示担保
（包括适销性、特定用途适用性与非侵权性）。作者不对任何直接或间接损失负责，
包括但不限于**数据或录音丢失、转写结果错误、应用崩溃、设备异常、耗电或发热**。

使用建议：重要录音请先自行备份；先导出转写结果再更新版本；本应用不保证在所有
机型、HyperOS/MIUI 版本与音频编码上都能正常工作。

*THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND. Back up
important recordings before use; the app is not guaranteed on every device.*

## 5. 可选功能的边界

- **云端转写**：需用户自行配置服务地址与密钥；启用后数据发往用户指定的第三方服务，
  内容与风险由用户自行承担。
- **Shizuku 超级岛**：完全可选，不影响离线转写核心；不同机型/OEM 版本可能受限或
  不可用。相关操作**不构成小米/HyperOS 官方授权**。若 OEM 防火墙链未启用，
  应用会选择安全回退普通通知，而不是改动影响其他应用的全局规则。
- **诊断与日志**：默认保存在应用私有目录，不自动发布到共享存储。

## 6. 联系方式

问题、缺陷报告与权利异议请通过
[GitHub Issues](https://github.com/zy839971925-zyy/tingxiejian-android/issues) 联系。
