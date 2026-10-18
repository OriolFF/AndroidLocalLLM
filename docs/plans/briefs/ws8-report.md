# WS8 report — eval follow-ups (parser bugs, pipeline gaps, conditional-skip switch)

Branch `recipekeeper/ws8`, worktree `~/.herdr/worktrees/AndroidLocalLLM/recipekeeper-ws8`, base `78421de` (recipekeeper/main with WS0–WS5b, WS7). One commit per brief step; the final verify command is green (see the bottom).

**Headline:** every bug and gap the WS7 eval surfaced is fixed and the fixes are visible in the regenerated goldens — the Guardian round-up now parses `250g potatoes` (Jaccard 0.77 → 1.00), joyofbaking gets its method (0 → 11 steps), the list page answers `NO RECIPE`, bbcgoodfood keeps the page's total of 30 min instead of a fabricated 30 + 30 split. Both `knownIssues` allowances are gone; **10/10 fixtures pass every check** (`EvalParserTest` 20/20). Plan decision #3's conditional-skip branch is implemented behind `WebClipConfig.skipModelWhenStructuredComplete`, **still `false`** in `RecipeKeeperClipConfig`; flipping it is a one-line change. One thing worth knowing: the first prompt revision (TotalMinutes slot + refusal rule alone) *regressed* three fixtures at the fixed seed; a second revision (notes capped at three, "write times as whole minutes") recovered all of them, and that is the prompt that shipped — details under step 8.

## Per step

### 1. Parser bug 1 — decimals (`5f7f46c`)

- `app/recipekeeper/src/main/kotlin/com/llmlocal/recipekeeper/parser/RecipeLineGrammar.kt`: `numbered` is now `^\s*(?:Step\s*)?\d{1,3}\s*(?:[):]|\.(?!\d))\s*(.*?)\s*$` — a dot directly followed by a digit is a decimal point. `250.0g potatoes`, `2.5 cups flour`, `1.5 tsp salt`, `6.0tbsp olive oil` survive; `2. Preheat…`, `2.Preheat…`, `3) Fry 2.5 minutes` still parse as steps.
- Tests: `RecipeKeeperParserTest.decimal quantities keep their integer part…`, `RecipeKeeperDraftParserTest.decimal quantities are promoted intact…`.

### 2. Parser bug 2 — unicode fractions / hour ranges (`9d3c035`)

- `RecipeLineGrammar.parseMinutes`: after the ISO-8601 branch, `normaliseFractions` rewrites `1¼`/`1 ½` → `1.25`/`1.5`, `½` → `0.5`, `1 1/4` → `1.25`, `1/2` → `0.5`; the hour and minute regexes then run with `findAll` and a range keeps the **largest** value (`1-1¼ hours` → 75, `1 - 1 1/2 hours` → 90, `20-25 min` → 25 as before). Bare numbers also accept decimals (`1½` → 2).
- Test: the `minutes parsing table` gained 11 rows (`1-1¼ hours` → 75, `1½ h` → 90, `½ hour` → 30, `2⅓ hours` → 140, `1,5 h` → 90, …).

### 3. Gap 5 — `TotalMinutes:` slot (`842dc0b`)

- `RecipeKeeperPromptBuilder.SYSTEM`: `TotalMinutes: <integer minutes, optional>` after `CookMinutes:`, plus the rule "Copy the page's total time into TotalMinutes; if the page gives only a total, do not split it into PrepMinutes and CookMinutes."
- `RecipeKeeperParser` already preferred `parsed.totalMinutes` over `prep + cook`; the precedence is now pinned by `explicit TotalMinutes wins over prep plus cook, and alone it is kept` (explicit 45 beats 10 + 20; `TotalMinutes:` with no value falls back to the sum).
- `renderLlmInput` for STRUCTURED already rendered `Times: total 30 min` when only `totalTime` exists (bbcgoodfood); no renderer change was needed. Result in the goldens: bbcgoodfood `–/–/30` (was `30/30/60`).

### 4. Gap 4 — refusal path (`2209a7f`)

