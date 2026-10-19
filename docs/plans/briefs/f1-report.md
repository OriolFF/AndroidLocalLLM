# F1 report — favourites + detail/library hooks

Branch `features/f1`. Verified: `./gradlew :app:recipekeeper:testDebugUnitTest :app:recipekeeper:assembleDebug` → BUILD SUCCESSFUL, 0 failures (new: +6 library, +3 detail, 2 use-case tests). No device work (per brief). Previews compile; I did not render them in Android Studio.

## Files (all inside the F1 ownership list)
- `recipes/domain/usecase/ToggleFavouriteUseCase.kt` — body: `repository.setFavourite(id, favourite)`. `ObserveLibraryUseCase` was already complete from F0 (unchanged).
- `recipes/presentation/library/`
  - **new** `FavouriteToggle(checked, labels, onCheckedChange, modifier, scrim = false, tint = White)` — 48dp `IconToggleButton`, 36dp scrim disc when `scrim`, `Favorite`/`FavoriteBorder`, `contentDescription = favourite`, `stateDescription = favouriteStateOn/Off`. Also used by the detail top bar.
  - **new** `LibraryFilterRow(filter, labels, onFavouritesChange)` — scrollable row with one `FilterChip`.
  - `RecipeTile` — new params `onToggleFavourite(Boolean)`, `onEdit`; heart is a sibling of the merged card node; menu = Favourite add/remove, Edit, Share, Open original (hidden for `CREATED`), Delete; created tiles use `createdByYou` as meta and `recipeTileDescriptionCreated`; tile node gets `stateDescription = favouriteStateOn` when favourited.
  - `LibraryScreen` — filter row under the search field (hidden while loading / onboarding empty), `animateItem()` on tiles, favourites-empty state, filter-aware search-empty title, onboarding `TextButton(writeRecipe)` → `CreateRecipe`.
  - `LibraryRoute` — top-bar `Add` action (`newRecipe`) between Search and Models.
- `recipes/presentation/detail/`
  - `RecipeDetailScreen` — top bar `[♡][✎][⋮]`; overflow = Share, Open original*, Re-extract*, Keep screen on, Delete (* hidden when created); `SourceRow` created/clipped variants with `createdOn`/`clippedOn` + `metaSeparator` + `editedOn`, `EditNote` icon and no trailing button for created; hero subtitle = `createdByYou` for created; incomplete badge never shown for created.
  - `RecipeDetailViewModel` — clears `currentStep` when a new emission has fewer steps. (`Edit`/`ToggleFavourite` intents were already wired by F0.)
- `recipes/presentation/previews/` — `SampleData` (+`favouriteRecipe`, `createdRecipe`, `editedRecipe`, `createdSummary`, `favouriteSummaries`; main summary list now has a heart and a created tile); library previews: favourites on / on dark / empty / favourites+search empty dark; detail previews: favourite+edited, created, created dark.

## Labels consumed (all created by F0, no new strings added)
`favourite`, `favouriteAdd`, `favouriteRemove`, `favouriteStateOn`, `favouriteStateOff`, `favouritesFilter`, `favouritesEmptyTitle`, `favouritesEmptyMessage`, `showAllRecipes`, `searchEmptyFavouritesTitle`, `editRecipe`, `newRecipe`, `writeRecipe`, `createdByYou`, `recipeTileDescriptionCreated`, `createdOn`, `editedOn` (+ existing `clippedOn`, `metaSeparator`, `openOriginal`, `reExtract`, `shareRecipe`, `moreOptions`, `searchEmptyTitle`, `clearSearch`). Not consumed by F1: `changesSaved` (F3), `shareCreatedTemplate`/`shareBullet`/`shareNumbered` (F2).

## Tests
- `LibraryViewModelTest` +6: filter on/off, filter ANDs search, `isFavouritesEmpty` (not onboarding), toggle via use case reflects in list, `CreateRecipe`/`EditRecipe` → `NavigateToEditor(null/id)`, created rows keep `origin`.
- `RecipeDetailViewModelTest` +3: `Edit` effect, `ToggleFavourite` persists/flips, `currentStep` cleared on shrink.
- `ToggleFavouriteUseCaseTest` (new, 2): set/unset, `editedAt` untouched.

## For the lead
- Building rewrites `schemas/.../1.json` (entity differs from v1 until F2 bumps the version); I restored it with `git checkout`, as F0 did.
- On a device the heart still hits `RoomRecipeRepository.setFavourite` = `TODO("F2")` until F2 merges; `onOpenEditor` / `onEdit` remain unwired in the nav host until F3, so the new top-bar `+`, tile Edit and detail pencil do nothing yet.
- Share on the detail moved into the overflow (UX decision D4).
