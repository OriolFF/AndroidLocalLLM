# Recipe Keeper — input sources (web / images / voice) and screenshot extraction: implementation plan

Status: PLAN, gate decided (see below). Nothing implemented yet. Written 2026-10-24 from the repo state at `ab2eadb` (branch `recipekeeper/dev`). Companion: `recipekeeper-input-sources-ux-spec.md` (screens, states, labels, decisions S1–S11). Style and conventions follow `recipekeeper-app-plan.md` / `recipekeeper-features-plan.md`: contracts first, parallel streams with disjoint file ownership, a report per stream, QA last.

Rules every stream honours (CLAUDE.md Architecture rules): **0** no user-visible string literal in Kotlin (resources + label classes without defaults); **1** ViewModels take use cases only (plus `DispatcherProvider`, `SavedStateHandle`, labels/config, route params); **2** `presentation / domain / data / di` packages, `presentation → domain ← data`, `domain` pure Kotlin; **3** MVI, brand values in the app module.

> **The gate.** `docs/research/screenshot-extraction-eval.md` (being written by the eval agent) decides whether I1's vision wiring and I2's images path are built. **I0 and I3 ship regardless**: the source chooser, the voice placeholder, the `origin = IMAGES` plumbing and the URL share target. If the eval says "not feasible", `WebClipConfig.imagePolicy.enabled = false` hides the images row, the `image/*` share filters are not declared, and I1/I2 shrink to the parts listed under "without vision" in §5. Numbers marked **[eval]** (visual token budget, max images, tiling, time copy) are defaults to be replaced by the eval's recommendation.

## Gate decision (lead, 2026-10-24, from `docs/research/screenshot-extraction-eval.md`)

**Vision: YES, with limits.** The eval's verdict is "feasible with limits": build the full I1/I2 step lists for the image-only / shared-screenshot flow; **never use vision in the web-clipper path** (DOM/structured capture stays the only path for pages). Numbers that replace the **[eval]** placeholders:

| Setting | Value | Reason (eval §) |
|---|---|---|
| `IMAGES_SOURCE` build flag / `ImagePolicy.enabled` | `true` | §7 |
| `visualTokenBudget` | **280, fixed** — the shipped `.litertlm` caps vision at 280 tokens per image; 560/1120 are rejected by the engine | §2 |
| `maxImages` | **3** (the use case caps at the policy value; copy says "up to 3 pictures") | §7 "1–3 images" |
| `maxLongEdgePx` | **2400** — keep phone screenshots at native size (the eval measured 1079×2397); the engine downsamples to the budget itself | §1, §8 |
| `tileImages` / `tallAspect` | `true` / `2.5` — never send a tall strip; split it into overlapping viewport-shaped tiles (tiles count toward the cap) | §4 "Tall strips" |
| Content order | images first, then the user text (as measured) | §1 |
| Android budget API | `ExperimentalFlags.visualTokenBudget` per message (I1 verifies with `javap`); values above the bundle cap are an `INVALID_ARGUMENT`, so the engine clamps to 280 and logs | §2 |
| Prompt | `RecipeKeeperPromptBuilder` gains `SYSTEM_IMAGES` = `SYSTEM` + one line: *"The pictures are consecutive screenshots or photos of one recipe; read only text that is visible; do not guess amounts you cannot read; omit unknown fields."*; the user turn is `Extract the recipe from these pictures.` (model-facing literals, accepted) | §8 |
| Time copy | keep "usually under a minute per picture" (flagship CPU estimate: 15–20 s for one, 25–40 s for three) | §5 |
| Guardrail (I2, should) | when the model answers `NO RECIPE` / an empty template for ≥ 2 images, retry **once** automatically with the first ⌈n/2⌉ images before showing "No recipe found in these pictures" | §4 "Refusals", §7 |
| Guardrail (I3) | detail source row and hero for `origin = IMAGES` read "From your photos · Check the amounts" (label `fromYourPhotosCheckAmounts`), because quantities are the weak spot | §7 |
| Vision backend | CPU only in this bundle (`ModelInfo.vision backends = ['cpu']`); `LLM_VISION_BACKEND` is `"CPU"` for both build types until a bundle with a GPU vision tower exists | §5 |

Everything else in the plan stands. The eval outputs (`tools/eval/screenshots/outputs/*.txt`) are the goldens for `EvalImagesParserTest` (I2 copies them).

## Post-QA status (lead, 2026-10-24)

I0–I4 are merged on `recipekeeper/dev` (`IMAGES_SOURCE = true`). `docs/plans/briefs/i4-qa-report.md`: no P1; the three P2s are fixed and re-verified on `emulator-5556` by the lead (share receiver no longer dies on an ungranted URI — the share is dropped with a log line; the extraction screen now hosts the shared snackbar; the source chooser scrolls in a 300 dp-tall window). Also fixed: single-`Uri` `SEND_MULTIPLE` shares (P3-7) and the dialog's "Reads images" wording (P3-5). **Decided**: the detail/hero line keeps "From your photos · Check the amounts" (P3-4 — the gate decision, quantities are the weak spot); the tile keeps the short form. **Open P3 backlog**: failure screen still shows "Saving pictures" done + the keep strip (P3-2); empty review visible behind the Photo Picker (P3-3); time-hint row partly under the bottom bar before scrolling and left-aligned grid on wide windows (P3-1); own "Share recipe" lists the app as a target (P3-6); picker oddity at 1280x800 @ 240 dpi (P3-8); low-text picture warning (P3-9, Phase 2). Not verified on device: empty-library "More ways to add…", screen-reader custom actions, Chrome URL share, web "Reading flips at first token" on a model-path page.

---

## 0. What we are building

1. **Source chooser** — library "+" → `ModalBottomSheet` "Add a recipe" with From the web / From images / By voice (Coming soon) / Write it yourself; FAB stays "Open browser". (UX spec §1)
2. **Images source [eval]** — Photo Picker and system share target (`image/*`) → prepare/review screen → the existing extraction screen with an image-bearing `LlmRequest` → saved with `origin = IMAGES`, pictures = the input images. (UX spec §2)
3. **URL share target** — `text/plain` share with a URL opens the in-app browser on it. (UX spec §2.2)
4. **Voice placeholder** — disabled row + the `ClipInput.Voice` contract, nothing else. (UX spec §3)
5. **Engine vision capability [eval]** — `LlmRequest.images`, `LiteRtLlmEngine(visionBackend, …)`, a `capabilities()` query, `ModelCapability.VISION` on descriptors.

Out of scope (Phase 2): camera capture, voice recording/transcription, content-hash duplicate check (`sourceKey` column), re-extract from saved pictures, Direct Share shortcuts, `ACTION_VIEW` for http links.

---

## 1. Architecture decisions

