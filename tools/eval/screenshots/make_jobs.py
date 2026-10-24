#!/usr/bin/env python3
"""make_jobs.py <shotsDir> <out jobs.json>: one job per page x mode (single|multi|tall) x budget (+70 on two pages)."""
import json, sys, glob, os
from pathlib import Path
here = Path(__file__).parent
shots, out = Path(sys.argv[1]), sys.argv[2]
sysp = (here / "system_prompt.txt").read_text()
pages = json.loads((here / "pages.json").read_text())
USER = "Extract the recipe from this page."
jobs = []
for p in pages:
    pid = p["id"]
    vs = sorted(glob.glob(str(shots / f"{pid}_v*.png")))
    if not vs: continue
    modes = {"single": vs[:1], "multi": vs, "tall": [str(shots / f"{pid}_full.png")]}
    for budget in (280, 140) + ((70,) if pid in ("rte_tikka", "gr_afrato") else ()):
        for mode, imgs in modes.items():
            jobs.append(dict(id=f"{pid}__{mode}__b{budget}", images=imgs, budget=budget, system=sysp, user=USER))
json.dump(jobs, open(out, "w"), indent=1)
print(len(jobs), "jobs")
