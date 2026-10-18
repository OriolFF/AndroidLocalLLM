# WS6 report — device QA of Recipe Keeper on `emulator-5554`

Run on 2026-10-18 by the ws6 agent (android-ux persona) against `recipekeeper/main` at `a6d9185` (WS4 + WS5b integrated). No code was edited; every finding below carries a proposed fix.

**Build / install.** `./gradlew :app:recipekeeper:assembleDebug` → BUILD SUCCESSFUL; `adb install -r app/recipekeeper/build/outputs/apk/debug/recipekeeper-arm64-v8a-debug.apk` → Success (170 MB free before, 125 MB after; nothing deleted). `pm clear` before the first launch so the run started from a true fresh install.

**Device.** `sdk_gphone64_arm64`, API 36, 1080×2400 @ 420 dpi (411×914 dp), system language Spanish. No LLM model fits (`/data` 98 % full), so readiness stayed `NoModel` for the whole run.

**Screenshots.** `QA` below = `/private/tmp/claude-501/-Users-oriol-Documents-development-projectes-AndroidLocalLLM/6d100e70-7137-46b6-ad09-7c1e8dd3fa5f/scratchpad/qa/`. 44 PNGs, the raw hierarchy dumps (`dump-*.xml`) and `logcat-full.txt` are there too.

**Tooling notes (so the next person does not lose 20 minutes).**
- `android layout` fails on this emulator ("Error communicating with instrumentation server") and `adb shell uiautomator dump` is SIGKILLed in `UiAutomationManager.registerUiTestAutomationServiceLocked`: a uiautomator2 agent (`com.github.uiautomator`, host port forward `tcp:60161 → 9008`) already owns the UiAutomation connection and restarts itself when force-stopped. Hierarchy dumps were taken through that agent instead: `curl -X POST http://127.0.0.1:60161/jsonrpc/0 -d '{"jsonrpc":"2.0","id":1,"method":"dumpWindowHierarchy","params":[false]}'`. It exposes text / content-desc / checkable / bounds but not Compose `stateDescription`, `heading` or live regions.
- The emulator has no `sqlite3`. Seeding was done by force-stopping the app, pulling `databases/recipekeeper.db{,-wal,-shm}` with `run-as … cat`, checkpointing + inserting with the host `sqlite3`, pushing the file back with `run-as sh -c 'cat > databases/recipekeeper.db'` and deleting the stale `-wal/-shm`. Pictures are two screenshots copied to `files/recipes/r1/`. Room accepted the file (identity hash preserved). Three recipes: one with 2 pictures + 2 pre-checked ingredients, one `truncated = 1` with no picture and a very long title, one plain. The seeded data is still in the app sandbox; `adb shell pm clear com.llmlocal.recipekeeper` resets it.

## 1. What was verified

