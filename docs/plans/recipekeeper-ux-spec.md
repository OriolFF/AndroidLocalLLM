# UX Spec — `:app:recipekeeper` / Recipe Keeper (on-device recipe web clipper)

Produced 2026-10-18 by the project `android-ux` agent persona from the repo state. Companion to `recipekeeper-app-plan.md`. One correction applied: the spec assumed `DEFAULT_MAX_TOKENS = 1024` governs output; that constant is dead code in `LiteRtLlmEngine`, output length is controlled by LiteRT-LM defaults until WS1 passes `maxOutputToken`.

Facts relied on:
- `LlmTaskScreen` is "chips of inputs → one streamed card"; a WebView + overlay + multi-phase pipeline cannot be expressed by it → new feature module (`:feature:webclipper`, per plan A1).
- `LlmTaskRepositoryImpl` parses once at `Done`; the ViewModel gets raw `Token` deltas, so the streaming skeleton runs its own cheap incremental parse on `streamedText`.
- Debug builds run the model on CPU: page text must be trimmed before prompting.
- No Coil, Room, WebView helper or JSON lib in the catalog; `llmlocal.android.room` is an empty stub; `material3-adaptive` is in the catalog but unused.
- `ModelStatus`/`NoModelAvailableBanner` take `LlmTaskLabels`, so the clipper reuses `GradientHero` directly.
- Hardcoded copy in shared code: `AppErrorView` ("Retry"), `DownloadProgressBar` ("s left"/"m left"), `ModelManagementViewModel` snackbars, worker notification titles.

## 1. Navigation map

| Destination | Route | Presentation |
|---|---|---|
| Library | `library` | Full screen, `AppScaffold`; **start destination** |
| Browser | `browser?url={url}` | Full screen, own top bar (URL bar replaces title) |
| Candidate picker | state-driven | `ModalBottomSheet` over Browser (`BrowserState.sheet`) |
| Extraction | `extract/{candidateId}` | Full screen pushed over Browser |
| Recipe detail | `recipe/{recipeId}` | Full screen, custom collapsing hero |
| Model management | `modelManagement` | Full screen, `AppScaffold`, `ModelManagementRoute` as-is |
| Delete confirm | — | `AlertDialog` |

Why Library starts: value accrues in the library; a returning user opens the app to cook. Starting in the WebView puts the no-model banner over someone else's page. The library empty state explains the product, points to model management when no model is ready, and offers "Open browser" as the single primary action. Phase 2: `ACTION_SEND`/`ACTION_VIEW` intent filter → `browser?url=`.

Back-stack rules:
- Library → Browser: `navigate("browser")`. System back on Browser: `BackHandler(enabled = canGoBack)` pops WebView history first, then Navigation pops to Library.
- Browser → Extraction: the WebView must survive: keep the instance in an application-context holder (not `remember`). Cancel returns to the same page without reload.
- Extraction → Detail on success: `navigate("recipe/{id}") { popUpTo("browser") }` so back from Detail returns to the page. Extraction is never on the back stack after completion.
- Detail → "Open original": `navigate("browser?url=…") { launchSingleTop = true }`.
- Model management reachable from Library top bar, the not-ready clip button, the library no-model hero and the extraction error state; use the existing `navigateToModelManagement()` pattern. **Model acquisition is exactly the shared flow: the user chooses and downloads a model there. This app has no one-tap default download and no model-gate sheet (plan A15).**
- One `SnackbarHostState` owned by `AppRoot`.

Top-bar ownership: Library, Model management → `AppScaffold`. Browser and Detail use their own `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`.

ViewModels: `LibraryViewModel`, `BrowserViewModel` (owns scan + candidates + FAB state), `ExtractionViewModel`, `RecipeDetailViewModel`, plus shared `ModelReadinessObserver`.

## 2. Browser screen

```
┌ statusBars inset ────────────────────────────┐
│ [←] ( 🔒 cooking.site/pasta…        ⟳ )  [⋮] │  48dp row
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │  2dp LinearProgressIndicator (1..99%)
├──────────────────────────────────────────────┤
│                 AndroidView(WebView)         │
│                              ┌─────────────┐ │
│                              │ ✂ Clip recipe│ │  ExtendedFAB, 16dp end, 16dp + navBar bottom
│                              └─────────────┘ │
└ navigationBars inset (not drawn over) ───────┘
```

