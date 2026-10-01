#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=build/pipeline-check
mkdir -p "$out"
javac --release 8 -encoding UTF-8 -d "$out" \
  src/com/example/tingxiejian/{StableTextTracker,TextNormalizer,VadEngine,RecognitionPipeline,SegmentPolishQueue,BoundedAudioQueue}.java \
  design-tools/{PipelineLogicCheck,PolishQueueCheck,AudioQueueCheck}.java
java -cp "$out" com.example.tingxiejian.PipelineLogicCheck
java -cp "$out" com.example.tingxiejian.PolishQueueCheck
java -cp "$out" com.example.tingxiejian.AudioQueueCheck
