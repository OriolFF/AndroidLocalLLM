# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Source of truth.** `README.md` is out of date on stack (LLM runtime, model, Gradle version) and module layout — trust this file when they disagree.

## What this project is

A **white-label Android template** for apps that run an **LLM on-device** to generate structured output from a user-provided list of inputs. Each white-label app is its own Gradle module — `:app:<name>` — that supplies:

- its own **design tokens** (palette, typography, shapes, gradient brushes) via `DesignTokens`
- its own **system prompt + parser** via `LlmTaskConfig<T>`
- its own **UI copy** via per-feature labels (e.g. `LlmTaskLabels`, `ModelManagementLabels`)
- its own **launcher icon, app name, `applicationId`, `AndroidManifest.xml`, `Application` class**
- its own **per-app domain types** (e.g. `Recipe`, `MedicalAdvice`)

Everything else — the LLM engine, model management, MVI scaffold, design-system primitives, WorkManager downloads — lives in the shared `:core:*` and `:feature:*` modules and is reused verbatim.

The template ships two demo apps:

- **`:app:recipes`** — generate a cooking recipe from a list of ingredients (warm orange brand).
- **`:app:medical`** — (planned) generate lifestyle advice from a list of symptoms (teal/blue brand).

> The first app in the repo is `:app:recipes`; the rest of this file uses recipe examples for concreteness.

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

> `mediapipe-tasks-genai` remains pinned in `gradle/libs.versions.toml` (the previous runtime) but is **not wired in**. LiteRT-LM is the only engine in use — don't re-enable MediaPipe.

## Commands

The project does not commit the Gradle wrapper JAR — generate it once with a system Gradle (`gradle wrapper --gradle-version 9.4.1`) or by opening the project in Android Studio.

```bash
# Build
./gradlew assemble                          # everything
./gradlew :app:recipes:assembleDebug        # recipe app debug APK (per-ABI splits)
./gradlew :app:recipes:installDebug         # stream install on connected device
adb shell am start -n com.llmlocal.recipe/.MainActivity

# Tests
./gradlew test                              # all unit tests across modules
./gradlew :core:llm:test                    # single module
./gradlew :app:recipes:test                 # per-app tests

# Lint
./gradlew lint

# Verify module boundaries (must NOT show any back-edge)
./gradlew :feature:llm-task:dependencies --configuration releaseRuntimeClasspath
```

Tests live in `src/test/kotlin/` next to the code they exercise. The LiteRT-LM `Engine` itself is **not** unit-tested (requires native libraries); tests cover the prompt builder, parser, use case, repository, and pure types.

## Module layout & dependency direction

Now-in-Android style. Type-safe project accessors are enabled, so use `projects.core.llm`, `projects.feature.llm_task`, etc.

```
:app:recipes                       # demo 1 — recipe generator (own tokens, prompt, parser, copy, icon)
:app:medical                       # demo 2 — medical-advice generator (planned)
:build-logic                       # Composite build, convention plugins
:core
  :core:common                     # DispatcherProvider, Outcome, FlowExt, commonModule
  :core:model                      # Generic models: LlmModelDescriptor, ModelFamily (no domain types)
  :core:designsystem               # DesignTokens interface + AppTheme(tokens, …) + shared components
  :core:network                    # OkHttp HttpClientProvider + networkModule
  :core:llm                        # LiteRtLlmEngine, LlmModelManager, downloads, catalog, llmModule
  :core:domain                     # LlmTaskEvent<T>, LlmTaskRepository, GenerateLlmTaskUseCase,
                                   #   LlmTaskConfig<T>, PromptBuilder, TaskParser<T> (pure-Kotlin)
  :core:data                       # LlmTaskRepositoryImpl + dataModule
:feature
  :feature:llm-task                # Generic MVI scaffold: LlmTaskViewModel, LlmTaskScreen, LlmTaskRoute,
                                   #   LlmTaskLabels, InputsCard, InputRow, InputChips, NoModelBanner
  :feature:modelmanagement         # MVI: ModelManagementScreen, ViewModel, ModelDownloadScheduler
```

Dependency direction (one-way, enforced):

