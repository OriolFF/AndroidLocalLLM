# Recipe Keeper — Favourites, Edit recipe, Create recipe: implementation plan

Status: PLAN (nothing implemented yet). Written 2026-10-19 from the repo state at `3f424c8` (`recipekeeper/main`: R1–R4 architecture refactor + S1–S4 strings migration merged). Companion: `recipekeeper-features-ux-spec.md` (screens, states, labels, decisions D1–D12). Style and conventions follow `recipekeeper-app-plan.md`: contracts first, parallel streams with disjoint file ownership, a report per stream, QA last.

Rules every stream honours (CLAUDE.md Architecture rules): **0** no user-visible string literal in Kotlin (resources + label classes without defaults); **1** ViewModels take use cases only (plus `DispatcherProvider`, `SavedStateHandle`, labels/config, route params); **2** `presentation / domain / data / di` packages, `presentation → domain ← data`, `domain` pure Kotlin.

---

## 0. What we are building

1. **Favourites** — `isFavourite` flag on a recipe; heart toggles on library tiles and the detail top bar (+ tile menu item); a "Favourites" `FilterChip` row in the library that ANDs with search; "No favourites yet" empty state. (UX spec §1)
2. **Edit recipe** — pencil on the detail (and tile menu) opens `recipe/{id}/edit`: one editor screen for title, description, servings, prep/cook/total, reorderable ingredient and step lists with paste splitting, notes, pictures (keep/remove/add from the photo picker, cover), read-only source. Discard-changes dialog, inline validation, `editedAt`, checked ingredients remapped. (UX spec §2)
3. **Create recipe** — "+ New recipe" top-bar action (and empty-state button) opens `recipe/new` in the same editor; `origin = CREATED`, no source; created recipes render with "Created by you" and share their full text. (UX spec §3)
4. Room schema **v1 → v2 with a migration** (the v1 DB exists on the QA emulator and the real phone).

Out of scope (Phase 2, listed in UX spec §9): camera capture, FAB speed-dial, servings scaling, tags.

---

## 1. Architecture decisions

| # | Decision | Why |
|---|---|---|
| P1 | **Contract freeze first (F0, lead, ~1–2 h)**, then three parallel streams with disjoint files, then integration + QA. Every new field gets a **default value** so the app compiles after F0 with no behaviour change. | Same mechanism that made WS0–WS5 work; defaults keep `toEntity/toDomain`, the clip sink and every test compiling while streams fill the bodies in. |
| P2 | **Room v2 via `Migration(1, 2)`** with explicit SQL (three `ALTER TABLE … ADD COLUMN`, one index), schema `2.json` committed, `MigrationTestHelper` instrumented test. **No `fallbackToDestructiveMigration`.** | Both QA devices hold v1 with real rows; destructive fallback would silently wipe the library. |
| P3 | **F0 adds all three columns at once** (`isFavourite`, `editedAt`, `origin`) in one migration, even though F1/F3 consume them separately. | One migration, one schema export, no second bump in the same release. |
| P4 | **All new label fields and string keys land in F0**, copied from UX spec §6, including the test fixtures. F1/F3 only *consume* labels. | `strings_recipekeeper.xml` and the label classes would otherwise be edited by two streams. |
| P5 | **MVI plumbing for the cross-feature hooks is stubbed in F0**: `LibraryIntent.CreateRecipe / EditRecipe(id) / ToggleFavourite(id) / SetFavouritesFilter(on)`, `LibraryEffect.NavigateToEditor(id?)`, `RecipeDetailIntent.Edit / ToggleFavourite`, `RecipeDetailEffect.NavigateToEditor(id)`, plus the new route callbacks `LibraryRoute(onOpenEditor)` / `RecipeDetailRoute(onEdit)` and the `when` branches that emit the effects. | F1 owns library + detail files; F3 owns the nav host and must wire the callbacks without editing F1's files. |
| P6 | **Editor domain use cases belong to F3** (`Load…`, `Save…`, `AddRecipePicture…`, `DiscardRecipeEdits…`, `SplitPastedLines…`); F2 owns persistence (entity/DAO/migration/repository/picture store/importer/sink/share). They meet only at the interfaces frozen in F0 (`RecipeRepository`, `RecipePictureStore`, `PictureImporter`). | Keeps F3 self-contained and testable with `FakeRecipeRepository` + a temp-dir `FileRecipePictureStore` + `FakePictureImporter`; F2 never needs the editor. |
| P7 | **Two Koin modules**: `recipesModule` (data + existing use cases + library/detail VMs, F2) and a new `recipeEditorModule` (editor use cases + VM, F3), both registered by F0 in `AppModules.kt`. | Disjoint DI ownership. |
| P8 | **Draft model `RecipeEdit`** (not `RecipeDraft`, which already names the streaming parser output in `clip/domain`). Lines and pictures carry stable `key`s; lines carry `sourceIndex` for the check remap. | Stable `LazyColumn` keys for `animateItem()`; the remap rule in UX spec D8 needs the original index. |
| P9 | **Pictures: temp-in-cache, commit-on-save.** `AddRecipePictureUseCase` copies + downscales into `cacheDir/recipe-editor/<editorSession>/`; `SaveRecipeUseCase` moves new files into `filesDir/recipes/<id>/` (numbered after the highest existing file) and deletes dropped files; `DiscardRecipeEditsUseCase` deletes the temp files. The app sweeps `cacheDir/recipe-editor/` (> 24 h) at start. | Discard restores removed pictures; process death does not leak into `filesDir`; the picker's read grant is consumed immediately (UX spec §9.5). Deviates from the brief's `RemoveRecipePictureUseCase`: removal is a draft mutation, the file deletion is part of Save. |
| P10 | **Validation lives in the domain** (`RecipeEditValidator`, used by `SaveRecipeUseCase` → `SaveResult.Invalid(errors)`), not in the ViewModel. | One source of truth, unit-tested without Android. |
| P11 | **Sort key stays `clippedAt`** (= "added to the library"); created recipes set it at creation; edits never change it. | UX spec D3/D8. |
| P12 | **Re-clip "Replace" keeps `isFavourite`** (sink reads the old row); `origin = CLIPPED`, `editedAt = null`, edits discarded (dialog message overridden to say so). | A favourite recipe should not lose its heart because the user refreshed it. |
| P13 | `ShareRecipeUseCase` takes a `RecipeShareTemplates(clipped, created, bullet, numbered)` value built from labels in DI (replacing the single `template` lambda). | Created recipes have no URL to share. |
| P14 | No new `:core:*` / `:feature:*` code. The duplicate-dialog message is overridden by resource name in the app. | Three parallel streams on shared modules was the costliest part of the first plan. |

