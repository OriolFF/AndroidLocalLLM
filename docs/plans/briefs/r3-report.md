# R3 report — `:feature:webclipper` refactor

Branch `refactor/r3`. Only `feature/webclipper/**` and this report were touched; `:app:recipekeeper` was **not** edited.

## Package tree (`com.llmlocal.feature.webclipper`)
```
domain/                      pure Kotlin (only :core:common/domain/web/llm *types*; no android, koin, presentation, data)
  WebClipConfig, PicturePick, ClipSink, ClipSaveOptions       contracts the app implements (semantics unchanged)
  ClipCandidate, CandidateSource, CandidatePayload            candidate model
  WholePageCandidate.kt (WHOLE_PAGE_CANDIDATE_ID, wholePageCandidate, hostOf)
  TruncationHeuristic
  ClipSession (interface + Current)
  PageScanner, PictureDownloader, ReadinessSource, LlmGenerationSource, TempPictureStore   source interfaces
  ClipProgress.kt   Phase, PhaseStatus, PictureStatus, ExtractionError, ClipRequest, ProposedPicture, sealed ClipProgress
  usecase/ ScanPageUseCase, ObserveReadinessUseCase, ClipUseCase<T>, CheckDuplicateUseCase, CancelClipUseCase
data/
  WebViewHolder, WebViewPageScanner, ImageDownloaderPictureDownloader, ModelReadinessSource,
  InMemoryClipSession, EngineLlmGenerationSource (engine + GenerateLlmTaskUseCase + LlmRequestOptions), CacheDirTempPictureStore
presentation/
  BrowserViewModel, ExtractionViewModel, WebClipperBrowserRoute, WebClipperExtractionRoute, WebClipperLabels
  mvi/ (Browser*/Extraction* Intent/State/Effect, PictureDraft, BrowserSheet, ClipFabState)
  ui/ (screens, ClipFab, BrowserTopBar, sheets), previews/
di/ WebClipperModule.kt (webClipperModule, WEBCLIPPER_TEMP_DIR_QUALIFIER)
```

## Use cases
| Use case | Responsibility | Depends on |
|---|---|---|
| `ScanPageUseCase` | `quick()` structured-count badge (failures → 0); `invoke(config)` full capture → `config.detect` (≤10) → stores page+candidates in the session | `PageScanner`, `ClipSession`, `DispatcherProvider` |
| `ObserveReadinessUseCase` | shared model-readiness `StateFlow` | `ReadinessSource` |
| `ClipUseCase<T>` | whole pipeline as `Flow<ClipProgress>`: direct path, render, engine init, stream, accept/fallback, parallel pictures, truncation, save, session clear; cleans temp files / releases marker on failure or collector cancel | `LlmGenerationSource`, `PictureDownloader`, `TempPictureStore`, `ClipSession`, `DispatcherProvider` |
| `CheckDuplicateUseCase` | `sink.existingId`, throwing sink ⇒ `null` | `DispatcherProvider` (sink passed per call) |
| `CancelClipUseCase` | stop engine, release extraction marker, optionally request candidate-sheet reopen (`invoke(reopenCandidates)`) | `LlmGenerationSource`, `ClipSession` |

`ClipUseCase` is invoked as `clip(config, sink, ClipRequest(candidate, snapshot, replaceId, isPictureKept))`; `config`/`sink` are per-call so the use case is a plain Koin `factory`. `isPictureKept` is read once, right before saving, so checkbox toggles made while the model runs count. The 600 ms "navigate beat" and snackbar/effects stay in `ExtractionViewModel`. `RetryPicturesUseCase` is not here (app-level).

