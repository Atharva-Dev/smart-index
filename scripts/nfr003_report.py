#!/usr/bin/env python3
"""NFR-003 report: p50/p95 per benchmark metric from Macrobenchmark JSON, with a 1,000 ms gate.

Usage: python3 scripts/nfr003_report.py <dir> [--gate-ms 1000] [--out nfr003-report.md]
Exit codes: 0 all gated p95 under the gate, 1 a gated p95 >= gate or a gated metric missing, 2 no JSON found.
"""
import argparse
import json
import math
import os
import sys

DEFAULT_DIR = "benchmark/build/outputs/connected_android_test_additional_output"
PLAIN_KEYS = {"timeToInitialDisplayMs", "timeToFullDisplayMs", "frameDurationCpuMs", "frameOverrunMs"}
GATED_PREFIXES = ("SIDX:search:firstPage", "SIDX:timeline:firstPage")


def wanted(key):
    return key.startswith("SIDX:") or key in PLAIN_KEYS


def gated(key):
    return key.startswith(GATED_PREFIXES)


def percentile_nearest_rank(values, pct):
    s = sorted(values)
    return s[max(0, math.ceil(pct * len(s)) - 1)]


def find_files(root):
    found = []
    for base, _dirs, files in os.walk(root):
        for f in files:
            if f.endswith("benchmarkData.json"):
                found.append(os.path.join(base, f))
    return sorted(found)


def collect_metrics(bench):
    out = {}
    for group in ("metrics", "sampledMetrics"):
        for key, val in (bench.get(group) or {}).items():
            runs = val.get("runs") if isinstance(val, dict) else None
            if not (wanted(key) and runs):
                continue
            # sampledMetrics (FrameTimingMetric) runs are lists of per-frame samples: flatten one level.
            flat = []
            for r in runs:
                if isinstance(r, (list, tuple)):
                    flat.extend(float(x) for x in r)
                else:
                    flat.append(float(r))
            if flat:
                out[key] = flat
    return out


def expects_gated(name):
    # search (S1-S3, S6), timeline open (S4a) and startup (S5) benchmarks must carry a gated metric.
    n = name.lower()
    return n.startswith(("search", "openbusyday", "coldstart", "warmstart")) or "timeline" in n


def fmt(x):
    return f"{x:.1f}"


def build(files, gate_ms):
    rows, failed, header = [], False, []
    for path in files:
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
        ctx = data.get("context") or {}
        build_info = ctx.get("build") or {}
        header.append(
            f"- `{os.path.basename(path)}`: device {build_info.get('model', '?')} "
            f"({build_info.get('brand', '?')}), build {build_info.get('fingerprint', build_info.get('device', '?'))}, "
            f"sdk {build_info.get('version', {}).get('sdk', '?')}"
        )
        for bench in data.get("benchmarks", []):
            name = bench.get("name", "?")
            metrics = collect_metrics(bench)
            if expects_gated(name) and not any(gated(k) for k in metrics):
                rows.append((name, "(gated metric missing)", 0, "—", "—", "—", "FAIL"))
                failed = True
            for key in sorted(metrics):
                v = metrics[key]
                p95 = percentile_nearest_rank(v, 0.95)
                status = "—"
                if gated(key):
                    status = "PASS" if p95 < gate_ms else "FAIL"
                    failed = failed or status == "FAIL"
                rows.append((name, key, len(v), fmt(percentile_nearest_rank(v, 0.5)), fmt(p95), fmt(max(v)), status))
    return rows, failed, header


def render(rows, header, gate_ms):
    lines = ["# NFR-003 report", "", f"Gate: p95 < {gate_ms:g} ms on `SIDX:search:firstPage*` and `SIDX:timeline:firstPage*`.", ""]
    lines += header + ["", "| benchmark | metric | n | p50 | p95 | max | gate |", "|---|---|---|---|---|---|---|"]
    for r in rows:
        lines.append("| " + " | ".join(str(c) for c in r) + " |")
    return "\n".join(lines) + "\n"


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("dir", nargs="?", default=DEFAULT_DIR)
    ap.add_argument("--gate-ms", type=float, default=1000)
    ap.add_argument("--out", default="nfr003-report.md")
    args = ap.parse_args(argv)
    files = find_files(args.dir)
    if not files:
        print(f"no *benchmarkData.json under {args.dir}", file=sys.stderr)
        return 2
    rows, failed, header = build(files, args.gate_ms)
    text = render(rows, header, args.gate_ms)
    with open(args.out, "w", encoding="utf-8") as fh:
        fh.write(text)
    print(text)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