---

## 2. Contracts to freeze (F0)

Written by the lead before the streams start. Signatures may grow; renames go through the lead.

### 2.1 Domain (`recipes/domain/`) — pure Kotlin

```kotlin
enum class RecipeOrigin { CLIPPED, CREATED }

data class ClippedRecipe(
    /* existing fields unchanged … */
    val isFavourite: Boolean = false,
    val editedAt: Long? = null,
    val origin: RecipeOrigin = RecipeOrigin.CLIPPED,
)

data class RecipeSummary(
    /* existing … */
    val isFavourite: Boolean = false,
    val origin: RecipeOrigin = RecipeOrigin.CLIPPED,
)

/** Library list filter; grows with tags/origin later. */
data class LibraryFilter(val favouritesOnly: Boolean = false)

/** Editor draft (UX spec §2). `id == null` = create. Keys are stable per editor session. */
data class RecipeEdit(
    val id: String?,
    val origin: RecipeOrigin,
    val title: String = "",
    val description: String = "",
    val servings: String = "",
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val ingredients: List<EditableLine> = emptyList(),
    val steps: List<EditableLine> = emptyList(),
    val notes: String = "",
    val pictures: List<EditorPicture> = emptyList(),
    val sourceUrl: String = "",
    val sourceHost: String = "",
)
data class EditableLine(val key: Long, val text: String, val sourceIndex: Int? = null)
/** [path] is a temp file while [isNew], else the stored file under `filesDir/recipes/<id>/`. */
data class EditorPicture(val key: Long, val path: String, val isNew: Boolean)

enum class EditSection { INGREDIENTS, STEPS }

sealed interface EditorError { data object TitleRequired : EditorError; data object NeedContent : EditorError }

sealed interface SaveResult {
    data class Saved(val id: String) : SaveResult
    data class Invalid(val errors: Set<EditorError>) : SaveResult
    data class Failed(val cause: Throwable) : SaveResult
}

interface RecipeRepository {
    /* existing … */
    fun observeAll(filter: LibraryFilter = LibraryFilter()): Flow<List<RecipeSummary>>   // replaces observeAll()
    fun search(query: String, filter: LibraryFilter = LibraryFilter()): Flow<List<RecipeSummary>>
    suspend fun setFavourite(id: String, favourite: Boolean)
}

interface RecipePictureStore {
    /* existing dirFor / moveIn / deleteAll … */
    /** Moves [files] in, numbering after the highest existing `NNN.ext` so nothing is clobbered. */
    override fun moveIn(recipeId: String, files: List<File>): List<String>   // behaviour note only; signature unchanged
    /** Deletes exactly these stored files of [recipeId]; ignores paths outside its folder. */
    fun delete(recipeId: String, paths: List<String>)
}

/** Copies a picked picture (content://, file://) into [into], downscaled to ≤ [maxLongEdgePx] JPEG. */
interface PictureImporter {
    suspend fun import(uri: String, into: File, maxLongEdgePx: Int = 1_280): Outcome<File>
    /** Deletes temp files under the editor cache older than [olderThanMillis]. */
    fun sweep(olderThanMillis: Long)
}

/** Share text per origin; built from labels in DI (P13). */
data class RecipeShareTemplates(
    val clipped: (title: String, url: String) -> String,
    val created: (title: String, ingredientsHeader: String, ingredientLines: String, stepsHeader: String, stepLines: String) -> String,
    val bullet: (String) -> String,
    val numbered: (Int, String) -> String,
    val ingredientsHeader: String,
    val stepsHeader: String,
)
```

Use-case classes created in F0 with `TODO("F1"/"F3")` bodies so DI and tests compile:

```kotlin
class ToggleFavouriteUseCase(repository)            { suspend operator fun invoke(id: String, favourite: Boolean) }          // F1
class ObserveLibraryUseCase(repository, deleteWithUndo) { operator fun invoke(query: String, filter: LibraryFilter): Flow<List<RecipeSummary>> } // F1 (signature change; F0 updates the single caller)
class LoadRecipeForEditUseCase(repository)          { suspend operator fun invoke(id: String?): RecipeEdit? }             // F3; null id → blank CREATED draft; unknown id → null
class SaveRecipeUseCase(repository, pictureStore, dispatchers, idFactory, clock) { suspend operator fun invoke(edit: RecipeEdit): SaveResult } // F3
class AddRecipePictureUseCase(importer, tempRoot: File, dispatchers) { suspend operator fun invoke(sessionId: String, uri: String): Outcome<EditorPicture> } // F3
class DiscardRecipeEditsUseCase(dispatchers)        { suspend operator fun invoke(edit: RecipeEdit) }                      // F3; deletes isNew files
class SplitPastedLinesUseCase                       { operator fun invoke(text: String, section: EditSection): List<String> } // F3
object RecipeEditValidator                          { fun validate(edit: RecipeEdit): Set<EditorError> }                    // F3
```

