# Sharing on-device LLM model files across our Android apps

Date: 2026-10-19. Research only, no code changed.
Evidence legend: **[doc]** read in official docs/source this session; **[src]** read in LiteRT-LM `main` source; **UNVERIFIED** = from general knowledge, not confirmed here (needs a device spike).

## 1. Executive summary

Today each app (`:app:recipes`, `:app:medical`, `:app:recipekeeper`) downloads its own 2.6 GB `.litertlm` into `filesDir/llm/` and hands `file.absolutePath` to `EngineConfig(modelPath = String)`. **LiteRT-LM exposes only a path-string API** [src: `EngineConfig.modelPath: String`, `nativeCreateEngine(modelPath: String, …)`, `nativeCreateModelInfo(modelPath: String)`; the official Android guide also shows only `modelPath` — no fd/AssetFileDescriptor/URI]. That is the decisive constraint: any sharing scheme must end with a *filesystem path the native code can open*. Android's sandbox (UID + SELinux MCS categories) forbids cross-app access to `filesDir`, hard links, and symlinks, so the only mechanisms are (a) system-mediated blobs (`BlobStoreManager`, API 30+), (b) a provider app serving an fd, or (c) world-visible shared storage. Options (a) and (b) deliver an **fd, not a path**; whether `/proc/self/fd/<n>` can be re-opened by the native loader is **UNVERIFIED** and plausibly blocked by SELinux for blobstore/other-app files. Realistic outcome: **share the *download* (once per device) via BlobStoreManager with `allowSameSignatureAccess()`, then copy once into each app's `filesDir`** — this saves bandwidth/time but *not* disk (still N copies, unless the `/proc/self/fd` spike succeeds, which would also save disk and be the ideal). Shared external storage (MediaStore.Downloads) is the only option that shares disk *and* yields a real path, but it is world-readable, user-deletable, and path access to another app's file is not guaranteed on Android 10+. Recommendation: **spike `/proc/self/fd` on a real device first (1 day); ship BlobStoreManager + copy-on-first-use for API 30+, keep per-app download for API 24–29**; do not use sharedUserId, MANAGE_EXTERNAL_STORAGE, or a companion app.

## 2. Comparison table

| # | Option | API | Prompt/permission | LiteRT-LM can use it? | Saves disk? | Saves download? | Security (who reads) | Play policy | Lifecycle |
|---|---|---|---|---|---|---|---|---|---|
| 1 | **BlobStoreManager** | 30+ | none | fd only → needs `/proc/self/fd` (UNVERIFIED) or copy | only if fd path works | **yes** | same-signature / named pkgs / public (your choice) | fine | system GC on expiry & when no lease/commit holders; lease API |
| 2 | **Signature ContentProvider** | 24+ | signature permission, none to user | fd → same caveat; copy works | only if fd path works | yes (if a host exists) | same signing key only | fine | host-app dependent; election needed |
| 3a | MediaStore.Downloads | 29+ (legacy path <29) | none for own files; **others' non-media files not readable by path** | path only for files you own (UNVERIFIED for cross-app) | yes if readable | yes | any app w/ storage access; user can delete | fine | survives uninstall (orphan) |
| 3b | SAF `OPEN_DOCUMENT_TREE` | 21+ | **user picks folder every app**; Downloads root blocked on 11+ [doc] | URI/fd only | yes | yes | user-chosen | fine | persists; user-managed |
| 3c | `MANAGE_EXTERNAL_STORAGE` | 30+ | special access toggle | real path | yes | yes | all files | **restricted by Play policy; rejected for this use** | orphaned |
| 3d | app-specific external dir | 19+ | none | path | no | no | not shareable across apps on 11+ (`Android/data` blocked) [doc] | — | removed with app |
| 4 | `sharedUserId` | ≤ 28 usable; deprecated 29 | none | path | yes | yes | shared UID | Play disallows adding to existing apps (cannot migrate installed apps) | n/a |
| 5 | AICore / Gemini Nano / ML Kit GenAI | device-dependent | none | **No** — system-managed Gemini Nano, not a Gemma `.litertlm` | n/a | n/a | OS | fine | OS |
| 6 | Companion "model manager" app | any | install 2nd app | via #2 | maybe | yes | signature | fine, high UX cost | host uninstall = loss |
| 7 | grantUriPermission / persistable grants | 19+ | none | URI/fd only | no | maybe | per grant | fine | grants die on restart/uninstall |
| 8 | hard link / symlink / `/data/local/tmp` | — | — | **do not work** across sandboxes | — | — | — | — | — |

