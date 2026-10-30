"""Keep each line's stop order in lines.json the same as on kastoria.citybus.gr.

On kastoria.citybus.gr every line has one main route that goes out and comes
back in one list of stops: our "out" direction, then our "in" one from the stop
where the bus turns round. Other routes of a line are variants (a Wednesday
trip, a school run, another ending). This reads them all and compares the main
route of each line with its two directions in lines.json:

- a stop added between two stops we have, or a stop taken out: written to
  lines.json. A new stop's minutes after the first stop are worked out from
  where it sits between its neighbours; one that isn't in stops.json yet is
  added there with the site's names, for a person to tidy
- anything bigger: the first or last stop of a direction changing, the bus
  turning round somewhere else, stops in another order, or more than MAX_EDITS
  changes in a direction. Nothing is written and the report says what changed
- a variant changing: reported, since our alternative endings (branches) and
  school lines come from them and are kept by hand

The street paths ("shape") stay as they are; the report says when a direction
changed, so scripts/route_shapes.py can be run again. data/citybus_routes.json
keeps which route is each line's main one and what the site said the last time,
so each change is reported once.

    python scripts/sync_routes.py               # check only, print the report
    python scripts/sync_routes.py --write       # also apply the small changes

.github/workflows/sync-timetables.yml runs it every morning after the stops.
"""

import argparse
import difflib
import json
import os
import sys
import time
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from sync_stops import API, ASSETS, PAGE, get, meters, read_site

ROOT = Path(__file__).resolve().parent.parent
SNAPSHOT = ROOT / "data" / "citybus_routes.json"

# More stops added or taken out of one direction than this is a new route, not a tweak
MAX_EDITS = 3


def read_routes(token: str, agency: str) -> dict:
    """{route code: {"line", "name", "stops": [stop codes in order]}} for every route of every line"""
    routes = {}
    for line in json.loads(get(f"{API}/el/{agency}/lines", token)):
        for r in line.get("routes") or []:
            time.sleep(1)
            seq = json.loads(get(f"{API}/el/{agency}/routes/{r['code']}/sequence", token))
            routes[r["code"]] = {"line": line["code"], "name": r["name"].strip(),
                                 "stops": [x["code"] for x in sorted(seq, key=lambda x: x["sequence"])]}
    return routes


def main_routes(lines: list, routes: dict) -> dict:
    """{line id: route code} for the lines whose out and in directions are exactly a route of theirs"""
    found = {}
    for line in lines:
        out, back = ([x["stop"] for x in d["stops"]] for d in line["directions"][:2])
        loop = out + back[1:]
        hits = [c for c, r in routes.items() if r["line"] == line["id"] and r["stops"] == loop]
        if hits:
            found[line["id"]] = sorted(hits)[0]
    return found


def edit_direction(direction: dict, new: list, coords: dict) -> tuple[list | None, list, str | None]:
    """
    The direction's stops changed to [new]: (new stops with offsets, what changed, why it can't be
    done on its own). Stops it keeps keep their offsets; an added one gets the offset between its
    neighbours in proportion to the distance.
    """
    old = direction["stops"]
    old_ids = [x["stop"] for x in old]
    if new[0] != old_ids[0] or new[-1] != old_ids[-1]:
        return None, [], f"starts or ends somewhere else ({old_ids[0]}→{old_ids[-1]} became {new[0]}→{new[-1]})"
    ops = difflib.SequenceMatcher(None, old_ids, new, autojunk=False).get_opcodes()
    added = [s for tag, _, _, j1, j2 in ops if tag in ("insert", "replace") for s in new[j1:j2]]
    removed = [s for tag, i1, i2, _, _ in ops if tag in ("delete", "replace") for s in old_ids[i1:i2]]
    if set(added) & set(removed):
        return None, [], f"stops in another order ({', '.join(sorted(set(added) & set(removed)))})"
    if len(added) + len(removed) > MAX_EDITS:
        return None, [], f"{len(added)} stops added and {len(removed)} taken out"
    if any(s not in coords for s in added):
        return None, [], f"a new stop has no position ({', '.join(s for s in added if s not in coords)})"

    kept = {x["stop"]: x["offset"] for x in old if x["stop"] not in removed}
    result = []
    for k, stop in enumerate(new):
        if stop in kept:
            result.append({"stop": stop, "offset": kept[stop]})
            continue
        prev = result[-1]
        nxt = next(s for s in new[k + 1:] if s in kept)
        a, b = meters(coords[prev["stop"]], coords[stop]), meters(coords[stop], coords[nxt])
        share = a / (a + b) if a + b else 0.5
        result.append({"stop": stop, "offset": round(prev["offset"] + share * (kept[nxt] - prev["offset"]))})
    changes = [f"+{s}" for s in added] + [f"-{s}" for s in removed]
    return result, changes, None