| Area | Result | Evidence |
|---|---|---|
| Fresh launch → library empty state + no-model hero | OK. Hero copy, "Open models" full-width action, `EmptyState` with "Open browser" as the only primary action, no FAB on the empty state. Koin started 40 definitions, first frame +1.0 s, no "no definition found". | `QA/01-library-empty-no-model.png` |
| Hero action → model management | OK. Opens the shared screen with Recipe Keeper copy: intro says "select it for recipe clipping"; no "recipe generation" wording anywhere on the screen. | `QA/02-model-management-from-hero.png`, `02b`, `02d` |
| Top-bar models action → model management | OK, same destination; back returns to the library. | `QA/03-model-management-from-topbar.png`, `03b` |
| Model detail sheet | OK (size, family, min RAM, file, URL). | `QA/02f-info-tap-2s.png` |
| Model-list snackbars via "Use" on a non-downloaded model | **Not reachable from the UI** — see finding 6. | — |
| Library with data | OK. Adaptive grid, 4:3 hero or gradient + `Restaurant` placeholder, 2-line title ellipsis, `host · total time`, "Possibly incomplete" badge, long-press menu (Open original / Share / Delete in error colour). | `QA/06-library-grid-seeded.png`, `08-tile-long-press-menu.png` |
| Recipe detail | OK. Collapsing 16:10 hero with title on scrim, meta chips, source row with "Open original", ingredient check-off with strike-through and "n of m checked" (persisted: still "3 of 11" after reopening later), numbered steps, picture pager ("Picture 1 of 2"), overflow (Open original / Re-extract / Keep screen on / Delete), top bar fades to surface on scroll. | `QA/07-detail-with-picture.png`, `07b`, `07c`, `07d` |
| Browser: open from empty-state action | Opens, pill shows hint "Search or enter a site" — **but the address bar cannot be focused** (finding 1). | `QA/04-browser-blank.png`, `05*` |
| Browser: page load states (reached via "Open original") | OK. Loading: Stop (×) + 2 dp progress line; loaded: host-only pill with lock icon + Reload; reload toggles back to Stop. | `QA/09a-browser-allrecipes-loading.png`, `09b`, `11b-browser-reloading.png` |
| Browser: overflow menu | OK. Forward (disabled when no history), Share page, Open in browser, Copy link; 48 dp rows. | `QA/11-browser-overflow.png` |
| Browser: FAB `ModelNotReady` | OK. `secondaryContainer`, `Memory` icon, label "Choose a model", content-desc = label; tap → model management; back returns to the same page without reload. | `QA/09b`, `10-fab-tap-to-models.png`, `10b-browser-after-models.png` |
| Browser: FAB collapse / expand on scroll | OK (178×56 dp → 56×56 dp → 178×56 dp). Needed the site's cookie dialog dismissed first; while the dialog is up the page does not scroll. | `QA/13c-fab-after-scroll-down.png`, `13d` |
| Browser: system back through WebView history | OK. Carbonara → Pasta → back = Carbonara (Forward becomes enabled); back again pops to the library; a third back leaves the app. | `QA/17a-wikipedia-pasta-link.png`, `17b-after-system-back.png`, `14b` |
| Browser: IME overlap | OK. Focusing the Wikipedia search box shows the keyboard, the WebView is resized above it and the FAB is hidden; FAB returns when the keyboard closes. | `QA/18a-ime-open.png`, `18b-ime-closed.png` |
| Browser: FAB vs site chrome | FAB covers Allrecipes' cookie-consent "Show Purposes" button and the sticky ad footer (finding 4). | `QA/09b`, `12-consent-dismissed.png` |
| 962×600 dp window (`wm size 1280x800` + `wm density 213`) | Library: hero spans all columns, 5-column adaptive grid (177 dp cells). Detail: two panes (hero/meta/source/ingredients ⎮ steps/pictures). Browser: layout fine, **pill exceeds the 720 dp cap** (finding 3). | `QA/19b-library-1280x800.png`, `19c-detail-1280x800.png`, `19a-browser-1280x800.png` |
| Landscape phone (`user_rotation 1`) | Browser top bar + FAB respect insets; pill again wider than 720 dp. Back after rotation works. | `QA/21a-browser-landscape.png` |
| Logcat | No crash, no ANR, no Koin error, no Room/SQLite error, no WebView render-process loss. See §5. | `QA/logcat-full.txt` |

Emulator restored: `wm size reset`, `wm density reset`, `accelerometer_rotation 1` / `user_rotation 0`, pushed temp files removed from `/data/local/tmp`. Nothing else on the device was changed (the uiautomator2 agent I force-stopped once restarted by itself within a second).

## 2. Bugs, ranked by user impact

### 1. Address bar never enters edit mode — the browser cannot be typed into (P0)
Tapping the URL pill (or the blank-start auto-focus) leaves it as a plain button; the IME never opens. Verified by tap, by immediate screenshot (no flicker), and by hardware-keyboard Tab + Enter: `mInputShown=false` every time; the dump always shows `Button "Search or enter a site" desc="Address bar"`. The only way to load a page today is "Open original" from a saved recipe. `QA/04-browser-blank.png`, `05-url-pill-tapped.png`, `05b`, `05c`.

