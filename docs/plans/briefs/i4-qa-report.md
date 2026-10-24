# I4 QA report — input sources (chooser, images clip, share target) on emulator-5556 (branch `inputs/i4-qa`, 2026-10-24)

Build under test: the debug arm64 split the lead installed from `recipekeeper/dev` @ `273e509` (`IMAGES_SOURCE = true`). Evidence (screenshots `NN-*.png`, uiautomator dumps, logcat) is in the scratchpad `qa7/` dir: `/private/tmp/claude-501/-Users-oriol--herdr-worktrees-AndroidLocalLLM-inputs-i4-qa/0aed20cf-bfbc-4bab-a928-6d13428a58c8/scratchpad/qa7/` (not committed). No code edits, no `pm clear`, no uninstall of the app, no `connectedDebugAndroidTest`.

**Verdict: no P1. Three P2 (share-receiver crash on an ungranted URI, "finish the current clip" snackbar is never visible, chooser sheet does not scroll in short windows). The images feature works end to end and is fast on the emulator.**

## Timings (every model run; CPU, Gemma 4 E2B, vision CPU, budget 280)

TTFT = the moment "Reading images" flips to Done / "Extracting recipe" becomes Active (first streamed token). It was read from screenshots taken every ~3 s, so it is a range. Totals come from the tap on the picker's Done/"Extract recipe" to the engine log `Generation done`.

| Run | Input | Reading → Extracting (TTFT) | Generation done / saved | Notes |
|---|---|---|---|---|
| 1 | 1 picture (`card.png`) | 3–6 s | 15:04:43.5, started ≈15:04:31 → **≈12 s**; detail on screen at ≈15 s | `conversation tokens=962`; vision encoder loaded from the xnnpack cache in 0.1 s; recipe "Fluffy Buttermilk Pancakes", 9 ingredients, 6 steps, all correct |
| 2 | 3 pictures (ingredients shot, steps shot, card) | 4–10 s | 15:08:22 → 15:08:40.4 = **≈18 s** | `tokens=1523`; result built from the two browser shots (title "Love & Lemons"), the unrelated card was ignored |
| 3 | 3 pictures (same set) | 6–9 s (hint "9s" under Extracting) | 15:18:09 → 15:18:24 = **≈15 s** | `tokens=1483`; recipe "Fluffy Buttermilk Pancakes" |
| 4 | 1 picture, Settings screenshot | — | `NO RECIPE`, failure screen in < 25 s | see Failures |
| 5 | 1 picture (ingredients shot, cropped) | — | ≈15 s | saved, **"Possibly incomplete"**, steps "P." / "dish or your brownies will overcook…" (the picture only showed half a step list) |

Comparison with `docs/research/screenshot-extraction-eval.md`: the eval predicts 15–20 s for one screenshot and 25–40 s for 3–5 on a flagship phone; the emulator (CPU, `arm64.nosme`, M4 host) is **faster** (12 s / 15–18 s). Nothing came near the 5-minute threshold, so no real-phone repeat was needed. No `SIGILL`/`SIGSEGV`/OOM in the run logs (see Logcat).

## Checklist (plan §5 I4 step 2)

### Chooser
| Check | Result |
|---|---|
| "+" opens the sheet; title, subtitle, 4 rows, copy | **PASS** (`02-chooser.png`). Copy matches the UX spec §1.5 word for word |
| Voice row dimmed with "Coming soon" | **PASS** visually (`02`). uiautomator exposes the texts "By voice" / "Dictate a recipe" / "Coming soon" but **no node for the disabled row** (`enabled=false` and `stateDescription` are not observable through uiautomator). Verified instead by the I3 Compose test, run by hand: `SourceChooserSemanticsTest` **5/5 OK** (`am instrument`, `adb install -t -r` of the androidTest APK; the app APK and model were not touched) |
| Tapping the voice row does nothing | **PASS** (`03`, sheet stays) |
| "Write it yourself" → empty editor | **PASS** (`04`) |
| "From the web" → browser | **PASS** (`05`, google.com start page) |
| Empty-library "More ways to add…" | **NOT VERIFIABLE** (library has recipes; no fresh AVD) |

