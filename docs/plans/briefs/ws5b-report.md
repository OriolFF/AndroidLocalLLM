# WS5b report — `:app:recipekeeper` brand, labels, navigation, library and detail UI

Branch `recipekeeper/ws5b`. Everything lives under `app/recipekeeper/`; no shared module was touched.

## Files

| Area | Files |
|---|---|
| Brand | `designsystem/RecipeKeeperTokens.kt` (plum/olive/saffron/parchment per UX spec §8, serif display, 4/8/12/16/24 shapes, hero gradients, `successColor`), `designsystem/Primitives.kt` (**TEMP shim**), `res/drawable/ic_launcher_foreground.xml` (bookmark + fork on the plum background) |
| Labels | `config/RecipeKeeperLibraryLabels.kt` (`@Immutable data class` with English defaults + `DefaultRecipeKeeperLibraryLabels`), `config/RecipeKeeperClipperLabels.kt` (`WebClipperLabels(...)`, three brand overrides), `config/RecipeKeeperModelManagementLabels.kt` (unchanged from WS0) |
| DI | `config/RecipeKeeperUiModule.kt` — `recipeKeeperUiModule`, registered in `RecipeKeeperApp` after `recipeKeeperConfigModule` |
| App | `RecipeKeeperApp.kt` (+ `SingletonImageLoader.Factory`), `MainActivity.kt` (NavHost: `library`, `browser?url={url}`, `extract/{candidateId}`, `recipe/{recipeId}`, `modelManagement`) |
| Library | `library/{LibraryState,LibraryIntent,LibraryEffect,LibraryViewModel,LibraryRoute,LibraryScreen,RecipeTile,PendingRecipeDeletes,UndoDeleteSnackbar,ModelStatusSource}.kt` |
| Detail | `detail/{RecipeDetailState,RecipeDetailIntent,RecipeDetailEffect,RecipeDetailViewModel,RecipeDetailRoute,RecipeDetailScreen}.kt` |
| Draft card | `output/ClipDraftCard.kt`, `output/StubDraftParser.kt` |
| Previews | `previews/{SampleData,LibraryPreviews,RecipeDetailPreviews,ClipDraftCardPreviews,ModelManagementPreviews}.kt` — every state, light + dark, 600dp+ / landscape variants |
| Tests | `src/test/kotlin/.../{testing/MainDispatcherRule,testing/TestDispatchers,testing/FakeRecipeRepository,library/LibraryViewModelTest,library/PendingRecipeDeletesTest,detail/RecipeDetailViewModelTest,output/StubDraftParserTest}.kt` |
| Build | `build.gradle.kts`: added `testImplementation(libs.junit / truth / kotlinx.coroutines.test / turbine)` |

## Public signatures

```kotlin
// library
sealed interface LibraryModelStatus { NoModel; Downloading(progress: PerModelProgress) { val percent: Int }; Ready }
interface ModelStatusSource { val status: StateFlow<LibraryModelStatus> }
class FakeModelStatusSource(initial = Ready) : ModelStatusSource
class StoreBackedModelStatusSource(selectionStore, progressStore, modelManager, scope) : ModelStatusSource   // TEMP shim
class PendingRecipeDeletes(repository, dispatchers, scope = app scope, undoWindowMs = 5_000) {
    val pending: StateFlow<Set<String>>; fun schedule(id); fun undo(id): Boolean; suspend fun commit(id) }
class LibraryViewModel(repository, modelStatusSource, pendingDeletes, dispatchers)  // state: LibraryState, effects: LibraryEffect
@Composable fun LibraryRoute(labels, snackbarHostState, onOpenRecipe, onOpenBrowser: (String?) -> Unit, onOpenModelManagement, modifier, viewModel = koinViewModel())
@Composable fun LibraryScreen(state, labels, onIntent, modifier)
@Composable fun RecipeTile(summary, labels, onClick, onDelete, onShare, onOpenOriginal, modifier)
suspend fun SnackbarHostState.showRecipeDeleted(labels, onUndo, onDismissed)

// detail
class RecipeDetailViewModel(recipeId, repository, pendingDeletes, dispatchers)
@Composable fun RecipeDetailRoute(recipeId, labels, snackbarHostState, onBack, onOpenBrowser: (String) -> Unit, onDeleted: (String) -> Unit, modifier, viewModel = koinViewModel { parametersOf(recipeId) })
@Composable fun RecipeDetailScreen(state, labels, snackbarHostState, onIntent, onBack, modifier)

// output
@Composable fun ClipDraftCard(streamedText, isRunning, parseDraft: (String) -> RecipeDraft = StubDraftParser::parse,
    labels = DefaultRecipeKeeperLibraryLabels, modifier, clipperLabels = RecipeKeeperClipperLabels)
object StubDraftParser { fun parse(text: String): RecipeDraft }
```

