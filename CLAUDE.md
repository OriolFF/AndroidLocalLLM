# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

A template-style Android app that runs a **local LLM on-device** to generate cooking recipes from a list of ingredients. Built as a reusable starting point for any app that needs on-device LLM inference — change the prompt, change the parser, ship a new feature.

## Stack (from `gradle/libs.versions.toml`)

| Layer | Choice |
|---|---|
| LLM | **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android:0.13.1`) — Google's recommended on-device runtime |
| Default model | `Gemma 4 E2B IT` in `.litertlm` format (~2.58 GB). Other catalog entries: Gemma 4 q4, Phi-3.5 Mini, Llama 3.2 3B |
| UI | Jetpack Compose + Material 3 (BOM-aligned), edge-to-edge, dynamic color |
| Architecture | MVI — `*Intent` / `*State` / `*Effect` per feature |
| State | `StateFlow` (state) + `Channel` (one-shot effects) |
| DI | Koin 4.0.0 (classic DSL, no KSP) |
| Build | Version catalog (`gradle/libs.versions.toml`) + type-safe project accessors (`enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`) + `:build-logic` convention plugins |
| Async | Kotlin Coroutines + `Flow` |
| Networking | OkHttp 4.12 (only used to download the model on first run) |
| Persistence | Preferences DataStore (`model_selection`, `model_download_progress`); model files cached in `Context.filesDir/llm/` |
| Background work | WorkManager 2.9.1 (downloads run as `CoroutineWorker` with foreground notification) |
| Tests | JUnit 4 + Truth + Turbine + `kotlinx-coroutines-test` |

Build tooling: AGP 9.2.1, Kotlin 2.2.21, KSP 2.3.2, Gradle 9.4.1, JVM target 17.

## Commands

The project does not commit the Gradle wrapper JAR — generate it once with a system Gradle (`gradle wrapper --gradle-version 9.4.1`) or by opening the project in Android Studio.

```bash
# Build
./gradlew assemble                          # everything
./gradlew :app:assembleDebug                # debug APK (per-ABI splits; see app/build.gradle.kts)
./gradlew :app:installDebug                 # stream install on connected device
adb shell am start -n com.llmlocal.recipe/.MainActivity

# Tests
./gradlew test                              # all unit tests across modules
./gradlew :core:llm:test                    # single module
./gradlew :core:llm:test --tests "com.llmlocal.core.llm.parser.RecipeParserTest"   # single class
./gradlew :core:llm:test --tests "*RecipeParserTest.parses*a*well-formed*recipe*"   # single test method

# Lint
./gradlew lint

# Verify module boundaries (must NOT show any back-edge)
./gradlew :feature:recipe:dependencies --configuration releaseRuntimeClasspath
```

Tests live in `src/test/kotlin/` next to the code they exercise. The LiteRT-LM `Engine` itself is **not** unit-tested (requires native libraries); tests cover the prompt builder, parser, use case, repository, and pure types.

## Module layout & dependency direction

Now-in-Android style. Type-safe project accessors are enabled, so use `projects.core.llm`, `projects.feature.recipe`, etc.

```
:app                          # Koin host, MainActivity, NavHost (single Activity)
:build-logic                  # Composite build, convention plugins
:core
  :core:common                # DispatcherProvider, Outcome, FlowExt, commonModule
  :core:model                 # Pure-Kotlin models: Ingredient, Recipe, LlmModelDescriptor
  :core:network               # OkHttp HttpClientProvider + networkModule
  :core:llm                   # LlmEngine (LiteRtLlmEngine), LlmModelManager, RecipePromptBuilder, RecipeParser,
                              #   LlmModelCatalog, DataStore-backed progress/selection stores, llmModule
  :core:domain                # Pure-Kotlin: RecipeRepository, GenerateRecipeUseCase, RecipeEvent (NO Koin)
  :core:data                  # RecipeRepositoryImpl + dataModule (binds domain → data)
:feature
  :feature:recipe             # MVI: RecipeScreen, RecipeRoute, RecipeViewModel
  :feature:modelmanagement    # MVI: ModelManagementScreen, ViewModel, ModelDownloadScheduler
