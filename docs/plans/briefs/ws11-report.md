# WS11 report — fixes from the end-to-end clip test

Worktree branch `recipekeeper/ws11`, based on `recipekeeper/main` at `3ccff9f` (WS9 merged, WS10 report in progress). One commit per bug, in the brief's order, plus the lead's added item 13 and the cancel bug that the finished §Scenario 5 of the main-checkout WS10 report surfaced. No device work (emulators were in use); unit tests, previews and builds only.

**Bug numbering.** The brief and my commit messages use the numbering of the WS10 report *as committed in this worktree* (`3ccff9f`). The main checkout's newer copy (`d26b104`) inserted a new bug 4 ("snackbars never show") and shifted every later bug by one. The table below gives both.

| Brief / commit # | Main-checkout # | Bug | Commit |
|---|---|---|---|
| 1 | 1 | Stale candidates after save | `bcc7aa1` |
| 2 | 2 | Whole-page skips the sheet | `f791bd8` |
| 3 | 3 | Ingredient-less JSON-LD stubs | `5c8fe4e` |
| 4 | 5 | Foreign pictures pre-selected | `594bb07` |
| 5 | 6 | Duplicate "Show raw output" | `90d1a34` |
| 7 | 8 | Pictures-unavailable not surfaced | `449f161` |
| 8 | 9 | Stop below the fold | `67cb359` |
| 9 | 10 | Pictures ring spins for the whole run | `11c56e7` |
| 10 | 11 | Invisible skeleton lines | `06c1dee` |
| 11 | 12 | Snackbar covers the library FAB | `2f90bfb` |
| 6 (item 13) | 7 | Duplicate recipes → warning dialog | `e881c10` |
| — (§Scenario 5) | 4 | "Clip cancelled" / "Saved" snackbars never show | `66bbc7e` |
| 12 | 13 | Memory growth | **untouched** (observation) |

Decision A18 (back from a saved recipe returns to the browser page) — no change, as instructed.

## Per bug

### 1. Stale candidates after save (P1) — `bcc7aa1`
**Fix.** `BrowserViewModel.onScan` routes from the cached `state.candidates` only while `session.current.value?.snapshot != null`; otherwise it runs a full scan. The ViewModel also collects `session.current` and, when it turns `null` (a save's `ClipSession.clear()`, or a navigation's `clearPage()`), empties `candidates`, closes any sheet, resets the proactive quick-scan count and recomputes the FAB, so the button drops back to "Clip recipe" after a save. `ExtractionViewModel` sends `ShowSnackbar(labels.scanFailed)` before `PopToBrowser` whenever it has no candidate (init) or no snapshot (`start()` after the page was dropped), so a silent pop cannot recur.
**Files.** `feature/webclipper/.../BrowserViewModel.kt`, `ExtractionViewModel.kt`.
**Tests.** `BrowserViewModelTest`: "a save clears the cached candidates and the next tap re-scans the page" (2 captures, second `NavigateToExtraction`), "a cleared session also drops the proactive quick-scan badge". `ExtractionViewModelTest`: "empty session explains itself and pops straight back", "retry after the page was dropped from the session pops with the snackbar".

