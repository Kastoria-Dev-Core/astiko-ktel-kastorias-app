"""Export the app's timetables as a GTFS feed, for Google Maps and other trip planners.

Reads the same assets the app uses (lines, stops, timetables, meta) and writes a
gtfs.zip that follows https://gtfs.org/schedule/reference/. The service days work
the same way as in the app (domain/Schedule.kt):

- weekday, Saturday and Sunday timetables, and a Wednesday one for the lines that
  have it (the others run their weekday timetable on Wednesdays)
- fixed holidays from meta.json and the movable ones (Clean Monday, Good Friday,
  Easter Monday, Holy Spirit Monday) run the Sunday timetable
- the summer timetable runs between meta.summer.from and meta.summer.to, and a
  line without one keeps its winter timetable

Times at the stops come from each stop's offset in lines.json. Only the first stop
of a trip is marked as an exact time (timepoint 1). The alternative endings
("branches") are not exported, because the timetables don't say which trips take them.

    python scripts/export_gtfs.py                      # gtfs.zip, a year from today
    python scripts/export_gtfs.py --start 2026-11-01 --days 180 --out build/gtfs.zip
"""

import argparse
import csv
import io
import json
import zipfile
from datetime import date, timedelta
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"

AGENCY = {
    "agency_id": "astiko-ktel-kastorias",
    "agency_name": "Αστικό ΚΤΕΛ Καστοριάς",
    "agency_url": "https://astikoktelkastorias.gr/",
    "agency_timezone": "Europe/Athens",
    "agency_lang": "el",
    "agency_phone": "+30 24670 83959",
    "agency_email": "astikast@otenet.gr",
}
PUBLISHER = {"name": "Kastoria Dev Core", "url": "https://github.com/Kastoria-Dev-Core"}
DIRECTION_IDS = {"out": 0, "in": 1}
SERVICE_DAYS = ("weekday", "saturday", "sunday", "wednesday")
BUS = 3


def orthodox_easter(year: int) -> date:
    """Same formula as Schedule.orthodoxEaster, valid 1900-2099"""
    a, b, c = year % 4, year % 7, year % 19
    d = (19 * c + 15) % 30
    e = (2 * a + 4 * b - d + 34) % 7
    month = (d + e + 114) // 31
    day = ((d + e + 114) % 31) + 1
    return date(year, month, day) + timedelta(days=13)


def is_holiday(day: date, fixed: list[str]) -> bool:
    easter = orthodox_easter(day.year)
    movable = {(easter + timedelta(days=n)).strftime("%m-%d") for n in (-48, -2, 1, 50)}
    key = day.strftime("%m-%d")
    return key in fixed or key in movable


def service_day(day: date, fixed: list[str], has_wednesday: bool) -> str:
    if is_holiday(day, fixed) or day.weekday() == 6:
        return "sunday"
    if day.weekday() == 5:
        return "saturday"
    return "wednesday" if day.weekday() == 2 and has_wednesday else "weekday"


def season(day: date, summer: dict) -> str:
    key = day.strftime("%m-%d")
    return "summer" if summer["from"] <= key <= summer["to"] else "winter"


def decode_polyline(encoded: str) -> list[tuple[float, float]]:
    """Google's encoded polyline, precision 5, as in Geo.decodePolyline"""
    points, index, lat, lng = [], 0, 0, 0
    while index < len(encoded):
        for coord in (0, 1):
            shift = result = 0
            while True:
                b = ord(encoded[index]) - 63
                index += 1
                result |= (b & 0x1F) << shift
                shift += 5
                if b < 0x20:
                    break
            delta = ~(result >> 1) if result & 1 else result >> 1
            if coord == 0:
                lat += delta
            else:
                lng += delta
        points.append((lat / 1e5, lng / 1e5))
    return points


def gtfs_time(minutes: int) -> str:
    """Minutes after midnight as HH:MM:SS. Past midnight goes on as 24:10:00, as GTFS wants."""
    return f"{minutes // 60:02d}:{minutes % 60:02d}:00"


def stop_name(stop: dict, lang: str) -> str:
    """The stop's name with its qualifier, the same as Stop.title() in the app"""
    base = stop["name"][lang] or stop["name"]["el"]
    qualifier = stop.get("qualifier")
    if not qualifier:
        return base
    extra = qualifier[lang] or qualifier["el"]
    return f"{base[:-1]}, {extra})" if base.endswith(")") else f"{base} ({extra})"


def load(name: str):
    return json.loads((ASSETS / name).read_text(encoding="utf-8"))


