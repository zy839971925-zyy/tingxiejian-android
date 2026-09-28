# Contributing

Thanks for improving 听写间. Small, focused changes are easiest to verify.

1. Open an issue explaining the device/API level, expected behavior and a **redacted**
   reproduction. Never upload original audio, full transcripts, API keys or private diagnostic
   exports.
2. Keep offline recognition functional with no Shizuku, no configured cloud provider and no
   network. Any application HTTP request belongs in `Cloud.java`.
3. Prefer an explicit regression check that fails before a fix and passes after it. Run
   `bash design-tools/check-all.sh` before proposing changes. If you have legally obtained
   build dependencies/models, also run `bash build.sh` and test on an ARM64 phone.
4. Preserve existing `need(id)` checks, async failure guards, real progress semantics and
   optional-notification fallbacks. UI motion must respect disabled animations and large fonts.
5. Submit only original work that you can license under MIT; adaptations of Apache-2.0 code
   must retain attribution/terms. Do not add model weights, MiSans, SDK binaries, release APKs,
   signing files or vendor libraries to Git history. Review `THIRD_PARTY_NOTICES.md` first.

By submitting, you agree that your original contribution is available under this project's MIT
license, excluding explicitly documented upstream portions.
