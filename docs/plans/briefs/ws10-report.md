# WS10 report — end-to-end clip test with the real model (android-ux persona)

Main checkout, branch `recipekeeper/main` at `762a9fb` (WS9 merged). No code edited, no `git` write commands. Device **`emulator-5556`** (AVD `LlmPhone_API_36`, 8 GB, arm64, API 36), app pid 4534 for the whole session, model `gemma-4-E2B-it.litertlm` (2,588,147,712 B) already in `files/llm/`, debug build → **CPU backend**. Build `./gradlew :app:recipekeeper:assembleDebug` (22:46), `adb install -r` of the 47.4 MB arm64 split (22:47), no `pm clear`.

`QA2` below = `/private/tmp/claude-501/-Users-oriol-Documents-development-projectes-AndroidLocalLLM/6d100e70-7137-46b6-ad09-7c1e8dd3fa5f/scratchpad/qa2/` (screenshots `NN-*.png`, `meminfo.txt`, `logcat.txt`, `logcat-final.txt`). The Room DB was copied out with `run-as cat` to inspect the saved rows (`select … from recipes`), nothing was written to it.

## Two caveats that shape every timing below

1. **The device was shared.** At 22:48 (one minute after my install) and again at ~09:25 someone else drove the emulator window (`MotionEvent` bursts from the touch device, not adb; `PK` physical-keyboard IME mode): they searched DuckDuckGo, clipped a Taste of Home collection page (`QA2/00-foreign-detail.png`), and later opened Models. I kept my own actions scripted and re-checked the screen before each step; the foreign clip is reported as finding 3 because it exposed a real detection bug.
2. **Inference speed varied 30×.** The identical BBC "Easy pancakes" prompt (986 conversation tokens, same output) took **299 s** at 22:53 and **10 s** at 09:27 / 09:29 / 09:33. Last night the host (M4, 64 GB) was running two emulators, two Gradle daemons and other agent panes; this morning it was idle. Guest `top` during the slow run showed the inference thread at only ~67 % of one vCPU with 279 % idle, i.e. the hypervisor was not giving the guest its cores. Treat the night figures as "worst case on a loaded host" and the morning ones as the emulator's real CPU speed; neither is a phone number. The host also suspended twice during background polling (00:58→05:51 and 02:50→09:20 gaps), which is why scenario 4 has no usable duration.

## Scenario results

| # | Scenario | Result | Timing (tap → first draft → done → detail) | Screens |
|---|---|---|---|---|
| 1 | JSON-LD page → single recipe (bbcgoodfood.com/recipes/easy-pancakes) | **PASS** | FAB `Recipe found` ≤ 24 s after Enter (page still loading images); tap 22:53:15 → draft (title, description, 3 chips, 6 ingredients, step 1) visible by +17 s → `Generation done` +299 s → detail +303 s | `04-s1-fab-found`, `05-s1-extracting-phases`, `05b/05c-s1-extracting-*`, `06-s1-detail`, `07-s1-library-after` |
| 2 | Round-up → candidate sheet (Guardian, Nigel Slater) | **PASS** | FAB `5 recipes found` +9 s after Enter; sheet; pick #4 "Fig, maple syrup and dark chocolate ice-cream" 23:01:55 → draft by +73 s (first-draft shot at +3 s still skeleton) → done +234 s → detail ≈ +235 s | `08-s2-fab-found`, `09-s2-candidate-sheet`, `12-s2-first-draft`, `12-s2-mid`, `13-s2-detail`, `13b-s2-detail-pictures` |
| 3 | Plain-text page (joyofbaking.com → m.joyofbaking.com) | **PASS**, 2.5× slower than #1 | FAB stays `Clip recipe` (no JSON-LD → no proactive badge); tap 23:08:59 → readability scan → extraction in < 1.5 s (one TEXT candidate) → draft by +42 s → done +742 s (1,232 tokens: the readable-text prompt is longer) → detail +746 s. 8 ingredients, 10 steps, "3-4 people", Prep 15 / Cook 3 / 18 min total (parser sums prep+cook, documented), **0 pictures** → gradient hero | `14-s3-fab`, `15-s3-after-tap`, `16-s3-extracting`, `17-s3-detail` |
| 4 | Non-recipe page (bbc.com home) | **FAIL (bug 2)** then correct failure UI | FAB `Clip recipe` → `Scanning page…` → **straight to "Clipping recipe"**, the "No recipe found on this page" sheet never showed. The whole-page run ended in the parse-failed state with the right copy and actions; raw output = `NO RECIPE` (2,131 tokens). Duration unusable (host suspended) | `18-s4-page`, `19-s4-sheet` (shows Scanning), `20-s4-failed`, `21-s4-raw-output`, `21b-s4-back-to-page` |
| 5 | Cancel mid-extraction | see §Scenario 5 | — | `22-*`, `23-*` |
| 6 | "Not this one?" from a round-up extraction | **PASS** | header `theguardian.com · <page title>` + `Not this one?`; tap → sheet reopened with the same 5 candidates, CPU 0 %, `cache/webclipper` empty | `10-s6-extraction-header`, `11-s6-back-to-sheet` |
| 7 | Pictures offline (airplane mode, page already loaded) | **PASS with gap (bug 7)** | airplane on 09:33:16 (FAB still `Recipe found`), tap 09:33:22 → done +10 s → detail +10 s; recipe saved with **0 pictures**, gradient hero, airplane icon in status bar; **no "Pictures unavailable" chip and no "Retry pictures"**; airplane off 09:33:34 (verified `disabled`) | `24a-s7-airplane-fab`, `24b-s7-plus1s`, `25-s7-detail-offline` |
| 8 | Library: grid, search, delete + undo, share | **PASS** (one P3) | grid 7 tiles (4 duplicates of Easy pancakes, see bug 6); search "custard" → only the fig tile; "zzqx" → `Nothing matches “zzqx”` + Clear search; long-press → Open original / Share recipe / Delete; Delete → tile hidden + `Recipe deleted` / Undo; Undo → tile back, 7 folders intact; Share → system chooser "Sharing text" with `<title>\n<url>` | `26-s8-library-grid`, `27-s8-search-custard`, `27b-s8-search-empty`, `28-s8-tile-menu`, `29-s8-deleted-snackbar`, `30-s8-undone`, `31-s8-share-sheet` |
| 9 | Model management | **PASS** | "Available models · 4 of 4 models · 1 installed"; Gemma 4 E2B IT card highlighted with the **★ Active** pill (WS9 rule: selected ∧ installed); the other three show Download | `32-s9-models`, `32b-s9-models-scrolled` |
| 10 | Memory | **no OOM** | PSS 1,818,226 KB after 1 extraction → 2,657,467 KB after 6 (RSS 2.85 GB); native heap 1,145,628 → 1,662,936 KB; Dalvik 25 MB; device free RAM 3.2 GB, `status normal`; 0 `lmkd`/`lowmemorykiller` lines, no `FATAL`/`AndroidRuntime` crash across the session | `QA2/meminfo.txt` |