Cause (code reading, consistent with every observation): `UrlTextField` is only composed while `state.urlFocused` is true, and its `onFocusChanged` at `feature/webclipper/src/main/kotlin/com/llmlocal/feature/webclipper/ui/BrowserTopBar.kt:245-247` sends `UrlFocusChanged(false)` whenever `!isFocused && state.urlFocused`. Compose delivers the initial `Inactive` focus event on attach, so the field dismisses itself in the same frame it appears, before `LaunchedEffect { focusRequester.requestFocus() }` (line 207) runs. The blank-start `LaunchedEffect` in `WebClipperBrowserScreen.kt:52-54` is defeated the same way. The ViewModel tests pass because the state machine is right; the bug is in the composable.

Proposed fix (BrowserTopBar.kt, `UrlTextField`): ignore focus loss until the field has actually gained focus once —
```kotlin
var hadFocus by remember { mutableStateOf(false) }
…
.onFocusChanged { focus ->
    if (focus.isFocused) hadFocus = true
    else if (hadFocus && state.urlFocused) onIntent(BrowserIntent.UrlFocusChanged(false))
}
```
and request focus from a `LaunchedEffect(Unit)` as now. Alternative that also removes the recomposition flash: keep one `BasicTextField` mounted always and switch between host-only display and editing with `readOnly`/`visualTransformation`. Add a Compose UI test (`createComposeRule`) that taps the pill and asserts a focused text field exists; `testing-setup` skill covers the dependency wiring.

### 2. "Active" / "Installed" badge renders one letter per line in the model list (P1)
With Recipe Keeper's serif `titleLarge`, the model title fills the inner title row and the `StatusDot` badge is squeezed to a 7×93 dp column ("A / c / t / i / v / e"). `QA/02-model-management-from-hero.png`, `02d`, `03`. The other brands have narrower sans titles, so it has been hiding.

Fix in shared code (check both other brands after): `feature/modelmanagement/src/main/kotlin/com/llmlocal/feature/modelmanagement/components/ModelCard.kt:120-125` give the title `modifier = Modifier.weight(1f, fill = false)` and `maxLines = 2, overflow = TextOverflow.Ellipsis`; `core/designsystem/src/main/kotlin/com/llmlocal/core/designsystem/component/StatusDot.kt:84` give the label `maxLines = 1, softWrap = false`. Optionally move the badge under the title on narrow widths.

### 3. Browser URL pill ignores the 720 dp cap on wide windows (P1 for tablets, P3 on phones)
Measured 765 dp at a 962 dp window and ≈759 dp in phone landscape (spec §2: cap 720 dp, centred). `QA/19a-browser-1280x800.png`, `21a-browser-landscape.png`.

Cause: `BrowserTopBar.kt:104-106` applies `Modifier.fillMaxWidth().widthIn(max = 720.dp)`; `fillMaxWidth` fixes the min constraint first, so `widthIn` cannot shrink it. Fix: swap the order — `Modifier.widthIn(max = 720.dp).fillMaxWidth()` (the enclosing `Box(contentAlignment = Center)` already centres it).

### 4. Clip FAB collides with site chrome (P2, product)
On Allrecipes the FAB sits on top of the cookie dialog's third button and later on the sticky ad footer; the page cannot be scrolled until the dialog is dismissed, so the FAB blocks exactly the control the user needs. `QA/09b-browser-allrecipes-loaded.png`, `12-consent-dismissed.png`. Spec §2 lists long-press → "Hide on this page" (labels `fabHideForPage` / `showClipButton` exist) but `ClipFab.kt` has no long-press and no docked "Show clip button" chip. Smallest fix: implement that long-press + `AssistChip` (`feature/webclipper/src/main/kotlin/com/llmlocal/feature/webclipper/ui/ClipFab.kt:66-85`, `WebClipperBrowserScreen.kt:68-82`), remembering the choice per host in `BrowserState`. Larger alternative from plan risk #8: a `BottomAppBar` instead of a floating button.

