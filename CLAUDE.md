# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Subagents run as herdr panes.** Inside herdr (`HERDR_ENV=1`), every subagent
> is spawned as a separate `claude` process via `scripts/herdr-spawn.sh` (or the
> `.claude/skills/herdr-subagents/SKILL.md` workflow) so the user can watch every
> agent in the herdr UI. New panes must use the **`claude-work` profile**
> (`CLAUDE_CONFIG_DIR=~/.claude-work`; plain `claude` routes to another provider).
> Never use the in-process Agent tool inside herdr; outside herdr, fall back to it.

> **Source of truth.** `README.md` is badly out of date (it still describes MediaPipe, Gemma 3, a single `:app`, and a `:feature:recipe` module that no longer exist). Trust this file and the code when they disagree.

## What this project is

A **white-label Android template** for apps that run an **LLM on-device** to turn a user-provided list of inputs into structured output. Each white-label app is its own Gradle module, `:app:<name>`, that supplies only:

- **design tokens** (palette, typography, shapes, hero gradients, success color) via a `DesignTokens` impl
- **system prompt + parser** via one `LlmTaskConfig<T>` value
- **UI copy** via `LlmTaskLabels` and `ModelManagementLabels` values (no `strings.xml` lookups in features; `strings.xml` holds `app_name` only)
- **launcher icon, `applicationId`, `AndroidManifest.xml`, `Application` class, `MainActivity`**
- **its own result type** (`Recipe`, `MedicalAdvice`) and the composable that renders it

Everything else (LiteRT-LM engine, model catalog/download/selection, the generic MVI task screen, model-management screen, design-system primitives, WorkManager plumbing) lives in `:core:*` and `:feature:*` and is reused verbatim.

Three apps ship and all build today:

- `:app:recipes` — ingredients → recipe. Package `com.llmlocal.recipe`, Application class `RecipeApp`.
- `:app:medical` — symptoms → lifestyle/wellness advice with a mandatory disclaimer. Package `com.llmlocal.medical`, Application class `MedicalApp`.
- `:app:recipekeeper` — **Recipe Keeper**, a recipe web clipper: in-app WebView browser, a floating "Clip recipe" button, on-device extraction of the recipe on the current page, pictures downloaded alongside it, Room library + detail screens. Package `com.llmlocal.recipekeeper`, Application class `RecipeKeeperApp`. It does **not** use `:feature:llm-task`; it uses `:feature:webclipper` + `:core:web`. Plan, UX spec and stream reports live in `docs/plans/`.

## Stack

| Layer | Choice |
|---|---|
| LLM runtime | **LiteRT-LM** `com.google.ai.edge.litertlm:litertlm-android:0.18.0`. `mediapipe-tasks-genai` is still pinned in the catalog but **not wired in** — don't re-enable it. |
| Default model | `Gemma 4 E2B IT` (`.litertlm`, ~2.58 GB). Catalog also has Gemma 4 q4, Phi-3.5 Mini, Llama 3.2 3B. |
| UI | Jetpack Compose + Material 3 (BOM 2026.09.00, Material3 1.4.0), edge-to-edge. `AppTheme` supports dynamic color but defaults to `dynamicColor = false` so brand tokens show. |
| Architecture | MVI per feature: `*Intent` / `*State` / `*Effect`, `StateFlow` for state, `Channel(BUFFERED)` for one-shot effects |
| DI | Koin 4.2.2, classic DSL, no KSP, no annotations |
| Async | Coroutines + `Flow` |
| Networking | OkHttp 5.5 — only for model download. `Response.body` is non-null in 5.x. |
| Persistence | Preferences DataStore: `model_selection` and `model_download_progress`. Model files in `Context.filesDir/llm/`. Recipe Keeper additionally uses **Room 2.8.5** (KSP 2.3.12, via `llmlocal.android.room`) with pictures under `filesDir/recipes/<id>/`. |
| Web | `androidx.webkit` 1.17.1 WebView + injected `capture.js`/Readability.js (`:core:web`); **Coil 3.6.3** for images (Recipe Keeper only). |
| Background | WorkManager 2.12.0, `CoroutineWorker` promoted to a `dataSync` foreground service |
| Tests | JUnit 4 + Truth + Turbine + `kotlinx-coroutines-test` |