### Scenario 5 — cancel

SCENARIO5_PLACEHOLDER

### Other things seen along the way

- **Streaming UX works as designed** once tokens arrive: title → description → chips → ingredients → steps fill in, the dimmed tail + cursor shows the unparsed line (`16-s3-extracting.png`), elapsed seconds tick under the Extracting phase, `Keep the app open until it finishes` is shown, the picture strip appears within ~2 s with the first three checked. The milestone live region could not be heard (no TalkBack on the emulator).
- **First token** is not instantaneous even on fast runs: at +7 s the card is still skeleton (`22-s5-before-stop.png`), the whole recipe then lands between +8 s and +10 s. Prefill of a ~500-token prompt costs those seconds.
- **Jank while streaming**: `HWUI Davey!` frames of 700–1,680 ms (`DequeueBufferDuration` up to 800 ms) and the main thread at ~55 % CPU during the slow night runs. The emulator's GPU path is the prime suspect (WS6 saw the same on page load). Re-measure on a phone with `android-profiler` before touching code.
- **Back from detail** goes to the browser (the page you clipped), not the library; the library is one more Back. The brief expected library; the UX spec is silent. Product call.
- The engine initialised in ~2.4 s after Koin start (`Engine ready (model=2468 MB, backend=CPU, maxNumTokens=4096)` at 22:48:46) and never re-initialised.

## Bugs, ranked

### 1. (P1) `Recipe found` button does nothing after you come back from a saved recipe
**Seen** twice (S7 attempt 1 at 09:29:52, S5 attempt at 09:33:42): detail → top-bar back → browser still shows `Recipe found`; tap → nothing (no screen change, no snackbar, no engine log). From the library → browser path the same tap works. 
**Cause.** `ExtractionViewModel.finish()` calls `session.clear()` (`feature/webclipper/.../ExtractionViewModel.kt:278`) but `BrowserViewModel` keeps its cached `state.candidates`; `onScan()` routes from that cache (`BrowserViewModel.kt:271`), the new `ExtractionViewModel` finds no candidate in the empty session and pops silently (`ExtractionViewModel.kt:118-119`). 
**Fix.** In `onScan()` use the cache only when `session.current.value?.snapshot != null`, otherwise `runFullScan { routeCandidates(it) }`; additionally have `BrowserViewModel` collect `session.current` and clear `candidates` (and `recomputeFab()`) when it turns null, so the badge drops to `Clip recipe` after a save. The `candidate == null` branch in `ExtractionViewModel.init` should also `send(ShowSnackbar(labels.scanFailed))` before `PopToBrowser` so a silent pop can never recur.

