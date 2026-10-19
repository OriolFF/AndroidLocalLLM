# F2 report — persistence v2, migration, pictures import, sink, share

Verified: `./gradlew :app:recipekeeper:testDebugUnitTest :app:recipekeeper:assembleDebug` → BUILD SUCCESSFUL, 135 unit tests, 0 failures; `compileDebugAndroidTestKotlin` compiles. `MigrationTest` was **not run** (QA runs it on emulator-5556).

## Files
- `recipes/data/RecipeKeeperDatabase.kt`: `version = 2`, `MIGRATION_1_2`, KDoc (v1 shipped → migrations from now on).
- `schemas/…/2.json` new (exported by the build; `1.json` untouched).
- `recipes/data/RecipeDao.kt`: `observeSummaries(favouritesOnly)`, `searchSummaries(query, favouritesOnly)`, `setFavourite(id, favourite)`.
- `recipes/data/RoomRecipeRepository.kt`: filter applied, `setFavourite`.
- `recipes/data/FileRecipePictureStore.kt`: `moveIn` numbers after the highest existing `NNN`; `delete(recipeId, paths)` removes only files directly inside the recipe folder (canonical paths, so `..` traversal and other recipes' files are ignored).
- `recipes/data/ContentResolverPictureImporter.kt` new: bounds pass → `inSampleSize` decode → EXIF orientation (framework `android.media.ExifInterface`, no new dependency) → scale to ≤ `maxLongEdgePx` → JPEG q85 via `.part` + rename; `Outcome.Failure` on any error; `sweep` walks `cacheDir/recipe-editor/` and removes stale files and empty dirs. `sampleSizeFor` is a public pure helper.
- `clip/data/RecipeKeeperClipSink.kt`: on `replaceId`, reads the old row and keeps `isFavourite`; always `origin = CLIPPED`, `editedAt = null`.
- `recipes/domain/usecase/ShareRecipeUseCase.kt`: `ShareRecipeUseCase(repository, templates: RecipeShareTemplates)`; CLIPPED → `clipped(title, url)`, CREATED → `created(...)` with bulleted/numbered lines. A **secondary constructor** taking the old `(title, url) -> String` lambda is kept only so the F1-owned `LibraryViewModelTest`/`RecipeDetailViewModelTest` still compile; delete it at merge once they pass real templates.
- `recipes/di/RecipesModule.kt`: `addMigrations(MIGRATION_1_2)`, `PictureImporter` single, `EDITOR_TEMP_DIR` qualifier (`const val EDITOR_TEMP_DIR = "editor-temp-dir"`, defined in this file, = `cacheDir/recipe-editor`), `ShareRecipeUseCase` built with `RecipeShareTemplates` from `recipeKeeperLibraryLabels(resources)` (`shareTemplate`, `shareCreatedTemplate`, `shareBullet`, `shareNumbered`) plus `recipekeeper_ingredients_header` / `recipekeeper_steps_header`. `ToggleFavouriteUseCase` was already registered by F0.
- `gradle/libs.versions.toml`: `androidx-room-testing`; `app/recipekeeper/build.gradle.kts`: `androidTestImplementation(libs.androidx.room.testing)`.

## Migration SQL
```sql
ALTER TABLE recipes ADD COLUMN isFavourite INTEGER NOT NULL DEFAULT 0;
ALTER TABLE recipes ADD COLUMN editedAt INTEGER;
ALTER TABLE recipes ADD COLUMN origin TEXT NOT NULL DEFAULT 'CLIPPED';
CREATE INDEX IF NOT EXISTS index_recipes_isFavourite ON recipes (isFavourite);
```
Matches the entity `@ColumnInfo` defaults (`"0"`, none, `"CLIPPED"`) and `2.json`.

## Tests
- `RecipePictureStoreAndConvertersTest` (+2 tests, converters test extended): numbering after the highest file (with a gap), `delete` ignores foreign/traversal/missing paths, `deleteAll` unchanged, summary row carries `isFavourite`/`origin`.
- `RecipeKeeperClipSinkTest` (new): Replace keeps `isFavourite`, resets `origin`/`editedAt`; Keep both is not a favourite.
- `ShareRecipeUseCaseTest` (new): clipped vs created, bullets and numbering, unknown id.
- `src/androidTest/.../recipes/data/MigrationTest.kt`: v1 DB with two rows + three pictures → v2, values kept, defaults applied, index present. Run only this class on emulator-5556.

## For the lead at merge
- Call `PictureImporter.sweep(now - 24h)` at app start in `RecipeKeeperApp` (not owned by F2).
- Remove the `ShareRecipeUseCase` secondary constructor once F1's tests use `RecipeShareTemplates`.
- Do not install this commit over a database built from the F0 commit (v1 with the new columns missing from the identity hash).
