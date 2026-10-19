# F4 QA report — favourites, edit, create (emulator-5556, branch `recipekeeper/main`, 2026-10-20)

Evidence in `qa6/` (screenshots `NN-*.png`, uiautomator dumps `NN.xml`, `v1-rows.txt`, `v1-recipekeeper.db`, `v2-recipekeeper.db`, `v2.txt`, `logcat-full.txt`). No code edits, no git writes.

## ⚠ Incident caused by this QA run (read first)
`./gradlew :app:recipekeeper:connectedDebugAndroidTest …` **uninstalls the app after the run** (AGP default), which wiped `com.llmlocal.recipekeeper` data on emulator-5556: the 22/23-recipe fixture, the picture files and the 2.6 GB model. I did not anticipate this; the brief forbids `pm clear` and deleting recipes, and this had the same effect.
Recovery done: reinstalled the same APK, pushed `~/.cache/llmlocal-eval/gemma-4-E2B-it.litertlm` back to `files/llm/`, restored the **v1 database** from my pre-install dump (`qa6/v1-recipekeeper.db`, 23 rows, `user_version=1`, restored *before* first launch so the migration ran again on the real v1 file). Library shows 23 recipes again.
**Not recoverable:** the downloaded picture files (`files/recipes/<id>/*`, 22 folders; counts in `qa6/v1-rows.txt`). Rows reference pictures that no longer exist (tiles show placeholder / "Pictures unavailable", "Retry pictures" works because the source URL is stored). Picture-file checks were therefore done on a recipe created during the run.
Lesson / follow-up: run instrumented tests on a *different* device/AVD, or install the test APK by hand (`adb install -t` + `am instrument`) so the app APK is never uninstalled. Add to the QA brief template.

## Migration (v1 → v2) — PASS (but `MigrationTest` itself FAILS to run)
- Pre-install dump: `qa6/v1-rows.txt` — 23 rows (22 expected in the brief, actual was 23), 0 soft-deleted, `user_version=1`, WAL merged by sqlite3.
- After installing the merged APK and first launch: `user_version=2`, 23 rows, identical id list (`diff` clean), `sum(isFavourite)=0`, `origin='CLIPPED'` for all, `count(editedAt)=0`, index `index_recipes_isFavourite` present, `room_master_table.identity_hash = 854d627abae57bb4b1b73015831c31f4` = `identityHash` in `schemas/…/2.json`; `2.json` is byte-identical to the committed one. No Room/SQLite/migration error in logcat (only an unrelated `SQLiteLog (28) double-quoted string literal` warning from GMS).
- The migration was then re-run a second time on the restored v1 DB: same result, no error.
- **`MigrationTest`: FAIL.** Command: `./gradlew :app:recipekeeper:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.llmlocal.recipekeeper.recipes.data.MigrationTest`. Result: 1 test, 1 failure, `java.lang.AbstractMethodError: abstract method "kotlinx.serialization.KSerializer[] kotlinx.serialization.internal.GeneratedSerializer.typeParametersSerializers()" on receiver Class<androidx.room.migration.bundle.FieldBundle$$serializer>` (thrown by `MigrationTestHelper` reading the schema JSON). A kotlinx-serialization runtime/compiler-plugin version mismatch on the androidTest classpath — see bug B3.

## Favourites
| Check | Result |
|---|---|
| Heart on tile toggles, DB `isFavourite` updated | PASS (`04-hearted.png`, DB rows) |
| Heart on detail toggles | PASS (`15`) |
| Survives restart (`force-stop` + relaunch) | PASS (`05`, `06`) |
| Filter chip narrows list | PASS (`06`) |
| Filter + search ("cake" → 1; "zzz" → "No favourite matches “zzz”" + Clear search) | PASS (`07`, `08`) |
| Empty state "No favourites yet" + "Show all recipes" returns to full list | PASS (`12`) |
| Un-heart inside filtered view removes the tile (animateItem) | PASS functionally; the fade was not catchable with screencap latency (`11`) |
| Replace on re-clip keeps heart | PASS — re-clipped Brownies (LLM on emulator): same id, `isFavourite=1`, `editedAt` NULL, title back to the original; dialog warns "Any edits you made to it are lost" |
| uiautomator shows `Favourite` + state descriptions | PARTIAL — `content-desc="Favourite"` present on every heart (tile + detail), **`stateDescription` is not exposed by this uiautomator** (no attribute in the dump, `checked` stays false). Not verifiable here; needs TalkBack or a Compose test (`SemanticsProperties.StateDescription`). |

