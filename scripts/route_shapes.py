"""Give every line direction in lines.json a street path to draw on the map.

The path is built stop by stop:

1. The operator's own route maps. astikoktelkastorias.gr embeds a Google My Maps
   map on the page of each main line. Between two consecutive stops that both lie
   on the route drawn there, in order, that drawing is used.
2. OSRM, for the rest: the stretches where the route has changed since the
   operator drew its map (e.g. the way out of the Αφετηρία), and the lines the
   operator has no map for (school and variant routes).

The path is stored as an encoded polyline in the direction's "shape" field, and
"shapeSource" says where it came from: "operator", "osrm", or "mixed".

Some returns end two ways, e.g. line 1 either up past the ΚΤΕΛ to the Αφετηρία
or along the lake to Αφετηρία 2. The other ending is taken from the route
variants in data/kastoria_bus_data.csv and stored in the direction's "branches",
each with its own stops and shape.

Run it again whenever stops or their order change:

    python scripts/route_shapes.py
"""

import argparse
import csv
import json
import math
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
USER_AGENT = "astiko-ktel-kastorias-app route shapes (github.com/Kastoria-Dev-Core/astiko-ktel-kastorias-app)"

# Google My Maps embedded on the line pages of astikoktelkastorias.gr/δρομολόγια
# (Καλοχώρι has no page map, its extension is part of the Μεσοποταμία map)
OPERATOR_MAPS = {
    "Μεσοποταμία": "1dE80GwWmD0RpaSjR18-PU-RK1UU",
    "Κεφαλάρι": "1uTOwa7KSvuCh3_P7OZn__X-o4gY",
    "Εργατικές": "1DycZK9C45R6OnYMgQhfgVVv4K-4",
    "Πανεπιστήμιο": "1UdawUb4PrE5L0xEWpMWQxke1FFE-1cE",
    "Μανιάκοι": "10sAM30VtlriXz7ewS-LkJFsacsI",
    "Ομόνοια - Νοσοκομείο": "1U_Hj_8R7h_5jnrc3vqFCTPD3JTgBz48",
    "Κορομηλιά": "1rNnyHBJtGHc3xuVwv86gOFZy8H4",
    "Πολυκάρπη": "18cvFC4cGUqbrCahE4K_j4eRDxJs",
}

# A stop counts as on a route when it's within this distance of it
ON_ROUTE_METERS = 80
# Two drawn routes are chained (e.g. Μεσοποταμία then on to Καλοχώρι) when one ends this close to where the other starts
CHAIN_METERS = 150
# OSRM: a stop further than this from any road probably has wrong coordinates
MAX_SNAP_METERS = 60

KML = "{http://www.opengis.net/kml/2.2}"


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as res:
        return res.read()


def operator_routes():
    """Every route drawn on the operator's maps, as (map, route name, [(lat, lng), ...])"""
    routes = []
    for line, mid in OPERATOR_MAPS.items():
        root = ET.fromstring(fetch(f"https://www.google.com/maps/d/kml?mid={mid}&forcekml=1"))
        for placemark in root.iter(KML + "Placemark"):
            coords = placemark.find(f".//{KML}LineString/{KML}coordinates")
            if coords is None:
                continue
            points = [(float(lat), float(lng)) for lng, lat, *_ in (c.split(",") for c in coords.text.split())]
            name = (placemark.findtext(KML + "name") or "").strip()
            routes.append((line, name, points))
        time.sleep(0.5)
    return routes


class Plane:
    """Flat x/y metres around Kastoria, close enough at this scale"""

    def __init__(self, lat0):
        self.kx = 111_320 * math.cos(math.radians(lat0))
        self.ky = 110_540

    def xy(self, p):
        return p[1] * self.kx, p[0] * self.ky


def project(p, a, b):
    """Distance from p to segment ab, and how far along ab (0..1) the closest point is"""
    dx, dy = b[0] - a[0], b[1] - a[1]
    length2 = dx * dx + dy * dy
    t = 0.0 if length2 == 0 else max(0.0, min(1.0, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / length2))
    return math.hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dy), t