### Images availability
| State | Result |
|---|---|
| Gemma 4 E2B selected and installed → Available, row taps into the picker | **PASS** |
| Text-only model selected (a 2 MB dummy `gemma-4-E2B-it-q4.litertlm` was created to make the q4 variant "installed" and selected through "Use this model") → supporting text "Your current model can't read images", tap → dialog "This model can't read images / Gemma 4 E2B IT (q4 quantised) only reads text. Pick a model marked "reads images" in Models…" with Cancel / **Open models** (opens model management) | **PASS** (`60`, `61`) |
| Model file moved away (`mv` to `.qa7bak`, app restarted) → "Choose a model first" → tap → model management ("4 of 4 models · 0 installed") | **PASS** (`54`, `55`) |
| State restored: file moved back, dummy deleted, E2B "Active" again (`63`) | done |

### Picker path
| Check | Result |
|---|---|
| Photo Picker (ordered selection, limit 3) | **PASS**; 4 PNGs pushed to `/sdcard/Pictures` + MediaStore scan |
| Pick 1 → straight to extraction (no review) | **PASS** (run 1). Source strip "From your photos · 1 picture"; single-picture hint shows the privacy sentence as a second line (`09-run1-t6.png`) |
| Pick 3 → review: numbered tiles, counter "3 of 3 pictures", privacy and time rows | **PASS** (`18`). Cap is **3** (I2 `ImagePolicy`), not the 5 of the spec's default; the counter reads "x of 3" and the Add tile is hidden at the cap |
| Move right / Move left (buttons) | **PASS** (`19`, selection follows the tile; ‹ disabled on the first tile) |
| Remove | **PASS** (`21`, "2 of 3 pictures", Add tile appears) |
| Add more (1 slot left) → picker → picture appended | **PASS** (`22`, `23`). The picker opened in its multi-select UI (Done button) with the selection limit enforced at 1 |
| Phases "Reading images" Active until the first token → Extracting → Saving → detail | **PASS** (V11). Reading stays Active with the hint "Reading your pictures on your phone — this takes longer than a page · Ns" and flips to Done when Extracting becomes Active (`24-strip-a.png`) |
| Custom accessibility actions (Move left/right/Remove) | **NOT VERIFIABLE** with uiautomator; present in code (`ImageTile.kt:78-85`). No Compose test exists for the review screen |

### Share path
Real Photos app sharing is not available; the share was driven from the system **Files (DocumentsUI)** app, which is a real sender that grants read access.
| Check | Result |
|---|---|
| Files → select 2 images → Share → "Recipe Keeper" in the share sheet ("Sharing 2 images") → the review appears with "Picture 1 of 2 / 2 of 2", counter "2 of 3 pictures" (cold start of the process) | **PASS** (`39`) |
| Back from that review → library, temp dir `cache/webclipper` empty | **PASS** |
| `am start -a SEND text/plain` with a URL → browser opens that page (also tried while the app was running, and on top of model management) | **PASS** (`06`, `41`). It is pushed on top of whatever screen is open (the stack was Library → Models → Browser, so Back returns to Models; consistent with the spec) |
| URL share from Chrome | NOT TESTED (no Chrome share exercised; used `am start` with an explicit component) |
| Share while an extraction runs → nothing navigates | **PASS** (stays on the extraction, run completes) |
| … and the snackbar "Finish the current clip first" shows | **FAIL — P2-2**: not visible in any of 3 attempts (frames 0.3–4 s after the share, `40`, `42`, `44`) |
| Share arrives while the app is in the recents / singleTop | **PASS** (the 15:13 share reused task `t111`, one `MainActivity`) |
| `adb` shares of a `content://media` URI | the shell cannot grant the URI, so `ShareReceiverActivity` crashed — see **P2-1** (an adb artefact for the trigger, but a real robustness gap) |

### Saved result
| Check | Result |
|---|---|
| Detail source row | **PASS with a copy deviation**: icon `PhotoLibrary`, "From your photos · Check the amounts" and "Added Oct 24, 2026". Spec §2.7 says "From your photos" + Added; the extra "Check the amounts" is the I3 label `fromYourPhotosCheckAmounts` (P3-4) |
| No Open original / Re-extract | **PASS**: overflow = Share recipe, Keep screen on, Delete (`11`) |
| Share text is the full recipe | **PASS**: title, "Ingredients:" list, "Steps:" list (`12`) |
| Tile meta and description | **PASS**: "From your photos · 25 min", content description "FLUFFY BUTTERMILK PANCAKES, from your photos" |
| Editor without a source row | **PASS** (`13`; cover = the kept picture) |
| Delete + Undo | **PASS** (confirm dialog "Delete this recipe?", snackbar "Recipe deleted / Undo", Undo restores the row and keeps the picture folder) |
| Pictures kept, temp dir empty after save | **PASS** (`files/recipes/<id>/001.jpg`, `cache/webclipper` empty) |
| Duplicate screenshots | not deduplicated (by design, S7): re-running the same picture creates a second recipe |

