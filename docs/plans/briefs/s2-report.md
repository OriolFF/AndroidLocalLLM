# S2 report — strings for modelmanagement, core:llm notifications, app model-management labels

## Resource files added
- `feature/modelmanagement/src/main/res/values/strings_modelmanagement.xml` — family/filter option names, progress "Ns left"/"Nm left", and `modelmanagement_default_*` brand-neutral copy for previews.
- `core/llm/src/main/res/values/strings_llm.xml` — notification channel name/description, download title/text, done title/text, generic failure, foreground-service failure reasons.
- `app/{recipes,medical,recipekeeper}/src/main/res/values/strings_modelmanagement.xml` — keys `<app>_modelmanagement_*` (recipes: "selected for recipe generation", medical: "selected for wellness guidance", recipekeeper: "will read your clipped recipes"). English text is unchanged.

## Label factories
- `@Composable rememberRecipeModelManagementLabels()`, `rememberMedicalModelManagementLabels()`, `rememberRecipeKeeperModelManagementLabels()` replace the old `val XModelManagementLabels` objects (same files).
- `@Composable defaultModelManagementLabels()` (feature, `presentation/mvi/DefaultModelManagementLabels.kt`) for previews.
- Test fixture `TestModelManagementLabels` (replaces `TestLabels` in `Fakes.kt`).

## Public signature changes
- `ModelManagementLabels` has a new required field `unknownModelError: (modelId) -> String`. It replaces the literal "Unknown model: …" in `ModelManagementViewModel`. The class already had no defaults.
- `SizeBucket`, `StatusFilter` and `QuantFilter` lose `displayName`. `ModelFamily.displayName` is replaced by `@Composable ModelFamily.label()` (public, in `FilterBar.kt`). Chip labels are now resources.
- `FilterBar` (previously unused label fields), `ModelDetailSheet` and `ModelCard` now read their copy from `modelManagementLabels`.
- Call sites changed, labels argument only: `recipes/MainActivity.kt`, `medical/MainActivity.kt`, `recipekeeper/app/RecipeKeeperNavHost.kt`. I also removed the unused `MedicalModelManagementLabels` import in `medical/.../ModelManagementPreviews.kt`.

## Deviation from the brief
- `DownloadProgressBar` uses the feature's own string resources, not `secondsLeftTemplate`/`minutesLeftTemplate`. It is also used by the Recipe Keeper library banner outside `ModelManagementRoute`, where `LocalModelManagementLabels` is not provided. The two template label fields remain for the contract.
- `ModelDownloadWorker` failure reasons (foreground-service failure, generic "Download failed") now come from `applicationContext.getString`.

## Remaining literals (allowed)
- Log tag and log messages in `ModelDownloadWorker`.
- `ByteFormat.kt` unit symbols "GB/MB/KB/B": locale-neutral unit abbreviations. Flag if you want them in resources.
- `"${descriptor.minRamMb} ${labels.detailMinRamUnit}"`, `" · "` and `" / "` separators: structural formatting.
- Error message in `LocalModelManagementLabels` (developer-facing).
- Failure reasons from `LlmModelManager` live in `:core:llm` outside the brief's owned files and are untouched.

## Results
`./gradlew :feature:modelmanagement:testDebugUnitTest :core:llm:testDebugUnitTest :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug` passes.