## Moved FQNs (old → new), prefix `com.llmlocal.feature.webclipper.`
| Old | New |
|---|---|
| `WebClipConfig`, `PicturePick`, `ClipSink`, `ClipSaveOptions` | `domain.*` |
| `ClipCandidate`, `CandidateSource`, `CandidatePayload` | `domain.*` |
| `wholePageCandidate`, `hostOf`, `WHOLE_PAGE_CANDIDATE_ID` | `domain.*` (top-level, same names) |
| `TruncationHeuristic` | `domain.TruncationHeuristic` |
| `ClipSession` (class) | `domain.ClipSession` (**interface**); impl `data.InMemoryClipSession` |
| `capture.PictureDownloader` | `domain.PictureDownloader` (still `fun interface`, same signature) |
| `capture.PageScanner` / `capture.WebViewPageScanner` | `domain.PageScanner` / `data.WebViewPageScanner` |
| `capture.ImageDownloaderPictureDownloader` | `data.ImageDownloaderPictureDownloader` |
| `readiness.ReadinessSource` / `asReadinessSource()` | `domain.ReadinessSource` / `data.ModelReadinessSource` (class) |
| `WebViewHolder` | `data.WebViewHolder` |
| `WebClipperLabels` | `presentation.WebClipperLabels` |
| `WebClipperBrowserRoute`, `WebClipperExtractionRoute` | `presentation.*` |
| `BrowserViewModel`, `ExtractionViewModel` | `presentation.*` |
| `mvi.*` (Browser*/Extraction* …, `PictureDraft`) | `presentation.mvi.*` |
| `mvi.Phase`, `mvi.PhaseStatus`, `mvi.PictureStatus`, `mvi.ExtractionError` | `domain.*` (needed by the domain events) |
| `ui.*`, `previews.*` | `presentation.ui.*`, `presentation.previews.*` |
| `di.webClipperModule`, `di.WEBCLIPPER_TEMP_DIR_QUALIFIER` | unchanged |
| `ExtractionViewModel.EXTRACTION_TEMPERATURE` | `data.EngineLlmGenerationSource.EXTRACTION_TEMPERATURE` |
| `ExtractionViewModel.DEFAULT_KEEP`, `MAX_LONG_EDGE_PX` | `domain.usecase.ClipUseCase.DEFAULT_KEEP`, `.MAX_LONG_EDGE_PX` |
| `BrowserViewModel.MAX_CANDIDATES` | `domain.usecase.ScanPageUseCase.MAX_CANDIDATES` |

### Importers to fix in `:app:recipekeeper` (not edited; only imports break)
`MainActivity.kt`, `config/RecipeKeeperClipperLabels.kt`, `config/RecipeKeeperLibraryLabels.kt`, `config/RecipeKeeperConfigModule.kt`, `config/RecipeKeeperUiModule.kt`, `clip/RecipeKeeperClipConfig.kt`, `clip/RecipeKeeperClipSink.kt`, `detail/RecipeDetailViewModel.kt` and tests `clip/RecipeKeeperPipelineTest`, `clip/RecipeKeeperClipConfigTest`, `testing/FakePictureDownloader`, `eval/*`. Mechanical rewrite:
- `…webclipper.{WebClipConfig,PicturePick,ClipSink,ClipSaveOptions,ClipCandidate,CandidateSource,CandidatePayload,wholePageCandidate}` → `…webclipper.domain.<same>`
- `…webclipper.capture.PictureDownloader` → `…webclipper.domain.PictureDownloader`
- `…webclipper.{WebClipperBrowserRoute,WebClipperExtractionRoute,WebClipperLabels}` → `…webclipper.presentation.<same>`
- `di.webClipperModule` / `di.WEBCLIPPER_TEMP_DIR_QUALIFIER` unchanged.

Verified: `:app:recipekeeper:assembleDebug` fails only with unresolved references to the types above (no other errors).