| # | Decision | Why |
|---|---|---|
| V1 | **Input abstraction in `:feature:webclipper`: `sealed interface ClipInput { WebPage(snapshot); Images(images); Voice(transcript) }`.** `ClipSession.Current` carries an `input` (its `snapshot` becomes a nullable convenience); `ClipRequest`, `ClipSink.save`, `WebClipConfig.renderLlmInput` get `ClipInput`-aware overloads **with defaults that delegate to the existing `PageSnapshot` versions**, so every current implementor and test compiles after I0. The Gradle module and package keep the name `webclipper`; docs may say "clipper". | The pipeline (phases, streaming, pictures, save, cancel, duplicate dialog) is source-independent; only the first phase and the request differ. Defaults keep the three streams compiling in parallel (same mechanism as F0/P1). Renaming the module would touch every file for no behaviour. |
| V2 | **Images are one synthetic candidate** (`IMAGES_CANDIDATE_ID = "images"`, `CandidatePayload.Images`, `CandidateSource.IMAGES`), no detection step. `WebClipConfig.candidatesFor(input: ClipInput.Images)` has a default that builds it; apps may override later (e.g. split a cookbook spread into two recipes). | There is nothing to detect in a screenshot without running the model; one candidate keeps `extract/{candidateId}` and `ExtractionViewModel` unchanged in shape. |
| V3 | **`LlmRequest.images: List<LlmImage>`** (non-data class holding `bytes` + `mimeType`), carried by `LlmRequestOptions.images` through `LlmTaskRepositoryImpl` and `GenerateLlmTaskUseCase`; `LlmEngine.capabilities(): LlmCapabilities` default `LlmCapabilities(vision = false)`. The engine rejects images it cannot take with `LlmToken.Error(UnsupportedImagesException)` → `ExtractionError.ImagesUnsupported`. | Additive; `LlmRequest(prompt)` keeps the legacy meaning. The repository already maps options → request. A typed exception lets the UI say "this model can't read images" instead of a generic failure. |
| V4 | **`LiteRtLlmEngine` builds the engine with `visionBackend` whenever the selected descriptor has `ModelCapability.VISION` and the build enables vision** (`BuildConfig.LLM_VISION_BACKEND`: debug `"CPU"`, release `"GPU"`, `"NONE"` disables). `maxNumImages = imagePolicy.maxImages` (5). The visual token budget is set per request where the 0.18 API allows it (**verify with `javap` in I1**; research notes only name `ExperimentalFlags.visualTokenBudget`). | `initialize()` is idempotent and the engine is a singleton; re-creating it to add vision would reload 2.6 GB. The vision tower is loaded on demand by LiteRT-LM, so text-only clips pay nothing. |
| V5 | **`ModelCapability { TEXT, VISION, AUDIO }` on `LlmModelDescriptor.capabilities` (default `setOf(TEXT)`)**; only `gemma-4-E2B-it` is marked `VISION` until the q4 bundle is inspected. Readiness stays `ModelReadiness.Ready(descriptor)`; consumers read `descriptor.capabilities`. The feature exposes `ObserveInputCapabilitiesUseCase` → `ImagesAvailability { Available, NoModel, ModelLacksVision, Disabled }`. | `ModelFamily.MULTIMODAL` is a filter chip grouping, not a capability; a set scales to audio for voice. No readiness state machine change. |
| V6 | **Prepared images live in the clipper temp dir** (`TempPictureStore`), produced by `PrepareImagesUseCase` over a new **`LocalImageImporter` in `:core:web`** (content/file URI → EXIF-rotated JPEG ≤ `maxLongEdgePx`, SHA-256, optional tall-image tiling). The sink moves them into `filesDir/recipes/<id>/` exactly like downloaded pictures; cancel/back deletes them. | `:core:web` already owns image bytes handling (`ImageDownloader`) and is brand-free; the feature cannot use the app's `PictureImporter`. Same lifecycle as downloads means `ClipUseCase`'s temp-file bookkeeping needs no new branch. |
| V7 | **`ImagePolicy(enabled, maxImages = 5, visualTokenBudget = 280, maxLongEdgePx = 1536, tallAspect = 2.5, tileImages = false)` on `WebClipConfig`** with defaults; Recipe Keeper's config overrides from the eval. `maxImages` is capped by the token arithmetic `(maxNumTokens − maxOutputTokens − 256) / visualTokenBudget` at runtime. | One place for every [eval] number; the arithmetic prevents an over-long request from failing inside the native layer. |
| V8 | **Share target = `ShareReceiverActivity` → `MainActivity` (`singleTop`) → `ClipInputInbox` (feature domain, Koin single) → `RecipeKeeperNavHost` navigates.** No Navigation deep links; URIs travel as `ClipData` with read grants. | Content URIs do not survive string routes; an inbox keeps the Activity thin and the NavHost the single navigation owner. A dedicated receiver activity keeps `MainActivity`'s launch semantics and gives `excludeFromRecents`/`noHistory` for free. |
| V9 | **`RecipeOrigin` gains `IMAGES` and `VOICE`; no Room migration** (text column, default `'CLIPPED'`, unknown names map to `CLIPPED`). DB stays at version 3; `3.json` untouched. | Verified in `RecipeEntity.kt`. A migration would only be needed for a duplicate-check key, which is Phase 2. |
| V10 | **No duplicate check for images in v1**; `ClipSink.existingId(input)` default returns `null` for non-web inputs. | UX spec S7. |
| V11 | **Phase 1 ("Reading …") stays Active until the first streamed token for every input.** | For images the prefill is the slow part; today Reading flips to Done before the model runs because reading was text rendering. One rule, truthful progress; harmless for web pages. |
| V12 | **No new `:core:designsystem` primitives; the `ModelManagementLabels` class gains one field (`capabilityVision`)** so the model list can show "Reads images" from `descriptor.capabilities` without a literal in `:core:llm`. Both other apps fill it. | Rule 0; smallest shared touch. |
| V13 | **Contract freeze (I0) is small and compiles with zero behaviour change**: new types, additive fields with defaults, enum values, label fields + strings, `TODO("I1"/"I2"/"I3")` bodies where a stream owns the implementation. Then I1 ∥ I2 ∥ I3, then I4. | Same mechanism as WS0/F0; disjoint ownership (§5) keeps three agents off each other's files. |

### Module graph after the change

```
:app:recipekeeper   → unchanged edges (+ ShareReceiverActivity, chooser, labels, origin branches)
:feature:webclipper → unchanged edges (+ ClipInput, images route, inbox, PrepareImagesUseCase over :core:web LocalImageImporter)
:core:web           → unchanged edges (+ LocalImageImporter)
:core:llm           → unchanged edges (+ visionBackend wiring, capabilities)
:core:domain        → unchanged (LlmRequest.images, LlmCapabilities, LlmRequestOptions.images)
:core:model         → unchanged (ModelCapability)
```

No new catalog entries. Boundary check unchanged: `./gradlew :feature:webclipper:dependencies --configuration releaseRuntimeClasspath` must show no `:app`.

---

## 2. Contracts to freeze (I0)

Written by the lead before the streams start. Signatures may grow; renames go through the lead.

### 2.1 `:core:model`, `:core:domain` — capability and request

```kotlin
// :core:model
enum class ModelCapability { TEXT, VISION, AUDIO }
data class LlmModelDescriptor(/* existing … */, val capabilities: Set<ModelCapability> = setOf(ModelCapability.TEXT))
// catalog (I1): DEFAULT_MODEL.capabilities = setOf(TEXT, VISION); others unchanged until inspected

// :core:domain, package com.llmlocal.core.llm.engine
/** One picture for the model. Not a data class: ByteArray equality is identity and that is fine here. */
class LlmImage(val bytes: ByteArray, val mimeType: String = "image/jpeg", val width: Int = 0, val height: Int = 0)

data class LlmRequest(
    val prompt: String,
    val systemInstruction: String? = null,
    val maxOutputTokens: Int? = null,
    val jsonSchema: String? = null,
    val temperature: Double? = null,
    val images: List<LlmImage> = emptyList(),      // NEW; sent before the prompt text (order configurable in I1 if the eval says otherwise)
    val visualTokenBudget: Int? = null,            // NEW; null = runtime default
)

data class LlmCapabilities(val vision: Boolean = false, val audio: Boolean = false, val maxImages: Int = 0)

interface LlmEngine {
    /* existing … */
    /** What the loaded engine can take; meaningful after [initialize]. Default: text only. */
    fun capabilities(): LlmCapabilities = LlmCapabilities()
}

/** Thrown inside LlmToken.Error when [LlmRequest.images] is non-empty and the engine has no vision. */
class UnsupportedImagesException(message: String) : IllegalStateException(message)

// :core:domain, repository
data class LlmRequestOptions(
    val systemInstruction: Boolean,
    val maxOutputTokens: Int? = null,
    val temperature: Double? = null,
    val jsonSchema: String? = null,
    val images: List<LlmImage> = emptyList(),      // NEW
    val visualTokenBudget: Int? = null,            // NEW
)
```