## 3. Options in detail

### 3.1 BlobStoreManager (API 30+)
- **How** [doc: developer.android.com/training/data-storage/shared/datasets]: app computes SHA-256 of the content, builds `BlobHandle.createWithSha256(digest, label, expiryMillis, tag)`; contributor does `createSession(handle)` → `openSession(id)` → `openWrite(offset, length)` → `allowSameSignatureAccess()` / `allowPackageAccess(pkg, cert)` / `allowPublicAccess()` → `commit(executor, cb)`. Consumers call `openBlob(handle)` → `ParcelFileDescriptor`. The system verifies the digest at commit, so integrity is built in and the blob is **content-addressed**: any app that knows the sha256 can find it, no host app or package-name coupling.
- **Leases / expiry** (reference page was too large to read here; **UNVERIFIED** details): `acquireLease(handle, description, expiryMillis)` keeps a blob from being GC'd; blobs whose expiry passed and with no leases are removed; per-app lease/session quotas exist (`getRemainingLeaseQuotaBytes`, `getLeasedBlobs`). The user-visible `label` appears in Settings → storage (doc says label is user-visible). Size limits: **UNVERIFIED**; the doc example writes 200 MiB — a 2.6 GB blob must be tested; per-app quota may apply.
- **Seekable fd for native code?** `openBlob` returns a read-only `ParcelFileDescriptor` onto a file in system storage — seekable/mmap-able in-process (**UNVERIFIED**). But LiteRT-LM wants a path; see §4.
- **Availability:** AOSP feature, OEM builds should ship it; but Go/low-RAM or stripped OEM builds may lack the service (`getSystemService(BLOB_STORE_SERVICE)` null) — **UNVERIFIED**; must null-check and fall back.
- **Play policy:** no issue; it is the platform's intended ML-dataset sharing API.
- **Lifecycle/orphans:** blob not tied to a package; survives uninstall of the contributor while an expiry/lease holds. Use expiry = long (e.g. 1 year) and re-acquire a lease each time any app uses it; the system cleans up when nobody leases it. Good: no orphan logic of our own.

### 3.2 Signature-protected ContentProvider
- A provider in `:core:llm` (so every app has it) with `android:permission="com.llmlocal.permission.MODEL_ACCESS"` (`protectionLevel="signature"`), `openFile(uri,"r")` → `ParcelFileDescriptor.open(file, MODE_READ_ONLY)`. Each app declares a *different authority* (`${applicationId}.models`) since two apps cannot share one authority; clients query siblings via `PackageManager.queryContentProviders`/known package list (needs `<queries>` on API 30+).
- Same fd-vs-path problem. Copy-from-fd works on every API 24+.
- **Who hosts?** Each app that has the file hosts it; a client asks siblings in a deterministic order and takes the first that has it ("leader election" = first installed w/ file). When the host is uninstalled the file vanishes with its `filesDir` — remaining apps must re-download or already hold their own copy (they would, after copy-on-first-use). This is therefore **a sneakier "copy from neighbour" feature, not true sharing**, and it is the right fallback for API 24–29.
- Security: signature permission → only our key; no user prompt. Per-app UID check is enforced by the framework.