def build(start: date, days: int) -> dict[str, list[dict]]:
    lines, stops = load("lines.json"), load("stops.json")
    timetables, meta = load("timetables.json"), load("meta.json")
    stop_by_id = {s["id"]: s for s in stops}

    def departures(line_id: str, dir_id: str, service: str, season_name: str) -> list[str]:
        """Departures from the first stop, falling back to winter and from Wednesday to weekday, like Schedule.departuresFor"""
        for svc in (service, "weekday") if service == "wednesday" else (service,):
            for wanted in (season_name, "winter"):
                for tt in timetables:
                    if (tt["line"], tt["direction"], tt["service"], tt["season"]) == (line_id, dir_id, svc, wanted):
                        return sorted(tt["departures"])
        return []

    has_wednesday = any(tt["service"] == "wednesday" for tt in timetables)

    # Summer is its own service only for a service day that has a summer timetable somewhere
    has_summer = {svc for svc in SERVICE_DAYS
                  if any(tt["service"] == svc and tt["season"] == "summer" for tt in timetables)}

    def service_id(day: date) -> str:
        svc = service_day(day, meta["holidays"], has_wednesday)
        sea = season(day, meta["summer"]) if svc in has_summer else "winter"
        return f"{svc}-{sea}"

    end = start + timedelta(days=days - 1)
    calendar_dates, used_services = [], set()
    day = start
    while day <= end:
        sid = service_id(day)
        calendar_dates.append({"service_id": sid, "date": day.strftime("%Y%m%d"), "exception_type": 1})
        used_services.add(sid)
        day += timedelta(days=1)

    routes, trips, stop_times, shapes, used_stops = [], [], [], [], set()
    for line in lines:
        line_has_trips = False
        for direction in line["directions"]:
            dir_id = direction["id"]
            shape_id = f"{line['id']}-{dir_id}" if direction.get("shape") else ""
            direction_trips = 0
            for sid in sorted(used_services):
                svc, sea = sid.split("-")
                for dep in departures(line["id"], dir_id, svc, sea):
                    hh, mm = map(int, dep.split(":"))
                    first = hh * 60 + mm
                    trip_id = f"{line['id']}-{dir_id}-{sid}-{dep.replace(':', '')}"
                    trips.append({
                        "route_id": line["id"],
                        "service_id": sid,
                        "trip_id": trip_id,
                        "trip_headsign": direction["headsign"]["el"],
                        "direction_id": DIRECTION_IDS.get(dir_id, ""),
                        "shape_id": shape_id,
                    })
                    for seq, ds in enumerate(direction["stops"], start=1):
                        t = gtfs_time(first + ds["offset"])
                        stop_times.append({
                            "trip_id": trip_id,
                            "arrival_time": t,
                            "departure_time": t,
                            "stop_id": ds["stop"],
                            "stop_sequence": seq,
                            "timepoint": 1 if seq == 1 else 0,
                        })
                        used_stops.add(ds["stop"])
                    direction_trips += 1
            if direction_trips and shape_id:
                for seq, (lat, lng) in enumerate(decode_polyline(direction["shape"]), start=1):
                    shapes.append({"shape_id": shape_id, "shape_pt_lat": f"{lat:.5f}",
                                   "shape_pt_lon": f"{lng:.5f}", "shape_pt_sequence": seq})
            line_has_trips |= direction_trips > 0
        if line_has_trips:
            routes.append({
                "route_id": line["id"],
                "agency_id": AGENCY["agency_id"],
                "route_short_name": line["number"],
                "route_long_name": line["name"]["el"],
                "route_type": BUS,
                "route_color": line["color"].lstrip("#").upper(),
                "route_text_color": line["textColor"].lstrip("#").upper(),
            })

    gtfs_stops, translations = [], []
    for sid in sorted(used_stops):
        stop = stop_by_id[sid]
        gtfs_stops.append({
            "stop_id": sid,
            "stop_code": sid,
            "stop_name": stop_name(stop, "el"),
            "stop_lat": f"{stop['lat']:.6f}",
            "stop_lon": f"{stop['lng']:.6f}",
        })
        english = stop_name(stop, "en")
        if english != stop_name(stop, "el"):
            translations.append({"table_name": "stops", "field_name": "stop_name", "language": "en",
                                 "translation": english, "record_id": sid})
    for line in lines:
        if any(r["route_id"] == line["id"] for r in routes) and line["name"]["en"]:
            translations.append({"table_name": "routes", "field_name": "route_long_name", "language": "en",
                                 "translation": line["name"]["en"], "record_id": line["id"]})

    return {
        "agency.txt": [AGENCY],
        "stops.txt": gtfs_stops,
        "routes.txt": routes,
        "trips.txt": trips,
        "stop_times.txt": stop_times,
        "calendar_dates.txt": calendar_dates,
        "shapes.txt": shapes,
        "translations.txt": translations,
        "feed_info.txt": [{
            "feed_publisher_name": PUBLISHER["name"],
            "feed_publisher_url": PUBLISHER["url"],
            "feed_lang": "el",
            "feed_start_date": start.strftime("%Y%m%d"),
            "feed_end_date": end.strftime("%Y%m%d"),
            "feed_version": f"{meta['updated']}_{start.strftime('%Y%m%d')}",
        }],
    }


def write_zip(tables: dict[str, list[dict]], out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for name, rows in tables.items():
            if not rows:
                continue
            buf = io.StringIO()
            writer = csv.DictWriter(buf, fieldnames=list(rows[0].keys()), lineterminator="\n")
            writer.writeheader()
            writer.writerows(rows)
            z.writestr(name, buf.getvalue())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=Path("gtfs.zip"))
    parser.add_argument("--start", type=date.fromisoformat, default=date.today(), help="first service date, YYYY-MM-DD")
    parser.add_argument("--days", type=int, default=365, help="how many days the feed covers")
    args = parser.parse_args()
    tables = build(args.start, args.days)
    write_zip(tables, args.out)
    counts = ", ".join(f"{len(rows)} {name[:-4]}" for name, rows in tables.items() if name != "feed_info.txt")
    print(f"{args.out}: {counts}")


if __name__ == "__main__":
    main()
