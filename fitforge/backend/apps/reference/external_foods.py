"""Validated adapters for licensed public food-catalog APIs.

These adapters only accept nutrition reported per 100 g by the upstream
source. They deliberately reject missing macros and non-mass portions rather
than manufacturing a value that the source did not provide.
"""

import json
import os
import re
from decimal import ROUND_HALF_UP, Decimal, InvalidOperation
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

OPEN_FOOD_FACTS_PRODUCT_URL = "https://world.openfoodfacts.org/api/v3/product/{barcode}"
USDA_FDC_URL = "https://api.nal.usda.gov/fdc/v1"
USER_AGENT = "FitForge/1.0 (https://github.com/K2976/SIH-26)"
TWO_PLACES = Decimal("0.01")


class ExternalFoodError(RuntimeError):
    """An upstream record could not be safely converted into a FoodItem."""


class UsdaConfigurationError(ExternalFoodError):
    """USDA FoodData Central is selected but its API key is unavailable."""


def require_usda_api_key(environ=None) -> str:
    environ = os.environ if environ is None else environ
    api_key = environ.get("USDA_FDC_API_KEY", "").strip()
    if not api_key:
        raise UsdaConfigurationError(
            "USDA_FDC_API_KEY is not configured. Add a FoodData Central API key "
            "to the environment before running seed_external_foods."
        )
    return api_key


def _request_json(request: Request, provider: str) -> dict:
    try:
        with urlopen(request, timeout=30) as response:  # noqa: S310 - fixed HTTPS hosts
            return json.loads(response.read())
    except (HTTPError, URLError, TimeoutError, json.JSONDecodeError):
        # Do not interpolate the request URL: USDA authenticates in its query
        # string, so doing so could disclose the API key in logs.
        raise ExternalFoodError(
            f"{provider} API request failed; no external food rows were written."
        ) from None


def _decimal(value, field: str) -> Decimal:
    try:
        number = Decimal(str(value))
    except (InvalidOperation, TypeError, ValueError):
        raise ExternalFoodError(f"Upstream record has no valid {field} value.") from None
    if not number.is_finite() or number < 0:
        raise ExternalFoodError(f"Upstream record has an invalid {field} value.")
    return number.quantize(TWO_PLACES, rounding=ROUND_HALF_UP)


def _positive_decimal(value, field: str) -> Decimal:
    number = _decimal(value, field)
    if number <= 0:
        raise ExternalFoodError(f"Upstream record has a non-positive {field} value.")
    return number


def _display_decimal(value: Decimal) -> str:
    return format(value.normalize(), "f")


def _validate_spec(spec: dict) -> None:
    required = {"slug", "name", "portion_label", "portion_reason"}
    missing = sorted(required - spec.keys())
    if missing:
        raise ExternalFoodError(f"External food manifest entry is missing: {', '.join(missing)}")


def fetch_open_food_facts_food(spec: dict) -> dict:
    """Resolve one curated barcode through the official Open Food Facts API."""

    _validate_spec(spec)
    barcode = str(spec.get("barcode", "")).strip()
    if not re.fullmatch(r"\d{8,14}", barcode):
        raise ExternalFoodError(f"Invalid Open Food Facts barcode for {spec['slug']}.")

    fields = ",".join(
        (
            "code",
            "product_name",
            "brands",
            "nutriments",
            "serving_quantity",
            "serving_quantity_unit",
            "serving_size",
        )
    )
    url = OPEN_FOOD_FACTS_PRODUCT_URL.format(barcode=barcode) + "?" + urlencode({"fields": fields})
    payload = _request_json(
        Request(url, headers={"Accept": "application/json", "User-Agent": USER_AGENT}),
        "Open Food Facts",
    )
    if not str(payload.get("status", "")).startswith("success"):
        raise ExternalFoodError(f"Open Food Facts has no current product for {barcode}.")

    product = payload.get("product") or {}
    product_name = str(product.get("product_name", ""))
    expected_terms = spec.get("required_product_name_terms", [])
    if any(term.casefold() not in product_name.casefold() for term in expected_terms):
        raise ExternalFoodError(f"Open Food Facts product identity changed for {barcode}.")

    serving_unit = str(product.get("serving_quantity_unit", "")).casefold()
    if serving_unit not in {"g", "gram", "grams"}:
        raise ExternalFoodError(
            f"Open Food Facts product {barcode} lacks a gram-based label serving."
        )
    portion = _positive_decimal(product.get("serving_quantity"), "serving quantity")
    expected_portion = _positive_decimal(spec.get("expected_portion_g"), "expected portion")
    if portion != expected_portion:
        raise ExternalFoodError(
            f"Open Food Facts serving changed for {barcode}; review the documented portion."
        )

    nutriments = product.get("nutriments") or {}
    return {
        "slug": spec["slug"],
        "name": {"en": spec["name"]},
        "kcal_per_100g": _decimal(nutriments.get("energy-kcal_100g"), "energy"),
        "protein_g": _decimal(nutriments.get("proteins_100g"), "protein"),
        "carbs_g": _decimal(nutriments.get("carbohydrates_100g"), "carbohydrate"),
        "fat_g": _decimal(nutriments.get("fat_100g"), "fat"),
        "default_portion_g": portion,
        "portion_label": spec["portion_label"],
        "source": "open_food_facts",
        "barcode": barcode,
        "is_active": True,
    }


