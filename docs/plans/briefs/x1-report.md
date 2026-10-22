# X1 report — F4 QA bugs (editor IME, MigrationTest, a11y tests)

Branch `fixes/x1`. Commits: `69d619b` (B3), `65f4321` (B1), final commit (B2 + reorder/paste tests + this report).
Verified: `./gradlew :app:recipekeeper:testDebugUnitTest :app:recipekeeper:assembleDebug :app:recipekeeper:assembleDebugAndroidTest` → BUILD SUCCESSFUL (all unit tests green). Instrumented runs on emulator-5556 by **manual install** (`adb install -r` of the arm64 app APK, `adb install -t -r` of the androidTest APK, `am instrument`). No `connectedDebugAndroidTest`, no `pm clear`, no uninstall; model file and library data still present afterwards. The one device setting touched (`secure show_ime_with_hard_kb=1`, needed so the soft keyboard shows on this AVD) was deleted again at the end.

## B3 — MigrationTest now runs and passes
Two causes, not one:
1. `AbstractMethodError … typeParametersSerializers`: the androidTest classpath is aligned to `debugRuntimeClasspath` (consistent resolution), which resolves kotlinx-serialization **1.7.3**; `room-testing` 2.8.5's generated serializers need ≥ 1.8. A pin on `androidTestImplementation` is rejected by that alignment, so the pin is on **`debugImplementation`** (`kotlinx-serialization-core/-json` 1.8.1, catalog `kotlinx-serialization`). Release classpath untouched.
2. Once it executed, the test failed with `duplicate column name: isFavourite`: **`schemas/…/1.json` had been overwritten with the v2 schema** by F3's commit `4982e31` (same identity hash as 2.json), so `createDatabase(…, 1)` built a v2 table. Restored `1.json` from `4982e31^` (identityHash `a955b1c0…`, no favourite/edit/origin columns). `2.json` unchanged. **Lead: keep this file; don't let a build regenerate it** (Room only writes the schema of the current version).
Also: espresso-core 3.5.0 (pulled in by compose ui-test) crashes on API 36 (`InputManager.getInstance`), so `androidx.test.espresso:espresso-core` 3.7.0 is on androidTest.

```
$ am instrument -w -e class com.llmlocal.recipekeeper.recipes.data.MigrationTest com.llmlocal.recipekeeper.test/androidx.test.runner.AndroidJUnitRunner
com.llmlocal.recipekeeper.recipes.data.MigrationTest:.
Time: 0.296
OK (1 test)
```

## B1 — focused editor row hidden by the keyboard
Root cause (found on the real app, not what the QA report guessed): the `LazyColumn` viewport extended **under** the IME because IME space was only `contentPadding`, so Compose treated a row hidden behind the keyboard as visible and never scrolled it. Fix, in `RecipeEditorScreen.kt` / `components/EditableLineList.kt`:
- the list gets `padding(bottom = ime)` (viewport shrinks); `contentPadding` keeps only the bars part (`innerPadding.bottom − ime`); `imeNestedScroll()` added;
- each row's text field has a `BringIntoViewRequester`; `onFocusChanged` + `LaunchedEffect(focused, WindowInsets.isImeVisible)` requests it after one frame and again after 350 ms (`animateItem` placement of a just-added row is still moving at the first request).
Save stays reachable (top bar pinned). Landscape uses the same lists (two panes), same fix.

Evidence on emulator-5556 (portrait, real app, create editor, add steps with the keyboard open): before the fix the focused Step 3/4 row was clipped by the keyboard (only its top edge visible); after the fix the focused row sits fully above the keyboard with its text visible.

**Test:** `EditorImeTest` (Compose UI test, real `RecipeEditorScreen`, edge-to-edge, real IME, asserts keyboard height > 300 px and focused row bottom ≤ IME top after pressing Next on the last ingredient 8×). **Honest limit:** this test also passes against the *unfixed* code — a bare `Scaffold` test activity does not reproduce the failure, only the app window does. So it is a regression guard for "row above keyboard", not proof of the fix; the proof is the on-device before/after above. Landscape was not re-verified by hand (no screenshot); same code path.

## B2 + reorder/paste — Compose UI tests
- `FavouriteToggleSemanticsTest`: `SemanticsProperties.StateDescription` is "Not in your favourites" → click → "In your favourites" → click → off. PASS. (No production change needed: `FavouriteToggle` already sets `stateDescription`.)
- `EditorLinesTest` (3 tests): custom actions "Move down"/"Move up" exist and reorder (steps 0,1,2 → 1,0,2 → 1,2,0, two `MoveLine` intents); first row has only Move down, middle both, last only Move up; a pasted 5-line text in an ingredient row yields rows One…Five (host reducer mirrors `RecipeEditorViewModel.changeLine` with the real `SplitPastedLinesUseCase`; the VM itself is covered by `RecipeEditorViewModelTest`). All PASS.
- `EditorTestHost.kt`: shared stateful host (add/move/edit-line reducer) + `performCustomAccessibilityActionWithLabel` helper (not in this ui-test version).

```
EditorImeTest:                  OK (1 test)
EditorLinesTest:                OK (3 tests)
FavouriteToggleSemanticsTest:   OK (1 test)
MigrationTest:                  OK (1 test)
```

## Files
`app/recipekeeper/build.gradle.kts` (test deps + `debugImplementation` serialization pin), `gradle/libs.versions.toml` (test-only entries), `schemas/…/1.json` (restored), editor `RecipeEditorScreen.kt`, `components/EditableLineList.kt`, `src/androidTest/…/recipes/presentation/{editor/EditorTestHost,EditorImeTest,EditorLinesTest, library/FavouriteToggleSemanticsTest}.kt`. `FavouriteToggle.kt` untouched. No strings added.

## Not done / for the lead
- Drag-to-reorder (long-press) still untested; custom actions are the guaranteed path.
- Landscape IME not re-checked on device; very short viewports (≈130 px) can still show only part of a tall multi-line row.
- `EvalRunnerTest` is unaffected; running `am instrument` without `-e class` would also run it.