### 2. (P1) Non-recipe pages skip the "No recipe found on this page" sheet
**Seen** on bbc.com (S4): FAB → `Scanning page…` → straight into a 9,000-char whole-page extraction; "Extract anyway" / "Keep browsing" never offered. 
**Cause.** `RecipeKeeperClipConfig.detect()` returns a `WholePage` candidate whenever readable text ≥ 500 chars (`app/recipekeeper/.../clip/RecipeKeeperClipConfig.kt:69`, `wholePageCandidate` :171-185), and `BrowserViewModel.routeCandidates()` treats `size == 1` as "navigate" (`BrowserViewModel.kt:304-312`). The sheet is only reachable for pages with < 500 chars. 
**Fix.** Either `detect()` returns `emptyList()` when neither tier matches (the feature already builds the whole-page candidate itself in `extractWholePage()` via `wholePageCandidate(snapshot)`), or `routeCandidates()` maps "all candidates are `CandidatePayload.WholePage`" to `BrowserSheet.NothingDetected`. The first is smaller; update `RecipeKeeperClipConfigTest` (`long readable text yields WholePage`, line 117) accordingly.

### 3. (P1) List/collection pages with ingredient-less JSON-LD `Recipe` nodes produce garbage recipes
**Seen** in the foreign clip of `tasteofhome.com/collection/recipes-with-ground-beef/`: saved "Unstuffed Peppers" with ingredients `["ground beef","2 cups leftover cooked rice"]` and one "step" that is the card's tip sentence (`QA2/00-foreign-detail.png`, DB row `480afec8`). The collection's cards carry `Recipe` JSON-LD with name/description/image/cookTime but no `recipeIngredient`/`recipeInstructions`; the compact rendering looks like a recipe stub, so the model fills the gaps and the prompt's `NO RECIPE` rule never fires. 
**Cause.** `detectStructured()` only de-dupes (`RecipeKeeperClipConfig.kt:72-96`, filter at :76); `accept()` returns `true` for every non-WholePage candidate (`:366-369`). 
**Fix.** In the `:76` filter drop nodes where `mapper.ingredients(raw).isEmpty() && mapper.instructions(raw).isEmpty()` (they then fall through to Tier B / nothing-detected). In `accept()` apply a floor to STRUCTURED and TEXT results as well: ≥ 2 quantity-bearing ingredient lines **or** ≥ 2 steps (reuse `MIN_COMPLETE_*` at `:438-439`), so a two-line "recipe" becomes `ParseFailed` and, for STRUCTURED, falls back to `SchemaOrgRecipeMapper` (which would also be empty here → the parse-failed screen, which is right).

### 4. (P2) Pictures from other recipes are pre-selected on round-up pages
**Seen** in S2: strip = fig ice-cream, feta pancakes, fricadelles, pork (`12-s2-first-draft.png`); first three checked → 2 of the 3 saved pictures belong to other recipes (`13b-s2-detail-pictures` pager). Even on the single-recipe BBC page the third pre-checked picture is a "related recipe" card image (`22-s5-before-stop.png`). 
**Cause.** `pickPictures()` appends `og:image` and then every large page image after the node's own `imageUrls` (`RecipeKeeperClipConfig.kt:371-399`); `DEFAULT_KEEP = 3` checks the first three regardless (`ExtractionViewModel.kt`, companion). 
**Fix.** Pre-check only `min(3, nodeImageCount)` for STRUCTURED candidates (pass the count through `PictureDraft.keep` from the config, e.g. `pickPictures` returns a `List<PicturePick(url, preferred)>`), and when the page has more than one structured candidate do not add the global `images`/`og:image` pool at all.

### 5. (P2) "Show raw output" appears twice on the extraction screen
`ClipDraftCard` renders its own toggle (`app/recipekeeper/.../output/ClipDraftCard.kt:109-124`) and the feature adds `item("raw") { RawOutput(...) }` under the draft (`WebClipperExtractionScreen.kt:198-199`); both are visible in every extraction screenshot (`12-s2-first-draft`, `15-s3-after-tap`, `22-s5-before-stop`). 
**Fix.** Remove the card's copy; the feature's one also drives `ExtractionIntent.ToggleRaw` and is what the failure screen reuses.

### 6. (P2) Same URL clipped again → duplicate recipes
Four "Easy pancakes" tiles from `bbcgoodfood.com/recipes/easy-pancakes` (`26-s8-library-grid.png`); DB rows `9a734fe8`, `6a9dfe53`, `7b412f8f`, `cd0ca9a6`. 
**Fix (needs a product decision).** `RecipeKeeperClipSink.save()` (`clip/RecipeKeeperClipSink.kt:31-52`) could look up `repository.findBySourceUrl(snapshot.url)` and reuse that id (re-clip = refresh), or the extraction screen could warn "Already in your library — replace?" before saving. The DB has no index on `sourceUrl` yet.