```
:app:<name>       ──►  every :core:* + every :feature:*  (composition only)
:feature:*        ──►  :core:designsystem, :core:domain, :core:model, :core:common,
                       :core:data (the dataModule for the use case),
                       :core:llm (for download/cache/selection APIs)
:core:data        ──►  :core:domain, :core:llm, :core:network, :core:model
:core:llm         ──►  :core:model, :core:common, :core:network, :core:domain (api)
:core:domain      ──►  local copies of LlmEngine + LlmToken (no compile-time dep on :core:llm)
```

`:core:domain` is pure-Kotlin with no Koin dependency. To allow it to define the `LlmEngine` and `LlmToken` contracts without a compile-time dep on `:core:llm`, those two types are duplicated as local files inside `:core:domain` (same `com.llmlocal.core.llm.engine` package) — `:core:llm/engine/LiteRtLlmEngine.kt` implements the same interface. **Don't change one without the other.**

**`api(...)` vs `implementation(...)` convention**: use `api(...)` only when a module exposes types from a downstream module in its own public surface. `:core:llm` declares `:core:domain` as `api(...)` so consumers of `:core:llm` automatically see `:core:domain` types without a direct edge. Everything else stays `implementation(...)` to keep runtime classpaths tight.

## Convention plugins (`:build-logic`)

All Android modules apply convention plugins from `:build-logic` (single source of truth for SDK versions, JVM target, library wiring):

- `llmlocal.android.application` — raw application module (SDK / JVM / BuildConfig only). Rarely used directly; `:app:<name>` modules use the composite `white-label-app` plugin below.
- `llmlocal.android.library` — every other Android library module
- `llmlocal.android.compose` — adds Compose + BOM
- `llmlocal.android.koin` — adds Koin runtime
- `llmlocal.android.feature` — composite: library + compose + koin + designsystem/domain/model/common + Compose tooling. Applied by `:feature:*`
- `llmlocal.android.white-label-app` — composite: application + compose + koin + **every** `:core:*` and `:feature:*` project dep + per-ABI splits + Compose preview tooling. Applied by `:app:<name>` modules. **Use this for any new `:app:<name>`** — don't list deps by hand.
- `llmlocal.android.room` — stub for future Room use

When adding a new module, pick the most specific existing convention plugin; only write a new one if none fit.

## White-label template architecture

### `DesignTokens` — per-app brand

`:core:designsystem` owns no brand. Each `:app:<name>` defines its own `DesignTokens` impl:

```kotlin
// app/recipes/.../designsystem/RecipeTokens.kt
object RecipeTokens : DesignTokens {
    override val lightColorScheme: ColorScheme = …
    override val darkColorScheme: ColorScheme = …
    override val typography: Typography = …
    override val shapes: Shapes = …
    override val heroLightGradient: Brush = …
    override val heroDarkGradient: Brush = …
    override val successColor: Color = Color(0xFF2A825A)
}
```

`AppTheme(tokens, …)` reads via `LocalDesignTokens` (a `staticCompositionLocalOf`). Nested components that need brand-specific values that aren't M3 slots (hero gradients, `StatusTone.Installed`'s background) read from `LocalDesignTokens.current.successColor` etc. — never hardcode `Sage40` etc.

### `LlmTaskConfig<T>` + `PromptBuilder` + `TaskParser<T>` — per-app prompt/parser

`:core:domain` defines three contracts:

```kotlin
interface PromptBuilder { fun systemPrompt(): String; fun userPrompt(inputs: List<String>): String }
fun interface TaskParser<T> { fun parse(cumulativeText: String): T? }
data class LlmTaskConfig<T>(val promptBuilder: PromptBuilder, val parser: TaskParser<T>)
```

Each `:app:<name>` provides one `LlmTaskConfig<T>` value where `T` is the app's domain result type:

```kotlin
// app/recipes/.../config/RecipeTaskConfig.kt
val RecipeTaskConfig: LlmTaskConfig<Recipe> = LlmTaskConfig(
    promptBuilder = RecipePromptBuilder(),
    parser = RecipeParser(),
)
```

The shared `:core:data/LlmTaskRepositoryImpl` builds the prompt via `promptBuilder.systemPrompt() + "\n\n" + promptBuilder.userPrompt(inputs)`, streams through the engine, and re-parses on every chunk. The consuming screen casts `state.result: Any?` to its domain type.

### `LlmTaskLabels` + `LocalLlmTaskLabels` — per-app copy

`LlmTaskLabels` is a `data class` of every UI string the generic `LlmTaskScreen` reads (hero title, input placeholder, button labels, error messages, no-model banner copy). Each `:app:<name>` provides one `LlmTaskLabels` value:

