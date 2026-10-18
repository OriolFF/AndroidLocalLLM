# WS7 report — local evaluation harness (standard pipeline vs the real model)

Branch `recipekeeper/ws7`, worktree `~/.herdr/worktrees/AndroidLocalLLM/recipekeeper-ws7`, base `ff675fe` (recipekeeper/main with WS0–WS3, WS5a). No production code was changed. The only Gradle change is the `androidTest` setup of `:app:recipekeeper`.

**Headline:** path (a) works end to end on this Mac — the `litert-lm` 0.18.0 Python wheel loads the same `gemma-4-E2B-it.litertlm` bundle the app downloads (sha256 `181938105e0e…`), so all 10 fixtures have committed goldens and timings, `EvalParserTest` is green in CI without the model, and the report answers decision #3 (recommendation: **skip the model for STRUCTURED candidates whose deterministic mapping is already complete; run it only when the mapping is thin, and always for TEXT / whole-page**). The run also surfaced two production bugs and three pipeline gaps, listed below with patches.

## Files

```
tools/eval/README.md                       how to run the loop, what the numbers mean
tools/eval/fixtures.tsv                    fixture → URL, fetch date, whether HTML is committed
tools/eval/html/*.html                     5 sources ≤ 300 KB (joyofbaking, cfe, marmiton, pinchofyum list, nigella)
tools/eval/capture_fixtures.sh             re-fetch + re-capture with WS2's jsdom harness (jsdom lives in ~/.cache/llmlocal-eval/jsdom)
tools/eval/run_local_model.sh              runner: (a) desktop LiteRT-LM wheel, (b) --device adb/instrumented
tools/eval/run_local_model.py              the desktop runner (venv at ~/.cache/llmlocal-eval/venv, model at ~/.cache/llmlocal-eval/)
app/recipekeeper/src/test/resources/eval/pages/<fixture>.json        10 PageSnapshot captures (+ _meta)
app/recipekeeper/src/test/resources/eval/outputs/<fixture>.txt       10 goldens (raw model output)
app/recipekeeper/src/test/resources/eval/outputs/<fixture>.timing.json   TTFT / total / prefill+decode tokens & tok/s / sampler / model sha
app/recipekeeper/src/test/kotlin/com/llmlocal/recipekeeper/eval/EvalFixtures.kt            registry, expectations, loader, prompt assembly
app/recipekeeper/src/test/kotlin/com/llmlocal/recipekeeper/eval/EvalPromptRenderingTest.kt  step 2 (+ EvalFixtureRegistryTest)
app/recipekeeper/src/test/kotlin/com/llmlocal/recipekeeper/eval/EvalParserTest.kt           step 4 + EvalReport (step 5)
app/recipekeeper/src/androidTest/kotlin/com/llmlocal/recipekeeper/eval/EvalRunnerTest.kt    path (b) runner
app/recipekeeper/build.gradle.kts          + testInstrumentationRunner, androidTestImplementation(ext-junit, runner 1.7.0, litert-lm)
docs/plans/briefs/ws7-eval-report.md       committed copy of build/eval/report.md + recommendation
```

## What was built, step by step