### 5. Default model is badged "Active" (star, primary container) while it is not installed (P2, product)
On a fresh install the catalog default is "selected", so the first card reads "Active · Download" next to a library hero that says "Choose a model to start clipping". `QA/02-model-management-from-hero.png`. Shared behaviour from `:feature:modelmanagement` (`ModelCard.kt:126-131` shows the Active badge on `isSelected` alone). Proposal: show "Active" only when `isSelected && isInstalled`; a selected-but-missing model gets no badge (or an "Default" tone) and keeps the Download action. Needs a label for the new state if you want text.

### 6. Two label strings are unreachable from the UI (P3)
- `WebClipperLabels.modelNotReadySnackbar` is only sent on `BrowserIntent.Scan` while not ready (`BrowserViewModel.kt:244-247`), but the screen never sends `Scan` for a `ModelNotReady` FAB — it sends `OpenModelManagement` directly (`WebClipperBrowserScreen.kt:75-78`). Observed: tap → model management, no snackbar (`QA/10-fab-tap-to-models.png`). Matches the spec table, so either delete the label or send the snackbar from the screen path too.
- `ModelManagementLabels.modelNotDownloadedSnackbar` (`ModelManagementViewModel.kt:146`) needs `Select` on a non-installed model, but "Use this model" is only rendered when `isInstalled && !isSelected` (`ModelCard.kt:208-227`) and the detail sheet has no select action. The brief's "tap Use on a non-downloaded model" therefore cannot be performed; the string is dead code until the sheet gets a select button.

### 7. Predictive back not enabled (P3, informational)
`WindowOnBackDispatcher: OnBackInvokedCallback is not enabled for the application` ×10 in logcat; `app/recipekeeper/src/main/AndroidManifest.xml` has no `android:enableOnBackInvokedCallback="true"`. No behavioural impact at `targetSdk 34`; add it together with the other two apps when moving to 35+.

### 8. Main-thread stalls on WebView first paint / window resize (P3)
`Choreographer: Skipped 43 frames` and `34 frames`, `HWUI Davey! ≈750 ms` ×3 during the run (first Allrecipes load and the `wm size` relayout). Debug build + CPU emulator; re-measure on a device with `android-profiler` before acting.

### Observed once, not reproduced
After `wm size 1280x800` while on the browser, two system back presses did nothing (dump still showed the browser); the back arrow worked. Rotating to landscape and back on the browser later popped correctly (`QA/21a`, `21b`). Keep an eye on `BackHandler(enabled = state.canGoBack)` in `WebClipperBrowserRoute.kt:123` after Activity recreation; if it recurs, log `canGoBack` on `HistoryChanged`.

## 3. Could not be verified, and why

- **Everything that needs `ModelReadiness.Ready`**: FAB `PageLoading → Idle → Found(n)` badge, `Scanning`, `Extracting`, the candidate sheet, the nothing-detected sheet, "Extract anyway", the extraction screen, `recipeSaved` / `clipCancelled` snackbars, the library download card and the `Downloading n%` FAB. `recomputeFab()` (`BrowserViewModel.kt:311-328`) puts readiness first, so with no model the FAB is `ModelNotReady` on every page, including the JSON-LD Allrecipes page and the plain Wikipedia page. The proactive quick scan does run but has no visible effect. Previews for these states compile (part of `assembleDebug`); rendering them needs the IDE or a screenshot-test module.
- **Typing a URL, the search template, the page-load-error snackbar**: blocked by finding 1.
- **TalkBack `stateDescription` / live-region / `heading` values**: not exposed by the uiautomator2 dump; needs TalkBack or a Compose semantics test.
- **Real-device timing, memory, GPU path**: out of scope for this emulator.

## 4. Accessibility findings (uiautomator2 dumps of library, browser, detail, model list)

