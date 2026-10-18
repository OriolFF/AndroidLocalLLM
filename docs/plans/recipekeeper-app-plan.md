# `:app:recipekeeper` (Recipe Keeper) — on-device recipe web clipper: implementation plan

Status: PLAN (nothing implemented yet). Written 2026-10-18; decisions confirmed with the user the same day (name `recipekeeper`, no one-tap default download, LLM runs on structured data too, local eval harness).
Companion documents: `recipekeeper-ux-spec.md` (screens, states, labels, brand), `recipekeeper-research-notes.md` (LiteRT-LM 0.18 API, WebView extraction, library versions).

> **Naming.** The app is **Recipe Keeper**: module `:app:recipekeeper`, package `com.llmlocal.recipekeeper`, `applicationId com.llmlocal.recipekeeper`, class prefix `RecipeKeeper*`. (`:app:recipes` is the existing ingredients→recipe generator; do not confuse the two.)

---

## 0. What we are building

An Android white-label app, `:app:recipekeeper`, package `com.llmlocal.recipekeeper`, that:

1. Opens an **in-app browser** (WebView + URL bar). The user browses any cooking site.
2. Shows a **floating "Clip recipe" button** over the page. On tap (or proactively after page load) the page is **captured**: JSON-LD `schema.org/Recipe` blocks, readable main text, image list with layout metadata, Open Graph tags.
3. **Detects recipes** on the page. Several → a **candidate picker** sheet; one → straight to extraction; none → "nothing detected" sheet with "Extract anyway".
4. **Extracts** the chosen recipe with the **local LLM** (LiteRT-LM, Gemma 4 E2B, same model catalog/download mechanism as the other apps): title, description, servings, prep/cook/total time, ingredients, steps, notes. The model works on **text only**. Pictures are chosen by heuristics from the page and **downloaded** locally alongside the recipe; they are never sent to the model. Streaming output is shown as a skeleton that fills in.
5. **Saves** the recipe locally (Room) and shows it in a **library** (grid) with a **detail** screen (hero image, check-off ingredients, steps, source link, share, delete).
6. Reuses the existing **model-management** screen for download/selection.

Everything the brand does not own stays in `:core:*` / `:feature:*`, following the template's rule: *if the generic `LlmTaskScreen` can't express the UX, add a new `:feature:<x>` that reuses `GenerateLlmTaskUseCase` + `LlmTaskConfig<T>`.*

---

## 1. Architecture decisions

| # | Decision | Why |
|---|---|---|
| A1 | New modules: **`:core:web`** (page capture, JSON-LD helpers, image download; brand-free) and **`:feature:webclipper`** (browser + clip FAB + candidate sheet + extraction screen, generic over `T` via `WebClipConfig<T>` + `WebClipperLabels`). Library/detail/Room/recipe types are **app-only**. | Matches the template: the clip flow is reusable by any "extract T from a web page" brand; recipe schema and storage are the brand's. Module boundaries also give subagents conflict-free ownership. |
| A2 | **Extend `LlmEngine`** with a request object: `generateStream(request: LlmRequest)` carrying `systemInstruction`, `maxOutputTokens`, `temperature` and an optional `jsonSchema` (constrained decoding). Text only; no image input. Pass `maxNumTokens` to `EngineConfig`. Wire `cancel()` to `Conversation.cancelProcess()`. Keep `generateStream(prompt: String)` as a default-method delegate so nothing else changes. | LiteRT-LM 0.18 already supports all of this; the wrapper simply never used it. `cancel()` being a no-op means native compute keeps burning CPU after the user taps Stop. `maxTokens` in `LiteRtLlmEngine` is currently dead code. |
| A3 | **Two-tier detection, LLM always runs for extraction.** Tier A: JSON-LD/microdata `Recipe` nodes → candidates deterministically, and the LLM input is a compact rendering of those fields (~300–800 tokens, fast). Tier B: no structured data → candidates from heading/ingredient-list heuristics, LLM input is the readable text of that section (truncated). If the LLM fails on a Tier A candidate, **fall back to the deterministic schema mapping** so a structured recipe is never lost. | The user explicitly wants the local model to detect and extract; structured data makes that fast and reliable, and the fallback guarantees quality on the ~90% of sites that ship JSON-LD. |
| A4 | **Prompt/output format v1 = line-oriented sections** (`Title:` / `Ingredients:` `-` / `Steps:` `1.` …, extended with `Description:`, `Servings:`, `PrepMinutes:`, `CookMinutes:`, `Notes:`), parsed by a tolerant regex parser plus an **incremental draft parser** for the streaming skeleton. **JSON-schema constrained decoding is a time-boxed spike** (WS1), promoted only if it works on CPU/emulator and device. | The existing apps prove the line format works with a 2B model; `ResponseFormat.json` has an open issue with speculative decoding and is unverified on CPU. |
| A5 | **Token budget**: `maxNumTokens = 4096` (CPU, debug) / `4096` (GPU, release; FLOAT32 activations if GPU is ever enabled), `maxOutputToken = 1024`, prompt budget ≤ ~2,500 tokens ≈ **9,000 characters** of cleaned text. Budget constants live in `WebClipConfig` so a brand can tune them. | Mid-range CPU prefill is ~150–300 tok/s; 2.5k tokens ≈ 10–15 s to first token. Larger contexts hit known GPU bugs and make the wait unacceptable. |
| A6 | **Pictures are storage only, chosen by heuristics, never model input**: JSON-LD `image` > `og:image` > largest displayed `<img>` inside the recipe card/near the title > step images in DOM order. Downloaded in parallel with the LLM via the shared OkHttp client, resized (long edge ≤ 1280 px hero / ≤ 800 px others, JPEG q85), max 8 per recipe, stored in `filesDir/recipes/<id>/` and referenced from the recipe row. | User requirement (2026-10-18): images are stored alongside the recipe they belong to; no image input to the model. Deterministic, instant, testable. |
| A7 | **Persistence = Room 2.8.5 via the (currently empty) `llmlocal.android.room` convention plugin + KSP 2.3.12.** Gate: the first `:app:recipekeeper` build with `ksp(...)`. If KSP fails against Kotlin 2.4.21, fall back to kotlinx-serialization JSON files (`plugin.serialization 2.4.21`, `kotlinx-serialization-json 1.11.0`) behind the same `RecipeRepository` interface. | Room gives search, Flow, undo-delete and migrations; the repository interface keeps the fallback cheap. |
| A8 | **Images displayed with Coil 3.6.3** (`coil-compose` + `coil-network-okhttp`, wired to the Koin `OkHttpClient`). App-only dependency. | Only the new app shows remote/local images. |
| A9 | **Model readiness becomes shared**: extract `ModelReadinessObserver` (selection → progress → installed → `engine.initialize()`) into `:core:llm`; `LlmTaskViewModel` delegates to it. | The clipper needs the gate in two ViewModels; copying ~60 lines re-introduces the "download finished but screen says no model" bug. |
| A10 | **Shared copy debt fixed now**: `ModelManagementLabels` gains the three snackbar templates, `ModelManagementViewModel` receives labels via `parametersOf`; `AppErrorView` takes a `retryLabel`. | "selected for recipe generation" is wrong for this app (and for medical). |
| A11 | **Shared design-system primitives added**: `PhaseProgress`, `StreamingText`/`StreamingCursor`, `SkeletonLine`, `SectionHeader`, `NumberBadge` in `:core:designsystem`; the two existing cards switch to them. | They are already duplicated in `StreamedRecipeCard` and `StreamedMedicalCard`; the extraction screen needs them too. |
| A12 | **Navigation Compose 2.10**, start destination = **Library**; browser, extraction, detail are full screens; the candidate picker is a `ModalBottomSheet` driven by state. | See UX spec §1. |
| A15 | **No one-tap default download.** Model acquisition uses exactly the existing mechanism: the user opens the model-management screen and chooses/downloads a model. When no model is ready, the clip button, the library banner and the extraction error state all simply **navigate to model management**. No model-gate sheet, no `StartDefaultDownload`, no size/variant copy in this app. | User decision 2026-10-18: same mechanism as the other apps; the user chooses the model. |
| A16 | **Local evaluation harness (WS7)**: fixtures of captured pages → rendered prompts → run through the **same `.litertlm` model locally** → outputs parsed by the production parser and asserted. Golden outputs are committed so the JVM tests run without the model; a script regenerates them when prompts/models change, and an on-device instrumented variant runs the full pipeline against the downloaded model. | User decision 2026-10-18: validate the standard line-format pipeline against a local model before considering the "skip the LLM for structured data" switch. |
| A13 | **Extraction is `viewModelScope`-bound in v1** with "keep the app open" hint and `FLAG_KEEP_SCREEN_ON`. WorkManager-backed extraction is Phase 2. | Keeps v1 tractable; the worker pattern already exists for downloads when we need it. |
| A14 | **No cleartext HTTP** (platform default). http-only sites show the WebView error page. | Secure default; nearly all recipe sites are https. Revisit if testers hit it. |