Top bar (`BrowserTopBar`): `Surface(tonalElevation = 2.dp)` + `statusBarsPadding()`. Leading `IconButton` 48dp `ArrowBack` (`labels.browserBackToLibrary`) pops to Library. URL pill: `BasicTextField` in a 48dp `Surface(shape = extraLarge, color = surfaceVariant)`; unfocused shows host only with `Lock`/`Public` icon; focused shows full URL, select all, `KeyboardType.Uri`, `ImeAction.Go`. No scheme and (space or no dot) → search via `labels.searchUrlTemplate`. Trailing icon toggles `Refresh` ↔ `Close` while loading. `MoreVert` menu: Forward, Share page, Open in external browser, Copy link. 2dp progress bar with `AnimatedVisibility` when 0<progress<100. IME: WebView container gets `imePadding()`. 600dp+: pill capped at 720dp, centred.

WebView container: `Modifier.weight(1f).navigationBarsPadding()` so page never renders under the gesture bar; only the FAB floats. `onProgressChanged` → `PageProgress`; `onPageFinished` → `PageFinished(url,title)`; `doUpdateVisitedHistory` → canGoBack/Forward; scroll listener → FAB expand/collapse. JS + DOM storage enabled.

Clip FAB (`ClipFab`): `ExtendedFloatingActionButton` bottom-end, 56dp; expanded on page load / scroll up, collapsed circle on scroll down; `AnimatedContent` over `ClipFabState`.

| `ClipFabState` | Visual | Label | Tap |
|---|---|---|---|
| `ModelNotReady(download?)` | `secondaryContainer`, icon `Memory`; RUNNING → 24dp determinate ring | `fabChooseModel` / `fabDownloading(percent)` | Navigate to Model management |
| `PageLoading` | primary container 38% alpha, `ContentCut` | `fabClip` | no-op + snackbar `waitForPage`; `stateDescription = fabUnavailableWhileLoading` |
| `Idle` | `primaryContainer`, `ContentCut` | `fabClip` | `Scan` |
| `Scanning` | 24dp indeterminate ring | `fabScanning` | disabled (JS, <1 s) |
| `Found(n)` | `BadgedBox` count over `ContentCut`; container `primary` | `fabFound(n)` | n==1 → Extraction; n>1 → Candidate sheet |
| `Extracting` | `AutoAwesome` + ring | `fabExtracting` | return to Extraction |

Proactive scan on `PageFinished`: only the cheap JSON-LD/microdata detector; ≥1 found → `Found(n)` without a tap. Heavier readability fallback runs on tap. FAB ≥ 56dp, never < 48dp collapsed. `contentDescription` = label; count in `stateDescription`. Long-press → "Hide on this page" (optional); a 48dp `AssistChip` "Show clip button" docks bottom-start. Hide FAB while IME is visible.

```kotlin
data class BrowserState(
  val url: String = "", val draftUrl: String = "", val urlFocused: Boolean = false,
  val pageTitle: String? = null, val pageProgress: Int = 0, val isLoading: Boolean = false,
  val canGoBack: Boolean = false, val canGoForward: Boolean = false,
  val fab: ClipFabState = ClipFabState.PageLoading, val fabExpanded: Boolean = true,
  val candidates: List<ClipCandidate> = emptyList(),
  val sheet: BrowserSheet = BrowserSheet.None,   // None | Candidates | NothingDetected
  val readiness: ModelReadiness,
)
sealed interface BrowserIntent { UpdateDraftUrl; SubmitUrl; Reload; Stop; GoForward; PageProgress; PageFinished; Scrolled; Scan; PickCandidate(id); ExtractWholePage; DismissSheet; OpenModelManagement; SharePage; CopyLink; OpenExternal }
sealed interface BrowserEffect { NavigateToExtraction(candidateId); NavigateToModelManagement; ShowSnackbar(msg); LoadUrl(url); ShareText(text) }
```

## 3. Candidate picker (`CandidateSheet`, `ModalBottomSheet`)

