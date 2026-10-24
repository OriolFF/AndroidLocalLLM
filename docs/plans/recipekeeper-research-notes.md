# Research notes — LiteRT-LM 0.18, WebView extraction, persistence (verified 2026-10-18)

Companion to `recipekeeper-app-plan.md`. Sources were checked against Maven metadata, GitHub sources/releases/issues and official docs; `UNVERIFIED` marks exceptions.

## A. LiteRT-LM 0.18.0 Kotlin API (`com.google.ai.edge.litertlm`)

Artifact `com.google.ai.edge.litertlm:litertlm-android:0.18.0` (Google Maven, 2026-10-06, latest). 0.18.0 clamps an over-large `max_num_tokens` to the bundle's static limit and logs it.

```kotlin
data class EngineConfig(modelPath: String, backend: Backend = Backend.CPU(), visionBackend: Backend? = null, audioBackend: Backend? = null,
  maxNumTokens: Int? = null /* total context */, maxNumImages: Int? = null, cacheDir: String? = null, activationDataType: ActivationDataType? = null)
sealed class Backend { data class CPU(val threadCount: Int? = null); class GPU; data class NPU(val nativeLibraryDir: String = ""); class GOOGLE_TENSOR }
class Engine(engineConfig) : AutoCloseable { fun initialize(); fun isInitialized(); fun createConversation(config = ConversationConfig()): Conversation; fun close() }
data class ConversationConfig(systemInstruction: Contents? = null, initialMessages: List<Message> = listOf(), tools = listOf(), samplerConfig: SamplerConfig? = null,
  automaticToolCalling = true, channels = null, extraContext = emptyMap(), loraConfig = null, prefillPrefaceOnInit = false, maxOutputToken: Int? = null,
  thinkingConfig: ThinkingConfig? = null, enableResponseFormat: Boolean = false /* MUST be true to pass responseFormat */, enableSpeculativeDecoding: Boolean? = null, chatTemplate: String? = null)
data class SamplerConfig(topK: Int, topP: Double, temperature: Double, seed: Int = 0)
class Conversation : AutoCloseable {
  fun sendMessage(message|contents|text, extraContext = emptyMap(), repetitionPenaltyConfig = null, noRepeatNgramConfig = null, suppressTokensConfig = null,
                  maxOutputToken: Int? = null, thinkingConfig = null, responseFormat: ResponseFormat? = null): Message
  fun sendMessageAsync(...same...): Flow<Message>; fun sendMessageAsync(message, callback: MessageCallback, ...)
  fun cancelProcess()   // cancels in-flight inference; streaming gets onError(CancellationException)
  fun getTokenCount(): Int; fun close()
}
sealed class Content { Text(text); ImageBytes(bytes); ImageFile(absolutePath); AudioBytes; AudioFile; ToolResponse }
object ResponseFormat { fun json(schema: String | Map<String, Any?>); fun regex(pattern) }   // LLGuidance-backed
```

- Vision (informational only, **out of scope**: the user decided on 2026-10-18 that pictures are stored with the recipe and never sent to the model): set `EngineConfig(visionBackend = Backend.CPU()|GPU())`, send `Contents.of(Content.Text(prompt), Content.ImageBytes(bytes))`. The `gemma-4-E2B-it.litertlm` bundle (2,588,147,712 bytes) includes vision + audio encoders, loaded on demand. `ExperimentalFlags.visualTokenBudget = 70|140|280|560|1120`. CPU vision speed on Android: UNVERIFIED.
- Context: model card says 32K; default when `maxNumTokens == null` is `max(4096, round-up to 4096 multiple of prompt)`; set it explicitly (plan: 4096). Known GPU issues: #3444 (fails near ~2k tokens at 4096 on some GPU paths), #4001/#3879/#3913 (FP16 activations corrupt long contexts on Adreno → `activationDataType = FLOAT32`), #2799 (SIGSEGV multi-image near limit).
- Tokens: Gemma 262k SentencePiece vocab; ~1.3 tokens/word English (UNVERIFIED for Gemma 4). Cleaned 3k-word page ≈ 4k tokens; raw HTML 3–5× more. With 4096 total, truncate input to ~2,500 tokens (~9,000 chars) and keep ~1,000–1,500 for output.
- Speed (official, 1024-token prompt, 4 threads): S26 Ultra CPU 557 tok/s prefill, 46.9 tok/s decode, TTFT 1.8 s, 1733 MB peak; GPU 3808/52.1/0.3 s/676 MB. Mid-range Android CPU UNVERIFIED, estimate 150–300 tok/s prefill, 10–20 tok/s decode → 2.5k-token prompt ≈ 10–15 s TTFT, 600-token output ≈ 30–60 s.
- Constrained JSON: `ConversationConfig(enableResponseFormat = true, samplerConfig = SamplerConfig(1, 1.0, 0.0))` then `sendMessageAsync(msg, responseFormat = ResponseFormat.json(schema))`. Issue #3944 (0.17.0, Gemma 4 E2B, GPU): breaks with speculative decoding on ("compute_mask() called after stop"); works with it off. Unverified on CPU → plan WS1 spike.
- Other: `Engine.initialize()` up to ~10 s off main thread; set `cacheDir`; one Conversation at a time; `Backend.CPU(threadCount)`; GPU needs `libOpenCL.so`/`libvndksupport.so` `uses-native-library` (already in manifests).
- Docs: https://developers.google.com/edge/litert-lm/android , https://developers.google.com/edge/litert-lm/api_overview , https://github.com/google-ai-edge/LiteRT-LM , https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm

### I1 addendum — vision API in 0.18.0 (`javap -p` of `classes.jar`, 2026-10-24)

- `EngineConfig(modelPath: String, backend: Backend, visionBackend: Backend?, audioBackend: Backend?, maxNumTokens: Int?, maxNumImages: Int?, cacheDir: String?, activationDataType: ActivationDataType?)` — all named args used by the engine.
- `Content.ImageBytes(bytes: ByteArray)`, `Contents.of(List<Content>)`; images go **before** `Content.Text`.
- **Visual token budget: no per-message parameter.** `ExperimentalFlags.visualTokenBudget: Int?` is a process-global static (get/set, not annotated `@ExperimentalApi` itself but needs the opt-in at use). The bytecode reads it in `Conversation.sendMessage`/`sendMessageAsync` (passed to JNI each send) and in `Engine.initialize`. So it behaves per message if set under the engine mutex before each send; `ConversationConfig.extraContext` / `sendMessageAsync(extraContext)` are generic maps, not the budget. `LiteRtLlmEngine` sets `request.visualTokenBudget ?: defaultVisualTokenBudget (280)` clamped to `[1, 280]` (logs a warning when clamped) before each image message, and the default at engine creation.
- `ModelInfo.from(path)`: `inputModalities()`, `maxVisionTokenBudget()`, `maxContextTokens()`, `supportedBackends(Modality)` — the way to inspect a bundle before adding `ModelCapability.VISION`.
- `Backend.CPU(threadCount: Int? = null)`, `Backend.GPU()` unchanged.

### WS1 spike addendum (2026-10-18)

**On-device JSON-schema spike: NOT RUN.** No `.litertlm` file exists on this machine (`find ~ -name "*.litertlm" -size +100M` is empty), the only attached device `emulator-5554` is `sdk_gphone64_arm64` (arm64-v8a, API 36 — not the x86_64 image the plan assumed) with **225 MB free** on `/data`, so the 2.58 GB Gemma 4 E2B bundle could neither be pushed nor downloaded. Per the WS1 brief the 2.6 GB download was not attempted. The questions below stay open until a model is on a device; everything else in this addendum was verified statically with `javap` against `litertlm-android-0.18.0.aar` (`~/.gradle/caches/.../litertlm-android/0.18.0/.../classes.jar`) and by compiling `LiteRtLlmEngine` against it.

**API confirmed by javap / compilation (parameter names are the Kotlin names, verified by named-argument compilation):**