### Module graph after the change

```
:app:recipekeeper   → every :core:* + :feature:*  (white-label plugin) + :core:web + :feature:webclipper (plugin adds them too)
:feature:webclipper → feature plugin deps (:core:designsystem/domain/model/common) + :core:llm + :core:data + :core:web
:core:web       → :core:common, :core:network, :core:model   (Android lib: WebView JS bridge, org.json, OkHttp)
:core:llm       → unchanged edges; LlmEngine request type lives in :core:domain
```

Allowed-edge check stays: no `:feature → :app`, no `:core → :feature`.

### New catalog entries (`gradle/libs.versions.toml`)

```toml
[versions]
room = "2.8.5"
coil = "3.6.3"
androidx-webkit = "1.17.1"
kotlinx-serialization = "1.11.0"     # fallback only; add if A7 gate fails

[libraries]
androidx-room-runtime  = { group = "androidx.room", name = "room-runtime",  version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
coil-compose           = { group = "io.coil-kt.coil3", name = "coil-compose",        version.ref = "coil" }
coil-network-okhttp    = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }
androidx-webkit        = { group = "androidx.webkit", name = "webkit", version.ref = "androidx-webkit" }

[plugins]
androidx-room = { id = "androidx.room", version.ref = "room" }
# ksp already present: ksp = "2.3.12", plugin alias `ksp`
```

`build-logic/convention/build.gradle.kts` needs `compileOnly` on the KSP and Room Gradle plugin artifacts so `AndroidRoomConventionPlugin` can apply them by id (`com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12`, `androidx.room:room-gradle-plugin:2.8.5`).

---

## 2. Contracts to freeze first (WS0)

These are written as stubs in WS0 so every stream compiles against them in parallel. Signatures may grow; they must not be renamed without telling the other streams.

### 2.1 `:core:domain` — engine request (package `com.llmlocal.core.llm.engine`)

```kotlin
data class LlmRequest(
    val prompt: String,
    val systemInstruction: String? = null,
    val maxOutputTokens: Int? = null,
    val jsonSchema: String? = null,          // constrained decoding; null = free text
    val temperature: Double? = null,         // null = engine default
    // Text only by design: pictures are stored with the recipe, never sent to the model.
)

interface LlmEngine {
    suspend fun initialize(): Outcome<Unit>
    fun generateStream(request: LlmRequest): Flow<LlmToken>
    fun generateStream(prompt: String): Flow<LlmToken> = generateStream(LlmRequest(prompt))
    fun cancel()
}
```

`LlmTaskRepositoryImpl` keeps calling the String overload; nothing else changes.

### 2.2 `:core:web` (package `com.llmlocal.core.web`)

