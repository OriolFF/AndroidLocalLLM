# S4 report — strings for the rest of `:app:recipekeeper`

## Added / changed
- `app/recipekeeper/src/main/res/values/strings_recipekeeper.xml` — all library/detail/draft/delete copy (3 plurals: servings, ingredients/steps found). English text unchanged.
- `recipes/presentation/RecipeKeeperLibraryLabels.kt` — no defaults; `DefaultRecipeKeeperLibraryLabels` removed. New `@Composable rememberRecipeKeeperLibraryLabels()` (keyed on `LocalConfiguration`) and `recipeKeeperLibraryLabels(resources: Resources)`.
- New label fields (were inline literals in screens): `downloadingModelDescription(title, percent)`, `metaSeparator`, `stepSpokenDescription(position, text)`.
- `RecipeKeeperNavHost` uses `rememberRecipeKeeperLibraryLabels()`; `RecipesModule` share template uses `recipeKeeperLibraryLabels(androidContext().resources).shareTemplate`.
- `ClipDraftCard` `labels` param lost its default (previews pass `rememberRecipeKeeperLibraryLabels()`); previews updated.
- Test fixture `testing/TestRecipeKeeperLibraryLabels.kt`. No existing test constructed labels, so no other test changes.

## Lead must do at merge (S2/S3 not in this worktree)
In `RecipeKeeperNavHost.kt` switch:
- `labels = RecipeKeeperClipperLabels,` (two places: browser + extraction routes) → `labels = rememberRecipeKeeperClipperLabels(),`
- `labels = RecipeKeeperModelManagementLabels,` → `labels = rememberRecipeKeeperModelManagementLabels(),`
(`rememberX()` is composable; call it inside the `composable { }` block or hoist next to `val labels`.)

## Remaining literals (allowed)
Route patterns/ids/keys in the nav host, `text/plain` MIME type, preview `@Preview(name=…)` names, `SampleData` fixtures. Out of scope (S2/S3 files): `RecipeKeeperModelManagementLabels.kt`, `RecipeKeeperClipperLabels.kt`, `ModelManagementPreviews.kt` literals.

## Results
`./gradlew :app:recipekeeper:testDebugUnitTest :app:recipekeeper:assembleDebug` — pass.
