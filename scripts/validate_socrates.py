#!/usr/bin/env python3
"""
Compares the close approaches found by Kaksha with the ones listed by CelesTrak SOCRATES.

SOCRATES publishes every conjunction closer than 5 km for the next 7 days. This script keeps
the ones that involve an Indian satellite and fall inside the 24 hour window of the latest
Kaksha run, then checks how many of them Kaksha also found. Two events count as the same
when they involve the same two objects and their times of closest approach are close.

Only the Python standard library is used.

Usage:
    python scripts/validate_socrates.py --socrates backend/data/socrates/sort-minRange.csv
The Kaksha backend must be running, because the events are read from its API.
"""
import argparse
import csv
import json
import statistics
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path


def get_json(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.load(response)


def parse_iso(text):
    return datetime.fromisoformat(text.replace("Z", "+00:00")).astimezone(timezone.utc)


def parse_socrates_time(text):
    # Example: 2026-10-25 17:16:25.360 (UTC)
    return datetime.strptime(text, "%Y-%m-%d %H:%M:%S.%f").replace(tzinfo=timezone.utc)


def load_socrates(path, indian_ids, window_start, window_end):
    """Returns the matching rows and the largest miss distance the file reaches.

    The file is sorted by miss distance. If a download was cut short, the rows above that
    distance are missing, so only events up to that distance can be compared fairly.
    """
    rows = []
    coverage_km = 0.0
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            try:
                a, b = int(row["NORAD_CAT_ID_1"]), int(row["NORAD_CAT_ID_2"])
                miss = float(row["TCA_RANGE"])
                tca = parse_socrates_time(row["TCA"])
            except (TypeError, ValueError):
                continue  # a broken line, for example the last one of a cut-off download
            coverage_km = max(coverage_km, miss)
            if a not in indian_ids and b not in indian_ids:
                continue
            if not (window_start <= tca <= window_end):
                continue
            rows.append({
                "pair": frozenset((a, b)),
                "names": (row["OBJECT_NAME_1"], row["OBJECT_NAME_2"]),
                "tca": tca,
                "miss": miss,
            })
    return rows, coverage_km


def match(socrates, ours, tolerance):
    """One-to-one matching: for each SOCRATES event, the closest unused Kaksha event of the same pair."""
    unused = list(ours)
    matched = []
    unmatched_socrates = []
    for s in sorted(socrates, key=lambda e: e["tca"]):
        candidates = [o for o in unused
                      if o["pair"] == s["pair"] and abs((o["tca"] - s["tca"]).total_seconds()) <= tolerance]
        if not candidates:
            unmatched_socrates.append(s)
            continue
        best = min(candidates, key=lambda o: abs((o["tca"] - s["tca"]).total_seconds()))
        unused.remove(best)
        matched.append((s, best))
    return matched, unmatched_socrates, unused


def percent(part, whole):
    return f"{100 * part / whole:.1f}%" if whole else "n/a"


def build_report(args, run_at, window_end, coverage_km, socrates, ours, matched, missed, extra):
    dt = [abs((o["tca"] - s["tca"]).total_seconds()) for s, o in matched]
    dmiss = [abs(o["miss"] - s["miss"]) for s, o in matched]
    lines = [
        f"# Validation against CelesTrak SOCRATES, {run_at:%Y-%m-%d}",
        "",
        f"- Kaksha run: {run_at:%Y-%m-%d %H:%M} UTC, window until {window_end:%Y-%m-%d %H:%M} UTC",
        f"- SOCRATES file: `{Path(args.socrates).name}`",
        f"- Compared up to a miss distance of {coverage_km:.2f} km, the largest value present in the SOCRATES file",
        f"- Two events are the same when the object pair matches and the closest-approach times differ by at most {args.tolerance} s",
        "",
        "| | Count |",
        "|---|---|",
        f"| SOCRATES events with an Indian satellite in the window | {len(socrates)} |",
        f"| Kaksha events in the window | {len(ours)} |",
        f"| Found by both | {len(matched)} |",
        f"| **Match rate** (found by both / SOCRATES events) | **{percent(len(matched), len(socrates))}** |",
        f"| Precision (found by both / Kaksha events) | {percent(len(matched), len(ours))} |",
        "",
    ]
    if matched:
        lines += [
            "For the events found by both:",
            "",
            f"- Time of closest approach differs by {statistics.median(dt):.1f} s (median), {max(dt):.1f} s (largest)",
            f"- Miss distance differs by {statistics.median(dmiss):.3f} km (median), {max(dmiss):.3f} km (largest)",
            "",
        ]
    if missed:
        lines += ["SOCRATES events Kaksha did not find (first 10):", ""]
        for s in missed[:10]:
            lines.append(f"- {s['names'][0]} and {s['names'][1]}, {s['tca']:%Y-%m-%d %H:%M:%S} UTC, {s['miss']:.3f} km")
        lines.append("")
    if extra:
        lines += ["Kaksha events SOCRATES does not list (first 10):", ""]
        for o in extra[:10]:
            lines.append(f"- {o['names'][0]} and {o['names'][1]}, {o['tca']:%Y-%m-%d %H:%M:%S} UTC, {o['miss']:.3f} km")
        lines.append("")
    lines += [
        "Differences are expected. SOCRATES and Kaksha use different element sets (SOCRATES is updated",
        "a few times a day and its file may be older than the Kaksha run), different propagation code,",
        "and SOCRATES only screens objects it classes as active. A few kilometres of difference in the",
        "position of two fast objects moves the miss distance and the time noticeably.",
    ]
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--socrates", required=True, help="path to SOCRATES sort-minRange.csv")
    parser.add_argument("--api", default="http://localhost:8080", help="Kaksha backend address")
    parser.add_argument("--tolerance", type=int, default=120, help="allowed difference of closest-approach times, seconds")
    parser.add_argument("--out", default="reports", help="folder for the markdown report")
    args = parser.parse_args()

    stats = get_json(f"{args.api}/api/stats/last-run")
    run_at = parse_iso(stats["runAt"])
    window_end = run_at + timedelta(hours=24)

    satellites = get_json(f"{args.api}/api/satellites/indian")
    indian_ids = {s["noradId"] for s in satellites}

    events = get_json(f"{args.api}/api/events?date={run_at:%Y-%m-%d}")
    ours = [{
        "pair": frozenset((e["primaryId"], e["secondaryId"])),
        "names": (e["primaryName"], e["secondaryName"]),
        "tca": parse_iso(e["tca"]),
        "miss": e["missKm"],
    } for e in events if run_at <= parse_iso(e["tca"]) <= window_end]

    socrates, coverage_km = load_socrates(args.socrates, indian_ids, run_at, window_end)
    # Kaksha events beyond that distance cannot appear in the file, so they are left out of the comparison.
    ours = [o for o in ours if o["miss"] <= coverage_km]
    matched, missed, extra = match(socrates, ours, args.tolerance)

    report = build_report(args, run_at, window_end, coverage_km, socrates, ours, matched, missed, extra)
    print(report)

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    out_file = out_dir / f"socrates-{run_at:%Y-%m-%d}.md"
    out_file.write_text(report, encoding="utf-8")
    print(f"Report written to {out_file}")


if __name__ == "__main__":
    main()
