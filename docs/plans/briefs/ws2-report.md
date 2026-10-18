# WS2 report — page capture (`:core:web`)

Branch `recipekeeper/ws2`. Everything lives under `core/web/**` plus the
`org-json` catalog entry (test-only). No shared contract was changed:
`PageCapture`, `PageSnapshot` and friends are exactly as WS0 froze them.

## What was found in the worktree and what changed

An earlier attempt had filled most of the module in. Reviewed and kept:
the vendored Readability files (byte-identical to the jsdelivr 0.6.0
artifacts), `THIRD_PARTY_LICENSES.md`, `PageSnapshotMapper`, `webModule`,
the JSON-LD fixtures and most of `JsonLdExtractor`. Fixed or rewritten:

- **`WebViewPageCapture` never injected Readability.** It only loaded
  `capture.js` and `quickscan.js`, so `readable` would always have been
  `null` on device. It now assembles one script that wraps Readability.js,
  Readability-readerable.js and the `capture.js` IIFE in an outer function
  (`return (\n…\n)` on one line to avoid ASI returning `undefined` past
  the header comments). Assets are read on `dispatchers.io` once and cached.
- **`JsonLdExtractor.clean` stripped `<!-- … -->` as a prefix**, discarding
  the JSON inside the comment. It now unwraps BOM, comment and CDATA
  (`//<![CDATA[` too). `@type` values are normalised to bare names
  (`https://schema.org/Recipe`, `schema:Recipe` → `Recipe`).
- **`IsoDuration` summed ranges** (`"30-40 minutes"` → 70) and its test
  enshrined that. Rewritten: ranges split on `-`/`–`/`to`/`or` and take the
  max; decimals (`PT1.5H`), weeks, `2h30`, seconds; malformed ISO-looking
  strings (`P2M`) are not fed to the free-text fallback.
- **`capture.js`**: lazy images whose `src` is a `data:` placeholder were
  dropped (now every candidate is tried and `data:`/`blob:` skipped, largest
  `srcset` entry by descriptor); microdata `name` fell back to the whole
  card's text (now proper per-element values: `content`/`src`/`href`/
  `datetime`, nested `itemscope`s become nested objects, nested-in-recipe
  roots are not double-emitted); the recipe-card selector now matches the
  brief (`[class*="recipe"]` included, `body`/`html` excluded); sections
  climb out of wrapper elements and descend into wrappers that contain the
  next heading, so intro paragraphs before an `h2` inside `.entry-content`
  are not lost; `innerText` is used for section text (CSS-aware) with a
  block-aware fallback; `truncated` now reflects only the 4 MB guard;
  `readable.siteName` is passed through.
- **`quickscan.js`** could not count comment-wrapped JSON-LD; it strips the
  same wrappers.
- **`ImageDownloader`**: creates the parent directory, cancels the OkHttp
  call when the coroutine is cancelled (`enqueue` + `invokeOnCancellation`),
  `ensureActive()` per read, cleans both `.part` and the target on failure,
  recycles bitmaps, 15 s call timeout via a child client.

## Files

```
core/web/build.gradle.kts                                   +implementation(libs.androidx.core.ktx), +testImplementation(libs.org.json)
core/web/THIRD_PARTY_LICENSES.md                            Apache-2.0 notice for @mozilla/readability 0.6.0
core/web/src/main/assets/webclipper/Readability.js          vendored, unmodified
core/web/src/main/assets/webclipper/Readability-readerable.js
core/web/src/main/assets/webclipper/capture.js              IIFE → object (schema in header comment)
core/web/src/main/assets/webclipper/quickscan.js            IIFE → number
core/web/src/main/kotlin/com/llmlocal/core/web/WebViewPageCapture.kt
core/web/src/main/kotlin/com/llmlocal/core/web/PageSnapshotMapper.kt   internal, JVM-testable
core/web/src/main/kotlin/com/llmlocal/core/web/JsonLdExtractor.kt
core/web/src/main/kotlin/com/llmlocal/core/web/IsoDuration.kt
core/web/src/main/kotlin/com/llmlocal/core/web/ImageDownloader.kt
core/web/src/main/kotlin/com/llmlocal/core/web/di/WebModule.kt
core/web/src/test/kotlin/com/llmlocal/core/web/{IsoDuration,JsonLdExtractor,PageSnapshotMapper,WebViewPageCapture,ImageDownloader}Test.kt
core/web/src/test/resources/jsonld/*.json                   8 fixtures (single, @graph, array, @type array, mainEntity, HowToSection, ImageObject, yield array)
core/web/src/test/resources/page_snapshot.json              real capture.js output (jsdom) for the WPRM fixture
core/web/tools/capture-harness/{harness.mjs,fixture-wprm.html,fixture-plain-blog.html}   dev aid, not in the build
gradle/libs.versions.toml                                   org-json = "20250517" (version + library)
```

## Public signatures (unchanged from WS0 where they existed)

```kotlin
interface PageCapture { suspend fun capture(webView: WebView): Outcome<PageSnapshot>; suspend fun quickScanStructured(webView: WebView): Int }
class WebViewPageCapture(context: Context, dispatchers: DispatcherProvider) : PageCapture
object JsonLdExtractor { fun fromScriptBlocks(blocks: List<String>): List<JsonLdNode> }
object IsoDuration { fun toMinutes(iso: String?): Int? }
class ImageDownloader(client: OkHttpClient, dispatchers: DispatcherProvider) {
    suspend fun download(url: String, referer: String?, into: File, maxLongEdgePx: Int): Outcome<File>
}
val webModule: Module   // single<PageCapture> { WebViewPageCapture(androidContext(), get()) }, single { ImageDownloader(get(), get()) }
internal object PageSnapshotMapper { fun fromJson(json: JSONObject, capturedAt: Long): PageSnapshot }
```