```kotlin
// app/recipes/.../config/RecipeLabels.kt
val RecipeLabels: LlmTaskLabels = LlmTaskLabels(
    heroTitle = "What's in your kitchen?",
    generateLabel = "Generate recipe",
    …
)
```

Same pattern for `ModelManagementLabels` (read by `:feature:modelmanagement` via `LocalModelManagementLabels`).

### `:app:<name>` shape — what every white-label app owns

```kotlin
app/recipes/
├── build.gradle.kts                    # applies llmlocal.android.white-label-app
├── src/main/AndroidManifest.xml        # .RecipesApp + WorkManager provider + foreground service
├── src/main/kotlin/com/llmlocal/recipe/
│   ├── RecipesApp.kt                   # Application + Koin startKoin + WorkManager Configuration.Provider
│   ├── MainActivity.kt                 # AppTheme(tokens = RecipeTokens), NavHost, provides CompositionLocals
│   ├── RecipeScreen.kt                 # wraps LlmTaskRoute with RecipeTaskConfig + RecipeLabels + renderOutput
│   ├── domain/Recipe.kt                # data class Recipe(...)
│   ├── prompt/RecipePromptBuilder.kt   # implements PromptBuilder
│   ├── parser/RecipeParser.kt          # implements TaskParser<Recipe>
│   ├── config/RecipeTaskConfig.kt      # val RecipeTaskConfig: LlmTaskConfig<Recipe>
│   ├── config/RecipeLabels.kt          # val RecipeLabels: LlmTaskLabels
│   ├── config/RecipeModelManagementLabels.kt
│   ├── config/RecipesConfigModule.kt   # Koin module that exposes RecipeTaskConfig + RecipeLabels
│   ├── designsystem/RecipeTokens.kt    # object RecipeTokens : DesignTokens
│   ├── output/StreamedRecipeCard.kt    # the recipe-specific result renderer
│   └── previews/RecipePreviews.kt      # @Preview composables for designer iteration
└── src/main/res/values/strings.xml     # app_name only — all other copy lives in labels
```

The package convention is `com.llmlocal.<appname>.*`. The `applicationId` in `build.gradle.kts` and the `package=` attribute in the manifest must match.

## Creating a new white-label app

The fast path to add a third white-label app (e.g. `:app:fitness` for workout advice):

1. **Copy the directory**: `cp -R app/recipes app/fitness` then rename the Kotlin package to `com.llmlocal.fitness.*` everywhere.
2. **Update `settings.gradle.kts`**: append `include(":app:fitness")`.
3. **Update `app/fitness/build.gradle.kts`**:
   - `namespace = "com.llmlocal.fitness"`
   - `applicationId = "com.llmlocal.fitness"` (must be unique on device)
4. **Replace launcher icons**: drop new vectors into `app/fitness/src/main/res/drawable/` and `mipmap-anydpi-v26/`. Update the `ic_launcher_background` color in `app/fitness/src/main/res/values/`.
5. **Define `FitnessTokens : DesignTokens`** in `app/fitness/.../designsystem/`. Match the brand's color palette, fonts, shapes.
6. **Define `FitnessTaskConfig : LlmTaskConfig<FitnessAdvice>`** — implement `FitnessPromptBuilder : PromptBuilder` and `FitnessParser : TaskParser<FitnessAdvice>`.
7. **Define `FitnessLabels : LlmTaskLabels`** (all the UI copy) and `FitnessModelManagementLabels : ModelManagementLabels`.
8. **Update `MainActivity`**: `AppTheme(tokens = FitnessTokens) { … }` and pass `FitnessTaskConfig` + `FitnessLabels` + `renderOutput = { FitnessCard(it as FitnessAdvice) }` to `LlmTaskRoute`. Pass `FitnessModelManagementLabels` to `ModelManagementRoute`.
9. **Update `app_name`** in `app/fitness/src/main/res/values/strings.xml`. (All other strings stay in the Kotlin labels — no `strings.xml` lookup is done in features.)
10. **Update the `Application` class** — startKoin module list is identical to the recipe app (it only references the shared modules).
11. **Build**: `./gradlew :app:fitness:assembleDebug`. Both APKs build from one Gradle invocation.

The shared `:core:*` and `:feature:*` modules are not touched. The new app consumes them verbatim.

