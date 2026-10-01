# Corpus benchmark harness

No user corpus or Android device has been supplied. There are no measured model rankings in this repository. Tests use synthetic text and timings to check the scorer; those values are not model results. Downloaded public upstream examples are model smoke-test material, not this user's accuracy corpus.

Create a private directory outside the tracked tree (or ignored `benchmark/corpus/`). Write one JSON object per line in `corpus.jsonl` following `corpus.schema.json`; `audio` is the private recording's path, `reference` a human-checked faithful transcript, and `category` one of the twelve supported categories. Annotate `hotwords` with the professional/name/place terms being tested. Optional `numbers` is the ordered sequence of exact number spans, including Chinese number words when needed. Record permission/desensitization information in `consent_note`. Do not commit recordings or private transcripts.

Run each candidate with the same audio, VAD boundaries, hotword list, threads and provider on the SAME Android device. Keep inference cold-load and warmed-run measurements separate. Save actual outputs as JSONL following `result.schema.json`, including model manifest hash, device, sherpa runtime version and configuration. No reference transcript or expected number annotation may be passed to a recognizer. Hotwords are the declared contextual vocabulary, not a hidden correction based on ground truth.

```sh
python3 benchmark/score.py /private/corpus.jsonl /private/qwen-results.jsonl --output /private/qwen-report.json
python3 -m unittest discover -s benchmark -p 'test_*.py'
```

CER is corpus micro-average Levenshtein character edits divided by reference characters after NFKC, casefold and whitespace removal. Punctuation stays in the score. Empty reference text can incur insertion errors; an all-empty reference corpus has a null CER, not a fabricated zero. Hotword errors count missing/excess occurrences of reference-present annotated terms. Numeric errors are token-level edit distance over digit/date/decimal/percentage spans, or explicitly annotated expected spans; Chinese number-word matching is strict. ITN equivalents (`三百`/`300`) are not silently treated as equal. Report and compare faithful and normalized outputs separately when studying ITN.

Required timing definitions:

- `first_partial_ms`: monotonic time from first audio submission to first nonempty partial, null for offline-only recognizers.
- `finalization_ms`: speech-segment/VAD end to availability of its finalizer output (includes queue wait). File-only runs can additionally record audio EOF to final document completion, clearly labeled outside this scalar.
- `decode_seconds`: measured recognition compute/wall time for the audio, excludes model load and file setup; divided by positive `audio_duration_seconds` for RTF.
- `peak_memory_bytes`: sampled total app process PSS/native+Java memory during that run, not Java heap alone. Capture with Android `Debug.MemoryInfo`/`dumpsys meminfo`; record sampling interval in config. Desktop RSS is not Android PSS and must be labeled accordingly.
- `model_load_ms`: monotonic time for model initialization, including on-demand file verification when applicable; record cold/warm condition in config.

The scorer reports observation counts, median/p95/max and per-category metrics. Missing observations remain null. Duplicate, missing and unknown result IDs, invalid categories, negative/nonfinite timing or zero duration are rejected. The harness scores observations; it does not infer accuracy from a model card, invent absent latency, or invoke cloud inference.