Build: AGP 9.4.1 (needs Gradle ≥ 9.6), Kotlin 2.4.21, Gradle 9.8.1 (wrapper **is** committed), `compileSdk` 37, `targetSdk` 34, `minSdk` 24, JVM target 17. Lifecycle 2.11, Navigation 2.10, Activity 1.13, core-ktx 1.19, DataStore 1.2, Coroutines 1.11. `gradle/gradle-daemon-jvm.properties` pins the daemon to a JetBrains **JDK 21** toolchain, auto-provisioned via the foojay resolver, so the build works even when the launcher JVM is newer (this machine runs JDK 25).

## Commands

```bash
# Build
./gradlew assemble                          # every module, both apps
./gradlew :app:recipes:assembleDebug        # per-ABI split APKs under app/recipes/build/outputs/apk/debug/
./gradlew :app:medical:assembleDebug
./gradlew :app:recipekeeper:assembleDebug
./gradlew :app:recipes:installDebug && adb shell am start -n com.llmlocal.recipe/.MainActivity
./gradlew :app:medical:installDebug && adb shell am start -n com.llmlocal.medical/.MainActivity
./gradlew :app:recipekeeper:installDebug && adb shell am start -n com.llmlocal.recipekeeper/.MainActivity

# Tests
./gradlew test                              # all modules
./gradlew :core:common:test                 # one module
./gradlew :core:web:testDebugUnitTest :feature:webclipper:testDebugUnitTest :app:recipekeeper:testDebugUnitTest
./gradlew :core:common:test --tests "com.llmlocal.core.common.result.OutcomeTest"   # one class

# Lint
./gradlew lint

# Module boundary check (must show no :feature → :app or :core → :feature edge)
./gradlew :feature:llm-task:dependencies --configuration releaseRuntimeClasspath
./gradlew :feature:webclipper:dependencies --configuration releaseRuntimeClasspath
```

**Test reality check:** unit tests exist for `:core:common` (`OutcomeTest`), `:core:domain` (engine default-method delegation), `:core:llm` (`FakeLlmEngine`, `ModelReadinessObserverTest`), `:core:web` (JSON-LD, durations, snapshot mapping, capture, downloader), `:feature:webclipper` (browser + extraction ViewModels, truncation heuristic) and `:app:recipekeeper` (schema mapper, parsers, clip config, pipeline round trip, library/detail ViewModels). The original `RecipeParser`/`MedicalParser` and `LlmTaskRepositoryImpl` still have none. `FakeLlmEngine` lives in `core/llm/src/test` (no test fixtures module); other modules copy it. The LiteRT `Engine` itself needs native libs and is not unit-testable. `org.json` is a stub on the JVM: tests touching `JSONObject` add `testImplementation(libs.org.json)`.

## Architecture rules (apply to every new feature and every change)

1. **ViewModels depend on use cases, never on repositories, stores, engines or downloaders directly.** A `*ViewModel` constructor takes use cases (plus `DispatcherProvider`, labels/config and route parameters) and maps use-case results/events to `*State`/`*Effect`. Orchestration (call the engine, download pictures, write the repository, undo windows) lives in a use case. The lone exception that still exists is historical (see below) and must not be copied.
2. **One package per feature, split into `presentation`, `domain` and `data`.** Inside a feature module (`:feature:<x>`) or an app's feature area (`app/<name>/src/main/kotlin/com/llmlocal/<name>/<feature>/`):
   - `presentation/` — composables, `*Route`, `*Screen`, `*ViewModel`, MVI `State`/`Intent`/`Effect`, labels.
   - `domain/` — use cases, domain models, repository *interfaces*; pure Kotlin, no Android, no Koin.
   - `data/` — repository implementations, Room/DataStore/network/file sources, mappers, Koin bindings for data.
   Dependencies point inward: `presentation → domain ← data`. `presentation` never imports `data`.
3. Keep MVI as described below; keep brand values and copy in the app module via tokens/labels.

