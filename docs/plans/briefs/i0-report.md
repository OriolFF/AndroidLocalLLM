# I0 report — input-sources contract freeze

Branch `inputs/i0-contracts`. Additive only; users see no change (`IMAGES_SOURCE = false`, sheet not rendered, images route unreachable).

## New types / fields (file → what)

**`:core:model`** `LlmModelDescriptor.kt` — `enum ModelCapability { TEXT, VISION, AUDIO }`; `LlmModelDescriptor.capabilities: Set<ModelCapability> = setOf(TEXT)` (catalog untouched; I1 adds VISION to Gemma 4 E2B).

**`:core:domain`**
- `core/llm/engine/LlmRequest.kt` — `LlmRequest.images: List<LlmImage> = emptyList()`, `LlmRequest.visualTokenBudget: Int? = null`; new `class LlmImage(bytes, mimeType="image/jpeg", width=0, height=0)`, `data class LlmCapabilities(vision, audio, maxImages)`, `class UnsupportedImagesException`.
- `core/llm/engine/LlmEngine.kt` — `fun capabilities(): LlmCapabilities = LlmCapabilities()`.
- `core/domain/repository/LlmTaskRepository.kt` — `LlmRequestOptions.images`, `.visualTokenBudget` (defaults).
- `:core:data` `LlmTaskRepositoryImpl` copies both onto the `LlmRequest` (both branches).

**`:core:llm`** `LiteRtLlmEngine.generateStream` — only change: non-empty `request.images` → `LlmToken.Error(UnsupportedImagesException(...))` and return (I1 replaces). `FakeLlmEngine` (core/llm/src/test) already records `requests`/`lastRequest`; unchanged.

**`:core:web`** `LocalImageImporter.kt` (`LocalImageImporter`, `ImportedImage`), `ContentResolverLocalImageImporter.kt` (bodies `TODO("I2")`); `WebModule` += `single<LocalImageImporter>`.

**`:feature:webclipper`** (package `com.llmlocal.feature.webclipper`)
- `domain/input/ClipInput.kt` — `ClipInput {WebPage, Images, Voice}`, `ClipImage`, `ClipInput.snapshotOrNull`, plus (beyond plan) `enum ClipInputKind {WEB, IMAGES, VOICE}` and `ClipInput.kind`.
- `domain/input/ImagesAvailability.kt`; `domain/input/ClipInputInbox.kt` (`PendingClipInput`, `ClipInputInbox`); `data/InMemoryClipInputInbox.kt` (fully implemented, CAS consume).
- `domain/ClipCandidate.kt` — `CandidateSource += IMAGES, TRANSCRIPT`; `CandidatePayload += Images(count), Transcript`; `IMAGES_CANDIDATE_ID`; `imagesCandidate(input, title)`.
- `domain/ClipSession.kt` — `Current(input, candidates)` with `snapshot: PageSnapshot?` convenience; `set(input, …)` abstract, `set(snapshot, …)` kept as default delegating. `InMemoryClipSession` implements the input overload.
- `domain/ClipProgress.kt` — `ClipRequest(candidate, input, …)` + kept secondary ctor `(candidate, snapshot, …)`; `ClipRequest.snapshot: PageSnapshot?`; `ExtractionError.ImagesUnsupported`.
- `domain/WebClipConfig.kt` — `ImagePolicy(enabled=false, maxImages=3, visualTokenBudget=280, maxLongEdgePx=2400, tallAspect=2.5, tileImages=true)` (gate values), `imagePolicy`, `renderImagesPrompt` (= `DEFAULT_IMAGES_PROMPT` "Extract the recipe from these pictures."), `candidatesFor(Images, title)`, `renderLlmInput(candidate, ClipInput, budget)`.
- `domain/ClipSink.kt` — `save(item, input: ClipInput, …, options)` (web delegates, others throw `UnsupportedOperationException`), `existingId(input)`.
- `domain/LlmGenerationSource.kt` — `generate(..., images = emptyList(), visualTokenBudget = null)`, `capabilities()`; `EngineLlmGenerationSource` forwards through `LlmRequestOptions` and delegates `capabilities()` to the engine.
- `domain/usecase/` — `PrepareImagesUseCase` (+`Result`), `DiscardPreparedImagesUseCase`, `ObserveInputCapabilitiesUseCase` (all `TODO("I2")`); `CheckDuplicateUseCase` + `invoke(sink, input: ClipInput)` overload.
- `presentation/mvi/ExtractionState.kt` — `inputKind: ClipInputKind = WEB`, `imageCount: Int = 0`; `ExtractionViewModel` fills them from the session.
- `presentation/images/` — `ImagesClipState`, `ImagesClipIntent`, `ImagesClipEffect` (+`ImagesNotice`), `ImagesClipViewModel` (constructible; `onIntent` is a no-op, no init work), `WebClipperImagesRoute` (renders `AppLoadingIndicator`).
- Labels: `WebClipperLabels` += the 28 UX §2.9 fields (no defaults: `imagesTitle … imagesUnsupportedMessage`, `backToLibrary`, `clipAlreadyRunning`; plurals `imagesSkipped`, `imagesSourceStrip`), `WebClipperLabelsFactory.webClipperLabels`, `strings_webclipper.xml` keys exactly as the spec (`webclipper_images_*`, `webclipper_extraction_phase_reading_images`, `webclipper_extraction_back_to_library`, `webclipper_clip_already_running`), `TestWebClipperLabels` (each renders its own name).
- `WebClipperExtractionScreen`: `ExtractionError.ImagesUnsupported` branch (images-unsupported title/message + Open models + Back) in `ErrorBlock` and `errorTitle`.