def render(report: dict, status: str) -> str:
    out = [f"**Route sync: {status}**", ""]
    for title, key in (("Problems", "problems"), ("Changes", "changes"),
                       ("Needs a look", "look"), ("Notes", "notes")):
        if report[key]:
            out += [f"### {title}", *[f"- {x}" for x in report[key]], ""]
    if report["changes"]:
        out += ["The street paths of the changed directions weren't redrawn; run "
                "`python scripts/route_shapes.py` to make them pass the new stops.", ""]
    if report["look"]:
        out += ["Nothing under **Needs a look** was written. Check the line on kastoria.citybus.gr and fix "
                "lines.json by hand. A change that's only too big can be applied by running the workflow with "
                "**force**; a new first or last stop, a new turning point or stops in another order always need "
                "a person, since the timetable's minutes are counted from the first stop.", ""]
    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--write", action="store_true", help="apply the small changes")
    parser.add_argument("--force", action="store_true", help="apply main route changes however many stops they add or drop")
    parser.add_argument("--report", type=Path, help="also write the report to this file")
    args = parser.parse_args()
    global MAX_EDITS
    if args.force:
        MAX_EDITS = 10_000

    report = {"problems": [], "changes": [], "look": [], "notes": []}
    lines_path, stops_path = ASSETS / "lines.json", ASSETS / "stops.json"
    lines = json.loads(lines_path.read_text(encoding="utf-8"))
    stops = json.loads(stops_path.read_text(encoding="utf-8"))
    snapshot = json.loads(SNAPSHOT.read_text(encoding="utf-8")) if SNAPSHOT.exists() else None
    previous = snapshot["routes"] if snapshot else None

    routes, site = {}, {}
    try:
        site, token, agency = read_site()
        routes = read_routes(token, agency)
    except (OSError, ValueError, AttributeError, KeyError) as e:
        report["problems"].append(f"Couldn't read the routes from {PAGE} ({e})")
    if routes and len(routes) < 0.8 * len(previous or routes):
        report["problems"].append(f"The site lists {len(routes)} routes, {len(previous)} last time; "
                                  "that looks like a broken answer")

    mains = dict(snapshot["mains"]) if snapshot else {}
    lines_changed, stops_added = False, []
    if not report["problems"]:
        if not mains:
            mains = main_routes(lines, routes)
            report["notes"].append("First run: data/citybus_routes.json now holds each line's main route and "
                                   "what the site said, and later runs report what changes from here")
            for line in lines:
                if line["id"] not in mains:
                    report["look"].append(f"Line {line['id']}: none of its routes on the site matches its "
                                          "directions, so it isn't kept in sync")
        coords = {s["id"]: (s["lat"], s["lng"]) for s in stops} | {c: (s["lat"], s["lng"]) for c, s in site.items()}
        known = {s["id"] for s in stops}

        for line in lines:
            code = mains.get(line["id"])
            if code is None:
                continue
            route = routes.get(code)
            fresh = previous is None or previous.get(code, {}).get("stops") != (route or {}).get("stops")
            if route is None:
                if fresh:
                    report["look"].append(f"Line {line['id']}: its main route {code} is no longer on the site")
                continue
            out, back = line["directions"][0], line["directions"][1]
            turn = out["stops"][-1]["stop"]
            seq = route["stops"]
            if seq == [x["stop"] for x in out["stops"]] + [x["stop"] for x in back["stops"]][1:]:
                continue
            if seq.count(turn) != 1:
                if fresh:
                    report["look"].append(f"Line {line['id']}: route {code} no longer turns round at {turn}")
                continue
            i = seq.index(turn)
            edits = []
            for direction, new in ((out, seq[:i + 1]), (back, seq[i:])):
                result, changes, why = edit_direction(direction, new, coords)
                if why:
                    if fresh:
                        report["look"].append(f"Line {line['id']} {direction['id']}: {why}")
                    edits = None
                    break
                if changes:
                    edits.append((direction, result, changes))
            for direction, result, changes in edits or []:
                direction["stops"] = result
                lines_changed = True
                report["changes"].append(f"Line {line['id']} {direction['id']}: {' '.join(changes)}")
                for change in changes:
                    s = change[1:]
                    if change[0] == "+" and s not in known:
                        known.add(s)
                        stops_added.append({"id": s, "name": {"el": site[s]["el"], "en": site[s]["en"]},
                                            "lat": site[s]["lat"], "lng": site[s]["lng"]})
                        report["changes"].append(f"Stop {s} \"{site[s]['el']}\" added to stops.json; tidy its names")
                    if change[0] == "-":
                        for d in line["directions"]:
                            if any(s in b["stops"] for b in d.get("branches", [])):
                                report["look"].append(f"Line {line['id']}: stop {s} left the main route but an "
                                                      "alternative ending (branch) still has it")

        # The variants are only reported: branches and school lines are kept by hand
        if previous is not None:
            main_codes = set(mains.values())
            for code in sorted(set(routes) | set(previous)):
                if code in main_codes:
                    continue
                before, after = previous.get(code), routes.get(code)
                if before == after:
                    continue
                if after is None:
                    report["look"].append(f"Route {code} \"{before['name']}\" (line {before['line']}) is no longer on the site")
                elif before is None:
                    report["look"].append(f"New route {code} \"{after['name']}\" on line {after['line']}")
                else:
                    ops = difflib.SequenceMatcher(None, before["stops"], after["stops"], autojunk=False).get_opcodes()
                    diff = [f"+{s}" for t, _, _, j1, j2 in ops if t in ("insert", "replace") for s in after["stops"][j1:j2]] + \
                           [f"-{s}" for t, i1, i2, _, _ in ops if t in ("delete", "replace") for s in before["stops"][i1:i2]]
                    report["look"].append(f"Route {code} \"{after['name']}\" (line {after['line']}) changed: "
                                          f"{' '.join(diff) or 'same stops, other order'}")

    new_snapshot = {"mains": dict(sorted(mains.items())), "routes": dict(sorted(routes.items()))}
    snapshot_changed = bool(routes) and not report["problems"] and new_snapshot != snapshot
    if report["problems"]:
        status = "blocked"
    elif lines_changed or snapshot_changed:
        status = "changed"
    else:
        status = "unchanged"

    if status == "changed" and args.write:
        if lines_changed:
            end = "\n" if lines_path.read_text(encoding="utf-8").endswith("\n") else ""
            lines_path.write_text(json.dumps(lines, indent=2, ensure_ascii=False) + end, encoding="utf-8")
            if stops_added:
                end = "\n" if stops_path.read_text(encoding="utf-8").endswith("\n") else ""
                stops_path.write_text(json.dumps(stops + stops_added, indent=2, ensure_ascii=False) + end, encoding="utf-8")
            # The app takes downloaded data only when meta.json's date is newer than its own
            try:
                today = datetime.now(ZoneInfo("Europe/Athens")).date()
            except ZoneInfoNotFoundError:
                today = datetime.now().date()
            meta_path = ASSETS / "meta.json"
            meta = json.loads(meta_path.read_text(encoding="utf-8"))
            meta["updated"] = today.isoformat()
            meta_path.write_text(json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")
        SNAPSHOT.write_text(json.dumps(new_snapshot, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    elif status == "changed":
        status = "changed (not written, run with --write)"

    text = render(report, status)
    print(text)
    if args.report:
        args.report.write_text(text, encoding="utf-8")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as f:
            f.write(f"status={status.split(' ')[0]}\n")
            f.write(f"look={'yes' if report['look'] else 'no'}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
