import json
from decimal import Decimal
from pathlib import Path

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

from apps.reference.models import FoodItem, Recipe, RecipeIngredient

DEFAULT_SEED_PATH = Path(__file__).resolve().parent.parent.parent / "seed_data" / "recipes.json"
RECIPE_FIELDS = {"name", "serving_description", "serving_grams_total", "is_active"}


class Command(BaseCommand):
    help = "Load composed recipes and their ingredient gram weights from JSON."

    def add_arguments(self, parser):
        parser.add_argument("--file", default=str(DEFAULT_SEED_PATH))

    @transaction.atomic
    def handle(self, *args, **options):
        data = json.loads(Path(options["file"]).read_text())
        created = updated = unchanged = 0
        for row in data["recipes"]:
            serving_grams = Decimal(str(row["serving_grams_total"]))
            if serving_grams <= 0:
                raise CommandError(f"{row['slug']}: serving_grams_total must be positive")
            component_specs = row["ingredients"]
            if not component_specs:
                raise CommandError(f"{row['slug']}: at least one ingredient is required")

            ingredient_slugs = [component[0] for component in component_specs]
            ingredients = {
                ingredient.slug: ingredient
                for ingredient in FoodItem.objects.filter(slug__in=ingredient_slugs)
            }
            missing = sorted(set(ingredient_slugs) - ingredients.keys())
            if missing:
                raise CommandError(f"{row['slug']}: unknown ingredients: {', '.join(missing)}")

            fields = {
                key: Decimal(str(value)) if key == "serving_grams_total" else value
                for key, value in row.items()
                if key in RECIPE_FIELDS
            }
            recipe = Recipe.objects.filter(slug=row["slug"]).first()
            recipe_changed = False
            was_created = recipe is None
            if recipe is None:
                recipe = Recipe.objects.create(slug=row["slug"], **fields)
                created += 1
            else:
                for field, value in fields.items():
                    if getattr(recipe, field) != value:
                        setattr(recipe, field, value)
                        recipe_changed = True
                if recipe_changed:
                    recipe.save()

            existing = {item.ingredient.slug: item for item in recipe.ingredients.all()}
            composition_changed = False
            for ingredient_slug, raw_grams in component_specs:
                grams = Decimal(str(raw_grams))
                if grams <= 0:
                    raise CommandError(
                        f"{row['slug']}: grams for {ingredient_slug} must be positive"
                    )
                component = existing.pop(ingredient_slug, None)
                if component is None:
                    RecipeIngredient.objects.create(
                        recipe=recipe,
                        ingredient=ingredients[ingredient_slug],
                        grams_in_serving=grams,
                    )
                    composition_changed = True
                elif component.grams_in_serving != grams:
                    component.grams_in_serving = grams
                    component.save()
                    composition_changed = True
            if existing:
                stale_ids = [item.pk for item in existing.values()]
                RecipeIngredient.objects.filter(pk__in=stale_ids).delete()
                recipe.touch()
                composition_changed = True

            if recipe_changed or composition_changed:
                if not was_created:
                    updated += 1
            elif not was_created:
                unchanged += 1

        self.stdout.write(
            self.style.SUCCESS(
                f"seed_recipes: {created} created, {updated} updated, {unchanged} unchanged"
            )
        )