**Current state vs these rules (2026-10-19):** `LlmTaskViewModel`, `ExtractionViewModel` and `RecipeDetailViewModel` still inject stores/engines/downloaders directly and the Recipe Keeper code is split by screen (`library/`, `detail/`, `clip/`, `data/`) rather than feature/presentation-domain-data. Any work that touches these files must move the touched logic into use cases and the new layout rather than extending the old shape.

## Module layout & dependency direction

```
:app:recipes, :app:medical, :app:recipekeeper   # composition roots — own tokens, config, labels, icon, manifest
:build-logic                       # included build with the convention plugins
:core:common                       # DispatcherProvider, Outcome, FlowExt, commonModule
:core:model                        # LlmModelDescriptor, ModelFamily — no domain types
:core:designsystem                 # DesignTokens + AppTheme + AppScaffold, GradientHero, StatusDot, EmptyState…
:core:network                      # OkHttp HttpClientProvider + networkModule
:core:llm                          # LiteRtLlmEngine, LlmModelCatalog, LlmModelManager, ModelDownloadWorker,
                                   #   ModelDownloadProgressStore, LlmModelSelectionStore, llmModule
:core:domain                       # PURE KOTLIN (kotlin-jvm plugin): LlmTaskConfig, PromptBuilder, TaskParser,
                                   #   LlmTaskEvent, LlmTaskRepository, GenerateLlmTaskUseCase
:core:data                         # LlmTaskRepositoryImpl + dataModule
:core:web                          # PageSnapshot/PageCapture (WebView + capture.js/Readability), JsonLdExtractor, IsoDuration,
                                   #   ImageDownloader, webModule — brand-free page capture for clipper-style apps
:feature:llm-task                  # generic MVI scaffold: LlmTaskViewModel/Route/Screen, LlmTaskLabels, input components
:feature:modelmanagement           # MVI: ModelManagementViewModel/Route/Screen, ModelDownloadScheduler, WorkInfoObserver
:feature:webclipper                # generic web clipper over WebClipConfig<T>/ClipSink<T>: WebViewHolder, Browser/Extraction
                                   #   ViewModels + routes, ClipFab, candidate sheets, WebClipperLabels, webClipperModule
```

Allowed edges (one-way):

```
:app:*            → every :core:* and :feature:*     (added for you by the white-label-app plugin, incl. :core:web, :feature:webclipper)
:feature:*        → :core:designsystem/domain/model/common (feature plugin) + :core:llm, :core:data, :core:web (declared per module)
:core:web         → :core:common, :core:network, :core:model
:core:data        → :core:domain, :core:llm, :core:model
:core:llm         → api(:core:common, :core:model, :core:domain) + :core:network
:core:domain      → :core:model, :core:common   (no Android, no Koin)
```

`:core:llm` exposes `:core:common`, `:core:model` and `:core:domain` as `api(...)`, so anything depending on `:core:llm` sees those types without a direct edge. Everything else is `implementation(...)`.

**`LlmEngine` / `LlmToken` / `LlmRequest` live in `:core:domain`** (package `com.llmlocal.core.llm.engine`, because `:core:domain` must not depend on `:core:llm`); `:core:llm/engine/LiteRtLlmEngine.kt` implements that interface. There is only one copy. `LlmEngine.generateStream(request: LlmRequest)` is the real method; `generateStream(prompt: String)` is a default method delegating to it. `LlmRequest` carries `systemInstruction`, `maxOutputTokens`, `jsonSchema` (constrained decoding) and `temperature`; **no image input by design** (Recipe Keeper stores pictures, it never sends them to the model).

**Project deps are written as `project(":core:llm")` strings**, both in convention plugins and in module `build.gradle.kts` files. `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` is on in `settings.gradle.kts` (so `projects.core.llm` would also resolve) but nothing uses it yet; follow the existing string style for consistency and don't remove the flag.

## Convention plugins (`:build-logic/convention`)

