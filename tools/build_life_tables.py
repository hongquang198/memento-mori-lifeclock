"""Extracts the life-table subset the app ships from the UN WPP 2024 abridged table.

Why a script and not a checked-in blob: the full file is 138 MB and changes with every WPP
revision. The committed asset (~250 KB) must be reproducible from a citation, so anyone can
re-derive it and compare. `docs/Methodology.md` records the URL, the revision and these filters.

Source (medium variant, 1950-2023):
    https://population.un.org/wpp/assets/Excel%20Files/1_Indicator%20(Standard)/CSV_FILES/WPP2024_Life_Table_Abridged_Medium_1950-2023.csv.gz

Usage:
    python tools/build_life_tables.py <input.csv.gz> core/mortality/src/main/assets/life_tables.csv
"""

import csv
import gzip
import sys
from pathlib import Path

# Latest period in the 2024 revision. Pinned, never "the newest row": a silent source refresh must
# not change a user's number (see docs/Methodology.md, "vintages").
PERIOD = "2023"

# LocTypeID 4 = country. Regions (2), subregions (3) and the world (1) are aggregates and must not
# appear as if they were a place a person can live.
COUNTRY = "4"

SEXES = {"Male": "M", "Female": "F"}


def main(source: str, destination: str) -> None:
    rows: list[tuple[str, str, int, str]] = []
    with gzip.open(source, "rt", encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            if row["Time"] != PERIOD or row["LocTypeID"] != COUNTRY:
                continue
            sex = SEXES.get(row["Sex"])
            iso3 = row["ISO3_code"]
            if not sex or not iso3:
                continue
            rows.append((iso3, sex, int(row["AgeGrpStart"]), row["ex"]))

    rows.sort()
    target = Path(destination)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["iso3", "sex", "age", "ex"])
        writer.writerows(rows)

    countries = len({row[0] for row in rows})
    print(f"{len(rows)} rows, {countries} countries -> {target} ({target.stat().st_size} bytes)")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
