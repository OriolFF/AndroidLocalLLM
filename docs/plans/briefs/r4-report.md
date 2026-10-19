# R4 report — `:app:recipekeeper` split into `recipes` + `clip` features

Branch `refactor/r4`. `:app:recipekeeper:testDebugUnitTest :app:recipekeeper:assembleDebug` — **129 tests, 0 failures**, build green.

## Final tree (`com.llmlocal.recipekeeper`)
```
app/            MainActivity, RecipeKeeperApp, RecipeKeeperNavHost (routes + NavHost), RecipeKeeperModelManagementLabels,
                ModelManagementPreviews, di/AppModules.kt (appModule: Coil ImageLoader; recipeKeeperModules list)
designsystem/   RecipeKeeperTokens
recipes/        presentation/ RecipeKeeperLibraryLabels, library/ (Route, Screen, ViewModel, State/Intent/Effect, RecipeTile, UndoDeleteSnackbar),
                              detail/ (Route, Screen, ViewModel, State/Intent/Effect), previews/ (Library, RecipeDetail, SampleData)
                domain/       ClippedRecipe, RecipeSummary, RecipeRepository, RecipePictureStore (iface), PictureFetcher (iface),
                              LibraryModelStatus + LibraryModelStatusSource (iface), usecase/*
                data/         Room (Database, Dao, Entity, Converters), RoomRecipeRepository, FileRecipePictureStore,
                              WebClipperPictureFetcher, ReadinessLibraryModelStatusSource
                di/           RecipesModule.kt (recipesModule)
clip/           presentation/ ClipDraftCard, RecipeKeeperClipperLabels, previews/ClipDraftCardPreviews
                domain/       RecipeKeeperClipConfig, SchemaOrgRecipeMapper, TextCleaner, RecipeKeeperPromptBuilder, RecipeKeeperParser,
                              RecipeKeeperDraftParser, RecipeLineGrammar, StubDraftParser, RecipeDraft, RecipeKeeperTaskConfig
                data/         RecipeKeeperClipSink
                di/           ClipModule.kt (clipModule)
```
`presentation` imports no `data`; `domain` imports no `android.*`/`androidx.*`/`org.koin.*` (checked by grep). Deviations from "pure Kotlin": `domain` uses `java.io.File` (picture interfaces), `:core:common` (`DispatcherProvider`), `:core:llm` `PerModelProgress` (inside `LibraryModelStatus.Downloading`, because the shared `DownloadProgressBar` takes it) and, in `clip/domain`, `:feature:webclipper` types (`WebClipConfig` etc., as the brief placed `RecipeKeeperClipConfig` there).

## Use cases (`recipes/domain/usecase`)
| Use case | Responsibility | Depends on |
|---|---|---|
| `ObserveLibraryUseCase(query)` | list or search, minus recipes inside the undo window | `RecipeRepository`, `DeleteRecipeWithUndoUseCase` |
| `ObserveRecipeUseCase(id)` | live recipe | `RecipeRepository` |
| `ToggleIngredientCheckedUseCase(recipe, index)` | toggle + persist, returns updated recipe (`null` if index invalid) | `RecipeRepository` |
| `ResetChecksUseCase(recipe)` | clear + persist | `RecipeRepository` |
| `DeleteRecipeWithUndoUseCase` | `schedule/undo/commit`, `pending` flow; app-scoped `single` (was `PendingRecipeDeletes`) | `RecipeRepository`, `DispatcherProvider` |
| `RetryRecipePicturesUseCase(recipe)` | download first 3 candidates, move into folder, replace pictures | `RecipeRepository`, `PictureFetcher`, `RecipePictureStore`, `DispatcherProvider` |
| `ShareRecipeUseCase(id)` | share text from the labels' `shareTemplate` (injected by DI) | `RecipeRepository`, template lambda |
| `ObserveLibraryModelStatusUseCase()` | hot `LibraryModelStatus` (replaces `ModelStatusSource`) | `LibraryModelStatusSource` |

