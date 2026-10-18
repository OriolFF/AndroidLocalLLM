# Recipe Keeper eval harness (WS7)

Measures how the **standard pipeline** — `RecipeKeeperClipConfig.detect` →
`renderLlmInput` → `RecipeKeeperPromptBuilder` → **Gemma 4 E2B (`.litertlm`)** →
`RecipeKeeperParser` — performs on captured real pages, with the **same model
file the app downloads**, and turns the results into repeatable JVM tests plus
a Markdown report (plan decision A16; the data behind decision #3, "should
STRUCTURED pages skip the model?").

Nothing here touches production code. The only Gradle change is the
`androidTest` configuration of `:app:recipekeeper`.

## Layout

```
tools/eval/
├── README.md                 this file
├── fixtures.tsv              fixture name → source URL, fetch date, whether the HTML is committed
├── html/<fixture>.html       raw HTML of the fixtures ≤ 300 KB (the others are JSON-only; see fixtures.tsv)
├── capture_fixtures.sh       re-fetch + re-capture fixtures with WS2's capture.js in jsdom
├── run_local_model.sh        (a) desktop LiteRT-LM runner / (b) --device adb runner
└── run_local_model.py        the desktop runner (litert-lm Python wheel 0.18.0)

app/recipekeeper/src/test/resources/eval/
├── pages/<fixture>.json      PageSnapshot captures = harness.mjs --json output + _meta (URL, date)
└── outputs/<fixture>.txt     goldens: raw model output
    outputs/<fixture>.timing.json   TTFT, total ms, prefill/decode tokens + tok/s, sampler, model sha256

app/recipekeeper/src/test/kotlin/com/llmlocal/recipekeeper/eval/
├── EvalFixtures.kt           registry + expectations, snapshot loader, prompt assembly (mirrors LlmTaskRepositoryImpl)
├── EvalPromptRenderingTest.kt   detect/render/prompt assertions → build/eval/prompts/<fixture>.txt (+ .meta.json)
└── EvalParserTest.kt         golden → parser assertions + build/eval/report.md (EvalReport)

app/recipekeeper/src/androidTest/kotlin/com/llmlocal/recipekeeper/eval/EvalRunnerTest.kt   on-device runner (path b)
```

## The re-run loop

Run it whenever the prompt, the renderer, the parser, a fixture or the model changes:

```bash
./gradlew :app:recipekeeper:testDebugUnitTest --tests '*EvalPromptRenderingTest*' \
  && tools/eval/run_local_model.sh \
  && ./gradlew :app:recipekeeper:testDebugUnitTest --tests '*EvalParserTest*'
```

1. **Rendering test** (no model): for every fixture, asserts the expected
   tier / candidate count, that the rendered input fits `budgetChars`, and
   writes the exact prompt the app would send to
   `app/recipekeeper/build/eval/prompts/<fixture>.txt`.
2. **Runner**: streams every prompt through LiteRT-LM and writes the goldens
   to `src/test/resources/eval/outputs/`. Commit them.
3. **Parser test** (no model, runs in CI): parses every committed golden with
   `RecipeKeeperParser` + `RecipeKeeperDraftParser` (every prefix monotonic),
   applies the quality bar (title, ≥ 3 ingredients, ≥ 2 steps, ingredient count
   within ±20 % of JSON-LD, servings/time present when the source has them,
   no unexpected section headers, Jaccard ≥ 0.8 vs `SchemaOrgRecipeMapper`
   for STRUCTURED fixtures) and writes `app/recipekeeper/build/eval/report.md`.
   Fixtures without a golden are **skipped with an explicit assumption
   message**, never failed. Copy the report to
   `docs/plans/briefs/ws7-eval-report.md` when goldens change.

`./gradlew :app:recipekeeper:testDebugUnitTest --tests '*Eval*'` runs both JVM
halves (plus the registry guard test) in one go.

## Runner path (a): desktop LiteRT-LM on this Mac (default)

`run_local_model.sh` needs `python3` (3.10–3.13; `uv` is used when present),
`curl`, and ~3 GB of disk. On first run it

- creates a venv at `~/.cache/llmlocal-eval/venv` with `litert-lm==0.18.0`
  (the Python wheel of the same release as the Android AAR; it ships
  `liblitert-lm.dylib` for macOS arm64 and `.so` for Linux x86_64),
- downloads `gemma-4-E2B-it.litertlm` (2.59 GB, public, the catalog URL) to
  `~/.cache/llmlocal-eval/` unless `LLMLOCAL_EVAL_MODEL` points at a copy
  (set `LLMLOCAL_EVAL_CACHE` to move the whole cache),
- runs `run_local_model.py` with **CPU** backend, `max_num_tokens 4096`,
  `top_k 40 / top_p 0.95 / seed 0` (LiteRtLlmEngine defaults),
  `temperature 0.2`, `max_output_tokens 1024` (plan §3.4), one fresh
  conversation per prompt, prompt sent as a single user turn — i.e. what
  `LlmTaskRepositoryImpl` + `LiteRtLlmEngine` do.

Useful flags (all forwarded to the Python runner; `--help` lists them):
`--only <fixture>` (repeatable), `--dry-run`, `--temperature`, `--threads N`,
`--max-output-tokens`, `--backend gpu` (macOS GPU is not supported by the
wheel today; CPU is the path that matches debug builds anyway).

Timing JSON fields: `ttftMs` / `totalMs` are wall-clock around the stream;
`benchmark.*` is LiteRT-LM's own `BenchmarkInfo` (prefill tokens and tok/s,
decode tokens and tok/s); `maxOutputTokensHit` flags outputs that ran into the
cap; `modelSha256` ties the goldens to a model build.