Header: `labels.candidatesTitle` ("Which recipe?") + `labels.candidatesSubtitle(n, host)`. Items: M3 `ListItem` ≥72dp, whole row clickable; leading 56dp thumbnail (`AsyncImage`, `Restaurant` fallback); headline title (2 lines); supporting `candidateMetaTemplate(ingredientCount, totalMinutes)` or first ~100 chars; trailing `StatusDot(sourceStructured | sourceText, DataObject | Article, Installed | Idle)`.
Single detection: skip the sheet → Extraction; Extraction header shows `TextButton(notThisOne)` when other candidates exist.
Nothing detected: `EmptyState(SearchOff, nothingDetectedTitle, nothingDetectedMessage)` + `Button(extractAnyway)` → `ExtractWholePage` + `TextButton(keepBrowsing)`. Cap 10, structured first.

## 4. Extraction progress (`ClipExtractionScreen`)

Full screen (LLM 5–60 s on CPU). LazyColumn:
1. `TopAppBar(title = extractionTitle, navigationIcon = Close → cancel)`.
2. Source strip `host · page title` + `notThisOne` when applicable.
3. `PhaseProgress` (shared primitive): Reading page → Extracting recipe → Saving pictures → Saving to library. Done = `CheckCircle` in `designTokens.successColor`; active = 20dp ring; pending = `outlineVariant` circle; failed = `ErrorOutline` error; warning tone for partial. Active phase sub-line `extractingHint` / elapsed seconds.
4. `ClipDraftCard` (app slot `renderDraft`): skeleton that fills in. Fixed order Title → Meta row → Ingredients → Steps; `SkeletonLine`s replaced as the incremental draft parser (debounced ~80 ms) completes lines, with `animateContentSize()` and per-row fade/slide. The unparsed tail renders dimmed with `StreamingCursor`. Overflow `showRawOutput` toggles monospace raw view. Picture strip of 96dp thumbnails with keep `Checkbox` (first 3 checked); downloads run in parallel with the LLM and the pictures are only stored with the recipe, never shown to the model.
5. `OutlinedButton(stopExtraction, StopCircle)` full width while running. On completion the last phase ticks and after ~600 ms `NavigateToRecipe(id)`; if the user scrolled, show `Button(viewRecipe)` instead.

Cancel: immediate job cancel + picture jobs cancelled, nothing persisted, pop to Browser, snackbar `clipCancelled`. No confirmation.

| Failure | Phase mark | Body | Actions |
|---|---|---|---|
| Engine init / generation failed | Extracting ✕ | `EmptyState(ErrorOutline, modelFailedTitle, reason)` | `tryAgain`, `backToPage` |
| Parser null at Done | Extracting ✕ | `parseFailedTitle` / `parseFailedMessage` + raw expander | `tryAgain`, `backToPage`, `tryWholePage` if candidate was STRUCTURED (plan: deterministic fallback first) |
| Output truncated | Extracting ⚠ | banner `truncatedWarning` | save proceeds; detail shows `incompleteChip` |
| Pictures: no network / 4xx | Pictures ⚠ | non-blocking | detail `picturesUnavailable` + `retryPictures` |
| Model file removed mid-flow | Extracting ✕ | as model failed | `openModels` |
| Process death | — | `keepAppOpenHint` under active phase | Phase 2: WorkManager |

```kotlin
data class ExtractionState(
  val candidate: ClipCandidate, val otherCandidates: Int,
  val phases: Map<Phase, PhaseStatus>,  // Reading, Extracting, Pictures, Saving → Pending | Active | Done | Failed | Warning
  val streamedText: String = "", val pictures: List<PictureDraft> = emptyList(),  // url, localFile?, keep, status
  val isRunning: Boolean, val error: ExtractionError? = null, val truncated: Boolean = false,
  val showRaw: Boolean = false, val savedId: String? = null,
)
sealed interface ExtractionIntent { Start; Cancel; Retry; RetryWholePage; TogglePicture(url); ToggleRaw; BackToCandidates }
sealed interface ExtractionEffect { NavigateToRecipe(id); PopToBrowser; ShowSnackbar(msg) }
```

## 5. Library and Recipe detail