## Edit
| Check | Result |
|---|---|
| Pencil (tile menu / detail) → editor with fields, 3 picture slots | PASS (`17`) |
| Title change | PASS |
| Reorder via drag | NOT TESTED (hand-rolled long-press drag, no edge auto-scroll per F3; adb swipes can't drive it reliably) |
| Reorder via Move up/down custom actions | **NOT VERIFIABLE**: uiautomator can't show custom actions and no Compose UI test exists (`androidTest` has only `eval/` and `recipes/data/MigrationTest`; the optional step 3 was not written). |
| Paste a 5-line list into an ingredient row → 5 rows | PASS (clipboard via a multi-line description field, Ctrl+V; rows "One…Five" appended; `28`) |
| IME "Next" on last ingredient adds a row | PASS |
| Remove a step | PASS |
| Remove a stored picture + add one from the photo picker (JPEG pushed to `/sdcard/Pictures`, MediaStore scan) | PASS on a created recipe: 001+002 → remove 001, add one → folder `002.jpg 003.jpg` (numbering after highest, dropped file gone) |
| Save → detail updated, "Changes saved" snackbar, "Clipped … · Edited Oct 20, 2026" row, `editedAt` set | PASS (`33`; DB `editedAt=1792448890838`, 17 ingredients, 4 steps) |
| Checked ingredients kept on surviving rows after edits | PASS (`checkedIngredients [0,2]` unchanged; "2 of 17 checked") |
| Heart + origin untouched by edit | PASS (`origin=CLIPPED`, `isFavourite=1`) |
| Back with changes → dialog; Keep editing keeps draft; Discard restores | PASS (`31`; after Discard the stored picture files were still present) |
| Process death (`am kill` after Home, reopen from Recents) | PASS — new pid, editor reopened with the typed title (`43`, `44`). Re-launching from the launcher icon instead opens the library (expected, new task). |
| Temp pictures cleaned | OK for removed ones (file deleted immediately). One orphan temp file stayed in `cache/recipe-editor/` from a session killed mid-edit; sweep (24 h) will take it. |

## Create
| Check | Result |
|---|---|
| "+" → empty editor | PASS (`34`) |
| Save with title + 1 ingredient (+ a step) → detail "Created by you" | PASS (`52`), `origin=CREATED`, no `sourceUrl` |
| No Open original / Re-extract (overflow = Share recipe, Keep screen on, Delete) | PASS (`53`) |
| Share text contains ingredients and steps | PASS: `PD test\n\nIngredients:\n- 2 cups flour\n\nSteps:\n1. Mix it again more` (`54`) |
| Tile meta "Created by you", description "PD test, created by you" | PASS |
| Pictures on create (2 via multi-picker) | PASS: `001.jpg`, `002.jpg` moved into `files/recipes/<id>/` |
| Empty-library "Write a recipe" path | NOT TESTED (would require emptying the library; forbidden) |

## Insets / IME
| Check | Result |
|---|---|
| Keyboard on the last step keeps the row visible | **FAIL** — B1 |
| Save reachable with keyboard open | PASS (top bar pinned) |
| Landscape (1:1 rotation) | Two panes appear, layout fine (`72`); IME same failure as B1, worse (`73`) |
| `wm size 1280x800` | PASS — library grid, detail two panes (`77`), editor two panes (`78`) |
| Settings restored | `wm size reset`, `wm density reset` (420), `accelerometer_rotation=1`, `user_rotation=0`; test JPEGs removed from `/sdcard/Pictures` |

## Regression sweep
Clip from the browser (structured page, LLM on emulator) → Replace → detail → Back → browser → Back → detail: PASS. Delete + Undo (tapped within the snackbar window) restores the row (`deletedAt` NULL): PASS. Delete without Undo removes the row and its picture folder: PASS. Model management screen: loads, "4 of 4 models · 1 installed": PASS. No `FATAL EXCEPTION` in `logcat-full.txt`.

## Bugs, ranked
**B1 — P2 — Focused step/ingredient row is not scrolled above the keyboard.** Tapping a step row near the bottom (portrait) or any row in a pane (landscape) opens the IME and the focused row stays at its old y (e.g. `[316,2226][901,2400]` with the keyboard top at ~1515), so the user types blind; in landscape the viewport is ~130 px tall. Repro: edit/create → scroll so "Steps" is mid-screen → tap step 1 (`47`, `51`, `73`). Likely cause: `OutlinedTextField` rows in the `LazyColumn` have no bring-into-view on focus and `Scaffold(contentWindowInsets = safeDrawing)` only shrinks the viewport. Fix: in `recipes/presentation/editor/components/EditableLineList.kt:263` (the row's `OutlinedTextField`, modifier at `:282`) add a `BringIntoViewRequester` and call `bringIntoView()` from `onFocusChanged` + a `LaunchedEffect(imeVisible)` (or `Modifier.imePadding()`/`imeNestedScroll` on the list), and `scrollToItem` the focused item in the `LazyColumn` state (`RecipeEditorScreen.kt` ~L225); consider `ImeAction`-based `windowSoftInputMode=adjustResize` check.
**B2 — P3 — `stateDescription` of the favourite heart not observable via uiautomator** (see above); add a Compose test asserting `SemanticsProperties.StateDescription` for `FavouriteToggle`.
**B3 — P2 (test infra) — `MigrationTest` cannot run**: kotlinx-serialization `AbstractMethodError` in `MigrationTestHelper` (Room testing pulls `androidx.room:room-migration` whose serialization runtime clashes with the project's Kotlin 2.4.21 / serialization version). Fix: in `gradle/libs.versions.toml`/`app/recipekeeper/build.gradle.kts` add an explicit `androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json(-core):<version matching the Kotlin plugin>")` (or align Room testing to the Room version), then rerun on a device without touching 5556's app. Until then the live-DB evidence above is the only migration proof.
**B4 — P3 (process) — QA template**: `connectedAndroidTest` uninstalls the app (see incident).
No P1 bugs found.

## Could not verify
Custom accessibility actions (Move up/down) and drag reorder; `stateDescription`; the exact un-heart exit animation; empty-library "Write a recipe"; `MigrationTest` pass; picture-file behaviour on the original 22 clipped recipes (files lost); remaining-clipped-recipe screens with real photos.