- `EngineConfig(modelPath: String, backend: Backend, visionBackend: Backend?, audioBackend: Backend?, maxNumTokens: Int?, maxNumImages: Int?, cacheDir: String?, activationDataType: ActivationDataType?)` — matches §A. `LiteRtLlmEngine` now always passes `maxNumTokens` (default 4096, `LiteRtLlmEngine.DEFAULT_MAX_NUM_TOKENS`, set explicitly in `llmModule`).
- `ConversationConfig` has exactly the 14 fields listed in §A, in that order; the ones we use: `systemInstruction: Contents?`, `samplerConfig: SamplerConfig?`, `maxOutputToken: Int?` (singular), `enableResponseFormat: Boolean = false`, `enableSpeculativeDecoding: Boolean? = null`.
- `SamplerConfig(topK: Int, topP: Double, temperature: Double, seed: Int = 0)`.
- `Conversation.sendMessageAsync(contents: Contents, extraContext: Map<String, Any?> = emptyMap(), repetitionPenaltyConfig = null, noRepeatNgramConfig = null, suppressTokensConfig = null, maxOutputToken: Int? = null, thinkingConfig = null, responseFormat: ResponseFormat? = null): Flow<Message>` — `responseFormat` is the 8th and last parameter; `Contents.of(String)`, `Contents.of(vararg Content)`, `Contents.of(List<Content>)` all exist.
- `ResponseFormat.json(schema: String)`, `ResponseFormat.json(schema: Map<String, Any?>)`, `ResponseFormat.regex(pattern: String)`; `ResponseFormat.Type = { REGEX, JSON_OBJECT }`; `schemaOrPattern` is stored as a plain `String`. The Kotlin layer does **no** schema validation — a malformed schema will only fail inside the native LLGuidance compiler at send time, so expect an `LlmToken.Error` (not a construction-time exception) for a bad schema.
- Kotlin-side guard (synchronous `IllegalArgumentException` from `sendMessage`/`sendMessageAsync`): `"response_format cannot be used unless enableResponseFormat=True was passed to ConversationConfig."` `LiteRtLlmEngine` derives both from the same `request.jsonSchema != null`, so this cannot trip.
- `Conversation.cancelProcess()`, `Conversation.getTokenCount(): Int`, `Conversation.getBenchmarkInfo(): BenchmarkInfo`, `Conversation.isAlive` exist. JNI also exposes `nativeMaxContextTokens(engineHandle)` (not surfaced on the public Kotlin `Engine` in 0.18.0) — the only way to read back the *clamped* context size today is the runtime's log line.

**Cancellation semantics (important, drove the WS1 design):**

- `sendMessageAsync(...): Flow<Message>` is `callbackFlow { sendMessageAsync(contents, callback, …); awaitClose { } }` and the `awaitClose` lambda is **empty** (`invokeSuspend$lambda$0` returns `Unit`). Cancelling the collecting coroutine therefore closes the channel but does **not** stop native inference; the model keeps running until EOS and `trySend` silently fails. This is why the old `cancel()` no-op left CPU burning after Stop.
- `LiteRtLlmEngine` now keeps the live `Conversation` in an `AtomicReference`, calls `cancelProcess()` from `cancel()` and from the flow's `CancellationException` path (collector cancelled), then `close()`s in `finally`.
- `JniMessageCallbackImpl.onError(code, msg)` maps one error code to `java.util.concurrent.CancellationException(msg)` (the `cancelProcess()` path) and everything else to `LiteRtLmJniException`. The `callbackFlow` is closed with that cause, so a cancelled generation surfaces to the collector as a `CancellationException`; `LiteRtLlmEngine` swallows it (ends the stream quietly if the collector is still alive, rethrows if the collector itself was cancelled) and never emits `LlmToken.Error` for it.
- UNVERIFIED on device: whether `close()` immediately after `cancelProcess()` is safe while the native decode thread is still unwinding. The AI Edge Gallery app does the same sequence; if a native crash appears in logcat on Stop, add a short wait for the `CancellationException` to arrive before `close()`.

**Issue #3944 ("compute_mask() called after stop") mitigation available but not applied:** `ConversationConfig.enableSpeculativeDecoding: Boolean?` defaults to `null` (runtime default). If the device spike reproduces the error with `jsonSchema` set, pass `enableSpeculativeDecoding = false` in `LiteRtLlmEngine` whenever `request.jsonSchema != null`. Left out until observed, to avoid slowing free-text generation.

**Runtime sanity on `emulator-5554` (no model):** `recipes-arm64-v8a-debug.apk` built from the WS1 branch installs and launches; Koin starts 19 definitions; `LiteRtLlmEngine` is constructed through `llmModule` with `maxNumTokens = 4096` (no crash); process idles at 0% CPU; `initialize()` correctly fails with "Model file does not exist". Streaming and Stop → CPU-drop were **not** observed.

**How to run the device spike once a model is present** (`adb shell run-as com.llmlocal.recipe ls files/llm/` shows a `.litertlm`):

