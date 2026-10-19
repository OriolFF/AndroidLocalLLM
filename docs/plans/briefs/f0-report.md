# F0 report — features contract freeze

Branch `features/f0`. Behaviour unchanged (hidden hooks only). Verified: `./gradlew :app:recipekeeper:assembleDebug :app:recipekeeper:testDebugUnitTest :app:recipes:assembleDebug :app:medical:assembleDebug` → BUILD SUCCESSFUL, 129 unit tests, 0 failures.

## Domain (`recipes/domain/`)
- `RecipeOrigin { CLIPPED, CREATED }`, `LibraryFilter(favouritesOnly = false)`.
- `ClippedRecipe` += `isFavourite = false`, `editedAt: Long? = null`, `origin = CLIPPED`; `RecipeSummary` += `isFavourite = false`, `origin = CLIPPED`.
- `RecipeEdit`, `EditableLine`, `EditorPicture`, `EditSection`, `EditorError { TitleRequired, NeedContent }`, `SaveResult { Saved, Invalid, Failed }` (file `RecipeEdit.kt`).
- `RecipeRepository`: `observeAll(filter = LibraryFilter())`, `search(query, filter = LibraryFilter())`, `setFavourite(id, favourite)`.
- `RecipePictureStore.delete(recipeId, paths)` (+ KDoc: `moveIn` numbers after the highest existing file — F2 implements).
- `PictureImporter` (`import`, `sweep`), `RecipeShareTemplates` (not yet used: `ShareRecipeUseCase` still takes the single `template` lambda; F2 switches it).
- `RecipeEditValidator.validate` — `TODO("F3")`.
- Use cases with `TODO` bodies: `ToggleFavouriteUseCase` (F1); `LoadRecipeForEditUseCase`, `SaveRecipeUseCase`, `AddRecipePictureUseCase`, `DiscardRecipeEditsUseCase`, `SplitPastedLinesUseCase` (F3). Constructors/signatures as plan §2.1.
- `ObserveLibraryUseCase(query, filter)` — **filter has no default**; body already does `observeAll(filter)` / `search(query, filter)` (F1 owns it but it is functionally complete).

## Data (`recipes/data/`)
- `RecipeEntity` += `isFavourite` (`defaultValue "0"`), `editedAt`, `origin` (`defaultValue "CLIPPED"`, enum name) + `Index(isFavourite)`; `RecipeSummaryRow` += `isFavourite`, `origin` **with defaults** (the existing converters test builds the row positionally); mappers carry all three (unknown origin string → `CLIPPED`).
- `RecipeDao`: both summary queries now also select `r.isFavourite, r.origin` (needed to compile). **No filter predicate yet — F2 adds `favouritesOnly`.**
- `RoomRecipeRepository.observeAll/search` accept `filter` and ignore it (F2); `setFavourite` and `FileRecipePictureStore.delete` are `TODO("F2")`.
- **Room version stays 1** and the entity now differs from the committed `1.json`: the build rewrites `schemas/.../1.json`; I restored it with `git checkout`. Do not run this commit on a device with a v1 DB (identity-hash crash); F2 bumps to 2 + `MIGRATION_1_2` + exports `2.json`.

## Presentation
- Library: `LibraryIntent.SetFavouritesFilter(on)`, `ToggleFavourite(id, favourite)`, `EditRecipe(id)`, `CreateRecipe`; `LibraryEffect.NavigateToEditor(id: String?)`; `LibraryState.filter` + `isFavouritesEmpty` (`isEmptyLibrary` now also requires `!filter.favouritesOnly`); `LibraryViewModel` takes `toggleFavourite: ToggleFavouriteUseCase`, observes `(query, filter)`; `LibraryRoute(onOpenEditor: (String?) -> Unit = {})`.
- Detail: `RecipeDetailIntent.Edit`, `ToggleFavourite`; `RecipeDetailEffect.NavigateToEditor(id)`; `RecipeDetailViewModel` takes `toggleFavourite`; `RecipeDetailRoute(onEdit: (String) -> Unit = {})`.
- Editor package `recipes/presentation/editor/`: `EditorMode`, `RecipeEditorState` (`isDirty`, `notFound`), `RecipeEditorIntent` (all plan variants; minutes as `String`), `RecipeEditorEffect`, `EditorNotice`, `EditorTarget`, `RecipeEditorViewModel` (constructor per plan, `onIntent` = `TODO("F3")`), `RecipeEditorRoute` (placeholder `AppLoadingIndicator`), `RecipeEditorLabels` + `rememberRecipeEditorLabels()` / `recipeEditorLabels(resources)`.
- Nav host: `Route.NewRecipe` (`recipe/new`) and `Route.EditRecipe` (`recipe/{recipeId}/edit`, `ARG_ID`, `build(id)`) registered pointing at the placeholder; **`onOpenEditor` / `onEdit` are deliberately not wired**, so the screens are unreachable until F3.