**`:feature:modelmanagement`** `ModelManagementLabels.capabilityVision` (not rendered yet); `DefaultModelManagementLabels`, `strings_modelmanagement.xml` (`modelmanagement_capability_vision`), `TestModelManagementLabels`.

**Apps** — `app/{recipes,medical,recipekeeper}`: `*ModelManagementLabels.kt` + `strings_modelmanagement.xml` (`recipes_/medical_/recipekeeper_modelmanagement_capability_vision` = "Reads images").

**`:app:recipekeeper`**
- `RecipeOrigin {CLIPPED, CREATED, IMAGES, VOICE}` (entity mapping already mapped unknown → CLIPPED; round-trip test added); `RecipeSource`; `LibraryModelStatus.Ready(supportsImages = false)` data class (`ReadinessLibraryModelStatusSource` maps `Ready(descriptor)` → `VISION in descriptor.capabilities`, `Initializing` → `Ready()`; `LibraryScreen`, previews, `FakeLibraryModelStatusSource`, `LibraryViewModelTest` updated).
- `LibraryIntent` += `OpenSourceChooser`, `DismissSourceChooser`, `ChooseSource(source)`, `DismissImagesUnavailable`; `LibraryEffect.NavigateToImagesClip`; `LibraryState` += `sourceChooserOpen`, `imagesAvailability = Disabled`, `imagesUnavailableDialog`. The ViewModel sets/clears `sourceChooserOpen` and `imagesUnavailableDialog`; `ChooseSource` just closes the sheet (I3 routes it). `LibraryRoute(onOpenImagesClip = {})` handles the new effect.
- `ObserveImagesAvailabilityUseCase` (stub, always `Disabled`), injected into `LibraryViewModel` (new ctor param `observeImagesAvailability`, test updated).
- `Route.ClipImages("clip/images")` composable → `WebClipperImagesRoute`; `onOpenImagesClip` navigation; extract `onSaved`: `popUpTo(if (candidateId == IMAGES_CANDIDATE_ID) Library else Browser)`.
- `RecipeKeeperClipConfig.imagePolicy = ImagePolicy(enabled = BuildConfig.IMAGES_SOURCE)`; `build.gradle.kts`: `buildConfigField("boolean", "IMAGES_SOURCE", "false")`, `manifestPlaceholders["imagesShareTarget"] = "false"` (unused by the manifest).
- `RecipeKeeperLibraryLabels` += `addRecipe, moreWaysToAdd, sourceChooserTitle/Subtitle, sourceWeb/Images/Voice/Editor (+Hint), comingSoon, imagesNeedsModel, imagesModelCantRead, imagesUnavailableTitle, imagesUnavailableMessage(String), openModels (reuses recipekeeper_choose_model_action), fromYourPhotos, fromYourPhotosCheckAmounts (gate; key recipekeeper_from_your_photos_check_amounts), recipeTileDescriptionImages(String), addedOn(String)`; keys `recipekeeper_<snake>` per UX §1.5; `TestRecipeKeeperLibraryLabels` updated. App override `webclipper_extraction_back_to_library` = "Back to your recipes" in `app/recipekeeper/.../strings_webclipper.xml`.