1. **Fixtures (10).** Fetched with `curl -L -A "Mozilla/5.0 …"` on 2026-10-18, captured with `core/web/tools/capture-harness/harness.mjs` + jsdom 26.1.0 (installed in a scratch dir, now documented at `~/.cache/llmlocal-eval/jsdom`). Stored as the harness' `--json` output plus a `_meta` block. Coverage: JSON-LD single (`single_bbcgoodfood_pancakes`), `@graph` (`graph_pinchofyum_cookies`), round-up with 5 Recipe nodes (`roundup_guardian_slater`), HowToSection (`howto_recipetineats_tikka`), two plain-text pages without structured data (`plain_joyofbaking_pancakes`, `plain_cfe_banana_bread`), non-English (`nonen_marmiton_crepes`, French), list page with no recipe (`list_pinchofyum_recipes`), very long page (`long_theperfectloaf_sourdough`, 31k readable chars) and a microdata page (`micro_nigella_banana_bread`, 23k chars). Dotdash/Meredith (Allrecipes, Simply Recipes, Serious Eats), Food Network, Cookie+Kate, Budget Bytes, Tasty, Chefkoch answer 403/406 to curl and could not be used. No JSON-LD round-up outside The Guardian was reachable; multi-recipe JSON-LD is rare (Google's guidelines discourage it).
2. **`EvalPromptRenderingTest`** (parameterized, 20 cases + 2 registry guards): loads the snapshot through a copy of `PageSnapshotMapper`'s structural mapping (the public `JsonLdExtractor` does the JSON-LD/microdata part, so Tier A sees exactly what production sees), asserts tier + candidate-count bounds, rendered length ≤ `budgetChars` (9,000), `Page:` header, and writes `build/eval/prompts/<fixture>.txt` (system + `\n\n` + user, as `LlmTaskRepositoryImpl` concatenates it) plus a `.meta.json`. Rendered inputs: 1.4–3.2k chars for STRUCTURED, 1.8k / 6.1k for the two TEXT pages, 2.3k for the whole-page negative; nothing hit the budget.
3. **Runner.** `tools/eval/run_local_model.sh` → `run_local_model.py`: one `Engine` (CPU, `max_num_tokens 4096`, `enable_benchmark`), a fresh conversation per prompt, single user turn, `top_k 40 / top_p 0.95 / seed 0` (engine defaults), `temperature 0.2`, `max_output_tokens 1024` (plan §3.4). Writes the golden and a timing JSON with wall-clock TTFT/total and LiteRT-LM's `BenchmarkInfo`. `--dry-run`, `--only`, `--help` work without the wheel. Path (b): `--device` pushes prompts to `/data/local/tmp/eval/prompts`, installs app + test APK, runs `EvalRunnerTest` (Koin's real engine, or a CPU `LiteRtLlmEngine` around `-e modelPath`), pulls `files/eval/outputs` via `run-as` + tar. Compiled (`assembleDebugAndroidTest` → `recipekeeper-debug-androidTest.apk`), **not executed**: the only device is `emulator-5554` (170 MB free, no model).
4. **`EvalParserTest`** (parameterized, 20 cases): parses every golden with `RecipeKeeperParser`, checks title / ≥ 3 ingredients / ≥ 2 steps / ingredient count ±20 % of JSON-LD / servings & time when the source has them / no unexpected `Label:` or Markdown headers / Jaccard ≥ 0.8 on normalised ingredient tokens vs `SchemaOrgRecipeMapper`, and walks **every character prefix** through `RecipeKeeperDraftParser` (lists only grow, items only extend, title stable, final draft == final parse). Fixtures without a golden skip via `Assume` with an explicit "ASSUMPTION: no golden …" message. Negative fixtures (`expectRecipe = false`) are reported, not asserted. Known production defects are declared per fixture in `EvalFixture.knownIssues` and reported as `KNOWN FAIL`; if such a check unexpectedly passes the test fails, so the allowance is removed when the bug is fixed.
5. **`EvalReport`** writes `build/eval/report.md` from `@AfterClass`: per-fixture table, per-tier summary, check details, and a "model vs `fallback()`" table with Δ flags for the STRUCTURED fixtures. Committed copy with the recommendation: `docs/plans/briefs/ws7-eval-report.md`.
6. **Loop documented** in `tools/eval/README.md`.

## Results (Mac, Apple-silicon CPU; see the eval report for the full tables)

| Tier | Fixtures | Parse ok | All checks ok | Mean prompt chars | Mean TTFT | Mean total | Decode tok/s | Mean Jaccard |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| STRUCTURED | 7 | 7 | 6 (+1 known: parser bug) | 2870 | 1.2 s | 19.6 s | ~30 | 0.97 (1.00 on 6/7) |
| TEXT | 2 | 2 | 1 (+1 known: renderer gap) | 4729 | 2.0 s | 13.1 s | ~30 | – |
| WHOLE_PAGE (negative) | 1 | "parsed" | – | 3045 | 1.2 s | 3.4 s | ~30 | – |

- Prefill 650–780 tok/s, decode 30 tok/s on this Mac; time is dominated by output length (360–940 tokens → 11–32 s). The research notes put a flagship phone CPU at 557 / 47 tok/s and a mid-range one at 150–300 / 10–20 tok/s, i.e. **the same prompts take roughly 1–2× (flagship) to 2–3× (mid-range) these totals on device**. No output hit the 1,024-token cap; the longest (30-ingredient tikka masala) used 938.
- The model's format discipline is good: every golden starts with `Title:`, no Markdown, no unknown headers; it emits empty optional lines (`Servings:` with no value) instead of omitting them, which the grammar tolerates.
- Draft parsing was monotonic over all 23k prefixes.

