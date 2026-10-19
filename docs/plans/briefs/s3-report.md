# S3 report — webclipper strings

## Resources added
- `feature/webclipper/src/main/res/values/strings_webclipper.xml` (all `WebClipperLabels` copy; plural `webclipper_fab_found`; formatted strings for the lambda fields).
- `app/recipekeeper/src/main/res/values/strings_webclipper.xml` — three brand overrides (`webclipper_browser_back_to_library`, `webclipper_extraction_recipe_saved`, `webclipper_extraction_extracting_hint`). They override the feature's same-named strings via resource merging.

## Label factories
- Feature: `webClipperLabels(resources, searchUrlTemplate = ::defaultSearchUrlTemplate)`, `@Composable defaultWebClipperLabels(searchUrlTemplate)`, `defaultSearchUrlTemplate(query)` (new file `WebClipperLabelsFactory.kt`).
- App: **`@Composable fun rememberRecipeKeeperClipperLabels(): WebClipperLabels`** (exact name for S4) plus non-composable `recipeKeeperClipperLabels(resources)`, in `clip/presentation/RecipeKeeperClipperLabels.kt`.

## Changed public signatures
- `WebClipperLabels`: no field has a default anymore (names/types unchanged).
- Removed `val RecipeKeeperClipperLabels` (replaced by the functions above).

## Remaining literals
No user-visible literals found in `feature/webclipper` or `core/web` main code; the UI already read everything from labels. Left alone: log TAG, URLs/headers/JS asset names, clipboard `ClipData` label `"url"` (not shown), anim label `"clipFabIcon"`, preview sample data. The preview "WebView" stand-in text was removed (empty Box).

## Tests / build
- `TestWebClipperLabels.kt` (`testWebClipperLabels()`) added in `feature/webclipper/src/test`; both ViewModel tests use it. `BrowserTopBarTest` (androidTest) uses `webClipperLabels(...)` from resources; it compiles, not run on a device.
- Passed: `:feature:webclipper:testDebugUnitTest`, `:core:web:testDebugUnitTest`, `:app:recipes:assembleDebug`, `:app:medical:assembleDebug`, `:feature:webclipper:compileDebugAndroidTestKotlin`, `:feature:webclipper:lintDebug`.
- **`:app:recipekeeper:assembleDebug` fails only on S4-owned call sites**: `RecipeKeeperNavHost.kt:31,96,114` still reference the old `RecipeKeeperClipperLabels` object. S4 must switch them to `rememberRecipeKeeperClipperLabels()`. Resources process fine.
