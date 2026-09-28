<p align="center"><a href="CHANGELOG.md">简体中文</a> · <strong>English</strong></p>

# Changelog

> Historical versions below. **Post-release documentation commits do not change the v1.0.3 tag or APK.**

## Documentation updates after release (main branch only)

- Added linked Chinese and English guides across the README, architecture, build, licensing and security docs;
  clarified user steps and privacy boundaries.
- Acknowledged GPT-6 Sol assistance and development/build work on an Android phone with Aether.

## 1.0.3 — first-run flow, motion and UI usability

- Four native guide pages ending in Welcome: staggered two-line title and a start button that expands into
  the home screen, with back navigation and interruption cleanup.
- Coordinated bidirectional transitions among Home, Settings, Transcript and Chat. Added **Reduce motion**
  and respected system animation settings.
- Improved home header for large fonts, supporting-text contrast, separation of the bottom action area,
  and touch targets in Settings and Chat.
- When cloud recognition is configured and enabled, home clearly warns that audio is sent to the chosen service;
  settings changes sync immediately upon return.
- Added confirmation before clearing chat and read the installed package version in Settings.
- Incorporated source-review hardening: durable results, concurrent-import isolation, export restoration,
  invalidation of stale chat callbacks, cloud URL checks, private diagnostics and safe Shizuku gate fallback,
  plus packaging/regression checks.
- Published a separate full ARM64 APK in a GitHub Release. Redistribution rights for streaming Zipformer and
  `embed.onnx` remain unverified: read release notices before downloading. User acceptance of app terms
  does not grant the project model redistribution rights.

## 1.0.2 — island compatibility recovery and terms panel

- Restored the v1.0 island compatibility route, including its experimental on-demand OEM firewall-chain
  activation. Fixed the 1.0.1 regression where the island no longer appeared after authorization.
  **This is historical behavior; v1.0.3 instead prioritizes a safe fallback when the chain is disabled.**
- Turned **Read and confirm** into a visual card with a heading, scrollable terms and a checkbox-unlocked
  main button. Confirmation is required for transcription, not for unrelated features.
- Updated the guide with a brand waveform and a **04 · Terms** card that links to full terms;
  cards enter in sequence.

## 1.0.1 — source review and third-party terms

- Added the **read-and-confirm** gate before first transcription, listing third-party library/model terms.
  Models are prepackaged in the APK, without a first-run model download.
- Bundled `assets/licenses/DISCLAIMER.txt` and added [DISCLAIMER.en.md](DISCLAIMER.en.md):
  unverified weights are described for personal use, with no redistribution right granted and a removal
  route for rights holders. **This wording does not establish publisher redistribution permission.**
- Hardened save-before-broadcast, atomic history, concurrent imports, export after recreation, cloud URL
  validation, private diagnostics, optional Shizuku firewall fallback and Maven hash checks.

## 1.0 — local release

- Added a native, skippable first-use permission guide, available later from Settings.
- Enlarged and spaced the transcript action grid, with centered icon/label groups and larger targets.
- ARM64 offline recognition, punctuation and anonymous speaker grouping, plus optional cloud AI and
  optional HyperOS island integration.
- Replaced the proprietary MiSans source font with Android's system font, documented binary licensing
  limits and separated private build inputs from public source.

## 0.17

- Fixed missing Shizuku `aidl` / `shared` transitive classes in the APK; added a DEX definition check.

## 0.16

- Added an opt-in Shizuku island bridge with a restore guard; fixed shared-element return mapping,
  history View stability and small interaction animations.

Earlier experiments are not preserved as separate public releases. Private backups may contain history
if you own them; do not republish APKs or bundled model weights without a rights review.