- `llmlocal.android.library` — every `:core:*` Android lib (SDK levels, JVM 17, `-Xjvm-default=all`)
- `llmlocal.android.compose` — Compose compiler + BOM; must be applied after an Android plugin
- `llmlocal.android.koin` — Koin core/android runtime
- `llmlocal.android.feature` — library + compose + koin + designsystem/domain/model/common + lifecycle/viewmodel + tooling. Used by `:feature:*`.
- `llmlocal.android.white-label-app` — application + compose + koin + **every** `:core:*`/`:feature:*` + navigation/work/lifecycle + per-ABI splits (`arm64-v8a`, `x86_64`, `armeabi-v7a`, `x86`, plus a universal APK). **Use this for every `:app:<name>`**; the module's own `build.gradle.kts` then only sets `namespace`, `applicationId`, version, and release proguard.
- `llmlocal.android.application` — raw app plugin used by white-label-app; not applied directly
- `llmlocal.android.room` — applies KSP + the Room Gradle plugin, adds `room-runtime` + `ksp(room-compiler)`, `schemaDirectory("$projectDir/schemas")`. Used by `:app:recipekeeper` (commit the generated `schemas/**/N.json`). The root `build.gradle.kts` must keep `alias(libs.plugins.androidx.room) apply false` or the convention plugin cannot see the Room classes.

SDK constants live in `build-logic/convention/src/main/kotlin/com/llmlocal/convention/AndroidConfig.kt`.

## How a white-label app plugs in

### Brand: `DesignTokens`

`:core:designsystem` has no palette of its own. `AppTheme(tokens = RecipeTokens) { … }` installs the tokens into `LocalDesignTokens` and builds `MaterialTheme` from them. Shared components that need non-M3 brand values read `designTokens.heroLightGradient`, `designTokens.successColor`, etc. Never hardcode a brand color in `:core:*` or `:feature:*`.

### Behaviour: `LlmTaskConfig<T>`

```kotlin
interface PromptBuilder { fun systemPrompt(): String; fun userPrompt(inputs: List<String>): String }
fun interface TaskParser<T> { fun parse(cumulativeText: String): T? }
data class LlmTaskConfig<T>(val promptBuilder: PromptBuilder, val parser: TaskParser<T>)
```

Each app defines one value, e.g. `val RecipeTaskConfig = LlmTaskConfig(RecipePromptBuilder(), RecipeParser())`. The parser is the source of truth for the output format the prompt asks for; **change the prompt format → change the parser**.

### Copy: `LlmTaskLabels` and `ModelManagementLabels`

Both are `@Immutable data class`es of every string (and a few string-producing lambdas) the shared screens render. They reach the UI differently:

- `LlmTaskLabels` is passed **as a plain parameter** from `LlmTaskRoute` down to `LlmTaskScreen` and its components. There is no `LocalLlmTaskLabels` composition local, despite a stale KDoc reference.
- `ModelManagementLabels` is provided via `LocalModelManagementLabels` inside `ModelManagementRoute`; components read `modelManagementLabels`.

### Wiring: how `config` and `labels` reach the ViewModel

`LlmTaskRoute(config, labels, renderOutput, …)` creates its ViewModel with `koinViewModel { parametersOf(config, labels) }`, and `llmTaskModule`'s `viewModel { params -> … }` pulls them out of the parameters. The per-app Koin module (`recipesConfigModule`, `medicalsConfigModule`) that also registers them as `single`s is a convenience for previews/tests, **not** how the screen gets them. The app's screen wrapper (`RecipeScreen`, `MedicalScreen`) casts `state.result as? Recipe` inside `renderOutput` since `LlmTaskState.result` is `Any?`.

### Per-app file shape (recipes shown; medical mirrors it 1:1)

```
app/recipes/src/main/kotlin/com/llmlocal/recipe/
├── RecipeApp.kt                    # Application + Configuration.Provider; startKoin(common, network, llm, data, llmTask, modelManagement, recipesConfig)
├── MainActivity.kt                 # AppTheme(tokens = RecipeTokens) + NavHost("recipes", "modelManagement") + AppScaffold top bar
├── RecipeScreen.kt                 # LlmTaskRoute(config = RecipeTaskConfig, labels = RecipeLabels, renderOutput = StreamedRecipeCard)
├── domain/Recipe.kt, Ingredient.kt
├── prompt/RecipePromptBuilder.kt
├── parser/RecipeParser.kt
├── config/RecipeTaskConfig.kt, RecipeLabels.kt, RecipeModelManagementLabels.kt, RecipesConfigModule.kt
├── designsystem/RecipeTokens.kt
├── output/StreamedRecipeCard.kt
└── previews/
```

