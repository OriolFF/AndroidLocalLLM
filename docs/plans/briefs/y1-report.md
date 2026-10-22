# Y1 report — ES/CA/EL recipe detection + clearer "no direct recipe" copy

Branch `fixes/y1`. Verified: `./gradlew :core:web:testDebugUnitTest :feature:webclipper:testDebugUnitTest :app:recipekeeper:testDebugUnitTest :app:recipekeeper:assembleDebug` — green.

## What changed
1. **Units/words** (`RecipeKeeperClipConfig` companion): `quantityLine` is now built from a unit list (EN/DE/FR/IT kept + the ES/CA/EL lists from the brief, plus `vasito(s)`). Word boundaries use `(?<![\p{L}\p{N}])` / `(?![\p{L}\p{N}])` instead of `\b` (Greek-safe); `IGNORE_CASE` (Kotlin adds UNICODE_CASE). New shapes:
   - number words (`un/una/uno/dos/tres/dues/ένα/μία/μια/δύο/τρία/τρεις`) only when followed by a unit;
   - letters after a number are `\p{L}` (Greek `3 αυγά`);
   - quantity after the name: `farina, 200 g`, `αλεύρι 200 γρ.` (name ≤ 60 non-digit chars, line ends with number + unit);
   - abbreviations `c/s c/c c.s. c.p. cda cdas cdta cdtas ud uds κ.σ. κ.γ. γρ. τεμ. φλ.`.
   - `ingredientsWord`, `instructionsWord`, `notesWord` extended with all brief words (+ unaccented Greek variants for all-caps headings, `consells`, `σημειώσεις`, `συμβουλές`). `listPath` gained `categoria(s)`, `etiqueta(s)`, `buscar`, `cerca`, `κατηγορία/ες`; `recipePath` gained `syntag`, `συνταγ`.
2. **`RecipeLineGrammar`** section headers accept the same words (ES/CA/EL ingredients, steps, servings `Personas/Persones/Comensals/Μερίδες/Άτομα`, prep/cook/total times, notes, title, description). Tests: `MultilingualGrammarTest`.
3. **`capture.js`**: when a page has < 2 `h1–h3`, `h4–h6`, `[role=heading]` and bold-only `p/div/span` (≤ 80 chars, not digit-led) also start sections (levels: tag number / `aria-level` / 4 / 5). Cap kept at 20, but sections are now built up to 80 and `capSections` keeps quantity-bearing sections (+ the one before) first, so a mega-menu of h3 no longer eats the 20 slots (seen on vilaweb.cat). Fixtures `fixture-es-blog-h4.html`, `fixture-ca-boldheads.html`, `fixture-el-rolehead.html` in the harness; captured JSON in `app/recipekeeper/src/test/resources/y1/pages/` (+ one real page, `es-cronica-real.json`). `MultilingualDetectionTest`: one TEXT candidate each.
4. **Copy**: feature `strings_webclipper.xml` → "No direct recipe found on this page" / "This page doesn't expose a recipe we can read directly. You can still ask the on-device model to extract it." / "Extract with AI" / "Keep browsing" (keys unchanged). The app has no override of these keys, so no app string change was needed. Removed the hardcoded default literal on `WebClipperLabels.nothingDetectedMessage` (rule 0). Test labels updated.
5. **List pages**: WS12-A rule untouched; regression test for `/categoria/…`, `/recetas/` index, `/categories/receptes/…`, `/κατηγορια/…`.

## Device (emulator-5556, `adb install -r`, no `pm clear`; Gemma 4 E2B on CPU)
| Page | Outcome |
|---|---|
| ES `cronica.com.ar` bizcocho de yogur (no Recipe JSON-LD) | FAB "Clip recipe" → **no sheet**, straight to extraction (1 TEXT candidate). Model output was a correct recipe, but `accept()` rejected it → "Couldn't read a recipe" (nothing saved). See finding below. |
| CA `vilaweb.cat` coca de Sant Joan (news article, recipe inline) | Sheet shown with the **new copy** ("No direct recipe found…", "Extract with AI", "Keep browsing"). Correct: the ingredients sit in one `<p>` (innerText folds them into a single line), no quantity lines → nothing detectable. |
| EL `iefimerida.gr` μπισκότα βουτύρου γιαγιάς | **No sheet**, extraction → saved row "Συνταγή για μπισκότα βουτύρου, σαν της γιαγιάς": 3 ingredients (125 γρ βούτυρο, 55 γρ ζάχαρη, 180 γρ αλεύρι), steps, picture. |

## Finding for the lead (not fixed, outside Y1)
`stepsCoverMethod`/`methodParagraphs` mis-fires on blog prose: with no "instructions" heading it takes the run of ≥ 40-char paragraphs right after the last quantity line as "the method". On cronica that run is 2 non-method paragraphs (cook time / repeated sentence) before a short sub-heading ends it, so the model's 15 correct steps fail the 60 % word-coverage check and the clip is rejected. The same happens with the pre-Y1 regex (the last quantity line is identical).
