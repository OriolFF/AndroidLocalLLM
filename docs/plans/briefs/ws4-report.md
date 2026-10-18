# WS4 report — `:feature:webclipper`

Branch `recipekeeper/ws4`. Everything below builds with `./gradlew :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug` and the feature's 29 unit tests are green (`./gradlew :feature:webclipper:testDebugUnitTest`). `:feature:webclipper → releaseRuntimeClasspath` has no `:app` or `:feature` edges.

## Files

### Shared contract (minimal, as allowed by the brief)
- `core/domain/.../repository/LlmTaskRepository.kt` — new `LlmRequestOptions(systemInstruction, maxOutputTokens, temperature, jsonSchema)` with `LlmRequestOptions.Legacy`; new 4-arg `generateStream(engine, config, inputs, options)`; the 3-arg overload is now an interface default delegating with `Legacy` (identical behaviour: `systemPrompt() + "\n\n" + userPrompt(inputs)` as one prompt). New `LlmTaskParseException(rawOutput) : IllegalStateException` so callers can tell "parser returned null" from engine failures.
- `core/domain/.../usecase/GenerateLlmTaskUseCase.kt` — 4-arg `invoke(engine, config, inputs, options)` overload; 3-arg unchanged.
- `core/data/.../repository/LlmTaskRepositoryImpl.kt` — implements the 4-arg method (`systemInstruction = true` → separate system turn; `false` → legacy concatenation) and emits `LlmTaskParseException` on parser `null` (same message text as before).

### Feature (`feature/webclipper/**`)
| File | What |
|---|---|
| `build.gradle.kts` | + `libs.coil.compose` (thumbnails / picture strip; the app already ships `coil-network-okhttp`). |
| `WebViewHolder.kt` | Application-context WebView; settings per research §B; `WebViewClient` keeps http/https, hands other schemes to the system, recreates on `onRenderProcessGone` (bumps `generation`); `WebChromeClient` progress/title; `doUpdateVisitedHistory`; `saveState/restoreState`; `bindLifecycle/unbindLifecycle` for pause/resume; `Listener` for page events; scroll deltas via a private `WebView` subclass. |
| `ClipSession.kt` | + `activeExtraction`, `reopenCandidates` signals, `addCandidate`, `clearPage()` (keeps the active-extraction marker). |
| `WholePageCandidate.kt` | `wholePageCandidate(snapshot)` (id `whole-page`, `CandidatePayload.WholePage`) and `hostOf(url)`. |
| `capture/PageScanner.kt` | `PageScanner { quickScanStructured(); capture() }` bound to the holder's WebView (`WebViewPageScanner(PageCapture, WebViewHolder)`), so the ViewModel never imports `android.webkit`. |
| `capture/PictureDownloader.kt` | `fun interface PictureDownloader` + `ImageDownloaderPictureDownloader` adapter over `:core:web`'s final `ImageDownloader`. |
| `readiness/ModelReadiness.kt` | **TEMP shim** (see below). |
| `BrowserViewModel.kt` | URL submit (`resolveInput`: scheme kept / `https://` for host-like / `labels.searchUrlTemplate`), page progress, debounced (1.5 s) proactive `quickScanStructured`, `Scan` → capture → `config.detect` → `ClipSession.set` → navigate / sheet, FAB derivation, scroll collapse, reopen-candidates, share/copy/open-external. |
| `ExtractionViewModel.kt` | Phases Reading→Extracting→Pictures→Saving; `GenerateLlmTaskUseCase(engine, config.llmConfig, listOf(renderLlmInput), LlmRequestOptions(systemInstruction = true, maxOutputTokens = config.maxOutputTokens, temperature = 0.2))`; pictures downloaded in parallel (`async`, failures → `Warning`); STRUCTURED parse failure → `config.fallback()`; cancel = job cancel + `engine.cancel()` + temp file deletion + `PopToBrowser`; `TruncationHeuristic`; `NavigateToSaved(id)` 600 ms after the last tick; `session.clear()` on save. Result type erased to `Any` like `LlmTaskViewModel`. |
| `TruncationHeuristic.kt` | `looksTruncated(text, maxOutputTokens)`: ≥ 80 % of the cap at ~4 chars/token **and** last line without terminal punctuation / trailing `:`. |
| `mvi/BrowserIntent.kt` | + `PageStarted(url)`, `HistoryChanged(canGoBack, canGoForward)`, `PageLoadFailed`. |
| `mvi/BrowserEffect.kt` | + `ReloadPage`, `StopLoading`, `GoForward`, `OpenExternal(url)`, `CopyToClipboard(text)`. |
| `mvi/BrowserState.kt` | `readiness: ModelReadiness = ModelReadiness.Checking` (shim type). |
| `mvi/ExtractionState.kt` | + `sourceHost`, `pageTitle` (source strip). |
| `WebClipperLabels.kt` | + `scanFailed = "Couldn't read this page"` (capture failure). |
| `ui/BrowserTopBar.kt` | Back arrow, URL pill (host + lock/globe unfocused, `BasicTextField` select-all / `Uri` / `Go` focused, 720dp cap), Refresh↔Close, overflow menu, 2dp progress. |
| `ui/ClipFab.kt` | `ExtendedFloatingActionButton` + `AnimatedContent` over `ClipFabState`; one semantics node (`contentDescription` = label, `stateDescription`, `disabled()` for `PageLoading`, `traversalIndex = -1`); `label()` / `stateDescription()` helpers. |
| `ui/CandidateSheet.kt`, `ui/NothingDetectedSheet.kt` | `ModalBottomSheet`s with `*Content` composables split out for previews; `ListItem` ≥ 72dp, Coil thumbnail, `StatusDot` source badge. |
| `ui/WebClipperBrowserScreen.kt` | Own `Scaffold(contentWindowInsets = safeDrawing)`, FAB hidden while IME visible or no page, WebView slot as traversal group, sheets. |
| `ui/WebClipperExtractionScreen.kt` | Top bar + Close, source strip + "Not this one?", `PhaseProgress`, invisible polite live region, truncated banner, `renderDraft` slot, raw-output toggle (`StreamingCursor`, `clearAndSetSemantics` while running), picture strip with `toggleable(role = Checkbox)` tiles, Stop / View recipe, error table (§4); ≥ 600dp landscape puts phases in a 36 % column. |
| `ui/DesignShims.kt`, `ui/ReducedMotion.kt` | **TEMP shim** primitives (see below) + `rememberReducedMotion()`. |
| `WebClipperBrowserRoute.kt` | `koinViewModel { parametersOf(config, labels, initialUrl) }`, holder listener → intents, effects → holder / share / clipboard / external, `rememberSaveable` Bundle for WebView state on `ON_STOP`, `BackHandler(canGoBack)`, `key(generation) { AndroidView(...) }`. |
| `WebClipperExtractionRoute.kt` | `koinViewModel(key = candidateId) { parametersOf(candidateId, config, sink, labels) }`, elapsed-seconds ticker, system back = Cancel. **Signature grew**: optional `snackbarHostState: SnackbarHostState? = null` before `modifier` (for "Clip cancelled" / "Saved"). |
| `di/WebClipperModule.kt` | `single { ClipSession }`, `single { WebViewHolder }`, `single<PageScanner>`, `single<PictureDownloader>`, temp dir under `cacheDir/webclipper` (qualifier `WEBCLIPPER_TEMP_DIR_QUALIFIER`), `single<ReadinessSource>` (shim), both `viewModel { params -> … }` (new `org.koin.core.module.dsl.viewModel`), real engine via `get(named(REAL_ENGINE_QUALIFIER))`. |
| `previews/WebClipperPreviews.kt` | Light/dark previews: all 8 FAB states (expanded + collapsed), candidate sheet, nothing-detected sheet, browser chrome (found / loading), extraction (running, saved+truncated, parse failed, landscape). Uses a private M3-baseline `PreviewTokens` (no brand values). |
| `src/test/...` | `fakes/FakeLlmEngine.kt` (copy of `core/llm/src/test`), `fakes/TestSupport.kt` (`MainDispatcherRule`, `TestDispatchers`, `FakeWebClipConfig`, `FakeClipSink`, `FakePageScanner`, `FakePictureDownloader`, `FakeReadinessSource`, fixtures), `BrowserViewModelTest` (13), `ExtractionViewModelTest` (12), `TruncationHeuristicTest` (4). |