### 2. Whole-page skips the "No recipe found" sheet (P1) — `f791bd8`
**Fix.** `RecipeKeeperClipConfig.detect()` returns `emptyList()` when neither tier matches; the private `wholePageCandidate`, `WHOLE_PAGE_ID` and `MIN_WHOLE_PAGE_CHARS` are gone. The feature's "Extract anyway" / "Try the whole page" paths already build the synthetic candidate with `wholePageCandidate(snapshot)`; `renderLlmInput` / `accept` still handle `CandidatePayload.WholePage`.
**Eval.** `list_pinchofyum_recipes` now expects `ExpectedTier.NONE`, `minCandidates = 0`. `EvalFixtures.topCandidate(snapshot)` is non-null: the top detected candidate or, exactly like tapping "Extract anyway", `wholePageCandidate(snapshot)` — so the whole-page prompt is still written to `build/eval/prompts/` and the committed golden `eval/outputs/list_pinchofyum_recipes.txt` stays valid (its row reports tier `WHOLE_PAGE`, status "INFO (model answered NO RECIPE, as expected)"). `EvalReport.Row` and `EvalParserTest` use the same fallback.
**Files.** `app/recipekeeper/.../clip/RecipeKeeperClipConfig.kt`; tests `RecipeKeeperClipConfigTest`, `eval/EvalFixtures.kt`, `EvalPromptRenderingTest.kt`, `EvalParserTest.kt`.
**Tests.** "a page with no recipe yields nothing, however long its readable text"; the three whole-page rendering / accept tests now build the candidate with `wholePageCandidate`. All 10 eval fixtures pass detection + rendering; goldens still parse.

### 3. Ingredient-less JSON-LD stubs become garbage recipes (P1) — `5c8fe4e`
**Fix.** `detectStructured()` drops nodes where `mapper.ingredients(raw)` **and** `mapper.instructions(raw)` are both empty (collection-card stubs) before de-duplication. `accept()` now has a floor for detected candidates: ≥ 2 quantity-bearing ingredient lines **or** ≥ `MIN_COMPLETE_STEPS` (2) steps; whole-page keeps its stricter quantity-only rule (steps alone are easy to invent). `MIN_WHOLE_PAGE_QUANTITY_LINES` is renamed `MIN_ACCEPT_QUANTITY_LINES` (still 2). A rejected STRUCTURED result falls back to `SchemaOrgRecipeMapper` as before, which is also empty for a stub → parse-failed screen, which is right.
**Files.** `RecipeKeeperClipConfig.kt`; new fixture `app/recipekeeper/src/test/resources/jsonld/collection_card.json` (name / description / image / cookTime only).
**Tests.** "collection-page Recipe stubs without ingredients or instructions are not candidates" (three cards → `detect` empty; cards + a real node → only the real one, id `structured-0`), "accept needs two quantity lines or two steps for detected candidates" (the WS10 "Unstuffed Peppers" row is rejected for TEXT and STRUCTURED), "accept requires two quantity ingredients for whole-page results". Eval: no fixture lost a candidate (Guardian still 5).

### 4. Foreign pictures pre-selected on round-ups (P2) — `594bb07`
**Contract change (additive).** `WebClipConfig.rankPictures(candidate, snapshot, max): List<PicturePick>` with `data class PicturePick(url, preferred = true)`; its default wraps the existing `pickPictures` with every pick preferred, so `FakeWebClipConfig` and any other implementor compile unchanged. `ExtractionViewModel.startPictures` now calls `rankPictures`, de-duplicates by URL and pre-checks only the first `DEFAULT_KEEP` (3) *preferred* picks; everything else starts unchecked.
**Recipe Keeper ranking.** STRUCTURED: the node's own `image`s are preferred; `og:image` / `twitter:image` are preferred only when the page holds a single recipe node (then it is that recipe's hero); page images are offered unchecked; on a round-up (> 1 recipe node, computed with `detectStructured`) the og / page pool is skipped entirely. TEXT / whole-page keep the previous behaviour (everything preferred) since there is no structured ownership to go by. `pickPictures` is now `rankPictures(...).map { it.url }`, so the old tests still hold.
**Files.** `feature/webclipper/.../WebClipConfig.kt`, `ExtractionViewModel.kt`, `mvi/ExtractionState.kt` (KDoc), `RecipeKeeperClipConfig.kt`, fakes.
**Tests.** Feature: "only preferred picks start checked, at most three, non-preferred stay unchecked", "a config without preferred information keeps the first three checked". App: preferred flags on the existing ranking test, "rankPictures on a round-up page offers only the node's own images", TEXT all-preferred assertion.