def landings(stops, route, plane):
    """
    Walks the route from its start and finds each stop on it, always moving forward,
    so a route that passes the same street twice is followed in the right order.
    Returns (distance, segment, t) per stop; a stop too far from the route doesn't move the walk on.
    """
    pts = [plane.xy(p) for p in route]
    cum = [0.0]
    for a, b in zip(pts, pts[1:]):
        cum.append(cum[-1] + math.dist(a, b))
    seg, prev, result = 0, None, []
    for stop in stops:
        s = plane.xy(stop)
        # Look ahead far enough for the next stop, but not so far that a later pass of the same street wins
        reach = cum[seg] + 1500 + (3 * math.dist(s, plane.xy(prev)) if prev else 0)
        best = (float("inf"), seg, 0.0)
        j = seg
        while j < len(pts) - 1 and cum[j] <= reach:
            d, t = project(s, pts[j], pts[j + 1])
            if d < best[0]:
                best = (d, j, t)
            j += 1
        result.append(best)
        if best[0] <= ON_ROUTE_METERS:
            seg, prev = best[1], stop
    return result


def between(route, start, end):
    """The route from one stop's landing point to the next one's"""
    def point(seg, t):
        a, b = route[seg], route[seg + 1]
        return a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])

    (s_seg, s_t), (e_seg, e_t) = start, end
    return [point(s_seg, s_t)] + route[s_seg + 1:e_seg + 1] + [point(e_seg, e_t)]


def decode(encoded):
    points, index, lat, lng = [], 0, 0, 0
    while index < len(encoded):
        for which in (0, 1):
            result = shift = 0
            while True:
                b = ord(encoded[index]) - 63
                index += 1
                result |= (b & 0x1F) << shift
                shift += 5
                if b < 0x20:
                    break
            delta = ~(result >> 1) if result & 1 else result >> 1
            if which == 0:
                lat += delta
            else:
                lng += delta
        points.append((lat / 1e5, lng / 1e5))
    return points


def encode(points):
    """Google's encoded polyline format, 5 decimals"""
    out, prev = [], (0, 0)
    for lat, lng in points:
        cur = (round(lat * 1e5), round(lng * 1e5))
        for delta in (cur[0] - prev[0], cur[1] - prev[1]):
            v = ~(delta << 1) if delta < 0 else delta << 1
            while v >= 0x20:
                out.append(chr((0x20 | (v & 0x1F)) + 63))
                v >>= 5
            out.append(chr(v + 63))
        prev = cur
    return "".join(out)


def osrm_route(osrm, points):
    coords = ";".join(f"{lng:.6f},{lat:.6f}" for lat, lng in points)
    body = json.loads(fetch(f"{osrm}/route/v1/driving/{coords}?overview=full&geometries=polyline"))
    if body.get("code") != "Ok":
        raise RuntimeError(body.get("message") or body.get("code"))
    return body["routes"][0], body["waypoints"]


# The raw dataset the JSON was built from. It has every route variant of each line,
# while lines.json keeps one per direction.
DATASET = Path(__file__).resolve().parent.parent / "data" / "kastoria_bus_data.csv"
# Branches are only the last few stops, where a variant ends at the other Αφετηρία
MAX_BRANCH_STOPS = 6


def route_variants(known_stops):
    """line id -> [(terminus name, [stop ids in order]), ...] for every route variant in the dataset"""
    rows = [r for r in csv.DictReader(DATASET.open(encoding="utf-8")) if r["entry_type"] == "route_stop"]
    grouped = {}
    for r in rows:
        grouped.setdefault((r["line_code"], r["route_code"]), []).append(r)
    variants = {}
    for (line, _), rs in sorted(grouped.items()):
        rs.sort(key=lambda r: int(r["stop_sequence"]))
        ids = [r["stop_code"] for r in rs]
        if all(i in known_stops for i in ids):
            variants.setdefault(line, []).append((rs[-1]["stop_name"], ids))
    return variants


