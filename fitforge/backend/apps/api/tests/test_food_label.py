import io
import json
from decimal import Decimal
from unittest.mock import patch

import pytest
from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.urls import reverse
from rest_framework.test import APIClient

from apps.reference.models import FoodItem, Recipe, RecipeIngredient

pytestmark = pytest.mark.django_db


@pytest.fixture
def client():
    user = get_user_model().objects.create_user(username="food", password="password")
    api_client = APIClient()
    api_client.force_authenticate(user=user)
    return api_client


@pytest.fixture
def biryani():
    rice = FoodItem.objects.create(
        slug="ifct-a015",
        name={"en": "Rice, raw, milled"},
        kcal_per_100g=Decimal("356.36"),
        protein_g=Decimal("7.94"),
        carbs_g=Decimal("78.24"),
        fat_g=Decimal("0.52"),
        default_portion_g=Decimal("100.00"),
        portion_label="100 g",
        source="ifct_2017",
    )
    recipe = Recipe.objects.create(
        slug="vegetable-biryani",
        name={"en": "Vegetable biryani"},
        serving_description="1 plate, ~300g",
        serving_grams_total=Decimal("300.00"),
    )
    RecipeIngredient.objects.create(
        recipe=recipe, ingredient=rice, grams_in_serving=Decimal("80.00")
    )
    return recipe


def upload():
    return SimpleUploadedFile("meal.jpg", b"jpeg bytes", content_type="image/jpeg")


def gemini_response(labels):
    payload = {
        "candidates": [{"content": {"parts": [{"text": json.dumps({"candidates": labels})}]}}]
    }
    return io.BytesIO(json.dumps(payload).encode())


def test_requires_api_key_with_clear_setup_message(client, biryani, monkeypatch):
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    response = client.post(reverse("food-label"), {"image": upload()}, format="multipart")
    assert response.status_code == 503
    assert "GEMINI_API_KEY" in response.data["error"]
    assert ".env" in response.data["error"]


def test_returns_recipe_names_from_the_catalog(client, biryani, monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY", "test-key")
    with patch(
        "apps.reference.food_label.urllib.request.urlopen",
        return_value=gemini_response(["Vegetable biryani", "invented food"]),
    ):
        response = client.post(reverse("food-label"), {"image": upload()}, format="multipart")

    assert response.status_code == 200
    assert response.data == {"candidates": ["Vegetable biryani"]}


def test_uncertain_label_returns_empty_candidates_for_search_fallback(client, biryani, monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY", "test-key")
    with patch(
        "apps.reference.food_label.urllib.request.urlopen",
        return_value=gemini_response([]),
    ):
        response = client.post(reverse("food-label"), {"image": upload()}, format="multipart")
    assert response.data == {"candidates": []}


def test_requires_authentication():
    response = APIClient().post(reverse("food-label"), {"image": upload()}, format="multipart")
    assert response.status_code == 401