If the new app needs richer UX than the generic `LlmTaskScreen` provides (multiple steps, a consent gate, a separate "history" feature), write a `:feature:fitness-advice` module that uses the same `LlmTaskConfig<T>` / `GenerateLlmTaskUseCase` plumbing — the contract layer is shared.

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

The end-to-end flow wires the engine through the generic domain abstraction:

1. `LlmTaskViewModel.startGeneration()` calls `GenerateLlmTaskUseCase(realEngine, config, inputs)` with the app's `LlmTaskConfig<T>`.
2. `GenerateLlmTaskUseCase` (in `:core:domain`) delegates to `LlmTaskRepository.generateStream(engine, config, inputs)`.
3. `LlmTaskRepositoryImpl` (in `:core:data`) builds the prompt via `config.promptBuilder.systemPrompt() + "\n\n" + config.promptBuilder.userPrompt(inputs)` and collects `engine.generateStream(prompt)`, buffering the cumulative text. On `Done`, it calls `config.parser.parse(buffer.toString())` and emits `LlmTaskEvent.Complete(parsed)`.
4. The app-specific `TaskParser<T>` (e.g. `RecipeParser`) extracts the structured result. **If you change the prompt format, update the parser to match** — the parser is the source of truth for the expected output structure.

### Model files and engine wiring

- **Catalog**: `LlmModelCatalog.ALL` is the single source of truth for known models. To add a model, add a `LlmModelDescriptor` (in `:core:model`) and include it in `ALL`.
- **Selection**: persisted in `LlmModelSelectionStore` (DataStore). The engine's `modelPathProvider` lambda resolves the path lazily on each `initialize()` from this store + `LlmModelManager.targetFile()`.
- **Download**: `LlmModelManager.download(descriptor)` streams the URL into `Context.filesDir/llm/<filename with .part tmp>` with throttled `DownloadProgress` emissions. Downloads are launched as a `ModelDownloadWorker` (`CoroutineWorker`) promoted to a foreground service — so the OS keeps the download alive when the app is backgrounded.
- **Backend selection**: compile-time via `BuildConfig.LLM_BACKEND` — `"CPU"` for the x86_64 emulator (where GPU/NPU fail), `"GPU"` for real devices. See `core/llm/build.gradle.kts`.

### Substituting a different engine or a fake

The `LlmEngine` is injected per-call into the use case (`GenerateLlmTaskUseCase.invoke(engine, config, inputs)`), so tests can pass a fake engine. The real engine is registered with the `REAL_ENGINE_QUALIFIER` qualifier in `llmModule`:

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
- Generic domain contracts: `core/domain/src/main/kotlin/com/llmlocal/core/domain/task/{PromptBuilder,TaskParser,LlmTaskConfig}.kt`
- Generic data impl: `core/data/src/main/kotlin/com/llmlocal/core/data/repository/LlmTaskRepositoryImpl.kt`
- Generic MVI scaffold: `feature/llm-task/src/main/kotlin/com/llmlocal/feature/llmtask/{LlmTaskViewModel,LlmTaskRoute,LlmTaskScreen}.kt`
- App-level wiring example (recipes): `app/recipes/src/main/kotlin/com/llmlocal/recipe/{RecipesApp,MainActivity}.kt`
- Per-app config example: `app/recipes/src/main/kotlin/com/llmlocal/recipe/config/`
- Per-app design tokens example: `app/recipes/src/main/kotlin/com/llmlocal/recipe/designsystem/RecipeTokens.kt`

## Navigation

Single Activity (`MainActivity`) hosts a Compose `NavHost` with two destinations:

- `recipes` (start destination) — wraps `LlmTaskRoute(config = RecipeTaskConfig, …)`. Each `:app:<name>` defines its own destination names.
- `modelManagement` — `ModelManagementRoute(labels = RecipeModelManagementLabels)`. Identical route in every `:app:<name>`.

## Gotchas / non-obvious behaviour