### 7. (P2) Pictures-unavailable state is not surfaced after the fact
S7 saved the recipe with no pictures and the detail shows nothing about it; `labels.picturesUnavailable` is only used as the phase detail line (`WebClipperExtractionScreen.kt:246`) and `labels.retryPictures` is referenced nowhere, so UX-spec §4 ("detail `picturesUnavailable` + `retryPictures`") is unimplemented. 
**Fix.** Persist a `picturesFailed: Boolean` on `ClippedRecipe` (set in `finish()` from `anyPictureFailed && kept.isEmpty()`), show an `AssistChip(picturesUnavailable)` in the detail meta row (`detail/RecipeDetailScreen.kt:577` area) and a `TextButton(retryPictures)` that re-runs `ImageDownloader` on the stored source URL's picks. Until then, at least show the chip.

### 8. (P2) The `Stop` button is below the fold during extraction
`OutlinedButton(stopExtraction)` is the last item after the draft card and picture strip (`WebClipperExtractionScreen.kt:439-441`); on a 1080×2400 phone it is never on screen without scrolling (`22-s5-before-stop.png`), so the only reachable cancel is the small top-bar X. 
**Fix.** Render Stop in a `Scaffold` `bottomBar` (or a sticky row directly under `PhaseProgress`) while `state.isRunning`.

### 9. (P3) "Saving pictures" ring spins for the whole model run
Downloads finished in the first ~5 s of every run (temp files time-stamped at tap time) but the phase only becomes Done/Warning in `finish()` after `pictures.await()` (`ExtractionViewModel.kt:270-271`), i.e. after the model. On the 5–12 min night runs the ring spun the whole time (`05b-s1-extracting-now.png`). 
**Fix.** Set `Phase.Pictures` to Done/Warning inside `startPictures()` right after `awaitAll()` (`:293-307`), keep `finish()` reading the result.

### 10. (P3) Skeleton lines are invisible on the draft card
Before the first token the card shows blank gaps where `SkeletonLine`s should shimmer (`12-s2-first-draft.png`, `22-s5-before-stop.png`): the skeleton colour sits on a tonal-elevation-2 surface of this brand with almost no contrast. 
**Fix.** `SkeletonLine` (`core/designsystem/.../component/SkeletonLine.kt`) should use `onSurface.copy(alpha = 0.08f–0.14f)` over the parent's colour instead of a fixed container colour.

### 11. (P3) Snackbar covers the library FAB
`Recipe deleted / Undo` is drawn over "Clip a recipe" (`29-s8-deleted-snackbar.png`); the host is app-wide in `MainActivity`, so the Scaffold cannot lift the FAB. 
**Fix.** Give the library `AppScaffold` the snackbar host (or pad the FAB by the snackbar height while one is shown).

### 12. (P3, observation) Memory grows ~0.5 GB native across six extractions
PSS 1.82 GB → 2.66 GB, native heap +517 MB, with every `Conversation` closed in `finally`. Could be LiteRT-LM KV-cache arenas, WebView, or Coil's decoded pictures. No pressure on an 8 GB device; worth a `dumpsys meminfo` series on a 6 GB phone before release.

## Could not be verified

- **Cancel mid-generation** on a loaded host (model finishes in ~10 s; see §Scenario 5 for what was verified).
- **"Saving pictures ⚠" phase visual** and the **truncation banner / `Possibly incomplete` chip**: the offline run finished in 10 s before a dump could catch the phase; no output was truncated (`truncated = 0` on all 7 rows).
- **`Try the whole page`** (parser null on a STRUCTURED candidate) and **"model file removed mid-flow"**: did not occur / not provoked (deleting the model would have ended the session).
- **TalkBack**: milestone live region, `stateDescription`s, heading semantics (uiautomator does not expose them).
- **Scenario 4 duration** and any **real-phone / GPU** figure.
- The `ModelNotReady` → management flow and "Use this model" (out of scope; model already installed).

## Timings summary

| Run | Prompt source | Conversation tokens | Tap → done | Note |
|---|---|---|---|---|
| S1 BBC easy-pancakes (22:53) | JSON-LD compact | 986 | 299 s | host loaded |
| S2 Guardian fig ice-cream (23:01) | JSON-LD compact | 905 | 234 s | host loaded |
| S3 Joy of Baking (23:08) | readable section text | 1,232 | 742 s | host loaded |
| S4 bbc.com whole page (00:57) | 9,000-char readable | 2,131 | n/a | host suspended |
| BBC easy-pancakes ×3 (09:27, 09:29, 09:33) | JSON-LD compact | 986 | 10 s each | host idle, airplane mode on the last |