```

Dependency direction (one-way, enforced):

```
:feature:*  ──►  :core:*
:core:data  ──►  :core:domain, :core:llm, :core:network, :core:model
:core:llm   ──►  :core:model, :core:common, :core:network, :core:domain (api)
:core:domain ──►  :core:llm.engine.LlmEngine, :core:llm.engine.LlmToken (API only)
:app        ──►  all (composition only)
```

`:core:domain` is pure-Kotlin with no Koin dependency — that's why `GenerateRecipeUseCase` is registered in `:core:data`'s `dataModule` rather than in `:core:domain`.

## Convention plugins (`:build-logic`)

All Android modules apply convention plugins from `:build-logic` (single source of truth for SDK versions, JVM target, library wiring):

- `llmlocal.android.application` — `:app` only (sets `compileSdk=34`, `minSdk=24`, `targetSdk=34`, JVM 17, BuildConfig)
- `llmlocal.android.library` — every other Android module
- `llmlocal.android.compose` — adds Compose + BOM
- `llmlocal.android.koin` — adds Koin runtime
- `llmlocal.android.feature` — composite: library + compose + koin + designsystem/domain/model/common + Compose tooling. Applied by `:feature:*`
- `llmlocal.android.room` — stub for future Room use

When adding a new module, pick the most specific existing convention plugin; only write a new one if none fit.

## MVI loop (per feature)

Each feature module follows the same pattern:

```
User action ──► FeatureIntent ──► ViewModel.onIntent()
                                          │
                                          ▼
                                  UseCase / Repo
                                          │
                                          ▼
                          Flow<FeatureEvent> (Partial / Done / Failed)
                                          │
                  ┌───────────────────────┴───────────────────────┐
                  ▼                                               ▼
        FeatureState  ◄─── update (StateFlow)         FeatureEffect ◄─── send (Channel)
                  │                                               │
                  ▼                                               ▼
              Compose UI                              Snackbar / Navigation (LaunchedEffect)
