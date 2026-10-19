# R1 report — `:feature:llm-task` + `:app:recipes` + `:app:medical`

## New package tree
```
feature/llm-task/.../feature/llmtask/
  presentation/  LlmTaskRoute, LlmTaskScreen, LlmTaskViewModel
    components/  InputChips, InputRow, InputsCard, NoModelAvailableBanner
    mvi/         LlmTaskEffect, LlmTaskIntent, LlmTaskLabels, LlmTaskState, ModelStatus
  domain/        ModelAvailability, ModelReadinessSource, SelectedModelSource, LlmGenerationSource
    usecase/     ObserveModelReadinessUseCase, ObserveSelectedModelUseCase, RecheckModelUseCase,
                 GenerateTaskUseCase, CancelGenerationUseCase
  data/          ModelReadinessSourceImpl, SelectedModelSourceImpl, LlmGenerationSourceImpl  (internal)
  di/            LlmTaskModule.kt (llmTaskModule)
app/recipes/.../recipe/   RecipeApp, MainActivity, config/, designsystem/,
  recipe/presentation/  RecipeScreen, StreamedRecipeCard, RecipePreviews, RecipePrimitivePreviews, ModelManagementPreviews
  recipe/domain/        Recipe, Ingredient, RecipePromptBuilder, RecipeParser
app/medical/.../medical/  MedicalApp, MainActivity, config/, designsystem/,
  medical/presentation/ MedicalScreen, StreamedMedicalCard, MedicalPreviews, MedicalPrimitivePreviews, ModelManagementPreviews
  medical/domain/       MedicalAdvice, Symptom, MedicalPromptBuilder, MedicalParser
```

## Use cases
| Use case | Responsibility | Depends on |
|---|---|---|
| ObserveModelReadinessUseCase | hot `StateFlow<ModelAvailability>` for the selected model | ModelReadinessSource |
| ObserveSelectedModelUseCase | selected model descriptor (default when unset), distinct | SelectedModelSource |
| RecheckModelUseCase | re-run disk + engine check | ModelReadinessSource |
| GenerateTaskUseCase | stream `LlmTaskEvent<T>`; engine `initialize()` happens inside the stream (init failure → `Failed`) | LlmGenerationSource |
| CancelGenerationUseCase | stop in-flight generation | LlmGenerationSource |

`ModelAvailability` (domain) is mapped from `:core:llm`'s `ModelReadiness` in `data/`; domain imports no `:core:llm`.
`SelectedModelSource` has an extra `describe(id)` (catalog lookup) beyond the brief, so the ViewModel no longer touches `LlmModelCatalog`.

## Moved public FQNs (old → new)
- `com.llmlocal.feature.llmtask.{LlmTaskRoute,LlmTaskScreen,LlmTaskViewModel}` → `…llmtask.presentation.*`
- `com.llmlocal.feature.llmtask.mvi.*` → `com.llmlocal.feature.llmtask.presentation.mvi.*`
- `com.llmlocal.feature.llmtask.components.*` → `com.llmlocal.feature.llmtask.presentation.components.*`
- `com.llmlocal.feature.llmtask.di.llmTaskModule` — unchanged
- recipes: `recipe.RecipeScreen`, `recipe.output.StreamedRecipeCard`, `recipe.previews.*` → `recipe.recipe.presentation.*`; `recipe.domain.{Recipe,Ingredient}`, `recipe.prompt.RecipePromptBuilder`, `recipe.parser.RecipeParser` → `recipe.recipe.domain.*`
- medical: same pattern under `com.llmlocal.medical.medical.{presentation,domain}`
- Importers outside my scope: `:app:recipekeeper` only imports `llmTaskModule` (unchanged) — no action.

## Koin
`llmTaskModule`: `factory<ModelReadinessSource>`, `factory<SelectedModelSource>`, `factory<LlmGenerationSource>` (real engine via `REAL_ENGINE_QUALIFIER`), five use-case `factory`s, `viewModel { params -> LlmTaskViewModel(…, config = params.get(), labels = params.get()) }`.
`LlmTaskViewModel(observeReadiness, observeSelectedModel, recheckModel, generateTask, cancelGeneration, dispatchers, config, labels)`; State/ModelStatus mapping unchanged.

## Tests
New `LlmTaskViewModelTest` (7 tests, fakes of the domain interfaces): readiness→Ready enables generate, Downloading/Failed mapping, selection → state, token/complete streaming, failure message, cancel resets, recheck. Build file gained test deps.
`./gradlew :feature:llm-task:testDebugUnitTest :app:recipes:assembleDebug :app:medical:assembleDebug` — all green (7/7 tests).

## Left for the lead
- `RecipeApp`/`MedicalApp` kept at package root; per-app `config/` and `designsystem/` untouched.
- `LlmGenerationSourceImpl` and `ModelReadinessSourceImpl` have no unit tests (need core stubs); `ModelReadinessSourceImpl` adds a `stateIn(Eagerly)` over the observer's state.