### Failures
| Check | Result |
|---|---|
| Non-recipe screenshot (Settings) → "No recipe found in these pictures" + "Pick different pictures" / "Back to your recipes"; raw output expander shows `NO RECIPE` | **PASS** (`26`, `27`) |
| "Pick different pictures" → review with the **same** image | **PASS** (`28`: "Picture 1 of 1", "1 of 3 pictures", Add more) |
| Cancel from the review → library, temp dir empty | **PASS** |
| Stop mid-run → library, temp dir empty, nothing saved | **PASS** (`29` showed `images-<id>-0/1.jpg` in `cache/webclipper` while running; empty after Stop; no recipe added) |
| Kill the process mid-review (`am kill` after Home) and reopen from Recents → review restored with the same pictures | **PASS** (`31`, `32`; new pid; temp files `images-9cf47c56-0/1.jpg` present, deleted on Cancel) |

### Insets / adaptive
| Check | Result |
|---|---|
| Review at 1280x800 @ 240 dpi (≈853x533 dp, set while the review was open) | **PASS** (`52`): tiles at the start, "Extract recipe" bottom bar above the gesture bar, nothing covered |
| Extraction at 1280x800 @ 240 dpi | **PASS**: two panes, phases left, draft card right (`51b`) |
| Sheet at 1280x800 @ 240 dpi | **PASS**: centred, ≈640 dp max width (`50`), four rows |
| Sheet at 1280x800 @ 420 dpi (≈305 dp tall) | **FAIL — P2-3**: only the first two rows fit; rows "By voice" and "Write it yourself" are cut off and the sheet does **not** scroll (`47`, `48`) |
| Portrait phone: gesture bar never covers "Extract recipe" | **PASS** (`18`). At 2400 px the time-hint row sits half under the bottom bar until scrolled (P3-1) |
| `wm size reset`, `wm density reset` | done (1080x2400, 420) |

### TalkBack / a11y (uiautomator dump)
- Sheet (`02-chooser.xml`): title and subtitle text nodes; the clickable rows are separate unlabeled merged nodes at `[0,1539][1080,1728]` etc. (uiautomator does not surface Compose merged labels, the Compose test covers the semantics); close scrim `content-desc="Close sheet"`, `Drag handle`.
- Review grid (`18`): tiles `Picture 1 of 3`, `Remove picture 1 of 3` (≥ 42 px visible, 48 dp hit), `Move left` / `Move right`, number badges, counter, privacy/time rows, `Cancel` (close icon), `Extract recipe`. `stateDescription` ("Selected") and custom actions are not visible to uiautomator.
- Extraction: phase texts and hints are separate nodes; "Keep this picture" checkboxes have descriptions.

### Regression
| Check | Result |
|---|---|
| Web clip end to end (loveandlemons brownies, shared URL → page → "Clip recipe" FAB) | **PASS**: saved with 10 ingredients, `Open original` present. The page has JSON-LD and the extraction screen said "Read directly from the page's recipe data", so the model did not run; "Reading flips at the first token" for **web** was therefore **not exercised on device** (unit test `ClipUseCaseTest` covers it) |
| Duplicate dialog on a re-clip | **PASS**: "Already in your library — You clipped this page before. Replace… Any edits you made to it are lost." Cancel / Keep both / Replace |
| Model management "Reads images" on Gemma | **PASS** (Recipe Keeper `58`; first pill on E2B only) |
| `:app:recipes` / `:app:medical` model lists unchanged apart from the tag | **PASS**: rebuilt both from this worktree (`assembleDebug`), installed (`recipes` with `-r`, `medical` fresh). Both show "Reads images" as the first pill on Gemma 4 E2B IT only; Gemma q4, Phi-3.5 and Llama 3.2 cards have exactly their previous tags (`instruction-tuned, quantised, compact, google` / `instruction-tuned, microsoft, reasoning` / `chat, meta, general-purpose`); the 4-of-4 header is unchanged. The Gemma q4 variant carries no vision capability in the catalog (by design) |