**Absolute numbers are Mac numbers.** Apple-silicon CPU prefill/decode is in
the same order of magnitude as a flagship phone CPU but not the same; the
research notes' S26 Ultra figures are 557 / 47 tok/s (prefill / decode).
Use the Mac timings for *relative* comparisons (tier vs tier, prompt size vs
time) and path (b) for device numbers.

## Runner path (b): adb-driven instrumented runner

```bash
tools/eval/run_local_model.sh --device [--serial SERIAL] [--model-path /data/local/tmp/eval/gemma-4-E2B-it.litertlm]
```

Pushes the prompts to `/data/local/tmp/eval/prompts`, installs the debug app
+ test APK, runs `EvalRunnerTest` (`-e promptsDir …`, optional `-e modelPath …`,
`-e maxOutputTokens`, `-e temperature`), and pulls
`files/eval/outputs/` back into `src/test/resources/eval/outputs/` via
`run-as`. Without `--model-path` the test uses the engine from the app's Koin
graph: the model selected in Recipe Keeper's model management, CPU backend
in debug builds (`BuildConfig.LLM_BACKEND`). With `--model-path` it builds a
fresh CPU `LiteRtLlmEngine` around that file (e.g. `adb push` the bundle to
`/data/local/tmp/eval/` on a phone with space).

Requirements: a real **arm64** device with ≥ 3 GB free and the model
downloaded in Recipe Keeper (or pushed). `emulator-5554` has ~170–220 MB free
and no model, so path (b) was only compiled (`assembleDebugAndroidTest`),
not executed, in WS7. Instrumented timing JSON has `ttftMs` / `totalMs` only
(the Kotlin API does not expose `BenchmarkInfo` through `LlmEngine`).

## Fixtures

| Fixture | Covers | Tier | Source |
|---|---|---|---|
| `single_bbcgoodfood_pancakes` | JSON-LD single Recipe | STRUCTURED | bbcgoodfood.com |
| `graph_pinchofyum_cookies` | Yoast `@graph` with a Recipe node | STRUCTURED | pinchofyum.com |
| `roundup_guardian_slater` | round-up: 5 Recipe nodes in one array | STRUCTURED ×5 | theguardian.com (Nigel Slater) |
| `howto_recipetineats_tikka` | `HowToSection` instructions, 30 ingredients | STRUCTURED | recipetineats.com |
| `plain_joyofbaking_pancakes` | plain-text blog, no structured data | TEXT | joyofbaking.com |
| `plain_cfe_banana_bread` | plain-text, table layout, no structured data | TEXT | cookingforengineers.com |
| `nonen_marmiton_crepes` | non-English (French) | STRUCTURED | marmiton.org |
| `list_pinchofyum_recipes` | list/category page, no recipe (negative) | WHOLE_PAGE | pinchofyum.com |
| `long_theperfectloaf_sourdough` | very long page (~31k chars readable) | STRUCTURED | theperfectloaf.com |
| `micro_nigella_banana_bread` | microdata Recipe, ~23k chars readable | STRUCTURED | nigella.com |

Fetched 2026-10-18 with `curl -L -A "Mozilla/5.0 …"` (see `fixtures.tsv`).
Captures come from `core/web/tools/capture-harness/harness.mjs` under jsdom,
not from the WebView: layout is faked, so `images[]` is always empty and
`topOffset` is synthetic; everything the pipeline reads for detection and
rendering (JSON-LD, microdata, Readability text, sections) is real. Pages
behind bot protection (Dotdash/Meredith sites, Food Network, Cookie+Kate,
Tasty…) return 403/406 to curl and were not used.

`tools/eval/capture_fixtures.sh [--only name] [--from-html]` re-captures;
after a re-capture check the expectations in `EvalFixtures.kt` and re-run the
loop above.

## Reading the report

`build/eval/report.md` has a per-fixture table (tier, prompt chars, parse
status, field coverage, Jaccard vs the deterministic mapper, TTFT/total,
decode tok/s), per-tier summary lines, the failed-check details, and a
"model vs `fallback()`" table for the STRUCTURED fixtures — the direct input
to decision #3. The committed snapshot with the recommendation is
`docs/plans/briefs/ws7-eval-report.md`.
