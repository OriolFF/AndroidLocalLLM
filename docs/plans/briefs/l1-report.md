# L1 report — learning detection: hints domain, storage, learning hook

Branch `learning/l1`. Contracts of `recipekeeper-learning-detection.md` §1/§2 implemented as frozen.

## Verification
- `./gradlew :app:recipekeeper:testDebugUnitTest :app:recipekeeper:assembleDebug :app:recipekeeper:assembleDebugAndroidTest` — green, 232 unit tests, 0 failures.
- `MigrationTest` (migrate1To2 + new migrate2To3) run on `emulator-5556` by `adb install -r` of the arm64 APK + `install -t -r` of the test APK + `am instrument -e class …MigrationTest`: `OK (2 tests)`. No `connectedDebugAndroidTest`, no `pm clear`. (The installed app was upgraded in place with `-r`; its data is kept and its DB migrates 2→3 on next launch.)
- `schemas/…/3.json` exported and committed.

## Final table SQL (Room `3.json` / `MIGRATION_2_3`)
```sql
CREATE TABLE IF NOT EXISTS `detection_hints` (`host` TEXT NOT NULL, `kind` TEXT NOT NULL, `token` TEXT NOT NULL,
  `confidence` INTEGER NOT NULL, `successes` INTEGER NOT NULL, `failures` INTEGER NOT NULL, `lastSeenAt` INTEGER NOT NULL,
  PRIMARY KEY(`host`, `kind`, `token`));
CREATE INDEX IF NOT EXISTS `index_detection_hints_host` ON `detection_hints` (`host`);
```
**Deviation from the plan text:** `host` is `NOT NULL` and `''` means generic. A NULL in a composite primary key is distinct per row in SQLite, so upserting the same generic hint twice would duplicate it; Room also rejects nullable PK columns. The domain model still uses `host: String? = null`; the store maps `null ⇄ ''`.

## `ClipSaveOptions` flag (additive, `feature/webclipper/.../domain/ClipSink.kt`)
`ClipSaveOptions(…, val usedHints: Boolean = false)`. The sink passes `options.usedHints` to `LearnDetectionHintsUseCase`.

## Exact DAO API (`recipes/data/DetectionHintDao.kt`, entity `DetectionHintEntity(host, kind, token, confidence, successes, failures, lastSeenAt)`)
```kotlin
suspend fun genericAtLeast(minConfidence: Int): List<DetectionHintEntity>   // host = '' AND confidence >= min
suspend fun forHost(host: String): List<DetectionHintEntity>
suspend fun find(host: String, kind: String, token: String): DetectionHintEntity?
suspend fun upsert(hints: List<DetectionHintEntity>)                         // @Upsert
suspend fun delete(host: String, kind: String, token: String)
suspend fun trimKind(kind: String, keep: Int)                                // keep newest `keep` by lastSeenAt
suspend fun deleteAll()
fun observeCount(): Flow<Int>
```
`RecipeKeeperDatabase` v3 adds `detectionHintDao()`; `RecipesModule` binds `DetectionHintDao` and `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`.

## Files
- domain (`clip/domain/detection/`): `DetectionHints.kt` (`HintKind`, `QuantityShape`, `DetectionHint`, `DetectionHints`, `DetectionHintsSource`, `DetectionHintsStore`), `DeriveDetectionHintsUseCase.kt`, `DetectionHintUseCases.kt` (`LearnDetectionHintsUseCase`, `PenaliseDetectionHintsUseCase`, `ResetDetectionHintsUseCase`), `BuiltInVocabulary.kt`.
- data: `clip/data/detection/RoomDetectionHintsStore.kt` (`DetectionHintsSource` + `DetectionHintsStore`), `recipes/data/{DetectionHintEntity,DetectionHintDao,RecipeKeeperDatabase}.kt`.
- `RecipeKeeperClipSink`: new optional ctor params `learning: LearnDetectionHintsUseCase?`, `learningScope: CoroutineScope?`; after `repository.upsert` it launches the learn call on `learningScope` (`dispatchers.io`), exceptions logged and swallowed — the save never waits.
- `ClipModule`: store (as source and store), derive/learn/penalise/reset use cases, sink wiring. Strings: none (no UI in L1).

## Behaviour notes
- Derive emits hints at confidence 25 / counters 0; `reinforce` adds the success (25 → 50 on first success so a new hint is readable at once; a second → 75; cap 100). A new hint is penalised from 50 to 10 → deleted.
- `reinforce` caps 200 rows per kind (newest `lastSeenAt`); writes serialised by a mutex.
- Units: token after a leading number (must be followed by more text, so `3 eggs` is no unit) or after a trailing number, ≥ 2 lines, 1–14 chars, not known. Headings/levels/shape per plan §1; a known heading is never learned; multi-word headings and level/shape hints are host-specific and skipped when the URL has no host.
- `LearnDetectionHintsUseCase` ignores `Structured` candidates; `detectedByHints` does not change what is derived (reinforce is the same upsert), it is part of the contract for L2/logging.
- **Host key** used for learning and expected by `hintsFor(host)`: `TextCleaner.host(snapshot.url)` (no `www.`/`m.`).
- `PenaliseDetectionHintsUseCase(host, usedHints)` penalises generic rows under host `null` and host rows under `host`.

## What the lead must do at merge (L2 side)
1. After L2 adds `ClipCandidate.detectedByHints`, set it into the options in `feature/webclipper/.../usecase/ClipUseCase.kt` (the `ClipSaveOptions(...)` at ~line 202): `usedHints = candidate.detectedByHints`. L1 did not touch it (not owned; the field does not exist on this branch).
2. Call `PenaliseDetectionHintsUseCase` from the parse-failed / accept()-rejected path for hint-detected candidates (penalty triggers are L2/lead wiring; cancel/delete optional).
3. `BuiltInVocabulary` duplicates `RecipeKeeperClipConfig`'s unit and heading word lists (those are private to L2's file). If L2 exposes them, point `ClipModule` at those and delete the copy.
4. `RecipesModule` was touched by L1 only for the DAO + migration (L2 adds the reset binding there).
