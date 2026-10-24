# I2 report — images clip feature (importer, prepare/review, pipeline branches, extraction screen)

Branch `inputs/i2-images-clip` (from I0). Gate: vision **yes** (budget 280, max 3, `maxLongEdgePx` 2400, tiling on, `tallAspect` 2.5). Nothing user-visible changes until the lead flips `IMAGES_SOURCE`; the route stays unreachable otherwise. No device work.

## Files

**`:core:web`**
- `ImageGeometry.kt` (new, pure): `targetScale`, `sampleSizeFor`, `scaledSize`, `tileRects`, `TileRect`; constants `TILE_ASPECT = 2.2`, `MIN_SHORT_EDGE_PX = 1080`, `MAX_PIXELS = 12M`.
- `ContentResolverLocalImageImporter.kt`: real body. Two-pass decode (`inJustDecodeBounds` → `inSampleSize` → exact scale), `androidx.exifinterface` rotation/flip (all 8 orientations) in one `Matrix` pass, alpha flattened on white, JPEG q85 via `<file>.part` + rename, SHA-256 streamed over the written file. `tile()` decodes the source once and writes `Bitmap.createBitmap` crops.
- `LocalImageImporter.kt`: **signature change** — `tile(source, newFile: (index: Int) -> File, tallAspect, overlap)` replaces `intoDir: File`, so the caller registers every tile with its own bookkeeping (the use case hands out `TempPictureStore.newFile`). Nobody else called it (I0 stub).
- `build.gradle.kts` + `gradle/libs.versions.toml`: `androidx-exifinterface` 1.4.2 (was only transitive via Coil).
- Deviation worth knowing: the long-edge cap alone would shrink a 1080×20000 strip to ~130 px wide and make the tiles useless, so `targetScale` keeps the **short edge ≥ 1080** (plus a 12 MP decode bound) — a tall strip stays legible, a normal photo still goes down to 2400.

**`:feature:webclipper`** (package `com.llmlocal.feature.webclipper`)
- `domain/usecase/PrepareImagesUseCase.kt`, `DiscardPreparedImagesUseCase.kt`, `ObserveInputCapabilitiesUseCase.kt`: bodies per plan §3.2 (details below).
- `domain/usecase/ClipUseCase.kt`: input-generic pipeline (web / images / voice), V11, images branch, guardrail.
- `domain/ClipProgress.kt`: **additive** `ClipProgress.Retrying(imageCount)` (see guardrail).
- `presentation/ExtractionViewModel.kt`: input-aware (`checkDuplicate(sink, input)`, `ClipRequest(candidate, input, …)`), new ctor param `discardImages: DiscardPreparedImagesUseCase`, handles `Retrying`, `Cancel` discards an images session. DI updated.
- `presentation/WebClipperExtractionRoute.kt`: new trailing param `onPickDifferent: (() -> Unit)? = null`.
- `presentation/ui/WebClipperExtractionScreen.kt`: images branches (source strip, `phaseReadingImages`, hints, no-recipe empty state, `backToLibrary`, `imagesUnsupported*` + Open models); `phaseLabel(phase, labels, kind = WEB)`.
- `presentation/images/`: `ImagesClipViewModel` (full), `WebClipperImagesRoute` (full), `ImagesReviewScreen`, `ImageTile`, `ImagesPreviews.kt` (new).
- Tests (`src/test`): `PrepareImagesUseCaseTest`, `ObserveInputCapabilitiesUseCaseTest`, `ImagesClipViewModelTest`, `ClipUseCaseTest` (+14), `ExtractionViewModelTest` (+5), `fakes/TestSupport.kt` (`FakeLocalImageImporter`, `preparedImage`, `FakeClipSink` input-aware save, `FakeWebClipConfig.imagePolicy`); `core/web`: `ImageGeometryTest` (8).

## Public signatures (new / changed)

