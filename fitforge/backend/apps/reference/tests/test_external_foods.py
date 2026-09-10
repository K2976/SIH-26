import json
from decimal import Decimal

import pytest
from django.core.management import CommandError, call_command

from apps.reference import external_foods
from apps.reference.external_foods import (
    ExternalFoodError,
    UsdaConfigurationError,
    fetch_open_food_facts_food,
    fetch_usda_fdc_food,
    require_usda_api_key,
)
from apps.reference.management.commands.seed_external_foods import DEFAULT_MANIFEST_PATH
from apps.reference.management.commands.seed_foods import DEFAULT_SEED_PATH as IFCT_SEED_PATH
from apps.reference.models import FoodItem


def _off_spec():
    return {
        "slug": "off-test-biscuit",
        "name": "Test Biscuit",
        "barcode": "12345678",
        "required_product_name_terms": ["test", "biscuit"],
        "expected_portion_g": "12.50",
        "portion_label": "1 biscuit ~12.5g",
        "portion_reason": "The label defines one biscuit as 12.5 g.",
    }


def _usda_spec():
    return {
        "slug": "usda-test-grain",
        "name": "Test grain, cooked",
        "fdc_id": 42,
        "data_types": ["Foundation"],
        "required_description_terms": ["test", "grain", "cooked"],
        "portion_match_terms": [["cup"]],
        "portion_label": "1 cup cooked ~{grams}g",
        "portion_reason": "FDC supplies the gram weight for one cooked cup.",
    }


def test_usda_key_is_required_and_never_defaulted(monkeypatch):
    monkeypatch.delenv("USDA_FDC_API_KEY", raising=False)

    with pytest.raises(UsdaConfigurationError, match="USDA_FDC_API_KEY is not configured"):
        require_usda_api_key()


def test_open_food_facts_uses_only_complete_per_100g_data(monkeypatch):
    monkeypatch.setattr(
        external_foods,
        "_request_json",
        lambda request, provider: {
            "status": "success",
            "product": {
                "product_name": "Test biscuit",
                "serving_quantity": 12.5,
                "serving_quantity_unit": "g",
                "nutriments": {
                    "energy-kcal_100g": 480,
                    "proteins_100g": 6.2,
                    "carbohydrates_100g": 68,
                    "fat_100g": 20.1,
                },
            },
        },
    )

    row = fetch_open_food_facts_food(_off_spec())

    assert row["source"] == "open_food_facts"
    assert row["barcode"] == "12345678"
    assert row["default_portion_g"] == Decimal("12.50")
    assert row["portion_label"] == "1 biscuit ~12.5g"
    assert row["kcal_per_100g"] == Decimal("480.00")


def test_open_food_facts_rejects_missing_macros(monkeypatch):
    monkeypatch.setattr(
        external_foods,
        "_request_json",
        lambda request, provider: {
            "status": "success",
            "product": {
                "product_name": "Test biscuit",
                "serving_quantity": 12.5,
                "serving_quantity_unit": "g",
                "nutriments": {
                    "energy-kcal_100g": 480,
                    "proteins_100g": 6.2,
                    "fat_100g": 20.1,
                },
            },
        },
    )

    with pytest.raises(ExternalFoodError, match="carbohydrate"):
        fetch_open_food_facts_food(_off_spec())


def test_usda_macros_and_default_portion_come_from_matched_fdc_record(monkeypatch):
    response = {
        "fdcId": 42,
        "description": "Test grain, cooked",
        "dataType": "Foundation",
        "foodNutrients": [
            {"nutrient": {"id": 1008}, "amount": 120},
            {"nutrient": {"id": 1003}, "amount": 4.4},
            {"nutrient": {"id": 1005}, "amount": 21.3},
            {"nutrient": {"id": 1004}, "amount": 2.1},
        ],
        "foodPortions": [
            {
                "gramWeight": 185,
                "amount": 1,
                "modifier": "cooked",
                "measureUnit": {"name": "cup", "abbreviation": "cup"},
            }
        ],
    }
    monkeypatch.setattr(external_foods, "_request_json", lambda request, provider: response)

    row = fetch_usda_fdc_food(_usda_spec(), "not-a-real-test-key")

    assert row["source"] == "usda_fdc"
    assert row["barcode"] is None
    assert row["default_portion_g"] == Decimal("185.00")
    assert row["portion_label"] == "1 cup cooked ~185g"
    assert row["protein_g"] == Decimal("4.40")


def test_manifest_documents_every_portion_and_source():
    manifest = json.loads(DEFAULT_MANIFEST_PATH.read_text())
    ifct_names = {
        row["name"]["en"].casefold() for row in json.loads(IFCT_SEED_PATH.read_text())["foods"]
    }

    assert len(manifest["open_food_facts"]) >= 8
    assert len(manifest["open_food_facts"]) == manifest["metadata"]["open_food_facts_rows"]
    assert len(manifest["usda_fdc"]) >= 4
    assert len(manifest["usda_fdc"]) == manifest["metadata"]["usda_fdc_rows"]
    assert all(row["portion_reason"] for row in manifest["open_food_facts"])
    assert all(row["portion_reason"] for row in manifest["usda_fdc"])
    assert all(row["barcode"].isdigit() for row in manifest["open_food_facts"])
    assert all(isinstance(row["fdc_id"], int) for row in manifest["usda_fdc"])
    assert all(row["name"].casefold() not in ifct_names for row in manifest["usda_fdc"])


@pytest.mark.django_db
def test_seed_command_is_idempotent_searchable_and_barcode_lookupable(monkeypatch):
    row = {
        "slug": "off-test-biscuit",
        "name": {"en": "Test Biscuit"},
        "kcal_per_100g": Decimal("480.00"),
        "protein_g": Decimal("6.20"),
        "carbs_g": Decimal("68.00"),
        "fat_g": Decimal("20.10"),
        "default_portion_g": Decimal("12.50"),
        "portion_label": "1 biscuit ~12.5g",
        "source": "open_food_facts",
        "barcode": "12345678",
        "is_active": True,
    }
    monkeypatch.setattr(
        "apps.reference.management.commands.seed_external_foods.fetch_external_foods",
        lambda path, sources: [row],
    )

    call_command("seed_external_foods", source="open_food_facts")
    version = FoodItem.objects.get(slug="off-test-biscuit").updated_at_version
    call_command("seed_external_foods", source="open_food_facts")

    assert FoodItem.objects.count() == 1
    assert FoodItem.objects.get(barcode="12345678").slug == "off-test-biscuit"
    assert FoodItem.objects.filter(name__en__icontains="biscuit").exists()
    assert FoodItem.objects.get().updated_at_version == version


@pytest.mark.django_db
def test_default_seed_fails_before_writing_when_usda_key_is_missing(monkeypatch):
    monkeypatch.delenv("USDA_FDC_API_KEY", raising=False)

    with pytest.raises(CommandError, match="USDA_FDC_API_KEY is not configured"):
        call_command("seed_external_foods")

    assert FoodItem.objects.count() == 0
