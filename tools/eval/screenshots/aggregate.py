#!/usr/bin/env python3
"""aggregate.py: results.csv -> per mode x budget means (all pages; a failed/unparsed run counts as 0 recall)."""
import csv
from collections import defaultdict
from pathlib import Path

rows = list(csv.DictReader(open(Path(__file__).parent / "results.csv")))
g = defaultdict(list)
for r in rows:
    parts = r["id"].split("__")
    key = "__".join(parts[1:]) if len(parts) > 1 else "?"
    g[key].append(r)


def f(x):
    try:
        return float(x)
    except (TypeError, ValueError):
        return None


def mean(xs):
    xs = [x for x in xs if x is not None]
    return sum(xs) / len(xs) if xs else float("nan")


print("| mode__budget | runs | parsed | ing recall | ing precision | qty exact (matched) | step tok recall | halluc/run | prefill tok | total s |")
print("|---|---|---|---|---|---|---|---|---|---|")
for k in sorted(g):
    rs = g[k]
    parsed = [r for r in rs if r["parsed"] == "1"]
    print(f"| {k} | {len(rs)} | {len(parsed)}/{len(rs)} | {mean([f(r['ing_rec']) for r in rs]):.2f} | "
          f"{mean([f(r['ing_prec']) for r in parsed]):.2f} | {mean([f(r['qty_ok']) for r in parsed]):.2f} | "
          f"{mean([f(r['step_rec']) for r in rs]):.2f} | {mean([f(r['halluc']) for r in rs]):.1f} | "
          f"{mean([f(r['prefill_tok']) for r in rs]):.0f} | {mean([f(r['total_ms']) for r in rs]) / 1000:.1f} |")