- **WorkManager default initializer is disabled** in `AndroidManifest.xml` so that `Application` (a `Configuration.Provider`) can supply a Koin-aware `WorkerFactory`. Don't re-enable it.
- **Android 14+ requires the WorkManager foreground service to be declared explicitly** in `AndroidManifest.xml` with `android:foregroundServiceType="dataSync"` (matching what `ModelDownloadWorker.foregroundInfo` passes on API 34+). Without it the OS throws `IllegalArgumentException: foregroundServiceType 0x1 is not a subset of foregroundServiceType attribute 0x0` the moment a worker calls `setForeground(...)`. The library's own manifest entry isn't reliably merged into apps that disable the default WorkManager initializer, so we declare it ourselves next to the `<provider>`. Keep the type in sync with `ModelDownloadWorker.foregroundInfo`.
- **Notification channel** `model_downloads` is created idempotently in the `Application.onCreate` before Koin starts.
- **Background continuation depends on `setForeground` succeeding.** The persistent notification you see while a download is running is itself a foreground-service notification — that service is what keeps the `ModelDownloadWorker` process alive after the user swipes the app from recents. `ModelDownloadWorker.doWork` calls `setForeground(initialInfo)` before it starts streaming bytes and treats that call as **mandatory**: if it throws (battery optimization, OEM task killers, etc.) the worker fails up front with a clear `KEY_FAILURE_REASON` rather than silently degrading to a notification-less download that the OS will kill the moment the app is backgrounded. Don't reintroduce a silent `catch (t: Throwable)` around `setForeground` — that exact path is what produced the original "Download failed: Software caused connection abort" report.
- **Closing the app mid-download surfaces as `SocketException`, not `CancellationException`.** When the user swipes the app away while a download is running, WorkManager cancels the worker and the OS closes OkHttp's socket. The blocked `input.read(...)` returns `SocketException("Software caused connection abort")` — which would otherwise fall through `ModelDownloadWorker.doWork`'s `catch (t: Throwable)` branch and be reported as a download failure. `LlmModelManager.downloadInto` reclassifies any non-CE exception caught while `coroutineContext[Job]?.isActive == false` as `CancellationException`, plus does a cooperative `coroutineContext.ensureActive()` at the top of the read loop so steady-state cancellation is responsive. With this, app-closed-mid-download reports `markCancelled` instead of `markFailed`. A real network failure (worker still active, socket fails) still reports `markFailed` as expected.
- **The `.part` file is deleted on cancellation/failure**, so a re-download starts from zero on the next launch. Resumable downloads (HTTP `Range` header against the partial file, with the 200/206/416 fallback) are not implemented — flag if that's wanted for the multi-GB models in the catalog.
- **`LlmTaskViewModel` must react to download completion, not just selection changes.** For a first-time user the default model is auto-selected (`LlmModelSelectionStore.current()` falls back to `DEFAULT_MODEL.id`), so `selectionStore.selectedModelId` emits exactly once at init and never again when the user downloads that default model in the model-management screen. `LlmTaskViewModel.observeSelection()` therefore also observes `ModelDownloadProgressStore.progressFor(currentId)` via `flatMapLatest` and re-runs `checkModel()` when the state transitions to `SUCCEEDED`. Without that second trigger the recipe screen stays stuck on `NoModelAvailable` even though the model file is on disk. RUNNING / FAILED / CANCELLED transitions don't change on-disk presence and are intentionally skipped.
- **Per-ABI APK splits** are enabled in the `llmlocal.android.white-label-app` convention plugin because LiteRT-LM ships ~25–30 MB of native code per ABI — universal APKs blow past the install-size floor on low-storage devices.
- **The `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`** flag in `settings.gradle.kts` is what lets `projects.feature.llm_task` resolve correctly. Don't remove it.
- **`LlmTaskSelectionStore.current()` is non-suspending** — the engine's `modelPathProvider` lambda is not `suspend`, so it reads an `AtomicReference<String?>` snapshot mirrored by a long-running collector.
- **`.gitignore` excludes model caches** (`**/files/llm/`, `**/cache/llm/`) — the model is re-downloaded from HuggingFace on first run.

## Tests

Existing test coverage (good places to extend):

- `core/common/src/test/kotlin/com/llmlocal/core/common/result/OutcomeTest.kt` — `Outcome` sealed hierarchy helpers
- `core/llm/src/main/kotlin/com/llmlocal/core/llm/download/LlmModelManager.kt` (unit-tested via `:core:llm/src/test/`)
- Per-app parser/prompt-builder tests live alongside the impl — e.g. `app/recipes/src/test/kotlin/com/llmlocal/recipe/parser/RecipeParserTest.kt`

Pattern for engine-touching tests: pass a fake `LlmEngine` to `GenerateLlmTaskUseCase` and a stub `LlmTaskConfig`. The engine interface is intentionally tiny for this reason.