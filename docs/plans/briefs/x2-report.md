# X2 report — clip-quality defects (app clip domain)

Branch `fixes/x2`. Verified: `./gradlew :app:recipekeeper:testDebugUnitTest :app:recipekeeper:assembleDebug` green. No device run (build + JVM tests only). One commit per item.

| # | Item | Where | Test |
|---|---|---|---|
| 1 | Defect 2: `isComplete` needs mean step ≥ 25 chars or total ≥ 120, no ingredient with ≥ 3 ` - `; blob (> 200 chars, unnumbered) split on sentences; dash-joined `recipeIngredient` split on ` - `/` – ` (and glued `letter-digit`, the receptes.cat shape) when ≥ 3 parts | `RecipeKeeperClipConfig.isComplete`, `SchemaOrgRecipeMapper` | ClipConfigTest, MapperTest |
| 2 | Defect 3: TEXT acceptance — steps must reuse ≥ 60 % of the method's words and number ≥ half its paragraphs, else `ParseFailed` | `RecipeKeeperClipConfig.accept` (+ `renderedText` stash) | ClipConfigTest |
| 3 | Defect 4: first heading section matching `notesWord`, ≤ 600 chars, becomes `notes` on `fallback()` (single-recipe pages only) | `RecipeKeeperClipConfig.fallback` | ClipConfigTest |
| 4 | Defect 5: picture dedupe by stem (WP `-WxH`/`-scaled`, `/thumb*/`, `crop-`, `w_N`, query), keeps the largest | new `PictureKeys`, `rankPictures` | PictureKeysTest |
| 5 | Defect 7: servings ≤ 40 chars / first sentence / cut before "or"; no `servingsDetail` (not trivial) — the long text is truncated in mapper and parser | `TextCleaner.shortServings` | MapperTest |
| 6 | Defect 8: space between quantity and known unit (EN/ES/CA/DE/FR/IT) | `TextCleaner.separateUnits` | MapperTest |
| 7 | Defect 9: lower-case continuation joins the previous step; promo lines dropped | `SchemaOrgRecipeMapper.splitTextSteps` | MapperTest |
| 8 | Defect 10: prompt (copy every step, times only when labelled, never inferred) + regenerated goldens + `ws7-eval-report.md` | `RecipeKeeperPromptBuilder`, `eval/outputs/**` | EvalParserTest green |
| 9 | Defect 11: strip `www.`/`m.`/`mobile.` from host | `TextCleaner.host` | MapperTest |
| 10 | WS12 A: strict threshold first; relaxed only when it yields exactly one section; list-style paths never relax | `detectText` / `pickSections` / `listPath` | ClipConfigTest (6 two-line cards → empty) |
| 11 | WS12 C: waiting hint while draft has no title/ingredient/step and the tail is blank | `ClipDraftCard.TailBlock`, `RecipeDraft.isEmpty` | DraftParserTest |
| 12 | WS12 D: sink stores only the preferred picks as `pictureCandidates` (new optional `preferredPictures` ctor param, wired in `ClipModule`); retry use case unchanged apart from KDoc | `RecipeKeeperClipSink`, `ClipModule` | PipelineTest |

## Eval before / after (same model, Mac CPU, 10 fixtures; full tables in `ws7-eval-report.md`)

All fixtures OK before and after. Targeted effect: Nigella's inferred `total 180` → none. **Not fixed:** Joy of Baking (15/3/18 → 15/2/17, still derived from step text) and Guardian (15/10/25, unchanged) — Gemma E2B does not reliably obey "never infer". Steps +1…+2 on four fixtures, −1 on tikka (15 vs 16 in the mapper); Jaccard 1.00 on all STRUCTURED. Totals −1.6…+4.8 s (noise). A hard guarantee needs a deterministic post-filter (not done).

## Notes for the lead

- **Test-fixture change:** `lasagne-4x3.jpg` / `lasagne-16x9.jpg` (jsonld/howto_sections.json + two tests) renamed `lasagne-side` / `lasagne-slice`: by item 4 `-NxM` aspect crops are one photo, which is the Love & Lemons defect.
- Eval Jaccard now tokenises `250g` as `250`,`g` (mapper spaces units).
- Item 2 relies on `renderLlmInput` having run for the candidate (it stashes the rendered text per candidate id); without it the check falls back to the section text. Pages whose method has no instructions heading are only checked when the paragraphs directly follow the ingredient lines (a short line ends the run), so comment/related-link furniture is never taken for the method. A Smitten-like page whose layout breaks that run is not caught.
- Item 7: promo lines are dropped outright (not merged); the pattern is deliberately narrow (`to see`, `see our`, `click`, `video`, `watch the/our/a video|tutorial|how`) so a real "Watch the oven" step survives.
- Item 12: the sink can't see which pictures the user unticked (it only gets files), so it keeps the clipper's *preferred* set; if a clip had no preferred proposal it falls back to the full list.
- Item 1 also splits glued `letter-digit` dashes (`farina-75grs`), beyond the brief's ` - ` rule, because that is the receptes.cat shape.
- Not touched (X3): `feature/webclipper`, `core/web` (defects 1, 6, WS12 B).
