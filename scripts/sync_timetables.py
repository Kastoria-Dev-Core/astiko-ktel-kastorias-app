"""Keep timetables.json the same as the timetables on astikoktelkastorias.gr.

Every line has a page on the operator's site with its departures in HTML tables:
one table per direction (the return one says "Επιστροφή"), or one table whose
columns say which direction they are. The columns are the kinds of day:
Καθημερινές, Τετάρτη, Σάββατο, Κυριακή. A "-" means no trip.

This reads those pages and compares them with timetables.json:

- nothing changed: does nothing
- a normal change (a trip added, moved or dropped): writes timetables.json and
  sets the date in meta.json
- anything that looks wrong: a page that can't be read, a cell that isn't a
  time, a timetable that disappears, or more than half of one changing at once.
  Then nothing is written and the report says why, so a person can look. Run it
  with --force to accept such a change after checking it.

A Wednesday column is kept only when it differs from the weekday one (line 10
has an extra trip on Wednesdays). Tables for other routes on a line's page,
like line 6's Sunday trips from Ύδρα, are left out and listed in the report.
The site shows one timetable for the whole year, stored as the winter one; the
app uses it all year unless a line also has a summer one.

    python scripts/sync_timetables.py               # check only, print the report
    python scripts/sync_timetables.py --write       # also apply a normal change
    python scripts/sync_timetables.py --dump        # show what was read from each page

.github/workflows/sync-timetables.yml runs it every morning.
"""

import argparse
import json
import os
import re
import sys
import time
import unicodedata
import urllib.request
from datetime import datetime
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import quote
from zoneinfo import ZoneInfo

ASSETS = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
SITE = "https://astikoktelkastorias.gr/"
USER_AGENT = "astiko-ktel-kastorias-app timetable sync (github.com/Kastoria-Dev-Core/astiko-ktel-kastorias-app)"

# The page of each line on the operator's site, from astikoktelkastorias.gr/δρομολόγια
PAGES = {
    "01": "μεσοποταμία",
    "02": "1050-2",
    "03": "κορομηλιά",
    "04": "πανεπιστήμιο",
    "06": "μανιάκοι",
    "07": "εργατικές",
    "08": "1084-2",
    "09": "ομόνοια-νοσοκομείο",
    "10": "καλοχώρι",
}
DAYS = (("ΚΑΘΗΜΕΡ", "weekday"), ("ΤΕΤΑΡΤ", "wednesday"), ("ΣΑΒΒΑΤ", "saturday"), ("ΚΥΡΙΑΚ", "sunday"))
RETURN = "ΕΠΙΣΤΡΟΦ"
SEASON = "winter"
TIME = re.compile(r"(\d{1,2})\s*[:.]\s*(\d{2})")
EMPTY = {"", "-", "–", "—", "−"}
# A change bigger than this share of a timetable needs a person to look
MAX_CHANGE = 0.5


def norm(text: str) -> str:
    """Upper case without accents, for matching words like Τετάρτη"""
    decomposed = unicodedata.normalize("NFD", text)
    return "".join(c for c in decomposed if not unicodedata.combining(c)).upper()


def letters(text: str) -> str:
    """Only the letters and digits, for comparing titles that differ in dashes and spaces"""
    return re.sub(r"[\W_]+", "", norm(text))