1. Temporary change in `core/data/.../LlmTaskRepositoryImpl.kt`: replace `engine.generateStream(prompt)` with `engine.generateStream(LlmRequest(prompt, jsonSchema = SCHEMA, temperature = 0.0))` where `SCHEMA = """{"type":"object","properties":{"title":{"type":"string"},"ingredients":{"type":"array","items":{"type":"string"}}},"required":["title","ingredients"]}"""`. Revert afterwards.
2. `./gradlew :app:recipes:installDebug`, `adb logcat -c`, generate once; watch `adb logcat -s LiteRtLlmEngine:* litertlm:* LiteRtLm:*`.
3. Record: (a) did `createConversation` accept `enableResponseFormat = true` (otherwise `LlmToken.Error` right away), (b) is `state.streamedText` valid JSON matching the schema (paste into any JSON validator), (c) wall time first-token/last-token vs. the same prompt with `jsonSchema = null` (stamp `Log.d` in the `Partial` branch), (d) any `compute_mask() called after stop` / `SIGSEGV` lines, (e) the `Generation done (conversation tokens=N)` debug line `LiteRtLlmEngine` now logs after every normal generation (that is `getTokenCount()`; compare with prompt+output word count to calibrate the §A ~1.3 tokens/word estimate), (f) `adb shell top -n 1 | grep llmlocal` before and 3 s after tapping Stop mid-generation (expect the process to fall back to ~0%).
4. If (d) shows the compute_mask error, retry with `enableSpeculativeDecoding = false` (see above) before concluding constrained decoding is unusable on CPU.

## B. WebView page extraction

- `WebView.evaluateJavascript(script, callback)`: UI thread, async; result is JSON-encoded by Chromium. A JS string comes back quoted/escaped (double-encoded if the script did `JSON.stringify`); `undefined` comes back as the 4-char string `"null"`. **Return an object from an IIFE** and parse the result with `JSONObject(result)`. No documented size limit (the `EvaluationResultSizeLimitExceededException` belongs to `androidx.javascriptengine`); multi-MB works but janks → send cleaned text, not raw HTML. Alternative for large payloads: `WebViewCompat.addWebMessageListener` + `postMessage` chunks.
- Run after `onPageFinished` plus `document.readyState === "complete"`/settle delay; allow manual re-run (SPA hydration).
- schema.org/Recipe: `script[type="application/ld+json"]` → `JSON.parse` each (try/catch); block may be object, array, or `{"@graph":[...]}`; `@type` string or array; also `WebPage.mainEntity → Recipe`. Microdata `[itemscope][itemtype*="schema.org/Recipe"]` with `itemprop` name/recipeIngredient(ingredients)/recipeInstructions/image; RDFa `[typeof~="Recipe"]`. Fields: `name`, `description`, `image` (string | string[] | ImageObject{url,contentUrl,width,height} | ImageObject[]), `recipeIngredient` (legacy `ingredients`), `recipeInstructions` (Text | Text[] | HowToStep[]{text,name,image,url} | HowToSection[]{name,itemListElement} | ItemList), `recipeYield` (Text | QuantitativeValue | array), `prepTime`/`cookTime`/`totalTime` ISO-8601 (`PT1H30M`, `PT0H20M0S`, `P0DT0H30M`), `author`, `recipeCategory`, `recipeCuisine`, `keywords`, `nutrition`, `datePublished`, `video`. Reference implementation: recipe-scrapers `_schemaorg.py`/`_utils.py`.
- Readability: `@mozilla/readability` 0.6.0, Apache-2.0, `Readability.js` ≈ 90 KB raw / 25 KB gz, `Readability-readerable.js` 4 KB (`isProbablyReaderable`). Inject via `evaluateJavascript` (or `WebViewCompat.addDocumentStartJavaScript` / 1.16+ `addJavaScriptOnEvent` in an isolated world). Use `new Readability(document.cloneNode(true), {charThreshold: 500}).parse()` → `{title, content, textContent, excerpt, byline, length, siteName, lang}`; `parse()` mutates the DOM (hence the clone); it strips images aggressively → take images from our own DOM scan.
- jsoup 1.23.2 is the Kotlin-side alternative (needs core-library desugaring NIO); no layout info, so do images in JS. Plan uses the JS hybrid.
- Image candidates (JS): for each `document.images`: `src = currentSrc || src || dataset.src || dataset.lazySrc || last srcset entry`, `alt`, `getBoundingClientRect()` w/h, `naturalWidth/Height`, `top + scrollY`, `inRecipeCard = !!img.closest('[itemtype*="Recipe"], .wprm-recipe, .tasty-recipes, .mv-create-card, [class*="recipe"]')`; filter `data:` and tiny; absolute via `new URL(src, document.baseURI)`. Rank JSON-LD image > `og:image`/`og:image:secure_url`/`twitter:image` > largest displayed in recipe card near title > step images in DOM order.
- `androidx.webkit:webkit:1.17.1` (minSdk 24 since 1.16). Settings: JS on (unavoidable), `domStorageEnabled = true`, `allowFileAccess/allowContentAccess = false`, `safeBrowsingEnabled = true`, `mixedContentMode = NEVER_ALLOW`; never `addJavascriptInterface` on arbitrary pages; override `onRenderProcessGone`. `shouldOverrideUrlLoading(view, WebResourceRequest)`: `false` for http/https, `true` + Intent for `intent:`/`mailto:`/`tel:`. `onProgressChanged`, `onReceivedTitle`, `doUpdateVisitedHistory`. Cookies persist by default; consent banners have no API (optional CSS hider for common CMPs, fragile).
- Compose: `AndroidView(factory, update, onRelease)`; keep the instance outside recomposition; never `loadUrl` in `update` on every recomposition; `saveState/restoreState` with a `Bundle`; `BackHandler(enabled = canGoBack)`; no nesting in `LazyColumn`; hoist above the NavHost or hold in an application-context holder; destroy on release to avoid native leaks. Accompanist WebView is deprecated.