- Prompt: "If the page contains no recipe, respond with exactly: NO RECIPE". `RecipeKeeperParser.parse` returns `null` when the first non-blank line is that refusal (any case, optional Markdown decoration or full stop; `NO_RECIPE_TEXT` constant), even if a format follows. `null` → `LlmTaskParseException` → `ExtractionError.ParseFailed(rawOutput)` → the existing "Try again / Back to page / Try the whole page" UX (the screen already hides "Try the whole page" when the candidate *is* the whole page).
- **New hook `WebClipConfig.accept(candidate, item): Boolean = true`** (`feature/webclipper/.../WebClipConfig.kt`). `ExtractionViewModel` calls it on every `Complete` result; a rejection is treated exactly like a parse failure (structured fallback, else `ParseFailed` with the raw text). `RecipeKeeperClipConfig.accept` requires `≥ MIN_WHOLE_PAGE_QUANTITY_LINES (2)` ingredients matching the existing `quantityLine` regex for `CandidatePayload.WholePage`; detected candidates are always accepted.
- Tests: `RecipeKeeperParserTest.NO RECIPE refusal returns null…`, `RecipeKeeperClipConfigTest.accept requires two quantity ingredients…`, `ExtractionViewModelTest.a parsed result the config rejects is treated as a parse failure` (TEXT → `ParseFailed`, STRUCTURED → fallback saved). `FakeWebClipConfig` gained `rejected` / `acceptCalls`.
- `EvalParserTest` applies `clipConfig.accept` like the ViewModel does, and the negative fixture's status distinguishes "answered NO RECIPE" / "rejected by accept" / "invented a recipe".

### 5. Gap 3 — method in the preceding section (`15b818c`)

- `RecipeKeeperClipConfig.renderSection(section, snapshot, budget)`: after collecting the candidate and its following sections as before, if that text contains no `instructionsWord`, `precedingProse` walks backwards to the nearest section whose **non-quantity lines** add up to ≥ `MIN_PRECEDING_PROSE_CHARS` (300), stopping at another recipe's ingredients heading, and prepends it. `trimHeadAtParagraph` keeps the *end* of that prose within `budget − body − 2` (first blank line / newline / space in the first 40 % of the kept window, `[…]` marker, a `## heading` first line survives), so the candidate itself is never cut. `renderLlmInput` now computes `available` before rendering and passes it down; the final `truncateAtParagraph` still applies.
- Effect on `plain_joyofbaking_pancakes`: rendered input 2,598 → 7,053 chars (budget 9,000), `Steps` 0 → 11.
- Tests (synthetic joyofbaking-like snapshot: h1 intro + method prose, empty h2, `Pancake Recipe:` h2 with quantity lines): prose precedes the candidate under the full budget; with `budgetChars = 1_200` the candidate stays whole and the prose is cut from its start (`[…]` marker, intro gone, "Serve immediately…" kept); nothing is prepended when a following `Method` section exists or the budget is tiny; `trimHeadAtParagraph` unit table.

### 6. Gap 6 — tidy-ups (`24a1bd3`)

- `RecipeLineGrammar`: a `Notes:` line (bullet/number stripped, case and punctuation ignored, ≥ 3 words) that is contained in a step already parsed is dropped — both for lines under `Notes:` and for the inline `Notes: …` value. Monotonic for the draft parser because steps precede notes in the grammar. Visible in the goldens: bbcgoodfood's single note ("If preparing in advance, cook and stack the pancakes.") and Nigella's third note are step echoes and vanish; pinchofyum keeps its three paraphrased notes.
- `TextCleaner.tidyNumbers`: `(\d+)\.0(?!\d)` → `$1`; `SchemaOrgRecipeMapper.ingredients` and `yieldText` apply it, so both the fallback and the rendered LLM input read `250g potatoes` / `Makes 10 items` (`6.75`, `0.05`, `1.50` untouched).
- Tests: `notes that repeat a step sentence are dropped, genuine notes stay`, `SchemaOrgRecipeMapperTest.float quantities and yields are tidied…`.

### 7. Conditional-skip switch — decision #3, NOT flipped (`ac795bb`)