## Decision #3 — should STRUCTURED candidates skip the model?

Model vs deterministic mapper on the 7 STRUCTURED fixtures (counts: ingredients / steps / servings / total min):

| Fixture | Mapper (`fallback()`) | Model | Verdict |
|---|---|---|---|
| single_bbcgoodfood_pancakes | 4 / 4 / Makes 8 pancakes / 30 | 4 / 4 / Makes 8 pancakes / **60** | model **fabricated** a prep 30 + cook 30 split from "total 30 min" (prompt has no `TotalMinutes:` slot) |
| graph_pinchofyum_cookies | 9 / 5 / 12 cookies / 20 | 9 / 5 / 12 cookies / 20 | identical; Notes duplicate step sentences |
| roundup_guardian_slater | 9 / 8 / Makes 10.0 items / – | 9 / 8 / same / – | identical text; parser decimal bug corrupts both paths equally (see bugs) |
| howto_recipetineats_tikka | 30 / 16 / 4 - 5 people / 60 | 30 / **15** / same / 60 | one HowToSection step merged; Notes partly **invented** from "(Note 2)" references |
| nonen_marmiton_crepes | 7 / 5 / 15 crêpes / 40 | 7 / 5 / same / 40 | identical |
| long_theperfectloaf_sourdough | 9 / 8 / 2 loaves / 1440 | 9 / 8 / same / 1440 | identical |
| micro_nigella_banana_bread | 12 / **1** / Yields: 8-10 slices / – | 12 / **11** / 8-10 slices / 1 | model **adds value**: splits the lumped microdata instructions, strips "Yields:"; cook "1-1¼ hours" → parser bug → 1 min |

So on well-formed JSON-LD the model reproduces the mapper verbatim (6/7 Jaccard 1.00) at a cost of 11–32 s on a Mac (longer on a phone), and when it deviates it is as often a regression (fabricated prep/cook, dropped step, invented notes) as an improvement (the one page whose structured instructions were a single blob).

