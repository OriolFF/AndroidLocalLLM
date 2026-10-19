# S1 report — strings for llm-task, designsystem, recipes, medical

## Resource files added
- `app/recipes/src/main/res/values/strings_recipes.xml` (nav, task labels, card headings, untitled fallback)
- `app/medical/src/main/res/values/strings_medical.xml` (nav, task labels, card headings, condition + disclaimer fallbacks)
- None needed in `:feature:llm-task` / `:core:designsystem`: both already take all copy via labels/parameters (no previews in the feature, no literals in designsystem Kotlin outside KDoc).

## Label factories
- `rememberRecipeLabels()` / `recipeLabels(resources)` in `config/RecipeLabels.kt`; `rememberMedicalLabels()` / `medicalLabels(resources)` in `config/MedicalLabels.kt`. `val RecipeLabels` / `val MedicalLabels` removed.
- `recipesConfigModule` / `medicalsConfigModule` no longer register labels (they had no consumers).
- `RecipeScreen`, `MedicalScreen`, both `*Previews`, both `*PrimitivePreviews` use the remembered labels. Both `MainActivity`s use `stringResource` for "Back", "Manage models", "Models" and `app_name` for the root title.

## Public signature changes
- `LlmTaskLabels` gained `generationFailedMessage` (fallback in `LlmTaskViewModel` was the literal "Generation failed"). Any other app building `LlmTaskLabels` must supply it.
- `Recipe.title` is now `String?` and `MedicalAdvice.condition` / `.disclaimer` are `String?`. The parsers used to inject the literals "Untitled recipe", "General information" and the disclaimer fallback; the cards now resolve those fallbacks from resources. Parsing behaviour is otherwise unchanged. `MedicalParser.DISCLAIMER_FALLBACK` is gone.

## Tests
- `TestLlmTaskLabels` fixture added in `feature/llm-task/src/test`; `LlmTaskViewModelTest` uses it.

## Remaining literals (allowed)
- KDoc/comments mentioning example copy.
- Parser format tokens (`Ingredients`, `Steps`, `Condition`, `Likely Causes`, `Self-Care`, `When to Seek Care`, `Disclaimer`) and prompt builders: model-facing.
- `@Preview` sample data (recipe/symptom lists, model descriptor, error sample) and the `*PrimitivePreviews` gallery captions/phase items: dev-only fixtures that never ship in UI.
- Animation labels (`label = "skeleton"` etc.), nav route ids, `IllegalStateException` message in `Tokens.kt`.
- Not touched (S2 owns): `*ModelManagementLabels.kt`, `ModelManagementPreviews.kt`, `strings_modelmanagement.xml`.

## Results
`:feature:llm-task:testDebugUnitTest`, `:core:designsystem:testDebugUnitTest`, `:app:{recipes,medical,recipekeeper}:assembleDebug`, `:app:{recipes,medical}:lintDebug` all pass.