## C. Persistence and images

- KSP: `com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12` is the latest (2026-09-09), decoupled from Kotlin versions since 2.3.0; built against Kotlin 2.3.20, Kotlin guarantees N reads N+1 metadata. Support for KGP 2.4.21 officially UNVERIFIED → build gate in WS0.
- Room 2: `androidx.room:room-runtime:2.8.5`, `room-compiler:2.8.5` (ksp), Gradle plugin `androidx.room:2.8.5` + `room { schemaDirectory("$projectDir/schemas") }`; KSP2 supported. Room 3 (`androidx.room3:*:3.0.3`, KSP-only, coroutine-first, bundled SQLite driver) is an option; plan uses Room 2.8.5.
- No-KSP fallback: `org.jetbrains.kotlin.plugin.serialization:2.4.21` + `kotlinx-serialization-json:1.11.0`; one JSON per recipe under `filesDir/recipes/<uuid>.json`.
- Coil 3.6.3 (`io.coil-kt.coil3:coil-compose`, `coil-network-okhttp`); minSdk 23; 3.6.3 fixes an AGP 9.4.0 R8 metadata failure. Wire OkHttp: `ImageLoader.Builder(ctx).components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }`, register via `SingletonImageLoader.Factory` in the Application. Loads `File`, `Uri`, http(s) strings.
- Image download with OkHttp 5.5.0: set `Referer` = page URL and a browser `User-Agent` (many CDNs require it); `Response.body` non-null; stream to temp file then rename; decode with `inJustDecodeBounds` → `inSampleSize` so long edge ≤ 1280 px (hero) / 800 px (others); JPEG q85 (or WEBP_LOSSY API 30+); reject natural < 300×300; cap 8 images, 15 MB `Content-Length`, 15 s call timeout; store under `filesDir/recipes/<id>/`.

## D. Detection heuristics from existing clippers

recipe-scrapers / Mealie / Tandoor: schema first (JSON-LD + microdata), walk `@graph`, match `Recipe` in `@type` or `mainEntity`; site-specific CSS plugins as fallback; OpenGraph for title/image; normalise image (first / `.url`, http(s) only), instructions (flatten sections, strip duplicated step names), times (ISO then d/h/m/s regex, ranges → max), yields (first number + unit, default "servings"). Mealie offers an optional LLM extraction of page text when no schema is present — exactly Gemma's role here. Signals for "is there a recipe": JSON-LD Recipe; URL path `recipe|recipes|rezept|receta|recepta|recette|ricetta`; DOM `[itemtype*=Recipe]`, `.wprm-recipe-container`, `.tasty-recipes`, `.mv-create-card`, `[class*="recipe-card"]`; ≥3 list items matching `^\s*[\d½¼¾⅓⅔/.\-]+\s*(g|kg|ml|l|cup|tbsp|tsp|oz|lb)?`; an ingredients heading followed by instructions/method/directions/steps. Multiple recipes = multiple Recipe nodes (round-ups) or repeated ingredient headings → chooser.

## Version pins

| Thing | Pin |
|---|---|
| LiteRT-LM | `0.18.0` (latest) |
| androidx.webkit | `1.17.1` |
| Readability | `@mozilla/readability` 0.6.0, Apache-2.0 |
| KSP | `2.3.12` |
| Room | `2.8.5` (plugin `androidx.room`) |
| kotlinx-serialization (fallback) | plugin `2.4.21`, json `1.11.0` |
| Coil | `3.6.3` |
| OkHttp | `5.5.0` (current) |
