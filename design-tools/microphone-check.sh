#!/usr/bin/env bash
# Host behavior checks: desktop javac/java only; Android audio is a controlled test stand-in.
set -euo pipefail
cd "$(dirname "$0")/.."
MICROPHONE_CHECK_OUT=$(mktemp -d)
trap 'rm -rf "$MICROPHONE_CHECK_OUT"' EXIT
compile_microphone() {
  local microphone_source=$1
  local queue_source=$2
  local classes=$3
  mkdir -p "$classes"
  javac --release 8 -Xlint:-options -encoding UTF-8 -d "$classes" \
    design-tools/microphone-stubs/android/media/AudioFormat.java \
    design-tools/microphone-stubs/android/media/MediaRecorder.java \
    design-tools/microphone-stubs/android/media/AudioRecord.java \
    design-tools/microphone-stubs/com/example/tingxiejian/PcmDecoder.java \
    src/com/example/tingxiejian/AudioSource.java \
    "$queue_source" "$microphone_source" \
    design-tools/MicrophoneCheck.java
}
compile_microphone src/com/example/tingxiejian/MicrophoneAudioSource.java \
  src/com/example/tingxiejian/BoundedAudioQueue.java "$MICROPHONE_CHECK_OUT/actual"
java -cp "$MICROPHONE_CHECK_OUT/actual" com.example.tingxiejian.MicrophoneCheck
python3 - "$MICROPHONE_CHECK_OUT" <<'PY'
from pathlib import Path
import sys
out = Path(sys.argv[1])
source = Path('src/com/example/tingxiejian/MicrophoneAudioSource.java').read_text()
needle = 'if(n<=0&&stopped.get())break;'
if source.count(needle) != 1:
    raise SystemExit('Stop-tail mutation anchor must match the current source once')
(out / 'MicrophoneAudioSource.java').write_text(source.replace(needle, 'if(stopped.get())break;'))
needle = 'synchronized(lifecycleLock){if(stopped.get())return;recorder=audio;audio.startRecording();}'
if source.count(needle) != 1:
    raise SystemExit('Constructor-stop mutation anchor must match the current source once')
start = out / 'constructor-mutation'
start.mkdir()
(start / 'MicrophoneAudioSource.java').write_text(source.replace(needle,
    'synchronized(lifecycleLock){recorder=audio;audio.startRecording();}'))
queue = Path('src/com/example/tingxiejian/BoundedAudioQueue.java').read_text()
needle = 'tail=data;return false;'
if queue.count(needle) != 1:
    raise SystemExit('Queue-tail mutation anchor must match the current source once')
(out / 'BoundedAudioQueue.java').write_text(queue.replace(needle, 'return false;'))
PY
compile_microphone "$MICROPHONE_CHECK_OUT/MicrophoneAudioSource.java" \
  src/com/example/tingxiejian/BoundedAudioQueue.java "$MICROPHONE_CHECK_OUT/stop-mutation"
if java -cp "$MICROPHONE_CHECK_OUT/stop-mutation" com.example.tingxiejian.MicrophoneCheck \
    --stop-tail >"$MICROPHONE_CHECK_OUT/stop-mutation.log" 2>&1; then
  cat "$MICROPHONE_CHECK_OUT/stop-mutation.log"
  echo 'FAIL: old stop/read race escaped the regression check' >&2
  exit 1
fi
python3 - "$MICROPHONE_CHECK_OUT/stop-mutation.log" <<'PY'
from pathlib import Path
import sys
assert 'last positive read is retained after concurrent stop' in Path(sys.argv[1]).read_text()
PY
compile_microphone src/com/example/tingxiejian/MicrophoneAudioSource.java \
  "$MICROPHONE_CHECK_OUT/BoundedAudioQueue.java" "$MICROPHONE_CHECK_OUT/queue-mutation"
if java -cp "$MICROPHONE_CHECK_OUT/queue-mutation" com.example.tingxiejian.MicrophoneCheck \
    --overload >"$MICROPHONE_CHECK_OUT/queue-mutation.log" 2>&1; then
  cat "$MICROPHONE_CHECK_OUT/queue-mutation.log"
  echo 'FAIL: lost overload tail escaped the regression check' >&2
  exit 1
fi
python3 - "$MICROPHONE_CHECK_OUT/queue-mutation.log" <<'PY'
from pathlib import Path
import sys
assert 'overload retains every already-read frame including tail' in Path(sys.argv[1]).read_text()
PY
compile_microphone "$MICROPHONE_CHECK_OUT/constructor-mutation/MicrophoneAudioSource.java" \
  src/com/example/tingxiejian/BoundedAudioQueue.java "$MICROPHONE_CHECK_OUT/start-mutation"
if java -cp "$MICROPHONE_CHECK_OUT/start-mutation" com.example.tingxiejian.MicrophoneCheck \
    --constructor-stop >"$MICROPHONE_CHECK_OUT/start-mutation.log" 2>&1; then
  cat "$MICROPHONE_CHECK_OUT/start-mutation.log"
  echo 'FAIL: native start after lifecycle stop escaped the regression check' >&2
  exit 1
fi
python3 - "$MICROPHONE_CHECK_OUT/start-mutation.log" <<'PY'
from pathlib import Path
import sys
assert 'stop during native constructor prevents a later startRecording' in Path(sys.argv[1]).read_text()
PY
echo 'PASS: mutation sensitivity (stop/read tail, overload tail, stop during constructor all rejected)'