Library (`AppScaffold`, title `libraryTitle`; actions Search, `Psychology` → models). `LazyVerticalGrid(GridCells.Adaptive(168.dp))`, 16dp padding, 12dp spacing. `RecipeTile`: `Card(shape = large)`, 4:3 `AsyncImage` or `heroLightGradient` placeholder with `Restaurant`; title `titleMedium` 2 lines; meta `host · total time`; `incompleteChip` when truncated. FAB `ExtendedFloatingActionButton(Public, libraryFabClip)` → Browser (hidden when empty state shown). Model banner when no model is ready: `GradientHero(libraryNoModelTitle, libraryNoModelSubtitle, Memory, chooseModelAction)` spanning all columns; its action navigates to model management (no in-place download). While a download is RUNNING a compact card with `DownloadProgressBar` + `downloadingModel(name)` that also opens model management on tap. Empty: `EmptyState(MenuBook, libraryEmptyTitle, libraryEmptyMessage, openBrowser)`. Search-empty: `EmptyState(SearchOff, searchEmptyTitle(q), clearSearch)`. Loading: `AppLoadingIndicator` first emission only. Long-press tile menu: Delete, Share, Open original. No swipe-to-delete.

Detail: own `Scaffold`, collapsing hero 16:10 (`AsyncImage` + bottom scrim 60%, title `headlineMedium` on scrim) or `GradientHero(title, host)` when no picture; transparent `TopAppBar` with `ArrowBack`, `Share`, `MoreVert` (Delete, Open original, Re-extract, Keep screen on). Body: meta `FlowRow` of `AssistChip`s (servings/prep/cook/total + incomplete/pictures-unavailable); source `ListItem(Link, host, clippedOn(date), TextButton(openOriginal))`; Ingredients with `SectionHeader`, rows `toggleable(role = Checkbox)` ≥48dp, strike-through when checked, persisted per recipe, header `resetChecks`; Steps with `NumberBadge`, tap highlights current step; `HorizontalPager` pictures when >1; Notes. Share: `ACTION_SEND` text via `shareTemplate`. Delete: `AlertDialog(deleteTitle, deleteMessage, delete (error colour), cancel)` → soft delete, pop, snackbar `recipeDeleted` + `undo` 5 s, then hard delete + files. 600dp+/landscape: ingredients | steps two panes (cap 720dp or `ListDetailPaneScaffold` later).

## 6. Reuse vs new

Reuse as-is: `AppScaffold`, `GradientHero`, `StatusDot`/`StatusTone`, `EmptyState`, `AppLoadingIndicator`, `ModelManagementRoute`, `DownloadProgressBar`, `GenerateLlmTaskUseCase` + `LlmTaskConfig<T>`.
Avoid: `AppErrorView` until it takes `retryLabel`; `NoModelAvailableBanner` (coupled to `LlmTaskLabels`).
New shared primitives (`:core:designsystem/component/`): `PhaseProgress`, `StreamingText` + `StreamingCursor` (currently duplicated in both app cards, which also hardcode "Warming up the model…"), `SkeletonLine`, `SectionHeader`, `NumberBadge`, optional `AdaptiveContentWidth`.
New shared non-UI: `ModelReadinessObserver` in `:core:llm`.
Feature (`:feature:webclipper`): browser, FAB, sheets, extraction screen, `WebClipperLabels`. App-only: library, detail, `ClipDraftCard`, Room, tokens, labels.
New deps: Coil 3, Room + KSP, `androidx.webkit`.

## 7. Labels (English defaults)

Split: Browser/FAB/Candidates/Extraction fields → `WebClipperLabels` (feature); Library/Detail/Delete → app `RecipeKeeperLibraryLabels`.