### Adding a third app

1. `cp -R app/recipes app/<name>`, rename package `com.llmlocal.recipe` → `com.llmlocal.<name>` everywhere (Kotlin, manifest `android:name`, `namespace`, `applicationId`).
2. `include(":app:<name>")` in `settings.gradle.kts`.
3. Replace launcher vectors in `res/drawable/` + `res/mipmap-anydpi-v26/` and `ic_launcher_background`; set `app_name` in `res/values/strings.xml`.
4. Write `<Name>Tokens : DesignTokens`, `<Name>PromptBuilder`, `<Name>Parser`, `<Name>TaskConfig`, `<Name>Labels`, `<Name>ModelManagementLabels`, a result type, and its card.
5. Point `MainActivity` and the screen wrapper at the new values. `Application.onCreate` stays the same except for the per-app config module.
6. `./gradlew :app:<name>:assembleDebug`. No shared module changes.

If an app needs UX the generic `LlmTaskScreen` can't express, write a new `:feature:<x>` that reuses `GenerateLlmTaskUseCase` + `LlmTaskConfig<T>`. `:feature:webclipper` is the worked example: the app supplies a `WebClipConfig<T>` (detect candidates on a `PageSnapshot`, render the LLM input, deterministic fallback, picture ranking), a `ClipSink<T>` (persistence), `WebClipperLabels`, and a `renderDraft` composable for the streaming skeleton; see `app/recipekeeper/.../clip/`, `MainActivity.kt` and `docs/plans/recipekeeper-app-plan.md`.

### Recipe Keeper pipeline (`:app:recipekeeper` + `:feature:webclipper` + `:core:web`)

1. `WebViewHolder` owns one application-context `WebView`; after `onPageFinished`, `BrowserViewModel` runs a cheap `quickScanStructured` (JSON-LD/microdata count) to badge the FAB.
2. Tap → `PageCapture.capture()` injects Readability.js + `capture.js` via `evaluateJavascript` (the IIFE returns an object, so the result is single-encoded JSON) → `PageSnapshot` (JSON-LD nodes, readable text, heading sections, images with layout data, OG tags).
3. `RecipeKeeperClipConfig.detect`: Tier A = schema.org `Recipe` nodes → STRUCTURED candidates; Tier B = heading/ingredient-regex heuristics → TEXT candidates; else WholePage. One candidate → extraction; several → `CandidateSheet`.
4. `ExtractionViewModel` renders the candidate (compact JSON-LD fields or trimmed section text, ≤ `budgetChars` = 9,000 chars) and runs `GenerateLlmTaskUseCase(..., LlmRequestOptions(systemInstruction = true, maxOutputTokens = 1024, temperature = 0.2))`; pictures are picked by heuristics (`pickPictures`) and downloaded in parallel with `ImageDownloader`. Parse failure on a STRUCTURED candidate falls back to `SchemaOrgRecipeMapper`.
5. `RecipeKeeperClipSink.save` moves pictures to `filesDir/recipes/<id>/` and upserts into Room; the route navigates to the detail screen.

Model readiness for non-`LlmTask` screens comes from `:core:llm/readiness/ModelReadinessObserver` (`observe(scope): StateFlow<ModelReadiness>`); `LlmTaskViewModel` delegates to it too. Recipe Keeper has **no one-tap default download**: every "no model" state navigates to the shared model-management screen.

## Generation pipeline (the non-obvious core)

