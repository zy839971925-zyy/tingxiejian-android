# Architecture / 架构

The app uses native Android Views and a foreground `LocalService`. It needs neither WebView,
Termux nor a resident ADB connection at runtime. The release is arm64-v8a only.

## Flow

1. `MainActivity` opens Android's document picker and receives access to the selected URI.
2. `PcmDecoder`/`ModelPrep` prepare audio and private model files. `Transcriber` runs local
   sherpa-onnx speech recognition, punctuation and anonymous speaker clustering.
3. `Job` maps real work phases to stable, monotonic UI/notification state; phases without
   internal progress show elapsed time rather than fabricated percentages.
4. `History`, `TranscriptActivity`, `ChatActivity` and `Exporter` read/render/export results.
   Chat only calls `Cloud.java` after the user separately configures a provider/key.
5. `LocalService` posts a normal foreground notification. Optional island integration goes
   through `XiaomiIslandCapability` → `XiaomiIslandPayloadBuilder` → `XiaomiIslandPublisher`.
   The Shizuku bridge is opt-in, verifies read/write/restore of the narrow XMSF rule and falls
   back to ordinary notifications when unavailable; any SystemUI rendering decision is external.

## UI and privacy boundaries

- `WelcomeActivity` requests notification permission only after a user tap; it does not ask for
  microphone, all-files or Shizuku privileges automatically. Upgrade users can reopen it from
  Settings instead of being forced through first-run again.
- `UiTheme` handles dark mode; `PortalTransition` retains history source Views for the reverse
  shared-element path. `Motion` honors the platform animator scale.
- `Cloud.java` is the only module allowed to perform application-level HTTP requests. Do not add
  network calls to other classes or auto-enable cloud mode on first launch.
- Diagnostics may contain file names/device properties and are kept in app-private files. Never
  paste raw reports, recordings, keys or private history into public issues.

## Test layers

- `design-tools/check-all.sh`: pure logic, XML wiring, color, portal and network boundary.
- `design-tools/island-payload-check.sh`: tests exact FocusTemplate V3 structure against the
  shipped builder using a desktop JSON JAR.
- `build.sh`: verifies DEX class definitions, signature and that all nine model assets are STORED.
- Real device: startup, permission prompt, offline audio, speaker separation, font scaling,
  navigation return and actual status-bar island presentation. Static tests cannot replace this.