```kotlin
data class PageSnapshot(
    val url: String, val title: String?, val siteName: String?,
    val jsonLd: List<JsonLdNode>,            // flattened: arrays and @graph expanded, @type as List<String>
    val readable: ReadableContent?,          // Readability.js output: title, byline, excerpt, textContent, length
    val sections: List<TextSection>,         // headings h1–h3 with the text under them (for Tier B regioning)
    val images: List<PageImage>,             // src (absolute), alt, displayed w/h, natural w/h, top offset, inRecipeCard
    val openGraph: Map<String, String>,      // og:image, og:title, og:description, og:site_name, twitter:image
    val capturedAt: Long,
)
data class JsonLdNode(val types: List<String>, val raw: JSONObject)
data class TextSection(val heading: String?, val level: Int, val text: String, val imageSrcs: List<String>, val topOffset: Int)
data class PageImage(val src: String, val alt: String?, val width: Int, val height: Int, val naturalWidth: Int, val naturalHeight: Int, val top: Int, val inRecipeCard: Boolean)
data class ReadableContent(val title: String?, val byline: String?, val excerpt: String?, val textContent: String, val length: Int)

interface PageCapture {                       // implemented over WebView.evaluateJavascript (UI thread, suspend)
    suspend fun capture(webView: WebView): Outcome<PageSnapshot>
    suspend fun quickScanStructured(webView: WebView): Int   // count of schema.org Recipe-like nodes; cheap, for the proactive badge
}
object JsonLdExtractor { fun fromScriptBlocks(blocks: List<String>): List<JsonLdNode> }   // pure Kotlin, unit-tested
object IsoDuration { fun toMinutes(iso: String?): Int? }                                   // PT1H30M, P0DT0H30M, free text fallback
class ImageDownloader(client: OkHttpClient, dispatchers: DispatcherProvider) {
    suspend fun download(url: String, referer: String?, into: File, maxLongEdgePx: Int): Outcome<File>
}
val webModule: Module  // single { PageCapture }, single { ImageDownloader }
```

JS lives in `core/web/src/main/assets/webclipper/{readability.js,readability-readerable.js,capture.js}`; `capture.js` is an IIFE that **returns an object** (not a stringified one) so `evaluateJavascript`'s result is single-encoded JSON.

### 2.3 `:feature:webclipper` (package `com.llmlocal.feature.webclipper`)

```kotlin
data class ClipCandidate(
    val id: String, val title: String, val thumbnailUrl: String?, val snippet: String?,
    val source: CandidateSource,             // STRUCTURED | TEXT
    val ingredientCount: Int?, val totalMinutes: Int?,
    val payload: CandidatePayload,           // Structured(node: JsonLdNode) | Text(section: TextSection) | WholePage
)

interface WebClipConfig<T : Any> {
    val llmConfig: LlmTaskConfig<T>                       // promptBuilder.userPrompt(listOf(renderedInput))
    fun detect(snapshot: PageSnapshot): List<ClipCandidate>          // Tier A + Tier B, ranked, max 10
    fun renderLlmInput(candidate: ClipCandidate, snapshot: PageSnapshot, budgetChars: Int): String
    fun fallback(candidate: ClipCandidate, snapshot: PageSnapshot): T?   // deterministic mapping for STRUCTURED; null otherwise
    fun pickPictures(candidate: ClipCandidate, snapshot: PageSnapshot, max: Int): List<String>
    val budgetChars: Int get() = 9_000
    val maxOutputTokens: Int get() = 1_024
    val maxPictures: Int get() = 8
}
interface ClipSink<T : Any> {                 // app-owned persistence
    suspend fun save(item: T, snapshot: PageSnapshot, candidate: ClipCandidate, pictures: List<File>, truncated: Boolean): String  // returns saved id
}

@Immutable data class WebClipperLabels(...)   // the Browser/FAB/Candidates/Extraction fields from the UX spec §7 (not Library/Detail/Delete, which are app-only)

@Composable fun <T : Any> WebClipperBrowserRoute(config: WebClipConfig<T>, sink: ClipSink<T>, labels: WebClipperLabels, initialUrl: String?,
    onNavigateToModelManagement: () -> Unit, onNavigateToExtraction: (candidateId: String) -> Unit, onBackToLibrary: () -> Unit, snackbarHostState: SnackbarHostState)
@Composable fun <T : Any> WebClipperExtractionRoute(candidateId: String, config: WebClipConfig<T>, sink: ClipSink<T>, labels: WebClipperLabels,
    renderDraft: @Composable (streamedText: String, isRunning: Boolean) -> Unit,   // app renders its skeleton (like LlmTaskRoute.renderOutput)
    onSaved: (id: String) -> Unit, onCancelled: () -> Unit, onNavigateToModelManagement: () -> Unit)

// MVI (see UX spec §2–4 for the full field lists): BrowserState/Intent/Effect, ExtractionState/Intent/Effect, ClipFabState, BrowserSheet, Phase/PhaseStatus
// Shared between the two routes: ClipSession (single, holds PageSnapshot + candidates for the current page) and WebViewHolder (application-context WebView kept across navigation).
val webClipperModule: Module   // viewModel { params -> BrowserViewModel(...) }, viewModel { params -> ExtractionViewModel(...) }, single { ClipSession() }, single { WebViewHolder(androidContext()) }
```

### 2.4 `:core:llm` — readiness (package `com.llmlocal.core.llm.readiness`)

```kotlin
sealed interface ModelReadiness { NoModel; Checking; Downloading(progress: PerModelProgress); Initializing; Ready(descriptor); Failed(reason, descriptor?) }
class ModelReadinessObserver(selectionStore, progressStore, manager, engine: LlmEngine, dispatchers) {
    fun observe(scope: CoroutineScope): StateFlow<ModelReadiness>   // selection → progressFor(id) → isInstalled → initialize()
    suspend fun recheck()
}
```

`LlmTaskViewModel` maps `ModelReadiness` → its existing `ModelStatus` so `LlmTaskScreen` is untouched.

### 2.5 `:app:recipekeeper` (package `com.llmlocal.recipekeeper`)