1. `LlmTaskViewModel.startGeneration()` → `GenerateLlmTaskUseCase(realEngine, config, inputs)`.
2. `LlmTaskRepositoryImpl.generateStream` builds the prompt as `systemPrompt() + "\n\n" + userPrompt(inputs)`, collects `engine.generateStream(prompt)` and maps `LlmToken` → `LlmTaskEvent`:
   - `LlmToken.Partial(text)` → append to a buffer, emit `LlmTaskEvent.Token(delta)`
   - `LlmToken.Done` → **parse once** here: `parser.parse(buffer)`; non-null → `Complete(result)`, null → `Failed(IllegalStateException)`
   - `LlmToken.Error` / upstream exception → `Failed`
3. The ViewModel appends `Token` deltas to `state.streamedText` (so the card can show raw text while streaming) and sets `state.result` only on `Complete`. Parsing is **not** re-run per chunk.

`LiteRtLlmEngine`:
- `initialize()` is idempotent via `AtomicReference<Engine?>`; it fails `Outcome.Failure` if the model file is missing or under 1 MB.
- `generateStream` opens a fresh `Conversation` per call under a `Mutex` (LiteRT-LM can't run concurrent conversations on one engine) and closes it in `finally`. `EngineConfig.maxNumTokens` is set explicitly (default 4096); `ConversationConfig.maxOutputToken`, `systemInstruction` and `enableResponseFormat`/`ResponseFormat.json` come from the `LlmRequest`.
- **`cancel()` is real**: it calls `Conversation.cancelProcess()` on the live conversation, and the flow also cancels native compute when the collector is cancelled (LiteRT-LM's own `callbackFlow` has an empty `awaitClose`, so collector cancellation alone never stopped inference). A cancelled stream completes without a terminal token.
- Backend comes from `BuildConfig.LLM_BACKEND` in `core/llm/build.gradle.kts`, **per build type**: `debug` → `"CPU"`, `release` → `"GPU"`. So a debug build on a real phone also runs on CPU. The emulator's x86_64 GPU/NPU paths fail, which is why debug is CPU.
- Registered in `llmModule` as `single(named(REAL_ENGINE_QUALIFIER)) { LiteRtLlmEngine(...) } bind LlmEngine::class`; the `bind` is required or `get<LlmEngine>(named(...))` fails at runtime. A fake engine goes under a different qualifier.

## Models: catalog, selection, download

- **Catalog**: `LlmModelCatalog.ALL` (in `:core:llm/model`) is the single list; `DEFAULT_MODEL` is Gemma 4 E2B. Add a `LlmModelDescriptor` and append to `ALL`.
- **Selection**: `LlmModelSelectionStore` (DataStore `model_selection`). `selectedModelId: Flow` falls back to `DEFAULT_MODEL.id` when unset. `current()` is **non-suspending** and reads an `AtomicReference` mirror that `start()` keeps in sync; `llmModule` calls `start()` when the single is created. The engine's `modelPathProvider` lambda resolves `catalog.findById(selectionStore.current()) → manager.targetFile(descriptor)` lazily on each `initialize()`.
- **Progress**: `ModelDownloadProgressStore` (DataStore `model_download_progress`) holds per-model `DownloadState` (`IDLE/RUNNING/SUCCEEDED/FAILED/CANCELLED`), bytes, speed, ETA, failure reason, keyed `"<modelId>__state"` etc. Both ViewModels observe it.
- **Download**: `ModelDownloadScheduler.enqueue(descriptor)` submits a `OneTimeWorkRequest<ModelDownloadWorker>` as unique work `model-download-<id>` with `ExistingWorkPolicy.KEEP`, tag `model-download`, network-connected constraint. `WorkInfoObserver.observe(tag)` feeds `WorkInfo` back to the management ViewModel. The worker calls `LlmModelManager.download(descriptor)`, which streams into `<file>.part` and renames on success, verifying `sha256` if the descriptor has one.

## Gotchas

- **WorkManager default initializer is removed in each app's `AndroidManifest.xml`** (`tools:node="remove"` on `androidx.work.WorkManagerInitializer`) so the `Application` can act as `Configuration.Provider` and supply the Koin-built `ModelDownloadWorkerFactory`. Keep this in any new app manifest.
- **The `dataSync` foreground service is declared in `core/llm/src/main/AndroidManifest.xml`** (`androidx.work.impl.foreground.SystemForegroundService` with `android:foregroundServiceType="dataSync"`, `tools:node="merge"`) and merges into every app. The app manifests also repeat it, which is harmless. Each app manifest must still request the `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` and `POST_NOTIFICATIONS` permissions. Without the service type, Android 14+ throws `foregroundServiceType 0x1 is not a subset of foregroundServiceType attribute 0x0` on `setForeground`, and the androidx.work lint rule `SpecifyForegroundServiceType` fails `./gradlew lint` in `:core:llm`. Keep the type in sync with `ModelDownloadWorker.foregroundInfo` (`FOREGROUND_SERVICE_TYPE_DATA_SYNC` on API 34+).
- **`setForeground` is mandatory in `ModelDownloadWorker.doWork`.** If it throws, the worker records a clear failure reason and returns `failure()` instead of silently downloading without a foreground service (which the OS would kill on background). Don't wrap it in a swallowing `catch`.
- **Notification channel `model_downloads`** is created idempotently in `Application.onCreate` before `startKoin` (`ModelDownloadWorker.ensureNotificationChannel`).
- **Swiping the app away mid-download surfaces as `SocketException`, not `CancellationException`.** `LlmModelManager.downloadInto` re-throws any non-CE exception as `CancellationException` when `coroutineContext[Job]?.isActive == false`, and calls `ensureActive()` at the top of each read loop. So the worker's `catch (CancellationException)` branch runs `markCancelled` + deletes the file, while a genuine network failure (job still active) still hits `markFailed`.
- **No resumable downloads.** The `.part` file is deleted on cancel/failure; a retry starts from zero. HTTP `Range` support is not implemented. Flag it if a multi-GB model makes this painful.
- **`LlmTaskViewModel` must react to download completion, not just selection.** For a fresh install the default model is already "selected", so `selectedModelId` emits once and never again when the user downloads that model. `observeSelection()` therefore `flatMapLatest`s into `progressStore.progressFor(selectedId)` and re-runs `CheckModel` when the state is `null` or `SUCCEEDED`. Without this the task screen stays on `NoModelAvailable` after the download finishes.
- **Per-ABI splits are on** because LiteRT-LM ships ~25–30 MB of native code per ABI. `:core:llm` additionally filters NDK ABIs to `arm64-v8a`, `armeabi-v7a`, `x86_64`.
- **Remaining hardcoded copy in shared code:** `ModelDownloadWorker`'s notification titles and `DownloadProgressBar`'s "s left"/"m left". The model-management snackbars now come from `ModelManagementLabels` (`modelSelectedSnackbar`, `modelRemovedSnackbar`, `modelNotDownloadedSnackbar`; the ViewModel receives labels via `parametersOf`). `AppErrorView` only renders its button when `retryLabel` is passed.
- **Shared streaming primitives** live in `:core:designsystem/component`: `PhaseProgress`/`PhaseItem`/`PhaseStatus`, `StreamingText`/`StreamingCursor`, `SkeletonLine`, `SectionHeader` (optional `trailing` slot), `NumberBadge` (`selected`). Don't re-create them in an app.
- **Emulator for LLM testing on Apple Silicon.** The default `Medium_Phone_API_36.1` AVD (2 GB RAM, 1 core, ~6 GB data) cannot hold a model. Use the `LlmPhone_API_36` AVD (`~/.android/avd/LlmPhone_API_36.avd`, cloned from it: 8 GB RAM, 6 cores, 32 GB data, `fastboot.forceColdBoot=yes`), started with `emulator -avd LlmPhone_API_36 -port 5556 -no-snapshot`. **It needs `kernel.parameters=arm64.nosme` in its `config.ini`**: on M4 hosts the hypervisor leaks the SME feature bits into the guest but cannot execute SME instructions, so LiteRT-LM's XNNPACK kernels die with `SIGILL` on `rdsvl` inside `liblitertlm_jni.so` during `Engine.initialize()`. With the flag the Gemma 4 E2B model loads in ~5 s and the engine reports `Engine ready … backend=CPU`. Put a model in place without downloading: `adb -s emulator-5556 push <file>.litertlm /data/local/tmp/m && adb -s emulator-5556 shell "run-as <applicationId> sh -c 'mkdir -p files/llm && cat /data/local/tmp/m > files/llm/<catalog filename>'"` (the readiness observer treats an existing file as installed). A local copy of `gemma-4-E2B-it.litertlm` lives in `~/.cache/llmlocal-eval/` (used by `tools/eval`).
- **WebView gotchas (`:core:web`, `:feature:webclipper`):** `evaluateJavascript` returns JSON-encoded results (`undefined` arrives as the string `"null"`); the WebView must be held with the application context and its lifecycle driven explicitly, never held by a ViewModel with an Activity context; the shared emulator image is arm64 with ~220 MB free, so `installDebug` fails — use `adb install -r` of the arm64 split, and no 2.6 GB model fits on it (device QA of the LLM step needs a real phone).
- `.gitignore` excludes `**/files/llm/` and `**/cache/llm/`; models are always re-downloaded from HuggingFace on a fresh install.

## Key file locations

- Engine: `core/llm/src/main/kotlin/com/llmlocal/core/llm/engine/LiteRtLlmEngine.kt`
- Catalog: `core/llm/src/main/kotlin/com/llmlocal/core/llm/model/LlmModelCatalog.kt`
- Download: `core/llm/src/main/kotlin/com/llmlocal/core/llm/download/{LlmModelManager,ModelDownloadWorker,ModelDownloadWorkerFactory,ModelDownloadProgressStore}.kt`
- Selection: `core/llm/src/main/kotlin/com/llmlocal/core/llm/selection/LlmModelSelectionStore.kt`
- LLM DI + backend switch: `core/llm/src/main/kotlin/com/llmlocal/core/llm/di/LlmModule.kt`, `core/llm/build.gradle.kts`
- Domain contracts: `core/domain/src/main/kotlin/com/llmlocal/core/domain/task/{PromptBuilder,TaskParser,LlmTaskConfig}.kt`
- Repository: `core/data/src/main/kotlin/com/llmlocal/core/data/repository/LlmTaskRepositoryImpl.kt` (`LlmRequestOptions` + `LlmTaskParseException` in `core/domain/.../repository/LlmTaskRepository.kt`)
- Model readiness: `core/llm/src/main/kotlin/com/llmlocal/core/llm/readiness/{ModelReadiness,ModelReadinessObserver}.kt`
- Page capture: `core/web/src/main/kotlin/com/llmlocal/core/web/{PageSnapshot,PageCapture,WebViewPageCapture,JsonLdExtractor,IsoDuration,ImageDownloader}.kt`, JS in `core/web/src/main/assets/webclipper/`
- Web clipper: `feature/webclipper/src/main/kotlin/com/llmlocal/feature/webclipper/{WebClipConfig,ClipSink,BrowserViewModel,ExtractionViewModel,WebViewHolder}.kt`, `ui/`, `mvi/`, `di/WebClipperModule.kt`
- Recipe Keeper: `app/recipekeeper/src/main/kotlin/com/llmlocal/recipekeeper/{clip,parser,prompt,data,library,detail}/`, `MainActivity.kt`; plan + reports under `docs/plans/`
- Generic task MVI: `feature/llm-task/src/main/kotlin/com/llmlocal/feature/llmtask/{LlmTaskViewModel,LlmTaskRoute,LlmTaskScreen}.kt` and `mvi/`
- Model management: `feature/modelmanagement/src/main/kotlin/com/llmlocal/feature/modelmanagement/`
- Design tokens contract: `core/designsystem/src/main/kotlin/com/llmlocal/core/designsystem/theme/{Tokens,Theme}.kt`
- App wiring examples: `app/recipes/src/main/kotlin/com/llmlocal/recipe/{RecipeApp,MainActivity,RecipeScreen}.kt`, `app/medical/src/main/kotlin/com/llmlocal/medical/`, `app/recipekeeper/src/main/kotlin/com/llmlocal/recipekeeper/{RecipeKeeperApp,MainActivity}.kt`
