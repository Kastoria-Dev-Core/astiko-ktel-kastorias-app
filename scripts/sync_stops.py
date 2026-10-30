"""Keep stops.json the same as the stops on kastoria.citybus.gr.

kastoria.citybus.gr is the operator's live bus site, and its stops page lists
every stop with the operator's stop code, its position and the lines that
serve it. The page loads them from rest.citybus.gr with a token it gives every
visitor; this does the same, once a day. The people behind it told us to use
the site rather than an API key of our own.

This compares them with stops.json:

- a stop that moved up to MAX_MOVE_METERS: written to stops.json, with today's date in
  meta.json so the app takes the published copy
- anything that needs a person: a new stop (it also has to go into its line's
  stop order in lines.json, so the report says where it sits on the route), a
  stop that's gone, one that moved further, a new name on the site, or a stop
  now served by other lines (the route probably changed). Nothing of these is
  written; the report lists them
- the site can't be read, or lost many stops at once: nothing is written

The names in stops.json are never changed: they're tidied by hand (accents,
spelling, English). data/citybus_stops.json keeps what the site said the last
time, so each change is reported once, not every morning until it's fixed.

    python scripts/sync_stops.py               # check only, print the report
    python scripts/sync_stops.py --write       # also apply the moves and save what the site said

.github/workflows/sync-timetables.yml runs it every morning with the timetables.
"""

import argparse
import json
import math
import os
import re
import sys
import time
import urllib.request
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
SNAPSHOT = ROOT / "data" / "citybus_stops.json"
PAGE = "https://kastoria.citybus.gr/el/stops"
API = "https://rest.citybus.gr/api/v1"
USER_AGENT = "astiko-ktel-kastorias-app stop sync (github.com/Kastoria-Dev-Core/astiko-ktel-kastorias-app)"

# A stop moving further than this is more likely a mistake than a stop moved across the road
MAX_MOVE_METERS = 300
# Moves smaller than this are rounding, not worth a commit
MIN_MOVE_METERS = 3
# The site losing more than this share of its stops at once is a broken answer, not a change
MAX_LOST = 0.1


def get(url: str, token: str | None = None) -> str:
    headers = {"User-Agent": USER_AGENT}
    if token:
        headers["Authorization"] = "Bearer " + token
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as res:
                return res.read().decode("utf-8")
        except OSError:
            if attempt == 2:
                raise
            time.sleep(5 * (attempt + 1))
    raise OSError("unreachable")


def meters(a: tuple, b: tuple) -> float:
    k = 111_320 * math.cos(math.radians(a[0]))
    return math.hypot((a[1] - b[1]) * k, (a[0] - b[0]) * 110_540)


def read_site() -> tuple[dict, str, str]:
    """{code: stop} from the site, with the token and agency code the page uses"""
    page = get(PAGE)
    token = re.search(r"token\s*=\s*'([^']+)'", page).group(1)
    agency = re.search(r"agencyCode\s*=\s*'?(\d+)", page).group(1)
    greek = json.loads(get(f"{API}/el/{agency}/stops", token))
    time.sleep(1)
    english = {s["code"]: s["name"] for s in json.loads(get(f"{API}/en/{agency}/stops", token))}
    stops = {}
    for s in greek:
        stops[s["code"]] = {
            "code": s["code"],
            "el": s["name"].strip(),
            "en": english.get(s["code"], "").strip(),
            "lat": s["latitude"],
            "lng": s["longitude"],
            "lines": sorted(s.get("lineCodes") or []),
            "routes": sorted(s.get("routeCodes") or []),
        }
    return stops, token, agency


def neighbours(code: str, routes: list[str], token: str, agency: str, names: dict) -> list[str]:
    """Where a new stop sits on each of its routes: between which stops"""
    out = []
    for route in routes[:6]:
        time.sleep(1)
        try:
            seq = [x["code"] for x in sorted(json.loads(get(f"{API}/el/{agency}/routes/{route}/sequence", token)),
                                             key=lambda x: x["sequence"])]
        except (OSError, ValueError, KeyError):
            continue
        if code not in seq:
            continue
        i = seq.index(code)
        before = f"{seq[i - 1]} {names.get(seq[i - 1], '')}".strip() if i > 0 else "the start"
        after = f"{seq[i + 1]} {names.get(seq[i + 1], '')}".strip() if i + 1 < len(seq) else "the end"
        out.append(f"route {route}: between {before} and {after}")
    return out


