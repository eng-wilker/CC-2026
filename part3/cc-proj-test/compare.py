#!/usr/bin/env python3
"""Compare artillery result files: per-endpoint median / p95 / p99 (ms) and totals.

usage: python3 compare.py results-app.json results-fun.json [more.json ...]
"""
import json, sys

PFX = "plugins.metrics-by-endpoint.response_time."

def load(path):
    agg = json.load(open(path))["aggregate"]
    s = agg.get("summaries", {})
    c = agg.get("counters", {})
    eps = {k[len(PFX):]: v for k, v in s.items() if k.startswith(PFX)}
    total = s.get("http.response_time", {})
    return {"eps": eps, "total": total, "counters": c,
            "rate": agg.get("rates", {}).get("http.request_rate")}

def cell(v, key):
    x = v.get(key) if v else None
    return "-" if x is None else f"{x:.0f}"

def main(paths):
    runs = [(p, load(p)) for p in paths]
    names = sorted({e for _, r in runs for e in r["eps"]})
    head = f"{'endpoint':38}" + "".join(f"| {p[:22]:^22}" for p, _ in runs)
    sub = f"{'':38}" + "".join(f"| {'med':>6}{'p95':>8}{'p99':>8}" for _ in runs)
    print(head); print(sub); print("-" * len(head))
    for n in names + ["ALL REQUESTS"]:
        row = f"{n:38}"
        for _, r in runs:
            v = r["total"] if n == "ALL REQUESTS" else r["eps"].get(n)
            row += f"| {cell(v,'median'):>6}{cell(v,'p95'):>8}{cell(v,'p99'):>8}"
        print(row)
    print()
    for p, r in runs:
        c = r["counters"]
        errs = {k: v for k, v in c.items() if k.startswith(("errors.", "http.codes.4", "http.codes.5"))}
        print(f"{p}: requests={c.get('http.requests')} ok(200)={c.get('http.codes.200')} "
              f"rate={r['rate']}/s max={r['total'].get('max')}ms errors={errs or 'none'}")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    main(sys.argv[1:])
