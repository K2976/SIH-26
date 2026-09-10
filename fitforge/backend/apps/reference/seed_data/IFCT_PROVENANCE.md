# IFCT 2017 ingredient seed provenance

`foods.json` is a deterministic projection of the full composition table in
`@ifct2017/compositions@2.0.9`, archived by Zenodo as DOI
`10.5281/zenodo.7088653` and published under the MIT license copied alongside
this file.

- Downloaded npm tarball SHA-256:
  `623e31d4ea355cc829429c2d8e776fbdb5eba953ea713bad2b01c962d3e2cfd5`
- Extracted `index.csv` SHA-256:
  `678c9285b01290ad5cc318a560fa5634618ebd413dd9fe3164d5539125c4aeef`
- Verified input shape: 542 unique food codes and 421 columns.
- Package documentation says 528 key foods; the CSV also includes 14 edible
  oil/fat rows, so the actual complete-file count is 542. No rows are dropped.
- `enerc` is converted from kJ to kcal using 1 kcal = 4.184 kJ.
- The 14 oil/fat rows report zero `enerc` despite approximately 100 g fat per
  100 g. Their kcal values are deterministically derived from the source
  protein/carbohydrate/fat fields with 4/4/9 factors. The importer documents
  this branch and never substitutes a hand-entered nutrition value.
- Only fields represented by the app's Ingredient/FoodItem schema are emitted;
  the importer refuses inputs that do not have the verified full 421-column
  shape, preventing use of the older partial export.

Regenerate from the unpacked npm package:

```bash
python scripts/import_ifct2017.py \
  /path/to/package/index.csv \
  apps/reference/seed_data/foods.json
```
