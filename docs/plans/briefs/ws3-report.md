# WS3 report — shared model readiness, copy debt, design-system primitives

Branch: `ws3-shared-readiness-primitives` (worktree, based on `master` at `47eb643`).
Commit: see the terminal reply / `git log -1` on the branch.

## State found on arrival

Two earlier attempts had left uncommitted work in the worktree. None of it had ever compiled:

- `ModelReadinessObserver` had three type errors (nullable id passed to `findById`, `transform` typed as `Flow<NoModel>`), set `_readiness.value` from inside a `transform` instead of emitting, and lacked the `Checking` state the brief requires (initial state was `NoModel`, which would flash the no-model banner on every screen open).
- `ModelReadinessObserverTest` reassigned a `val` (`f.engine = …`), used unchecked casts on `Outcome`, and did not use Turbine.
- The primitives had no `@Preview`s, `PhaseProgress` drew a check-circle icon inside a second filled circle and used `AnimatedVisibility` for a static connector, `StreamingCursor` could not blink on its own, and `StreamedRecipeCard` wrapped icon singletons in `remember`.
- Both cards still carried a hardcoded default for `warmingUpLabel`.

The label, ViewModel, module and route changes for model management and `AppErrorView` were sound and were kept.

## What was done

### 1. `:core:llm` readiness (new package `com.llmlocal.core.llm.readiness`)

```kotlin
sealed interface ModelReadiness {
    data object Checking : ModelReadiness
    data object NoModel : ModelReadiness
    data class Downloading(val progress: PerModelProgress) : ModelReadiness
    data object Initializing : ModelReadiness
    data class Ready(val descriptor: LlmModelDescriptor) : ModelReadiness
    data class Failed(val reason: String, val descriptor: LlmModelDescriptor? = null) : ModelReadiness
}

class ModelReadinessObserver(
    selectionStore: LlmModelSelectionStore,
    progressStore: ModelDownloadProgressStore,
    manager: LlmModelManager,
    engine: LlmEngine,
    dispatchers: DispatcherProvider,
) {
    fun observe(scope: CoroutineScope): StateFlow<ModelReadiness>   // first call launches; later calls return the same flow
    suspend fun recheck()                                            // re-runs disk + engine check for the current selection
}
```

Pipeline: `selectedModelId` (null → default id, distinct) combined with recheck ticks → `flatMapLatest` → catalog miss → `NoModel`; otherwise `progressFor(id)` (distinct) → `RUNNING` → `Downloading(progress)`; any other state (`null`, `IDLE`, `SUCCEEDED`, `FAILED`, `CANCELLED`) → `Checking` → disk check on `dispatchers.io` → `NoModel` | `Initializing` → `engine.initialize()` → `Ready` | `Failed`. An engine that throws (rather than returning `Outcome.Failure`) is reported as `Failed`, not a dead collector.

The documented gotcha (default model already selected on a fresh install, so the selection flow never re-emits when that model is downloaded) is covered: the disk check is keyed off the per-model progress flow, so the worker writing `SUCCEEDED` triggers it.