### 2.2 Data (`recipes/data/`) — F0 adds the fields with defaults, F2 does the rest

```kotlin
@Entity(tableName = "recipes", indices = [/* existing */, Index(value = ["isFavourite"])])
data class RecipeEntity(
    /* existing … */
    @ColumnInfo(defaultValue = "0") val isFavourite: Boolean = false,
    val editedAt: Long? = null,
    @ColumnInfo(defaultValue = "CLIPPED") val origin: String = "CLIPPED",
)
data class RecipeSummaryRow(/* existing … */, val isFavourite: Boolean, val origin: String)
```

`RecipeKeeperDatabase.version = 2`, `MIGRATION_1_2` (§3.2), `2.json` exported. `FakeRecipeRepository` (tests) gains `setFavourite`, the filter parameters and in-memory `isFavourite` handling (F0).

### 2.3 Presentation stubs (F0)

```kotlin
// library
sealed interface LibraryIntent { /* existing */; data class SetFavouritesFilter(val on: Boolean); data class ToggleFavourite(val id: String, val favourite: Boolean); data class EditRecipe(val id: String); data object CreateRecipe }
sealed interface LibraryEffect { /* existing */; data class NavigateToEditor(val id: String?) }
data class LibraryState(/* existing */, val filter: LibraryFilter = LibraryFilter()) { val isFavouritesEmpty get() = !isLoading && filter.favouritesOnly && query.isBlank() && recipes.isEmpty() }
@Composable fun LibraryRoute(/* existing */, onOpenEditor: (id: String?) -> Unit, …)
// detail
sealed interface RecipeDetailIntent { /* existing */; data object Edit; data object ToggleFavourite }
sealed interface RecipeDetailEffect { /* existing */; data class NavigateToEditor(val id: String) }
@Composable fun RecipeDetailRoute(/* existing */, onEdit: (id: String) -> Unit, …)
// editor (F3 fills; F0 creates the package with the MVI types so the nav host compiles)
package com.llmlocal.recipekeeper.recipes.presentation.editor
enum class EditorMode { CREATE, EDIT }
data class RecipeEditorState(val mode: EditorMode, val edit: RecipeEdit? = null, val initial: RecipeEdit? = null, val isLoading: Boolean = true, val errors: Set<EditorError> = emptySet(), val showDiscardDialog: Boolean = false, val isSaving: Boolean = false, val importingPictures: Int = 0, val maxPictures: Int = 8) { val isDirty get() = edit != null && edit != initial; val notFound get() = !isLoading && edit == null }
sealed interface RecipeEditorIntent { TitleChanged(v); DescriptionChanged(v); ServingsChanged(v); PrepChanged(v: String); CookChanged(v); TotalChanged(v); NotesChanged(v); LineChanged(section, key, text); AddLine(section, afterKey: Long?); RemoveLine(section, key); MoveLine(section, from: Int, to: Int); PicturesPicked(uris: List<String>); RemovePicture(key); SetCover(key); Save; RequestClose; DismissDiscard; ConfirmDiscard }
sealed interface RecipeEditorEffect { Saved(id, mode); Closed; FocusLine(section, key); FocusTitle; ScrollTo(target: EditorTarget); Notify(kind: EditorNotice) }
enum class EditorNotice { SAVE_FAILED, PICTURE_FAILED, PICTURES_LIMIT }   // the route maps to labels (rule 0)
enum class EditorTarget { TITLE, INGREDIENTS }
@Composable fun RecipeEditorRoute(recipeId: String?, labels: RecipeEditorLabels, snackbarHostState: SnackbarHostState, onSaved: (id: String, mode: EditorMode) -> Unit, onClosed: () -> Unit, modifier: Modifier = Modifier, viewModel: RecipeEditorViewModel = koinViewModel { parametersOf(recipeId) })
class RecipeEditorViewModel(recipeId: String?, savedStateHandle: SavedStateHandle, loadForEdit, saveRecipe, addPicture, discardEdits, splitPasted, dispatchers, maxPictures: Int = 8)
```

### 2.4 Labels (F0, from UX spec §6)

`RecipeKeeperLibraryLabels` += 21 fields (§6.1); new `@Immutable data class RecipeEditorLabels` + `rememberRecipeEditorLabels()` / `recipeEditorLabels(resources)` (§6.2); `strings_recipekeeper.xml` += every key; `strings_webclipper_overrides.xml` += `webclipper_duplicate_message`; test fixtures `TestRecipeKeeperLibraryLabels` (extended) and `TestRecipeEditorLabels` (new).

### 2.5 Routes (F0 declares, F3 wires)

```kotlin
data object NewRecipe : Route("recipe/new")
data object EditRecipe : Route("recipe/{recipeId}/edit") { const val ARG_ID = "recipeId"; fun build(id: String) = "recipe/${Uri.encode(id)}/edit" }
```

---

## 3. Specification

### 3.1 Domain rules

