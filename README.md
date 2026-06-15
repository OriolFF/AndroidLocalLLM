# Local LLM Recipes — Android Template

A **template-style Android app** that runs a **local LLM on-device** to
generate cooking recipes from a list of ingredients. Built as a
reusable starting point for any app that needs on-device LLM
inference: change the prompt, change the parser, ship a new feature.

## Stack

| Layer | Choice |
|---|---|
| LLM | **MediaPipe LLM Inference** (`com.google.mediapipe:tasks-genai:0.10.27`) |
| Default model | `Gemma 3 1B IT (int4)` (`.task` format) |
| UI | **Jetpack Compose + Material 3** (BOM-aligned), edge-to-edge, dynamic color |
| Architecture | **MVI** — `RecipeIntent` / `RecipeState` / `RecipeEffect` |
| State | `StateFlow` (state) + `Channel` (one-shot effects) |
| DI | **Koin 4.0.0** (classic DSL, no KSP) |
| Build | Version catalog (`gradle/libs.versions.toml`) + type-safe project accessors + `:build-logic` convention plugins |
| Async | Kotlin Coroutines + `Flow` |
| Networking | OkHttp (only used to download the model on first run) |
| Persistence | None (model file cached in `Context.filesDir/llm/`) |
| Tests | JUnit 4 + Truth + Turbine + `kotlinx-coroutines-test` |

## Module layout (Now-in-Android style)

```
:app                          # Koin host, MainActivity, edge-to-edge setup
:build-logic                  # Composite build, convention plugins
:core
  :core:common                # DispatcherProvider, Outcome, FlowExt
  :core:model                 # Pure Kotlin models: Ingredient, Recipe, LlmModelDescriptor
  :core:designsystem          # AppTheme (Material 3 + dynamic color), AppScaffold
  :core:network               # OkHttp client + Koin module
  :core:llm                   # LlmEngine (MediaPipe wrapper), LlmModelManager (download), RecipePromptBuilder, RecipeParser
  :core:domain                # Pure Kotlin: RecipeRepository, GenerateRecipeUseCase, RecipeEvent
  :core:data                  # RecipeRepositoryImpl, Koin binding
:feature
  :feature:recipe             # MVI: RecipeScreen, RecipeViewModel, components
```

### Dependency direction (enforced, one-way)

```
:feature:*  ──►  :core:*
:core:data  ──►  :core:domain, :core:llm, :core:network, :core:model
:core:llm   ──►  :core:model, :core:common, :core:network
:app        ──►  all (composition only)
```

No back-edges. To verify: `./gradlew :feature:recipe:dependencies --configuration releaseRuntimeClasspath`.

## MVI loop

```
User action ──► RecipeIntent ──► ViewModel.onIntent()
                                      │
                                      ▼
                              UseCase (GenerateRecipeUseCase)
                                      │
                                      ▼
                          LlmEngine.generateStream(prompt)
                                      │
                                      ▼
                       Flow<RecipeEvent> (Token / Complete / Failed)
                                      │
                                      ▼
                       RecipeState  ◄─── update (StateFlow)
                       RecipeEffect ◄─── send   (Channel)
                                      │
                                      ▼
                              Compose UI
```

The template is shipped with full unit-test coverage for the
non-Android pieces (parsers, use cases, outcomes). The MediaPipe
`LlmInference` engine itself is not unit-tested because it requires
native libraries; in production you would test via the repository
contract with a fake `LlmEngine`.

## Quick start

### 1. Generate the Gradle wrapper

The project does not commit the Gradle wrapper JAR. Generate it once
with a system Gradle (or open the project in Android Studio, which
will do it for you):

```bash
# Option A — from a system Gradle install:
gradle wrapper --gradle-version 8.10.2

# Option B — Android Studio: open the project, it will offer to do this.
```

### 2. Build

```bash
./gradlew :app:assembleDebug
```

### 3. Install on a device

The recipe app needs a real device (or recent emulator with
hardware acceleration). MediaPipe LLM Inference is **not** supported
on x86 emulators reliably.

```bash
./gradlew :app:installDebug
adb shell am start -n com.llmlocal.recipe/.MainActivity
```

### 4. First run

On first launch the app will:

1. Check whether the model is already on disk
   (`Context.filesDir/llm/gemma-3-1b-it-int4.task`).
2. If not, prompt to download (~600 MB from HuggingFace).
3. Initialize the MediaPipe engine (cold start ~10 s on a
   modern device).
4. Show a banner with download progress, then the input UI.

After the first download the app is fully offline.

## Customizing the LLM

To swap in a different model, edit
`core/llm/src/main/kotlin/com/llmlocal/core/llm/model/LlmModelCatalog.kt`:

```kotlin
val DEFAULT_MODEL: LlmModelDescriptor = LlmModelDescriptor(
    id = "your-model-id",
    url = "https://huggingface.co/<org>/<repo>/resolve/main/<file>.task",
    sizeBytes = 1_500_000_000L, // approximate
    filename = "your-model.task",
    sha256 = null, // optional - if set, the file is verified after download
)
```

The `LlmModelManager` is generic over the URL; no other code needs to
change.

## Customizing the prompt

Edit `core/llm/src/main/kotlin/com/llmlocal/core/llm/prompt/RecipePromptBuilder.kt`
to change the system instruction or the user-message template. The
parser (`RecipeParser`) is documented in
`core/llm/src/main/kotlin/com/llmlocal/core/llm/parser/RecipeParser.kt`
and is the source of truth for the expected output format. If you
change the prompt format, update the parser to match.

## Caveats

- **MediaPipe is in maintenance-only mode.** The library still works in
  2026 but Google recommends LiteRT-LM for new projects. The LLM
  integration is isolated in `:core:llm`, so migrating later is a
  single-module change.
- **Cold start ~10 s.** MediaPipe initialization is heavy. The banner
  UI makes this visible; the app does not block the main thread.
- **Streaming granularity** is model-controlled. With Gemma 3 1B you
  get roughly 1–2 tokens per emission, which is enough for a smooth
  animation.
- **No cloud fallback by design.** This is an offline, local-only
  template. To add cloud fallback, introduce a `RemoteLlmEngine` in
  `:core:llm` and select it via a Koin qualifier in `LlmModule.kt`.

## Build commands

```bash
# Build everything
./gradlew assemble

# Run unit tests
./gradlew test

# Lint
./gradlew lint

# Check module boundaries
./gradlew :feature:recipe:dependencies --configuration releaseRuntimeClasspath

# Build the APK without signing (debug)
./gradlew :app:assembleDebug

# Stream install on connected device
./gradlew :app:installDebug
```

## Project layout cheatsheet

```
llmlocalAndroid/
├── app/                                  # Application module
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/llmlocal/recipe/
│       │   ├── MainActivity.kt           # ComponentActivity, edge-to-edge
│       │   └── RecipeApp.kt              # Application + Koin startKoin
│       └── res/...
├── build-logic/                          # Convention plugins
│   ├── settings.gradle.kts
│   └── convention/
│       ├── build.gradle.kts
│       └── src/main/kotlin/com/llmlocal/convention/
│           ├── AndroidApplicationConventionPlugin.kt
│           ├── AndroidLibraryConventionPlugin.kt
│           ├── AndroidComposeConventionPlugin.kt
│           ├── AndroidFeatureConventionPlugin.kt
│           ├── AndroidKoinConventionPlugin.kt
│           ├── AndroidRoomConventionPlugin.kt
│           └── AndroidConfig.kt
├── core/
│   ├── common/                           # Dispatchers, Outcome, FlowExt
│   ├── data/                             # RecipeRepositoryImpl
│   ├── designsystem/                     # AppTheme, AppScaffold
│   ├── domain/                           # Pure-Kotlin domain (use cases, interfaces)
│   ├── llm/                              # The LLM integration
│   │   ├── engine/                       # LlmEngine + LiteRtLlmEngine + LlmToken
│   │   ├── download/                     # LlmModelManager (download/cache)
│   │   ├── prompt/                       # RecipePromptBuilder
│   │   ├── parser/                       # RecipeParser
│   │   ├── model/                        # LlmModelCatalog, DownloadProgress
│   │   └── di/                           # LlmModule (Koin)
│   ├── model/                            # Pure-Kotlin models
│   └── network/                          # OkHttp client
├── feature/
│   └── recipe/                           # MVI recipe feature
│       └── src/main/kotlin/com/llmlocal/feature/recipe/
│           ├── RecipeScreen.kt           # Stateless composable
│           ├── RecipeRoute.kt            # Stateful wrapper
│           ├── RecipeViewModel.kt        # MVI dispatcher
│           ├── mvi/                      # Intent / State / Effect / ModelStatus
│           ├── components/               # IngredientInputRow, IngredientChips, …
│           └── di/RecipeModule.kt
├── gradle/
│   ├── libs.versions.toml                # Version catalog (single source of truth)
│   └── wrapper/gradle-wrapper.properties
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── README.md
```

## License

This template is a clean-room implementation. The bundled model
(Gemma 3 1B IT) is subject to Google's [Gemma Terms of Use][gemma-tos].
For a closed commercial app, swap in a model whose license suits
your needs (e.g. Llama 3.2 or Qwen 2.5).

[gemma-tos]: https://ai.google.dev/gemma/terms