def _usda_request(path: str, api_key: str) -> dict:
    url = f"{USDA_FDC_URL}/{path}?{urlencode({'api_key': api_key})}"
    headers = {"Accept": "application/json", "User-Agent": USER_AGENT}
    return _request_json(Request(url, headers=headers), "USDA FoodData Central")


def _matches_usda_food(food: dict, spec: dict) -> bool:
    if food.get("dataType") not in spec["data_types"]:
        return False
    description = str(food.get("description", "")).casefold()
    return all(term.casefold() in description for term in spec["required_description_terms"])


def _nutrient_amount(food: dict, nutrient_id: int, field: str) -> Decimal:
    for row in food.get("foodNutrients", []):
        nutrient = row.get("nutrient") or {}
        row_id = nutrient.get("id", row.get("nutrientId"))
        if row_id == nutrient_id:
            return _decimal(row.get("amount", row.get("value")), field)
    raise ExternalFoodError(f"USDA FoodData Central record has no {field} value per 100 g.")


def _usda_portion(food: dict, spec: dict) -> Decimal:
    # Portion policy: a packaged row uses its manufacturer label serving;
    # a generic row uses a household measure published on its pinned FDC
    # record. Every curated item explains its chosen adjustment unit in the
    # manifest's portion_reason. We never substitute a generic gram guess.
    for alternative in spec["portion_match_terms"]:
        for portion in food.get("foodPortions", []):
            measure = portion.get("measureUnit") or {}
            searchable = " ".join(
                str(value)
                for value in (
                    portion.get("modifier", ""),
                    portion.get("portionDescription", ""),
                    measure.get("name", ""),
                    measure.get("abbreviation", ""),
                )
            ).casefold()
            if all(term.casefold() in searchable for term in alternative):
                return _positive_decimal(portion.get("gramWeight"), "household portion weight")
    raise ExternalFoodError(
        f"USDA FoodData Central has no requested household portion for {spec['slug']}."
    )


def fetch_usda_fdc_food(spec: dict, api_key: str) -> dict:
    """Resolve one curated generic food and its household portion through FDC."""

    _validate_spec(spec)
    fdc_id = spec.get("fdc_id")
    if not isinstance(fdc_id, int) or fdc_id <= 0:
        raise ExternalFoodError(f"External food manifest has no valid FDC id for {spec['slug']}.")
    food = _usda_request(f"food/{fdc_id}", api_key)
    if food.get("fdcId") != fdc_id or not _matches_usda_food(food, spec):
        raise ExternalFoodError(f"USDA FoodData Central detail mismatch for {spec['slug']}.")

    portion = _usda_portion(food, spec)
    nutrition = {
        # FDC nutrient ids: 1008 energy (kcal), 1003 protein, 1005
        # carbohydrate by difference, and 1004 total lipid, all per 100 g.
        "kcal_per_100g": _nutrient_amount(food, 1008, "energy"),
        "protein_g": _nutrient_amount(food, 1003, "protein"),
        "carbs_g": _nutrient_amount(food, 1005, "carbohydrate"),
        "fat_g": _nutrient_amount(food, 1004, "fat"),
    }
    return {
        "slug": spec["slug"],
        "name": {"en": spec["name"]},
        **nutrition,
        "default_portion_g": portion,
        "portion_label": spec["portion_label"].format(grams=_display_decimal(portion)),
        "source": "usda_fdc",
        "barcode": None,
        "is_active": True,
    }


def fetch_external_foods(manifest_path: Path, sources: set[str]) -> list[dict]:
    """Fetch and validate every selected row before callers mutate the database."""

    manifest = json.loads(manifest_path.read_text())
    unknown = sources - {"open_food_facts", "usda_fdc"}
    if unknown:
        raise ExternalFoodError(f"Unknown external food source: {', '.join(sorted(unknown))}")

    # Check configuration first so an all-source run fails immediately and
    # predictably, before making unrelated network requests.
    usda_key = require_usda_api_key() if "usda_fdc" in sources else None
    rows = []
    if "open_food_facts" in sources:
        rows.extend(fetch_open_food_facts_food(spec) for spec in manifest["open_food_facts"])
    if "usda_fdc" in sources:
        rows.extend(fetch_usda_fdc_food(spec, usda_key) for spec in manifest["usda_fdc"])
    return rows
