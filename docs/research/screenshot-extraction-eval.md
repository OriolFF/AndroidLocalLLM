# Screenshot-based recipe extraction — evaluation (Gemma 4 E2B, LiteRT-LM 0.18)

Question: *if we use the multimodal model, can we obtain the recipe from one or several screenshots of a page?*
Short answer: **yes for short, cleanly laid-out recipes and for image-only recipes; no as a replacement for DOM capture.**
With the shipped `.litertlm` bundle the vision budget is capped at **280 tokens per image**, so a phone screenshot is read at
roughly 280 "patches". That is enough for big text and ≤ 10 ingredients, not for long ingredient lists or small print.

Everything below was measured on the Mac (CPU) with the same model file the app downloads. Scripts, outputs, truth and scores
are in `tools/eval/screenshots/`. Screenshots (28 MB) are not committed; `shoot.mjs` regenerates them.

## 1. Method

| Item | What was done |
|---|---|
| Model / runtime | `gemma-4-E2B-it.litertlm` (sha as in `tools/eval`), `litert-lm` Python wheel 0.18.0 (same release as the Android AAR), CPU engine **and CPU vision backend**, `max_num_tokens` 4096, `max_num_images` 5, top-k 40 / top-p 0.95 / seed 0, temperature 0.2, `maxOutputTokens` 1024 |
| Prompt | `RecipeKeeperPromptBuilder.SYSTEM` verbatim (`system_prompt.txt`) as a separate system message; user turn = the images + `Extract the recipe from this page.` |
| Pages (9) | 5 with JSON-LD/microdata truth: BBC Good Food pancakes, Nigella banana bread (microdata), RecipeTin tikka masala (30 ingredients), **Spanish** (directoalpaladar hojaldre y manzana), **Greek** (argiro.gr ψωμί με σταφίδες). 2 plain-text blogs: Joy of Baking pancakes, Cooking for Engineers banana bread (a table). 1 round-up: Guardian/Slater (5 recipes). 1 **image-only recipe** (a synthetic infographic card, `infographic/card.html`, rendered to PNG and shown as a single `<img>`). Pinch of Yum was dropped (its 72 000 px ad-heavy page never finished loading in headless Chrome) |
| Screenshots | Headless Chrome (puppeteer-core) with a Pixel-like profile: 412×915 CSS px, DPR 2.62 → **1079×2397** viewports; consent walls dismissed/removed. `v1…v5`: consecutive viewports (88 % step) starting at the first ingredient; `full`: one tall strip (4.5 viewports ≈ 1079×10 800) |
| Modes | **single** = `v1`; **multi** = all 2–5 viewports in one message; **tall** = the strip as one image (the engine downsamples it to the budget) |
| Budgets | **70, 140, 280** (70 only on two pages). 560 and 1120 are impossible with this bundle, see §2 |
| Baselines | **DOM** = the app's current pipeline, the committed goldens in `app/recipekeeper/src/test/resources/eval/outputs/` (6 pages that are fixtures). **Text-only** = same model on the page's rendered `innerText`, 9 000-char window starting at the first ingredient (8 pages; the infographic has no text) |
| Truth | JSON-LD/microdata of the page (fixture capture or live fetch). Plain pages and the infographic: hand-checked lists (`truth_manual.json`). For Cooking for Engineers the truth was **re-read from the screenshot**: the page shows `(167 g)`-style metric amounts that the DOM text dropped |
| Scoring | `score.py`, a Python port of the minimal `RecipeKeeperParser` line grammar (not the JVM parser). **R** = truth ingredient lines matched by an output line (line-token Jaccard ≥ 0.5, one-to-one); **P** = output lines matched; **Q** = of matched lines, share whose numbers are identical after normalising `½`/`1/2`/`1 1/2`; **S** = share of truth-step tokens present in the output steps (step *counts* are not comparable: Nigella's and the Spanish page's JSON-LD steps are one blob). A run that answers `NO RECIPE` or an empty template scores 0 |