```

Key conventions used across both features:

- The **stateful wrapper** (`*Route.kt`) injects the ViewModel via `koinViewModel()`, collects `state` with `collectAsStateWithLifecycle()`, and forwards one-shot effects via `LaunchedEffect { viewModel.effects.collect { ... } }`.
- The **stateless screen** (`*Screen.kt`) takes `(state, onIntent)` and knows nothing about the ViewModel — easy to preview and test.
- All `*Intent` types are `sealed interface`s with `data class`/`data object` variants — add a case whenever a new gesture is added.
- `*State` is `@Immutable` and aggregates everything the UI needs; filters are applied eagerly inside `_state.update { it.copy(...).recompute() }` so the UI never re-derives.
- `*Effect` (snackbar / navigation / scroll commands) flows through a `Channel(BUFFERED)` exposed as `Flow<Effect>`.

## The LLM integration (the most non-obvious part)

All LLM-specific code lives in `:core:llm`. The boundary the rest of the app sees is `LlmEngine`:

```kotlin
interface LlmEngine {
    suspend fun initialize(): Outcome<Unit>
    fun generateStream(prompt: String): Flow<LlmToken>  // Partial(text) | Done | Error
    fun cancel()
}
```

Concrete implementation: `LiteRtLlmEngine` (in `:core:llm/engine`). It uses an `AtomicReference<Engine?>` so `initialize()` is idempotent, and a `Mutex` to serialize concurrent `generateStream` calls (LiteRT-LM does not yet support concurrent conversations on the same engine).

The recipe flow wires the engine end-to-end:

1. `RecipeViewModel.startGeneration()` calls `GenerateRecipeUseCase(realEngine, ingredients)`.
2. `GenerateRecipeUseCase` (in `:core:domain`) delegates to `RecipeRepository.generateRecipeStream(engine, ingredients)`.
3. `RecipeRepositoryImpl` (in `:core:data`) builds the prompt via `RecipePromptBuilder` (system + user) and collects `engine.generateStream(prompt)`, buffering the cumulative text and re-parsing on every chunk. On `Done`, it emits `RecipeEvent.Complete(recipe)`.
4. `RecipeParser` extracts `Title / Ingredients / Steps / Notes` from the cumulative text. **If you change the prompt format, update the parser to match** — the parser is the source of truth for the expected output structure.

### Model files and engine wiring

- **Catalog**: `LlmModelCatalog.ALL` is the single source of truth for known models. To add a model, add a `LlmModelDescriptor` (in `:core:model`) and include it in `ALL`.
- **Selection**: persisted in `LlmModelSelectionStore` (DataStore). The engine's `modelPathProvider` lambda resolves the path lazily on each `initialize()` from this store + `LlmModelManager.targetFile()`.
- **Download**: `LlmModelManager.download(descriptor)` streams the URL into `Context.filesDir/llm/<filename with .part tmp>` with throttled `DownloadProgress` emissions. Downloads are launched as a `ModelDownloadWorker` (`CoroutineWorker`) promoted to a foreground service — so the OS keeps the download alive when the app is backgrounded.
- **Backend selection**: compile-time via `BuildConfig.LLM_BACKEND` — `"CPU"` for the x86_64 emulator (where GPU/NPU fail), `"GPU"` for real devices. See `core/llm/build.gradle.kts`.

### Substituting a different engine or a fake

The `LlmEngine` is injected per-call into the use case (`GenerateRecipeUseCase.invoke(engine, ingredients)`), so tests can pass a fake engine. The real engine is registered with the `REAL_ENGINE_QUALIFIER` qualifier in `llmModule`:

```kotlin
single(named(REAL_ENGINE_QUALIFIER)) { LiteRtLlmEngine(...) } bind LlmEngine::class
```

For a demo/fake engine, add a second binding under a different qualifier and resolve it from a route or a flag.

### Important code locations

- Engine: `core/llm/src/main/kotlin/com/llmlocal/core/llm/engine/LiteRtLlmEngine.kt`
- Catalog: `core/llm/src/main/kotlin/com/llmlocal/core/llm/model/LlmModelCatalog.kt`
- Download manager: `core/llm/src/main/kotlin/com/llmlocal/core/llm/download/LlmModelManager.kt`
- Worker + factory: `core/llm/src/main/kotlin/com/llmlocal/core/llm/download/ModelDownloadWorker.kt` and `ModelDownloadWorkerFactory.kt`
- DI: `core/llm/src/main/kotlin/com/llmlocal/core/llm/di/LlmModule.kt`
- App-level wiring (Koin + WorkManager `Configuration.Provider`): `app/src/main/kotlin/com/llmlocal/recipe/RecipeApp.kt`

## Navigation

Single Activity (`MainActivity`) hosts a Compose `NavHost` with two destinations:

- `recipes` (start destination) — `:feature:recipe`
- `modelManagement` — `:feature:modelmanagement` (opened from the top-app-bar `Psychology` icon, or via `RecipeEffect.NavigateToModelManagement` from the recipe screen)

## Gotchas / non-obvious behaviour

- **WorkManager default initializer is disabled** in `AndroidManifest.xml` so that `RecipeApp` (a `Configuration.Provider`) can supply a Koin-aware `WorkerFactory`. Don't re-enable it.
- **Android 14+ requires the WorkManager foreground service to be declared explicitly** in `AndroidManifest.xml` with `android:foregroundServiceType="dataSync"` (matching what `ModelDownloadWorker.foregroundInfo` passes on API 34+). Without it the OS throws `IllegalArgumentException: foregroundServiceType 0x1 is not a subset of foregroundServiceType attribute 0x0` the moment a worker calls `setForeground(...)`. The library's own manifest entry isn't reliably merged into apps that disable the default WorkManager initializer, so we declare it ourselves next to the `<provider>`. Keep the type in sync with `ModelDownloadWorker.foregroundInfo`.
- **Notification channel** `model_downloads` is created idempotently in `RecipeApp.onCreate` before Koin starts.
- **Background continuation depends on `setForeground` succeeding.** The persistent notification you see while a download is running is itself a foreground-service notification — that service is what keeps the `ModelDownloadWorker` process alive after the user swipes the app from recents. `ModelDownloadWorker.doWork` calls `setForeground(initialInfo)` before it starts streaming bytes and treats that call as **mandatory**: if it throws (battery optimization, OEM task killers, etc.) the worker fails up front with a clear `KEY_FAILURE_REASON` rather than silently degrading to a notification-less download that the OS will kill the moment the app is backgrounded. Don't reintroduce a silent `catch (t: Throwable)` around `setForeground` — that exact path is what produced the original "Download failed: Software caused connection abort" report.
- **Closing the app mid-download surfaces as `SocketException`, not `CancellationException`.** When the user swipes the app away while a download is running, WorkManager cancels the worker and the OS closes OkHttp's socket. The blocked `input.read(...)` returns `SocketException("Software caused connection abort")` — which would otherwise fall through `ModelDownloadWorker.doWork`'s `catch (t: Throwable)` branch and be reported as a download failure. `LlmModelManager.downloadInto` reclassifies any non-CE exception caught while `coroutineContext[Job]?.isActive == false` as `CancellationException`, plus does a cooperative `coroutineContext.ensureActive()` at the top of the read loop so steady-state cancellation is responsive. With this, app-closed-mid-download reports `markCancelled` instead of `markFailed`. A real network failure (worker still active, socket fails) still reports `markFailed` as expected.
- **The `.part` file is deleted on cancellation/failure**, so a re-download starts from zero on the next launch. Resumable downloads (HTTP `Range` header against the partial file, with the 200/206/416 fallback) are not implemented — flag if that's wanted for the multi-GB models in the catalog.
- **`RecipeViewModel` must react to download completion, not just selection changes.** For a first-time user the default model is auto-selected (`LlmModelSelectionStore.current()` falls back to `DEFAULT_MODEL.id`), so `selectionStore.selectedModelId` emits exactly once at init and never again when the user downloads that default model in the model-management screen. `RecipeViewModel.observeSelection()` therefore also observes `ModelDownloadProgressStore.progressFor(currentId)` via `flatMapLatest` and re-runs `checkModel()` when the state transitions to `SUCCEEDED`. Without that second trigger the recipe screen stays stuck on `NoModelAvailable` even though the model file is on disk. RUNNING / FAILED / CANCELLED transitions don't change on-disk presence and are intentionally skipped.
- **Per-ABI APK splits** are enabled in `app/build.gradle.kts` because LiteRT-LM ships ~25–30 MB of native code per ABI — universal APKs blow past the install-size floor on low-storage devices.
- **The `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`** flag in `settings.gradle.kts` is what lets `projects.feature.recipe` resolve correctly. Don't remove it.
- **`LlmModelSelectionStore.current()` is non-suspending** — the engine's `modelPathProvider` lambda is not `suspend`, so it reads an `AtomicReference<String?>` snapshot mirrored by a long-running collector.
- **`.gitignore` excludes model caches** (`**/files/llm/`, `**/cache/llm/`) — the model is re-downloaded from HuggingFace on first run.

## Tests

Existing test coverage (good places to extend):

- `core/common/src/test/kotlin/com/llmlocal/core/common/result/OutcomeTest.kt` — `Outcome` sealed hierarchy helpers
- `core/model/src/test/kotlin/com/llmlocal/core/model/IngredientTest.kt`
- `core/domain/src/test/kotlin/com/llmlocal/core/domain/usecase/GenerateRecipeUseCaseTest.kt` — uses Turbine
- `core/llm/src/test/kotlin/com/llmlocal/core/llm/parser/RecipeParserTest.kt` — covers partial streams and missing sections
- `core/llm/src/test/kotlin/com/llmlocal/core/llm/prompt/RecipePromptBuilderTest.kt`

Pattern for engine-touching tests: pass a fake `LlmEngine` and a fake `RecipeRepository` to the use case; the engine interface is intentionally tiny for this reason.