## Decisions worth knowing

- **Undo-delete is app-wide**, not per ViewModel: `PendingRecipeDeletes` (Koin single) hides ids immediately on every list and hard-deletes after 5 s in an application scope, so navigating away never loses a scheduled delete. Both the library long-press menu and the detail overflow use it; the detail route pops and `MainActivity` shows the undo snackbar from the root scope. Dismissing the snackbar early commits; an undo after the window shows `undoDeleteFailed`.
- **Share / Open original from the grid** read the full recipe once (`RecipeSummary` has no URL) and answer with an effect; copy formatting stays in the routes.
- **Search** is a toggle in the `AppScaffold` actions that pins an `OutlinedTextField` above the grid (the scaffold title is a plain `String`), filtering through `RecipeRepository.search`.
- **Detail**: compact = `LazyColumn` with a 16:10 hero under the status bar + transparent `TopAppBar` that fades to `surface` on scroll; no picture → `GradientHero` under a solid bar. ≥ 600dp = two independently scrolling panes (hero/meta/source/ingredients | steps/pictures/notes). Meta chips are a non-clickable custom chip (M3 chips announce as buttons). Keep-screen-on is a checkable overflow item.
- **Draft card** debounces `parseDraft` 80 ms on `Dispatchers.Default`, shows fixed-order skeletons (Title → Meta → Ingredients → Steps), dims the tail with a cursor, has its own raw toggle (the `renderDraft` slot receives no `showRaw`), and announces only milestones through one invisible live region. Shimmer/pulse are disabled when animator scale is 0.
- Brand palette uses the spec's exact primaries (`Plum40 = 7A3B5E`, `Olive40 = 596B3A`, `Saffron40 = 8A6A12`); the earlier attempt had drifted them lighter.

## Shims the lead must replace at merge

1. `library/ModelStatusSource.kt` — `StoreBackedModelStatusSource` and the `single<ModelStatusSource>` in `RecipeKeeperUiModule`. Replace with an adapter over WS3's `ModelReadinessObserver.observe(scope)`: `NoModel/Checking/Failed → NoModel`, `Downloading(p) → Downloading(p)`, `Initializing/Ready → Ready`. `LibraryModelStatus` can stay as the app-local type.
2. `designsystem/Primitives.kt` — `SkeletonLine(widthFraction, modifier, height)`, `StreamingCursor(modifier)`, `SectionHeader(label, icon, modifier, trailing)`, `NumberBadge(number, modifier, selected)`. Swap imports in `output/ClipDraftCard.kt` and `detail/RecipeDetailScreen.kt` to `com.llmlocal.core.designsystem.component.*` and delete the file (note `SectionHeader.trailing` and `NumberBadge.selected` are used; keep them or inline).
3. `output/StubDraftParser.kt` — `MainActivity` passes `parseDraft = StubDraftParser::parse` (marked `TODO(merge)`). Point it at WS5a's `RecipeKeeperDraftParser` (koinInject or a direct reference). The stub can stay for previews.

## Koin bindings the lead must connect

- `recipeKeeperUiModule` needs `RecipeRepository` (WS5a `recipeKeeperConfigModule`). Without it the app crashes on launch in this worktree — expected, not installed per the common brief.
- `MainActivity` resolves `koinInject<WebClipConfig<ClippedRecipe>>()` and `koinInject<ClipSink<ClippedRecipe>>()`. Koin binds by erased class, so WS5a must declare them as `single<WebClipConfig<ClippedRecipe>> { … }` / `single<ClipSink<ClippedRecipe>> { … }` — `single { RecipeKeeperClipConfig }` alone registers the concrete type only.
- `RecipeKeeperApp.newImageLoader` uses `getKoin().getOrNull<ImageLoader>()`; if WS5a binds a Coil `ImageLoader` it is picked up, otherwise a default loader is built.
- `ModelManagementLabels` snackbar fields from WS3 still need values in `RecipeKeeperModelManagementLabels` ("$name will read your clipped recipes", "$name removed", "$name is not downloaded yet").

## Not done / out of scope

- "Pictures unavailable / Retry pictures" chips on detail: `ClippedRecipe` carries no picture-failure flag, so there is nothing to render; labels exist.
- No device QA (shared emulator rule). Previews compile; visual check is WS6.

## Verification

- `./gradlew :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug` — BUILD SUCCESSFUL.
- `./gradlew :app:recipekeeper:testDebugUnitTest` — 26 tests, 0 failures (LibraryViewModelTest 9, RecipeDetailViewModelTest 9, PendingRecipeDeletesTest 5, StubDraftParserTest 3).
- `./gradlew :app:recipekeeper:lintDebug` — clean.
- Kotlin compile of the app has no warnings in `app/recipekeeper` sources.
- No new project dependencies, so no boundary check needed.