### 5. Duplicate "Show raw output" (P2) — `90d1a34`
**Fix.** `ClipDraftCard` loses its own toggle, raw block and the `clipperLabels` parameter; the feature's `RawOutput` item (driven by `ExtractionIntent.ToggleRaw`, reused by the failure state) is the only one. Callers (`MainActivity`, previews) never passed `clipperLabels`, so nothing else changed.

### 7. Pictures-unavailable not surfaced (P2) — `449f161`
**Contract change (additive).** `ClipSink.save(item, snapshot, candidate, pictures, truncated, options: ClipSaveOptions)` with a default forwarding to the five-argument `save`; `ClipSaveOptions(pictureCandidates, picturesFailed)` (+ `replaceId` from item 13). `ExtractionViewModel.finish` always calls the long form with every picked URL and `picturesFailed = anyPictureFailed && no picture reached Done`. That is a strict subset of the brief's "failed and none kept": a user who unticks every picture is not told their pictures failed; S7 (all downloads fail offline) sets it.
**Persistence.** `ClippedRecipe.picturesFailed: Boolean = false`, `pictureCandidates: List<String> = emptyList()`; `RecipeEntity` columns `picturesFailed` (INTEGER, default 0) and `pictureCandidates` (JSON text via the existing `List<String>` converter, default `[]`). **Room stays at version 1 and `schemas/.../1.json` was regenerated** (identity hash `b271d49c…` → see file; this commit, then again in `e881c10` for the `sourceUrl` index) — the database has never shipped, as the brief states. **Consequence for the device pass:** any install that already holds a WS10-era `recipekeeper.db` must be cleared first (`adb shell pm clear com.llmlocal.recipekeeper` or uninstall), otherwise Room throws "Room cannot verify the data integrity" on first open. `RecipeKeeperClipSink` stores both fields (`picturesFailed` only when no file was actually moved in). New `RecipeRepository.replacePictures(id, paths, picturesFailed)` (DAO transaction: delete + insert picture rows, update the flag; no-op for an unknown id).
**Detail screen.** `MetaChips` renders an `AssistChip(picturesUnavailable, BrokenImage)` plus a `TextButton(retryPictures)` (with a 18 dp progress ring while retrying) when `recipe.picturesFailed`; both dispatch `RecipeDetailIntent.RetryPictures`. `RecipeDetailViewModel` downloads the first `RETRY_PICTURES` (3) of `pictureCandidates` concurrently with the clipper's `PictureDownloader` (same `MAX_LONG_EDGE_PX = 1_280`, referer = source URL) into the clipper's cache dir, `RecipePictureStore.moveIn`s them, calls `replacePictures`, and emits `RecipeDetailEffect.PicturesRetryFailed` when nothing came through (route → `labels.retryPicturesFailed` snackbar). `RecipeDetailState.retryingPictures` disables the controls meanwhile.
**Files outside my list (minimal, additive, needed by the above):** `app/recipekeeper/.../config/RecipeKeeperLibraryLabels.kt` (+ `picturesUnavailable`, `retryPictures`, `retryPicturesFailed`), `config/RecipeKeeperUiModule.kt` (three new constructor args for `RecipeDetailViewModel`: `pictureDownloader = get()`, `pictureStore = get()`, `tempDir = get(named(WEBCLIPPER_TEMP_DIR_QUALIFIER))`), `previews/SampleData.kt` + `previews/RecipeDetailPreviews.kt` (two new previews: "pictures unavailable", "retrying pictures · dark"), `feature/webclipper/.../ClipSink.kt`.
**Tests.** Feature: "when every download fails the save is flagged picturesFailed with the picks", "no proposed pictures is not a failure", partial-failure assertion on the existing picture test. App: `RecipeDetailViewModelTest` "retry pictures downloads the first three candidates into the recipe folder and clears the flag" (001–003.jpg under `recipes/<id>/`, temp dir emptied, state updated), "retry pictures that fails again keeps the flag and reports it", "retry pictures without stored candidates only reports failure"; `RecipeKeeperPipelineTest` "sink records failed pictures and the candidate urls for a later retry". New test fake `testing/FakePictureDownloader.kt`.