class PageParser(HTMLParser):
    """The page as a list of text blocks and tables, in order"""

    BLOCKS = {"p", "h1", "h2", "h3", "h4", "h5", "h6", "figcaption", "li", "div", "caption", "section", "header"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.items: list[tuple] = []
        self._text: list[str] = []
        self._block = ""
        self._skip = 0
        self._depth = 0
        self._rows: list[list[str]] = []
        self._row: list[str] | None = None
        self._cell: list[str] | None = None
        self._span = 1

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style", "noscript"):
            self._skip += 1
            return
        if tag == "table":
            if self._depth == 0:
                self._flush()
                self._rows = []
            self._depth += 1
            return
        if self._depth:
            if tag == "tr":
                self._row = []
            elif tag in ("td", "th"):
                self._cell = []
                span = dict(attrs).get("colspan") or "1"
                self._span = int(span) if span.isdigit() else 1
            elif tag == "br" and self._cell is not None:
                self._cell.append(" ")
            return
        if tag in self.BLOCKS or tag == "br":
            self._flush()
            if tag in self.BLOCKS:
                self._block = tag

    def handle_endtag(self, tag):
        if tag in ("script", "style", "noscript"):
            self._skip = max(0, self._skip - 1)
            return
        if self._depth:
            if tag in ("td", "th") and self._cell is not None and self._row is not None:
                text = " ".join("".join(self._cell).split())
                self._row.extend([text] * self._span)
                self._cell = None
            elif tag == "tr" and self._row is not None:
                self._rows.append(self._row)
                self._row = None
            elif tag == "table":
                self._depth -= 1
                if self._depth == 0:
                    self.items.append(("table", self._rows))
            return
        if tag in self.BLOCKS:
            self._flush()

    def handle_data(self, data):
        if self._skip:
            return
        if self._depth:
            if self._cell is not None:
                self._cell.append(data)
            return
        self._text.append(data)

    def _flush(self):
        text = " ".join("".join(self._text).split())
        if text:
            self.items.append(("text", text, self._block))
        self._text = []


def day_of(cell: str) -> str | None:
    n = norm(cell)
    return next((day for key, day in DAYS if key in n), None)


def tables_of(items: list[tuple]) -> list[dict]:
    """The timetable tables of a page, each with its title and its day columns"""
    tables = []
    for i, item in enumerate(items):
        if item[0] != "table":
            continue
        rows = item[1]
        header = next((r for r, row in enumerate(rows) if any(day_of(c) for c in row)), None)
        if header is None:
            continue
        title = []
        # The heading just above the table, unless another table is in between
        for prev in reversed(items[:i]):
            if prev[0] == "table":
                break
            title.append(prev[1])
            break
        if i + 1 < len(items) and items[i + 1][0] == "text" and items[i + 1][2] == "figcaption":
            title.append(items[i + 1][1])
        title += [" ".join(c for c in row if c) for row in rows[:header]]
        title_text = " ".join(t for t in title if t)
        columns = [(c, day_of(cell), RETURN in norm(cell)) for c, cell in enumerate(rows[header]) if day_of(cell)]
        tables.append({"title": title_text, "returns": RETURN in norm(title_text), "columns": columns,
                       "rows": rows[header + 1:]})
    return tables


def parse_cell(text: str) -> tuple[list[str], bool]:
    """The times in a cell, and whether the cell held anything that isn't a time"""
    text = text.strip()
    if text in EMPTY:
        return [], True
    times = []
    for h, m in TIME.findall(text):
        if int(h) > 23 or int(m) > 59:
            return [], False
        times.append(f"{int(h):02d}:{m}")
    rest = re.sub(r"[\s,;/|\-–—]+", "", TIME.sub("", text))
    return times, not rest


def read_line(line_id: str, html: str, report: dict) -> dict[tuple, set]:
    """(direction, day) -> departures, from one line's page"""
    parser = PageParser()
    parser.feed(html)
    tables = tables_of(parser.items)
    report["dump"].append((line_id, tables))
    if not tables:
        report["problems"].append(f"Line {line_id}: no timetable found on the page")
        return {}

    returns = [t for t in tables if t["returns"]]
    outs = [t for t in tables if not t["returns"]]
    main = outs[:1]
    if returns:
        wanted = letters(norm(returns[0]["title"]).replace("ΕΠΙΣΤΡΟΦΗ", ""))
        main = [t for t in outs if letters(t["title"]) == wanted] or outs[:1]
    for extra in outs:
        if extra not in main:
            report["notes"].append(f"Line {line_id}: left out the table \"{extra['title'] or 'without a title'}\"")

    found: dict[tuple, set] = {}
    for table in main + returns:
        for col, day, col_returns in table["columns"]:
            direction = "in" if table["returns"] or col_returns else "out"
            times = set()
            for row in table["rows"]:
                if col >= len(row):
                    continue
                values, clean = parse_cell(row[col])
                if not clean:
                    report["problems"].append(f"Line {line_id}: \"{row[col]}\" isn't a time ({direction}, {day})")
                times.update(values)
            if (direction, day) in found:
                report["problems"].append(f"Line {line_id}: two tables for {direction}, {day}")
            if times:
                found[(direction, day)] = times
    # Wednesday only when it isn't the same as the other weekdays
    for direction in ("out", "in"):
        if found.get((direction, "wednesday")) == found.get((direction, "weekday")):
            found.pop((direction, "wednesday"), None)
    if not found:
        report["problems"].append(f"Line {line_id}: the tables have no departures")
    return found


def fetch(url: str) -> str:
    last = None
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read().decode("utf-8", errors="replace")
        except OSError as e:
            last = e
            time.sleep(5 * (attempt + 1))
    raise last


def compare(old: dict, new: dict, report: dict) -> bool:
    """Lists the changes in the report; False when one of them is too big to apply on its own"""
    ok = True
    for key in sorted(set(old) | set(new)):
        before, after = old.get(key, set()), new.get(key, set())
        if before == after:
            continue
        line, direction, day = key
        name = f"Line {line} {direction}, {day}"
        added, removed = sorted(after - before), sorted(before - after)
        parts = ([f"+{', '.join(added)}"] if added else []) + ([f"-{', '.join(removed)}"] if removed else [])
        report["changes"].append(f"{name}: {'  '.join(parts)}")
        if before and not after:
            report["big"].append(f"{name}: the whole timetable is gone")
            ok = False
        elif before and len(before ^ after) > max(3, MAX_CHANGE * len(before)):
            report["big"].append(f"{name}: {len(before ^ after)} changes to a timetable of {len(before)} departures")
            ok = False
    return ok


def render(report: dict, status: str) -> str:
    out = [f"**Timetable sync: {status}**", ""]
    for title, key in (("Problems", "problems"), ("Too big to apply on its own", "big"),
                       ("Changes", "changes"), ("Notes", "notes")):
        if report[key]:
            out += [f"### {title}", *[f"- {x}" for x in report[key]], ""]
    if status == "blocked":
        out += ["Nothing was written. Check the operator's site; if the change is right, run the "
                "workflow again with **force** on.", ""]
    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--write", action="store_true", help="apply the change to the asset files")
    parser.add_argument("--force", action="store_true", help="apply even a change that looks too big")
    parser.add_argument("--report", type=Path, help="also write the report to this file")
    parser.add_argument("--dump", action="store_true", help="print the tables read from each page")
    args = parser.parse_args()

    report = {"problems": [], "big": [], "changes": [], "notes": [], "dump": []}
    timetables_path, meta_path = ASSETS / "timetables.json", ASSETS / "meta.json"
    timetables = json.loads(timetables_path.read_text(encoding="utf-8"))
    lines = {line["id"]: line for line in json.loads((ASSETS / "lines.json").read_text(encoding="utf-8"))}

    new: dict[tuple, set] = {}
    for line_id, slug in PAGES.items():
        url = SITE + quote(slug) + "/"
        try:
            html = fetch(url)
        except OSError as e:
            report["problems"].append(f"Line {line_id}: couldn't read {url} ({e})")
            continue
        directions = {d["id"] for d in lines[line_id]["directions"]}
        for (direction, day), times in read_line(line_id, html, report).items():
            if direction not in directions:
                report["problems"].append(f"Line {line_id}: lines.json has no direction \"{direction}\"")
            new[(line_id, direction, day)] = times

    synced_lines = {line for line in PAGES if any(k[0] == line for k in new)}
    old = {(t["line"], t["direction"], t["service"]): set(t["departures"])
           for t in timetables if t["line"] in synced_lines and t["season"] == SEASON}

    if args.dump:
        for line_id, tables in report["dump"]:
            print(f"== {line_id}")
            for t in tables:
                cols = ", ".join(f"{d}{' (return)' if r else ''}" for _, d, r in t["columns"])
                print(f"   {'return ' if t['returns'] else ''}\"{t['title']}\": {cols}, {len(t['rows'])} rows")

    within_limits = compare(old, new, report)
    if report["problems"]:
        status = "blocked"
    elif not report["changes"]:
        status = "unchanged"
    elif within_limits or args.force:
        status = "changed"
    else:
        status = "blocked"

    if status == "changed" and args.write:
        kept = [t for t in timetables if not (t["line"] in synced_lines and t["season"] == SEASON)]
        fresh = [{"line": line, "direction": direction, "service": day, "season": SEASON,
                  "departures": sorted(times)} for (line, direction, day), times in new.items()]
        merged = sorted(kept + fresh, key=lambda t: (t["line"], t["direction"], t["service"], t["season"]))
        timetables_path.write_text(json.dumps(merged, indent=2, ensure_ascii=False), encoding="utf-8")
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
        meta["updated"] = datetime.now(ZoneInfo("Europe/Athens")).date().isoformat()
        meta_path.write_text(json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")
    elif status == "changed":
        status = "changed (not written, run with --write)"

    text = render(report, status)
    print(text)
    if args.report:
        args.report.write_text(text, encoding="utf-8")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as f:
            f.write(f"status={status.split(' ')[0]}\n")
            f.write(f"notes={'yes' if report['notes'] else 'no'}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