ViewModels: `LibraryViewModel(observeLibrary, observeModelStatus, observeRecipe, deleteWithUndo, shareRecipe, dispatchers)` (the brief's list plus `observeRecipe`, needed for "Open original"); `RecipeDetailViewModel(recipeId, observeRecipe, toggleIngredient, resetChecks, deleteWithUndo, retryPictures, shareRecipe, dispatchers)`.
Behaviour notes: share effects now carry the final text (`ShareRecipe(text)`), so routes no longer format it. Ingredient toggles are applied to state after the use case persists (not before), serialised by a `Mutex` in the detail ViewModel; the test was adapted (`advanceUntilIdle` before asserting).

## Koin
- `recipesModule`: DB, DAO, `RecipePictureStore`, `RecipeRepository`, `PictureFetcher`, `LibraryModelStatusSource`, use cases (`DeleteRecipeWithUndoUseCase` single, rest factory), `LibraryViewModel`, `RecipeDetailViewModel(parametersOf(recipeId))`.
- `clipModule`: `RecipeKeeperTaskConfig`, `WebClipConfig<ClippedRecipe>`, `ClipSink<ClippedRecipe>`.
- `appModule`: Coil `ImageLoader`. `recipeKeeperModules = listOf(appModule, recipesModule, clipModule)`; `RecipeKeeperApp` installs it after the shared modules. `recipeKeeperConfigModule` / `recipeKeeperUiModule` are gone.

## Moved FQNs (old → new, prefix `com.llmlocal.recipekeeper.`)
- `MainActivity`, `RecipeKeeperApp` → `app.*` (**manifest updated**: `.app.RecipeKeeperApp`, `.app.MainActivity`; launch with `adb shell am start -n com.llmlocal.recipekeeper/.app.MainActivity` — CLAUDE.md still says `/.MainActivity`)
- `domain.{ClippedRecipe,RecipeSummary,RecipeRepository}` → `recipes.domain.*`; `domain.RecipeDraft` → `clip.domain.RecipeDraft`
- `data.*` → `recipes.data.*`; `data.RecipePictureStore` (class) → interface `recipes.domain.RecipePictureStore` + `recipes.data.FileRecipePictureStore`
- `library.*` → `recipes.presentation.library.*`; `library.LibraryModelStatus` → `recipes.domain.LibraryModelStatus`; `library.ModelStatusSource` → `recipes.domain.LibraryModelStatusSource`; `library.ReadinessModelStatusSource` → `recipes.data.ReadinessLibraryModelStatusSource`; `library.PendingRecipeDeletes` → `recipes.domain.usecase.DeleteRecipeWithUndoUseCase`; `FakeModelStatusSource` removed (test fake `testing.FakeLibraryModelStatusSource`)
- `detail.*` → `recipes.presentation.detail.*`
- `config.RecipeKeeperLibraryLabels` / `DefaultRecipeKeeperLibraryLabels` → `recipes.presentation.*`; `config.RecipeKeeperClipperLabels` → `clip.presentation.*`; `config.RecipeKeeperModelManagementLabels` → `app.*`
- `clip.{RecipeKeeperClipConfig,RecipeKeeperTaskConfig,SchemaOrgRecipeMapper,TextCleaner}`, `parser.*`, `prompt.*`, `output.StubDraftParser` → `clip.domain.*`; `clip.RecipeKeeperClipSink` → `clip.data.*`; `output.ClipDraftCard` → `clip.presentation.*`
- `previews.*` → `recipes.presentation.previews` / `clip.presentation.previews` / `app` (model management)
- Room schema folder renamed to `schemas/com.llmlocal.recipekeeper.recipes.data.RecipeKeeperDatabase/1.json` (Room keys it by DB class FQN); content and DB version unchanged.

## `feature.webclipper` imports used (current FQNs — these need a sed if R-webclipper moves them)
`ClipSink`, `ClipCandidate`, `ClipSaveOptions`, `CandidatePayload`, `CandidateSource`, `PicturePick`, `WebClipConfig`, `WebClipperBrowserRoute`, `WebClipperExtractionRoute`, `WebClipperLabels`, `capture.PictureDownloader`, `di.webClipperModule`, `di.WEBCLIPPER_TEMP_DIR_QUALIFIER`; tests also use `wholePageCandidate`. Files: `app/RecipeKeeperNavHost.kt`, `app/RecipeKeeperApp.kt`, `clip/domain/RecipeKeeperClipConfig.kt`, `clip/data/RecipeKeeperClipSink.kt`, `clip/di/ClipModule.kt`, `clip/presentation/RecipeKeeperClipperLabels.kt`, `recipes/data/WebClipperPictureFetcher.kt`, `recipes/di/RecipesModule.kt`, and tests (`FakePictureDownloader`, clip/eval tests).

## Tests
Moved to mirror the packages (eval tests and resources unchanged under `eval/`; `RecipeKeeperPipelineTest` stays in `clip`). `PendingRecipeDeletesTest` → `DeleteRecipeWithUndoUseCaseTest`; the library/detail ViewModel tests build the real use cases over `FakeRecipeRepository`; the retry-pictures test runs the real `RetryRecipePicturesUseCase` over `WebClipperPictureFetcher` + `FileRecipePictureStore` + `FakePictureDownloader`. No new dedicated unit tests for the other use cases (they are thin and covered through the ViewModel tests).

## For the lead
- Update CLAUDE.md (launch command, Recipe Keeper file locations, "Current state vs rules" paragraph).
- `androidTest/.../EvalRunnerTest.kt` imports were remapped but the androidTest source set was not compiled here.