### 3.3 Shared external storage
- `MediaStore.Downloads` + `IS_PENDING` (API 29+): the creating app owns the entry; **other apps cannot open non-media files they didn't create** without `READ_EXTERNAL_STORAGE`+ it only exposes media types on 13+; for non-media in Downloads they must use SAF. Direct `File` path to another app's Downloads file is not reliable on 10+ (**UNVERIFIED**, FUSE-dependent). Also user can delete it, and it's visible in the Files app.
- SAF `OPEN_DOCUMENT_TREE`: on Android 11+ the root of internal storage and `Download` cannot be selected [doc]; grant lasts until reboot unless `takePersistableUriPermission` [doc]; each app needs its own user pick. Gives URI → fd, not path. Poor UX.
- `MANAGE_EXTERNAL_STORAGE`: Play restricts to file managers/backup/antivirus etc.; an LLM app sharing its own model does not qualify (policy from general knowledge, **UNVERIFIED** wording). Reject.
- App-specific external dirs: `Android/data/<pkg>` is not accessible to other apps on 11+ [doc]. `requestLegacyExternalStorage` is ignored from targetSdk 30 and dead on Android 11+.

### 3.4 `android:sharedUserId`
Deprecated in API 29. Can only be adopted at *first install* with the same signing key; already-installed apps cannot switch (and Play blocks adding it to updates). `sharedUserMaxSdkVersion="32"` only helps migrate existing sharedUserId users. Would also entangle our three apps' data. **Reject.**

### 3.5 System / Play solutions
- **Play Asset Delivery**: per-app asset packs, not shared; 2.6 GB also exceeds comfortable install-time pack limits (pack-size limits **UNVERIFIED**).
- **AICore / Gemini Nano / ML Kit GenAI** (developer.android.com/ai/gemini-nano, developers.google.com/ml-kit/genai): OS-shared, Google-curated Gemini Nano on supported devices only (Pixel/Samsung/Xiaomi etc., list changes). APIs expose task-level calls (Prompt API, summarise, rewrite…) — **they cannot load a Gemma `.litertlm`**, and our template relies on custom prompts/JSON schemas and device-independent behaviour. Could be an *additional* engine backend later; not a way to share our model.
- Vendor stores (Samsung/Qualcomm): no public cross-vendor API found; **UNVERIFIED**/not a path.

