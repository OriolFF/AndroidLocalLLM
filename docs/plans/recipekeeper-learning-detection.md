# Learning detection — design and contracts

Status: approved by the user 2026-10-22 ("implement it"). Streams: L1 (learning + storage), L2 (detector integration + reset action). Both on Sonnet; they start after `fixes/y1` (ES/CA/EL detection) is merged because they touch the same detector files.

## 0. Idea
When the fast detector finds nothing but the model extracts a recipe that passes our quality gates and the user keeps it, learn from that page so the next page like it is detected directly:
1. **Vocabulary** — unit tokens and heading words present in the recipe's ingredient/step lines and in the page's section headings that the built-in lists do not know → generic hints usable on any site.
2. **Site rules** — on this host, the heading text / heading level that wrapped the ingredients and the steps, and the quantity shape (number first, number after the name, number words, no units) → host-specific hints.
3. **Confidence** — hints gain confidence on each success and lose it on each failure (a hint-detected candidate whose extraction was rejected, cancelled or deleted within 10 minutes); below a floor they are dropped. All local, never uploaded. A "Reset learned detection" action clears them.

## 1. Domain contracts (`app/recipekeeper/.../clip/domain/detection/`, pure Kotlin)
```kotlin
enum class HintKind { UNIT, INGREDIENT_HEADING, INSTRUCTION_HEADING, QUANTITY_SHAPE, SECTION_LEVEL }
enum class QuantityShape { NUMBER_FIRST, NUMBER_AFTER, NUMBER_WORDS, NO_UNITS }

data class DetectionHint(
    val host: String?,          // null = generic (any site)
    val kind: HintKind,
    val token: String,          // lower-cased unit/word, shape name, or heading level "4"
    val confidence: Int,        // 0..100
    val successes: Int,
    val failures: Int,
    val lastSeenAt: Long,
)

data class DetectionHints(val generic: List<DetectionHint>, val forHost: List<DetectionHint>) {
    fun units(): Set<String>; fun ingredientHeadings(): Set<String>; fun instructionHeadings(): Set<String>
    fun quantityShapes(): Set<QuantityShape>; fun sectionLevels(): Set<Int>
    companion object { val EMPTY }
}

interface DetectionHintsSource { suspend fun hintsFor(host: String?): DetectionHints }          // read side (detector)
interface DetectionHintsStore {                                                                   // write side (learning)
    suspend fun reinforce(hints: List<DetectionHint>)   // upsert; successes+1, confidence = min(100, confidence + 25)
    suspend fun penalise(host: String?, kinds: Set<HintKind>, tokens: Set<String>)  // failures+1, confidence − 40, delete < 20
    suspend fun clear()
    fun count(): Flow<Int>
}

/** Derives hints from a successful extraction. Pure: no I/O. */
class DeriveDetectionHintsUseCase(private val knownUnits: Set<String>, private val knownHeadings: Set<String>) {
    operator fun invoke(snapshot: PageSnapshot, recipe: ClippedRecipe, candidate: ClipCandidate): List<DetectionHint>
}
/** Persists derived hints after a kept extraction. */
class LearnDetectionHintsUseCase(private val derive: DeriveDetectionHintsUseCase, private val store: DetectionHintsStore) {
    suspend operator fun invoke(snapshot: PageSnapshot, recipe: ClippedRecipe, candidate: ClipCandidate, detectedByHints: Boolean)
}
class PenaliseDetectionHintsUseCase(private val store: DetectionHintsStore) { suspend operator fun invoke(host: String?, usedHints: DetectionHints) }
class ResetDetectionHintsUseCase(private val store: DetectionHintsStore) { suspend operator fun invoke() }
```
Derivation rules (`DeriveDetectionHintsUseCase`):
- Units: for every ingredient line, the token following the leading number (or preceding/following a trailing number) that is alphabetic, 1–14 chars, not in `knownUnits` → `UNIT` generic hint. Require the same token in ≥ 2 lines of the recipe to avoid noise.
- Headings: the snapshot section whose text contains ≥ 60 % of the recipe's ingredient lines → its heading (lower-cased, trimmed, ≤ 40 chars) → `INGREDIENT_HEADING` (generic if it is a single word not in `knownHeadings`, host-specific otherwise); same for steps → `INSTRUCTION_HEADING`. Its `level` → `SECTION_LEVEL` host hint when level ≥ 4.
- Quantity shape: classify the ingredient lines (majority rule) → `QUANTITY_SHAPE` host hint.
- Learn only when: candidate payload is `WholePage` or `Text` (detector missed or was weak), recipe has ≥ 3 ingredients and ≥ 2 steps, `detectedByHints == false` or the hints were already used (then reinforce).