## Public signatures added / changed
```kotlin
// :core:domain
data class LlmRequestOptions(val systemInstruction: Boolean, val maxOutputTokens: Int? = null, val temperature: Double? = null, val jsonSchema: String? = null) { companion object { val Legacy } }
fun <T> LlmTaskRepository.generateStream(engine, config, inputs, options: LlmRequestOptions): Flow<LlmTaskEvent<T>>
operator fun <T> GenerateLlmTaskUseCase.invoke(engine, config, inputs, options: LlmRequestOptions): Flow<LlmTaskEvent<T>>
class LlmTaskParseException(val rawOutput: String) : IllegalStateException

// :feature:webclipper
class BrowserViewModel(config: WebClipConfig<*>, labels, scanner: PageScanner, session: ClipSession, readinessSource: ReadinessSource, dispatchers, initialUrl: String? = null)
class ExtractionViewModel(candidateId, config: WebClipConfig<Any>, sink: ClipSink<Any>, labels, session, generateTask, engine: LlmEngine, pictureDownloader: PictureDownloader, tempDir: File, dispatchers)
interface PageScanner; class WebViewPageScanner(PageCapture, WebViewHolder)
fun interface PictureDownloader; class ImageDownloaderPictureDownloader(ImageDownloader)
fun wholePageCandidate(snapshot): ClipCandidate; fun hostOf(url): String?; const val WHOLE_PAGE_CANDIDATE_ID
object TruncationHeuristic { fun looksTruncated(text, maxOutputTokens: Int?): Boolean }
@Composable fun WebClipperExtractionRoute(…, onNavigateToModelManagement, snackbarHostState: SnackbarHostState? = null, modifier = Modifier)
@Composable fun ClipFab(state, expanded, labels, onClick, modifier); fun ClipFabState.label(labels); fun ClipFabState.stateDescription(labels)
@Composable fun CandidateSheet / CandidateSheetContent / NothingDetectedSheet / NothingDetectedSheetContent / WebClipperBrowserScreen / WebClipperExtractionScreen / BrowserTopBar
```

