# Session persistence and foreground-service lifecycle

## Scope and model

Task admission: Action / Project lane, depth `ultra` inherited from the task package, autonomy `B` (full local execution already authorized). The material uncertainties were Android 15's `dataSync` timeout contract, process-death recovery, cancellation versus late completion, and preservation of the verified Island notification path. This branch applied the Reasoning Workflow's Execution Control and Audit / Verification families, with `synchronization-and-recovery.md` for durable state and concurrent ownership. Independent final review is integrated by the root executor; no device result is inferred from host checks.

`SessionState` is an immutable pure Java record. Active states advance in order: `RUNNING → FINALIZING → POLISHING`, with optional phases skippable. Any active state can reach `DONE`, `CANCELLED`, `INTERRUPTED`, or `FAILED`. All terminal states are absorbing, including attempts to replace cancellation with a late successful callback. Older progress cannot move a later phase backward.

`SessionLedger` holds the tested state/persistence logic. It publishes a new in-memory snapshot only after its storage write succeeds. The codec is versioned and rejects truncated, duplicate, unknown-version/state, oversized, or trailing data. The ledger retains at most 256 sessions, pruning older terminal entries and retaining active ones. Session records contain identity, kind, name, message, and creation/update timestamps; no audio, transcript, or credential is stored there.

`SessionRepository` serializes every read/write with one process-wide lock and uses Android `AtomicFile` at app-private `files/sessions-v1.bin`. Reads are bounded to 1 MiB. Missing storage begins empty; damaged or unreadable existing storage fails visibly rather than silently fabricating a fresh `RUNNING` state. On application startup, `TingxiejianApp` synchronously turns all stale active stages into `INTERRUPTED` before activities or service workers can start. Neural inference is not resumed.

## Shared API

All methods are public static and throw `IOException` for storage problems:

```java
String begin(Context context, String kind, String name);
boolean transition(Context context, String id, SessionState.State state, String message);
SessionState get(Context context, String id);
SessionState latest(Context context);
int recoverInterrupted(Context context);
```

`begin` returns a unique durable session ID. `transition` returns false for an unknown ID, a terminal session, a backward phase, or an identical state/message. `get` returns null for an unknown ID; `latest` returns the most recently begun session. `recoverInterrupted` is intended for Application startup, not during an active process. File transcription uses kind `file`; the live controller owns its own kind and uses the same repository.

## LocalService lifecycle

- File and live entry acquisition share the `DictationController.class` monitor. The file service rejects an active live session with a visible message and reserves its busy slot before writing metadata.
- Explicit cancel/stop/task removal interrupts the worker, persists `CANCELLED`, clears stale Bus progress, removes notifications, and stops the service. Cancellation is checked at callbacks, decoded cloud buffer boundaries, and before/after cloud requests. `CancelledException` extends `InterruptedIOException` so optional polishing cannot swallow cancellation as a recoverable API failure.
- `onTimeout(int startId, int fgsType)` marks cancellation and requests `stopSelf()` **before notification cleanup or disk persistence**. `INTERRUPTED` is persisted on a separate thread, without waiting for native inference. Destruction interrupts the worker, removes Handler callbacks, clears static ownership/busy/running, and attempts interruption persistence. Process-start recovery covers death before that write completes.
- Callbacks from a destroyed/timed-out/cancelled worker cannot publish a result. A result is saved to History outside the publication lock; cancellation during that save deletes the unpublished entry. An accepted `DONE` transition precedes result publication, and History always precedes the Bus result. A completion that has already committed is terminal and cannot be rewritten by a later cancel.
- Returning without a result becomes `FAILED`. A refused foreground start does not continue an unprotected background job. Empty or null starts stop instead of creating an idle `dataSync` foreground service. Successful completion retains the original four-second linger.
- Native calls and an already-running HTTP request are cooperatively cancelled; interrupt does not claim instantaneous native preemption. Late return is checked before publication. Android/OEM behavior still needs the device checks below.

`savedResult` copies the pipeline event before adding legacy History fields, preserving raw/final/polished text and segment layers, stable IDs, model metadata, and hotwords. Transport-only notification fields are removed. The faithful `text` remains unchanged.

