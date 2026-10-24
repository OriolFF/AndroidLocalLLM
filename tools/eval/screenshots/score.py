#!/usr/bin/env python3
"""score.py <outDir> [<outDir> ...] -> results.csv + results.md (stdout)

Parses each <page>__<mode>__b<budget>.txt (and <page>__text.txt / <page>__dom.txt) with a Python port of the
minimal RecipeKeeperParser line grammar (Title:/Servings:/Prep|Cook|TotalMinutes:/Ingredients:/Steps:/Notes:/NO RECIPE)
and scores against truth.json:
  ing_jacc   token-set Jaccard of all ingredient lines (the metric EvalParserTest uses)
  ing_rec    truth ingredient lines matched by an output line (line Jaccard >= 0.5, greedy one-to-one)
  ing_prec   output ingredient lines that match a truth line
  qty_ok     of the matched pairs, share whose numeric tokens (fractions normalised) are identical (OCR-sensitive)
  step_rec   share of truth-step tokens present in the output steps (truth blobs make step counts incomparable)
  steps      number of output steps vs truth steps
  halluc     output ingredient lines with no truth match + output steps sharing < 25% tokens with every truth step blob
"""
import csv, json, re, sys, unicodedata
from pathlib import Path

here = Path(__file__).parent
truth = json.loads((here / "truth.json").read_text())
FR = {"½": " 1/2", "¼": " 1/4", "¾": " 3/4", "⅓": " 1/3", "⅔": " 2/3", "⅛": " 1/8", "⅜": " 3/8", "⅝": " 5/8"}


def norm(s):
    for k, v in FR.items():
        s = s.replace(k, v)
    s = unicodedata.normalize("NFKD", s.lower())
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = re.sub(r"(?<=\d)(?=[a-z])", " ", s)
    return s


def toks(s):
    return {t for t in re.split(r"[^\w/]+", norm(s)) if t and t not in {"a", "of", "the", "and", "or"}}


def nums(s):
    out = []
    for m in re.finditer(r"\d+\s+\d/\d|\d+/\d+|\d+(?:\.\d+)?", norm(s)):
        t = m.group(0)
        if " " in t and "/" in t:
            a, b = t.split()
            n, d = b.split("/")
            out.append(str(round(int(a) + int(n) / int(d), 3)))
        elif "/" in t:
            n, d = t.split("/")
            out.append(str(round(int(n) / int(d), 3)))
        else:
            out.append(str(round(float(t), 3)).rstrip("0").rstrip("."))
    return sorted(out)


def jac(a, b):
    return len(a & b) / len(a | b) if a | b else 1.0


def parse(text):
    t = text.strip()
    if t.upper().startswith("NO RECIPE"):
        return None
    d = dict(title=None, servings=None, prep=None, cook=None, total=None, ing=[], steps=[], notes=[])
    sec = None
    for line in t.split("\n"):
        l = line.strip()
        m = re.match(r"(Title|Description|Servings|PrepMinutes|CookMinutes|TotalMinutes):\s*(.*)", l)
        if m:
            k = {"Title": "title", "Servings": "servings", "PrepMinutes": "prep", "CookMinutes": "cook", "TotalMinutes": "total"}.get(m.group(1))
            if k:
                d[k] = m.group(2)
            continue
        if l.startswith("Ingredients:"):
            sec = "ing"
        elif l.startswith("Steps:"):
            sec = "steps"
        elif l.startswith("Notes:"):
            sec = "notes"
        elif sec == "ing" and l.startswith("-"):
            d["ing"].append(l[1:].strip())
        elif sec == "steps" and re.match(r"\d+[.)]", l):
            d["steps"].append(re.sub(r"^\d+[.)]\s*", "", l))
        elif sec == "notes" and l.startswith("-"):
            d["notes"].append(l[1:].strip())
    return d


def match(out_lines, truth_lines, thr=0.5):
    """greedy one-to-one by line-token Jaccard"""
    pairs, used = [], set()
    scored = sorted(((jac(toks(a), toks(b)), i, j) for i, a in enumerate(out_lines) for j, b in enumerate(truth_lines)), reverse=True)
    ui, uj = set(), set()
    for s, i, j in scored:
        if s < thr:
            break
        if i in ui or j in uj:
            continue
        ui.add(i); uj.add(j); pairs.append((i, j, s))
    return pairs


def score(path, key):
    page = key.split("__")[0]
    tr = truth[page]
    out = parse(path.read_text())
    row = dict(id=key, page=page)
    meta = path.with_suffix(".json")
    if meta.exists():
        m = json.loads(meta.read_text())
        row.update(prefill_tok=m["bench"].get("prefillTokens"), ttft_ms=m.get("ttftMs"), total_ms=m.get("totalMs"),
                   decode_tok=m["bench"].get("decodeTokens"), error=m.get("error"))
    if out is None or not out["ing"]:
        row.update(parsed=0, ing_jacc=0, ing_rec=0, ing_prec=0, qty_ok=0, step_rec=0, steps=0, halluc=0, n_ing=0)
        return row
    ing, st = out["ing"], out["steps"]
    pairs = match(ing, tr["ingredients"])
    row["parsed"] = 1
    row["n_ing"] = len(ing)
    row["ing_jacc"] = round(jac(set().union(*[toks(x) for x in ing]), set().union(*[toks(x) for x in tr["ingredients"]])), 2)
    row["ing_rec"] = round(len(pairs) / len(tr["ingredients"]), 2)
    row["ing_prec"] = round(len(pairs) / len(ing), 2)
    row["qty_ok"] = round(sum(nums(ing[i]) == nums(tr["ingredients"][j]) for i, j, _ in pairs) / len(pairs), 2) if pairs else 0
    ttoks = set().union(*[toks(x) for x in tr["steps"]]) if tr["steps"] else set()
    otoks = set().union(*[toks(x) for x in st]) if st else set()
    row["step_rec"] = round(len(ttoks & otoks) / len(ttoks), 2) if ttoks else 0
    row["steps"] = f"{len(st)}/{len(tr['steps'])}"
    blob = [toks(" ".join(tr["steps"]))]
    badsteps = sum(1 for s in st if len(toks(s) & blob[0]) / max(1, len(toks(s))) < 0.25)
    row["halluc"] = (len(ing) - len(pairs)) + badsteps
    row["servings"] = int(bool(out["servings"]))
    row["times"] = int(any(out[k] for k in ("prep", "cook", "total")))
    return row


def main():
    rows = []
    for d in sys.argv[1:]:
        for p in sorted(Path(d).glob("*.txt")):
            rows.append(score(p, p.stem))
    cols = ["id", "parsed", "n_ing", "ing_jacc", "ing_rec", "ing_prec", "qty_ok", "steps", "step_rec", "halluc", "servings", "times", "prefill_tok", "ttft_ms", "total_ms", "decode_tok", "error"]
    with open(here / "results.csv", "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=cols, extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)
    print("| " + " | ".join(cols[:-1]) + " |")
    print("|" + "---|" * (len(cols) - 1))
    for r in rows:
        print("| " + " | ".join(str(r.get(c, "")) for c in cols[:-1]) + " |")


main()