```kotlin
// :core:web
LocalImageImporter.tile(source: ImportedImage, newFile: (index: Int) -> File, tallAspect: Double, overlap: Double = 0.10): Outcome<List<ImportedImage>>
object ImageGeometry { targetScale(w,h,maxLongEdgePx): Double; sampleSizeFor(w,h,scale): Int; scaledSize(w,h,scale): Pair<Int,Int>; tileRects(w,h,tallAspect,overlap): List<TileRect> }

// :feature:webclipper
ClipProgress.Retrying(imageCount: Int)
fun <T : Any> WebClipperExtractionRoute(…, onShowSnackbar = null, onPickDifferent: (() -> Unit)? = null)
fun WebClipperExtractionScreen(…, modifier, onPickDifferent: (() -> Unit)? = null)
fun phaseLabel(phase: Phase, labels: WebClipperLabels, kind: ClipInputKind = ClipInputKind.WEB): String
ExtractionViewModel(candidateId, config, sink, labels, session, clip, checkDuplicate, cancelClip, discardImages, dispatchers)   // + discardImages
@Composable fun ImagesReviewScreen(state: ImagesClipState, labels: WebClipperLabels, onIntent: (ImagesClipIntent) -> Unit, modifier = Modifier, snackbarHostState: SnackbarHostState? = null)
val ClipImage.key: String   // file name; identity of a tile in state/intents
ImagesClipViewModel.SAVED_KEY = "images.prepared"
```
`ImagesClipViewModel`, `WebClipperImagesRoute`, `ImagesClip*` MVI types keep the I0 signatures.

## Behaviour

- **Prepare**: sequential on `io`, one bitmap at a time; failed URI → `skipped++`, doesn't use cap; tiling when `policy.tileImages && height > tallAspect × width` (tiles carry `tileOf = index of the picked URI`); cap after tiling (`capped`, surplus tiles deleted, extra URIs not even imported); every file comes from `TempPictureStore.newFile`, the untiled strip is deleted; cancellation deletes what was produced.
- **ClipUseCase**: `renderLlmInput(candidate, input, budget)`; images → bytes read on `io` → `llm.generate(…, images, policy.visualTokenBudget)`; pictures = the prepared files, proposed all-kept, `PictureChanged(Done, file)` each, Pictures phase Done, no downloader call; save via `sink.save(item, input, …)`. **V11** applies to every input: Reading Active from the start, flips Done + Extracting Active on the first `Streamed` delta (a pass that streams nothing flips right before settling; `ImagesUnsupported` leaves Reading Active so the screen marks Reading ✕). The existing web test that expected Extracting Active before the first token was updated; a web V11 assertion test was added.
- **Failure keeps the pictures**: on an emitted `Failed` for images the temp files and the session stay, so "Pick different pictures" / Retry work; cancel (collector cancelled, or `ExtractionViewModel.Cancel` → `DiscardPreparedImagesUseCase` + `session.clear()`) deletes them; a successful save deletes only the unticked ones.
- **Retry guardrail**: parser null / `accept == false` (not an engine error) **and** ≥ 2 images → once, with the first ⌈n/2⌉ images (3→2, 2→1). Emits `ClipProgress.Retrying(n)` — the ViewModel clears `streamedText` and sets `imageCount`; phases restart (Reading Active, Extracting Pending). I chose this small reset event over a silent retry because the first answer's text ("NO RECIPE") would otherwise be glued in front of the second stream and break the app's incremental draft parser. No new UI state or label. A second failure → `ParseFailed(second answer)`; one image is never retried; engine errors are never retried. All input pictures are still saved (the retry only changes what the model sees).
- **`UnsupportedImagesException`** (engine token error) → `ExtractionError.ImagesUnsupported` → screen copy `imagesUnsupportedTitle/Message` + "Open models" + back.
- **ImagesClipViewModel**: restore order: `SavedStateHandle` (`images.prepared`, encoded as a `ArrayList<String>` of `path|w|h|sha|tileOf` — plain strings, not JSON, so no `org.json` stub issue) → `ClipInput.Images` in the session (taken, `clearPage()`) → `pendingUris` → `LaunchPicker`. A pick that ends in exactly one picture (and the review was empty) auto-extracts when `availability == Available`; otherwise the review shows. `AddMore` → `LaunchPickerFor(remaining)`, new pictures appended under the remaining cap. `PickerCancelled` closes only when nothing is prepared. Close/pop discard unless the pictures were handed to the session. Notices: `Notify(SKIPPED/CAPPED)`, the route reads counts from state.
- **Route**: `PickMultipleVisualMedia(maxItems = max(remaining, 2))` + `PickVisualMedia` when one slot is left (the multi contract requires ≥ 2); `PickVisualMediaRequest.Builder().setOrderedSelection(true)` is available on activity 1.13. `BackHandler` → `Close`.
- **Review screen**: `LazyVerticalGrid(Adaptive(148.dp))` (≈ 2 / 3 / 4 columns at 360 / 600 / 840 dp), `Scaffold(contentWindowInsets = safeDrawing)`, grid `contentPadding = innerPadding + 16dp`, "Extract recipe" in `bottomBar` (horizontal + bottom safeDrawing), info rows and button capped at 720 dp, numbered tiles (`NumberBadge`), selected outline animated 150 ms, ✕ 48 dp, arrow row (Move left/right) for the selected tile, `customActions` Move left / Move right / Remove on every tile, one polite live region after a move, dashed Add tile hidden at the cap, counter turns `error` colour when capped, `animateItem()`, skeleton tiles `invisibleToUser()` while preparing. Gate states: `NoModel` → `GradientHero` + disabled button; `ModelLacksVision` → `EmptyState` + "Open models"; nothing readable → `EmptyState(BrokenImage)` + "Pick different pictures" (re-opens the picker).
- **Extraction screen**: hint under whichever of Reading/Extracting is Active (images: `imagesExtractingHint` + elapsed, plus `imagesPrivacyHint` for a single picture; web keeps `extractingHint`).