- **Icon-only controls**: every one has a content description — Back, Search, Manage models, Details, Reload / Stop loading, More options, Back to your recipes, Share recipe. Address bar: `Button`, desc "Address bar", visible text = host. OK.
- **Touch targets**: all IconButtons measure 48×48 dp (the 40 dp Reload/Stop button is padded to the 48 dp minimum); model-card Details 48; menu rows 48; ingredient rows 411×48 dp `checkable/checked` exposed; step rows ≥48 with "Step i of n. …" descriptions; FAB 56 dp expanded and collapsed; "Open original" 48. Meta chips are non-interactive (32 dp, fine). Pager peek "Picture 2 of 2" is a 4 dp decorative node.
- **Browser FAB**: one node, content-desc = label ("Choose a model"), `traversalIndex = -1`; `stateDescription` not verifiable here. OK by inspection of `ClipFab.kt:78-84`.
- **Library FAB has no accessible name in the dump**: the clickable node is `NAF="true"` with empty text/desc and the "Clip a recipe" text never appears in the tree (`dump-06-library-grid.xml`, re-checked at 962 dp). The browser `ExtendedFloatingActionButton` shows the same pattern (its `Text` child is absent, only the explicit content-desc survives), so this looks like the M3 extended-FAB label not being exposed through this dump path. Cheap insurance: add `Modifier.semantics { contentDescription = labels.libraryFabClip }` at `app/recipekeeper/src/main/kotlin/com/llmlocal/recipekeeper/library/LibraryScreen.kt:118-124` and verify once with TalkBack.
- **No `heading()` semantics anywhere** (`grep heading()` over app + shared UI = 0): `SectionHeader` (`core/designsystem/.../SectionHeader.kt:67`), `GradientHero` title, "Available models", "Your recipes". Add `Modifier.semantics { heading() }` to `SectionHeader`'s label and the hero title so TalkBack heading navigation works on the detail screen.
- **Mixed-language experience**: the device is in Spanish; all app copy is English (labels have no locale), while M3 system strings ("Cerrar hoja", "Controlador de arrastre" on the sheet) and the `DateFormat` date ("Clipped 16 oct 2026") follow the device. Expected under the labels design, but worth a line in CLAUDE.md so nobody files it as a bug.
- **Hero title contrast**: title in white over a 0 → 65 % black gradient (`RecipeDetailScreen.kt:530`). On the light seeded picture it was legible but marginal (`QA/07-detail-with-picture.png`); consider a minimum flat 40 % scrim behind the title band or `headlineMedium` with a 1 dp shadow.
- **Model list badge**: the vertical "Active" text also produces a 7×93 dp node; fixed by finding 2.
- **Focus indication**: after hardware-keyboard navigation the Search action shows a persistent focus ring (`QA/06-library-grid-seeded.png`); normal Compose behaviour, not a bug.

## 5. Logcat summary (`QA/logcat-full.txt`, 10 161 lines from the browser session onward; earlier segments were inspected live)

- Startup: `[Koin] Started 40 definitions in 0.517 ms`, no `NoDefinitionFound`, `Displayed com.llmlocal.recipekeeper/.MainActivity +1s33ms` (cold, fresh install), +799 ms / +165 ms on later launches.
- No `FATAL EXCEPTION`, no `ANR`, no `AndroidRuntime` stack from the app process, no Room/SQLite errors after the seeded DB was pushed, no `onRenderProcessGone`.
- WebView: `variations_seed_loader … Seed missing signature` (emulator WebView, harmless), `page_load_metrics … Invalid first_paint` ×6 (Chromium internal), `cr_media` Bluetooth permission warnings (standard for WebView without Bluetooth permission).
- `WindowOnBackDispatcher` ×10 → finding 7. `Choreographer` skipped frames ×2 → finding 8.
- `ModelDownloadWorker` never ran (no download was started; checked after an accidental mis-tap during a list fling).

## 6. Suggested follow-ups, in order
1. Fix finding 1 and add a Compose UI test for the address bar (blocker for any further browser QA).
2. Fix findings 2 and 3 (two one-line shared fixes; re-screenshot `:app:recipes` and `:app:medical` model lists after 2).
3. Decide on 4 and 5 (product).
4. Re-run this brief on a device with a model installed to cover §3 (candidate / nothing-detected sheets, extraction, snackbars); the seeding recipe above gives instant library/detail data.