## DI
`recipeEditorModule` (empty) in `recipes/di/RecipeEditorModule.kt`, registered in `recipeKeeperModules` after `recipesModule`; `recipesModule` registers `ToggleFavouriteUseCase` and passes it to both ViewModels.

## Labels and strings (all in `strings_recipekeeper.xml`, copy from UX spec §6)
`RecipeKeeperLibraryLabels` += 21 fields: `favourite`, `favouriteAdd`, `favouriteRemove`, `favouriteStateOn`, `favouriteStateOff`, `favouritesFilter`, `favouritesEmptyTitle`, `favouritesEmptyMessage`, `showAllRecipes`, `searchEmptyFavouritesTitle`, `editRecipe`, `newRecipe`, `writeRecipe`, `createdByYou`, `recipeTileDescriptionCreated`, `createdOn`, `editedOn`, `changesSaved`, `shareCreatedTemplate`, `shareBullet`, `shareNumbered`.
Key = `recipekeeper_` + snake_case of the field (exact keys in spec §6.1).

`RecipeEditorLabels` (48 fields): `editTitle`, `newTitle`, `close`, `save`, `saving`, `titleLabel`, `titleRequired`, `descriptionLabel`, `servingsLabel`, `prepLabel`, `cookLabel`, `totalLabel`, `totalDerived`, `ingredientsHeader`, `ingredientsHint`, `ingredientLabel`, `ingredientPlaceholder`, `addIngredient`, `removeIngredient`, `reorderIngredient`, `stepsHeader`, `stepsHint`, `stepLabel`, `stepPlaceholder`, `addStep`, `removeStep`, `reorderStep`, `moveUp`, `moveDown`, `needContent`, `notesLabel`, `picturesHeader`, `addPicture`, `removePicture`, `pictureDescription`, `useAsCover`, `coverBadge`, `picturesLimit`, `pictureFailed`, `sourceReadOnly`, `sourceReadOnlyHint`, `discardTitle`, `discardMessageEdit`, `discardMessageNew`, `discard`, `keepEditing`, `saveFailed`, `recipeNotFound`.
Keys per spec §6.2; `ingredientsHeader`, `stepsHeader`, `notesLabel`, `picturesHeader`, `pictureDescription`, `recipeNotFound` reuse existing keys.

Override: `webclipper_duplicate_message` in the app's `strings_webclipper.xml` (there is no `strings_webclipper_overrides.xml`; the existing app file is the overrides file) now warns that edits are lost.

Test fixtures: `TestRecipeKeeperLibraryLabels` extended, `TestRecipeEditorLabels` new (each field renders its own name). `FakeRecipeRepository` honours `LibraryFilter`, implements `setFavourite` (records `favouriteCalls`), summaries carry `isFavourite`/`origin`. Existing VM tests pass the new `toggleFavourite` use case.

## For F1–F3 / the lead
- F1: `ToggleFavouriteUseCase` body; UI consumes the library labels; `LibraryRoute`/`RecipeDetailRoute` hooks already emit effects.
- F2: DAO predicate, `setFavourite`, `delete`, version 2 + migration + `2.json`, switch `ShareRecipeUseCase` to `RecipeShareTemplates`, bind `PictureImporter` and the `RecipeShareTemplates`; the app-start `sweep` is the lead's.
- F3: all editor bodies, `recipeEditorModule` bindings, wire `onOpenEditor` / `onEdit` in the nav host.