## Final public signatures
```kotlin
// presentation
@Composable fun <T : Any> WebClipperBrowserRoute(config: WebClipConfig<T>, sink: ClipSink<T>, labels: WebClipperLabels,
    initialUrl: String?, onNavigateToModelManagement: () -> Unit, onNavigateToExtraction: (candidateId: String) -> Unit,
    onBackToLibrary: () -> Unit, snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier)
@Composable fun <T : Any> WebClipperExtractionRoute(candidateId: String, config: WebClipConfig<T>, sink: ClipSink<T>,
    labels: WebClipperLabels, renderDraft: @Composable (streamedText: String, isRunning: Boolean) -> Unit,
    onSaved: (id: String) -> Unit, onCancelled: () -> Unit, onNavigateToModelManagement: () -> Unit,
    modifier: Modifier = Modifier, snackbarHostState: SnackbarHostState? = null)
// unchanged parameter lists; only the package moved.

class BrowserViewModel(config: WebClipConfig<*>, labels: WebClipperLabels, scanPage: ScanPageUseCase,
    observeReadiness: ObserveReadinessUseCase, session: ClipSession, dispatchers: DispatcherProvider, initialUrl: String? = null)
class ExtractionViewModel(candidateId: String, config: WebClipConfig<Any>, sink: ClipSink<Any>, labels: WebClipperLabels,
    session: ClipSession, clip: ClipUseCase<Any>, checkDuplicate: CheckDuplicateUseCase, cancelClip: CancelClipUseCase,
    dispatchers: DispatcherProvider)

// domain contracts (semantics unchanged; only package) — WebClipConfig<T>, ClipSink<T>, ClipSaveOptions, PicturePick as before.
interface LlmGenerationSource { suspend fun initialize(): Outcome<Unit>
    fun <T : Any> generate(config: LlmTaskConfig<T>, input: String, maxOutputTokens: Int): Flow<LlmTaskEvent<T>>; fun cancel() }
interface TempPictureStore { fun newFile(runId: String, index: Int): File; fun delete(file: File) }
class ClipUseCase<T : Any> { operator fun invoke(config: WebClipConfig<T>, sink: ClipSink<T>, request: ClipRequest): Flow<ClipProgress> }
```
`ClipProgress` = `PhaseChanged | Streamed | ExtractedWithoutModel | PicturesProposed | PictureChanged | Truncated | Failed | Saved`.

## Koin (`webClipperModule`, same name)
- singles: `ClipSession ← InMemoryClipSession`, `WebViewHolder`, `PageScanner ← WebViewPageScanner`, `PictureDownloader ← ImageDownloaderPictureDownloader`, `TempPictureStore ← CacheDirTempPictureStore(named(WEBCLIPPER_TEMP_DIR_QUALIFIER))`, `ReadinessSource ← ModelReadinessSource`, `LlmGenerationSource ← EngineLlmGenerationSource(named(REAL_ENGINE_QUALIFIER))`, the temp-dir `File`.
- factories: `ScanPageUseCase`, `ObserveReadinessUseCase`, `CheckDuplicateUseCase`, `CancelClipUseCase`, `ClipUseCase<Any>`.
- ViewModels: same `parametersOf(config, labels, initialUrl)` / `parametersOf(candidateId, config, sink, labels)` as before.

## Tests
- `:feature:webclipper:testDebugUnitTest` — **56 tests, 0 failures** (`TruncationHeuristicTest` 4, `ClipUseCaseTest` 20, `BrowserViewModelTest` 18, `ExtractionViewModelTest` 14 — now the thin VM/state-mapping tests).
- `ExtractionViewModelTest` pipeline scenarios moved to `domain/usecase/ClipUseCaseTest` (event-level assertions). Fakes still implement the domain interfaces (`PageScanner`, `PictureDownloader`, `ReadinessSource`, `ClipSink`, `WebClipConfig`); the LLM fake is `FakeLlmEngine` behind the real `EngineLlmGenerationSource`.
- `:feature:webclipper:compileDebugAndroidTestKotlin` compiles (`BrowserTopBarTest` moved to `presentation.ui`; not run on a device).
- `:app:recipekeeper:assembleDebug` — fails **only** on the moved imports listed above, as expected.

## Deviations / left for the lead
1. **`WebClipperBrowserRoute` imports `data.WebViewHolder`** (`koinInject<WebViewHolder>()`): the route hosts the real WebView, which is Android UI state with no useful domain abstraction. This is the single `presentation → data` edge. If the lead wants it removed: add a `WebViewHost` interface in `presentation/` and have the DI module bind a small adapter in `di/`.
2. **VMs still take `ClipSession`** (a domain interface) as the brief allows (`ClipSessionSource` collapsed into the one `ClipSession` interface rather than a second near-identical one). `ExtractionViewModel` uses it only for the initial candidate lookup, `addCandidate` on "whole page" retry, and the page snapshot.
3. `ExtractionViewModel.dispatchers` is kept per the brief's signature but is now unused (all dispatching is inside the use cases); `BrowserViewModel.dispatchers` likewise.
4. `domain/` still imports *types* from `:core:llm` (`ModelReadiness`) and `:core:web` (`PageSnapshot`, `JsonLdNode`, `TextSection`) — plain data types, no concrete engine/WebView classes.
5. `ClipSession.activeExtraction` is now set when the `ClipUseCase` flow starts being collected (not synchronously in `start()`); no observable difference in the UI.