`LlmTaskRepositoryImpl` (I0, one line each) copies `images` and `visualTokenBudget` onto the `LlmRequest`. `FakeLlmEngine` (`core/llm/src/test`) records `lastRequest.images` and exposes `capabilities` as a constructor flag (I1).

### 2.2 `:core:web` — local images

```kotlin
/** A picked picture made model- and storage-ready: JPEG q85, EXIF applied, long edge ≤ maxLongEdgePx. */
interface LocalImageImporter {
    suspend fun import(uri: String, into: File, maxLongEdgePx: Int): Outcome<ImportedImage>
    /** Splits a tall [source] into overlapping tiles of aspect ≈ 1:1.6; `listOf(source)` when not tall. */
    suspend fun tile(source: ImportedImage, intoDir: File, tallAspect: Double, overlap: Double = 0.10): Outcome<List<ImportedImage>>
}
data class ImportedImage(val file: File, val width: Int, val height: Int, val sha256: String)
class ContentResolverLocalImageImporter(context: Context, dispatchers: DispatcherProvider) : LocalImageImporter
// webModule += single<LocalImageImporter>
```

### 2.3 `:feature:webclipper` — input, session, candidate, config, sink, inbox, use cases

```kotlin
// domain/input/ClipInput.kt
sealed interface ClipInput {
    data class WebPage(val snapshot: PageSnapshot) : ClipInput
    data class Images(val images: List<ClipImage>) : ClipInput          // in reading order
    data class Voice(val transcript: String, val language: String? = null) : ClipInput   // placeholder, unused
}
data class ClipImage(val file: File, val width: Int, val height: Int, val sha256: String, val tileOf: Int? = null)

val ClipInput.snapshotOrNull: PageSnapshot? get() = (this as? ClipInput.WebPage)?.snapshot

// ClipCandidate.kt (additive)
enum class CandidateSource { STRUCTURED, TEXT, IMAGES, TRANSCRIPT }
sealed interface CandidatePayload { /* existing */; data class Images(val count: Int) : CandidatePayload; data object Transcript : CandidatePayload }
const val IMAGES_CANDIDATE_ID = "images"
fun imagesCandidate(input: ClipInput.Images, title: String): ClipCandidate   // title from labels (rule 0): the route passes labels.imagesTitle

// ClipSession.kt
interface ClipSession {
    data class Current(val input: ClipInput, val candidates: List<ClipCandidate>) {
        /** Web-only convenience; `null` for images/voice. Existing call sites that assume a page switch to it in I2. */
        val snapshot: PageSnapshot? get() = input.snapshotOrNull
    }
    fun set(input: ClipInput, candidates: List<ClipCandidate>)
    fun set(snapshot: PageSnapshot, candidates: List<ClipCandidate>) = set(ClipInput.WebPage(snapshot), candidates)   // kept
    /* rest unchanged */
}

// ClipProgress.kt
class ClipRequest(val candidate: ClipCandidate, val input: ClipInput, val replaceId: String? = null, val isPictureKept: (String) -> Boolean = { true }) {
    constructor(candidate: ClipCandidate, snapshot: PageSnapshot, replaceId: String? = null, isPictureKept: (String) -> Boolean = { true }) :
        this(candidate, ClipInput.WebPage(snapshot), replaceId, isPictureKept)
    val snapshot: PageSnapshot? get() = input.snapshotOrNull
}
sealed interface ExtractionError { /* existing */; data object ImagesUnsupported : ExtractionError }

// WebClipConfig.kt (additive, all with defaults)
data class ImagePolicy(
    val enabled: Boolean = false,          // Recipe Keeper flips to true when the eval says go
    val maxImages: Int = 5,                // [eval]
    val visualTokenBudget: Int = 280,      // [eval]
    val maxLongEdgePx: Int = 1_536,        // [eval]
    val tallAspect: Double = 2.5,
    val tileImages: Boolean = false,       // [eval]
)
interface WebClipConfig<T : Any> {
    /* existing … */
    val imagePolicy: ImagePolicy get() = ImagePolicy()
    /** The user turn for an images clip; the pictures travel on the request, not in the text. */
    fun renderImagesPrompt(input: ClipInput.Images): String = DEFAULT_IMAGES_PROMPT   // model-facing text, not user-visible (accepted literal)
    fun candidatesFor(input: ClipInput.Images, title: String): List<ClipCandidate> = listOf(imagesCandidate(input, title))
    /** Dispatches on the input; web → existing renderLlmInput. */
    fun renderLlmInput(candidate: ClipCandidate, input: ClipInput, budgetChars: Int): String = when (input) {
        is ClipInput.WebPage -> renderLlmInput(candidate, input.snapshot, budgetChars)
        is ClipInput.Images -> renderImagesPrompt(input)
        is ClipInput.Voice -> input.transcript.take(budgetChars)
    }
}

// ClipSink.kt (additive)
interface ClipSink<T : Any> {
    /* existing three members … */
    /** Input-aware save; the default serves web pages and refuses the rest, so an app opts in per input kind. */
    suspend fun save(item: T, input: ClipInput, candidate: ClipCandidate, pictures: List<File>, truncated: Boolean, options: ClipSaveOptions): String =
        when (input) {
            is ClipInput.WebPage -> save(item, input.snapshot, candidate, pictures, truncated, options)
            else -> throw UnsupportedOperationException("sink does not support ${input::class.simpleName}")
        }
    suspend fun existingId(input: ClipInput): String? = input.snapshotOrNull?.let { existingId(it) }
}

// domain/input/ClipInputInbox.kt — what the share receiver hands over
sealed interface PendingClipInput { data class Images(val uris: List<String>) : PendingClipInput; data class Url(val url: String) : PendingClipInput }
interface ClipInputInbox { val pending: StateFlow<PendingClipInput?>; fun offer(input: PendingClipInput); fun consume(): PendingClipInput? }
// data/InMemoryClipInputInbox.kt; webClipperModule += single<ClipInputInbox>

// domain/input/ImagesAvailability.kt
enum class ImagesAvailability { Available, NoModel, ModelLacksVision, Disabled }

// domain/usecase (new; bodies TODO("I2") in I0)
class ObserveInputCapabilitiesUseCase(readiness: ReadinessSource, config: WebClipConfig<*>) { fun observe(scope: CoroutineScope): StateFlow<ImagesAvailability> }
class PrepareImagesUseCase(importer: LocalImageImporter, tempPictures: TempPictureStore, dispatchers: DispatcherProvider) {
    data class Result(val images: List<ClipImage>, val skipped: Int, val capped: Boolean)
    suspend operator fun invoke(uris: List<String>, policy: ImagePolicy): Result
}
class DiscardPreparedImagesUseCase(tempPictures: TempPictureStore) { operator fun invoke(images: List<ClipImage>) }
// LlmGenerationSource (additive)
interface LlmGenerationSource {
    fun <T : Any> generate(config: LlmTaskConfig<T>, input: String, maxOutputTokens: Int, images: List<LlmImage> = emptyList(), visualTokenBudget: Int? = null): Flow<LlmTaskEvent<T>>
    fun capabilities(): LlmCapabilities = LlmCapabilities()
}
```