### 8. Stop below the fold (P2) — `67cb359`
**Fix.** `WebClipperExtractionScreen` wraps its `Scaffold` in `BoxWithConstraints`; on compact windows the action (Stop while running, "View recipe" once saved) is rendered in `bottomBar` as a tonal `Surface` that pads the navigation bar itself, and the "action" item is removed from the compact `LazyColumn`. Wide windows keep it under the phases in the left column.
**Not unit-testable;** preview "Extraction — running" shows it. Device pass: Stop must be visible on a 1080×2400 phone without scrolling, and the list must still scroll to its last item above the bar.

### 9. Pictures ring spins for the whole run (P3) — `11c56e7`
**Fix.** `startPictures()` sets `Phase.Pictures` to Done / Warning right after `awaitAll()` (and Done when nothing was proposed); `finish()` only reads the deferred result.
**Test.** "pictures phase settles as soon as the downloads finish, while the model is still running" (slow fake engine, Pictures = Warning at +200 ms with Extracting still Active).

### 10. Invisible skeleton lines (P3) — `06c1dee`
**Fix.** `SkeletonLine` paints `onSurface.copy(alpha)` with the alpha pulsing 0.07 → 0.14 (nominal 10 %) over whatever the parent draws, instead of a fixed `surfaceVariant` fill behind a `Modifier.alpha` pulse. Same contrast on a plain surface, a tonal-elevation-2 card, or `surfaceContainerHigh` (the picture tiles).
**Other brands.** `RecipePrimitivePreviews` / `MedicalPrimitivePreviews` (`:app:recipes`, `:app:medical`) use the same component on a plain surface; both apps compile and their previews only differ in the `onSurface` hue. I could not render previews without a device/IDE — the device pass should glance at the "SkeletonLine + StreamingCursor" preview of each brand in Android Studio.

### 11. Snackbar covers the library FAB (P3) — `2f90bfb`
**Root cause** differs slightly from the report: the library's `AppScaffold` already owned a `SnackbarHost`; the overlap happened because the FAB was overlaid on the content by `LibraryScreen` instead of living in the scaffold's FAB slot, so Material's `Scaffold` could not place the snackbar above it.
**Fix / contract change (additive).** `AppScaffold` gains `floatingActionButton: @Composable () -> Unit = {}` and forwards it. New public `LibraryFab(state, labels, onIntent)` in `library/LibraryScreen.kt`; `LibraryRoute` passes it to the slot; `LibraryScreen` is now just the column (search field + grid). `LibraryPreviews` wrap the screen in the same scaffold.