## Label keys consumed (all already on `WebClipperLabels` from I0 — no new keys, no string edits)
`imagesTitle, imagesPreparing, imagesOrderTitle, imagesOrderHint, imagesCount, imageTileDescription, imageSelected, moveImageLeft, moveImageRight, removeImage, addImages, imagesPrivacyHint, imagesTimeHint, extractFromImages, imagesCapped, imagesSkipped, imagesNoneReadableTitle/Message, pickDifferentImages, imagesSourceStrip, phaseReadingImages, imagesExtractingHint, imagesNoRecipeTitle/Message, imagesUnsupportedTitle/Message, backToLibrary`; reused: `cancel` (review Close icon), `openModels`, `modelNotReadySnackbar` (+ `fabChooseModel`) for the no-model hero (the library's `libraryNoModelTitle`/`chooseModelAction` are app labels, not reachable from the feature). `clipAlreadyRunning` is I3's.

## Tests / builds
`./gradlew :core:web:testDebugUnitTest :feature:webclipper:testDebugUnitTest :app:recipekeeper:testDebugUnitTest :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug` → BUILD SUCCESSFUL. Unit tests: webclipper **117** (was 67; +50), core:web **46** (+8), recipekeeper 261 — 0 failures. Previews are in the main source set (compiled by `assembleDebug`): preparing, review 2, review 5 (cap 5), capped, no model, text-only model, nothing readable, wide 840 dp, dark. Boundary: `:feature:webclipper releaseRuntimeClasspath` has no `project :app` edge; `:core:web` only gained `androidx.exifinterface`. Decode/EXIF (`ContentResolverLocalImageImporter`) is covered by geometry tests only; the decode path is unverified on a device.

## What the lead must wire at merge
1. **Sink** (I3): implement `ClipSink.save(item, input: ClipInput, …)` for `ClipInput.Images` and `existingId(input) == null` (the default `save` throws for images; `ClipUseCase` calls the input-aware overload for every input).
2. **`onPickDifferent`** in `RecipeKeeperNavHost`: pass it to `WebClipperExtractionRoute` — `navController.navigate(Route.ClipImages.pattern) { popUpTo(Route.Extract.pattern) { inclusive = true } }`. Without it the no-recipe failure only shows "Back to library" (the images ViewModel takes the pictures back from `ClipSession`). Extraction `onCancelled` must pop to the library (images). Also pass `onShowSnackbar`/`snackbarHostState` to `WebClipperImagesRoute` (I0 already does).
3. Koin: `ExtractionViewModel` now needs `DiscardPreparedImagesUseCase` — already registered by I0 and wired in `webClipperModule`; nothing for apps to add.
4. Flip `IMAGES_SOURCE` / the manifest placeholder together (unchanged from I0 notes). I1's `LiteRtLlmEngine` must advertise `VISION` on the catalog descriptor for `ObserveInputCapabilitiesUseCase` to report `Available`.
5. Merge notes: `FakeLlmEngine` in the webclipper test tree has no vision flag (the images path works against it because `EngineLlmGenerationSource` does not gate on `capabilities()` — the engine rejects with `UnsupportedImagesException`, tested through a scripted failure). I did not touch `:core:llm` / `:core:domain`. `EvalImagesParserTest` untouched (I3/I4).
