import json

from django.core.management.base import BaseCommand

from apps.diary.models import PoseLandmarkSequence


class Command(BaseCommand):
    """Dumps every collected PoseLandmarkSequence row to one JSON file for the
    offline ml/ training pipeline (see fitforge/ml/README.md) — decouples the
    pipeline from Django/the database entirely; it only ever reads this file.

    Every row's own landmark_frames/rep_labels are JSON-encoded text (see the
    model docstring), so they're parsed back into real JSON structures here
    rather than exported as nested strings.
    """

    help = "Export collected PoseLandmarkSequence rows to a JSON file for fitforge/ml/ training."

    def add_arguments(self, parser):
        parser.add_argument("--output", required=True, help="Path to write the JSON dataset file.")

    def handle(self, *args, **options):
        rows = []
        queryset = PoseLandmarkSequence.objects.select_related("exercise").order_by("created_at")
        for row in queryset:
            rows.append(
                {
                    "client_uuid": str(row.client_uuid),
                    "exercise_slug": row.exercise_id,
                    "camera_position": row.camera_position,
                    "calibration_source": row.calibration_source,
                    "landmark_frames": json.loads(row.landmark_frames),
                    "rep_labels": json.loads(row.rep_labels),
                }
            )

        with open(options["output"], "w") as f:
            json.dump(rows, f)

        self.stdout.write(self.style.SUCCESS(f"export_pose_sequences: wrote {len(rows)} sample(s)"))
