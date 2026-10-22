# X3 report — clipper-feature defects

Branch `fixes/x3`, three commits (one per item). Verify run: `./gradlew :core:web:testDebugUnitTest :feature:webclipper:testDebugUnitTest :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug` — green.

## 1. Defect 1 — picture CDN hot-link/bot protection
- `core/web/ImageDownloader.download(url, referer, into, maxLongEdgePx, cookies: String? = null)` — new trailing optional `cookies` (sent as `Cookie`). Old callers compile unchanged.
- `internal fun buildRequest(url, referer, cookies)` assembles headers (UA, Accept, `Referer`/`Cookie` only when non-blank); unit-tested in `ImageDownloaderTest`.
- On HTTP 403 with a Referer, one retry without `Referer` (cookies kept).
- `feature/webclipper/data/ImageDownloaderPictureDownloader` reads `CookieManager.getInstance().getCookie(url)` (guarded by `runCatching`) and passes it. `core/web` stays WebView-agnostic.
- Deviation: the `PictureDownloader` domain interface is **not** changed. The adapter already receives the image URL, so it can look the cookies up itself; changing the interface would have forced edits to every fake in tests for no gain.
- **Not verified on device**: see below. The QA report's note that Chefkoch's CDN 403s even with browser UA + Referer from a Mac suggests cookies may not be enough (TLS fingerprint); the in-page `fetch()` fallback proposed in the QA report was not in this brief's scope and is not implemented.

## 2. Defect 6 — "2 recipes found" for one recipe
- `quickscan.js`: microdata/RDFa roots counted only when JSON-LD found 0 Recipe nodes.
- New fixture `core/web/tools/capture-harness/fixture-jsonld-and-microdata.html` (JSON-LD + microdata of the same recipe). Run through the jsdom harness: `quickScan: 1` (was 2 by construction); `fixture-wprm.html` still 1.
- No Kotlin unit test: the script only runs in a JS engine; the jsdom harness is the existing test path for it. The full `detect()` path already dedupes (QA report), so unchanged.

## 3. Finding B — snackbars dying with the navigation transition
- `WebClipperExtractionRoute` and `WebClipperBrowserRoute` gained `onShowSnackbar: ((String) -> Unit)? = null`; when set, `ShowSnackbar` effects go through it, otherwise the old behaviour.
- `RecipeKeeperNavHost` passes `{ message -> scope.launch { snackbarHostState.showSnackbar(message) } }` (root scope) to both. Library/detail routes untouched (not needed).

## Device verification — NOT DONE
Installed the arm64 debug APK on emulator-5556 (`adb install -r`, model present) and loaded chefkoch.de (consent wall + "Recipe found" badge visible). Then another session started installing/instrumenting on the same emulator (`installPackageLI`, `finished inst` kills in logcat at 22:21–22:22) and killed the app mid-clip, so I stopped to avoid disrupting it. Still to check on a free device: Chefkoch and Joy of Baking pictures saved (`picturesFailed` false), and `Clip cancelled` / `Saved to your recipes` still visible ≥ 3 s after the transition.