- `WebClipConfig`: `val skipModelWhenStructuredComplete: Boolean get() = false` and `fun isComplete(item: T): Boolean = true`.
- `ExtractionViewModel.run`: `directResult()` — when the switch is on and the candidate is STRUCTURED, `config.fallback` is computed first; if `isComplete` accepts it, Reading and Extracting are marked `Done` at once, `ExtractionState.extractedWithoutModel = true`, pictures download, `finish()` saves and navigates. The engine is never initialised or called, `renderLlmInput` is not run. Thin/missing mappings and TEXT candidates take the model path unchanged. The shared tail (pictures → saving → navigate) is now `finish(candidate, snapshot, result, pictures, truncated)`.
- `WebClipperLabels.extractedWithoutModel = "Read directly from the page's recipe data"` is rendered as the Extracting phase detail (`WebClipperExtractionScreen.PhaseList`) when `state.extractedWithoutModel`.
- `RecipeKeeperClipConfig`: `override val skipModelWhenStructuredComplete = false`, `isComplete = ingredients.size >= 3 && steps.size >= 2` (`MIN_COMPLETE_INGREDIENTS` / `MIN_COMPLETE_STEPS`, the eval's bar). **Flipping decision #3 is changing that one `false` to `true`.** On the eval set that would make 6/7 STRUCTURED fixtures instant; Nigella's microdata (1 lumped step) would still go through the model.
- Tests: `switch off - a complete structured mapping still goes through the model`, `switch on - a complete structured mapping is saved without running the model` (no engine request, no render, all phases Done, picture downloaded and saved, `extractedWithoutModel`), `switch on - an incomplete or missing mapping and TEXT candidates still run the model`; `RecipeKeeperClipConfigTest.isComplete needs three ingredients and two steps, and the skip switch is off`.

### 8. Eval — goldens regenerated (`4dbc512`)

Loop: `./gradlew :app:recipekeeper:testDebugUnitTest --tests '*EvalPromptRenderingTest*' && tools/eval/run_local_model.sh && ./gradlew :app:recipekeeper:testDebugUnitTest --tests '*EvalParserTest*'` (the runner script first had to be fixed — see "files outside the list"). Same model (sha256 `181938105e0e…`), same sampler (`seed 0`, `temperature 0.2`, `top_k 40`, `top_p 0.95`, `max_output_tokens 1024`), Mac CPU.

**Three prompt iterations were needed; the fixed seed makes the 2B model sensitive to wording.**

| Prompt | What it added | Result on the 10 fixtures |
|---|---|---|
| A — brief steps 3+4 only (`TotalMinutes:` slot, "do not split a total", `NO RECIPE`) | — | list → `NO RECIPE` ✓, bbcgoodfood → `–/–/30` ✓, joyofbaking 11 steps ✓, Guardian Jaccard 1.00 ✓ — **but** Nigella stopped after `CookMinutes: 1 / TotalMinutes: 1` (397 chars, parse `null` → `EvalParserTest` FAIL), tikka looped on `Note 5: Note 5: …` until the 1,024-token cap, cfe fell from 12 to 6 steps ("Fold bake") |
| B — A + `Notes:` "up to three short tips … not already steps", "Write times as whole minutes (1½ hours = 90)" | notes cap, minute rule | all 10 parse, 20/20 checks, no cap hit, Nigella 11 steps cook 75 ✓, tikka 16 steps = mapper ✓ — **but** the list page was no longer refused (invented recipe, caught by `accept`: 0 quantity ingredients), bbcgoodfood split again (`15/15/30`, total right), Guardian invented `20/10/30` |
| **C (shipped)** — B with "only the prep, cook or total time the page states — never derive them from step durations; if the page gives only a total, fill TotalMinutes alone and omit PrepMinutes and CookMinutes" and "If the page is a list, search or category page, or otherwise contains no recipe, respond with exactly: NO RECIPE" | stricter time rule, refusal names list pages | **20/20 checks, no cap hit**, list → `NO RECIPE`, bbcgoodfood `–/–/30`, tikka 16 steps, joyofbaking 11 steps, Guardian 1.00. Residual: Guardian `15/10/25` and Nigella total `180` are model-derived times on pages whose structured data has no time field (not asserted; the mapper path has no times either), joyofbaking cook `3` from "2-3 minutes" |

The goldens of A and B are kept for reference in the scratchpad only; the committed goldens + timings are C. Per-fixture before (WS7) / after (WS8-C):

| Fixture | WS7 status | WS8 status | Steps | Prep/Cook/Total | Jaccard | Total ms |
|---|---|---|---|---|---|---|
| single_bbcgoodfood_pancakes | OK (fabricated 30/30/60) | OK | 4 → 4 | 30/30/60 → **–/–/30** | 1.00 | 12.7 → 15.4 s |
| graph_pinchofyum_cookies | OK | OK | 5 → 5 | 10/10/20 | 1.00 | 21.5 → 20.5 s |
| roundup_guardian_slater | **KNOWN FAIL** (Jaccard) | **OK** | 8 → 8 | –/–/– → 15/10/25 (invented) | **0.77 → 1.00** | 15.5 → 16.7 s |
| howto_recipetineats_tikka | OK (15 steps, 6 invented notes) | OK | 15 → **16 (= mapper)** | 15/45/60 | 1.00 | 32.4 → 31.7 s |
| plain_joyofbaking_pancakes | **KNOWN FAIL** (0 steps) | **OK** | 0 → **11** | –/–/– → 15/3/18 | – | 5.4 → 20.0 s |
| plain_cfe_banana_bread | OK | OK | 12 → 11 | –/55/55 → 15/55/70 | – | 20.8 → 15.1 s |
| nonen_marmiton_crepes | OK | OK | 5 → 5 | 10/30/40 | 1.00 | 11.1 → 14.6 s |
| list_pinchofyum_recipes (negative) | INFO: invented recipe | **INFO: `NO RECIPE`** | 1 → – | – | – | 3.4 → 1.5 s |
| long_theperfectloaf_sourdough | OK | OK | 8 → 8 | 1380/60/1440 | 1.00 | 24.3 → 22.4 s |
| micro_nigella_banana_bread | OK (cook parsed as 1 min) | OK | 11 → 9 | –/1/1 → –/–/180 (derived) | 1.00 | 20.0 → 20.0 s |

Per tier (WS7 → WS8): STRUCTURED all-checks-ok 6/7 → **7/7**, mean total 19.6 → 20.2 s, mean Jaccard 0.97 → **1.00**; TEXT 1/2 → **2/2**, mean prompt 4.7k → 7.3k chars, mean total 13.1 → 17.6 s; decode ~30 tok/s throughout. Both `knownIssues` entries were removed from `EvalFixtures.kt` (the harness fails on an unexpectedly passing known issue, which is how the Guardian one announced itself after step 1). `docs/plans/briefs/ws7-eval-report.md` is refreshed with the new generated report plus a "what changed since WS7" table.

**Regressions to be aware of (reported, checks not loosened):** the model now fills time slots it derives from step text on pages without stated times (Guardian, Nigella's 180, joyofbaking's cook 3). The eval cannot assert "no invented time" without also flagging legitimate cases like Nigella's "bake for 1-1¼ hours", so this stays a prose note; with decision #3 flipped these STRUCTURED pages would not reach the model at all.

### 9. Verify

```
./gradlew :app:recipekeeper:testDebugUnitTest :feature:webclipper:testDebugUnitTest \
  :app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug lint
```

**BUILD SUCCESSFUL** on the final tree (prompt C, regenerated goldens). 154 JVM tests (`:app:recipekeeper` 121, `:feature:webclipper` 33), 0 failures, 0 skipped — the eval classes among them: `EvalPromptRenderingTest` 20, `EvalFixtureRegistryTest` 2, `EvalParserTest` 20. 15 debug APKs (5 per app). Lint: warnings only, none in files WS8 touched (`MissingOnRenderProcessGone` in `WebViewHolder.kt`, `ModifierParameter` in `ClipDraftCard.kt`, `ObsoleteSdkInt`/`MonochromeLauncherIcon`/`UseTomlInstead` in the recipekeeper app module — all pre-existing). Module boundaries unchanged: the only new cross-module surface is the three `WebClipConfig` default members (`accept`, `skipModelWhenStructuredComplete`, `isComplete`), all with defaults, so `FakeWebClipConfig` and any other implementor compile without changes.

## Files outside the brief's list that I had to touch

- `feature/webclipper/src/main/kotlin/com/llmlocal/feature/webclipper/WebClipperLabels.kt` — the new `extractedWithoutModel` label the brief asks for lives here.
- `feature/webclipper/src/main/kotlin/com/llmlocal/feature/webclipper/mvi/ExtractionState.kt` — `extractedWithoutModel: Boolean = false` (the only way the screen can know the model was skipped).
- `feature/webclipper/src/main/kotlin/com/llmlocal/feature/webclipper/ui/WebClipperExtractionScreen.kt` — one `when` branch rendering that label as the Extracting phase detail.
- `tools/eval/run_local_model.sh` — `"${py_args[@]}"` / `"${extra[@]}"` trip `set -u` on macOS bash 3.2 when the array is empty, so the documented no-argument run (`tools/eval/run_local_model.sh`) failed with `py_args[@]: unbound variable`; both are now `${arr[@]+"${arr[@]}"}`.
- `app/recipekeeper/src/test/kotlin/com/llmlocal/recipekeeper/clip/SchemaOrgRecipeMapperTest.kt`, `RecipeKeeperClipConfigTest.kt`, `parser/*Test.kt` are inside `app/recipekeeper/src/test/**`, i.e. allowed.

## Not done / for the lead

- The switch stays off (brief). Flip = `RecipeKeeperClipConfig.skipModelWhenStructuredComplete = true`. The UX spec's "Tidy up with AI" on-demand action from the WS7 recommendation is not built; with the switch on the user gets the mapper result and no way to run the model on that page except re-clipping with the switch off.
- `RecipeDraft` (streaming skeleton) still has no `totalMinutes`; the draft card shows prep/cook only. Trivial to add if the card should display the total while streaming.
- Path (b) (device run) is still unexecuted; all timings below are Mac numbers.