## 2. Data (`clip/data/detection/`, Room in `recipes/data`)
- Table `detection_hints(host TEXT NULL, kind TEXT, token TEXT, confidence INT, successes INT, failures INT, lastSeenAt INT, PRIMARY KEY(host, kind, token))` with index on `host`. **Database version 3**, `MIGRATION_2_3` creates the table; export `3.json`; migration test extended.
- `RoomDetectionHintsStore : DetectionHintsSource, DetectionHintsStore` (DAO queries: generic where host IS NULL and confidence ≥ 40; host rows for `host`; cap 200 rows per kind by `lastSeenAt`).
- Trigger: `RecipeKeeperClipSink.save(...)` calls `LearnDetectionHintsUseCase` after the row is written (on `dispatchers.io`, failures logged, never block the save). The sink needs to know whether hints were used: add `usedHints: Boolean` to `ClipSaveOptions` (feature domain, additive default `false`) — or carry it on `ClipCandidate.payload` metadata; pick the additive option that compiles for all implementors.
- Penalty triggers: `ExtractionError.ParseFailed` / `accept()` rejection for a hint-detected TEXT candidate, cancel during extraction, and delete of a hint-detected recipe within 10 min (the delete-with-undo use case commit path). Minimal viable: parse-fail/reject only; cancel/delete optional.

## 3. Detector integration (`RecipeKeeperClipConfig.detect`, L2)
- `detect(snapshot)` becomes hint-aware: `WebClipConfig.detect` is synchronous, so the hints are **loaded before** detection by the scanner path: add `suspend fun prepare(snapshot): Unit`? Simpler: `RecipeKeeperClipConfig(hintsSource)` with an internal `@Volatile var hints: DetectionHints` refreshed by a tiny `RefreshDetectionHintsUseCase(host)` the browser calls on `PageFinished` (feature `BrowserViewModel` already scans after load — it can call `config.onPageLoaded(host)` if `WebClipConfig` gains an optional `suspend fun onPageLoaded(host: String?) {}` default no-op). Choose this: additive default member in `WebClipConfig`, called by `ScanPageUseCase` before `detect`.
- Use: `quantityLine` built from `builtInUnits + hints.units()`; `ingredientsWord`/`instructionsWord` extended with learned headings; when `hints.sectionLevels()` contains 4/5 the capture already provides those sections (Y1); `QUANTITY_SHAPE.NUMBER_AFTER` enables the trailing-number regex, `NO_UNITS` lowers `MIN_QUANTITY_LINES` to count bare "name" lines under a learned ingredients heading (requires ≥ 5 lines), `NUMBER_WORDS` enables number-word patterns.
- Mark candidates found thanks to hints: `ClipCandidate.payload` is sealed in the feature; add `detectedByHints: Boolean = false` to `ClipCandidate` (additive) so the sink/penalty paths know.
- Reset action: Library top-bar overflow (`⋮`) → "Reset learned detection" with a confirm dialog and a snackbar; shows the count ("12 learned hints"). Labels in `strings_recipekeeper.xml`, rule 0.

## 4. Tests
- `DeriveDetectionHintsUseCaseTest`: Greek page fixture → learns `κ.σ.`/`γρ.`-style units and `υλικά` heading; Spanish number-after page → `NUMBER_AFTER`; no learning below the quality floor; known tokens not re-learned.
- `RoomDetectionHintsStore` logic via a fake DAO (JVM) + `MigrationTest` 2→3 (androidTest, manual install on 5556).
- Detector tests: a page that fails today detects after hints are injected; list page still empty with hints; penalty drops a hint below 20.
- Eval: `EvalPromptRenderingTest` unchanged (hints empty).

## 5. Streams
- **L1** owns `clip/domain/detection/**`, `clip/data/detection/**`, `recipes/data/{RecipeKeeperDatabase.kt,RecipeDao.kt (new DAO file preferred: DetectionHintDao.kt),Migrations}`, `schemas/**`, `clip/data/RecipeKeeperClipSink.kt`, `clip/di/ClipModule.kt`, `src/androidTest/.../recipes/data/MigrationTest.kt`, tests for the above.
- **L2** owns `clip/domain/RecipeKeeperClipConfig.kt` (+ its tests), `feature/webclipper/domain/{WebClipConfig.kt,ClipCandidate.kt,usecase/ScanPageUseCase.kt}` (additive members only) + feature tests, `recipes/presentation/library/**` (overflow + dialog), `recipes/presentation/RecipeKeeperLibraryLabels.kt`, `res/values/strings_recipekeeper.xml`, `recipes/di/RecipesModule.kt` (bind the reset use case), library tests.
- Lead merges L1 then L2, wires anything cross-cutting, runs the full verification, then a Sonnet QA pass on `emulator-5556` with 3 pages that fail detection today (clip via "Extract with AI", revisit → expect direct detection).