### 2.4 `:feature:webclipper` — presentation stubs (I0 declares, I2 fills)

```kotlin
// presentation/images/
data class ImagesClipState(
    val images: List<ClipImage> = emptyList(), val selectedKey: String? = null,
    val isPreparing: Boolean = false, val skipped: Int = 0, val capped: Boolean = false,
    val availability: ImagesAvailability = ImagesAvailability.Available, val maxImages: Int = 5,
) { val needsReview get() = images.size >= 2; val canExtract get() = images.isNotEmpty() && availability == ImagesAvailability.Available }
sealed interface ImagesClipIntent { data class ImagesPicked(val uris: List<String>); data object PickerCancelled; data class Select(val key: String);
    data class Move(val from: Int, val to: Int); data class Remove(val key: String); data object AddMore; data object Extract; data object Close; data object OpenModelManagement }
sealed interface ImagesClipEffect { data object LaunchPicker; data class LaunchPickerFor(val remaining: Int); data class NavigateToExtraction(val candidateId: String);
    data object Closed; data object NavigateToModelManagement; data class Notify(val kind: ImagesNotice) }
enum class ImagesNotice { SKIPPED, CAPPED }      // the route maps to labels (rule 0)
@Composable fun <T : Any> WebClipperImagesRoute(config: WebClipConfig<T>, labels: WebClipperLabels, pendingUris: List<String>?, onNavigateToExtraction: (String) -> Unit,
    onClosed: () -> Unit, onNavigateToModelManagement: () -> Unit, snackbarHostState: SnackbarHostState?, onShowSnackbar: ((String) -> Unit)?, modifier: Modifier = Modifier)
class ImagesClipViewModel(pendingUris: List<String>?, savedStateHandle: SavedStateHandle, config: WebClipConfig<*>, labels: WebClipperLabels, session: ClipSession,
    prepareImages: PrepareImagesUseCase, discardImages: DiscardPreparedImagesUseCase, observeCapabilities: ObserveInputCapabilitiesUseCase, dispatchers: DispatcherProvider)
```

`ExtractionState` gains `inputKind: ClipInputKind` (`WEB, IMAGES, VOICE`) and `imageCount: Int` so the screen branches without touching the session. `WebClipperLabels` gains the §2.9 fields of the UX spec; `defaultWebClipperLabels()` / `webClipperLabels(resources)` read them; `TestWebClipperLabels` updated.

### 2.5 `:app:recipekeeper` — origin, chooser, share, labels

```kotlin
// recipes/domain
enum class RecipeOrigin { CLIPPED, CREATED, IMAGES, VOICE }
enum class RecipeSource { WEB, IMAGES, VOICE, EDITOR }                         // chooser rows
sealed interface LibraryModelStatus { /* existing */; data class Ready(val supportsImages: Boolean = false) : LibraryModelStatus }   // replaces the object

// recipes/presentation/library (stubs: intents handled, effects emitted, sheet composable placeholder)
sealed interface LibraryIntent { /* existing */; data object OpenSourceChooser; data object DismissSourceChooser; data class ChooseSource(val source: RecipeSource); data object DismissImagesUnavailable }
sealed interface LibraryEffect { /* existing */; data object NavigateToImagesClip }
data class LibraryState(/* existing */, val sourceChooserOpen: Boolean = false, val imagesAvailability: ImagesAvailability = ImagesAvailability.Disabled, val imagesUnavailableDialog: Boolean = false)
@Composable fun LibraryRoute(/* existing */, onOpenImagesClip: () -> Unit = {})
class LibraryViewModel(/* existing use cases */, observeInputCapabilities: ObserveImagesAvailabilityUseCase)   // app wrapper over the feature use case + config flag

// app
class ShareReceiverActivity : Activity()                                       // I3
object ShareIntentParser { fun parse(intent: Intent): PendingClipInput? }      // pure, unit-tested (I3)
// RecipeKeeperNavHost: Route.ClipImages("clip/images"), inbox observer, `extract/images` is the existing Extract route
// AndroidManifest: MainActivity launchMode="singleTop"; ShareReceiverActivity + filters (image/* filters behind the manifest placeholder `imagesShareTarget`)
```

Labels: `RecipeKeeperLibraryLabels` += UX spec §1.5 fields; `strings_recipekeeper.xml` += keys; `ModelManagementLabels.capabilityVision` + `strings_modelmanagement.xml` in the feature + the three apps' label factories; test fixtures updated. `RecipeKeeperClipConfig.imagePolicy` = `ImagePolicy(enabled = BuildConfig.IMAGES_SOURCE /* I3 adds the flag, default false until the eval */, …)`.

---

## 3. Specification

### 3.1 Engine (I1)

- `LiteRtLlmEngine(context, modelPathProvider, …, backend, visionBackend: Backend? = null, maxNumImages: Int = 0, descriptorProvider: () -> LlmModelDescriptor?)`; `buildEngine()` passes `visionBackend` and `maxNumImages` only when `descriptorProvider()?.capabilities` contains `VISION` and `visionBackend != null`; logs `vision=<backend|off>`.
- `generateStream(request)`: `images.isNotEmpty() && !capabilities().vision` → `emit(LlmToken.Error(UnsupportedImagesException(...)))`, return. Else `Contents.of(request.images.map { Content.ImageBytes(it.bytes) } + Content.Text(request.prompt))`. The budget: find the 0.18 API (`javap` on `litertlm-android-0.18.0.aar`; candidates: `ConversationConfig.extraContext`, `sendMessageAsync(extraContext = …)`, an `ExperimentalFlags`/`EngineSettings` type) and apply `request.visualTokenBudget`; if there is no per-request knob, set it at engine level from `imagePolicy.visualTokenBudget` and document it.
- `capabilities()` = `LlmCapabilities(vision = visionEnabled, maxImages = maxNumImages)`.
- `llmModule`: `visionBackend = buildVisionBackend(BuildConfig.LLM_VISION_BACKEND)` (`core/llm/build.gradle.kts`: debug `"CPU"`, release `"GPU"`; `"NONE"` → null), `maxNumImages = 5`, `descriptorProvider` from the selection store + catalog (same lambda shape as `modelPathProvider`).
- Catalog: `DEFAULT_MODEL.capabilities = setOf(TEXT, VISION)`; q4/Phi/Llama stay text until their bundles are inspected (note in the KDoc how: the `.litertlm` manifest lists the encoders).
- Known risks to check on device: research notes #2799 (SIGSEGV multi-image near the context limit → the V7 arithmetic keeps a margin), SME `SIGILL` on the emulator for vision kernels (`arm64.nosme` is already in the AVD config), peak memory with the vision tower.

### 3.2 Pipeline (I2)

