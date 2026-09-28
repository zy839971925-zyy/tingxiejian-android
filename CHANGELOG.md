# Changelog

## 1.0.3 — 首次引导、动效与界面易用性

- 四页原生引导与欢迎页：两行标题依次出现，开始按钮展开至首页；返回路径和动画打断后复位。
- 首页、设置、全文与对话的双向转场协调；设置新增“减少动效”，尊重系统动画开关。
- 优化大字体下首页栏高度、正文与说明文字可读性、底部操作区分隔及设置/对话触控区域。
- 云端识别启用时首页明确标明录音会发送至已配置的服务；从设置返回后即时同步偏好。
- 清空问答记录前确认，设置页版本读取安装包信息。
- 合入前期源码审查加固：结果持久化、并发导入隔离、导出恢复、聊天回调失效、云端 URL 校验、
  私有诊断与 Shizuku 门禁安全回退，并补充打包及回归检查。
- GitHub Release 单独提供 ARM64 完整 APK；流式 Zipformer 与 `embed.onnx` 的再分发权尚未核实，
  下载前请阅读 Release 免责声明和第三方清单；用户确认条款不等于项目获得模型再分发许可。

## 1.0.2 — 超级岛兼容恢复与条款面板

- **修复超级岛不显示**：完全恢复 V1.0 的超级岛兼容路径（含按需启用 OEM 防火墙链的实验方式），
  修正 1.0.1 中授权成功后超级岛完全不再显示的回归。
- **条款面板改为卡片式**：「阅读并确认」使用与应用一致的视觉（眉题、阅读区、勾选解锁主按钮）与弹性入场；
  勾选确认后才能开始转写，其余功能不受影响。
- **引导页更新**：增加品牌波形标记与「04 · 使用条款」卡片，可随时阅读完整条款；卡片依次入场。

## 1.0.1 — 审查加固与第三方条款确认

- 首次转写前增加**阅读并确认**门：列出全部第三方库与模型条款，勾选同意后才能开始；
  全部模型预先打包在 APK 内，无需联网下载。
- APK 内附 `assets/licenses/DISCLAIMER.txt`，仓库新增 [DISCLAIMER.md](DISCLAIMER.md)；
  声明模型权重仅供个人使用、不授予再分发权，权利人异议可移除。
- 源码审查加固：结果先持久化再广播、原子历史写入、并发导入隔离、导出重建恢复、
  云端 URL 校验、诊断私有化、可选 Shizuku 防火墙链安全回退、Maven 依赖哈希校验。

## 1.0 — local release

- Native first-use permission guide (skippable; available again in Settings).
- More spacious transcript action grid, with centered icon/label groups and larger targets.
- ARM64 offline ASR, punctuation and anonymous speaker separation; optional cloud AI and
  optional HyperOS island path.
- This open-source snapshot replaces the proprietary MiSans source font with the Android system
  font, documents binary licensing limitations and separates private build inputs from source.

## 0.17

- Fixed missing Shizuku `aidl` and `shared` transitive classes in APK; added a DEX definition test.

## 0.16

- Added opt-in Shizuku island bridge with restore guard; repaired shared-element return mapping,
  history View stability and small interaction animations.

Earlier experiments are not preserved as separate public releases. See project history in private
backups if you own them; do not republish APKs or bundled model weights without rights review.