### Item 13 — duplicate warning before re-clipping (bug 6 / main-checkout bug 7) — `e881c10`
**Contract changes (additive).** `ClipSink.existingId(snapshot): String? = null`; `ClipSaveOptions.replaceId: String? = null`. `WebClipperLabels` + `duplicateTitle` ("Already in your library"), `duplicateMessage` ("You clipped this page before. Replace the saved recipe with a fresh clip?"), `replace`, `keepBoth`, `cancel`. `ExtractionIntent.ReplaceExisting`, `ExtractionIntent.KeepBoth`; `ExtractionState.duplicateOf`, `ExtractionState.replaceId`.
**Flow.** `ExtractionViewModel.init` → `checkDuplicateThenStart()`: asks `sink.existingId(snapshot)` on `dispatchers.io` **before anything runs** (no `renderLlmInput`, no engine init, no pictures, `activeExtraction` unset). A hit sets `duplicateOf`; `WebClipperExtractionScreen` shows an `AlertDialog` (title / message / Keep both + Replace as confirm row, Cancel as dismiss; dismissing the dialog = Cancel). Replace → `replaceId = existing`, `start()`; Keep both → `start()`; Cancel → the existing `ExtractionIntent.Cancel` (pops, no "Clip cancelled" snackbar since nothing ran). A sink that throws counts as "no duplicate". `finish()` passes `replaceId` in `ClipSaveOptions`.
**Recipe Keeper.** `RecipeRepository.findIdBySourceUrl(url)` → `RecipeDao.findIdBySourceUrl` (`sourceUrl = :url AND deletedAt IS NULL ORDER BY clippedAt DESC LIMIT 1`), new index on `sourceUrl` (schema regenerated, still v1). `RecipeKeeperClipSink.existingId` delegates to it; `save` uses `options.replaceId` as the id when present and wipes the old picture folder (`RecipePictureStore.deleteAll`) before moving the new files in, so `upsertWithPictures` replaces the row and its pictures under the same id (checked ingredients reset, `clippedAt` fresh). Exact-URL match only: query strings / fragments count as different pages — flag if that proves too strict.
**Tests.** Feature: "an already clipped page shows the duplicate dialog before anything runs", "Replace runs the extraction and saves under the existing id", "Keep both runs the extraction as a new item", "Cancel on the duplicate dialog pops without running or a cancelled snackbar", "a failing duplicate check is treated as no duplicate", plus "no existing → no dialog" assertions on the happy path. App: `RecipeKeeperPipelineTest` "sink knows a page it saved before and Replace reuses the id and wipes the old pictures" (also covers Keep both → second id). Preview "Extraction — duplicate warning, light".

### §Scenario 5 follow-up — extraction snackbars never show (main-checkout bug 4) — `66bbc7e`
**Fix.** `MainActivity` now passes the app-wide `snackbarHostState` to `WebClipperExtractionRoute` (it already did for browser, library and detail). The route also **launches** `showSnackbar` instead of awaiting it inside the effect collector — `showSnackbar` suspends until dismissal, so with the host wired the `PopToBrowser` / `NavigateToSaved` effect queued right behind it would otherwise have waited ~4 s. `WebClipperExtractionRoute.kt` is outside my file list; the change is 6 lines.
**Device pass:** "Clip cancelled" after X / Stop / system back (shows on the browser), "Saved to your recipes" after a clip (shows on the detail); the pop must be immediate.

## Out of scope, untouched
- **Bug 12 (main 13) — memory growth** (~0.5 GB native over six extractions): observation only; still worth a `dumpsys meminfo` series on a 6 GB phone.
- **Decision A18** (back from detail → browser page): kept.

## Shared-contract changes (all additive, old signatures still compile)
| Where | Change |
|---|---|
| `feature/webclipper/WebClipConfig.kt` | `data class PicturePick(url, preferred = true)`; `fun rankPictures(candidate, snapshot, max): List<PicturePick>` with a default over `pickPictures` |
| `feature/webclipper/ClipSink.kt` | `save(..., options: ClipSaveOptions)` overload (default → old `save`); `existingId(snapshot): String? = null`; `data class ClipSaveOptions(replaceId, pictureCandidates, picturesFailed)` |
| `feature/webclipper/WebClipperLabels.kt` | + `duplicateTitle`, `duplicateMessage`, `replace`, `keepBoth`, `cancel` |
| `feature/webclipper/mvi/*` | + `ExtractionIntent.ReplaceExisting`, `KeepBoth`; + `ExtractionState.duplicateOf`, `replaceId` |
| `feature/webclipper/WebClipperExtractionRoute.kt` | snackbar launched, not awaited (not in my list; 6 lines) |
| `core/designsystem/.../AppScaffold.kt` | + `floatingActionButton` slot (not in my list; default empty) |
| `app/recipekeeper/domain/RecipeRepository.kt` | + `replacePictures(id, pictures, picturesFailed)`, + `findIdBySourceUrl(url)` |
| `app/recipekeeper/domain/ClippedRecipe.kt` | + `picturesFailed`, `pictureCandidates` (defaults) |
| `app/recipekeeper/config/RecipeKeeperLibraryLabels.kt` | + `picturesUnavailable`, `retryPictures`, `retryPicturesFailed` (not in my list) |
| `app/recipekeeper/config/RecipeKeeperUiModule.kt` | `RecipeDetailViewModel` gets `pictureDownloader`, `pictureStore`, `tempDir` (not in my list) |
| `app/recipekeeper/schemas/.../1.json` | regenerated twice, still version 1 (two columns, one index) |
| `RecipeKeeperClipConfig` | `MIN_WHOLE_PAGE_QUANTITY_LINES` → `MIN_ACCEPT_QUANTITY_LINES`; `WHOLE_PAGE_ID`, `MIN_WHOLE_PAGE_CHARS` removed |

