"""Snap every line direction in lines.json to the streets.

Sends each direction's stops, in order, to OSRM and stores the street path it
returns as an encoded polyline in the direction's "shape" field. The app draws
that instead of straight lines between stops, and falls back to straight lines
for a direction without a shape.

Run it again whenever stops or their order change:

    python scripts/snap_routes.py

Uses the public OSRM demo server by default, which allows light use at about one
request per second. Pass --osrm to use another server.
"""

import argparse
import json
import math
import sys
import time
import urllib.request
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
USER_AGENT = "astiko-ktel-kastorias-app route snapping (github.com/Kastoria-Dev-Core/astiko-ktel-kastorias-app)"

# A stop further than this from any road probably has wrong coordinates
MAX_SNAP_METERS = 60
# A street path this many times longer than the straight lines usually means a detour
MAX_DETOUR = 2.5


def meters(a, b):
    lat1, lng1, lat2, lng2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lng2 - lng1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(h))


def route(osrm, points):
    coords = ";".join(f"{lng:.6f},{lat:.6f}" for lat, lng in points)
    url = f"{osrm}/route/v1/driving/{coords}?overview=full&geometries=polyline"
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as res:
        body = json.load(res)
    if body.get("code") != "Ok":
        raise RuntimeError(body.get("message") or body.get("code"))
    return body["routes"][0], body["waypoints"]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--osrm", default="https://router.project-osrm.org")
    parser.add_argument("--delay", type=float, default=1.1, help="seconds between requests")
    args = parser.parse_args()

    stops = {s["id"]: (s["lat"], s["lng"]) for s in json.loads((ASSETS / "stops.json").read_text(encoding="utf-8"))}
    lines_path = ASSETS / "lines.json"
    lines = json.loads(lines_path.read_text(encoding="utf-8"))

    warnings = []
    for line in lines:
        for direction in line["directions"]:
            name = f'{line["number"]} {direction["id"]}'
            points = [stops[s["stop"]] for s in direction["stops"]]
            if len(points) < 2:
                continue
            path, waypoints = route(args.osrm, points)
            direction["shape"] = path["geometry"]

            straight = sum(meters(a, b) for a, b in zip(points, points[1:]))
            detour = path["distance"] / straight if straight else 1
            print(f"{name:>8}: {len(points):2} stops, {path['distance'] / 1000:5.1f} km by road, x{detour:.2f} of straight")
            if detour > MAX_DETOUR:
                warnings.append(f"{name}: road path is {detour:.1f}x the straight lines, check for a detour")
            for stop, wp in zip(direction["stops"], waypoints):
                if wp["distance"] > MAX_SNAP_METERS:
                    warnings.append(f'{name}: stop {stop["stop"]} is {wp["distance"]:.0f} m from the nearest road')
            time.sleep(args.delay)

    lines_path.write_text(json.dumps(lines, ensure_ascii=False, indent=2), encoding="utf-8", newline="\n")
    print(f"\nWrote shapes to {lines_path}")
    if warnings:
        print("\nWorth a look on the map:")
        for w in warnings:
            print("  " + w)


if __name__ == "__main__":
    sys.exit(main())
