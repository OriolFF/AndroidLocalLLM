# Brief — WS2: page capture (`:core:web`)
Read `docs/plans/briefs/_common.md` first, then `docs/plans/recipekeeper-app-plan.md` §2.2, §3.1, §3.6 and **WS2** in section 4, and `docs/plans/recipekeeper-research-notes.md` §B (evaluateJavascript double-encoding, Readability 0.6.0, image heuristics, schema.org shapes) and §C (image download/resize).

**You own:** `core/web/**`, plus one catalog entry `org-json` in `gradle/libs.versions.toml` (test-only).

Steps (details in the plan):
1. Vendor `@mozilla/readability@0.6.0` `Readability.js` and `Readability-readerable.js` into `core/web/src/main/assets/webclipper/` (fetch from `https://cdn.jsdelivr.net/npm/@mozilla/readability@0.6.0/`), add `core/web/THIRD_PARTY_LICENSES.md` (Apache-2.0 notice).
2. Write `capture.js` (IIFE **returning an object**, never a stringified one): JSON-LD raw script texts; microdata `[itemscope][itemtype*="schema.org/Recipe"]` converted to pseudo-JSON-LD objects; OG/twitter meta map; Readability on `document.cloneNode(true)` → `{title, byline, excerpt, textContent, length}`; `sections` from h1–h3 (cap 20, 6k chars each, with image srcs and top offset); `images` with `currentSrc`/lazy attrs/`srcset` resolution, displayed and natural size, `top + scrollY`, `inRecipeCard`; skip `data:` and tiny images; absolute URLs via `new URL(src, document.baseURI)`; size guard (skip Readability if `outerHTML.length > 4 MB`). Also a separate tiny `quickscan.js` that only counts Recipe-like JSON-LD/microdata nodes.
3. `WebViewPageCapture : PageCapture` using `suspendCancellableCoroutine` + `WebView.evaluateJavascript` on the main dispatcher (`DispatcherProvider.main`), unwrap the `"null"` case, parse on `default`, map to `PageSnapshot`. Inject the JS assets by reading them once from `AssetManager`.
4. `JsonLdExtractor.fromScriptBlocks` (pure Kotlin): tolerant parse, expand arrays and `@graph`, `@type` → `List<String>`, follow `mainEntity`, keep the raw `JSONObject`.
5. `IsoDuration.toMinutes`: `PT1H30M`, `PT0H20M0S`, `P0DT0H30M`, free-text `1 hr 20 mins`, ranges → max; null/blank → null.
6. `ImageDownloader.download(url, referer, into, maxLongEdgePx)`: OkHttp (Referer + browser UA headers), `Content-Length` ≤ 15 MB, 15 s call timeout, stream to `.part`, decode bounds → `inSampleSize` → JPEG q85 → rename; return `Outcome<File>`.
7. `webModule`: `single<PageCapture> { WebViewPageCapture(androidContext(), get()) }`, `single { ImageDownloader(get(), get()) }`.
8. Tests in `core/web/src/test`: JSON-LD fixtures (single Recipe, `@graph`, array of recipes, `@type` array, `mainEntity`, HowToSection, ImageObject images, yield array) in `src/test/resources/jsonld/`; `IsoDurationTest` table; a captured `PageSnapshot` JSON fixture → model mapping test (write the mapper so it is testable without a WebView, e.g. `PageSnapshotMapper.fromJson(JSONObject)`).
Done when: tests green, `:core:web` builds, boundary check clean, report written.