## Existing signatures changed and how callers stayed compiling
- `ClipSession.Current(snapshot, candidates)` → `Current(input, candidates)`; `.snapshot` is now a nullable computed property. Existing call sites already used `?.` (`ExtractionViewModel` ×4, `BrowserViewModel` ×3), so **no source change was needed** there; `session.set(snapshot, …)` still works (default method).
- `ClipRequest.snapshot` is now `PageSnapshot?`: only `ClipUseCase` read it as non-null → `invoke` emits `Failed(ModelFailed("Unsupported clip input: …"))` for non-web input and passes the non-null snapshot into `Run`. **I2 replaces that early return with the images/voice branches.**
- `LibraryModelStatus.Ready` object → data class: every `Ready` reference became `Ready()` / `is Ready`.
- `LlmGenerationSource.generate` gained two defaulted params (fake implementors in tests unchanged; none exist).
- Exhaustive `when`s fixed with web/text behaviour: `RecipeKeeperClipConfig.renderLlmInput` (new payloads → whole-page render), `rankPictures` (→ no own pictures), `EvalFixtures.tierOf` (test), `ShareRecipeUseCase` (IMAGES/VOICE → the CLIPPED template; I3 switches it to the created template), extraction screen error branches.

## DI registrations
- `webModule`: `single<LocalImageImporter> { ContentResolverLocalImageImporter(androidContext(), get()) }`.
- `webClipperModule`: `single<ClipInputInbox> { InMemoryClipInputInbox() }`; `factory { PrepareImagesUseCase(importer = get(), tempPictures = get(), dispatchers = get()) }`; `factory { DiscardPreparedImagesUseCase(tempPictures = get()) }`; `factory { ObserveInputCapabilitiesUseCase(readiness = get(), config = get<WebClipConfig<*>>()) }` (the app binds the config single); `viewModel { params -> ImagesClipViewModel(pendingUris = params.getOrNull<List<String>>(), savedStateHandle = get(), config = params.get<WebClipConfig<*>>(), labels = params.get<WebClipperLabels>(), session = get(), prepareImages = get(), discardImages = get(), observeCapabilities = get(), dispatchers = get()) }` — callers pass `parametersOf(config, labels[, pendingUris])`.
- `recipesModule`: `factory { ObserveImagesAvailabilityUseCase() }`; `LibraryViewModel` gets `observeImagesAvailability = get()`.

## Tests / builds
`./gradlew :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug :feature:webclipper:testDebugUnitTest :app:recipekeeper:testDebugUnitTest :core:domain:test :core:llm:testDebugUnitTest :core:web:testDebugUnitTest :core:data:testDebugUnitTest :feature:modelmanagement:testDebugUnitTest` → BUILD SUCCESSFUL. Results (XML): webclipper 67, recipekeeper 261, core:llm 18, core:web 38, modelmanagement 13, core:domain 3 — 0 failures; `:core:data` has no tests. New tests: `InMemoryClipInputInboxTest` (3), `RecipePictureStoreAndConvertersTest` origin round trip + unknown → CLIPPED. Boundary check: `:feature:webclipper releaseRuntimeClasspath` has 0 `project :app` edges (a plain `grep -c ':app'` also matches `androidx.appcompat`, and the "Configure project :app:…" lines when not `-q`). `:app:recipekeeper:lint` finished without errors.

## For I1 / I2 / I3
- **I1**: replace the early `UnsupportedImagesException` in `LiteRtLlmEngine.generateStream`; `ImagePolicy.visualTokenBudget` is 280 (engine must clamp, gate decision). `ModelCapability` exists; catalog untouched.
- **I2**: `ClipUseCase` currently fails non-web inputs (see above); `PrepareImagesUseCase/Discard/ObserveInputCapabilities`, importer, `ImagesClipViewModel.onIntent` and `WebClipperImagesRoute` are stubs. `ExtractionViewModel` already sets `inputKind`/`imageCount` but still counts `otherCandidates` and runs `checkDuplicateThenStart` through the snapshot (`session.current.value?.snapshot ?: …` pops when null — that must become input-aware). Labels for the images screens are all in `WebClipperLabels` (`TestWebClipperLabels` renders names).
- **I3**: `ChooseSource` currently only closes the sheet; `ObserveImagesAvailabilityUseCase` is a constant `Disabled`; `ModelManagementLabels.capabilityVision` is filled but not rendered; manifest does not use `imagesShareTarget` yet; share template for IMAGES/VOICE should become the created template. Flip `IMAGES_SOURCE` at merge (build.gradle.kts) together with the manifest placeholder.
