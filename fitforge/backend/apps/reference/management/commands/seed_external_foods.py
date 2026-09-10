from decimal import Decimal
from pathlib import Path

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

from apps.reference.external_foods import ExternalFoodError, fetch_external_foods
from apps.reference.models import FoodItem

DEFAULT_MANIFEST_PATH = (
    Path(__file__).resolve().parent.parent.parent / "seed_data" / "external_foods.json"
)
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
    help = "Fetch and idempotently seed curated OFF and USDA FDC food rows."

    def add_arguments(self, parser):
        parser.add_argument("--file", default=str(DEFAULT_MANIFEST_PATH))
        parser.add_argument(
            "--source",
            choices=("all", "open_food_facts", "usda_fdc"),
            default="all",
            help="Limit the fetch to one source; defaults to both.",
        )

    def handle(self, *args, **options):
        sources = (
            {"open_food_facts", "usda_fdc"} if options["source"] == "all" else {options["source"]}
        )
        try:
            rows = fetch_external_foods(Path(options["file"]), sources)
        except (ExternalFoodError, OSError, ValueError) as exc:
            raise CommandError(str(exc)) from None

        created = updated = unchanged = 0
        # Every remote row has been validated before this transaction starts.
        # Any database failure also rolls the entire selected catalog back.
        with transaction.atomic():
            for row in rows:
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
                f"seed_external_foods: {created} created, {updated} updated, {unchanged} unchanged"
            )
        )