```kotlin
data class ClippedRecipe(id: String, title: String, description: String?, servings: String?, prepMinutes: Int?, cookMinutes: Int?, totalMinutes: Int?,
    ingredients: List<String>, steps: List<String>, notes: String?, sourceUrl: String, sourceHost: String, pictures: List<String> /* local paths */,
    truncated: Boolean, clippedAt: Long, checkedIngredients: Set<Int> = emptySet())
data class RecipeDraft(title: String?, description: String?, servings: String?, prepMinutes: Int?, cookMinutes: Int?, ingredients: List<String>, steps: List<String>, notes: String?, tail: String)
interface RecipeRepository { fun observeAll(): Flow<List<RecipeSummary>>; fun observe(id): Flow<ClippedRecipe?>; suspend fun upsert(r); suspend fun delete(id); suspend fun setChecked(id, Set<Int>); fun search(q): Flow<List<RecipeSummary>> }
```

---

## 3. Pipeline specification

### 3.1 Capture (`:core:web`)
1. `onPageFinished` → wait for `document.readyState === "complete"` or 1.5 s, then `quickScanStructured()` (JSON-LD/microdata count only) → FAB badge.
2. On tap → `capture()`: inject `readability.js` + `capture.js` via `evaluateJavascript`; the IIFE returns `{url,title,siteName,jsonLd:[rawStrings],og:{...},readable:{...},sections:[...],images:[...]}`. Parse with `org.json` on `Dispatchers.Default`.
3. Image list filters: has src, not `data:`, displayed area ≥ 150×150 or naturalWidth > 300, resolve `currentSrc`/`srcset`/`data-src`/`data-lazy-src`; absolute URLs via `new URL(src, document.baseURI)`.
4. Sections: walk `h1–h3`, collect following sibling text until the next heading (cap 20 sections, 6k chars each).
5. Size guard: if `outerHTML.length > 4 MB` skip readability and use sections only.

### 3.2 Detect (`RecipeKeeperClipConfig.detect`)
- Tier A: `jsonLd.filter { "Recipe" in types }` (+ `WebPage.mainEntity` of type Recipe, + microdata `[itemtype*="schema.org/Recipe"]` nodes the JS converts to pseudo-JSON-LD). One candidate per node; `title = name`, `thumbnail = first image URL`, `ingredientCount = recipeIngredient.size`, `totalMinutes = IsoDuration(totalTime ?: prep+cook)`.
- Tier B (only if Tier A is empty): a section is a candidate when its text has ≥ 3 lines matching the quantity-unit regex **or** contains an "ingredients" heading word followed within the same/next section by "instructions|method|directions|steps". URL path containing `recipe|rezept|receta|recepta|recette|ricetta` boosts rank. Fallback single candidate `WholePage` when readable length ≥ 500 chars.
- Rank: STRUCTURED first, then by heading level, then by position. Cap 10.

### 3.3 Render LLM input (`renderLlmInput`)
- STRUCTURED: `Name / Description / Yield / Times / Ingredients (one per line) / Instructions (flattened HowToSection→HowToStep, strip duplicated step names) / Image count`. Typically 300–800 tokens.
- TEXT: section heading + text, trimmed to `budgetChars` with a "[…truncated]" marker; prefer cutting at a paragraph boundary.
- WHOLE PAGE: `readable.textContent` trimmed to `budgetChars`.

### 3.4 Prompt (`RecipeKeeperPromptBuilder`)
System: "You extract ONE recipe from web page text. Respond ONLY in this format… Keep ingredient lines exactly as quantities appear. Do not invent steps. If a field is unknown, omit the line." Format:
```
Title: …
Description: … (one sentence, optional)
Servings: … (optional)
PrepMinutes: <int> (optional)
CookMinutes: <int> (optional)
Ingredients:
- …
Steps:
1. …
Notes:
… (optional)
```
User: "Page: <host> — <title>\n\n<rendered input>". Temperature 0.2 (via `LlmRequest.temperature`) for extraction; `maxOutputTokens = 1024`.