```kotlin
// ---- WebClipperLabels (feature) ----
browserBackToLibrary = "Back to library"; urlFieldHint = "Search or enter a site"; urlFieldDescription = "Address bar"
reloadPage = "Reload"; stopLoading = "Stop loading"; moreOptions = "More options"; goForward = "Forward"
sharePage = "Share page"; openExternal = "Open in browser"; copyLink = "Copy link"; linkCopied = "Link copied"
searchUrlTemplate: (String) -> String = { q -> "https://duckduckgo.com/?q=${Uri.encode(q)}" }
pageLoadError = "This page couldn't be loaded"
fabClip = "Clip recipe"; fabScanning = "Scanning page…"
fabFound: (Int) -> String = { n -> if (n == 1) "Recipe found" else "$n recipes found" }
fabExtracting = "Extracting…"; fabChooseModel = "Choose a model"; fabDownloading: (Int) -> String = { p -> "Downloading $p%" }
fabUnavailableWhileLoading = "Unavailable until the page finishes loading"; waitForPage = "Wait for the page to finish loading"
fabHideForPage = "Hide on this page"; showClipButton = "Show clip button"
modelNotReadySnackbar = "Choose and download a model first"
candidatesTitle = "Which recipe?"; candidatesSubtitle: (count, host) -> String = { n, h -> "$n recipes found on $h" }
candidateMetaTemplate: (Int?, Int?) -> String = { i, m -> listOfNotNull(i?.let { "$it ingredients" }, m?.let { "$it min" }).joinToString(" · ") }
sourceStructured = "Structured"; sourceText = "Text"; candidateThumbnailDescription: (String) -> String = { t -> "Picture of $t" }
nothingDetectedTitle = "No recipe found on this page"
nothingDetectedMessage = "Open the recipe itself — list, search and category pages don't contain the ingredients."
extractAnyway = "Extract anyway"; keepBrowsing = "Keep browsing"; notThisOne = "Not this one?"
extractionTitle = "Clipping recipe"; cancelExtraction = "Cancel"; stopExtraction = "Stop"
phaseReading = "Reading page"; phaseExtracting = "Extracting recipe"; phasePictures = "Saving pictures"; phaseSaving = "Saving to library"
extractingHint = "Running on your phone — this takes a moment"; elapsedTemplate: (Int) -> String = { s -> "${s}s" }
keepAppOpenHint = "Keep the app open until it finishes"
keepPicture = "Keep this picture"; showRawOutput = "Show raw output"; hideRawOutput = "Hide raw output"; viewRecipe = "View recipe"
clipCancelled = "Clip cancelled"; recipeSaved = "Saved to your library"
modelFailedTitle = "The model couldn't run"; parseFailedTitle = "Couldn't read a recipe from this page"
parseFailedMessage = "The page text was sent, but no ingredients or steps came back."
truncatedWarning = "The recipe may be incomplete"; tryAgain = "Try again"; tryWholePage = "Try the whole page"; backToPage = "Back to page"; openModels = "Open models"
picturesUnavailable = "Pictures unavailable"; retryPictures = "Retry pictures"

// ---- RecipeKeeperLibraryLabels (app) ----
libraryTitle = "Your recipes"; libraryFabClip = "Open browser"; libraryEmptyTitle = "No recipes yet"
libraryEmptyMessage = "Browse any cooking site and tap Clip. The recipe is extracted on your phone and saved here."
openBrowser = "Open browser"; searchHint = "Search recipes or ingredients"; searchEmptyTitle: (String) -> String = { q -> "Nothing matches “$q”" }; clearSearch = "Clear search"
recipeTileDescription: (title, host) -> String = { t, h -> "$t, from $h" }
libraryNoModelTitle = "Choose a model to start clipping"
libraryNoModelSubtitle = "Recipes are extracted on your phone by a local language model. Pick one in Models; it works offline afterwards."
chooseModelAction = "Open models"; downloadingModel: (name) -> String = { n -> "Downloading $n…" }; manageModels = "Manage models"
back = "Back"; shareRecipe = "Share recipe"
servingsTemplate: (Int) -> String = { n -> if (n == 1) "1 serving" else "$n servings" }; prepTemplate = { m -> "Prep $m min" }; cookTemplate = { m -> "Cook $m min" }; totalTemplate = { m -> "$m min total" }; minutesTemplate = { m -> "$m min" }
clippedOn: (date) -> String = { d -> "Clipped $d" }; openOriginal = "Open original"
ingredientsHeader = "Ingredients"; stepsHeader = "Steps"; notesHeader = "Notes"; picturesHeader = "Pictures"; resetChecks = "Reset"
checkedCount: (c, t) -> String = { c, t -> "$c of $t checked" }; stepDescription: (i, t) -> String = { i, t -> "Step $i of $t" }
keepScreenOn = "Keep screen on"; reExtract = "Re-extract from source"; heroImageDescription: (String) -> String = { t -> "Photo of $t" }
shareTemplate: (title, url) -> String = { t, u -> "$t\n$u" }; incompleteChip = "Possibly incomplete"
delete = "Delete"; cancel = "Cancel"; deleteTitle = "Delete this recipe?"; deleteMessage = "Its saved pictures are removed too."; recipeDeleted = "Recipe deleted"; undo = "Undo"
```

