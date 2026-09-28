# Changelog

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

## Unreleased — source audit hardening (not a new tested APK)

- Persist finished results in the service, use atomic unique history IDs and block overlapping
  imports/services; retain progress metadata across Activity recreation.
- Recover both document exports across picker recreation, invalidate chat replies after Clear,
  validate cloud URL host/redirects, keep diagnostics private with an explicit view and audio
  cache-clear control, and respect keyboard insets.
- Skip the optional Shizuku firewall experiment when its shared chain is disabled; do not enable
  global OEM rules affecting other apps. This may change island behavior on some devices.
- Pin Maven dependency hashes, bundle full license notices, extend export/safety checks and record
  review scope in [docs/REVIEW-2026-09-28.md](docs/REVIEW-2026-09-28.md).

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