`capture.js` return schema (what `PageSnapshotMapper` reads):
`{ url, title, siteName, jsonLd: string[], microdata: object[], og: {k: v}, readable: {title, byline, excerpt, siteName, textContent, length} | null, sections: [{heading, level, text, imageSrcs, topOffset}], images: [{src, alt, width, height, naturalWidth, naturalHeight, top, inRecipeCard}], truncated }`.

## Decisions worth knowing

- `IsoDuration.toMinutes` returns `null` for a **zero** duration (`PT0M`,
  `PT0S`, `"0 minutes"`). Sites emit these for times they never filled in
  and a "0 min" badge is noise. Non-zero seconds round to the nearest
  minute (`PT90S` → 2).
- `JsonLdNode.types` are bare names. Nodes are emitted for the top-level
  object, every `@graph` entry (recursively) and every `mainEntity`;
  `recipeInstructions`/`itemListElement` children stay inside `raw` and are
  **not** flattened into nodes. A `WebPage` that carries a `mainEntity`
  Recipe is emitted too (after the Recipe).
- Microdata objects are synthesised in JS with `@context`/`@type` and go
  through `JsonLdExtractor` like any block, so WS5a's `detect` sees one
  node kind only. Nested `itemscope`s (HowToStep, Person, ImageObject…)
  become nested objects, single values stay scalars, repeats become arrays.
- Image `src` preference: `currentSrc` → `src` → `data-src` →
  `data-lazy-src` → `data-original` → largest `data-srcset`/`data-lazy-srcset`
  /`srcset` entry; `data:`/`blob:`/non-http(s) are skipped. Filter: displayed
  area ≥ 150×150 **or** `naturalWidth > 300`.
- `inRecipeCard` uses the brief's selector but ignores a match on
  `body`/`html` (`<body class="single-recipe">` is common).
- Section walk: text of following siblings until the next `h1–h3`; when
  siblings run out it climbs to the parent's next sibling (so content after
  a `<header>`/card wrapper is still attributed to the nearest heading); a
  sibling containing a heading is descended into up to that heading.
  `nav/header/footer/aside/script/style/noscript/template/iframe` never
  contribute text. Cap 20 sections × 6000 chars. One `level: 0` section
  carries intro text before the first heading, or the whole body when the
  page has no headings.
- `og` map: keys lower-cased, first value per key wins (first `og:image`
  is normally the best one); `meta[name="og:*"]` accepted as well.
- `ImageDownloader` sends `User-Agent` (mobile Chrome), `Accept` and
  `Referer`; rejects `Content-Length` > 15 MB and bodies that grow past it.
  Output is always JPEG q85 regardless of input format.

## Tests and builds

- `./gradlew :core:web:testDebugUnitTest` — **37 tests, all green**
  (IsoDuration 9, JsonLdExtractor 14, PageSnapshotMapper 8,
  WebViewPageCapture 3, ImageDownloader 3). The real `org.json` is on the
  test classpath via `libs.org.json`; the mockable android.jar's stub
  comes after it so no `isReturnDefaultValues` was needed.
- jsdom harness (`core/web/tools/capture-harness/harness.mjs`, needs a
  directory with `jsdom` installed; run from that directory):
  WPRM-style fixture → quickScan 2, 1 JSON-LD Recipe (comment-wrapped
  `@graph`) + 1 microdata Recipe, 4 images (lazy `data:` placeholder
  resolved to `data-src`, tiny icon dropped), 6 sections, Readability
  article; plain-blog fixture (no structured data) → quickScan 0,
  Readability byline + ingredients/method sections, 1 image; a 4 MB page
  → `readable: null`, `truncated: true`. The harness is a dev aid for WS6,
  not part of the Gradle build.
- `./gradlew :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug` — **BUILD SUCCESSFUL**.
- `./gradlew :core:web:lintDebug` — **no issues** (one `UseKtx` warning was
  fixed by using `Bitmap.scale`; `androidx-core-ktx` is now declared
  explicitly in `core/web/build.gradle.kts` instead of relied on transitively).
- `./gradlew :core:web:dependencies --configuration releaseRuntimeClasspath`
  — project edges are only `:core:common`, `:core:model`, `:core:network`;
  no `:feature` or `:app` edge.

## Not done / for the lead

- **Device run against two real pages** (brief "done when") was not
  possible: `_common.md` forbids installing/launching on the shared
  emulator. The jsdom harness is the stand-in; WS6 should load one JSON-LD
  site and one plain blog and log the `PageSnapshot`.
- No shims were added (nothing from WS3 was needed).
- `ImageDownloader.download` has no JVM test of the happy path
  (`BitmapFactory` is a stub on the JVM); the arithmetic and failure
  clean-up are covered.
- Resumable downloads / `Range` are out of scope here as in the rest of the
  app.
- Round-up pages that list recipes only through `ItemList → ListItem →
  item` (not `@graph`/arrays/`mainEntity`) will not produce Recipe nodes;
  add `itemListElement[].item` following in `JsonLdExtractor` if WS5a wants
  it.