- `ClipUseCase.Run`: `input = request.input`. Reading: web → `renderLlmInput(candidate, snapshot, budget)`; images → `renderImagesPrompt(input)` and `images.map { LlmImage(file.readBytes(), "image/jpeg", w, h) }` on `dispatchers.io`; voice → transcript. **Reading stays Active until the first `Streamed` delta (V11)**; on the first delta: Reading Done, Extracting Active. Direct path and `llm.initialize()` unchanged.
- `llm.generate(config.llmConfig, input, config.maxOutputTokens, images, policy.visualTokenBudget)`; `EngineLlmGenerationSource` forwards through `LlmRequestOptions`.
- Pictures for images: `proposed = images.map { it.file.toURI().toString() }`; emit `PicturesProposed(all keep = true)`, then `PictureChanged(url, Done, file)` for each, phase Done; the files are already in `tempFiles` (registered by `PrepareImagesUseCase` through the same `TempPictureStore`), so cancel deletes them and `finish` keeps the kept ones. No downloader call.
- Fallback: `candidate.source == IMAGES` → no `fallback`; parser null / `accept` false → `ParseFailed(rawText)`; the screen maps it to the images copy when `inputKind == IMAGES`. `UnsupportedImagesException` → `ExtractionError.ImagesUnsupported`.
- Save: `sink.save(result, input, candidate, kept, truncated, options)`; `session.clear()`.
- Duplicate: `CheckDuplicateUseCase(sink, input)`; `ExtractionViewModel` skips the dialog when the sink returns `null` (images do).
- `ExtractionViewModel.init`: `page = session.current.value`; `inputKind`/`imageCount` from `page.input`; `sourceHost`/`pageTitle` only for web. `Cancel` and `BackToCandidates` unchanged; for images `otherCandidates = 0`.
- `ImagesClipViewModel`: `init` → if `savedStateHandle` holds prepared paths → rebuild; else if `session.current.input is Images` (returning from "Pick different pictures") → take it; else if `pendingUris != null` → `prepare(pendingUris)`; else `LaunchPicker`. `ImagesPicked` → `isPreparing = true` → `prepareImages(uris, policy)` → state (+ `Notify(SKIPPED/CAPPED)`); `images.size == 1 && !fromReview` → auto `Extract`. `Extract` → `session.set(Images(images), config.candidatesFor(input, labels.imagesTitle))` → `NavigateToExtraction(IMAGES_CANDIDATE_ID)`. `Close`/`PickerCancelled` → `discardImages` (unless the images were handed to the session) → `Closed`. Prepared paths are saved in `SavedStateHandle` under `"images.prepared"` (JSON array of `file|w|h|sha|tileOf`).
- `ObserveInputCapabilitiesUseCase`: `readiness.observe(scope)` mapped: `!config.imagePolicy.enabled → Disabled`; `Ready(d) → if (VISION in d.capabilities) Available else ModelLacksVision`; `NoModel/Downloading/Checking/Initializing/Failed → NoModel`.
- `PrepareImagesUseCase`: sequential import on `io` (memory), `tile()` when `policy.tileImages && height > tallAspect × width`, cap at `policy.maxImages` after tiling (`capped = true` when cut), `skipped` = failed URIs; every produced file is registered with `tempPictures.newFile("images-<run>", index)` so the store owns them.
- `LocalImageImporter` (`:core:web`): `openInputStream` twice (bounds → `inSampleSize` → decode), `ExifInterface` rotation (add `androidx-exifinterface` to the catalog if not transitively present; Coil 3 brings it), JPEG q85 via `.part` + rename, SHA-256 streamed over the written file; `tile()` decodes once and writes crops.

### 3.3 App (I3)

- Chooser: `SourceChooserSheet(state, labels, onIntent)` (UX spec §1.2–§1.4) + `ImagesUnavailableDialog`; `LibraryRoute` top-bar `Add` → `OpenSourceChooser`; empty state second button → `moreWaysToAdd`; `LibraryViewModel.ChooseSource`: `WEB → NavigateToBrowser(null)`, `EDITOR → NavigateToEditor(null)`, `VOICE → no-op`, `IMAGES → when (availability) { Available → NavigateToImagesClip; NoModel → NavigateToModelManagement; ModelLacksVision → imagesUnavailableDialog = true; Disabled → unreachable }`. `ObserveImagesAvailabilityUseCase` (app, `recipes/domain/usecase`) wraps the feature use case (rule 1: the VM takes the app use case). `LibraryModelStatusSource` maps `Ready(descriptor)` → `Ready(supportsImages)`.
- Origin branches: `RecipeTile` meta/description, `RecipeDetailScreen` source row (`PhotoLibrary`, `fromYourPhotos`, `addedOn`), overflow and tile menu hide Open original / Re-extract for `IMAGES` and `VOICE` (treat as `!origin.hasSource`, an extension `val RecipeOrigin.hasSource get() = this == CLIPPED`), `ShareRecipeUseCase` uses the created template for any origin without a source, editor hides the source row likewise, `GradientHero` subtitle `fromYourPhotos`.
- Sink: `RecipeKeeperClipSink.save(item, input: ClipInput, …)`: `WebPage` → existing path; `Images` → `origin = IMAGES`, `sourceUrl = ""`, `sourceHost = ""`, title fallback `untitledTitle`, pictures moved in, `pictureCandidates = []`, no learning; `existingId(input)` → `null` for images.
- Share: `ShareReceiverActivity` (plain `Activity`, no `setContent`), `ShareIntentParser` (handles `EXTRA_STREAM` single/multiple with typed getters on 33+, `ClipData` items, `EXTRA_TEXT` URL regex `https?://\S+`, trims trailing punctuation), forwards to `MainActivity` with `ClipData` + grant flag; `MainActivity.onCreate/onNewIntent` → `inbox.offer(parsed)` (Koin `get()`); `RecipeKeeperNavHost`: `val pending by inbox.pending.collectAsStateWithLifecycle()`; `LaunchedEffect(pending) { inbox.consume()?.let { … } }` with the "clip already running" guard (`ClipSession.activeExtraction`).
- Routes: `Route.ClipImages("clip/images")`; `WebClipperImagesRoute(pendingUris = inbox payload or null, …, onNavigateToExtraction = { navigate(Route.Extract.build(it)) { popUpTo(Route.ClipImages.pattern) { inclusive = true } } }, onClosed = popBackStack)`; `onSaved` for the extract route: `if (candidateId == IMAGES_CANDIDATE_ID) popUpTo(library) else popUpTo(browser)`; extraction `onCancelled` pops (lands on the library for images). "Pick different pictures" → `onPickDifferent` → `navigate(ClipImages) { popUpTo(Extract) { inclusive = true } }`.
- Manifest: `MainActivity android:launchMode="singleTop"`; `ShareReceiverActivity` with `android:theme="@style/Theme.RecipeKeeper.ShareReceiver"` (parent `android:Theme.Translucent.NoTitleBar`), `exported`, `excludeFromRecents`, `noHistory`; the `text/plain` filter always; the two `image/*` filters wrapped in a `debug`/`main` manifest variant or a placeholder (`manifestPlaceholders["imagesShareTarget"]`) toggled by the same `IMAGES_SOURCE` `buildConfigField` that sets `imagePolicy.enabled`.
- Model management tag: `ModelManagementLabels.capabilityVision`; the list item's chip row appends it when `VISION in descriptor.capabilities` (feature file `ModelListItem`/`ModelDetailSheet`, whichever renders tags); `RecipeModelManagementLabels`, `MedicalModelManagementLabels`, `RecipeKeeperModelManagementLabels` + three `strings_modelmanagement.xml` get the string.

