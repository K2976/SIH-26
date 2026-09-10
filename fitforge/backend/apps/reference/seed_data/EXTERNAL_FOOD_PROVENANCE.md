# External food catalog provenance

`external_foods.json` is a curated request manifest, not a scraped nutrition
export. `seed_external_foods` resolves every entry from its official API before
performing one atomic database update.

## Open Food Facts

- Endpoint: `GET https://world.openfoodfacts.org/api/v3/product/{barcode}`
- Record key: the product barcode stored on `FoodItem.barcode`
- Stored source: `open_food_facts`
- License: the Open Food Facts database is offered under the Open Database
  License (ODbL); individual database contents are under the Database Contents
  License. No product images are requested or stored.
- Official documentation:
  https://openfoodfacts.github.io/openfoodfacts-server/api/
- Official licensing guidance:
  https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/

The seed rejects products without all four required per-100-g values, without a
gram-based manufacturer serving, or whose pinned serving/identity has changed.
The manifest's `portion_reason` explains the user-facing adjustment unit for
each barcode.

## USDA FoodData Central

- Endpoint: `GET https://api.nal.usda.gov/fdc/v1/food/{fdc_id}`
- Record key: the pinned `fdc_id` in `external_foods.json`
- Stored source: `usda_fdc`
- License: FoodData Central data is CC0/public domain; USDA requests attribution.
- Official API guide and terms: https://fdc.nal.usda.gov/api-guide/
- Official data-type documentation: https://fdc.nal.usda.gov/data-documentation/

The committed IDs are generic foods absent by name from the IFCT seed and are
validated against their expected data type and description. Macros are read by
USDA nutrient ID and the default portion uses the selected FDC household
measure's `gramWeight`. Missing nutrients or portions fail the complete seed.

`USDA_FDC_API_KEY` is required in the process environment. It is sent only to
the USDA endpoint and is not written to the database, manifest, logs, or error
messages.
