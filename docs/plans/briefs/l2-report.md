# L2 report — learning detection: detector integration + reset action

Branch `learning/l2`. Verified: `./gradlew :feature:webclipper:testDebugUnitTest :app:recipekeeper:testDebugUnitTest :app:recipekeeper:assembleDebug` green (all pre-existing tests unchanged and passing).

## What changed
**Feature (additive only)** — `feature/webclipper/.../domain/`
- `WebClipConfig.onPageLoaded(host: String?)` — `suspend`, default no-op.
- `ClipCandidate.detectedByHints: Boolean = false` (last parameter).
- `ScanPageUseCase.invoke` calls `config.onPageLoaded(host)` after a successful capture, right before `detect`. Host is normalised like `TextCleaner.host` (`www.`/`m.`/`mobile.` dropped, lower-case); `null` when the URL has no host.

**Detector** — `clip/domain/RecipeKeeperClipConfig.kt`
- New last constructor parameter `hintsSource: DetectionHintsSource = DetectionHintsSource.NONE` (returns `DetectionHints.EMPTY`).
- `onPageLoaded` loads hints (source failure → EMPTY) and swaps in a private `Lexicon` (volatile). `fun currentHints(): DetectionHints` exposes what detection is using (for the penalty path).
- Lexicon = built-ins + learned: `UNIT` tokens extend the unit alternation; `INGREDIENT_HEADING`/`INSTRUCTION_HEADING` extend the heading words; `NUMBER_AFTER` adds a bare short name + trailing-number shape (`Harina 200`; sentence punctuation excluded so prose ending in a number does not match); `NUMBER_WORDS` adds number word + any word; `NO_UNITS` counts ≥ 5 short sentence-less lines under a *learned* ingredients heading. `SECTION_LEVEL` needs nothing (Y1 capture already yields levels 4/5).
- A TEXT candidate the built-in lexicon alone would not have found is flagged `detectedByHints = true`. STRUCTURED is never flagged.
- Public companion `quantityLine`/`ingredientsWord`/`instructionsWord` unchanged (built-in only).
- **accept() fix (Y1 finding):** `stepsCoverMethod` applies the strict 60 % coverage / half-the-paragraphs check only when an instructions heading (built-in or learned) is in the rendered text. Otherwise it compares against all text after the last quantity line and rejects only if steps cover < 30 % of its words **and** there are < 3 steps. Extra guard: a reference of < 25 distinct words is too thin to compare, so the result is believed (needed to keep the existing "needs two quantity lines or two steps" test green).

**Reset action (library)**
- Top-bar overflow `⋮` (content description = existing `moreOptions`) → "Reset learned detection" with the hint count as a second line; disabled while the count is 0. Confirm `AlertDialog` → clear → snackbar "Learned detection reset".
- MVI: `LibraryIntent.{RequestResetDetection, DismissResetDetection, ConfirmResetDetection}`, `LibraryEffect.DetectionReset`, `LibraryState.{learnedHintCount, resetDetectionDialogOpen}`.
- `LibraryViewModel` gained `observeLearnedHints: ObserveLearnedHintCountUseCase` and `resetDetection: ResetDetectionHintsUseCase` (use cases only).
- New `recipes/domain/usecase/ObserveLearnedHintCountUseCase(store)` (`store.count()`).

**Labels / strings (rule 0)** — `RecipeKeeperLibraryLabels` + `rememberRecipeKeeperLibraryLabels` factory + `TestRecipeKeeperLibraryLabels`; keys in `strings_recipekeeper.xml`: `recipekeeper_reset_detection`, plural `recipekeeper_learned_hints_count`, `recipekeeper_reset_detection_title`, `recipekeeper_reset_detection_message`, `recipekeeper_reset_detection_confirm`, `recipekeeper_reset_detection_done`.

## Stand-in contracts — what the lead must do at merge
`clip/domain/detection/DetectionHints.kt` is **my stand-in** for L1's package (same names and signatures as plan §1: `HintKind`, `QuantityShape`, `DetectionHint`, `DetectionHints` (+ `isEmpty`), `DetectionHintsSource` (+ `NONE`), `DetectionHintsStore`, `NoOpDetectionHintsStore`, `ResetDetectionHintsUseCase`). L1 owns this package: **keep L1's files, delete mine** if the types collide. L2 relies on two members beyond the plan text: `DetectionHints.isEmpty` (trivial: both lists empty — add to L1's class or replace the use in `RecipeKeeperClipConfig.onPageLoaded` with `generic.isEmpty() && forHost.isEmpty()`) and `DetectionHintsSource.NONE` (default parameter; replace with a local `EMPTY`-returning object if L1 lacks it).

## Wiring the lead must do (L1's store → L2)
1. `clip/di/ClipModule.kt` (L1's file): pass the store as the source —
   ```kotlin
   single<WebClipConfig<ClippedRecipe>> {
       RecipeKeeperClipConfig(
           untitledTitle = androidContext().getString(R.string.recipekeeper_untitled_recipe),
           hintsSource = get<DetectionHintsSource>(),
       )
   }
   ```
   and bind `single { RoomDetectionHintsStore(...) }` as both `DetectionHintsSource` and `DetectionHintsStore` (`bind`).
2. `RecipesModule` (mine) already binds `ObserveLearnedHintCountUseCase` and `ResetDetectionHintsUseCase` with `getOrNull<DetectionHintsStore>() ?: NoOpDetectionHintsStore`, so the app runs before the store exists. Once L1's store is bound, replace both with `get()`.
3. Penalty / learn paths: `config.currentHints()` returns the hints detection used; a candidate's `detectedByHints` says whether to pass them to `PenaliseDetectionHintsUseCase` / `LearnDetectionHintsUseCase(..., detectedByHints)`. L2 does not add `usedHints` to `ClipSaveOptions` (sink is L1's).
4. No `:feature:webclipper` change needed in `BrowserViewModel`: `ScanPageUseCase` already runs on page finish / FAB tap, so hints load per scan.

## Tests added
- `LearnedDetectionTest` (app): page missed today detects after hints injected and is flagged; built-in finds not flagged; learned headings; `NUMBER_AFTER`, `NUMBER_WORDS`, `NO_UNITS` (≥ 5 lines, needs learned heading); list/category pages stay empty with hints; empty hints (penalised away) restore built-in behaviour; failing source → built-ins.
- `RecipeKeeperClipConfigTest`: cronica-shaped regression (ingredients, two short prose paragraphs, sub-heading, 15 method paragraphs → 15-step output accepted; 3 short steps accepted; 2 thin steps rejected) and headed-method stays strict.
- `LibraryViewModelTest`: count follows store; request/dismiss; confirm clears + closes + emits `DetectionReset`.
- `ScanPageUseCaseTest` (feature): `onPageLoaded` called with normalised host before `detect`; not called on capture failure; `null` host for `about:blank`.
- Fakes: `FakeDetectionHintsSource`, `FakeDetectionHintsStore`, `hint()/hintsOf()/shapeHint()` helpers; `FakeWebClipConfig` records `onPageLoaded`.

## Not done / notes
- No device check of the overflow menu (no device work per brief); dropdown + dialog follow the tile-menu / detail-delete patterns.
- `DeriveDetectionHintsUseCaseTest`, store/migration tests belong to L1.