Cloud audio transcription now calls `TranscriptPolisher.polishSegment` automatically after each ASR chunk when text cloud configuration is present. The pure `CloudTranscript` accumulator gives each chunk a session-scoped ID, preserves `raw_text`, `original_text`, `final_text`, and faithful `text`, and keeps `polished_text` separate. It passes a copy to the polish callback, carries configured hotwords/models into History, and reports partial/fallback edits honestly. Polishing failure keeps the ASR text; cancellation propagates. Missing text cloud configuration skips the callback and gives an explicit no-key warning. Global session phase advances only on explicit `state` events, so background per-segment finalizer loading or polishing does not prematurely move an ongoing input into `FINALIZING`/`POLISHING`.

## Official Android evidence

Fetched directly from Android Developers on 2026-10-01:

- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types): `dataSync` includes import/export and local file processing. The existing service type and protected Manifest declaration remain unchanged by this branch.
- [Foreground service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout): Android 15 permits a total six hours of `dataSync` foreground work in a 24-hour period; the service receives `onTimeout(int,int)` and has only a few seconds to call `stopSelf()`.
- [Service.onTimeout reference](https://developer.android.com/reference/android/app/Service#onTimeout(int,int)): the new callback is API 35, provides a short grace period, and must stop/demote the service to avoid a platform failure.

The existing `vendor/android.jar` contains both timeout overloads; the full application compiled with the API 35 overload. This branch does not add microphone/background recording permissions or change the service's Manifest type.

## Verification

Behavioral tests were created with failing implementations first:

1. State RED: `RUNNING advances to FINALIZING` failed; GREEN: 41 assertions.
2. Ledger RED: `begin exposes persisted RUNNING` failed; GREEN: 24 assertions, including process death in all three active phases, terminal absorption, failed write/begin rollback, cross-thread late completion, corruption rejection, and bounded retention.
3. Cloud chunk RED: `segment has stable session identity` failed; GREEN: 19 assertions, including no-key/no-call, immutable faithful text under callback/output mutation, API fallback, partial polish, metadata, and interruption without committing the cancelled chunk.
4. The actual `SessionRepository` adapter passed 17 filesystem assertions, including eight concurrent writers, durable rollback, backup restoration, and fail-closed corruption handling. These use test-only Android Context/AtomicFile stand-ins; they verify adapter wiring and do not claim to execute Android's internal AtomicFile implementation.

Run: `bash design-tools/session-check.sh` (101 assertions total; existing desktop JSON jar and polish filesystem stubs required).

Fresh aapt2 resource linking and full javac compilation passed after integration. The existing seven Shizuku annotation warnings and deprecated-API notes remain. Island payload checks, Island gates, audit guards, cloud guards, and `design-tools/island-characterization.py` passed. The characterization check confirms six complete protected files, seven LocalService notification methods, eleven protected fields, and protected parsed Manifest declarations.

Exactly unchanged method bodies: `createChannel`, `notify`, `ensureForeground`, both `buildNotification` overloads, `stopActionUri`, and `updateNotice`. Channel IDs remain `transcribe_quiet_v2` / `transcribe_island_v1`; notification IDs remain 11 / 12; the fixed notification timestamp, five-second throttle, Island first-frame flags, and `DONE_LINGER_MS=4000` remain intact. No Shizuku/Xiaomi protected source or Manifest was edited by this branch. Lifecycle cancellation necessarily now stops immediately, instead of waiting for a cooperative callback followed by linger.

## Device checks still required

No phone/emulator lifecycle, native model, or HyperOS rendering result was produced in this environment.

- Android 15: record the existing `activity_manager/data_sync_fgs_timeout_duration`, temporarily shorten it with the documented `adb shell device_config put activity_manager data_sync_fgs_timeout_duration 5000`, start file transcription from the visible UI, and background it. Confirm service exit within the grace period, persisted `INTERRUPTED`, no late success/notification, and no `RemoteServiceException`; restore the prior device config afterward.
- Start file transcription, terminate the process, relaunch, and confirm a visible interrupted session with no false busy/running state. Re-run during finalization and text polishing.
- Cancel during decode, native finalization, cloud ASR, cloud polishing, and History save. Confirm late callbacks cannot become `DONE` or publish a result, then verify a new file/live session can start.
- Repeat successful/cancelled jobs on the previously verified HyperOS device and check first-frame opening, stable notification rank, 4-second success linger, and final notification removal. Host preservation is not proof of OEM presentation.