---

## 4. Tests to add

| Stream | Test | Covers |
|---|---|---|
| I0 | (none new) | every existing suite green with stubs + defaults |
| I1 | `LlmRequestTest` / `LlmTaskRepositoryImplTest` (+2) | options → request carries `images` and `visualTokenBudget`; legacy call sends none |
| I1 | `FakeLlmEngine` (+ flag) and `LlmEngineDefaultsTest` (`:core:domain`) | `capabilities()` default is text-only; fake records images in order |
| I1 | device spike (not a unit test; report) | one image through `LiteRtLlmEngine` on `emulator-5556` (manual test-APK install, never `connectedDebugAndroidTest`): init with vision, TTFT, memory, any SIGILL/SIGSEGV; budget API verified by `javap` |
| I2 | `PrepareImagesUseCaseTest` | order kept, cap + `capped`, decode failure → `skipped`, tiling on/off, every file registered with the temp store (fake importer writing 1×1 JPEGs) |
| I2 | `ClipUseCaseTest` (+5) | images path: Reading Active until first token (also asserted for web), pictures proposed as Done with local files, no downloader call, `generate` receives N `LlmImage`s in order with the policy budget, parse null → `ParseFailed` (no fallback), `UnsupportedImagesException` → `ImagesUnsupported`, cancel deletes temp files, save receives `ClipInput.Images` |
| I2 | `ImagesClipViewModelTest` | picker launch when nothing pending; pending URIs → prepare → review when ≥ 2, auto-extract when 1; move/remove/select; `Extract` sets the session and emits navigation; close discards; `SavedStateHandle` round trip; availability gates `canExtract` |
| I2 | `ObserveInputCapabilitiesUseCaseTest` | the four mappings incl. `Disabled` when the policy is off |
| I2 | `ExtractionViewModelTest` (+2) | images session → `inputKind = IMAGES`, no duplicate dialog; missing session still pops |
| I2 | `ContentResolverLocalImageImporter` (JVM part) | sampling math + tile geometry (pure functions extracted); decode is instrumented-only |
| I3 | `ShareIntentParserTest` | `SEND` single `EXTRA_STREAM`, `SEND_MULTIPLE`, `ClipData`-only URIs, `text/plain` with a URL inside prose, text without URL → null, > max URIs passed through (the use case caps) |
| I3 | `LibraryViewModelTest` (+5) | `OpenSourceChooser` state; `ChooseSource` per row and per availability → effects/dialog; `Ready(supportsImages)` mapping |
| I3 | `RecipeKeeperClipSinkTest` (+2) | images save sets `IMAGES` origin, empty source, moves pictures, no learning; `existingId(images) == null` |
| I3 | `RecipeEntityMappingTest` (+1) | `IMAGES`/`VOICE` round-trip through the text column; unknown name → `CLIPPED` |
| I3 | `ShareRecipeUseCaseTest` (+1) | images origin shares the full-text template |
| I3 | Compose UI test (`androidTest`, run manually on 5556) | chooser rows' semantics: voice `disabled` + state description, images row text per availability |
| I2/I3 | `EvalImagesParserTest` (JVM, skipped when goldens absent) | parses the eval agent's model outputs for screenshots (`tools/eval/screenshots/outputs/*.txt`, copied to `src/test/resources/eval/images/`) with `RecipeKeeperParser` + `accept`; the same quality bar as `EvalParserTest` |
| I4 | device QA (§5) | end to end on `emulator-5556`, then a real phone if the emulator is too slow |

Existing fakes reused: `FakeLlmEngine`, `FakeClipSink`, `FakePageScanner`, `FakeRecipeRepository`, `TestDispatchers`, `MainDispatcherRule`; new `FakeLocalImageImporter` (I2), `FakeClipInputInbox` (I3).

---

## 5. Work streams

Each stream: owner agent type, files it owns (nobody else edits them), steps, done-when. Streams marked ∥ run in parallel once I0 has landed. Every stream keeps `./gradlew :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug :feature:webclipper:testDebugUnitTest :app:recipekeeper:testDebugUnitTest` green, commits on its worktree branch, writes `docs/plans/briefs/<id>-report.md`. Subagents run as herdr panes with the `claude-work` profile (CLAUDE.md); test/QA agents on Sonnet.

### I0 — Contract freeze (lead; sequential; ~1–2 h)

Owns everything it touches; runs alone. Steps:
1. `:core:model` `ModelCapability` + descriptor field; `:core:domain` `LlmImage`, `LlmRequest.images/visualTokenBudget`, `LlmCapabilities`, `LlmEngine.capabilities()` default, `UnsupportedImagesException`, `LlmRequestOptions` fields; `LlmTaskRepositoryImpl` copies them.
2. `:core:web` `LocalImageImporter` + `ImportedImage` interface, `ContentResolverLocalImageImporter` with `TODO("I2")`, `webModule` binding.
3. `:feature:webclipper` §2.3: `ClipInput`, `ClipImage`, `snapshotOrNull`, enum values, `CandidatePayload.Images/Transcript`, `IMAGES_CANDIDATE_ID` + `imagesCandidate`, `ClipSession.Current(input, …)` with the kept `set(snapshot, …)` overload and the `snapshot` property, `ClipRequest(input)` + secondary ctor, `ExtractionError.ImagesUnsupported`, `ImagePolicy` + `WebClipConfig` defaults, `ClipSink` input-aware defaults, `PendingClipInput` + `ClipInputInbox` + `InMemoryClipInputInbox`, `ImagesAvailability`, use-case classes with `TODO("I2")`, `LlmGenerationSource` additive params (adapter forwards), `ExtractionState.inputKind/imageCount` (web defaults), §2.4 MVI types + a placeholder `WebClipperImagesRoute` rendering `AppLoadingIndicator`, labels fields + feature strings + `TestWebClipperLabels`, DI registrations (`single<ClipInputInbox>`, `factory` for the new use cases, `viewModel` for `ImagesClipViewModel`).
4. `:app:recipekeeper` §2.5: `RecipeOrigin.IMAGES/VOICE`, `RecipeSource`, `LibraryModelStatus.Ready(supportsImages)` (update the two call sites), library intents/effects/state fields with no-op handling, `LibraryRoute(onOpenImagesClip)`, `ObserveImagesAvailabilityUseCase` stub, `Route.ClipImages` + composable pointing at the placeholder route, `extract` `onSaved` branch, labels + strings + fixtures, `BuildConfig.IMAGES_SOURCE = false` + `manifestPlaceholders["imagesShareTarget"]`, `ModelManagementLabels.capabilityVision` + the three apps' strings/factories.
5. Fix every exhaustive `when` the new enum values break (candidate sheet `StatusDot`, sink, mappers) with the web/text behaviour.
6. Verify: the five Gradle tasks above + `:core:domain:test :core:llm:test :core:web:testDebugUnitTest :feature:modelmanagement:testDebugUnitTest`; boundary check. Commit `I0: input-sources contract freeze (ClipInput, LlmRequest.images, origin IMAGES, chooser stubs)`; branch the streams from it.
Done when: all apps build, behaviour unchanged (hidden hooks only), all tests green, report lists every new type/field/label key.

### I1 — Engine vision + capability (∥; `general-purpose`) **[eval-gated]**

