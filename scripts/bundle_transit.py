"""Put the four asset files together into one transit.json for the app to download.

The app (data/TransitRepository.kt) checks this address when it starts and uses a newer
copy from the next start, so a timetable change reaches users without a release.
.github/workflows/docs.yml publishes it on GitHub Pages with the docs.

    python scripts/bundle_transit.py --out _site/data/transit.json

SCHEMA goes up only when the format changes in a way older apps can't read; they then
keep the data they have. It must match TransitBundle.SCHEMA in the app.
"""

import argparse
import json
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
SCHEMA = 1


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=Path("transit.json"))
    args = parser.parse_args()
    bundle = {"schema": SCHEMA}
    for name in ("stops", "lines", "timetables", "meta"):
        bundle[name] = json.loads((ASSETS / f"{name}.json").read_text(encoding="utf-8"))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(bundle, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{args.out}: {args.out.stat().st_size // 1024} KB, data of {bundle['meta']['updated']}")


if __name__ == "__main__":
    main()
