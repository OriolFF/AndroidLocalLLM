# I3 report — app: source chooser, images origin, share target, labels, navigation

Branch `inputs/i3-chooser-share`. `IMAGES_SOURCE` stays **false**: users see the chooser without the images row, the URL share target works, image shares cannot reach the app.

## Files (all under `app/recipekeeper/src/main` unless noted)
- **Chooser:** `recipes/presentation/library/SourceChooserSheet.kt` (`SourceChooserSheet` = `ModalBottomSheet`, `SourceChooserContent` = body for previews/UI tests, `ImagesUnavailableDialog`); `LibraryRoute` ("+" → `OpenSourceChooser`, sheet, dialog with "Open models"), `LibraryScreen` (empty state "More ways to add…"), `LibraryViewModel.chooseSource` (WEB → browser, EDITOR → editor, VOICE → no-op, IMAGES per availability).
- **Availability:** `recipes/domain/usecase/ObserveImagesAvailabilityUseCase.kt` now `(source: LibraryModelStatusSource, enabled: Boolean)` returning `Flow<ImagesAvailability>`; `LibraryModelStatus.Ready(supportsImages, modelName)` (name feeds the dialog); `ReadinessLibraryModelStatusSource` fills both; `RecipesModule` passes `BuildConfig.IMAGES_SOURCE`.
- **Origin branches:** `RecipeOrigin.hasSource` (CLIPPED only) in `RecipeOrigin.kt`; `RecipeTile` (meta `fromYourPhotos`, description `recipeTileDescriptionImages`, no Open original), `RecipeDetailScreen` (hero/source label `fromYourPhotosCheckAmounts`, `PhotoLibrary` icon, `addedOn(date)`, no Open original / Re-extract / trailing button), `RecipeEditorScreen` (source row via `hasSource`), `ShareRecipeUseCase` (IMAGES/VOICE use the created template).
- **Sink/config:** `clip/data/RecipeKeeperClipSink.kt` (`save(item, input, …)`: images → origin `IMAGES`, empty source, pictures moved in, no learning/candidates; `existingId(input)` → null for images); `RecipeKeeperClipConfig.renderImagesPrompt` + `RecipeKeeperPromptBuilder.IMAGES_USER_TURN/IMAGES_GUIDANCE`.
- **Share:** `app/ShareIntentParser.kt` (pure `parse(action, mimeType, streamUris, clipUris, text)` + `Intent` adapter), `app/ShareReceiverActivity.kt`, `app/MainActivity.kt` (`singleTop`, `onCreate`/`onNewIntent` → `ClipInputInbox.offer`; skipped on restore), `app/RecipeKeeperNavHost.kt` (inbox observer, `clip/images` route fed by shared URIs, `clipAlreadyRunning` snackbar guard, URL → browser), `res/values/themes.xml` (`Theme.RecipeKeeper.ShareReceiver`).
- **Model management:** `feature/modelmanagement/.../ModelCard.kt`, `ModelDetailSheet.kt` render `labels.capabilityVision` as the first pill when `VISION in descriptor.capabilities`. The three apps' `capabilityVision` labels/strings were already filled by I0 (verified).
- **Previews:** chooser in every availability state + dark + dialog, images tile, images detail (`LibraryPreviews.kt`, `RecipeDetailPreviews.kt`, `SampleData.imagesRecipe/imagesSummary`).
- **Tests:** `ShareIntentParserTest` (7), `LibraryViewModelTest` (+5), `RecipeKeeperClipSinkTest` (+2), `ShareRecipeUseCaseTest` (+1); origin round trip already covered by I0's `RecipePictureStoreAndConvertersTest`. `androidTest/.../SourceChooserSemanticsTest.kt` written and compiled, **not run**.

## Manifest and flag mechanism
`MainActivity` gets `android:launchMode="singleTop"`. `ShareReceiverActivity` (`exported`, `excludeFromRecents`, `noHistory`, translucent theme) carries the always-on `SEND text/plain` filter. The two `image/*` filters (`SEND`, `SEND_MULTIPLE`) sit on `<activity-alias android:name=".app.ShareImagesAlias" android:targetActivity=".app.ShareReceiverActivity" android:enabled="${imagesShareTarget}">`. Intent filters cannot be toggled, components can: with the flag off the alias is disabled, so the system never resolves it (declared but inert; no `src/debug` manifests needed). `app/recipekeeper/build.gradle.kts` has one `val imagesSource = false` feeding both `BuildConfig.IMAGES_SOURCE` and `manifestPlaceholders["imagesShareTarget"]`.

## Label fields other brands must fill
None new: `ModelManagementLabels.capabilityVision` (I0) is filled in recipes, medical and recipekeeper. All chooser/origin copy was already in `RecipeKeeperLibraryLabels` (I0).

## Deviations / notes for the lead
1. **Prompt:** the repository has one system prompt per config, so `SYSTEM_IMAGES` could not be selected per input without touching core. The gate-decision guidance line travels at the head of the user turn (`IMAGES_USER_TURN`); `RecipeKeeperPromptBuilder.userPrompt` returns it untouched. If you want a real `SYSTEM_IMAGES`, add an input-aware hook in `LlmTaskConfig`/repository.
2. **Availability** is computed app-side over `LibraryModelStatusSource` (the feature's `ObserveInputCapabilitiesUseCase` was still `TODO("I2")`). Same four-way mapping; swap for a delegation at merge if preferred.
3. **"Pick different pictures":** `WebClipperExtractionRoute` has no `onPickDifferent` parameter in this base. `NavHostController.navigateToPickDifferentImages()` (popUpTo Extract inclusive → ClipImages) is ready, unused; wire it when I2's parameter lands.
4. A second image share while `clip/images` is open pops and recreates the screen with the new URIs.

## Lead must wire at merge
- `val imagesSource = true` in `app/recipekeeper/build.gradle.kts` (flips the row, `ImagePolicy.enabled` and the share alias together).
- Optionally swap `ObserveImagesAvailabilityUseCase` body for the feature use case; pass `onPickDifferent`.
- Run `SourceChooserSemanticsTest` in I4 (not run on 5556 here).

## Verify
`./gradlew :app:recipekeeper:testDebugUnitTest :app:recipekeeper:assembleDebug :app:recipes:assembleDebug :app:medical:assembleDebug :feature:modelmanagement:testDebugUnitTest :feature:webclipper:testDebugUnitTest :app:recipekeeper:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL.
