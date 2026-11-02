#!/usr/bin/env python3
"""
Cross-checks Kaksha against a second, independent SGP4 implementation (the python "sgp4"
package, written separately from Orekit) using the same GP files.

It answers two questions that the SOCRATES comparison alone cannot:

  A. SOCRATES uses its own, different element sets. Of its events, how many are really under
     5 km with the elements Kaksha used, and did Kaksha report all of those?
  B. Are Kaksha's own events correct? Each one is recomputed with the other SGP4 code.

Setup:   pip install sgp4
Usage:   python scripts/crosscheck_sgp4.py --socrates backend/data/socrates/sort-minRange.csv

The Kaksha backend must be running, and the GP files must be the ones the last run used.
"""
import argparse
import glob
import math
import statistics
from datetime import timedelta
from pathlib import Path

from sgp4 import omm
from sgp4.api import Satrec, jday

import validate_socrates as v


def load_satellites(gp_pattern):
    """Reads every GP file that matches the pattern. An object in two files keeps its newest elements."""
    satellites = {}
    for gp_path in sorted(glob.glob(gp_pattern)):
        with open(gp_path, newline="", encoding="utf-8") as f:
            for fields in omm.parse_csv(f):
                sat = Satrec()
                omm.initialize(sat, fields)
                key = int(fields["NORAD_CAT_ID"])
                old = satellites.get(key)
                if old is None or sat.jdsatepoch + sat.jdsatepochF > old.jdsatepoch + old.jdsatepochF:
                    satellites[key] = sat
    return satellites


def position(sat, t):
    jd, fr = jday(t.year, t.month, t.day, t.hour, t.minute, t.second + t.microsecond / 1e6)
    error, r, _ = sat.sgp4(jd, fr)
    return r if error == 0 else None


def closest_approach(a, b, centre, half_window, step):
    """Smallest distance between two satellites within a time window, searched on a fine grid."""
    best_distance, best_time = float("inf"), None
    for i in range(int(2 * half_window / step) + 1):
        t = centre + timedelta(seconds=-half_window + i * step)
        pa, pb = position(a, t), position(b, t)
        if pa is None or pb is None:
            continue
        distance = math.dist(pa, pb)
        if distance < best_distance:
            best_distance, best_time = distance, t
    return best_distance, best_time


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--socrates", required=True)
    parser.add_argument("--gp", default="backend/data/celestrak/gp-*.csv", help="GP files used by the last run (a pattern)")
    parser.add_argument("--api", default="http://localhost:8080")
    parser.add_argument("--out", default="reports")
    args = parser.parse_args()

    satellites = load_satellites(args.gp)
    stats = v.get_json(f"{args.api}/api/stats/last-run")
    run_at = v.parse_iso(stats["runAt"])
    window_end = run_at + timedelta(hours=24)
    indian_ids = {s["noradId"] for s in v.get_json(f"{args.api}/api/satellites/indian")}
    kaksha_events = v.get_json(f"{args.api}/api/events?date={run_at:%Y-%m-%d}")

    socrates, _ = v.load_socrates(args.socrates, indian_ids, run_at, window_end)

    # ---- A: SOCRATES events, recomputed with the elements Kaksha used ----
    missing_objects, under_5km = 0, []
    over_5km = 0
    for row in socrates:
        a_id, b_id = sorted(row["pair"])[:2]
        if a_id not in satellites or b_id not in satellites:
            missing_objects += 1
            continue
        distance, t = closest_approach(satellites[a_id], satellites[b_id], row["tca"], 240, 0.5)
        if distance < 5.0:
            under_5km.append((row, t))
        else:
            over_5km += 1

    kaksha_by_pair = {}
    for e in kaksha_events:
        kaksha_by_pair.setdefault(frozenset((e["primaryId"], e["secondaryId"])), []).append(e)
    reported = sum(
        1 for row, t in under_5km
        if any(abs((v.parse_iso(e["tca"]) - t).total_seconds()) < 120 for e in kaksha_by_pair.get(row["pair"], [])))

    # ---- B: Kaksha events, recomputed with the other SGP4 code ----
    miss_diff, time_diff, outside = [], [], 0
    for e in kaksha_events:
        t0 = v.parse_iso(e["tca"])
        distance, t = closest_approach(satellites[e["primaryId"]], satellites[e["secondaryId"]], t0, 30, 0.05)
        miss_diff.append(abs(distance - e["missKm"]))
        time_diff.append(abs((t - t0).total_seconds()))
        outside += distance >= 5.0

    lines = [
        f"# Cross-check with an independent SGP4 implementation, {run_at:%Y-%m-%d}",
        "",
        "## A. SOCRATES events recomputed with the element sets Kaksha used",
        "",
        "| | Count |",
        "|---|---|",
        f"| SOCRATES events with an Indian satellite in the window | {len(socrates)} |",
        f"| One of the two objects is not in Kaksha's catalog | {missing_objects} |",
        f"| Under 5 km with Kaksha's elements | {len(under_5km)} |",
        f"| Over 5 km with Kaksha's elements (the element sets differ) | {over_5km} |",
        f"| **Of those under 5 km, reported by Kaksha** | **{reported} of {len(under_5km)}** |",
        "",
        "## B. Kaksha events recomputed with the other SGP4 code",
        "",
        f"- Events checked: {len(kaksha_events)}",
        f"- Miss distance differs by {statistics.median(miss_diff) * 1e6:.3f} mm (median), {max(miss_diff) * 1e6:.3f} mm (largest)",
        f"- Time of closest approach differs by {statistics.median(time_diff):.2f} s (median), {max(time_diff):.2f} s (largest)",
        f"- Events the other code puts at 5 km or more: {outside}",
        "",
    ]
    report = "\n".join(lines)
    print(report)
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    out_file = out_dir / f"crosscheck-{run_at:%Y-%m-%d}.md"
    out_file.write_text(report, encoding="utf-8")
    print(f"Report written to {out_file}")


if __name__ == "__main__":
    main()
