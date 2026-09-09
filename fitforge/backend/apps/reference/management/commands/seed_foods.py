import json
from decimal import Decimal
from pathlib import Path

from django.core.management.base import BaseCommand

from apps.reference.models import FoodItem

DEFAULT_SEED_PATH = Path(__file__).resolve().parent.parent.parent / "seed_data" / "foods.json"
DECIMAL_FIELDS = {
    "kcal_per_100g",
    "protein_g",
    "carbs_g",
    "fat_g",
    "default_portion_g",
}
MODEL_FIELDS = DECIMAL_FIELDS | {
    "name",
    "portion_label",
    "source",
    "barcode",
    "is_active",
}


class Command(BaseCommand):
    help = "Load or update the food library from a JSON seed file, keyed by slug."

    def add_arguments(self, parser):
        parser.add_argument("--file", default=str(DEFAULT_SEED_PATH))

    def handle(self, *args, **options):
        data = json.loads(Path(options["file"]).read_text())
        created = updated = unchanged = 0
        for row in data["foods"]:
            fields = {
                key: Decimal(str(value)) if key in DECIMAL_FIELDS else value
                for key, value in row.items()
                if key in MODEL_FIELDS
            }
            food = FoodItem.objects.filter(slug=row["slug"]).first()
            if food is None:
                FoodItem.objects.create(slug=row["slug"], **fields)
                created += 1
                continue
            dirty = False
            for field, value in fields.items():
                if getattr(food, field) != value:
                    setattr(food, field, value)
                    dirty = True
            if dirty:
                food.save()
                updated += 1
            else:
                unchanged += 1

        self.stdout.write(
            self.style.SUCCESS(
                f"seed_foods: {created} created, {updated} updated, {unchanged} unchanged"
            )
        )