## Defects, ranked

### P1
None.

### P2
**P2-1 — `ShareReceiverActivity` crashes (process dies) when the sender did not grant the stream URI.** `FATAL EXCEPTION … Unable to start activity …ShareReceiverActivity … SecurityException: UID 10227 does not have permission to content://media/external/images/media/23` (thrown out of `startActivity(forwardIntent(...))` because the forwarded intent adds `FLAG_GRANT_READ_URI_PERMISSION` + a `ClipData` for a URI the receiver itself cannot read). Repro: `adb shell am start --grant-read-uri-permission -a android.intent.action.SEND -t image/png --eu android.intent.extra.STREAM content://media/external/images/media/<id> -n com.llmlocal.recipekeeper/.app.ShareReceiverActivity` (the shell has no grant to hand out). Real senders (Files, Photos) grant correctly, but the activity is `exported`, so any app or a sloppy sender can crash it and the whole process (an in-flight extraction dies with it). Fix: `ShareReceiverActivity.kt:20-21` wrap `startActivity(forwardIntent(...))` in `try { } catch (e: SecurityException) { }` (and consider `Intent.FLAG_GRANT_READ_URI_PERMISSION` only when `checkCallingUriPermission` succeeds); `finish()` in `finally`. Add a unit/instrumented case with an ungranted URI.