`RecipeKeeperModelManagementLabels`: every field as recipes except `introText` = "Tap a model to download, view details, or select it for recipe clipping. Downloads run in the background and survive closing the app."
Shared fix (plan A10): `ModelManagementLabels` += `modelSelectedSnackbar(name)` ("$name will read your clipped recipes" for Recipe Keeper), `modelRemovedSnackbar(name)`, `modelNotDownloadedSnackbar(name)`; VM receives labels via `parametersOf`.

## 8. Brand direction — `RecipeKeeperTokens`

Recipes = orange/caramel/terracotta; Medical = teal/clinical blue. Recipe Keeper reads as scrapbook / editorial cookbook; avoid orange and teal.
- Primary plum (H≈335): `Plum40 = 0xFF7A3B5E`, `Plum80 = 0xFFF0B3D1`, `onPrimary = Plum95`.
- Secondary olive (H≈95): `Olive40 = 0xFF596B3A`, `Olive80 = 0xFFBFD39A` → secondary containers, candidate badges, meta chips.
- Tertiary saffron (H≈45): `Saffron40 = 0xFF8A6A12`, `Saffron80 = 0xFFF2D27A` → FAB badge, Found accent, incomplete chip.
- Neutrals parchment: `Paper99 = 0xFFFFFBF7`, `Paper95 = 0xFFF6EFE6`, `Ink10 = 0xFF1C1418`; dark `Ink15 = 0xFF231A20`.
- Error: M3-standard warm reds. `successColor = 0xFF3E7A4E`.
- Hero gradients: light `Paper95 → 0xFFFFECF3 → Paper99`; dark `0xFF5A2744 → Ink20 → Ink15`.
- Typography: `FontFamily.Serif` SemiBold on display/headline/titleLarge; sans for the rest; body 16/24 (consider 17sp bodyLarge).
- Shapes one step flatter: 4/8/12/16/24. `dynamicColor = false`. FAB container `primaryContainer`, escalate to `primary` only in `Found(n)`.

## 9. Accessibility and adaptive

- FAB is one node: `contentDescription` = label; count/progress in `stateDescription`; `PageLoading` → `disabled()` + reason; live region Polite on the label text only.
- Streaming: do not mark the card a live region; one invisible `Text(liveRegion = Polite)` announces phase changes and milestones ("8 ingredients found"); raw tail `clearAndSetSemantics {}` while running; skeletons `invisibleToUser()`.
- URL pill: `Role.Button` semantics when unfocused; normal text-field semantics when focused.
- FAB `traversalIndex = -1f`; WebView host `isTraversalGroup = true`.
- Ingredient rows `toggleable(role = Checkbox)` ≥48dp; steps prefixed with `stepDescription`.
- Contrast: Plum40 on Paper99 ≈ 7:1; Plum80 on Ink15 ≈ 9:1; verify hero title on 60% scrim ≥ 4.5:1.
- Reduced motion: disable shimmer and cursor pulse when animator scale is 0.
- 600dp+: grid adapts; detail splits ingredients | steps; browser pill max 720dp; sheet keeps M3 max width. Landscape: extraction puts `PhaseProgress` in a left column (36%); detail hero 21:9 capped 320dp; FAB respects `safeDrawing`.

## 10. Ranked risks / open questions

1. Output/context budget on CPU (set `maxOutputToken`, trim input, honest "Possibly incomplete").
2. Extraction dies if the user leaves the app (v1 hint + keep screen on; v2 worker).
3. Multi-GB non-resumable model downloads before the first clip (shared gotcha; the user chooses the model in model management, nothing app-specific to do here).
4. Structured data covers most sites; decide whether STRUCTURED skips the LLM (plan: no, with deterministic fallback).
5. FAB vs site chrome; consider a `BottomAppBar` alternative.
6. WebView retention across navigation (application-context holder, explicit lifecycle).
7. Late-hydrating pages (debounced proactive scan; MutationObserver later).
8. Image rights & storage (text-only share, cap/resize).
9. Heuristic picture picking may choose an ad/logo (keep checkboxes; "Change picture" later).
10. Servings scaling, editing, tags, export: Phase 2.
11. Shared-copy debt now bites a third brand (fix in WS3).
12. Debug CPU latency: the skeleton-that-fills-in is the main mitigation, not polish.