### 3.5 Parse
- `RecipeKeeperParser : TaskParser<ClippedRecipe>` (final, at `Done`): tolerant regex over sections, header synonyms (`Ingredients|Ingredientes|Zutaten`), bullets `-|*|•`, steps `1.|1)|Step 1`. Null if no ingredients **and** no steps. Sets `truncated = true` when the text ends mid-step (no terminal punctuation and output token count hit the cap — the engine signals this via `LlmToken.Done(truncated=…)`? No: keep `LlmToken` unchanged; the repository's buffer length vs. `maxOutputTokens` heuristic in the ViewModel marks it).
- `RecipeKeeperDraftParser` (incremental, debounced 80 ms on `streamedText`): same grammar, returns `RecipeDraft` with completed lines only and the unparsed `tail`.
- On parse failure for a STRUCTURED candidate → `config.fallback()` deterministic mapping, marked `source = STRUCTURED_FALLBACK` in the snackbar copy.

### 3.6 Pictures
`pickPictures`: JSON-LD `image` URLs (string/array/ImageObject) → `og:image`/`twitter:image` → `images.filter{inRecipeCard}` by displayed area desc → remaining by area desc near the candidate's `topOffset`. Dedupe by URL without query. Download in parallel with the LLM (`ImageDownloader`, Referer = page URL, UA = WebView UA, 15 s timeout, Content-Length ≤ 15 MB). Failures are non-blocking (phase shows warning). The downloaded files are only ever persisted with the recipe; nothing image-related reaches the LLM.

### 3.7 Save
`RecipeKeeperClipSink.save()` → `RecipeRepository.upsert(ClippedRecipe)` with picture paths; `ExtractionEffect.NavigateToRecipe(id)`; the browser's `ClipSession` is cleared for that page.

---

## 4. Work streams

Each stream names: owner agent type, files it owns (no one else edits them), inputs it depends on, step list, and "done when". Streams marked ∥ run in parallel once WS0 has landed.

### WS0 — Foundation & contract freeze (lead; sequential; ~first)
Owner: lead session (or one `general-purpose` agent). Owns: `settings.gradle.kts`, `gradle/libs.versions.toml`, `build-logic/**`, new module skeletons, `app/recipekeeper/**` scaffold.
1. Add catalog entries from §1. Add KSP + Room Gradle plugin `compileOnly` deps to `build-logic/convention/build.gradle.kts`; implement `AndroidRoomConventionPlugin` (apply `com.google.devtools.ksp` + `androidx.room`, add `room-runtime` impl + `room-compiler` ksp, `room { schemaDirectory("$projectDir/schemas") }`).
2. `include(":core:web")`, `include(":feature:webclipper")`, `include(":app:recipekeeper")`.
3. Create `core/web/build.gradle.kts` (`llmlocal.android.library` + koin; deps `:core:common`, `:core:network`, `:core:model`, `androidx-webkit`, okhttp; test deps) with the §2.2 stubs and empty asset files.
4. Create `feature/webclipper/build.gradle.kts` (`llmlocal.android.feature`; deps `:core:llm`, `:core:data`, `:core:web`, navigation not needed, `androidx-webkit`) with §2.3 stubs (`TODO()` bodies, empty composables).
5. Add `:core:web` and `:feature:webclipper` to `AndroidWhiteLabelAppConventionPlugin` dependencies.
6. Scaffold `:app:recipekeeper`: `cp -R app/recipes app/recipekeeper`; rename package/`namespace`/`applicationId` to `com.llmlocal.recipekeeper`, `RecipeApp`→`RecipeKeeperApp`, `app_name` = "RecipeKeeper" (or chosen brand name), apply `llmlocal.android.room`, add Coil deps; keep manifest permissions/WorkManager removal/foreground service exactly as in recipes; delete the ingredient-generator files (`RecipeScreen`, `RecipeTaskConfig`, `RecipePromptBuilder`, `RecipeParser`, `StreamedRecipeCard`, previews) and add §2.5 stubs + a placeholder `LibraryScreen` that renders `EmptyState`.
7. (moved to WS1) `LlmRequest` and the `LlmEngine` overload are owned by WS1, which runs in parallel with WS0 in its own worktree; WS0 must not touch `core/domain` or `core/llm` engine files.
8. (moved to WS3) `ModelReadiness` types are owned by WS3; WS4 codes against the §2.4 sketch and integrates when WS3 lands.
9. `./gradlew assembleDebug` (all three apps) + `./gradlew :feature:webclipper:dependencies --configuration releaseRuntimeClasspath` boundary check. Commit "WS0: recipekeeper scaffold + contracts".
Done when: all three apps build, `:app:recipekeeper` installs and shows the placeholder library screen on `emulator-5554`, and the Room/KSP gate passes (an empty `@Database` compiles). If KSP fails: switch A7 to kotlinx-serialization in this same stream before anyone starts WS5a.

### WS1 — Engine upgrade (∥) — `:core:llm`, `:core:domain/engine`
Owner: `general-purpose`. Owns: `core/domain/.../engine/*`, `core/llm/.../engine/*`, `core/llm/.../di/LlmModule.kt`, `core/llm/build.gradle.kts`.
1. `LiteRtLlmEngine.generateStream(request)`: `ConversationConfig(systemInstruction = request.systemInstruction?.let { Contents.of(it) }, samplerConfig = …temperature override…, maxOutputToken = request.maxOutputTokens, enableResponseFormat = request.jsonSchema != null)`; pass `responseFormat = ResponseFormat.json(schema)` when set. Text content only; do not add vision/`visionBackend` wiring.
2. `EngineConfig(maxNumTokens = maxNumTokens)` with constructor default 4096.
3. Real cancel: keep the live `Conversation` in an `AtomicReference`; `cancel()` → `cancelProcess()`; also call it from the flow's `onCompletion`/cancellation path so coroutine cancellation stops native compute.
4. Remove the dead `maxTokens` field or wire it (= `maxNumTokens`). Update KDoc.
5. Spike (time-box 2 h, report only): on emulator (CPU) run a JSON-schema constrained request with a small recipe schema; record success/failure, speed, any "compute_mask() called after stop" errors; test `getTokenCount()` for the budget heuristic. Write findings to `docs/plans/recipekeeper-research-notes.md` §A addendum.
6. Unit tests: a `FakeLlmEngine` in `core/llm/src/testFixtures` or `core/domain/src/test` honouring `LlmRequest`; test default-method delegation.
Done when: both existing apps still generate on the emulator; Stop in `:app:recipes` ends CPU work (observe `top`/logcat); spike report written.

### WS2 — Page capture (∥) — `:core:web`
Owner: `general-purpose`. Owns: `core/web/**`.
1. Vendor `@mozilla/readability@0.6.0` (`Readability.js`, `Readability-readerable.js`, Apache-2.0 → add `core/web/THIRD_PARTY_LICENSES.md`).
2. Write `capture.js` per §3.1 (IIFE returning an object; JSON-LD raw strings + microdata→pseudo-JSON-LD; OG tags; readability on a `document.cloneNode(true)`; sections; images with `getBoundingClientRect`, `currentSrc`, lazy attrs; `inRecipeCard` via `closest('[itemtype*="Recipe"], .wprm-recipe, .tasty-recipes, .mv-create-card, [class*="recipe"]')`).
3. `WebViewPageCapture : PageCapture`: `suspendCancellableCoroutine` around `evaluateJavascript` on `Dispatchers.Main`; unwrap `"null"`; parse on `Default`; map to `PageSnapshot`. `quickScanStructured` runs a 20-line script only.
4. `JsonLdExtractor` (pure Kotlin): parse each block (try/catch, strip HTML comments/CDATA), expand arrays and `@graph`, normalise `@type` to list, follow `mainEntity`.
5. `IsoDuration.toMinutes` (`PT1H30M`, `PT0H20M0S`, `P0DT0H30M`, "1 hr 20 mins" regex fallback).
6. `ImageDownloader` per §3.6 (OkHttp from Koin, `inSampleSize` decode, JPEG q85, temp file + rename).
7. `webModule`.
8. Tests (`core/web/src/test`): JSON-LD fixtures from 6 real sites (single Recipe, `@graph`, array of recipes, `@type` array, `mainEntity`, HowToSection instructions, ImageObject images, yield array) saved as `src/test/resources/jsonld/*.json`; `IsoDuration` table test; `PageSnapshot` JSON→model mapping from a captured fixture.
Done when: tests green; a manual run on the emulator against two real recipe pages (one JSON-LD site, one plain blog) logs a sane `PageSnapshot` (title, ≥1 Recipe node or ≥1 section, ≥1 image).

### WS3 — Shared readiness, copy debt, design primitives (∥) — touches shared code, keep small
Owner: `android-ux` for primitives (3–4), `general-purpose` for 1–2; or one agent sequentially. Owns: `core/llm/.../readiness/*`, `feature/llm-task/.../LlmTaskViewModel.kt`, `feature/modelmanagement/**` (labels + VM + module), `core/designsystem/.../component/*`, both existing apps' `*Labels.kt`, `StreamedRecipeCard.kt`, `StreamedMedicalCard.kt`.
1. Implement `ModelReadinessObserver` (§2.4) from `LlmTaskViewModel.observeSelection()/checkModel()` logic; `LlmTaskViewModel` delegates and maps to `ModelStatus`. Register in `llmModule` as `factory`.
2. `ModelManagementLabels` += `modelSelectedSnackbar`, `modelRemovedSnackbar`, `modelNotDownloadedSnackbar`; `ModelManagementViewModel(labels)` via `parametersOf(labels)` from `ModelManagementRoute`; fill the fields in `RecipeModelManagementLabels`, `MedicalModelManagementLabels` (and the Recipe Keeper one in WS5b). `AppErrorView(retryLabel: String)`.
3. Add primitives to `:core:designsystem/component`: `PhaseProgress(items: List<PhaseItem>)`, `StreamingText(text, isGenerating, warmingUpLabel)` + `StreamingCursor()`, `SkeletonLine(widthFraction)`, `SectionHeader(label, icon)`, `NumberBadge(n)`. Brand-free (MaterialTheme + `designTokens.successColor` only). Previews for both existing token sets.
4. Switch `StreamedRecipeCard` and `StreamedMedicalCard` to the primitives and to `labels.warmingUpLabel`.
5. Unit tests: `ModelReadinessObserverTest` (Turbine; fake stores/engine) covering: fresh install default selected → download SUCCEEDED → Ready; selection change; init failure.
Done when: `./gradlew :app:recipes:assembleDebug :app:medical:assembleDebug test` green; both apps visually unchanged (screenshots before/after via `android-cli`).

### WS4 — `:feature:webclipper` (∥, largest) — browser, FAB, sheets, extraction
Owner: `general-purpose` for MVI/ViewModels/WebView plumbing + `android-ux` for the composables and review. Owns: `feature/webclipper/**`. Depends on WS0 stubs; uses fakes for `PageCapture`/`LlmEngine` until WS1/WS2 merge.
1. `WebViewHolder`: application-context `WebView` created once, `settings` per research §B (JS on, DOM storage on, file access off, safe browsing on, mixed content NEVER_ALLOW), `WebViewClient` (`shouldOverrideUrlLoading` keeps http/https, hands `intent:/mailto:/tel:` to the system, `onRenderProcessGone` recreates), `WebChromeClient` (progress, title), `doUpdateVisitedHistory` → canGoBack/Forward; `saveState/restoreState` on process death; `onPause/onResume/destroy` tied to the Activity lifecycle via `LifecycleEventObserver`.
2. `BrowserViewModel` (state/intents/effects from UX spec §2): URL submit (scheme inference, search fallback via `labels.searchUrlTemplate`), page progress, proactive `quickScanStructured` after load (debounced), `Scan` → `capture()` → `config.detect()` → `ClipSession.set(snapshot, candidates)` → `Found(n)` / sheet; model readiness from `ModelReadinessObserver` → `ClipFabState.ModelNotReady`, whose tap emits `NavigateToModelManagement` (A15); FAB collapse on scroll; hide FAB while IME visible.
3. `ExtractionViewModel`: reads candidate from `ClipSession`; phases Reading→Extracting→Pictures→Saving; runs `GenerateLlmTaskUseCase(engine, config.llmConfig, listOf(renderLlmInput(...)))` — **via a new overload that accepts `LlmRequest` extras** (`LlmTaskRepository.generateStream(engine, config, inputs, options: LlmRequestOptions)`; add to `:core:domain`/`:core:data` in this stream, coordinated with WS1); appends `streamedText`; starts picture downloads in parallel (`async`); on `Complete` → `sink.save` → `NavigateToRecipe`; on `Failed` → `config.fallback()` for STRUCTURED else error state; `Cancel` → cancel job + `engine.cancel()` + delete temp pictures; truncation heuristic.
4. Composables: `WebClipperBrowserScreen` (own `Scaffold`, `BrowserTopBar` with URL pill, `LinearProgressIndicator`, `AndroidView(WebView)` with `imePadding()`/`navigationBarsPadding()`, `ClipFab` with `AnimatedContent` over `ClipFabState`, `CandidateSheet`, `NothingDetectedSheet`), `WebClipperExtractionScreen` (`PhaseProgress`, source strip, `renderDraft` slot, picture strip with keep checkboxes, Stop button, error states table from UX spec §4). `BackHandler` for WebView history.
5. Labels: `WebClipperLabels` data class (UX spec §7 fields for Browser/FAB/Candidates/Extraction).
6. `webClipperModule`.
7. Tests: `BrowserViewModelTest`, `ExtractionViewModelTest` with `FakePageCapture`, `FakeLlmEngine` (scripted tokens), `FakeClipSink`: single candidate skips sheet; multiple opens sheet; model not ready blocks scan; cancel deletes temp files; parse failure on STRUCTURED uses fallback; pictures failure is non-blocking.
8. a11y pass per UX spec §9 (FAB single node + `stateDescription`, live region for phase changes only, skeleton `invisibleToUser`, traversal order).
Done when: with the fakes, the feature's debug preview app path (or `:app:recipekeeper` once WS5 lands) shows all FAB states, both sheets and a streamed extraction; tests green; `android-ux` review signed off with screenshots.

### WS5a — `:app:recipekeeper` domain, data, prompts, parsers (∥)
Owner: `general-purpose`. Owns: `app/recipekeeper/src/main/kotlin/com/llmlocal/recipekeeper/{domain,data,clip,prompt,parser}/**`, `app/recipekeeper/schemas/**`.
1. `ClippedRecipe`, `RecipeSummary`, `RecipeDraft` (§2.5).
2. `SchemaOrgRecipeMapper` (JSON-LD node → `ClippedRecipe`): all field shapes from research §B (image string/array/ImageObject; instructions Text/Text[]/HowToStep/HowToSection/ItemList; yield string/array/QuantitativeValue; durations via `IsoDuration`).
3. `RecipeKeeperClipConfig : WebClipConfig<ClippedRecipe>` implementing §3.2/3.3/3.6; `RecipeKeeperPromptBuilder` (§3.4); `RecipeKeeperParser` + `RecipeKeeperDraftParser` (§3.5).
4. Room: `RecipeKeeperDatabase` (`recipes`, `pictures` tables; FTS optional later), `RecipeDao` (Flow queries, search by title/ingredient LIKE), `RoomRecipeRepository : RecipeRepository`; `RecipeKeeperClipSink` (moves pictures from temp to `filesDir/recipes/<id>/`, upserts); undo-delete via soft-delete flag + 5 s hard delete.
5. Koin `recipeKeeperConfigModule`: `single { RecipeKeeperClipConfig }`, `single<RecipeRepository>`, `single<ClipSink<ClippedRecipe>>`, DB, Coil `ImageLoader` using the Koin `OkHttpClient`.
6. Tests: `SchemaOrgRecipeMapperTest` on the WS2 fixtures; `RecipeKeeperParserTest` (complete output, missing optional fields, truncated mid-step, synonyms, `•` bullets); `RecipeKeeperDraftParserTest` (prefix streaming: every prefix of a full output yields a monotonically growing draft); `RecipeKeeperClipConfigTest` (detect on structured/text/none fixtures, ranking, budget truncation at paragraph boundary); `IsoDuration` edge cases; Room DAO instrumented test optional.
Done when: tests green; a scripted `FakeLlmEngine` round trip produces a saved `ClippedRecipe` in an in-memory Room DB.

### WS5b — `:app:recipekeeper` UI, brand, nav, labels (∥; `android-ux`)
Owner: `android-ux`. Owns: `app/recipekeeper/src/main/kotlin/com/llmlocal/recipekeeper/{designsystem,config,library,detail,output,previews}/**`, `MainActivity.kt`, `RecipeKeeperApp.kt`, `res/**`.
1. `RecipeKeeperTokens : DesignTokens` per UX spec §8 (plum/olive/saffron, parchment neutrals, serif display, flatter shapes, hero gradients, `successColor`). Launcher vector + background colour. `themes.xml` unchanged structure.
2. Labels: `RecipeKeeperClipperLabels : WebClipperLabels`, `RecipeKeeperLibraryLabels` (Library/Detail/Delete fields from UX spec §7), `RecipeKeeperModelManagementLabels` (introText + the three snackbar templates from WS3).
3. `MainActivity`: routes `library` (start), `browser?url={url}`, `extract/{candidateId}`, `recipe/{recipeId}`, `modelManagement`; back-stack rules from UX spec §1 (`popUpTo("browser")` after save; `launchSingleTop` for browser re-entry). Library and model management under `AppScaffold`; browser/extraction/detail own their scaffolds. Single `SnackbarHostState`.
4. `LibraryViewModel` + `LibraryScreen` (adaptive grid, `RecipeTile`, search, no-model banner via `GradientHero` whose action navigates to model management and a compact `DownloadProgressBar` card while a download runs, empty state, FAB "Clip a recipe", long-press menu, undo-delete snackbar).
5. `RecipeDetailViewModel` + `RecipeDetailScreen` (collapsing hero with scrim or `GradientHero` fallback, meta chips, source row → browser, check-off ingredients persisted via `setChecked`, steps with current-step highlight, `HorizontalPager` pictures, notes, share text, delete dialog, keep-screen-on, 600dp+ two-pane variant).
6. `ClipDraftCard` (the `renderDraft` slot): skeleton that fills in from `RecipeKeeperDraftParser`, dimmed tail with `StreamingCursor`, raw-output toggle.
7. `RecipeKeeperApp`: same as `RecipeApp` + `webModule`, `webClipperModule`, `recipeKeeperConfigModule`; Coil `SingletonImageLoader.Factory`.
8. Previews for every screen/state in light+dark; a11y pass per UX spec §9.
Done when: app builds, navigates through all routes with WS5a's data layer and WS4's feature, screenshots of every state attached.

### WS6 — Integration, QA, docs (sequential, last)
Owner: lead + `android-ux` for visual QA.
1. `./gradlew assemble test lint`; boundary check on `:feature:webclipper` and `:core:web`.
2. Emulator (`emulator-5554`, CPU backend) end-to-end: choose and download a model in model management (or push a cached file to `files/llm/`), open a JSON-LD recipe site → badge → clip → extraction streams → saved → detail; a plain-text blog → Tier B; a round-up page → candidate sheet; a non-recipe page → nothing detected → extract anyway; cancel mid-extraction; airplane mode during pictures.
3. Real device if available (arm64, CPU debug): record TTFT and total extraction time for a Tier A and a Tier B page; tune `budgetChars` if > 60 s.
4. Memory: watch for WebView + engine peak (engine ~1.7 GB CPU peak at 1k tokens; confirm no OOM on a 6 GB device).
5. Update `CLAUDE.md`: new modules, edges, `LlmRequest`, readiness observer, Room plugin now real, recipekeeper app shape, new gotchas (double-encoded `evaluateJavascript`, WebView holder lifecycle, cancelProcess). Update the module boundary command list.
6. Commit per stream; final PR description summarises decisions A1–A14.

### WS7 — Local evaluation harness (∥ after WS5a; `general-purpose`)
Owner: `general-purpose`. Owns: `app/recipekeeper/src/test/resources/eval/**`, `app/recipekeeper/src/test/kotlin/.../eval/**`, `app/recipekeeper/src/androidTest/**`, `tools/eval/**`.
Goal: run the **standard pipeline** (`renderLlmInput` → `RecipeKeeperPromptBuilder` → model → `RecipeKeeperParser`) against the **same model file the app uses**, locally, and turn the results into repeatable tests.
1. Fixtures: capture `PageSnapshot` JSON from 8–10 real pages with the WS2 capture code on the emulator (JSON-LD single, JSON-LD `@graph`, round-up with 3+ recipes, HowToSection instructions, a plain-text blog, a non-English page, a list page with no recipe, a very long page). Save under `src/test/resources/eval/pages/*.json` with a `README` of URL + date.
2. `EvalPromptRenderingTest` (JVM): for each fixture run `detect()` + `renderLlmInput()` + prompt builder; assert detection counts and that every rendered prompt fits `budgetChars`; write the prompts to `build/eval/prompts/<fixture>.txt`.
3. `tools/eval/run_local_model.sh`: runs every prompt through LiteRT-LM **on the Mac with the same `.litertlm` file** and writes `src/test/resources/eval/outputs/<fixture>.txt`. Runner options, in order of preference: (a) the LiteRT-LM desktop CLI (`litert-lm` binary / Python wheel; availability on macOS to **verify first**, see research notes §A); (b) if no desktop runtime, an `adb`-driven path: an instrumented `EvalRunner` test in `src/androidTest` that reads the prompts from `assets`, runs `LiteRtLlmEngine` against the model already downloaded on the emulator/device, and writes outputs to external files that the script pulls back. Either way the outputs are committed as goldens.
4. `EvalParserTest` (JVM, runs in CI without the model): parse each golden with `RecipeKeeperParser`/`RecipeKeeperDraftParser`; assert per fixture: title non-empty, ≥ 3 ingredients, ≥ 2 steps, ingredient count within ±20% of the JSON-LD count when available, servings/time parsed when the source has them, no hallucinated section headers; for STRUCTURED fixtures compare against `SchemaOrgRecipeMapper` output (Jaccard ≥ 0.8 on ingredients).
5. `EvalReport`: the JVM test also writes `build/eval/report.md` (per fixture: tier, prompt tokens estimate, parse ok/fail, field coverage, time-to-first-token and total time read from the runner's log). This is the data for decision #3 (skip the LLM for structured data or not).
6. Make the harness re-runnable when prompts or the model change: `./gradlew :app:recipekeeper:testDebugUnitTest --tests '*EvalPromptRenderingTest*' && tools/eval/run_local_model.sh && ./gradlew :app:recipekeeper:testDebugUnitTest --tests '*EvalParserTest*'`.
Done when: goldens committed for all fixtures, `EvalParserTest` green in CI without a model, report shows the Tier A vs Tier B success rates and timings on at least one real device.

---

## 5. Schedule and parallelism

```
WS0 ──┬── WS1 engine (starts with WS0, own worktree) ┐
      ├── WS2 core:web ─────────────┤
      ├── WS3 shared readiness/ux ──┼── WS6 integration/QA/docs
      ├── WS4 feature:webclipper ───┤   (WS4 integrates WS1+WS2 fakes→real here)
      ├── WS5a app data/parsers ──┬─┤
      │                           └─ WS7 eval harness (after WS5a + WS2)
      └── WS5b app UI/brand/nav ────┘
```

Up to six agents after WS0. Conflict rules: only the owning stream edits its files; cross-stream contract changes go through the lead and are announced; everybody rebases on `master` before finishing; every stream must keep `./gradlew :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug` green.

Suggested agent types: WS1/WS2/WS5a/WS7 → `general-purpose`; WS3 primitives, WS4 composables, WS5b → `android-ux` (project agent in `.claude/agents/android-ux.md`; it registers after a Claude Code restart); WS4 MVI → `general-purpose`; WS6 QA → `android-ux` with `android-cli`.

---

## 6. Risks and open product decisions

1. **Module name** (decided): `recipekeeper` / Recipe Keeper.
2. **Model acquisition** (decided): no one-tap default; the user chooses in model management (A15). Downloads are still not resumable (existing gotcha).
3. **Should STRUCTURED candidates skip the LLM?** (decided for now): no — the LLM normalises, the deterministic fallback guards quality. WS7's eval harness measures this; flip to "skip" if the numbers say so. It is one branch in `ExtractionViewModel`.
4. **CPU latency in debug** (30–90 s per recipe). Mitigated by streaming skeleton + small inputs; release/GPU is much faster but GPU has long-context bugs (use FLOAT32 activations).
5. **KSP 2.3.12 × Kotlin 2.4.21** officially unverified → WS0 gate + serialization fallback.
6. **WebView retention across navigation** → `WebViewHolder` with application context and explicit lifecycle; never hold the Activity.
7. **Late-hydrating pages** → proactive scan debounced + manual tap always re-captures; MutationObserver re-scan is Phase 2.
8. **FAB vs site chrome** (cookie banners, "jump to recipe" footers) → collapse on scroll, hide on IME, long-press "hide on this page"; evaluate a `BottomAppBar` alternative in QA.
9. **Image rights/storage** → personal use, text-only share, 8 pictures max, resized.
10. **Process death during extraction** → v1 hint + keep-screen-on; Phase 2 `CoroutineWorker` (reuse the download worker's foreground pattern).

## 7. Phase 2 backlog (not in this plan's scope)
WorkManager-backed extraction; `ACTION_SEND`/`ACTION_VIEW` intent filter ("Share → RecipeKeeper" from Chrome); ingredient quantity/unit parsing and servings scaling; edit recipe; collections/tags; FTS search; export/import; resumable model downloads (HTTP Range) — flagged in CLAUDE.md already.