**Recommendation: flip to "skip", conditionally.** In `ExtractionViewModel`, for a `CandidateSource.STRUCTURED` candidate compute `config.fallback(candidate, snapshot)` first and **save it directly when it is complete** — `ingredients.size >= 3 && steps.size >= 2` (the same bar the eval uses) — showing the result instantly with an optional "Tidy up with AI" action that runs the model on demand. Run the model up front only when the mapping is thin (steps ≤ 1 as on Nigella's microdata, or no ingredients), and always for TEXT / whole-page candidates. Expected effect: the large majority of clips (every JSON-LD recipe site tested) become instant and deterministic; the model is spent where it demonstrably helps. Revisit once path (b) numbers exist for the target devices; the ordering of the conclusion does not depend on absolute speed.

## Production bugs found (not fixed here — outside WS7's files; patches proposed)

1. **`RecipeLineGrammar` strips the integer part of decimal quantities.** `addListItem` → `stripNumber` (and `numbered` for un-bulleted lines) treat `250.0g potatoes` / `2.5 cups flour` / `1.5 tsp salt` as a numbered item `250.` + text, yielding `0g potatoes`, `5 cups flour`, `5 tsp salt`. Hits the model path *and* any ingredient written with a decimal. Patch in `app/recipekeeper/src/main/kotlin/com/llmlocal/recipekeeper/parser/RecipeLineGrammar.kt`:
   ```kotlin
   // before: "^\\s*(?:Step\\s*)?\\d{1,3}\\s*[.):]\\s*(.*?)\\s*$"
   private val numbered = Regex("^\\s*(?:Step\\s*)?\\d{1,3}\\s*(?:[):]|\\.(?!\\d))\\s*(.*?)\\s*$", RegexOption.IGNORE_CASE)
   ```
   (a dot followed by a digit is a decimal, not a list marker). Add to `RecipeKeeperParserTest`: `- 2.5 cups flour` and `- 250.0g potatoes` stay intact; `2. Preheat…` and `2.Preheat…` still parse as step text. Visible in the eval as the Guardian fixture's Jaccard 0.77 (`knownIssues`).
2. **`RecipeLineGrammar.parseMinutes` mis-parses unicode fractions and ranges with them.** `1-1¼ hours` → 1 (should be 75). Patch: normalise vulgar fractions before matching (`¼→.25 ½→.5 ¾→.75 ⅓→.33 ⅔→.67`, also `1 1/4` → `1.25`), and take the max of a range of hour values. Add `"1-1¼ hours" → 75`, `"1½ h" → 90` to the minutes table test. Visible as `micro_nigella_banana_bread` cook = 1 min in the report (not asserted — the source has no time field).

## Pipeline gaps found (behaviour, not crashes)

3. **TEXT candidate whose method is in the *preceding* section gets no steps.** `renderSection` only appends sections after the candidate; on joyofbaking.com the ingredients h2 follows the h1 that holds the method prose, so the model saw only ingredients and emitted an empty `Steps:` (`plain_joyofbaking_pancakes`, `knownIssues`). Proposal for `RecipeKeeperClipConfig.renderSection`: if neither the candidate nor the pulled-in sections contain an instructions word, also prepend the nearest preceding section with ≥ 300 chars of prose (or fall back to `readable.textContent` within budget).
4. **Whole-page input on a non-recipe page makes the model invent a recipe** (`list_pinchofyum_recipes` → "Best Anytime Baked Chicken Meatballs", 6 generic ingredients, 1 step). The prompt has no refusal path. Proposal: add to `RecipeKeeperPromptBuilder.SYSTEM` "If the page contains no recipe, respond with exactly: `NO RECIPE`" — the grammar already returns `null` for that text, which lands in `ExtractionError.ParseFailed` → "Extract anyway" UX; and/or require ≥ 2 quantity lines in the parsed ingredients before accepting a whole-page result.
5. **No `TotalMinutes:` slot in the prompt** → the model splits a page's total time into equal prep/cook halves (bbcgoodfood). The grammar already accepts `TotalMinutes:`; add the optional line to the prompt format and have the parser prefer an explicit total over `prep + cook`.
6. Minor: `Notes:` often repeats step sentences or expands footnote markers into invented notes (pinchofyum, tikka); consider "Notes: only text that is not already a step". The Guardian's float quantities (`250.0g`, `Makes 10.0 items`) reach the UI verbatim via both paths — a `SchemaOrgRecipeMapper`/`TextCleaner` normalisation (`(\d+)\.0(?=\D)` → `$1`) would tidy them.

## Tests and builds run

- `./gradlew :app:recipekeeper:testDebugUnitTest --tests '*Eval*'` — **42 tests, 0 failures, 0 skipped** (EvalPromptRenderingTest 20, EvalFixtureRegistryTest 2, EvalParserTest 20; two checks reported as KNOWN FAIL, see above).
- `./gradlew :app:recipekeeper:testDebugUnitTest` — all WS5a tests still green alongside the eval tests.
- `./gradlew :app:recipekeeper:assembleDebugAndroidTest` — **BUILD SUCCESSFUL** (`EvalRunnerTest` compiles against `androidx.test.ext:junit 1.3.0`, `androidx.test:runner 1.7.0`, `litert-lm 0.18.0`).
- `./gradlew :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug` — **BUILD SUCCESSFUL**.
- `tools/eval/run_local_model.sh --dry-run` and the full run: 10/10 prompts ok, 3 min wall-clock including venv creation; `--help` lists both the shell and Python options.
- Module boundary: no new project dependency (only external `androidTestImplementation` artifacts).

## For the lead at merge

- **Nothing to swap.** No shims, no shared-contract change. `app/recipekeeper/build.gradle.kts` gained `testInstrumentationRunner` + three `androidTestImplementation` lines; `androidx.test:runner:1.7.0` is a string coordinate because the catalog has no entry (add `androidx-test-runner` if you prefer).
- The goldens are tied to model sha256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` and `litert-lm 0.18.0`; regenerate with the loop in `tools/eval/README.md` when prompts or the model change.
- `~/.cache/llmlocal-eval/` on this Mac now holds the 2.59 GB model, the Python venv and (after the first `capture_fixtures.sh`) jsdom; delete it to reclaim ~3 GB.
- Bugs 1–2 belong to WS5a's parser; gaps 3–5 to WS5a's clip config / prompt builder and WS4's `ExtractionViewModel`. Decision #3's conditional-skip branch is WS4/WS6 work.
- Path (b) remains unexecuted until an arm64 phone with the model is available: `tools/eval/run_local_model.sh --device` then re-run `EvalParserTest` and refresh `ws7-eval-report.md` with device timings.
