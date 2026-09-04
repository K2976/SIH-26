import json
from decimal import Decimal
from pathlib import Path

from django.core.management.base import BaseCommand

from apps.reference.models import Exercise

# MET values in the seed file come from a published compendium — see the
# "_meta" key in the seed JSON for the citation. Do not hand-edit met_value
# here; edit the seed file and re-run this command.
DEFAULT_SEED_PATH = Path(__file__).resolve().parent.parent.parent / "seed_data" / "exercises.json"

# Model fields the seed file may set. "_met_source" is documentation, not a
# field, and is dropped before it ever reaches the model.
MODEL_FIELDS = {
    "name",
    "instructions",
    "primary_muscles",
    "secondary_muscles",
    "equipment",
    "movement_pattern",
    "form_cues",
    "met_value",
    "is_bodyweight",
    "pose_config",
    "camera_position",
    "is_active",
}


def _normalize(field, value):
    if field == "met_value" and value is not None:
        return Decimal(str(value))
    return value


class Command(BaseCommand):
    help = "Load or update the exercise library from a JSON seed file, keyed by slug."

    def add_arguments(self, parser):
        parser.add_argument("--file", default=str(DEFAULT_SEED_PATH))

    def handle(self, *args, **options):
        path = Path(options["file"])
        data = json.loads(path.read_text())

        created = updated = unchanged = 0
        for row in data["exercises"]:
            slug = row["slug"]
            fields = {k: _normalize(k, v) for k, v in row.items() if k in MODEL_FIELDS}

            exercise = Exercise.objects.filter(slug=slug).first()
            if exercise is None:
                Exercise.objects.create(slug=slug, **fields)
                created += 1
                continue

            dirty = False
            for field, value in fields.items():
                if getattr(exercise, field) != value:
                    setattr(exercise, field, value)
                    dirty = True
            if dirty:
                exercise.save()
                updated += 1
            else:
                unchanged += 1

        self.stdout.write(
            self.style.SUCCESS(
                f"seed_exercises: {created} created, {updated} updated, {unchanged} unchanged"
            )
        )