Caveats: one run per cell (seed 0, temperature 0.2), no variance estimate; viewports are *unguided* scroll positions, not what a
user would pick; 9 pages is a probe, not a benchmark; Mac timings were taken while other jobs ran (first batch), so treat times
as ±30 %; the infographic is synthetic and clean, a best case.

## 2. What the bundle allows (found, not assumed)

`litert_lm.ModelInfo` on the shipped file: `input_modalities = text, vision, audio`, `max_vision_token_budget = 280`,
`vision_signature_selection = [70, 140, 280]`, vision backends `['cpu']`. Consequences, all reproduced:

* `visual_token_budget = 560 | 1120` → `INVALID_ARGUMENT: Visual token budget (560) cannot be larger than the engine's max vision tokens per image (280)`.
* Raising the engine cap (`max_vision_tokens_per_image`) to 560/1120 → `Failed to create engine: Requested target capacity (560) exceeds maximum available signature length (280)`.
* So `docs/plans/recipekeeper-research-notes.md` §A ("budget 70…1120") and the input-sources plan default of 560 do not hold for
  `gemma-4-E2B-it.litertlm`: **280 is the ceiling** (other Gemma 4 exports list 560/1120 as OCR-oriented budgets, [model card](https://huggingface.co/huggingworld/gemma-4-E2B-it-litert-lm)).
* The Python wheel exposes no budget argument; `run_vision.py` sets it per message through the C symbol
  `litert_lm_conversation_optional_args_set_visual_token_budget`. On Android it is `ExperimentalFlags.visualTokenBudget`.
* Cost per image is exactly the budget (prefill 637 tokens for 1 image + system + user, 1 705 for 5 images).

## 3. Results

### 3.1 Per page, budget 280 (and multi at 140)

Cells are **R / P / Q / S**. `refused/empty` = `NO RECIPE` or an empty template.

| page | single 280 | multi 280 | tall 280 | multi 140 | text-only | DOM |
|---|---|---|---|---|---|---|
| BBC pancakes (en) | 0.5 / 0.67 / 1.0 / 0.62 | 0.5 / 0.67 / 1.0 / 0.62 | 1.0 / 1.0 / 1.0 / 0.58 | refused/empty | 1.0 / 0.8 / 1.0 / 0.79 | 1.0 / 1.0 / 1.0 / 1.0 |
| Nigella banana bread (en) | 1.0 / 1.0 / 1.0 / 0.0 | 1.0 / 1.0 / 1.0 / 0.97 | refused/empty | 1.0 / 1.0 / 1.0 / 0.79 | 1.0 / 1.0 / 1.0 / 1.0 | 1.0 / 1.0 / 1.0 / 1.0 |
| RecipeTin tikka (30 ingr.) | 0.73 / 0.96 / 0.91 / 0.13 | 0.23 / 1.0 / 1.0 / 0.71 | 0.33 / 0.62 / 0.8 / 0.36 | 0.23 / 1.0 / 0.71 / 0.72 | 1.0 / 1.0 / 1.0 / 0.83 | 1.0 / 1.0 / 1.0 / 0.92 |
| DAP hojaldre (es) | refused/empty | 0.67 / 0.8 / 1.0 / 0.8 | 0.5 / 0.3 / 0.67 / 0.62 | 0.17 / 0.12 / 0.0 / 0.75 | 0.83 / 0.83 / 1.0 / 0.95 | – |
| Argiro ψωμί (el) | 0.92 / 1.0 / 1.0 / 0.0 | 0.92 / 1.0 / 1.0 / 0.78 | 0.92 / 0.92 / 1.0 / 0.43 | 0.85 / 1.0 / 1.0 / 0.68 | 0.92 / 1.0 / 1.0 / 0.94 | – |
| Joy of Baking (plain) | 1.0 / 1.0 / 1.0 / 0.92 | refused/empty | 1.0 / 1.0 / 1.0 / 0.46 | 0.88 / 1.0 / 1.0 / 0.52 | 1.0 / 1.0 / 1.0 / 1.0 | 1.0 / 1.0 / 1.0 / 1.0 |
| Cooking for Engineers (table) | 1.0 / 1.0 / 1.0 / 0.47 | 1.0 / 1.0 / 1.0 / 0.61 | refused/empty | 0.2 / 0.29 / 0.5 / 0.5 | 1.0 / 1.0 / 0.5 / 0.69 | 0.9 / 0.9 / 0.22 / 0.75 |
| Guardian round-up (5) | 0.0 / 0.0 / 0 / 0.18 | 1.0 / 1.0 / 1.0 / 0.92 | 0.56 / 0.42 / 1.0 / 0.05 | 0.78 / 0.78 / 1.0 / 0.36 | 1.0 / 0.9 / 1.0 / 0.94 | 1.0 / 1.0 / 1.0 / 1.0 |
| Infographic (image only) | 1.0 / 1.0 / 1.0 / 1.0 | 1.0 / 1.0 / 1.0 / 1.0 | 1.0 / 1.0 / 0.89 / 0.94 | 1.0 / 1.0 / 0.78 / 0.94 | – | – |

Full table (every run, with times and token counts): `tools/eval/screenshots/results.md` / `results.csv`; raw outputs in `outputs/`.

### 3.2 Means over pages, by mode and budget

| mode / budget | parsed | ingredient R | P | Q | step recall | prefill tokens | median TTFT | median total |
|---|---|---|---|---|---|---|---|---|
| DOM (6 pages) | 6/6 | 0.98 | 0.98 | 0.87 | 0.94 | 1 278 | 1.8 s | 17.9 s |
| text-only (8 pages) | 8/8 | 0.97 | 0.94 | 0.94 | 0.89 | 2 297 | 4.2 s | 24.9 s |
| **multi 280** (2–5 images) | 8/9 | **0.70** | 0.93 | 1.00 | 0.71 | 1 557 | 7.5 s | 25.8 s |
| single 280 | 8/9 | 0.68 | 0.83 | 0.86 | 0.37 | 637 | 2.0 s | 15.1 s |
| tall 280 | 7/9 | 0.59 | 0.75 | 0.91 | 0.38 | 634 | 1.9 s | 16.4 s |
| multi 140 | 8/9 | 0.57 | 0.77 | 0.75 | 0.58 | 912 | 3.3 s | 21.5 s |
| single 140 | 7/9 | 0.48 | 0.80 | 0.78 | 0.25 | 492 | 1.2 s | 9.0 s |
| tall 140 | 6/9 | 0.24 | 0.38 | 0.62 | 0.20 | 488 | 1.4 s | 12.1 s |
| 70 (single / multi / tall, 2 pages) | 5/6 | 0.37 / 0.36 / 0.09 | | | ≈ 0 | 425–685 | 1.0–2.1 s | 5–12 s |

R counts refusals as 0; P, Q are over parsed runs only. Budget 1120/560 were not measurable (§2).

### 3.3 Time

* TTFT includes the vision encoder. The engine's own TTFT for the 5-image run was 3.1 s against 7.5 s wall clock, so the CPU
  vision encoder costs **≈ 0.9 s per image** on an M-series Mac.
* Engine prefill runs at ≈ 556 tok/s here, decode ≈ 29–35 tok/s. Total time is dominated by decode (300–500 output tokens ≈ 10–17 s),
  not by images. A screenshot run costs about what a DOM run costs (single 280: 15 s vs DOM 18 s) and is cheaper than the
  text-only run (text windows are 1.4–2.9k tokens).
* 70/140 save 0.5–4 s and lose most of the content. 280 is the only budget worth shipping.

## 4. Error analysis

Examples are in `outputs/<page>__<mode>__b<budget>.txt`.

**Where screenshots work**
* **Image-only recipe**: 9/9 ingredients and 6/6 steps at every mode and budget 280, i.e. DOM capture has nothing here at all.
* **Short, readable recipes** (Joy of Baking 8 ingredients, Nigella 12, Argiro 13, Cooking for Engineers 10): 0.9–1.0 recall and
  exact quantities in `single 280`, with metric amounts copied exactly.
* **Layouts the DOM mangles**: Cooking for Engineers is one table with rotated step labels. The DOM pipeline dropped every metric amount
  (`1/2 cup chopped walnuts` where the page says `1/2 cup (70 g) chopped walnuts`, Q = 0.22 against the corrected truth); the screenshot run read all ten lines
  exactly (Q = 1.0) and its steps follow the table columns (`Mash`, `Melt`, `Whisk`, `Fold`).
* **Round-up with several recipes**: with five viewports from the recipe heading the model picked the right recipe (1.0 / 1.0).

**Where they fail**
* **Long ingredient lists** (30 lines on RecipeTin): `single` reads ~23 lines at 0.73 recall, but `multi` drops to 0.23 (7 lines, skipping from paprika to passata to rice) and `tall` to 0.33. More images do not mean more output: with 5 × 280 tokens the model summarises. Steps are also garbled: in `single` the steps repeat the ingredient lines (`1. 1/2 cup plain yoghurt…`).
* **Misread numbers** (OCR-sensitive; all at budget 140 except the first): infographic `¾ tsp salt → ¼ tsp salt` (also at 280 in `tall`), `1¼ cups → 1/4 cups`, RecipeTin `1 2/3 cups (400 ml) water → 1/2 cups`, `30 g / 2 tbsp butter → 7 tbsp`, `2 tsp lemon juice → 1 tsp`, Cooking for Engineers `2/3 cup sugar → 1 cup`, `2 eggs → 1 egg`, Joy `1/4 tsp salt → 1 tsp`. At 280 matched lines were exact in 86–100 % of cases (single 86 %, multi 100 %, tall 91 %); at 140 in 62–78 %. Unit symbols and fractions are the weak spot; a scaled recipe built on these would be wrong.
* **Missed lines and wrong view**: `single` = first viewport. BBC's `v1` shows 3 of 4 ingredients (sticky banner covers the first), recall 0.5; on the Spanish page it starts at the title: `NO RECIPE`. Guardian `single` starts on the article lead and the model **invented a recipe from the lead paragraph** (`Title: roasted on the street`, ingredients `nuts, salt, chocolate`; 0 / 0), the only clear hallucination, caused by the viewport containing no recipe.
* **Quantity/ingredient pairing lost** in two-column layouts: the Spanish page shows `1 ud. | Hojaldre lámina`; the model emitted `- 1 ud.` and `- Hojaldre lámina` as two ingredients (`Manzana` without its `3 uds.`).
* **Tall strips**: downsampling 1079×10 800 to 280 tokens makes text illegible; 3 of 9 refused, the rest are noisy (recall 0.59 at 280, 0.24 at 140). Do not send tall strips; send viewports.
* **Rule violations** that also occur in the text path: `Servings: Not specified`, invented `CookMinutes: 30`, `Cook Mode: Prevent screen from sleeping` as a field. The parser/prompt already tolerate these, but vision runs produce them more often.
* **Refusals**: `NO RECIPE` on 4 of 27 budget-280 runs and 11 of 60 overall, including `joy_pancakes multi 280` although `single 280` was perfect. Retry with fewer images is a plausible mitigation (not tested).

Text-only on the same rendered text is far more reliable (R 0.97) because it sees *all* of the page text exactly; DOM/structured is better still.

## 5. On-device feasibility (desk research + what was measured)

* **Prefill / decode.** Official LiteRT-LM Gemma 4 E2B numbers (1 024 prefill / 256 decode tokens, 4 CPU threads): S26 Ultra **CPU 557 prefill tok/s, 46.9 decode tok/s, TTFT 1.8 s**; GPU 3 808 / 52.1 / 0.3 s; M4 Max CPU 901 / 41.6 ([LiteRT-LM overview](https://developers.google.com/edge/litert-lm/overview), [model card](https://huggingface.co/huggingworld/gemma-4-E2B-it-litert-lm)). Our Mac measured 556 prefill and 29–35 decode, i.e. in the same range as a flagship phone CPU, so the §3 times are a fair first proxy. Estimate for a flagship phone: single screenshot ≈ 15–20 s, 3–5 screenshots 25–40 s, because the CPU vision encoder (~1–1.5 s/image) adds to prefill (1.7k tokens ≈ 3 s) and 300–500 decoded tokens ≈ 7–10 s. Debug builds run CPU (see `BuildConfig.LLM_BACKEND`); the vision backend is CPU-only in this bundle (`ModelInfo`), so release GPU does not speed up the image tower.
* **Memory.** The text decoder is ~0.8 GB resident, the 1.1 GB embeddings are memory-mapped, and "vision and audio models are loaded as needed" ([model card](https://huggingface.co/huggingworld/gemma-4-E2B-it-litert-lm)). Published peaks (S26 Ultra CPU 1 733 MB, GPU 676 MB) are text-only; the vision tower adds on top and is unquantised in some exports (≈ 3.8 GB file, [litert-torch #998 as cited by a community card](https://huggingface.co/TinmanLabSL/gemma4-companion-litert-lm)). No Android vision memory number exists in any source found; it must be measured. Mac peaks were not recorded.
* **Budget trade-off.** 70/140/280 only. 140 halves the cost of an image and loses ~40 % of recall and a lot of numeric fidelity; 70 is unusable for text. Practical choice: **280, max 3–5 images**.
* **Emulator `emulator-5556`: skipped on purpose.** A vision run needs `EngineConfig(visionBackend = CPU)`, `maxNumImages` and `Content.ImageBytes`, none of which `LiteRtLlmEngine`/`LlmRequest` expose (by design, "no image input"). Exercising it on the emulator means writing new Kotlin instrumentation that calls the LiteRT-LM API directly, outside this brief's allowed paths (`tools/eval/screenshots/`, `docs/research/`), and installing a test APK next to the app with its model. The emulator additionally runs the CPU path without SME (`arm64.nosme`), so absolute numbers would not transfer. It is the first item of the prototype below (spike on `emulator-5556`, manual `adb install -t` + `am instrument`, never `connectedDebugAndroidTest`). The real device `RFGYC3XSG6D` was attached but not used (outside the brief).
* **Crash risk.** The vision encoder uses different XNNPACK kernels than text; the SME `SIGILL` seen on M4 hosts for text needs a recheck with vision on the `nosme` AVD.

## 6. Product fit

| Screenshots win | Screenshots lose |
|---|---|
| Recipes shown **as images/infographics** (Instagram, TikTok, Pinterest, WhatsApp forwards) — DOM has nothing | **Speed**: same order as DOM today (15–40 s) but needs 3–5 shots for a normal page |
| **Shared screenshots** (`image/*` share target), photos of printed cookbook pages, PDFs rendered to images | **Quantity fidelity**: numbers misread in 0–14 % of matched lines at 280 and 22–38 % at 140 → wrong amounts in the library |
| Paywalled, canvas-rendered, app-only or login-gated views the WebView cannot read | **Long lists**: 30-ingredient recipes lose ~25–75 % of lines; multi-image attention dilutes |
| Table/rotated/multi-column layouts where the DOM text order is wrong (Cooking for Engineers) | **Wrong viewport = hallucination** (Guardian `single`), needs a review/confirm step |
| Privacy-friendly: nothing leaves the device, no site cooperation | **Cost**: ~0.9 s/image encode, no GPU path for vision, extra memory for the vision tower, large pictures for scroll capture |

DOM capture stays the primary path: it is exact (R 0.98, Q 0.87–1.0), cheaper, and works on 30-ingredient pages. Screenshots add a
**second input** for what the DOM cannot give.

## 7. Recommendation

1. **Ship a "Share a screenshot / pick pictures" entry point** for image-only recipes, **budget 280 (fixed), 1–3 images**, framed as
   *"Read from pictures — check the amounts"*. Do not offer it as a web-clip fallback for normal pages.
2. **Keep DOM/structured capture as the default** for the in-app browser. When the DOM path yields no recipe and the page has large
   recipe-like images, offer "Try reading the page as pictures" (user-triggered, never automatic).
3. **UX guardrails** required by the failure modes: a review screen (images in order, remove/reorder, cap 5 → recommend 3); a
   result screen that marks quantities as *read from an image* and highlights the numbers; refuse silently-empty results (an
   all-blank template or `NO RECIPE`) with one automatic retry using fewer images; never send tall strips; split long pages into
   viewports aligned to the recipe card.
4. **Do not promise scaling/shopping-list accuracy** from image recipes until quantities are user-confirmed.
5. Update the plan: the visual token budget default is **280** (not 560), the bundle exposes no higher; re-check if a different
   `.litertlm` export (one listing 560/1120 signatures) is adopted — then re-run `tools/eval/screenshots` (it takes ~40 min).

Gate for `recipekeeper-input-sources-plan.md`: **"feasible with limits"** — build I1/I2 for the image-only/shared-screenshot flow with
the guardrails above; do not use vision in the web-clipper path.

## 8. What a prototype needs

* `LlmRequest` gains `images: List<LlmImage>` (`ImageBytes` / file path, order significant) and `visualTokenBudget: Int?`;
  `LlmEngine.generateStream(request)` default behaviour unchanged (text only). `LlmRequestOptions` carries them from the use case.
* `LiteRtLlmEngine`: `EngineConfig(visionBackend = Backend.CPU(), maxNumImages = 5)` only for vision-enabled requests (separate
  engine instance or re-init, since the vision tower is memory), per-message `ExperimentalFlags.visualTokenBudget = 280`,
  `Contents.of(images…, Content.Text(user))`; keep one `Conversation` per call under the existing `Mutex`; `maxNumTokens` ≥ 4096.
  `ModelReadinessObserver` must also check that the selected model has vision (`ModelInfo`-style check or a catalog flag).
* Domain: `ClipInput.Images`, one synthetic `IMAGES` candidate, `origin = IMAGES` on the saved recipe (already in the plan).
* Prepare/review screen with downscale to ≤ 1080 px wide before encoding (the engine downsamples anyway; this saves memory and I/O).
* Share target: `ShareReceiverActivity` for `image/*` + `ACTION_SEND_MULTIPLE` (read grants via `ClipData`), photo picker entry.
* Eval: `EvalImagesParserTest` over `tools/eval/screenshots/outputs/*.txt` using the real `RecipeKeeperParser` (this doc used a Python port);
  device spike on `emulator-5556` first (init with vision on CPU, one 1080×1920 image at 280, TTFT/total/RSS, logcat for SIGILL/SIGSEGV), then one real phone.
* Prompt: add one line for images (`"The pictures are consecutive screenshots of one page; read only text that is visible; do not guess amounts you cannot read; omit unknown fields"`) and re-measure. Not tried here.

## 9. Reproduce

```bash
cd tools/eval/screenshots
npm i puppeteer-core            # in a scratch dir; set PUPPETEER_NODE_MODULES to it
./shoot_all.sh <shots> $PUPPETEER_NODE_MODULES bbc_pancakes rte_tikka nigella_banana joy_pancakes cfe_banana guardian_roundup es_hojaldre gr_afrato infographic_pancakes
python3 truth.py && python3 make_jobs.py <shots> jobs.json
~/.cache/llmlocal-eval/venv/bin/python run_vision.py --model ~/.cache/llmlocal-eval/gemma-4-E2B-it.litertlm --jobs jobs.json --out outputs
node dump_text.mjs <text>       # text-only baseline input, then ../run_local_model.py on SYSTEM + user
python3 score.py outputs baselines > results.md && python3 aggregate.py
```
`pip install pillow` is only needed for contact sheets. Files: `shoot.mjs`, `dump_text.mjs`, `run_vision.py`, `score.py`, `aggregate.py`,
`truth.py` + `truth_manual.json` + `truth.json`, `pages.json`, `system_prompt.txt`, `infographic/`, `outputs/` (60 runs), `baselines/` (DOM + text-only).
