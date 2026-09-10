#!/usr/bin/env python3
"""Convert the verified IFCT 2017 structured export into FitForge seed JSON.

Input is ``@ifct2017/compositions@2.0.9``'s 421-column ``index.csv``.
The package describes 528 key foods but the file actually contains 542 unique
food codes: 528 core rows plus 14 edible-oil/fat rows.  All 542 are retained.
"""

import argparse
import csv
import hashlib
import json
from decimal import ROUND_HALF_UP, Decimal
from pathlib import Path

EXPECTED_ROWS = 542
EXPECTED_COLUMNS = 421
EXPECTED_CSV_SHA256 = "678c9285b01290ad5cc318a560fa5634618ebd413dd9fe3164d5539125c4aeef"
SOURCE = "ifct_2017"
KJ_PER_KCAL = Decimal("4.184")
TWO_PLACES = Decimal("0.01")


def value(row, code):
    suffix = f"; {code}"
    column = next((name for name in row if name.endswith(suffix)), None)
    if column is None:
        raise ValueError(f"IFCT column {code!r} is missing")
    return row[column]


def kcal_per_100g(row):
    energy_kj = Decimal(value(row, "enerc"))
    if energy_kj:
        return energy_kj / KJ_PER_KCAL, "ifct_energy_kj_converted_to_kcal"

    # The 14 oil/fat rows have 0 in IFCT's energy column but ~100 g fat.
    # Derive energy only for those rows from the IFCT macros using the standard
    # 4/4/9 kcal factors. This is deterministic and avoids asserting that oil
    # has zero energy or inventing a hand-entered replacement value.
    protein = Decimal(value(row, "protcnt"))
    carbs = Decimal(value(row, "choavldf"))
    fat = Decimal(value(row, "fatce"))
    return protein * 4 + carbs * 4 + fat * 9, "ifct_macros_atwater_4_4_9"


def convert(input_path):
    checksum = hashlib.sha256(input_path.read_bytes()).hexdigest()
    if checksum != EXPECTED_CSV_SHA256:
        raise ValueError(f"Unexpected IFCT CSV SHA-256: {checksum}")
    with input_path.open(newline="", encoding="utf-8-sig") as source_file:
        reader = csv.DictReader(source_file)
        rows = list(reader)
        columns = reader.fieldnames or []
    if len(rows) != EXPECTED_ROWS or len(columns) != EXPECTED_COLUMNS:
        raise ValueError(
            f"Expected {EXPECTED_ROWS} rows/{EXPECTED_COLUMNS} columns; "
            f"found {len(rows)} rows/{len(columns)} columns"
        )

    codes = [value(row, "code") for row in rows]
    if len(set(codes)) != EXPECTED_ROWS:
        raise ValueError("IFCT food codes are not unique")

    foods = []
    energy_methods = set()
    for row in rows:
        calories, method = kcal_per_100g(row)
        energy_methods.add(method)
        foods.append(
            {
                "slug": f"ifct-{value(row, 'code').lower()}",
                "name": {"en": value(row, "name")},
                "kcal_per_100g": str(calories.quantize(TWO_PLACES, rounding=ROUND_HALF_UP)),
                "protein_g": value(row, "protcnt"),
                "carbs_g": value(row, "choavldf"),
                "fat_g": value(row, "fatce"),
                "default_portion_g": "100.00",
                "portion_label": "100 g",
                "source": SOURCE,
                "barcode": None,
                "is_active": True,
            }
        )

    return {
        "metadata": {
            "source": SOURCE,
            "source_package": "@ifct2017/compositions@2.0.9",
            "source_rows": EXPECTED_ROWS,
            "source_columns": EXPECTED_COLUMNS,
            "source_csv_sha256": EXPECTED_CSV_SHA256,
            "license": "MIT",
            "energy_methods": sorted(energy_methods),
        },
        "foods": foods,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.write_text(json.dumps(convert(args.input), indent=2) + "\n")


if __name__ == "__main__":
    main()
