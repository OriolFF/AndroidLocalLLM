# Sharing on-device LLM model files across our Android apps (v2)

Date: 2026-10-19 (v1 same day: API analysis only; v2 adds online research into Google's guidance and developer practice). Research only, no code changed.
Legend: **[doc]** official doc read this session · **[src]** source read this session (AOSP / LiteRT-LM `main` / Gallery `main` / the 0.18.0 AAR on this machine) · **[web]** blog/issue/forum read · **[skim]** only a search summary seen · **UNVERIFIED** = inference, needs a device test.

## 1. Executive summary

- **Google's official answer is "per app, not shared".** Play for On-device AI (still **beta**, page updated 2026-10-16) delivers "AI packs" into *the app's own* internal storage and says outright: "Models downloaded by Play for On-device AI should only be used by your apps. Models shouldn't be offered to other apps." Packs are capped at **1.5 GB compressed**, so our 2.58 GB `.litertlm` does not even fit in one pack [doc §2.1].
- **The only Google-run *shared* models are Google's own** (Gemini Nano via AICore, ML Kit unbundled models and Downloadable Fonts via Play services). Google's decision page routes "your own model, such as Gemma" to LiteRT, which is per-app [doc §2.2].
- **Nobody in the open-source Android LLM ecosystem shares model files across apps.** AI Edge Gallery, MLC Chat, PocketPal, SmolChat, Layla, Maid all keep a private copy; the only "no copy" designs (ChatterUI, kotlinllamacpp, llama.rn fork) read a *user-picked* file through SAF + a patched native loader that accepts an **fd**. Users ask for shared folders (PocketPal #475, Gallery #1012); maintainers log and defer [web §3].
- **BlobStoreManager is effectively unusable for a 2.6 GB blob** on normal phones: the per-app *lease* quota is `max(300 MiB, 1 % of /data)` (≈1.3 GB on a 128 GB phone) and an unleased blob is deleted ~6 h after commit by the daily idle job [src §4.1]. Adoption is near zero (4 Stack Overflow questions ever; no public ML usage found).
- **Zero-copy loading is blocked by LiteRT-LM 0.18.0**: the LLM JNI entry point is path-only (the embedding engine already takes `model_fd`), the shipped AAR exports no C API, and format detection trusts the path extension so `/proc/self/fd/N` fails. Even with a usable path, SELinux lets apps `read`/`map` a passed-in `system_data_file` fd but not `open` it, so re-opening a blob or a sibling's private file by path is denied. Feature request #3905 (2026-10-09) is open and assigned [src/web §5].
- **Recommendation:** keep per-app `filesDir` copies (what Google and everyone else does). Add the cheap win only: **"copy from a sibling app"** over a signature-protected ContentProvider so a device downloads the 2.6 GB once (bandwidth 1×, disk N×), and re-evaluate zero-copy when #3905 ships a `modelFd`. Do not adopt BlobStoreManager, Play AI packs, shared external storage, `sharedUserId`, `MANAGE_EXTERNAL_STORAGE` or a companion app for this model size.

## 2. Google's guidance

### 2.1 Play for On-device AI (beta) [doc: developer.android.com/google/play/on-device-ai, "Last updated 2026-10-16"]
- Announced at I/O '25 ("we've launched Play for On-Device AI in beta … to help developers manage custom model downloads efficiently", Android Developers Blog 2025-06-16). No GA announcement found in the I/O '26 recap (2026-05-26) or elsewhere; the page is still titled "(beta)". Runtime lib `com.google.android.play:ai-delivery` versions on Google Maven: 0.1.0-alpha01, 0.1.1-alpha01, **0.2.0-beta01** (2026-08-06) [src: maven-metadata].
- Mechanism: "AI packs" in the App Bundle, built by the `com.android.ai-pack` Gradle plugin (AGP ≥ 8.8; device targeting needs AGP ≥ 8.10). "AI packs only contain models." Delivery: install-time ("installed as an APK", read via `AssetManager`), fast-follow and on-demand ("downloaded to your app's internal storage space"; "Each AI pack is stored in a separate folder in the app's internal storage", located via `AiPackManager.getPackLocation().assetsPath()`). Device targeting by SoC/model/RAM/system feature. "At no additional cost."
- Limits: "Individual AI packs can be up to 1.5GB, based on their compressed download sizes." "The maximum cumulative app size … is 4GB." Downloads > 200 MB off Wi-Fi need user consent; sideloaded builds get `REQUIRES_USER_CONFIRMATION`. "Don't rely on cached AI pack locations between app launches."
- Sharing: explicitly **not** supported. "Models downloaded by Play for On-device AI should only be used by your apps. Models shouldn't be offered to other apps." Storage is per app; nothing about cross-app or cross-package reuse. Formats are not restricted (LiteRT/MediaPipe recommended); the fast-follow/on-demand path hands you a real file path, which LiteRT-LM needs.
- Real adopters: Argmax ships NVIDIA Parakeet 0.6B speech models per NPU "via Play's AI Packs" (Google Developers Blog 2026-04-23); waterkit issue #164 (2026-10-05) plans the same for vision models and records the 1.5 GB cap as a design constraint. No LLM-sized adopter found.
- Verdict for us: a 2.58 GB file exceeds the 1.5 GB pack cap (a split-into-chunks workaround is UNVERIFIED and unsupported by docs), and even if it fit it would be one download **per app**. It solves hosting/targeting, not sharing.

### 2.2 AICore / Gemini Nano / ML Kit GenAI [doc/web]
- Positioning is a hard split: Gemini Nano is the single system-managed model ("you only have one model on the device, and then all of the apps are using that same model through this AICore system" — Google DeepMind talk; AICore "manages the distribution of Gemini Nano and handles future updates" [doc: developer.android.com/ai/gemini-nano]). The AICore Developer Preview (2026-04-02) serves Google's **Gemma 4 E2B/E4B** as "the foundation for the next generation of Gemini Nano", through the ML Kit Prompt API, on AICore-enabled devices only; **no bring-your-own-model path** exists [web].
- Google's own decision page (2026-10-08): "start with ML Kit's GenAI APIs"; "For applications that require highly specialized solutions, you can use your own custom model, such as Gemma … Run your model directly on the user's device with LiteRT" [doc: developer.android.com/ai/generativeai]. I/O '26 recap: "bring your own fine-tuned small language model to Android" with LiteRT-LM [web].
- Precedent that *is* shared: ML Kit unbundled models "Reside in Google Play Services and are not counted towards the storage used by this app"; Play services "will only remove the model from storage if all apps that depend on the model are uninstalled" [doc: ml-kit/tips/installation-paths]. Same pattern for Downloadable Fonts (Play services font provider, a ContentProvider serving fds). Both are Google-owned providers; no public API lets a third party register a shared model.
- Implication: if the product can live with Gemini Nano/Gemma-4-via-AICore quality and device coverage, AICore is the only true "one copy per device" option, as a second `LlmEngine` backend. It cannot load our `.litertlm`.

### 2.3 LiteRT-LM / MediaPipe team guidance [doc/src/web]
- LiteRT-LM Android guide (2026-09-04): models come from "HuggingFace LiteRT Community"; sample uses `modelPath = "/path/to/your/model.litertlm"` and `cacheDir = context.cacheDir.path`; nothing on download, Play delivery, fds or sharing [doc]. MediaPipe LLM guide (maintenance mode): "The model is too large to be bundled in an APK … For deployment, host the model on a server and download it at runtime"; dev workflow is `adb push` to `/data/local/tmp/llm/` [doc].
- LiteRT NPU docs: "use LiteRT to export your compiled models into a single Google Play AI Pack" — the AI-pack recommendation is for *compiled NPU variants*, not for sharing [skim].
- GitHub: #3905 (2026-10-09, open, "type: feature request", assigned 2026-10-12) asks for a `modelFileDescriptor`/`fd:<n>` in `EngineConfig`; the author's isolated-process prototype on v0.16.1 loaded from an fd "within a few percent" of path speed and notes `/proc/self/fd/<n>` fails with "Unsupported or unknown file format" [web]. Merged PRs #3214/#3305/#3458 (Aug–Sep 2026) added fd loading to NPU AOT and the *embedding* engine. No issue mentions `content://`, asset packs or Play delivery for LLM models.
- Google AI Edge Gallery (the reference app): downloads with WorkManager into `context.getExternalFilesDir(null)` (falls back to `filesDir`; Pixel 11 "CD1A" builds use `filesDir` "to avoid FUSE filesystem DMA mapping issues") and *copies* imported files into `<dir>/imports/` via `FileOutputStream` [src: `ModelUtils.getModelStorageDir`, `Model.getPath`, `ModelImportDialog`]. Issue #1012 ("Put .litertlm files in a shared folder", macOS, 2026-07-15) was logged, not planned. Android storage path confirmed by a user bug: `/storage/emulated/0/Android/data/com.google.ai.edge.gallery/files/…` (#649).

### 2.4 Android storage team: BlobStoreManager [doc/src]
- Origin: Android 11 feature list, "Secure sharing of large datasets … such as those that involve machine learning or media playback … cached on the device using shared data blobs" [doc: about/versions/11/features]; AOSP README: "added in Android R", four access modes, leases, cross-user access limited to platform/privileged apps [src].
- Evidence of adoption/abandonment: Stack Overflow has **4** questions mentioning it (2021–2023); the accepted 2022 answer to the main one says the handle must be shared out-of-band ("on-device blob discovery is not supported") [src: StackExchange API]. One Medium tutorial (2021, not readable: 403). No GitHub sample using it for ML found; no Google sample, Codelab or talk beyond the 2020 guide. The guide itself has not changed since Android 11.
- Hard limits from AOSP `main` [src: `BlobStoreConfig`, `BlobStoreManagerService`, `BlobMetadata`]: lease quota per app `max(300 MiB, 1 % of /data size)` — `acquireLease` throws `LimitExceededException("Total amount of data with an active lease is exceeding the max limit")` when `blob.size > remaining quota`; `shouldBeDeleted()` is true for any blob with no valid lease once 6 h (`LEASE_ACQUISITION_WAIT_DURATION_MS`) have passed since every commit, evaluated by a daily idle job and on package removal; sessions idle for 7 days are dropped; `openBlob` returns a plain `ParcelFileDescriptor` from `Os.open(blobFile, O_RDONLY)` (revocable fds default off); blobs live under `/data/system/blobstore/blobs` (label `system_data_file`).
- Consequence: a 2.6 GB blob can be committed and opened, but **cannot be leased** on devices with < ~260 GB storage, so it is garbage-collected within ~6–30 h. v1's "long expiry + lease" plan does not work at this size.

## 3. What other developers do

| Project (runtime) | Obtained | Stored | Shared across apps? | Notes [source] |
|---|---|---|---|---|
| Google AI Edge Gallery (LiteRT-LM) | HF download (WorkManager); local import | `getExternalFilesDir()`; imports copied in | No | Shared-folder request logged (#1012) [src/web] |
| MLC Chat (mlc-llm) | HF download at runtime; optional APK bundle; adb push | app data dir (`Android/data/ai.mlc.mlcchat/files`) | No | [doc: llm.mlc.ai; skim] |
| PocketPal AI (llama.rn) | HF download; "Add local model" | copied to "the app's own storage which is inaccessible to the user" | No | #475 (2025-10, open): "other applications can also use the model without duplicating it" → "we need upstream support" (llama.rn #191) [web] |
| ChatterUI (cui-llama.rn) | user file via SAF | "Import" = copy; "Use External Model" = read in place, "slight delay in load times" | Only via user-picked shared file | Fork patches ggml to `fopen` an fd number [web] |
| kotlinllamacpp | user file via SAF | in place via `content://` + persistable grant → fd | Same | "you cannot pass traditional file paths to native libraries due to Scoped Storage" [web] |
| SmolChat (llama.cpp) | user file | stores a file path per model | Same, UNVERIFIED copy/no-copy | [skim] |
| Layla, Maid (llama.cpp) | user file / download | "local models stay in Layla's app storage" | No | [web/skim] |
| llama.cpp in Termux | wget to `~/` | Termux private home | No | docs: "recommended to place it in the ~/ directory for best performance"; shared-storage loads hit FUSE `EINVAL`/slow mmap (#18804, PR #7016) [web] |
| Firebase ML custom models | per-app download | app private | No | deprecated, shutdown 2027-06-15 [skim] |
| Argmax / waterkit (LiteRT) | Play AI packs | app internal | No | ≤ 1.5 GB per pack [web] |

Non-LLM precedents for "one developer, several apps, huge asset":
- **System-owned shared stores**: ML Kit unbundled models, Downloadable Fonts, Google TTS voices — all live inside Play services / the engine app and are served over a Google-controlled provider. The pattern is "one *privileged* host app", which we cannot be.
- **Add-on APKs**: AnySoftKeyboard language packs are separate APKs discovered via `queryBroadcastReceivers` and read with `createPackageContext(pkg).resources` [src: `AddOnsFactory`]; OsmAnd topo/contour plugins are paid add-on APKs with per-region data downloaded by the main app [skim]. Works because APK contents are world-readable, but Play caps a single install-time pack/APK far below 2.6 GB and LiteRT-LM cannot read an asset-in-APK by path.
- **Desktop LLM tooling**: users dedupe Ollama/llama.cpp/LM Studio with hard links or symlinks in one folder [web] — exactly what Android's sandbox forbids.
- **Community signal**: no r/androiddev or r/LocalLLaMA thread about sharing models *between apps* was found (several searches); the recurring ask is "let me pick my own file / don't duplicate my download", answered by SAF + fd loading in llama.cpp apps.

## 4. API options, condensed from v1 and corrected

| # | Option | Keep from v1 | Correction after v2 evidence |
|---|---|---|---|
| 1 | BlobStoreManager (API 30+) | content-addressed, same-signature access, commit verifies SHA-256, plain fd | **Reject for 2.6 GB**: lease quota `max(300 MiB, 1 %)`, unleased blobs GC'd after 6 h; near-zero ecosystem adoption; fd cannot be re-opened by path (SELinux `read map` but no `open` on `system_data_file`) |
| 2 | Signature ContentProvider | works on API 24+, same key only, no user prompt, copy-from-fd always works | Still the best cheap win; it is "copy from neighbour", not sharing. Same `/proc/self/fd` blocker (sibling's `app_data_file` has other MCS categories) |
| 3 | Shared external storage (MediaStore / SAF / `MANAGE_EXTERNAL_STORAGE`) | world-readable, user-deletable, path not guaranteed | Confirmed: Play's All-files-access policy lists file managers, backup, antivirus, document mgmt, search, encryption, migration; our use is not listed [doc]. FUSE paths also degrade mmap (llama.cpp #18804, Gallery `filesDir` workaround) |
| 4 | `sharedUserId` | deprecated API 29, cannot be added to installed apps | unchanged, reject |
| 5 | AICore / Gemini Nano / ML Kit GenAI | cannot load our `.litertlm` | Confirmed; only path to a true single on-device copy, as an optional second backend (Gemma 4 preview now, Nano 4 "later this year") |
| 6 | Play Asset Delivery / **Play for On-device AI** | per app | Confirmed per app and explicitly "shouldn't be offered to other apps"; 1.5 GB pack cap; still beta |
| 7 | Companion model-manager app | high UX cost | unchanged, reject |
| 8 | Hard links / symlinks / `/data/local/tmp` | do not work | unchanged |

## 5. LiteRT-LM and fd loading: verified state (0.18.0 and `main`)
- `EngineConfig.modelPath: String`; `nativeCreateEngine(modelPath, …)` calls `FileExists(path)` then `ModelAssets::Create(path)` [src: `Config.kt`, `jni/litertlm.cc`]. `nativeCreateEmbeddingEngine(model_fd: Int, modelPath, …)` already `dup()`s an fd into a `ScopedFile` — the fd plumbing exists one function away [src].
- The 0.18.0 AAR (`liblitertlm_jni.so`, 21 MB arm64) exports only `Java_…LiteRtLmJni_native*` symbols; **no `litert_lm_*` C API** is exported, so `litert_lm_engine_settings_create_from_raw_file_descriptor` [src: `c/engine.h`] is unreachable without building from source [src: `nm -D`].
- Format detection: `GetFileFormat(path, scoped_file)` trusts `.tflite/.task/.litertlm` extensions, else sniffs the header *only if a `ScopedFile` is supplied*; otherwise "Unsupported or unknown file format." [src: `runtime/util/file_format_util.cc`]. That is why `/proc/self/fd/N` fails (#3905). A symlink named `x.litertlm` → `/proc/self/fd/N` inside our own `filesDir` would pass the extension check, but the re-open then needs SELinux `open` on the target: apps have `allow appdomain system_data_file:file { getattr read map }` (no `open`) [src: `private/app.te`], and sibling `app_data_file`s differ in MCS category. The native loader mmaps the file ("could not mmap file descriptor" strings in the .so), which an fd passed over Binder supports — so a real `modelFd` API would work; a path never will across sandboxes. **Zero-copy = wait for #3905** (or vendor a from-source JNI, not worth it).

## 6. Synthesis

### 6.1 Ranked recommendation (three apps, same key, 2.58 GB `.litertlm`)
1. **Keep per-app private copies** (`filesDir/llm/`, already the case). This is Google's documented model (Play AI packs, LiteRT guides, Gallery) and universal practice. Keep `cacheDirProvider()`; do not move models to external/FUSE storage.
2. **Add "copy from sibling" via a signature-protected ContentProvider in `:core:llm`** (v1 §3.2 design): before downloading, query sibling packages (`<queries>` + a shared `<permission … protectionLevel="signature">`, declared identically in every app), `openFile` → stream into the existing `.part` flow, verify `sha256`, rename. Benefit: one 2.6 GB download per device instead of up to three; works on API 24+; no user prompt; no Play policy exposure. Cost: ~1 day; disk stays N×. Prerequisite: non-null `sha256` in every `LlmModelDescriptor`.
3. **Track LiteRT-LM #3905**; when `EngineConfig` gains an fd, turn the provider into a true share (host keeps the file, clients hold the pfd for the engine's lifetime, local copy deleted) — disk 1× for API 24+, no BlobStore needed. Re-check on every LiteRT-LM bump.
4. **Optional product-level alternative:** an AICore/ML Kit Prompt API backend behind `LlmEngine` for devices that have Gemini Nano / Gemma 4 via AICore — the only route to a single on-device copy today, at the price of device coverage and our JSON-schema/prompt control. Not a sharing mechanism for our file.
5. **Drop from the plan:** BlobStoreManager (quota/GC make it unusable at this size), Play for On-device AI as a sharing tool (per app, 1.5 GB cap, beta), shared external storage, `sharedUserId`, companion app.

### 6.2 If we adopted Play for On-device AI anyway (hosting, not sharing)
- Would require: AGP ≥ 8.8 (we are on 9.4.1, fine), `com.android.ai-pack` modules per app, `ai-delivery:0.2.0-beta01`, an App Bundle per app, and **splitting the 2.58 GB file into ≥ 2 packs** (UNVERIFIED that packs can be concatenated at runtime; docs say nothing) or a smaller model variant (Gemma 4 q4 in our catalog might fit; check its compressed size). Each app still downloads its own copy; `getPackLocation()` must be re-resolved every launch; sideloaded/debug builds hit `REQUIRES_USER_CONFIRMATION`; bundletool local testing "does not support updates".
- Gains: no HuggingFace hosting/gating, Play-managed patches and Wi-Fi/consent UX, device targeting (e.g. NPU variants later). Losses: no sharing, beta API, no F-Droid/sideload path, 1.5 GB cap. Recommendation: not now; revisit if we ship NPU-specific variants or drop below 1.5 GB.

### 6.3 Open questions and a 1-day spike
Spike (existing brief `docs/plans/briefs/spike-model-sharing-fd.md` can be trimmed — experiments 1–3 and 5 are now predicted to fail; keep them only as a 1-hour confirmation):
1. Signature ContentProvider between two debug-signed test apps: `openFile` of a 2.6 GB file, copy throughput and time on a real phone (expect ~1–2 min), behaviour when the host is uninstalled mid-copy, and the duplicate-`<permission>` install-order conflict (declare one shared name identically in all apps).
2. Confirm the SELinux prediction cheaply: `open("/proc/self/fd/N")` on a provider fd and on a blob fd, log `avc:` denials (expected `{ open }` denied).
3. Measure `acquireLease` on a 2.6 GB blob on a 128 GB phone (expect `LimitExceededException`) to close the BlobStore question with a device log.
4. Check the compressed size of the Gemma 4 q4 catalog entry against the 1.5 GB AI-pack cap.
Open: will #3905 land in 0.19/0.20 (ask on the issue)? Does Gemma-4-via-AICore meet our quality/JSON needs on Pixel/Samsung (prototype with the Prompt API)? Is per-device dedupe worth a day given that most users install one of the three apps (we have no install-overlap data)?

## 7. Sources
Google / official
- Play for On-device AI (beta) — https://developer.android.com/google/play/on-device-ai — read
- "Top 3 things to know for AI on Android at Google I/O '25" (2025-06-16) — https://android-developers.googleblog.com/2025/06/top-3-updates-for-ai-on-android-google-io.html — read
- "Top AI on Android updates … Google I/O '26" (2026-05-26) — https://android-developers.googleblog.com/2026/05/android-ai-intelligence-system.html — read
- "Announcing Gemma 4 in the AICore Developer Preview" (2026-04-02) — https://android-developers.googleblog.com/2026/04/AI-Core-Developer-Preview.html — read
- "Hybrid inference and new AI models are coming to Android" (2026-04-17) — https://android-developers.googleblog.com/2026/04/Hybrid-inference-and-new-AI-models-are-coming-to-Android.html — skimmed
- Find the right AI/ML solution (2026-10-08) — https://developer.android.com/ai/generativeai — read
- Gemini Nano overview — https://developer.android.com/ai/gemini-nano — skimmed
- "Building real-world on-device AI with LiteRT and NPU" (2026-04-23, Argmax) — https://developers.googleblog.com/building-real-world-on-device-ai-with-litert-and-npu/ — read
- LiteRT NPU acceleration (AI pack export) — https://developers.google.com/edge/litert/next/npu — skimmed
- LiteRT-LM Android guide (2026-09-04) — https://developers.google.com/edge/litert-lm/android — read
- MediaPipe LLM Inference for Android — https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android — read
- ML Kit installation paths — https://developers.google.com/ml-kit/tips/installation-paths — read
- ML Kit 2020 announcement — https://android-developers.googleblog.com/2020/06/mlkit-on-device-machine-learning-solutions.html — skimmed
- Downloadable Fonts — https://developer.android.com/guide/topics/text/downloadable-fonts.html — skimmed
- Play Asset Delivery (2026-10-06) — https://developer.android.com/guide/playcore/asset-delivery — read
- Play size limits — https://support.google.com/googleplay/android-developer/answer/9859372 — read
- Play "All files access" policy — https://support.google.com/googleplay/android-developer/answer/10467955 — read
- Android 11 features ("Secure sharing of large datasets") — https://developer.android.com/about/versions/11/features — read
- Access shared datasets guide — https://developer.android.com/training/data-storage/shared/datasets — read (v1)
- BlobStoreManager reference — https://developer.android.com/reference/android/app/blob/BlobStoreManager — skimmed
- `ai-delivery` Maven metadata — https://dl.google.com/android/maven2/com/google/android/play/ai-delivery/maven-metadata.xml — read
Source code
- AOSP BlobStore README — https://android.googlesource.com/platform/frameworks/base/+/a5cb005304ee/apex/blobstore/README.md — read
- AOSP `BlobStoreConfig.java`, `BlobStoreManagerService.java`, `BlobMetadata.java` (`main`) — https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/apex/blobstore/service/java/com/android/server/blob/ — read (grep)
- AOSP sepolicy `private/app.te`, `private/file_contexts` (`main`) — https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/main/private/ — read (grep)
- LiteRT-LM `c/engine.h`, `kotlin/.../Config.kt`, `LiteRtLmJni.kt`, `jni/litertlm.cc`, `runtime/util/file_format_util.cc` (`main`) — https://github.com/google-ai-edge/LiteRT-LM — read
- LiteRT-LM 0.18.0 AAR symbol table (local Gradle cache, `nm -D`) — read
- Gallery `data/ModelUtils.kt`, `data/Model.kt`, `data/DownloadRepository.kt`, `ui/modelmanager/ModelImportDialog.kt` (`main`) — https://github.com/google-ai-edge/gallery — read (grep)
- AnySoftKeyboard `AddOnsFactory.java` — https://github.com/AnySoftKeyboard/AnySoftKeyboard — read (grep)
Issues / community
- LiteRT-LM #3905 fd for Android engine — https://github.com/google-ai-edge/LiteRT-LM/issues/3905 — read
- LiteRT-LM #3139, PRs #2208/#3214/#3305/#3458 (fd work) — https://github.com/google-ai-edge/LiteRT-LM/issues/3139 — skimmed
- Gallery #1012 shared folder — https://github.com/google-ai-edge/gallery/issues/1012 — read
- Gallery #649 ENOENT (storage path) — https://github.com/google-ai-edge/gallery/issues/649 — read
- Gallery wiki: importing local models — https://github.com/google-ai-edge/gallery/wiki/6.-Importing-Local-Models-(optional) — read
- PocketPal #949 / #475 — https://github.com/a-ghorbani/pocketpal-ai/issues/475 — read
- llama.rn #191 external files via fd — https://github.com/mybigday/llama.rn/issues/191 — read
- ChatterUI README — https://github.com/Vali-98/ChatterUI — read
- kotlinllamacpp README — https://github.com/ljcamargo/kotlinllamacpp — read
- llama.cpp `docs/android.md` — https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md — read
- llama.cpp #18804 (Termux shared storage EINVAL) and PR #7016 — https://github.com/ggml-org/llama.cpp/issues/18804 — skimmed
- MLC Android SDK docs — https://llm.mlc.ai/docs/deploy/android.html — read
- mlc-llm #954 (weights location) — https://github.com/mlc-ai/mlc-llm/issues/954 — skimmed
- Layla FAQ — https://help.layla-network.ai/faq/ — read
- waterkit #164 Play AI packs — https://github.com/water-rs/waterkit/issues/164 — read
- Stack Overflow 66250094 (BlobStoreManager) + StackExchange search counts — https://stackoverflow.com/questions/66250094/new-blobstoremanager-read-write-on-android-11 — read via API
- "Shared Datasets in Android" (Medium, 2021) — https://supunisharaweerasekara.medium.com/shared-datasets-in-android-7ee612d33474 — unread (403)
- "Gemini Nano on device" talk (Muntenescu/Gaymond) — https://ai.engineer/talks/owH1f0N-keY-gemini-nano-on-device — skimmed
- Sharing models between Ollama and llama.cpp (desktop hard links) — https://www.rushis.com/sharing-local-llm-models-between-ollama-and-llama-cpp/ — skimmed
- Project files: `core/llm/.../download/LlmModelManager.kt`, `core/llm/.../engine/LiteRtLlmEngine.kt`, `docs/plans/briefs/spike-model-sharing-fd.md`, `CLAUDE.md` — read