### 3.6 Companion model-manager app
Same technology as 3.2 but forces users to install a 2nd app. Play has no sanctioned "install dependency" hook for third-party apps (Instant Apps/`PackageInstaller` can't silently install; cross-promotion needs user action — general knowledge, **UNVERIFIED**). If host is uninstalled, everyone loses the model. **Reject** unless BlobStoreManager proves unusable *and* sharing is a must.

### 3.7 `grantUriPermission` vs persistable grants
`grantUriPermission` (temporary, per-package, tied to process/reboot; revoked on revoke or uninstall) works with a ContentProvider but needs the host to push grants to a specific package — impossible for apps that start independently. With the signature permission it is unnecessary. `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` applies only to SAF/documents providers and lasts until the doc moves/deletes [doc]. **Not needed.**

### 3.8 Hard links, symlinks, `/data/local/tmp`
Each app's `/data/data/<pkg>` is `app_data_file` with a per-app MCS category set and a distinct UID; `link()`/`symlink` into another app's dir fails with EACCES (and since Android 8ish `fs.protected_hardlinks`-style restrictions plus SELinux deny it). `/data/local/tmp` is shell-only (not writable by untrusted apps). Confirmed by the platform security model, but not tested here → treat as **doc-level knowledge, not tested**. **Do not use.**

## 4. LiteRT-LM and fd-based loading
- **0.18.0 API**: `EngineConfig(modelPath: String, backend, visionBackend?, audioBackend?, maxNumTokens, cacheDir)`. JNI takes a string path [src, `main` branch — **0.18.0 tag not diffed; UNVERIFIED that it matches**]. No `AssetFileDescriptor`/`ParcelFileDescriptor`/URI constructor exists on `main`. Whether native code mmaps the `.litertlm` or reads it: **UNVERIFIED** (LiteRT generally mmaps weights; the flatbuffer-style container is likely mapped).
- **`/proc/self/fd/<n>` trick**: pass `"/proc/self/fd/$n"` as `modelPath`. Native `open()` of that symlink re-opens the underlying inode and re-runs SELinux/DAC checks against the *opener's domain* — for a blobstore file (system_data_file/blob dir) or another app's private file this is likely **denied** (**UNVERIFIED**). It does work for pipes/own files, and for fds from a ContentProvider only if the underlying file is readable by our domain. Also: `File.exists()`/`length()` on that path (used by `buildEngine()`) returns the target's metadata if opened-able. Must be tested on API 30, 33, 35 (real devices; emulator ok) before design is final.
- **`cacheDir`**: defaults to the model's directory when unset — with a shared/readonly model location we **must** keep passing `cacheDirProvider()` (we already do).
- **Fallback that always works**: copy fd → `filesDir/llm/<file>` once (needs 2.6 GB free per app; use `FileChannel.transferFrom` / `copyTo`, then verify sha256 and atomic rename, exactly the current `.part` flow).
- If a path is truly required and disk must be shared, the only fully path-based option is a **world-readable file in shared storage** (3.3) which we judge unsafe/unreliable.

## 5. Recommended architecture

**Phase 0 – spike (≤ 1 day, real devices API 30/34/36):** (1) commit a 2.6 GB blob with `allowSameSignatureAccess`; check size/quota errors; (2) from a second app, `openBlob` and try `Engine(EngineConfig("/proc/self/fd/N"))`; (3) same for a provider-served fd. Record results here.

**Phase 1 (works regardless of spike): "download once, copy locally"**
- API 30+: before downloading, `BlobStoreManager.openBlob(handle)` for the descriptor's `sha256`; if present → copy into `filesDir/llm/` (no network). Else download as today, then *also* contribute to the blob store (second write pass or write-through) with `allowSameSignatureAccess()` and a long expiry.
- API 24–29 (and no blob service): per-app download as today, **plus** optional sibling `ModelProvider` (signature) to copy from a neighbour that has it.
- Disk is N copies, bandwidth 1×. Offer a Settings action "free duplicate model copy" only if Phase 2 lands.

**Phase 2 (only if spike succeeds): zero-copy**
- Engine opens `/proc/self/fd/N` from `openBlob` (keep the `ParcelFileDescriptor` open for the engine's lifetime; closing it invalidates the path). Delete the local copy; disk = 1×.

**Descriptor requirement:** blobs are keyed by sha256 → **every catalog entry must carry `sha256`** (currently optional). Model id/version = hash.

## 6. Concrete changes in `:core:llm`
- `LlmModelDescriptor` (`:core:model`): make `sha256` and `sizeBytes` non-null for catalog entries.
- `LlmModelManager`:
  - new `SharedModelStore` interface (`open(descriptor): ParcelFileDescriptor?`, `contribute(descriptor, file)`, `acquireLease(descriptor)`) with `BlobStoreModelStore` (API 30+) and `ProviderModelStore` (<30) / `NoopModelStore`; bound in `llmModule` by `Build.VERSION.SDK_INT` and null-check of `Context.BLOB_STORE_SERVICE`.
  - `download(descriptor)`: before `downloadInto`, try `sharedStore.open(descriptor)` → `copyFrom(pfd, tmp)` reusing the existing `.part` → sha256 verify → rename path; after a successful network download call `sharedStore.contribute(...)`. Emit `DownloadProgress` during the copy so UI behaves the same.
  - `isInstalled`/`installedModelIds`: unchanged for Phase 1.
  - `remove(descriptor)`: delete local file only; **do not** delete the shared blob (let expiry/leases handle it); optionally `releaseLease`.
- `ModelDownloadWorker.doWork`: unchanged contract; `setForeground` still mandatory (copy of 2.6 GB takes tens of seconds). Add a progress-store state/label "Reusing shared copy" (labels live in `ModelManagementLabels`).
- `LiteRtLlmEngine`: Phase 1 no change (`modelPathProvider: () -> File`). Phase 2: change to a `ModelSource` sealed type (`Path(File)` | `Fd(ParcelFileDescriptor)`) and `buildEngine()` builds `modelPath = "/proc/self/fd/${pfd.fd}"`, holds the pfd in a field, closes it in a new `close()`; relax the `length() > MIN_MODEL_BYTES` check to `pfd.statSize`.
- `:core:llm` manifest: if provider fallback is kept, add `<permission android:name="${applicationId}.…" protectionLevel="signature">` per app (permission names must be unique per app or share one declared name — beware of the *"permission already declared by another package"* install conflict, use one shared name declared identically; **UNVERIFIED** which behaviour on install order) and `<queries>` for sibling packages.
- Tests: `FakeSharedModelStore` in `core/llm/src/test`; cases: blob hit, blob digest mismatch (treat as miss + delete `.part`), blob service null, cancel mid-copy.

## 7. Integrity, corruption, versioning
- Blob store verifies SHA-256 at commit [doc: "cryptographically secure SHA-256 hash"; commit verification itself **UNVERIFIED**], so a successful `openBlob` is trusted content; re-verify only after copying (cheap vs. download: ~10 s for 2.6 GB — optional).
- Provider fallback: always verify sha256 after copy; mismatch → delete and fall through to network.
- Updated model = new sha256 = new blob; old one expires (long expiry + lease refresh only by apps still using it). Catalog `id` should embed a version (`gemma-4-e2b-it@v2`) so selection store entries don't silently point at a changed file.
- Local file integrity at startup: currently only `length() > 1 MB`; add a stored-size check (descriptor `sizeBytes`) to catch truncation cheaply without full re-hash.

## 8. Risks and open questions
1. `/proc/self/fd` loading is unproven (SELinux re-open check; native may use `stat`/`pread`/mmap flags incompatible). Without it disk is not saved — set expectations.
2. 2.6 GB blob may exceed session/quota limits or take long to commit; blob store could evict under storage pressure; a user-visible label shows in settings.
3. Two sequential writes (download to `.part` and blob session) double I/O and temporarily need ~2× free space — stream into the blob session first and copy out, or accept it.
4. Cross-app permission declarations for the provider fallback conflict if the same name is declared by differing apps. Needs a test.
5. Per-app fallback means API 24–29 users (small share) get no benefit.
6. Version differences: LiteRT-LM 0.18.0 vs `main` — API may gain fd loading later; re-check on each upgrade (a native `fd` entry point would make Phase 2 trivial).
7. OEM BlobStore availability unmeasured.
8. Privacy: blobs with `allowPublicAccess` would let any app read the model — don't use it; use same-signature only.

## 9. Sources
- BlobStoreManager guide (read): https://developer.android.com/training/data-storage/shared/datasets
- BlobStoreManager reference (not fully read): https://developer.android.com/reference/android/app/blob/BlobStoreManager and …/BlobStoreManager.Session
- Documents/SAF & scoped storage (read): https://developer.android.com/training/data-storage/shared/documents-files
- LiteRT-LM Android guide (read): https://developers.google.com/edge/litert-lm/android
- LiteRT-LM source (read, `main`): https://github.com/google-ai-edge/LiteRT-LM/tree/main/kotlin/java/com/google/ai/edge/litertlm (`Config.kt`, `Engine.kt`, `LiteRtLmJni.kt`)
- Gemini Nano / AICore: https://developer.android.com/ai/gemini-nano
- ML Kit GenAI: https://developers.google.com/ml-kit/genai
- Android 16 behavior changes (read; nothing relevant to blob/provider/storage found): https://developer.android.com/about/versions/16/behavior-changes-all
- Project files: `core/llm/.../download/LlmModelManager.kt`, `core/llm/.../engine/LiteRtLlmEngine.kt`, `CLAUDE.md`
