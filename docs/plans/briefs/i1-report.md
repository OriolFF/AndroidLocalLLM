# I1 report — engine vision + capability

Branch `inputs/i1-engine-vision`.

## Budget API finding
No per-message budget parameter in 0.18.0 Kotlin. `ExperimentalFlags.visualTokenBudget` (global static, `@ExperimentalApi` opt-in) is read on every `sendMessage[Async]` and in `Engine.initialize`. `LiteRtLlmEngine` sets it under the generation mutex before each image message: `request.visualTokenBudget ?: 280`, clamped to 280 (warning logged: verified, `visualTokenBudget=560 exceeds the bundle cap, clamped to 280`). Addendum in `recipekeeper-research-notes.md` §A.

## Exact EngineConfig call
```kotlin
EngineConfig(modelPath = file, backend = backend, visionBackend = if (withVision) visionBackend else null,
    maxNumTokens = 4096, maxNumImages = if (withVision) 3 else null, cacheDir = cacheDir)
```
`withVision = visionBackend != null && maxNumImages > 0 && descriptorProvider()?.capabilities contains VISION`. Init log: `Engine ready (… vision=CPU maxImages=3)` (`vision=off maxImages=0` otherwise). Content = `Contents.of(images.map { Content.ImageBytes(it.bytes) } + Content.Text(prompt))`. Images with vision off → `LlmToken.Error(UnsupportedImagesException)`; more than 3 images → `IllegalArgumentException` error (I2's use case caps at the policy value anyway). `capabilities()` = `LlmCapabilities(vision, maxImages = 3)`.

## Files
`LiteRtLlmEngine` (+`@OptIn(ExperimentalApi)`, new ctor params `visionBackend, maxNumImages, defaultVisualTokenBudget, descriptorProvider`, `MAX_VISUAL_TOKEN_BUDGET`), `LlmModule` (`buildVisionBackend`, `MAX_NUM_IMAGES = 3`, descriptor from selection store + catalog), `core/llm/build.gradle.kts` (`LLM_VISION_BACKEND = "CPU"` debug **and** release), `LlmModelCatalog` (Gemma 4 E2B → `{TEXT, VISION}` + KDoc on checking a bundle), `FakeLlmEngine(vision)`, tests: `LlmEngineDefaultsTest` (+2), `FakeLlmEngineTest` (+2), new `LlmTaskRepositoryImplTest` (2, first tests in `:core:data`), `app/recipekeeper/src/androidTest/.../eval/VisionSpikeTest.kt` (the spike; manual install only).

## Spike (emulator-5556, LlmPhone_API_36 arm64.nosme, debug build, CPU text + CPU vision, Gemma 4 E2B)
Image: `tools/eval/screenshots/infographic/card.png` (900×1928 recipe card), system = `RecipeKeeperPromptBuilder.SYSTEM`, user = "Extract the recipe from these pictures.", maxOutput 1024, T 0.2.

| Run | TTFT | Total | Peak PSS | Parses with `RecipeKeeperParser` |
|---|---|---|---|---|
| 1 image, budget 280 | 3.8 s | 10.5 s | 2.58 GB | yes (full, correct pancakes recipe) |
| 1 image, budget 560 (clamped to 280) | 3.0 s | 9.3 s | 2.59 GB | yes (identical output) |
| 3 images (card, an unrelated launcher screenshot, card), budget 280 | 8.0 s | 13.9 s | 3.13 GB | yes |

Init: 0.2 s (lazy; the first generation loads the weights). PSS 0.19 GB before init → 0.62 GB after init → 2.6 GB during the first image run (text-only runs of the same model are ~1.7 GB), native heap 5 MB → 1.1 GB → 1.6 GB after 3 images. **Caveat:** timings are the Mac-hosted emulator (fast cores), not a phone; the eval's flagship-phone estimate (15–20 s/image) still stands. **No SIGILL/SIGSEGV/Fatal signal** in logcat; the multi-image run did not hit the #2799 crash at ~1.5k conversation tokens.

## Regression
`:app:recipes` debug arm64 installed with `adb install -r`, model copied into its `files/llm/`, two ingredients → "Simple Tomato and Egg Scramble" streamed and completed (209 conversation tokens). Stop: on this emulator generation finishes in ~3 s so a mid-stream Stop could not be exercised meaningfully (two taps ended with `Generation done`, process CPU 0 % after). The cancel code path is unchanged by I1. Recipe Keeper's installed app/data were kept (no uninstall, no `pm clear`, no `connectedDebugAndroidTest`).

## Q4 bundle finding
**The catalog's q4 URL is dead:** `…/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-q4.litertlm` returns 404. The repo currently lists `gemma-4-E2B-it.litertlm`, `-gpu`, `-web`, `_Google_Tensor_G5/G6`, `_NPU_Uncompiled`, `_Qualcomm_QCS8275`, `_intel_LNL/PTL` — no q4. So the q4 descriptor cannot be inspected and stays text-only; flag for the lead (catalog fix is outside I1's brief). Phi/Llama stay text-only (not inspected).

## Verify
`./gradlew :core:domain:test :core:llm:testDebugUnitTest :core:data:testDebugUnitTest :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug :feature:webclipper:testDebugUnitTest :app:recipekeeper:testDebugUnitTest` → BUILD SUCCESSFUL; `:app:recipekeeper:assembleDebugAndroidTest` builds.

## For CLAUDE.md
`BuildConfig.LLM_VISION_BACKEND` (`core/llm/build.gradle.kts`) is `"CPU"` in both build types — the shipped Gemma 4 E2B bundle has only a CPU vision tower; `"NONE"` disables image input. `LiteRtLlmEngine` builds the engine with `visionBackend`/`maxNumImages = 3` only when the selected `LlmModelDescriptor.capabilities` contains `ModelCapability.VISION` (set on Gemma 4 E2B; add it to other models only after `ModelInfo.from(path).inputModalities()` confirms), reports it via `LlmEngine.capabilities()`, and rejects `LlmRequest.images` otherwise with `UnsupportedImagesException`. Images are sent before the prompt text; the per-image token budget is the global `ExperimentalFlags.visualTokenBudget`, set per request and clamped to 280. Remove "no image input by design" from the `LlmRequest` line.
