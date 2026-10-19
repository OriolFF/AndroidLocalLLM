# Brief — spike: can LiteRT-LM load a model through `/proc/self/fd/<n>` from a shared fd?
Work in this git worktree only. Read `/Users/oriol/Documents/development/projectes/AndroidLocalLLM/docs/research/model-sharing-across-apps.md` (§4, §5 Phase 0) and CLAUDE.md first. Throwaway spike: do NOT touch `:core:*` / `:feature:*` production code; create a tiny standalone module `:spike:fdshare` (or two minimal app modules) using LiteRT-LM 0.18.0 (`litertlm-android`). Two apps with DIFFERENT applicationIds, SAME debug signing key (default debug keystore is fine): `spike.a` (contributor) and `spike.b` (consumer).

Devices: real phone `QV770GPJMT` (Android 16/API 36, 116 GB free) — primary. `emulator-5556` (LlmPhone_API_36, arm64.nosme) — secondary. Model: `~/.cache/llmlocal-eval/gemma-4-E2B-it.litertlm` (push via adb to /data/local/tmp then stream into the app; adb push to the phone is fine). Use `adb -s <serial>` always; never touch other devices/emulators.

Experiments (record pass/fail + exact error/logcat for each, per device):
1. A commits the model as a blob: `BlobHandle.createWithSha256`, `allowSameSignatureAccess()`, long expiry. Note session/quota/size errors, commit time.
2. B `openBlob(handle)` → `ParcelFileDescriptor`. Check `statSize`, seekability (`Os.lseek`), mmap via `FileChannel`/`Os.mmap`.
3. B `Engine(EngineConfig(modelPath = "/proc/self/fd/${pfd.fd}", backend = CPU, cacheDir = cacheDir))`.initialize() then one short generation. Capture SELinux denials (`adb logcat -b all | grep avc`).
4. Same with a signature ContentProvider in A serving `filesDir/model` via `openFile`; B opens it and tries `/proc/self/fd/N`.
5. Control: B opens its own `filesDir` file via `/proc/self/fd/N` (isolates the fd trick from sharing).
6. If 3/4 fail only on open(): try a fd from `dup`, `Os.dup`, and the `/dev/fd/N` alias; try copying into `memfd`-like alternatives only if trivial. Also check whether blob survives uninstall of A and whether B's `acquireLease` keeps it.
7. Measure init time and RSS for path vs fd loading (if either works).

Deliverable: commit on the worktree branch `spike/model-sharing-fd` with the spike code + `docs/research/model-sharing-spike-results.md` (results table, logs excerpts, verdict: zero-copy feasible yes/no per API level, and what to change in the §5/§6 recommendation). No pushes, no merges. Reply in the terminal with a 5-line verdict.