def other_endings(stop_ids, variants):
    """
    Ways this direction can end at a different Αφετηρία, e.g. line 1 coming back along
    the lake to Αφετηρία 2 instead of up past the ΚΤΕΛ. Each is the stops from where
    the variant leaves this direction to its own last stop.
    """
    endings = []
    for terminus, ids in variants:
        if not terminus.startswith("ΑΦΕΤΗΡΙΑ") or ids[-1] == stop_ids[-1]:
            continue
        shared = [i for i, stop in enumerate(ids) if stop in stop_ids[:-1]]
        if not shared:
            continue
        branch = ids[shared[-1]:]
        # Only a different ending near the end of this direction, not a whole other route
        if len(branch) <= MAX_BRANCH_STOPS and stop_ids.index(branch[0]) >= len(stop_ids) - MAX_BRANCH_STOPS and branch not in endings:
            endings.append(branch)
    return endings


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--osrm", default="https://router.project-osrm.org")
    parser.add_argument("--delay", type=float, default=1.1, help="seconds between OSRM requests")
    args = parser.parse_args()

    stops = {s["id"]: (s["lat"], s["lng"]) for s in json.loads((ASSETS / "stops.json").read_text(encoding="utf-8"))}
    lines_path = ASSETS / "lines.json"
    lines = json.loads(lines_path.read_text(encoding="utf-8"))
    plane = Plane(40.52)

    routes = operator_routes()
    # A map can draw a route in pieces, like Μεσοποταμία then on to Καλοχώρι. Chain those, only within one map.
    candidates = [(f"{m}: {n}", pts) for m, n, pts in routes] + [
        (f"{m}: {a_name} + {b_name}", a + b[1:])
        for m, a_name, a in routes
        for m2, b_name, b in routes
        if m == m2 and a is not b and math.dist(plane.xy(a[-1]), plane.xy(b[0])) <= CHAIN_METERS
        # An extension carries on away from where the first piece started, it doesn't come back (a round trip)
        and math.dist(plane.xy(b[-1]), plane.xy(a[0])) > math.dist(plane.xy(a[-1]), plane.xy(a[0]))
    ]
    print(f"{len(routes)} routes on the operator's maps, {len(candidates) - len(routes)} chained within a map\n")

    def on_route_pairs(found):
        return [i for i in range(len(found) - 1)
                if found[i][0] <= ON_ROUTE_METERS and found[i + 1][0] <= ON_ROUTE_METERS
                and (found[i + 1][1], found[i + 1][2]) >= (found[i][1], found[i][2])]

    warnings = []

    def build_path(name, stop_ids):
        """Encoded street path through the stops, and where it came from"""
        points = [stops[i] for i in stop_ids]
        # The operator route that covers the most stop-to-stop stretches
        label, route, found = max(
            ((label, route, landings(points, route, plane)) for label, route in candidates),
            key=lambda c: len(on_route_pairs(c[2])),
        )
        covered = set(on_route_pairs(found))
        path, gap_start = [], None

        def fill_gap(until):
            result, waypoints = osrm_route(args.osrm, points[gap_start:until + 1])
            for stop, wp in zip(stop_ids[gap_start:until + 1], waypoints):
                if wp["distance"] > MAX_SNAP_METERS:
                    warnings.append(f"{name}: stop {stop} is {wp['distance']:.0f} m from the nearest road")
            path.extend(decode(result["geometry"]))
            time.sleep(args.delay)

        for i in range(len(points) - 1):
            if i in covered:
                if gap_start is not None:
                    fill_gap(i)
                    gap_start = None
                path.extend(between(route, found[i][1:], found[i + 1][1:]))
            elif gap_start is None:
                gap_start = i
        if gap_start is not None:
            fill_gap(len(points) - 1)

        pairs = len(points) - 1
        source = "operator" if len(covered) == pairs else "osrm" if not covered else "mixed"
        print(f"{name} {source:<8} {len(covered):2}/{pairs} stretches from {label if covered else '-'}")
        return encode(path), source

    variants = route_variants(stops)
    for line in lines:
        for direction in line["directions"]:
            name = f'{line["number"]:>2} {direction["id"]:<3}'
            stop_ids = [s["stop"] for s in direction["stops"]]
            if len(stop_ids) < 2:
                continue
            direction["shape"], direction["shapeSource"] = build_path(name, stop_ids)

            branches = []
            for branch in other_endings(stop_ids, variants.get(line["id"], [])):
                shape, source = build_path(f"{name} branch to {branch[-1]}", branch)
                branches.append({"stops": branch, "shape": shape, "shapeSource": source})
            if branches:
                direction["branches"] = branches
            else:
                direction.pop("branches", None)

    lines_path.write_text(json.dumps(lines, ensure_ascii=False, indent=2), encoding="utf-8", newline="\n")
    print(f"\nWrote shapes to {lines_path}")
    for w in warnings:
        print("  " + w)


if __name__ == "__main__":
    sys.exit(main())
