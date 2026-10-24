#!/usr/bin/env python3
"""Ground truth (title, ingredients, steps, servings, times) for each page in pages.json -> truth.json.
JSON-LD pages: fixture capture (app/recipekeeper/src/test/resources/eval/pages/<fixture>.json) or a live fetch of `url`.
Plain pages: hand-checked lists in truth_manual.json (read from the page itself)."""
import json, re, sys, urllib.request, html
from pathlib import Path
here = Path(__file__).parent
root = here.parents[2]
pages = json.loads((here / "pages.json").read_text())
manual = json.loads((here / "truth_manual.json").read_text()) if (here / "truth_manual.json").exists() else {}

def find_recipes(node, out):
    if isinstance(node, list):
        for n in node: find_recipes(n, out)
    elif isinstance(node, dict):
        t = node.get("@type")
        if t == "Recipe" or (isinstance(t, list) and "Recipe" in t): out.append(node)
        for v in node.values():
            if isinstance(v, (list, dict)): find_recipes(v, out)

def steps(ins):
    out = []
    if isinstance(ins, str): return [s.strip() for s in re.split(r"\n+", re.sub(r"<[^>]+>", "\n", html.unescape(ins))) if s.strip()]
    for i in ins or []:
        if isinstance(i, str): out.append(html.unescape(i).strip())
        elif i.get("@type") == "HowToSection": out += steps(i.get("itemListElement"))
        else: out.append(html.unescape(i.get("text") or i.get("name") or "").strip())
    return [s for s in out if s]

res = {}
for p in pages:
    if p["id"] in manual:
        res[p["id"]] = manual[p["id"]]; continue
    fx = root / "app/recipekeeper/src/test/resources/eval/pages" / f"{p.get('fixture','')}.json"
    blocks = []
    if fx.exists():
        cap = json.loads(fx.read_text())["capture"]; blocks = list(cap["jsonLd"]) + list(cap.get("microdata") or [])
    else:
        req = urllib.request.Request(p["url"], headers={"User-Agent": "Mozilla/5.0 (Macintosh) Chrome/126"})
        h = urllib.request.urlopen(req, timeout=30).read().decode("utf8", "ignore")
        blocks = re.findall(r'<script[^>]*ld\+json[^>]*>(.*?)</script>', h, re.S)
    recs = []
    for b in blocks:
        try: find_recipes(json.loads(b) if isinstance(b, str) else b, recs)
        except Exception: pass
    if not recs: print("NO RECIPE JSON-LD", p["id"]); continue
    pick = int(p.get("recipeIndex", 0))
    r = recs[pick]
    res[p["id"]] = dict(title=html.unescape(str(r.get("name"))), ingredients=[html.unescape(i).strip() for i in r.get("recipeIngredient", [])],
                        steps=steps(r.get("recipeInstructions")), servings=str(r.get("recipeYield")), prep=r.get("prepTime"), cook=r.get("cookTime"),
                        total=r.get("totalTime"), source="json-ld", nRecipesOnPage=len(recs))
    print(p["id"], len(res[p["id"]]["ingredients"]), "ingredients", len(res[p["id"]]["steps"]), "steps")
(here / "truth.json").write_text(json.dumps(res, indent=1, ensure_ascii=False))