## TEMP shims the lead must replace at merge (all marked `// TEMP shim`)
1. `feature/webclipper/src/main/kotlin/com/llmlocal/feature/webclipper/readiness/ModelReadiness.kt` — `ModelReadiness` (same shape as plan §2.4), `fun interface ReadinessSource { observe(scope): StateFlow<ModelReadiness> }`, `ShimModelReadinessObserver(selectionStore, progressStore, manager, engine, dispatchers)`. At merge: delete the sealed interface + shim observer, point `BrowserViewModel`, `BrowserState`, `WebClipperPreviews` and the tests' `FakeReadinessSource` at `com.llmlocal.core.llm.readiness.ModelReadiness`, and bind `ReadinessSource` in `webClipperModule` to WS3's `ModelReadinessObserver` (or inject it directly; only `observe(scope)` is used).
2. `feature/webclipper/src/main/kotlin/com/llmlocal/feature/webclipper/ui/DesignShims.kt` — `PhaseItem(label, status: PhaseStatus, detail: String? = null)`, `PhaseProgress(items)`, `SkeletonLine(modifier, height, widthFraction)`, `StreamingCursor(modifier, color)`. `PhaseStatus` is the feature's `mvi.PhaseStatus`; map to WS3's enum if it differs. The extra `detail` line (hint + elapsed + keep-app-open under the active phase) is used by `WebClipperExtractionScreen.PhaseList`; if WS3's `PhaseItem` has no `detail`, render that sub-line in the screen instead.

## Decisions worth knowing
- **`PageScanner` instead of `PageCapture` in the ViewModel** so unit tests never need an `android.webkit.WebView` (the JVM stub throws). `FakePageScanner` plays the role the brief calls `FakePageCapture`.
- **Readiness `Checking` / `Initializing` render as `ClipFabState.PageLoading`** (dimmed, disabled) since the spec table has no state for "engine warming up"; `NoModel` / `Failed` / `Downloading` → `ModelNotReady(progress?)`.
- **`Scan` while not ready** emits `ShowSnackbar(modelNotReadySnackbar)` then `NavigateToModelManagement`; the screen sends `OpenModelManagement` for a `ModelNotReady` tap directly.
- **Found(n) from the quick scan** has no candidates yet; the tap runs the full capture and then routes (1 → extraction, >1 → sheet, 0 → nothing-detected).
- **"View recipe" button** is shown once `savedId` is set in addition to the automatic `NavigateToSaved` after 600 ms (the spec's "only if the user scrolled" suppression is not implemented).
- **"Not this one?"** cancels the run, sets `ClipSession.reopenCandidates`, pops; the browser ViewModel reopens the sheet from the session's candidates.
- **Thumbnail fallback icon is `Icons.Outlined.Image`**, not `Restaurant`, to keep the feature brand-neutral.
- **Browser `Scaffold` hosts `SnackbarHost(snackbarHostState)`** so snackbars sit above the FAB; WS5b's `AppRoot` should not also host the same state while the browser is shown (or drop the host here).
- `WebClipperBrowserRoute` syncs the ViewModel from an already-loaded WebView only when the ViewModel has no URL yet (fresh ViewModel after process death / re-entry); it never re-syncs on return from extraction, so candidates survive.
- Overflow-menu / share / open-external / clipboard use plain Android intents + `ClipboardManager` (no Compose clipboard API).
- `ModelMissing` is recognised from an init failure whose message contains "does not exist" (what `LiteRtLlmEngine` throws) or a `FileNotFoundException`.

## Builds / tests run
- `./gradlew :feature:webclipper:testDebugUnitTest` — 29 tests, all passing.
- `./gradlew :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug :core:domain:test :core:data:testDebugUnitTest` — BUILD SUCCESSFUL; per-ABI debug APKs produced for all three apps.
- `./gradlew :feature:webclipper:dependencies --configuration releaseRuntimeClasspath` — project edges: `:core:common`, `:core:data`, `:core:designsystem`, `:core:domain`, `:core:llm`, `:core:model`, `:core:network`, `:core:web` only.
- Not run: lint, device install (shared emulator rule), `android-ux` review (no herdr pane was spawned; the composables follow the UX spec §2–§4 and §9 directly).

## Open items for WS5b / WS6
- `:app:recipekeeper` must install `webClipperModule` (already done in WS0) **and** provide `WebClipConfig<ClippedRecipe>` + `ClipSink<ClippedRecipe>` to the routes via `parametersOf` (the routes do not resolve them from Koin).
- Clip temp pictures live in `cacheDir/webclipper/`; `ClipSink.save` receives the kept files and should move/copy them.
- `ExtractionViewModel` runs the engine init itself (`engine.initialize()` is idempotent), so an extraction started right after a download still works without the readiness observer.