`FakeWebClipConfig` / `FakeClipSink` (feature tests) and `FakeRecipeRepository` (app tests) were extended accordingly; `ClipDraftCard` lost its `clipperLabels` parameter; `LibraryScreen` lost the FAB (now `LibraryFab`).

## Verification
Final run on `66bbc7e` (all commits in):

```
./gradlew :feature:webclipper:testDebugUnitTest :app:recipekeeper:testDebugUnitTest \
          :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug lint
BUILD SUCCESSFUL
```

| Check | Result |
|---|---|
| `:feature:webclipper:testDebugUnitTest` | 49 tests, 0 failed (was 39) |
| `:app:recipekeeper:testDebugUnitTest` | 129 tests, 0 failed (was 121; eval report regenerated under `build/eval/`) |
| `:app:recipes` / `:app:medical` / `:app:recipekeeper` `assembleDebug` | all three build (per-ABI + universal APKs) |
| `lint` | passes; only the pre-existing `Deprecated 'org.jetbrains.kotlin.android' plugin usage` warnings |
| `:feature:webclipper:dependencies --configuration releaseRuntimeClasspath` | no `:feature → :app` / `:feature → :feature` edge (no project dependency added) |

Not run: anything on a device or emulator; Compose previews were compiled, not rendered.

## What the next device pass must re-check
1. **Clear app data first** (`adb shell pm clear com.llmlocal.recipekeeper`) — the v1 schema changed in place; an old database makes Room crash on open.
2. Bug 1: save → detail → back → tap "Recipe found": the button must read "Clip recipe" after the save and the tap must scan and open the extraction; a stale tap must at least show "Couldn't read this page".
3. Bug 2: bbc.com home / a category page → "No recipe found on this page" sheet; "Extract anyway" still runs the whole-page extraction.
4. Bug 3: `tasteofhome.com/collection/recipes-with-ground-beef/` → sheet (no stub candidates); a real recipe page still extracts.
5. Bug 4/5: Guardian round-up → only the chosen recipe's pictures are checked, no foreign thumbnails offered; BBC single recipe → node images + og checked, related-card images offered unchecked.
6. Bug 7/8: airplane-mode clip → detail shows "Pictures unavailable" + "Retry pictures"; airplane off → Retry downloads up to 3 pictures, hero appears, chip disappears; Retry while still offline → snackbar "Still couldn't download the pictures…".
7. Bug 8/9: Stop visible in the bottom bar during extraction on a phone; "View recipe" replaces it once saved; the last list item is not hidden under the bar.
8. Bug 9/10: "Saving pictures" ticks within seconds while "Extracting recipe" is still spinning.
9. Bug 10/11: skeleton lines visible on the draft card before the first token, light and dark, and in the recipes / medical primitive previews.
10. Bug 11/12: delete from the library → the "Recipe deleted / Undo" snackbar sits above the FAB.
11. Item 13: re-clip an already saved URL → dialog before any model activity (CPU idle, no engine log); Replace keeps one tile with fresh pictures and the same id; Keep both → two tiles; Cancel → back to the page, no snackbar.
12. §Scenario 5: "Clip cancelled" shows on the browser after X / Stop / back; "Saved to your recipes" shows on the detail; the pop is immediate.
13. Cancel deep into decoding on a loaded host remains unverified (WS10 could not provoke it either).
