# F3 report — editor domain + UI, create entry, nav

Branch `features/f3`. Verified: `./gradlew :app:recipekeeper:testDebugUnitTest :app:recipekeeper:assembleDebug` → BUILD SUCCESSFUL, 168 unit tests, 0 failures (129 from F0 + 39 new). No device work. `schemas/1.json` untouched.

## Files (all under `app/recipekeeper/src/`)
- Domain: `recipes/domain/RecipeEditValidator.kt`; use cases `LoadRecipeForEditUseCase`, `SaveRecipeUseCase`, `AddRecipePictureUseCase`, `DiscardRecipeEditsUseCase`, `SplitPastedLinesUseCase` (signatures unchanged from F0, bodies per plan §3.1).
- Presentation `recipes/presentation/editor/`: `RecipeEditorViewModel`, `RecipeEditorDraftCodec` (internal, org.json), `RecipeEditorRoute`, `RecipeEditorScreen` (new, public), `RecipeEditorPreviews`; `components/`: `EditableLineList` (rows, `LineEditorState` drag/focus holder, `LineSectionCopy`), `PictureStrip` (+ add / pending tiles), `MetaFields`, `DiscardChangesDialog`, `SourceReadOnlyRow`.
- DI: `recipes/di/RecipeEditorModule.kt` — use cases as `factory`, `viewModel { params -> RecipeEditorViewModel(recipeId = params.getOrNull(), savedStateHandle = get(), …) }`; temp root = `cacheDir/recipe-editor`.
- Nav: `app/RecipeKeeperNavHost.kt`.
- Tests: `RecipeEditValidatorTest` (4), `SplitPastedLinesUseCaseTest` (7), `LoadRecipeForEditUseCaseTest` (3), `SaveRecipeUseCaseTest` (10), `RecipeEditorViewModelTest` (15); `testing/FakePictureImporter.kt`.

## Public surface
- `RecipeEditorScreen(state, labels, snackbarHostState, onIntent, onAddPictures: () -> Unit, uiEffects: Flow<RecipeEditorEffect>, modifier)`.
- `RecipeEditorRoute` signature is the F0 one (unchanged). The route forwards `FocusTitle` / `FocusLine` / `ScrollTo` to the screen through a buffered channel (the screen owns the list states and focus requesters); `Saved` / `Closed` / `Notify` are handled in the route.

## SavedStateHandle
- `RecipeEditorViewModel.DRAFT_KEY = "recipe_editor_draft"`: one JSON string `{edit, initial}` (the dirty comparison survives restore). `SESSION_KEY = "recipe_editor_session"`: temp-picture session id. `init` restores from the draft instead of reloading the recipe; pictures are paths only.

## Snackbar mapping (route, rule 0)
`SAVE_FAILED → labels.saveFailed`; `PICTURE_FAILED → labels.pictureFailed`; `PICTURES_LIMIT → labels.picturesLimit(maxPictures)`. After an edit save the nav host shows `RecipeKeeperLibraryLabels.changesSaved` from the root scope. No new strings or label fields were added (all from F0).

## Nav host
`onOpenEditor` (library) and `onEdit` (detail) wired through `navigateToEditor(id?)` (`launchSingleTop`). `CREATE` saved → `navigate(recipe/{id}) { popUpTo(library) }`; `EDIT` saved → `popBackStack()` + `changesSaved` snackbar; `onClosed` → `popBackStack()`.

## Decisions / deviations
- Line caps: ingredient 500, **step 2 000** (plan says 500 for every line; a 500 cap would silently truncate pasted steps). Title 200, description 1 000, servings 40, notes 4 000, minutes 4 digits as planned.
- A newline typed (not pasted) into a step that yields a single part keeps the raw text (including a trailing `\n`), otherwise Enter in a step would be trimmed away by the split use case.
- Save is disabled while pictures are importing (prevents losing a picture mid-copy).
- Removing a *new* picture deletes its temp file immediately; removing a stored one only edits the draft (deleted on Save).
- Drag-to-reorder is hand-rolled on layout offsets (long-press the handle; one swap per layout pass); **no edge auto-scroll while dragging** — custom actions Move up / Move down on every handle are the guaranteed reorder path (UX spec §9.4).
- `SaveRecipeUseCaseTest` uses a local temp-dir store with a recording `delete` because `FileRecipePictureStore.delete`/numbering is still F2's `TODO`. `SaveRecipeUseCase` pairs `moveIn` results with the sources that existed, so it relies only on F2 keeping "results in source order, missing sources skipped".

## For the lead at merge
1. F2 must bind `PictureImporter` in Koin; until then opening the editor fails at VM creation (`AddRecipePictureUseCase`). Unit tests/previews are unaffected.
2. F2's `RecipePictureStore.moveIn` must number after the highest existing file and `delete` must be implemented, or edit-save will clobber/crash.
3. Run `importer.sweep(now − 24 h)` at app start (temp pictures live in `cacheDir/recipe-editor/<session>/`).
4. F1 owns the heart/pencil/menu UI that emits the editor effects; the nav host side is ready.
