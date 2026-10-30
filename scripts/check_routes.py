"""Notice when the operator redraws a route on its maps, so the stops can be checked.

The operator's site has no list of stops anywhere, so they can't be kept in sync
the way the timetables are. What it does have is a Google My Maps map on each
line's page with the route drawn on it (scripts/route_shapes.py reads the same
maps). This reads them every week and reports:

- which maps changed since the last report: a route redrawn, added or removed.
  A changed route usually means the buses go another way, so stops may have
  moved, been added or dropped
- the stretches of a route longer than MAX_GAP_METERS that pass none of our
  stops: either open road between villages, or a part of the route that's new
  and whose stops aren't in stops.json yet

It doesn't change anything. .github/workflows/check-routes.yml runs it every
Monday and opens an issue when the maps changed; the report of the last issue
is what it compares with. Fixing stops.json and merging is enough for the app:
the docs workflow publishes the data and the app picks it up on its next start.

    python scripts/check_routes.py                          # print the report
    python scripts/check_routes.py --previous last.md       # compare with an earlier report
"""

import argparse
import hashlib
import json
import math
import os
import re
import sys
from pathlib import Path

from route_shapes import ASSETS, ON_ROUTE_METERS, OPERATOR_MAPS, Plane, operator_routes, project

# A stretch of route at least this long with none of our stops on it is listed
MAX_GAP_METERS = 1_000
# The route is checked at points this far apart
STEP_METERS = 25
# How a map's fingerprint is written in the report, so the next run can read it back
FINGERPRINT = re.compile(r"^- (.+?): `([0-9a-f]{12})`", re.M)


def fingerprint(routes) -> str:
    """Short hash of a map's drawn routes; rounding to about a metre ignores the noise of re-saving"""
    data = sorted((name, [(round(lat, 5), round(lng, 5)) for lat, lng in points]) for name, points in routes)
    return hashlib.sha1(json.dumps(data, ensure_ascii=False).encode("utf-8")).hexdigest()[:12]


def gaps(points, stops_xy, plane):
    """Stretches of the route longer than MAX_GAP_METERS with no stop within ON_ROUTE_METERS"""
    xy = [plane.xy(p) for p in points]
    out = []
    run_start, run_length = None, 0.0
    for k, (a, b) in enumerate(zip(xy, xy[1:])):
        seg = math.dist(a, b)
        n = max(1, int(seg // STEP_METERS))
        for i in range(n):
            t = i / n
            q = (a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1]))
            near = any(math.dist(q, s) <= ON_ROUTE_METERS for s in stops_xy)
            if near:
                if run_start is not None and run_length >= MAX_GAP_METERS:
                    out.append((run_start, (points[k][0], points[k][1]), run_length))
                run_start, run_length = None, 0.0
            else:
                if run_start is None:
                    run_start = points[k]
                run_length += seg / n
    if run_start is not None and run_length >= MAX_GAP_METERS:
        out.append((run_start, points[-1], run_length))
    return out


def link(p) -> str:
    return f"[{p[0]:.4f}, {p[1]:.4f}](https://www.google.com/maps?q={p[0]:.5f},{p[1]:.5f})"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--previous", type=Path, help="an earlier report to compare the maps with")
    parser.add_argument("--report", type=Path, help="also write the report to this file")
    args = parser.parse_args()

    stops = json.loads((ASSETS / "stops.json").read_text(encoding="utf-8"))
    plane = Plane(40.52)
    stops_xy = [plane.xy((s["lat"], s["lng"])) for s in stops]

    try:
        routes = operator_routes()
    except OSError as e:
        print(f"Couldn't read the operator's maps: {e}", file=sys.stderr)
        status = "error"
        routes = None

    previous = {}
    if args.previous and args.previous.exists():
        previous = dict(FINGERPRINT.findall(args.previous.read_text(encoding="utf-8")))

    if routes is not None:
        by_map = {name: [] for name in OPERATOR_MAPS}
        for map_name, route_name, points in routes:
            by_map[map_name].append((route_name, points))
        prints = {name: fingerprint(r) for name, r in by_map.items()}
        changed = [name for name in prints if previous.get(name) != prints[name]]

        out = ["**Operator route maps**", ""]
        if not previous:
            out += ["First check, so there's nothing to compare with: this is the starting point. "
                    "Close this issue once you've looked; the next one opens only when a map changes.", ""]
        elif changed:
            out += ["These maps changed since the last report. Look at them on the line's page on "
                    "astikoktelkastorias.gr and check the line's stops in stops.json:", ""]
            out += [f"- **{name}**" + ("" if name in previous else " (new map)") for name in changed]
            gone = sorted(set(previous) - set(prints))
            out += [f"- **{name}** (no longer checked)" for name in gone]
            out += [""]

        out += ["### Stretches with none of our stops", "",
                f"Longer than {MAX_GAP_METERS} m. Open road between villages is expected here; a stretch "
                "inside town may be a new part of the route whose stops are missing.", ""]
        found = False
        for name, rs in by_map.items():
            for route_name, points in sorted(rs, key=lambda r: r[0]):
                for start, end, length in gaps(points, stops_xy, plane):
                    found = True
                    label = f"{name}, \"{route_name}\"" if route_name else name
                    out.append(f"- {label}: {round(length / 100) / 10:.1f} km from {link(start)} to {link(end)}")
        if not found:
            out.append("None.")
        out += ["", "### Maps", ""]
        out += [f"- {name}: `{prints[name]}`" for name in by_map]
        out += [""]
        text = "\n".join(out)
        status = "changed" if not previous or changed or set(previous) != set(prints) else "unchanged"
    else:
        text = "**Operator route maps: couldn't be read**\n"

    print(text)
    if args.report:
        args.report.write_text(text, encoding="utf-8")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as f:
            f.write(f"status={status}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
