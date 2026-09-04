import json
from pathlib import Path

from django.conf import settings
from django.core.management.base import BaseCommand

from apps.reference.models import Exercise, ReferenceVersion
from apps.reference.serializers import ExerciseSerializer

# Where the Android app's offline-first seed bundle lives, per
# PROJECT_GUIDE.md §13: "A seed bundle ships in assets/ as JSON, generated
# at build time from the server."
DEFAULT_OUTPUT_PATH = (
    settings.BASE_DIR.parent
    / "android"
    / "app"
    / "src"
    / "main"
    / "assets"
    / "reference_bundle.json"
)


class Command(BaseCommand):
    help = "Export the active exercise library to the JSON bundle Android ships in assets/."

    def add_arguments(self, parser):
        parser.add_argument("--output", default=str(DEFAULT_OUTPUT_PATH))

    def handle(self, *args, **options):
        exercises = Exercise.objects.filter(is_active=True).order_by("slug")
        bundle = {
            "reference_version": ReferenceVersion.current(),
            "exercises": ExerciseSerializer(exercises, many=True).data,
        }

        output_path = Path(options["output"])
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(json.dumps(bundle, indent=2))

        self.stdout.write(
            self.style.SUCCESS(f"Exported {exercises.count()} exercises to {output_path}")
        )