Owns: `core/domain/src/main/kotlin/com/llmlocal/core/llm/engine/**`, `core/llm/src/main/kotlin/com/llmlocal/core/llm/engine/**`, `core/llm/.../di/LlmModule.kt`, `core/llm/.../model/LlmModelCatalog.kt`, `core/llm/build.gradle.kts`, `core/llm/src/test/**` (`FakeLlmEngine`), `core/data/.../LlmTaskRepositoryImpl.kt` + its tests, `docs/plans/recipekeeper-research-notes.md` §A addendum.
Steps:
1. `javap` the 0.18 AAR for the visual-token-budget API, `EngineConfig.maxNumImages`, `Content.ImageBytes`; write the addendum.
2. `LiteRtLlmEngine` per §3.1 (vision config, image contents, capability, typed error); `llmModule` + `BuildConfig.LLM_VISION_BACKEND`.
3. Catalog: `DEFAULT_MODEL.capabilities += VISION`; inspect the q4 bundle header (or HF model card) and record the finding.
4. `FakeLlmEngine` flag + tests; repository tests for the new options.
5. Device spike on `emulator-5556` (manual install of a tiny instrumented runner or the eval agent's runner, **never** `connectedDebugAndroidTest`): init with vision on CPU, one 1080×1920 screenshot at budget 280 and 560, TTFT/total/memory, logcat for SIGILL/SIGSEGV. Report numbers.
6. Both existing apps still generate (`:app:recipes` on the emulator); Stop still drops CPU.
Without vision (eval says no): steps 1 (addendum only), 3 (no VISION flag), 4; the engine keeps rejecting images with `UnsupportedImagesException`.
Done when: tests green, spike numbers in the report, `LLM_VISION_BACKEND` documented in CLAUDE.md notes for the lead.

### I2 — Feature: images clip route, pipeline branches, inbox, importer (∥; `general-purpose` for domain/data/VM, `android-ux` for the review screen and extraction branches) **[eval-gated except the inbox]**

Owns: `feature/webclipper/src/main/kotlin/com/llmlocal/feature/webclipper/{domain/input/**, domain/usecase/{ClipUseCase,CheckDuplicateUseCase,PrepareImagesUseCase,DiscardPreparedImagesUseCase,ObserveInputCapabilitiesUseCase}.kt, data/{EngineLlmGenerationSource,InMemoryClipSession,InMemoryClipInputInbox}.kt, presentation/{ExtractionViewModel.kt, WebClipperExtractionRoute.kt, images/**, ui/WebClipperExtractionScreen.kt, mvi/ExtractionState.kt}}`, `feature/webclipper/src/main/res/values/strings_webclipper.xml`, `feature/webclipper/src/test/**`, `core/web/src/main/kotlin/com/llmlocal/core/web/{LocalImageImporter,ContentResolverLocalImageImporter}.kt` + tests.
Steps:
1. `ContentResolverLocalImageImporter` (+ pure helpers for sampling/tiling with JVM tests).
2. `PrepareImagesUseCase`, `DiscardPreparedImagesUseCase`, `ObserveInputCapabilitiesUseCase`; `ClipUseCase` images branch + V11 phase rule; `CheckDuplicateUseCase(sink, input)`; `EngineLlmGenerationSource` forwards images/budget and `capabilities()`.
3. `ExtractionViewModel`/`ExtractionState` input kind; `WebClipperExtractionScreen` branches (source strip, phase label, hints, failure copy, `backToLibrary`, `pickDifferentImages` → new `onPickDifferent` callback on the route).
4. `ImagesClipViewModel` + `WebClipperImagesRoute` (picker launchers with the ordered-selection request where available, `BackHandler` → `Close`, effects → snackbar via labels) + `ImagesReviewScreen` + `ImageTile` (UX spec §2.3–§2.4, a11y checklist §6).
5. Previews: preparing, review 2 and 5 images, no-model hero, text-only-model empty state, wide 840dp, dark.
6. Tests from §4 (I2 rows).
Without vision: only `InMemoryClipInputInbox` behaviour (already in I0) and the `CheckDuplicateUseCase(input)` overload; the rest stays stubbed, the placeholder route remains unreachable because the row is hidden and the filters are absent.
Done when: tests green; with `FakeLlmEngine` the images path round-trips in `ClipUseCaseTest`; previews render; `android-ux` review of the review screen signed off with screenshots.

### I3 — App: chooser, origin, share target, labels, nav (∥; `android-ux`, may pair with `general-purpose` for the share activity)

Owns: `app/recipekeeper/src/main/kotlin/com/llmlocal/recipekeeper/{app/**, recipes/presentation/**, recipes/domain/{RecipeOrigin,RecipeSource,LibraryModelStatus}.kt, recipes/domain/usecase/{ObserveImagesAvailabilityUseCase,ShareRecipeUseCase}.kt, recipes/data/ReadinessLibraryModelStatusSource.kt, clip/data/RecipeKeeperClipSink.kt, clip/domain/RecipeKeeperClipConfig.kt (imagePolicy only), clip/presentation/**}`, `app/recipekeeper/src/main/AndroidManifest.xml`, `app/recipekeeper/build.gradle.kts` (`IMAGES_SOURCE` flag + placeholder), `app/recipekeeper/src/main/res/**`, `app/recipekeeper/src/test/**` (library, sink, share parser, mapping, share use case), `app/recipekeeper/src/androidTest/**` (chooser semantics test), `feature/modelmanagement` label field + its renderer + the three apps' `*ModelManagementLabels.kt` and `strings_modelmanagement.xml` (small, listed here to keep ownership single).
Steps:
1. `SourceChooserSheet` + `ImagesUnavailableDialog`; `LibraryRoute`/`LibraryScreen`/`LibraryViewModel` wiring (UX spec §1); `ObserveImagesAvailabilityUseCase`; `ReadinessLibraryModelStatusSource` → `Ready(supportsImages)`.
2. Origin branches on tile, detail, menus, share, editor, hero (`RecipeOrigin.hasSource`).
3. `RecipeKeeperClipSink.save(item, input, …)` images path; `existingId(input)`.
4. `ShareReceiverActivity`, `ShareIntentParser`, `MainActivity` `singleTop` + intent handling → inbox; theme; manifest filters with the placeholder; NavHost inbox observer, `clip/images` route, `onSaved`/`onCancelled`/`onPickDifferent` wiring, "clip already running" guard.
5. `capabilityVision` label through model management (feature renderer + three apps).
6. Previews: sheet (all availability states, dark), images detail/tile; tests from §4 (I3 rows); the chooser Compose test under `androidTest` (not run on the shared emulator by this stream).
Without vision: identical, with `IMAGES_SOURCE = false` (row hidden, no image filters); the URL share target ships.
Done when: tests green; build green; the chooser works on a device with the images row in each state (toggle the flag locally to see it); report lists the exact manifest, the flag, and the label fields other brands must fill.

### I4 — Integration + device QA (sequential, last; lead merges, QA agent on **Sonnet** with the `android-ux` persona)

1. Lead: merge I1, I2, I3 onto `recipekeeper/dev`; set `IMAGES_SOURCE` and `ImagePolicy` from the eval doc; `./gradlew assemble :feature:webclipper:testDebugUnitTest :app:recipekeeper:testDebugUnitTest lint`; boundary check; install the arm64 split on **`emulator-5556`** with `adb install -r` (never `pm clear`: model + library live there).
2. QA agent (brief in the style of `f4-qa.md`), `emulator-5556` only, screenshots `qa7/<nn>-<check>.png`, logcat, UI dumps:
   - **Chooser**: "+" opens the sheet; rows and copy; voice dimmed with "Coming soon" in the UI dump (`enabled=false`, state description); "Write it yourself" → editor; "From the web" → browser; empty-library "More ways to add…" (on a throwaway profile or by filtering to an empty favourites view is not enough — use a fresh AVD or report as not verifiable).
   - **Images availability**: with the model selected (Gemma 4 E2B) → Available; select a text-only model → dialog with "Open models"; remove the model file → "Choose a model first" → model management.
   - **Picker path**: push 3 PNG screenshots of a known fixture page (`tools/eval/screenshots/` shots or `adb exec-out screencap` of the browser) to `/sdcard/Pictures`, `cmd media scan`; pick 1 → straight to extraction; pick 3 → review: Move left/right (buttons; custom actions via the Compose test from I3 if present), Remove, Add more (cap 5), counter, privacy and time rows; "Extract recipe" → phases "Reading images" (Active until first token) → … → detail; **record TTFT and total time per run** and compare with the eval; if a 3-image run exceeds 5 min on the emulator, repeat the timing on a real phone and report both.
   - **Share path**: share 2 images from the Photos app → review appears on top of the library; share a URL from Chrome → browser opens the page; share during a running extraction → snackbar, nothing navigates.
   - **Saved result**: detail source row "From your photos · Added …", no Open original / Re-extract, share text is full, tile meta, editor without source row, delete + undo.
   - **Failures**: a non-recipe screenshot (a settings screen) → "No recipe found in these pictures" → "Pick different pictures" returns to the review with the same images; cancel mid-run → library, temp dir empty (`run-as … ls cache/webclipper`); kill the app mid-review and reopen → review restored.
   - **Insets / adaptive**: review screen at `wm size 1280x800` (more columns, content capped), gesture bar never covers "Extract recipe"; sheet width on 1280x800; `wm size reset`.
   - **TalkBack**: `uiautomator dump` for the sheet and the review grid (content descriptions, state descriptions); live-region check by reading logcat for accessibility events is optional.
   - **Regression**: web clip end-to-end (Reading still flips at the first token), duplicate dialog on a re-clip, model management shows "Reads images" on Gemma, `:app:recipes`/`:app:medical` model lists unchanged otherwise.
3. Docs: CLAUDE.md (Recipe Keeper section: input sources, `clip/images`, `ShareReceiverActivity` + `singleTop`, `LlmRequest.images`, `LLM_VISION_BACKEND`, `ImagePolicy`, origin values; "Rule 0 status" gains the new label fields); UX spec §1 nav table pointer; Phase 2 backlog (camera, voice, `sourceKey` duplicate check, re-extract from pictures, Direct Share, `ACTION_VIEW`).
Done when: QA report has no P1/P2 open, timings recorded, CLAUDE.md updated, final commit on `recipekeeper/dev`.

---

## 6. Schedule and parallelism

```
eval (parallel agent) ─┐
I0 contract freeze (lead) ──┬── I1 engine vision + capability (gp) ────┐
                            ├── I2 feature images route + pipeline ────┼── I4 integration + QA (lead + Sonnet android-ux)
                            └── I3 app chooser / origin / share / nav ─┘
```

Three agents after I0. Gate check at the I0 → I1/I2 boundary: the lead reads `docs/research/screenshot-extraction-eval.md`; "feasible" → full I1/I2; "not feasible" → the "without vision" step lists. I3 starts either way. Conflict rules as before: only the owning stream edits its files; contract changes go through the lead; everyone rebases on `recipekeeper/dev` before finishing. Estimated effort: I0 1–2 h, I1 ~half a day (+ spike), I2 1–1.5 days, I3 ~1 day, I4 ~half a day.

Suggested worktree branches: `inputs/i1-engine-vision`, `inputs/i2-images-clip`, `inputs/i3-chooser-share` from the I0 commit on `recipekeeper/dev`.

---

## 7. Brief-ready step lists

Each brief starts with: read `docs/plans/briefs/_common.md` (worktree, commit + report rules, rule 0, architecture rules, never `connectedDebugAndroidTest` on 5556), then `recipekeeper-input-sources-ux-spec.md` §(relevant) and this plan §2 (contracts), §3 (spec), §4 (tests) and the stream's section in §5. "**You own:**" = the files listed in §5. Steps = the numbered list in §5. "Done when" = the stream's done-when line. State the gate outcome in the brief ("vision: yes/no per the eval") so the agent picks the right step list. Report file `docs/plans/briefs/<id>-report.md`; reply `<id> done — see <id>-report.md, branch <branch>, commit <hash>`.

---

## 8. Reading the eval into configuration

| Eval finding | `ImagePolicy` / wiring |
|---|---|
| Not feasible (parse rate < ~60 % or > 3 min per image on a phone CPU) | `enabled = false`; I1/I2 "without vision"; keep the URL share target; revisit with GPU/NPU bundles |
| Feasible at budget 280 | `enabled = true, visualTokenBudget = 280, maxImages = 5` |
| Feasible only at 560 | `visualTokenBudget = 560, maxImages = 3`; copy "up to 3 pictures" |
| Tall full-page capture beats viewport shots | `tileImages = false, maxLongEdgePx` from the eval's best downscale |
| Viewport shots beat the tall capture | `tileImages = true, tallAspect = 2.5` |
| Images-before-text vs text-before-images matters | `LiteRtLlmEngine` content order constant (I1) |
| System prompt needs an images variant | `RecipeKeeperPromptBuilder` gains `SYSTEM_IMAGES` (model-facing, accepted literal); `renderImagesPrompt` carries the user turn |
| Median time per image | replace the placeholder in `webclipper_images_time_hint` |

---

## 9. Risks and open decisions

1. **Eval outcome** — the whole images path hangs on it; the plan degrades cleanly (§5 "without vision").
2. **Visual token budget API** in LiteRT-LM 0.18 is unverified; if it is engine-level only, the budget becomes a constructor parameter and `LlmRequest.visualTokenBudget` is ignored with a log line (documented in I1).
3. **`ClipSession.Current.snapshot` turning nullable** touches several feature call sites and tests; I0 keeps them compiling with `!!`-free `?.` edits only where the type forces it, I2 finishes the job. Risk of a missed NPE path is covered by `ExtractionViewModelTest` "missing session pops".
4. **`singleTop` + `onNewIntent`** — verify that a share while the app is in the recents still delivers to the existing task (`CLEAR_TOP` + `SINGLE_TOP` from the receiver) and that the read grants survive the hop (I3 tests on device via the share path in I4).
5. **Memory** — vision tower + WebView on 4 GB devices; the spike (I1) and QA (I4) watch for OOM; `minRamMb` may rise for vision descriptors.
6. **Emulator vision speed** — CPU without SME may be too slow to QA realistically; I4 falls back to a phone for timings.
7. **Screenshots as hero pictures** (UX spec risk 7) — collect QA feedback; the alternative (pre-check only the first) is a one-line default in `ClipUseCase`'s images branch.
8. **FAB vs chooser** (UX spec S1) — flip rule documented; no code structure depends on it.
9. **Model management "Reads images" field** is a cross-brand label change (I3 owns it; recipes/medical factories must fill it or they fail to compile, which is the intended safety net).
10. **Eval goldens as tests** — `EvalImagesParserTest` is skipped when the eval agent's outputs are not copied; whoever merges the eval copies them so CI exercises the parser on real vision output.