- `ToggleFavouriteUseCase(id, favourite)` → `repository.setFavourite`. The library VM calls it optimistically and lets the repository emission confirm.
- `ObserveLibraryUseCase(query, filter)`: `if (query.isBlank()) observeAll(filter) else search(query, filter)` combined with the delete-undo `pending` set (unchanged).
- `LoadRecipeForEditUseCase(id)`: `null` → `RecipeEdit(id = null, origin = CREATED, ingredients = [blank], steps = [blank])`; else map the `ClippedRecipe`: lines get `key = index.toLong()`, `sourceIndex = index`; pictures `EditorPicture(key, path, isNew = false)`; minutes copied; `servings` / `description` / `notes` `orEmpty()`.
- `SplitPastedLinesUseCase`: ingredients → split on `\n`, trim, drop blank, strip `^[-*•·]\s+` and `^\(?\d+[.)]\s+`; steps → if ≥ 2 lines match `^(\(?\d+[.)]|step\s*\d+[:.]?|[①-⑳])\s*` (case-insensitive) split on those markers keeping multi-line bodies, else if `\n\s*\n` present split on blank lines, else `listOf(text)`; always trim and drop blanks.
- `RecipeEditValidator`: `TitleRequired` when `title.isBlank()`; `NeedContent` when no ingredient and no step has non-blank text. Length caps are enforced by the ViewModel on input (title 200, description 1 000, servings 40, line 500, notes 4 000, minutes 4 digits), not reported as errors.
- `SaveRecipeUseCase(edit)`:
  1. `validate` → `Invalid`.
  2. Base: edit → `repository.observe(id).first() ?: return Failed(IllegalStateException)`; create → none.
  3. Lines: non-blank, trimmed. `checkedIngredients` (edit) = indices `i` of the surviving ingredient lines whose `sourceIndex ∈ base.checkedIngredients`.
  4. Minutes: `total = edit.totalMinutes ?: (prep + cook).takeIf { prep != null && cook != null }`.
  5. Pictures (IO): `newPaths = store.moveIn(id, edit.pictures.filter { isNew }.map(::File))`; `dropped = base.pictures − edit.pictures.filter { !isNew }.map { path }` → `store.delete(id, dropped)`; final order = the strip order with new paths substituted.
  6. Recipe: edit → `base.copy(title, description?, servings?, prep, cook, total, ingredients, steps, notes?, pictures, truncated = false, editedAt = clock(), checkedIngredients, picturesFailed = base.picturesFailed && pictures.isEmpty())`; create → `ClippedRecipe(id = idFactory(), origin = CREATED, clippedAt = clock(), sourceUrl = "", sourceHost = "", pictures, truncated = false, …)`.
  7. `repository.upsert` → `Saved(id)`; any throwable (not `CancellationException`) → `Failed`.
- `AddRecipePictureUseCase(sessionId, uri)`: `importer.import(uri, File(tempRoot, "$sessionId/${UUID}.jpg"))` → `EditorPicture(key = nanoTime, path, isNew = true)`.
- `DiscardRecipeEditsUseCase(edit)`: deletes `edit.pictures.filter { isNew }` files (IO).
- `ShareRecipeUseCase(id)`: `CLIPPED` → `templates.clipped(title, url)`; `CREATED` → `templates.created(title, ingredientsHeader, ingredients.joinToString("\n") { bullet(it) }, stepsHeader, steps.mapIndexed { i, s -> numbered(i + 1, s) }.joinToString("\n"))`.

### 3.2 Data

**Migration (F2):**

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN isFavourite INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE recipes ADD COLUMN editedAt INTEGER")
        db.execSQL("ALTER TABLE recipes ADD COLUMN origin TEXT NOT NULL DEFAULT 'CLIPPED'")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipes_isFavourite ON recipes (isFavourite)")
    }
}
// recipesModule: Room.databaseBuilder(...).addMigrations(MIGRATION_1_2).build()
```

The column defaults must match the `@ColumnInfo(defaultValue)` strings exactly (`"0"`, none, `"CLIPPED"`) or Room's identity check fails after migrating. Regenerate `schemas/com.llmlocal.recipekeeper.recipes.data.RecipeKeeperDatabase/2.json` by building; commit it; keep `1.json`.

**DAO (F2):**

```kotlin
@Query("""SELECT r.id, r.title, r.sourceHost, r.totalMinutes, r.truncated, r.clippedAt, r.isFavourite, r.origin,
          (SELECT p.path FROM pictures p WHERE p.recipeId = r.id ORDER BY p.position LIMIT 1) AS heroPicture
          FROM recipes r WHERE r.deletedAt IS NULL AND (:favouritesOnly = 0 OR r.isFavourite = 1)
          ORDER BY r.clippedAt DESC""")