An `internal` primary constructor takes the collaborators as flows and lambdas for tests; the public constructor adapts the real stores. Registered in `llmModule` as `factory { ModelReadinessObserver(selectionStore = get(), progressStore = get(), manager = get(), engine = get(named(REAL_ENGINE_QUALIFIER)), dispatchers = get()) }` (named args are needed because the two constructors are both 5-ary from Koin's point of view).

### 2. `LlmTaskViewModel`

Constructor now takes `readinessObserver: ModelReadinessObserver` instead of `modelManager` and `progressStore`. `init` calls `observeSelection()` (keeps `selectedModel` in step with the store) and `observeReadiness()`. `CheckModel` intent → `readinessObserver.recheck()`.

Mapping to the unchanged `ModelStatus`: `Checking`/`Initializing` → `Checking`; `NoModel` → `NoModelAvailable`; `Downloading` → `NoModelAvailable` (deliberate: the task screen only renders the no-model banner and the Generate enabled state, and before this change it did not re-check on `RUNNING`, so the banner stayed up during a download; this keeps that exact behaviour); `Ready` → `Ready`; `Failed(reason)` → `Failed(reason)`. `selectedModelInstalled` is true for `Ready` and for `Failed` with a descriptor (file was on disk, engine refused it). `ModelStatus`, `LlmTaskState`, `LlmTaskScreen` and its components are untouched.

### 3. Copy debt

- `ModelManagementLabels` gained three fields (see "What other brands must provide").
- `ModelManagementViewModel(…, labels: ModelManagementLabels)`; `modelManagementModule` uses `viewModel { params -> … labels = params.get() }`; `ModelManagementRoute` creates it with `koinViewModel { parametersOf(labels) }`. The three hardcoded snackbar strings are gone.
- `AppErrorView(message, modifier, retryLabel: String? = null, onRetry: (() -> Unit)? = null)` — the button renders only when both `retryLabel` and `onRetry` are non-null; no hardcoded "Retry". There were no call sites in the repo to update.

### 4. Primitives in `core/designsystem/.../component/`

All brand-free (MaterialTheme slots + `designTokens.successColor` only), KDoc'd:

```kotlin
@Composable fun PhaseProgress(items: List<PhaseItem>, modifier: Modifier = Modifier)
@Immutable data class PhaseItem(val label: String, val status: PhaseStatus, val detail: String? = null, val statusDescription: String? = null)
enum class PhaseStatus { Pending, Active, Done, Failed, Warning }
@Composable fun StreamingText(text: String, isGenerating: Boolean, warmingUpLabel: String, modifier: Modifier = Modifier)
@Composable fun StreamingCursor(modifier: Modifier = Modifier)          // self-blinking 24×2dp primary bar
@Composable fun SkeletonLine(widthFraction: Float, modifier: Modifier = Modifier, height: Dp = 16.dp)
@Composable fun SectionHeader(label: String, icon: ImageVector?, modifier: Modifier = Modifier, iconTint: Color? = null)
@Composable fun NumberBadge(n: Int, modifier: Modifier = Modifier)
```

`PhaseProgress` follows UX spec §4: Done = `CheckCircle` in `successColor`, Active = 20dp indeterminate primary ring, Pending = hollow `outlineVariant` ring, Failed = `ErrorOutline` in `error`, Warning = `WarningAmber` in `tertiary`; optional `detail` sub-line for the hint / elapsed seconds; rows merge semantics and expose `statusDescription` as `stateDescription` for TalkBack (copy supplied by the caller, since the primitive carries none).

Previews: `:core:designsystem` has no token set of its own and cannot import the apps', so the previews live beside the existing screen previews in each app and use the real tokens: `app/recipes/.../previews/RecipePrimitivePreviews.kt` (`RecipeTokens`, light + dark) and `app/medical/.../previews/MedicalPrimitivePreviews.kt` (`MedicalTokens`, light + dark). Each shows every primitive in every state.

### 5. Cards

`StreamedRecipeCard` and `StreamedMedicalCard` now use `SectionHeader`, `StreamingText` and (recipes) `NumberBadge` from the design system; their private copies are deleted. Both take a required `warmingUpLabel: String`, passed from `RecipeScreen` / `MedicalScreen` and the previews as `RecipeLabels.warmingUpLabel` / `MedicalLabels.warmingUpLabel`. The rendered layout is identical to before (same sizes, colours, animation timings).

### 6. Tests — `core/llm/src/test/.../readiness/ModelReadinessObserverTest.kt`

13 tests, Turbine + Truth + `kotlinx-coroutines-test`, fakes for selection/progress flows, installed set and engine (including a gated engine to observe `Initializing`): fresh install → `NoModel`; default already selected, download `RUNNING` → `Downloading`, then `SUCCEEDED` → `Ready` with one `initialize()` call; `Initializing` before `Ready`; selection change → re-check (both directions); unknown id → `NoModel`; init `Failure` → `Failed(reason, descriptor)`; init failure without message → non-empty reason; init throwing → `Failed`; `recheck()` after `Failed` → `Ready`; model removed → `NoModel` (via progress write and via `recheck()`); cancelled download → `NoModel`; `observe()` idempotent.

Note for anyone extending these tests: the observer is launched in a scope the fixture owns on the test scheduler, not `runTest`'s `backgroundScope`, because `advanceUntilIdle()` stops once only background tasks remain.

## Verification

| Command | Result |
|---|---|
| `./gradlew :core:llm:test :core:common:test` | green (13 readiness tests + existing `OutcomeTest`) |
| `./gradlew :app:recipes:assembleDebug :app:medical:assembleDebug` | green, 10 split APKs |
| `./gradlew lint` | exit 0; the only new warning was `ModifierParameter` on `SkeletonLine`, fixed by making `widthFraction` required (now the brief's signature) |
| Module boundaries | no project dependency added or removed in any `build.gradle.kts` |

Device screenshots were **not** taken: `_common.md` (newer than `ws3.md`) says the single `emulator-5554` is shared and agents must not install or launch apps; the lead does device QA in WS6. The two preview galleries plus the unchanged card layouts are the visual-regression evidence available from this branch.

The `android-ux` subagent was not spawned for step 4; the primitives were written and reviewed by this agent against the rules in `.claude/agents/android-ux.md` (brand-free, labels-only copy, both token sets, a11y semantics).

## What other brands must provide (WS0 `app/recipekeeper`, WS5b)

`ModelManagementLabels` — three new required fields:

```kotlin
val modelSelectedSnackbar: (modelName: String) -> String      // recipes: "$name selected for recipe generation"
                                                               // medical: "$name selected for wellness guidance"
                                                               // Recipe Keeper (UX spec §7): "$name will read your clipped recipes"
val modelRemovedSnackbar: (modelName: String) -> String        // both apps: "$name removed"
val modelNotDownloadedSnackbar: (modelName: String) -> String  // both apps: "$name is not downloaded yet."
```

Any app's output card that used a private `StreamingText` must now pass `warmingUpLabel` (from its `LlmTaskLabels.warmingUpLabel`). `AppErrorView` callers that want a button must pass `retryLabel`.

## Files

New: `core/llm/src/main/kotlin/com/llmlocal/core/llm/readiness/{ModelReadiness,ModelReadinessObserver}.kt`, `core/llm/src/test/kotlin/com/llmlocal/core/llm/readiness/ModelReadinessObserverTest.kt`, `core/designsystem/.../component/{PhaseProgress,StreamingText,SkeletonLine,SectionHeader,NumberBadge}.kt`, `app/recipes/.../previews/RecipePrimitivePreviews.kt`, `app/medical/.../previews/MedicalPrimitivePreviews.kt`, this report.

Modified: `core/llm/.../di/LlmModule.kt`, `feature/llm-task/.../LlmTaskViewModel.kt`, `feature/llm-task/.../di/LlmTaskModule.kt`, `feature/modelmanagement/.../{ModelManagementRoute,ModelManagementViewModel}.kt`, `feature/modelmanagement/.../di/ModelManagementModule.kt`, `feature/modelmanagement/.../mvi/ModelManagementLabels.kt`, `core/designsystem/.../component/AppErrorView.kt`, `app/recipes/.../{RecipeScreen.kt, config/RecipeModelManagementLabels.kt, output/StreamedRecipeCard.kt, previews/RecipePreviews.kt}`, `app/medical/.../{MedicalScreen.kt, config/MedicalModelManagementLabels.kt, output/StreamedMedicalCard.kt, previews/MedicalPreviews.kt}`.

Not touched: `core/llm/.../engine/**`, `LlmEngine`, `LlmToken`, `ModelStatus`, `LlmTaskState`, `LlmTaskScreen`, any Gradle file.
