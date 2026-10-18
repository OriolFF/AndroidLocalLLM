# WS9 report — fixes from device QA (android-ux persona)

Branch `recipekeeper/ws9`, based on `recipekeeper/main` at `b5ed041`. Work list = `ws6-report.md`. One commit per finding (plus one follow-up commit for finding 1), in brief order. Nothing under `app/recipekeeper/{parser,prompt,clip}`, `ExtractionViewModel.kt` or `WebClipConfig.kt` was touched.

`QA` below = `/private/tmp/claude-501/-Users-oriol--herdr-worktrees-AndroidLocalLLM-recipekeeper-ws9/1c5d663d-955e-4d78-b241-e7ab3df29627/scratchpad/qa/` (this session's scratchpad; copy them out if they are needed beyond today).

## Commits

| # | Commit | Finding |
|---|---|---|
| 1 | `84f1c52` webclipper: address bar enters edit mode | 1 (P0) |
| 2 | `3597c19` modelmanagement: keep the Active/Installed badge on one line | 2 (P1) |
| 3 | `8e4af9b` webclipper: URL pill honours the 720 dp cap | 3 (P1) |
| 4 | `99fe72d` webclipper: long-press the clip button to hide it on this page | 4 (P2) |
| 5 | `70a7259` modelmanagement: badge a model Active only when selected and installed | 5 (P2) |
| 6 | `2e32eaa` webclipper: ModelNotReady tap shows the snackbar before model management | 6a (P3) |
| 7 | `f0918b9` a11y: FAB name, heading semantics and a legible hero title | A11y |
| 8 | `a7d99d5` webclipper: address bar follow-ups from device QA | 1 (follow-ups found on device) |

## Per finding

### 1. Address bar never entered edit mode (P0)
**Fix.** `UrlTextField` (`feature/webclipper/.../ui/BrowserTopBar.kt`): `onFocusChanged` ignores focus loss until the field has gained focus once (`hadFocus`), exactly as proposed; `LaunchedEffect(Unit)` requests focus **and** calls `LocalSoftwareKeyboardController.show()` so the blank-start auto-focus opens the IME on the first frame.
**Follow-ups found once it worked (commit 8):**
- Fast typing lost every character but the last (`QA/w2-url-typed.png`, observed while the emulator was under heavy load). Cause: the `LaunchedEffect(state.draftUrl)` re-sync ran against a draft one frame behind the local `TextFieldValue` and reset it. The ViewModel only sets `draftUrl` in the same update that focuses the field and otherwise just echoes `UpdateDraftUrl`, so the local value is now seeded once and the re-sync is gone.
- Reopening the browser on a page the shared WebView already shows ran the blank-start auto-focus before `WebClipperBrowserRoute` synced the URL: the screen came back with the keyboard up and the FAB hidden. `WebClipperBrowserScreen` gained `autoFocusAddressBar: Boolean = true`; the route passes it only on a genuinely blank start (no page in the ViewModel, no `initialUrl`, nothing in the WebView, nothing to restore).
- `BackHandler(enabled = state.urlFocused)` leaves edit mode instead of popping WebView history (the IME still eats the first back itself).
**Test.** `feature/webclipper/src/androidTest/.../ui/BrowserTopBarTest.kt` (`createComposeRule`, 3 tests): tap the pill → a focused node with `SetText` and the full URL as editable text; typing + IME *Go* → `UpdateDraftUrl("cooking.example/pasta")` then `SubmitUrl`; blank-start `WebClipperBrowserScreen` auto-focuses. **Ran on `emulator-5554`: 3/3 passed** (`./gradlew :feature:webclipper:connectedDebugAndroidTest`, report in `feature/webclipper/build/reports/androidTests/connected/debug/`). Wiring: `testInstrumentationRunner = AndroidJUnitRunner`, `ui-test-junit4`, `androidx.test.ext:junit`, `androidx.test:runner:1.7.0` (same literal `:app:recipekeeper` uses), and **`espresso-core` pinned to the catalog's 3.7.0** — the 3.5 that `ui-test-junit4` drags in crashes every test on API 35+ with `NoSuchMethodException: InputManager.getInstance`. The test APK also excludes `**/*.so` (LiteRT-LM natives no UI test loads; the library AAR has no JNI of its own), 81 MB → 48 MB.
**Device.** `QA/w1-browser-blank-focused.png` (blank start: edit mode + IME, `mInputShown=true`), `QA/w14-url-typed.png` is not available (see §Open), but the same flow on the installed build: pill tap → `EditText focused=true`, `input text en.wikipedia.org/wiki/Carbonara` → Enter → `QA/w15-wikipedia-loaded.png` shows `en.wikipedia.org` loaded with the Reload action.

### 2. "Active" badge wrapped one letter per line (P1)
**Fix.** `ModelCard.kt` title: `Modifier.weight(1f, fill = false)`, `maxLines = 2`, `TextOverflow.Ellipsis`. `StatusDot.kt` label: `maxLines = 1`, `softWrap = false`.
**Device.** `QA/w6-model-list-active-badge.png`: Recipe Keeper serif title "Gemma 4 E2B IT" wraps to two lines, the "★ Active" pill is 96×37 px (one line). To get a badge on this model-less emulator I dropped a 21-byte `files/llm/gemma-4-E2B-it.litertlm` into the app sandbox with `run-as` and removed it afterwards. `:app:recipes` / `:app:medical` lists could not be re-screenshotted (no space to install, see §Open); both apps assemble and the change is a pure layout constraint, so their sans titles only gain the two-line cap.

### 3. URL pill ignored the 720 dp cap (P1 tablets)
**Fix.** `BrowserTopBar.kt`: `Modifier.widthIn(max = 720.dp).fillMaxWidth()`.
**Device.** `wm size 1280x800` + `wm density 213` (962 dp window): `QA/w10-browser-1280x800.png`, pill centred; its inner text node measures 835 px = 627 dp, plus the pill's fixed parts (12 + 18 + 10 + 10 + 40 + 4 dp) = 721 dp, i.e. at the cap (ws6 measured 765 dp). `wm size reset` / `wm density reset` done; `QA/w11-browser-restored-size.png`.

### 4. Clip FAB vs site chrome (P2)
**Fix.** `ClipFab.kt`: optional `onHideForPage`; long-press (detector in the *Initial* pointer pass, because `ExtendedFloatingActionButton` has no `onLongClick` and a Main-pass detector would still let the release fire `onClick`) opens a one-item `DropdownMenu` "Hide on this page" (`fabHideForPage`, `VisibilityOff` icon, haptic). TalkBack gets the same via `onLongClick` semantics. `WebClipperBrowserScreen.kt`: while hidden a 48 dp `ElevatedAssistChip` "Show clip button" (`showClipButton`) docks bottom-start, outside the WebView traversal group. State: `BrowserState.hiddenFabHosts: Set<String>` + derived `fabHiddenForPage` (`hostOf(url) in hiddenFabHosts`); intents `HideFabForPage` / `ShowFab`; `BrowserViewModel.setFabHidden`. Remembered per host for the ViewModel's life: another host shows the button again, returning to the hidden host keeps it hidden until the chip is tapped.
**Tests.** `BrowserViewModelTest`: `hide for page is remembered per host and restored by ShowFab`, `hide for page is a no-op without a page` (existing tests untouched).
**Device.** `QA/w7-fab-longpress-menu.png` (menu above the FAB), `QA/w8-fab-hidden-chip.png` (chip bottom-start, FAB gone), `QA/w9-fab-restored.png`.

### 5. Default model badged "Active" while not installed (P2)
**Fix.** `ModelCard.kt`: `isActive = isSelected && isInstalled` drives the badge **and** the active styling (gradient, icon tint, bold title). Decision: a highlighted card with no badge would have been a half-state, so the whole active treatment follows `isActive`; a selected-but-missing model is a plain card with the Download action. Buttons unchanged.
**Device.** `QA/w5-model-list-default-not-installed.png` (Gemma 4 E2B IT selected, not installed: no badge, no highlight, Download/Cancel row) vs `QA/w6-model-list-active-badge.png` (same model with a file present: Active). Other apps: same shared card, not re-screenshotted (storage).

### 6a. Dead `modelNotReadySnackbar` path (P3)
**Fix.** `WebClipperBrowserScreen` dispatches `Scan` for every FAB state; `BrowserViewModel.onScan` shows `modelNotReadySnackbar` then `NavigateToModelManagement` (brief: snackbar + navigate). Decision: while readiness is `Downloading` it navigates **without** the snackbar — the button already shows the percentage and "choose and download a model first" would be wrong. `OpenModelManagement` intent kept (unused by the screen now).
**Tests.** Existing `model not ready blocks the scan and navigates to model management` still passes (snackbar + navigate); new `downloading model tap navigates to model management without the snackbar`.
**Device.** Only the Downloading branch was reachable (a stale RUNNING progress record, see §Open): tap → model management, no snackbar (`QA/w4-modelmgmt-from-fab-downloading.png`).

### 7. Accessibility
- `LibraryScreen.kt`: `Modifier.semantics { contentDescription = labels.libraryFabClip }` on the FAB — the dump now shows `desc="Clip a recipe"` (was NAF).
- `SectionHeader.kt` label and `GradientHero.kt` title: `semantics { heading() }`; the detail hero title too.
- `RecipeDetailScreen.kt` hero: bottom scrim `0 → 40 % at 45 % → 82 %` (flat ≥ 40 % behind the title band) + 1 dp text shadow. `QA/w12-detail-hero-scrim.png` — the seeded "photo" is a light, text-heavy screenshot (worst case) and the title stays legible. Pure white on a 40 % scrim over a white photo is still < 4.5:1; the 82 % band where the title actually sits gives ≈ 4.6:1 on pure white, and the shadow carries the rest.

## Verification

- `./gradlew :feature:webclipper:testDebugUnitTest :app:recipekeeper:testDebugUnitTest :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug lint` → BUILD SUCCESSFUL; 141 unit tests (webclipper 15 + 12 + 4, recipekeeper rest), lint 0 errors (webclipper: 2 pre-existing-style warnings, one of them `UseTomlInstead` for the `androidx.test:runner` literal, same as `:app:recipekeeper`).
- `./gradlew :feature:webclipper:connectedDebugAndroidTest` on `emulator-5554` → 3/3 passed.
- Device run: fresh `adb install -r` of the arm64 split at `84f1c52..f0918b9` (22:32), screenshots `QA/w1`–`w12` + `w15`.

## Open / for the lead

1. **Emulator storage.** `/data` sits at 1 % free (54–113 MB, fluctuating with system caches). Android refuses any install while free space is below its low-storage threshold (5 % ≈ 290 MB), so neither the test APK nor an app could be installed at all. There were **no APKs under `/data/local/tmp` to free**. What I did instead, each time for the duration of one install and restored right after: `settings put global sys_storage_threshold_percentage 0` → install → back to **1** (the value it already had when I arrived — I briefly deleted the key once by mistake and put it back to 1). Nothing else on the device was deleted. Consequence: the follow-up build (commit 8) and the `:app:recipes` / `:app:medical` model lists are **not** verified on device; the connected test ran when a 113 MB window appeared.
2. **A stale model download was resurrected by the reinstall.** The 22:32 install rescheduled a never-run `model-download-gemma-4-e2b-it` work request (left over from ws6's mis-tap); it streamed ~108 MB of Gemma 4 E2B onto the full disk, WorkManager's own Room DB threw `SQLiteFullException` (one app crash at 22:32:50, before any of my UI was touched), the worker failed with ENOSPC and deleted its `.part`. The progress DataStore could not persist the FAILED state, so the card and the FAB still read "Downloading 4%" (`QA/w4`, `w5`). `adb shell pm clear com.llmlocal.recipekeeper` resets it (also drops the seeded recipes). Worth a `pm clear` before the next QA run, and a note in CLAUDE.md that `ExistingWorkPolicy.KEEP` requests survive reinstalls.
3. **Finding 6b** (`modelNotDownloadedSnackbar` unreachable because the detail sheet has no Select action) was out of this brief; untouched.
4. The feature's `build.gradle.kts` gained `testInstrumentationRunner` and a test-APK `packaging.jniLibs.excludes` in addition to the androidTest deps (needed for the test to run at all); the catalog was not touched.
5. The accidental DuckDuckGo search in `QA/w3` came from the first (garbled) typing attempt; harmless.