fun observeSummaries(favouritesOnly: Boolean): Flow<List<RecipeSummaryRow>>
// searchSummaries(query, favouritesOnly) gets the same predicate
@Query("UPDATE recipes SET isFavourite = :favourite WHERE id = :id") suspend fun setFavourite(id: String, favourite: Boolean)
```

`RecipeEntity.origin` is stored as the enum name (`String` column, mapped in `toEntity/toDomain`; no converter needed). `RoomRecipeRepository.observeAll(filter)` / `search(query, filter)` / `setFavourite`.

**Picture store (F2):** `moveIn` numbers from `max(existing NNN) + 1`; new `delete(recipeId, paths)` deletes only files inside `dirFor(recipeId)`.

**Picture importer (F2):** `ContentResolverPictureImporter(context, dispatchers)`: `openInputStream` twice (bounds, then decode with `inSampleSize` so the long edge ≤ 1 280 px), honour EXIF orientation (`androidx.exifinterface` is already transitively available through Coil; if not, add `androidx-exifinterface` to the catalog), write JPEG q85 to `into` via a `.part` + rename; `Outcome.Failure` on any decode/IO error. `sweep(olderThan)` walks `cacheDir/recipe-editor/`.

**Clip sink (F2):** when `options.replaceId != null`, read the old row (`repository.observe(id).first()`) and copy `isFavourite`; set `origin = CLIPPED`, `editedAt = null`.

**DI (F2, `recipesModule`):** `single<PictureImporter> { ContentResolverPictureImporter(androidContext(), get()) }`, `single(named(EDITOR_TEMP_DIR)) { File(androidContext().cacheDir, "recipe-editor") }`, `factory { ToggleFavouriteUseCase(get()) }`, `RecipeShareTemplates` built from `recipeKeeperLibraryLabels(resources)`, `addMigrations(MIGRATION_1_2)`.

### 3.3 Presentation

**Library (F1):** `LibraryFilterRow` under the search field (hidden while `isEmptyLibrary` / loading); `RecipeTile(onToggleFavourite)` with the scrim-disc `FavouriteToggle` outside the merged node + menu items Favourite / Edit / Share / Open original (hidden for `origin == CREATED`) / Delete; tile meta uses `labels.createdByYou` for created rows; `LibraryGrid` empty states `isFavouritesEmpty` → `EmptyState(FavoriteBorder, favouritesEmptyTitle, favouritesEmptyMessage, showAllRecipes)`, search-in-favourites → `searchEmptyFavouritesTitle(q)`; onboarding empty state gains the `TextButton(writeRecipe)`; `LibraryRoute` top bar adds `IconButton(Add, newRecipe)` between Search and Models and maps `NavigateToEditor` → `onOpenEditor`; `LibraryViewModel.observeRecipes()` keys on `(query, filter)`; `ToggleFavourite` → use case (fire-and-forget on `viewModelScope`).

**Detail (F1):** `DetailTopBar` actions = `FavouriteToggle`, pencil (`Edit`), overflow (Share, Open original*, Re-extract*, Keep screen on, Delete; * hidden when created); `SourceRow` renders the clipped/created variants with `clippedOn`/`createdOn` + `metaSeparator` + `editedOn`; `MetaChips` never shows the incomplete badge for created rows (truncated is false anyway); `RecipeDetailViewModel`: `Edit` → `NavigateToEditor(recipeId)`, `ToggleFavourite` → use case; when a new emission has fewer steps than `currentStep`, clear `currentStep`.

**Editor (F3, `recipes/presentation/editor/`):** `RecipeEditorRoute` (Koin VM with `parametersOf(recipeId)`, `SavedStateHandle` via `get()`, effects → snackbar kinds mapped through `RecipeEditorLabels`, photo-picker launchers, `BackHandler(isDirty)`), `RecipeEditorScreen` (compact single column ≤ 720dp / two panes ≥ 600dp per UX spec §2.7, `Scaffold(contentWindowInsets = safeDrawing)`), components `EditableLineList`/`EditableLineRow` (drag handle with `detectDragGesturesAfterLongPress`, `customActions` Move up/down, `animateItem()`), `PictureStrip`/`AddPictureTile`, `MetaFields`, `DiscardChangesDialog`, `SourceReadOnlyRow`; `RecipeEditorViewModel` (draft in `SavedStateHandle` under one key as a `Parcelable`/`Bundle`-friendly snapshot — simplest: store the `RecipeEdit` as JSON via `org.json` in a `String` key, restore in `init`; `LineChanged` with `\n` → `splitPasted`; `PicturesPicked` → sequential `addPicture` with `importingPictures` counter and `PICTURES_LIMIT` when over `maxPictures`; `Save` → `saveRecipe` → `Saved`/errors/`SAVE_FAILED`; `RequestClose` → dialog when dirty; `ConfirmDiscard` → `discardEdits` → `Closed`); previews for create/edit/errors/wide. The nav host (`RecipeKeeperNavHost.kt`, F3) adds the two routes with `launchSingleTop`, wires `onOpenEditor` / `onEdit`, and handles `onSaved`: `CREATE` → `navigate(recipe/{id}) { popUpTo(library) }`, `EDIT` → `popBackStack()` + root-scope snackbar `changesSaved`; `onClosed` → `popBackStack()`.

---

## 4. Tests to add

| Stream | Test | Covers |
|---|---|---|
| F0 | (none new) | existing suites stay green with the stubs |
| F1 | `LibraryViewModelTest` (+6) | filter on/off narrows to favourites (fake repo honours `LibraryFilter`); filter + search AND; `isFavouritesEmpty`; toggle favourite calls the use case and the list reflects it; `CreateRecipe` / `EditRecipe` emit `NavigateToEditor(null/id)`; created rows keep their `origin` in state |
| F1 | `RecipeDetailViewModelTest` (+3) | `Edit` emits `NavigateToEditor`; `ToggleFavourite` persists; `currentStep` cleared when steps shrink |
| F1 | `ToggleFavouriteUseCaseTest` | sets/unsets on the fake repository |
| F2 | `RecipePictureStoreAndConvertersTest` (+3) | `moveIn` into a non-empty folder numbers after the highest file; `delete` ignores foreign paths; `deleteAll` unchanged |
| F2 | `RecipeKeeperClipSinkTest` (+1) | Replace keeps `isFavourite`, resets `origin`/`editedAt` |
| F2 | `ShareRecipeUseCaseTest` (new) | clipped vs created templates, numbering and bullets |
| F2 | `MigrationTest` (`src/androidTest`, `room-testing` `MigrationTestHelper`) | v1 DB with two rows + pictures migrates to v2, rows keep every value, defaults `isFavourite = 0`, `origin = 'CLIPPED'`, `editedAt NULL`; run by the QA stream on emulator-5556 (`connectedDebugAndroidTest` is too heavy for the shared runner; run the single class) |
| F3 | `SplitPastedLinesUseCaseTest` | ingredient bullets/numbers/blank lines; steps numbered / blank-line / single multi-line step; Windows `\r\n` |
| F3 | `RecipeEditValidatorTest` | title required; need content; both; valid |
| F3 | `SaveRecipeUseCaseTest` | create builds a CREATED recipe with new id and `clippedAt`; edit keeps id/`clippedAt`/`isFavourite`, sets `editedAt`, clears `truncated`; checks remapped after reorder/remove/insert; total derived only when both set; blank lines dropped; new pictures moved and dropped pictures deleted (temp-dir `FileRecipePictureStore`); `Invalid` persists nothing; repository failure → `Failed` |
| F3 | `LoadRecipeForEditUseCaseTest` | mapping, blank create draft, unknown id → null |
| F3 | `RecipeEditorViewModelTest` | edit mode loads the draft and `isDirty == false`; typing makes it dirty; `RequestClose` dirty → dialog, clean → `Closed`; `ConfirmDiscard` deletes new pictures (`FakePictureImporter` + temp files) and emits `Closed`; `LineChanged` with newlines splits and emits `FocusLine` on the last; `AddLine`/`RemoveLine`/`MoveLine`; `PicturesPicked` over the cap → `PICTURES_LIMIT`; `Save` invalid → errors + `ScrollTo(TITLE)`; `Save` valid → `Saved(id, mode)`; `SavedStateHandle` round trip restores the draft without reloading |
| F4 | device QA (below) | migration on the live DB, flows, insets, a11y dump, 600dp |

Existing fakes: `FakeRecipeRepository` (F0 extends), `TestDispatchers`, `MainDispatcherRule`; new `FakePictureImporter` (F3, copies a 1×1 JPEG fixture or just creates the file).

---

## 5. Work streams

Each stream: owner agent type, files it owns (nobody else edits them), steps, done-when. Streams marked ∥ run in parallel once F0 has landed. Every stream keeps `./gradlew :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug :app:recipekeeper:testDebugUnitTest` green, commits on its worktree branch, and writes `docs/plans/briefs/<id>-report.md`. Subagents run as herdr panes with the `claude-work` profile (CLAUDE.md); test/QA agents on Sonnet.

### F0 — Contract freeze (lead; sequential; ~1–2 h)

Owns everything it touches; runs alone. Steps:
1. Domain: `RecipeOrigin`, `LibraryFilter`, `RecipeEdit` + `EditableLine` + `EditorPicture` + `EditSection` + `EditorError` + `SaveResult`, new fields with defaults on `ClippedRecipe` / `RecipeSummary`, `RecipeRepository` (filter params + `setFavourite`), `RecipePictureStore.delete`, `PictureImporter`, `RecipeShareTemplates`; use-case classes with `TODO("F1")` / `TODO("F3")` bodies (§2.1); `ObserveLibraryUseCase` signature change + caller update.
2. Data: `RecipeEntity` / `RecipeSummaryRow` fields with defaults, mappers; `RoomRecipeRepository` / `FileRecipePictureStore` new methods with `TODO("F2")`; **do not** bump the DB version yet (F2 does the bump + migration + export together).
3. Presentation stubs (§2.3): intents/effects/state fields, VM `when` branches that emit the editor effects, route callbacks with default no-op wiring in the nav host, editor package with MVI types and a placeholder `RecipeEditorRoute` rendering `AppLoadingIndicator`.
4. Labels + strings + overrides + test fixtures (§2.4, from UX spec §6). Both label classes are complete after this step.
5. DI: `recipeEditorModule` (empty `module { }`) registered in `recipeKeeperModules`; `recipesModule` registers the F1 use case factory.
6. Routes declared in `RecipeKeeperNavHost.kt` (§2.5) pointing at the placeholder editor route.
7. `./gradlew :app:recipekeeper:assembleDebug :app:recipekeeper:testDebugUnitTest`; commit `F0: features contract freeze (favourites, editor, create)`; branch streams from it.
Done when: the app builds, installs, and behaves exactly as before (hidden hooks only); all existing tests green.

### F1 — Favourites + detail/library hooks (∥; `android-ux`)

Owns: `recipes/presentation/library/**`, `recipes/presentation/detail/**`, `recipes/presentation/previews/**`, `recipes/domain/usecase/{ToggleFavouriteUseCase,ObserveLibraryUseCase}.kt`, tests `recipes/presentation/library/**`, `recipes/presentation/detail/**`, `recipes/domain/usecase/ToggleFavouriteUseCaseTest.kt`.
Steps:
1. `ToggleFavouriteUseCase` body; `ObserveLibraryUseCase(query, filter)` body.
2. `LibraryViewModel`: state `filter`, `SetFavouritesFilter`, `ToggleFavourite`, observe keyed on `(query, filter)`; `LibraryState.isFavouritesEmpty`.
3. `LibraryFilterRow`, `FavouriteToggle` (scrim disc, `IconToggleButton`, `stateDescription`), `RecipeTile` heart + menu (Favourite, Edit, Share, Open original*, Delete) + created meta/description, grid `animateItem()`, empty states, onboarding `writeRecipe` button, top-bar `Add` action (UX spec §1, §3.1, §3.3).
4. Detail: top bar `[♡][✎][⋮]`, overflow reorder (Share first; hide Open original / Re-extract for created), `SourceRow` variants with `editedOn`, `ToggleFavourite` / `Edit` intents, `currentStep` clamp.
5. Previews: library with filter on/off/empty, created tile, detail created + edited.
6. Tests from §4 (F1 rows). Unit tests run against `FakeRecipeRepository`; on device the heart is a `TODO` until F2 merges — build only.
Done when: tests green, `:app:recipekeeper:assembleDebug` green, previews render light + dark, report lists every label consumed.

### F2 — Persistence v2, migration, pictures import, sink, share (∥; `general-purpose`)

Owns: `recipes/data/**`, `recipes/di/RecipesModule.kt`, `app/recipekeeper/schemas/**`, `clip/data/RecipeKeeperClipSink.kt`, `recipes/domain/usecase/ShareRecipeUseCase.kt`, `gradle/libs.versions.toml` (`androidx-room-testing`, optionally `androidx-exifinterface`), `app/recipekeeper/build.gradle.kts` (test deps only), `src/androidTest/**/recipes/**`, tests `recipes/data/**`, `clip/RecipeKeeperClipSinkTest.kt`, `recipes/domain/usecase/ShareRecipeUseCaseTest.kt`.
Steps:
1. Entity index + `RecipeKeeperDatabase.version = 2` + `MIGRATION_1_2` (§3.2) + `addMigrations` in DI; build to export `2.json`; commit it. KDoc on the database class updated (v1 shipped → migrations from now on).
2. DAO queries with `favouritesOnly`, `setFavourite`; `RecipeSummaryRow` columns; repository methods.
3. `FileRecipePictureStore`: numbering after the highest existing file; `delete(id, paths)`.
4. `ContentResolverPictureImporter` + `sweep`; DI binding + `EDITOR_TEMP_DIR` qualifier.
5. `RecipeKeeperClipSink`: carry `isFavourite` on replace; `origin = CLIPPED`, `editedAt = null`.
6. `ShareRecipeUseCase(repository, templates: RecipeShareTemplates)`; DI builds the templates from `recipeKeeperLibraryLabels(resources)`.
7. `MigrationTest` (androidTest, `room-testing`): create v1 with `MigrationTestHelper.createDatabase(name, 1)`, insert two rows + pictures via SQL, `runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)`, assert values. Do **not** run it on the shared emulator-5554; the QA stream runs it on 5556.
8. Unit tests from §4 (F2 rows).
Done when: tests green, `2.json` committed and identical to what a clean build exports, boundary check unchanged, report lists the SQL and anything the lead must do at merge (the app-start `sweep` call in `RecipeKeeperApp`, which F2 does not own).

### F3 — Editor domain + UI, create entry, nav (∥; `android-ux` for UI + VM, may pair with `general-purpose` for the use cases)

Owns: `recipes/domain/usecase/{LoadRecipeForEditUseCase,SaveRecipeUseCase,AddRecipePictureUseCase,DiscardRecipeEditsUseCase,SplitPastedLinesUseCase}.kt`, `recipes/domain/RecipeEditValidator.kt`, `recipes/presentation/editor/**`, `recipes/di/RecipeEditorModule.kt`, `app/RecipeKeeperNavHost.kt`, tests `recipes/domain/usecase/{Save,Load,SplitPasted}*Test.kt`, `recipes/domain/RecipeEditValidatorTest.kt`, `recipes/presentation/editor/**`, `testing/FakePictureImporter.kt`.
Steps:
1. Use cases + validator per §3.1, with tests first (they are pure).
2. `RecipeEditorViewModel` per §2.3/§3.3 incl. `SavedStateHandle` draft persistence; tests.
3. Components: `EditableLineList`/`Row` (drag + custom actions), `PictureStrip`/`AddPictureTile`, `MetaFields`, `DiscardChangesDialog`, `SourceReadOnlyRow`.
4. `RecipeEditorScreen` compact + wide; `RecipeEditorRoute` (photo pickers: `PickMultipleVisualMedia(maxItems)` when ≥ 2 slots else `PickVisualMedia`; `BackHandler`; effects → snackbar via labels; focus requesters).
5. `recipeEditorModule`: use cases as `factory`, `viewModel { params -> RecipeEditorViewModel(recipeId = params.getOrNull(), savedStateHandle = get(), …) }`.
6. Nav host: routes + `launchSingleTop`, `onOpenEditor` / `onEdit` wiring, post-save navigation + root-scope `changesSaved` snackbar, `onClosed`.
7. Previews: create (empty), edit (filled), errors, importing picture, wide 840dp, dark.
Done when: tests green; build green; with F2 absent the editor runs against the F0 `TODO`s only at save time (fine for previews/tests); report lists the exact `SavedStateHandle` key and the snackbar mapping.

### F4 — Integration + device QA (sequential, last; lead merges, QA agent on **Sonnet** with the `android-ux` persona)

1. Lead: merge F1, F2, F3 onto `recipekeeper/main`; add `importer.sweep(now − 24 h)` to `RecipeKeeperApp.onCreate` (after `startKoin`, on a background dispatcher); `./gradlew assemble :app:recipekeeper:testDebugUnitTest lint`; boundary check; install the arm64 split on **`emulator-5556`** with `adb install -r` (never `pm clear` — the model and the v1 library live there).
2. QA agent (brief in the style of `qa-sites.md` / `ws13.md`), device `emulator-5556` only:
   - **Migration**: before installing, dump `recipes` row count + ids from the v1 DB (`run-as … cat databases/recipekeeper.db*` + host `sqlite3`); after first launch confirm the same rows, `isFavourite = 0`, `origin = 'CLIPPED'`, no Room error in logcat; run `MigrationTest` once (`./gradlew :app:recipekeeper:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=…MigrationTest`).
   - **Favourites**: heart on tile and detail; state survives app restart; filter chip narrows; filter + search; empty state + "Show all recipes"; un-heart inside the filtered view animates out; Replace on re-clip keeps the heart; uiautomator dump shows `Favourite` + state descriptions.
   - **Edit**: pencil → editor; change title, reorder via drag and via accessibility actions (`uiautomator` custom actions are not visible — verify the Move up/down actions through a Compose UI test instead, see 3), paste a 5-line ingredient list → 5 rows, remove a step, remove a picture + add one from the picker (push a JPEG to `/sdcard/Pictures` and `cmd media … scan` first), Save → detail updated, `editedAt` set, checks kept on the surviving rows, dropped picture file gone, new file in the folder; Back with changes → dialog → Keep editing / Discard (file restored); process-death check (`am kill` mid-edit, reopen → draft restored).
   - **Create**: "+" → editor; Save with title + 1 ingredient → detail with "Created by you", no Open original / Re-extract, share text contains ingredients and steps; back → library; tile meta "Created by you"; empty-library path via "Write a recipe".
   - **Insets / IME**: keyboard open on the last step — the row stays above the keyboard; Save reachable; landscape; `wm size 1280x800` → two panes; `wm size reset`.
   - **Regression sweep**: clip → detail → back → browser unchanged; delete + undo; model management.
   - Screenshots `qa6/<nn>-<check>.png`, logcat, UI dumps; report `docs/plans/briefs/f4-qa-report.md` with pass/fail per check and bug file:line + fix.
3. Optional (if time): a Compose UI test for the editor row custom actions (`onNodeWithContentDescription("Reorder ingredient 1").performCustomAccessibilityActionWithLabel("Move down")`) under `src/androidTest`, run on 5556.
4. Docs: update `CLAUDE.md` (Recipe Keeper section: editor package, Room v2 + migration rule, favourites filter, created recipes; "Rule 0 status" gains `RecipeEditorLabels`), `recipekeeper-ux-spec.md` §5 pointer to the features spec, and the Phase 2 backlog.
Done when: QA report has no P1/P2 open, CLAUDE.md updated, final commit on `recipekeeper/main`.

---

## 6. Schedule and parallelism

```
F0 contract freeze (lead) ──┬── F1 favourites + hooks (android-ux) ────┐
                            ├── F2 persistence v2 + migration (gp) ───┼── F4 integration + QA (lead + Sonnet android-ux)
                            └── F3 editor domain + UI + nav (android-ux) ┘
```

Three agents after F0. Conflict rules as in the app plan: only the owning stream edits its files; a needed change to a frozen contract goes through the lead and is announced to the other two; everyone rebases on `recipekeeper/main` before finishing. Estimated effort: F0 1–2 h, F1 ~half a day, F2 ~half a day, F3 1–1.5 days, F4 ~half a day.

Suggested worktree branches: `features/f1-favourites`, `features/f2-persistence`, `features/f3-editor` from the F0 commit on `recipekeeper/main`.

---

## 7. Brief-ready step lists (copy into `docs/plans/briefs/f1.md`, `f2.md`, `f3.md`, `f4-qa.md`)

Each brief starts with: read `docs/plans/briefs/_common.md` (worktree, no device for F1–F3, commit + report rules, rule 0, architecture rules), then `recipekeeper-features-ux-spec.md` §(relevant) and this plan §2 (contracts), §3 (spec) and the stream's section in §5. "**You own:**" = the files listed in §5. Steps = the numbered list in §5. "Done when" = the stream's done-when line. Report file `docs/plans/briefs/<id>-report.md`; reply `<id> done — see <id>-report.md, branch <branch>, commit <hash>`.

---

## 8. Risks and open decisions

1. **Share demoted to the overflow on the detail** (UX spec D4) — confirm or swap before F1 starts.
2. **Migration correctness** — `defaultValue` strings must match the SQL defaults; the `MigrationTest` and the live-DB check in F4 are the gate. If anything goes wrong on the emulator, the fix is in F2's SQL, never `fallbackToDestructiveMigration`.
3. **Hand-rolled drag-to-reorder** — scope-limited; custom actions are the guaranteed path (UX spec §9.4).
4. **`SavedStateHandle` size** — a long recipe with 8 picture paths is a few KB; well under the 500 KB transaction cap. Pictures themselves never enter the bundle.
5. **Koin + `SavedStateHandle`** — `viewModel { params -> … get<SavedStateHandle>() }` works with `koinViewModel()`; if the lead's Koin version needs the `savedStateHandle` DSL, F3 adjusts the definition (one line).
6. **`PickMultipleVisualMedia` requires `maxItems > 1`** — handled by switching contracts when one slot remains.
7. **Replace discards edits** — mitigated by P12 + the dialog override; a snapshot/backup is Phase 2.
8. **`ObserveLibraryUseCase` signature change** — F0 updates the only caller; `LibraryViewModelTest` compiles because F0 passes `LibraryFilter()`.
9. **Large images** — `inSampleSize` decode only; test with a 4000×3000 fixture in the importer test (F2, JVM-side only for the sampling math; the real decode is instrumented).
10. **Phase 2 backlog added**: camera capture (`TakePicture` + `FileProvider`), FAB menu, servings scaling, tags/collections, favourites-first sort option, edit history/backup before Replace.