**P2-2 — The "Finish the current clip first" snackbar is never visible while an extraction runs.** The inbox guard works (the share is consumed, nothing navigates) but `RecipeKeeperNavHost.kt:115` calls `snackbarHostState.showSnackbar(...)` on the shared host and `WebClipperExtractionScreen`'s `Scaffold` (`WebClipperExtractionScreen.kt:133`) has no `snackbarHost`, so nothing renders it on the extraction screen. Three attempts with frames at 0.3–4 s after the share (`40`, `42`, `44`) show no snackbar. Fix: give the screen's `Scaffold` a `SnackbarHost(snackbarHostState)` (the route already receives the state) or render the NavHost-level host above every destination; then add a UI assertion. (The same missing host can swallow "Clip cancelled"/"Saved" if their navigation hasn't landed on a destination with a host yet.)

**P2-3 — `SourceChooserSheet` content does not scroll, so rows are unreachable in short windows.** At 1280x800 @ 420 dpi (305 dp tall, a phone in landscape with a bar) only "From the web" and "From images" are shown; swiping inside the sheet does nothing, so "Write it yourself" cannot be reached (`47`, `48`). `SourceChooserSheet.kt:78` is a plain `Column`; the spec (§1.2) assumed "scrollable by default", which the M3 sheet does not provide. Fix: `Modifier.verticalScroll(rememberScrollState())` on the content column (keep the title above the scroller if wanted) and a Compose test at a 300 dp height.

### P3
1. **Review screen at rest, portrait phone**: the time-hint row ("Reading pictures takes longer…") sits half under the bottom bar until the grid is scrolled (`18`, `20`); add bottom `contentPadding` equal to the bar height. On wide windows the tile grid is left-aligned while the button and info rows are centred (`ImagesReviewScreen.kt` grid has no `widthIn`/centring; spec §2.4 says cap and centre).
2. **Failure screen shows a misleading state**: "Saving pictures" is a green check and the keep-picture strip with a checked box remains under "No recipe found" (`26`). The phase should stay Pending and the strip should be hidden on failure (`ClipUseCase` marks Pictures Done up front for images).
3. **Empty review flashes behind the picker**: while the Photo Picker is open the screen underneath already shows "Clip from images / Put the pictures in reading order" with an empty dashed Add tile (`07-picker.png`); show the Preparing skeleton or a blank surface until pictures arrive.
4. **Detail source copy differs from the spec**: "From your photos · Check the amounts" (`fromYourPhotosCheckAmounts`) vs spec §2.7 "From your photos". Either update the spec or drop the suffix; keep consistency with the tile meta, which says only "From your photos".
5. **Dialog copy vs tag**: the dialog says `Pick a model marked "reads images"` while the tag is "Reads images".
6. **Share target for plain text also lists Recipe Keeper for its own "Share recipe"** (`12`): harmless no-op, but it is noise in the share sheet.
7. **`ShareIntentParser.streamUris`** logs `W/Bundle … Key android.intent.extra.STREAM expected ArrayList but value was Uri` when a sender uses `SEND_MULTIPLE` with a single Uri; it returns an empty list, so such a share is silently dropped. Accept a single `Uri` under `SEND_MULTIPLE` too.
8. **Picker odd behaviour at 1280x800 @ 240 dpi**: with three free slots the picker returned after the first tap (one picture, auto-extraction) and my other two taps landed on the extraction screen. Not reproduced at the default size. Unresolved, noted for a real-tablet pass.
9. **Model quality**: mixing an unrelated recipe card into a 3-picture set silently drops it; a half-visible step list yields a nonsense step ("P.") with the "Possibly incomplete" chip (the chip works as designed). The review step could warn about pictures with very little text; Phase 2.

## Copy review (UX spec §1.5 / §2.9)
Observed strings on device match the spec for: chooser (title, subtitle, 4 rows + hints, "Coming soon", "Choose a model first", "Your current model can't read images", dialog title/body/"Open models"), review ("Clip from images", "Put the pictures in reading order", "Ingredients first, then the steps.", "N of 3 pictures", "Picture i of n", "Remove picture i of n", "Move left/right", "Add more", privacy and time rows, "Extract recipe"), extraction ("From your photos · N pictures", "Reading images", the extracting hint, "No recipe found in these pictures" + message, "Pick different pictures", "Back to your recipes", "Show raw output"), model management ("Reads images"). Deviations: P3-4 (detail source line), P3-5. **Literal scan** (`Text("`, `text = "`, `contentDescription = "`, `showSnackbar("`, `Toast…("`) over `:feature:webclipper`, `:feature:modelmanagement`, `:core:web` and `:app:recipekeeper` main sources: no user-visible literal in the new images/chooser/share code; the only hits are pre-existing and not copy (`ClipData.newPlainText("url", …)`, empty `EditableLine` text, `"$percent%"`, size formatting). No P2 copy defects.

## Logcat
The only `FATAL EXCEPTION`s in the session are the two `ShareReceiverActivity` `SecurityException`s of P2-1 (both adb-triggered, 15:13). No `SIGILL`, `SIGSEGV`, `OutOfMemory`, ANR or LMK in the vision runs (engine log: `Engine ready … vision` path loads the vision encoder/adapter from the xnnpack cache in < 0.2 s, `Generation done (conversation tokens=962/1523/1483)`, `Waiting for a blocking GC NativeAlloc` 12 ms once). The logcat buffer rolled before the final dump (`logcat-tail.txt` holds only the tail).

## State restored on emulator-5556
- Library: the four original recipes (Galletas, Crepes, Greek biscuits, Best Homemade Brownies) verified in the DB (`deletedAt` empty) and their four picture folders; every recipe created by QA deleted, `cache/webclipper` empty. The "Keep both" brownies clip was not in the library or DB afterwards (the DB holds exactly the four originals), so it was either removed by my cleanup loop or not persisted; not investigated.
- Model: `gemma-4-E2B-it.litertlm` in place and selected ("Active"); the dummy q4 file removed.
- `wm size`/`wm density` reset; the four `qa7_*.png` pictures, their MediaStore rows, the `/sdcard/g*.png` screenshots and `/sdcard/ui*.xml` removed.
- Packages: `com.llmlocal.recipe` was reinstalled with `-r` (now the build from this branch, data kept); `com.llmlocal.medical` was installed for the model-list check and uninstalled again (it had no model or data); `com.llmlocal.recipekeeper.test` was updated by `adb install -t -r` (it was already installed); `com.llmlocal.recipekeeper` itself was never reinstalled.

## Could not verify
Empty-library "More ways to add…"; accessibility custom actions and `stateDescription` through a screen reader (uiautomator cannot show them; chooser semantics covered by the 5 passing Compose tests, no review-screen test exists); Chrome URL share; sharing from the Photos app (Files used); web "Reading flips at first token" on a page that goes through the model (the test page took the structured path); timings on a real phone (not needed).