def render(report: dict, status: str) -> str:
    out = [f"**Stop sync: {status}**", ""]
    for title, key in (("Problems", "problems"), ("Changes", "changes"),
                       ("Needs a look", "look"), ("Notes", "notes")):
        if report[key]:
            out += [f"### {title}", *[f"- {x}" for x in report[key]], ""]
    if report["look"]:
        out += ["Nothing under **Needs a look** was written. A new stop goes into stops.json and into its "
                "line's stop order in lines.json (the report says where); the names in stops.json are "
                "kept by hand.", ""]
    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--write", action="store_true", help="apply the moves and save what the site said")
    parser.add_argument("--force", action="store_true", help="also apply moves longer than MAX_MOVE_METERS")
    parser.add_argument("--report", type=Path, help="also write the report to this file")
    args = parser.parse_args()

    report = {"problems": [], "changes": [], "look": [], "notes": []}
    stops_path = ASSETS / "stops.json"
    ours = json.loads(stops_path.read_text(encoding="utf-8"))
    by_id = {s["id"]: s for s in ours}
    previous = {s["code"]: s for s in json.loads(SNAPSHOT.read_text(encoding="utf-8"))} if SNAPSHOT.exists() else None

    site, token, agency = {}, "", ""
    try:
        site, token, agency = read_site()
    except (OSError, ValueError, AttributeError, KeyError) as e:
        report["problems"].append(f"Couldn't read the stops from {PAGE} ({e})")
    if site and len(site) < (1 - MAX_LOST) * len(by_id):
        report["problems"].append(f"The site lists {len(site)} stops and stops.json has {len(by_id)}; "
                                  "that looks like a broken answer")

    moved = []
    if not report["problems"]:
        # Compared with the last time the site was read, so each change is reported once.
        # The first time, everything is compared with stops.json itself.
        seen = previous if previous is not None else {
            c: {"el": s["name"]["el"], "lines": site[c]["lines"] if c in site else []} for c, s in by_id.items()}
        names = {c: s["name"]["el"] for c, s in by_id.items()} | {c: s["el"] for c, s in site.items() if c not in by_id}

        for code in sorted(set(site) - set(by_id)):
            if code in seen:
                continue
            s = site[code]
            where = neighbours(code, s["routes"], token, agency, names)
            report["look"].append(
                f"New stop {code} \"{s['el']}\" ({s['en'] or 'no English name'}) at "
                f"[{s['lat']:.5f}, {s['lng']:.5f}](https://www.google.com/maps?q={s['lat']:.6f},{s['lng']:.6f}), "
                f"lines {', '.join(s['lines']) or 'none'}" + (f"; {'; '.join(where)}" if where else ""))
        for code in sorted(set(by_id) - set(site)):
            if previous is not None and code not in previous:
                continue
            report["look"].append(f"Stop {code} \"{by_id[code]['name']['el']}\" is no longer on the site")
        for code in sorted(set(by_id) & set(site)):
            s, o = site[code], by_id[code]
            d = meters((o["lat"], o["lng"]), (s["lat"], s["lng"]))
            if d >= MIN_MOVE_METERS:
                if d <= MAX_MOVE_METERS or args.force:
                    moved.append(code)
                    report["changes"].append(f"Stop {code} \"{o['name']['el']}\" moved {d:.0f} m")
                elif previous is None or meters((previous[code]["lat"], previous[code]["lng"]), (s["lat"], s["lng"])) >= MIN_MOVE_METERS:
                    report["look"].append(f"Stop {code} \"{o['name']['el']}\" moved {d:.0f} m on the site, "
                                          f"to [{s['lat']:.5f}, {s['lng']:.5f}](https://www.google.com/maps?q={s['lat']:.6f},{s['lng']:.6f}); "
                                          "not applied, run with force once it's checked")
            before = seen.get(code)
            if previous is not None and before:
                if before["el"] != s["el"]:
                    report["look"].append(f"Stop {code}: the site renamed it \"{before['el']}\" → \"{s['el']}\" "
                                          f"(stops.json says \"{o['name']['el']}\")")
                if before["lines"] != s["lines"]:
                    added = sorted(set(s["lines"]) - set(before["lines"]))
                    dropped = sorted(set(before["lines"]) - set(s["lines"]))
                    parts = ([f"now also line {', '.join(added)}"] if added else []) + \
                            ([f"no longer line {', '.join(dropped)}"] if dropped else [])
                    report["look"].append(f"Stop {code} \"{o['name']['el']}\": {'; '.join(parts)}. "
                                          "The route probably changed; check lines.json")
        if previous is None:
            report["notes"].append("First run: data/citybus_stops.json now holds what the site said, "
                                   "and later runs report what changes from here")

    snapshot = sorted(site.values(), key=lambda s: s["code"])
    snapshot_changed = bool(site) and not report["problems"] and (previous is None or snapshot != sorted(previous.values(), key=lambda s: s["code"]))
    if report["problems"]:
        status = "blocked"
    elif moved or snapshot_changed:
        status = "changed"
    else:
        status = "unchanged"

    if status == "changed" and args.write:
        for code in moved:
            by_id[code]["lat"], by_id[code]["lng"] = site[code]["lat"], site[code]["lng"]
        if moved:
            # Same layout as the file had, so the diff is only the moved stops
            end = "\n" if stops_path.read_text(encoding="utf-8").endswith("\n") else ""
            stops_path.write_text(json.dumps(ours, indent=2, ensure_ascii=False) + end, encoding="utf-8")
            # The app takes downloaded data only when meta.json's date is newer than its own
            try:
                today = datetime.now(ZoneInfo("Europe/Athens")).date()
            except ZoneInfoNotFoundError:
                today = datetime.now().date()
            meta_path = ASSETS / "meta.json"
            meta = json.loads(meta_path.read_text(encoding="utf-8"))
            meta["updated"] = today.isoformat()
            meta_path.write_text(json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")
        SNAPSHOT.write_text(json.dumps(snapshot, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
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
