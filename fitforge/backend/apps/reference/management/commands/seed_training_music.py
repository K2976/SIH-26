import json
from pathlib import Path

from django.core.management.base import BaseCommand

from apps.reference.models import TrainingMusicMapping

DEFAULT_SEED_PATH = (
    Path(__file__).resolve().parent.parent.parent / "seed_data" / "training_music.json"
)

MODEL_FIELDS = {"genre", "youtube_playlist_id", "is_active"}


class Command(BaseCommand):
    help = (
        "Load or update the (training_focus, cycle_phase) -> genre/playlist lookup from a "
        "JSON seed file. The shipped seed file's playlist IDs are TODO placeholders — see "
        "its _meta note."
    )

    def add_arguments(self, parser):
        parser.add_argument("--file", default=str(DEFAULT_SEED_PATH))

    def handle(self, *args, **options):
        path = Path(options["file"])
        data = json.loads(path.read_text())

        created = updated = unchanged = placeholder = 0
        for row in data["training_music_mappings"]:
            key = {
                "training_focus": row["training_focus"],
                "cycle_phase": row.get("cycle_phase"),
            }
            fields = {k: v for k, v in row.items() if k in MODEL_FIELDS}
            if fields.get("youtube_playlist_id", "").startswith("TODO_"):
                placeholder += 1

            mapping = TrainingMusicMapping.objects.filter(**key).first()
            if mapping is None:
                TrainingMusicMapping.objects.create(**key, **fields)
                created += 1
                continue

            dirty = False
            for field, value in fields.items():
                if getattr(mapping, field) != value:
                    setattr(mapping, field, value)
                    dirty = True
            if dirty:
                mapping.save()
                updated += 1
            else:
                unchanged += 1

        self.stdout.write(
            self.style.SUCCESS(
                f"seed_training_music: {created} created, {updated} updated, {unchanged} unchanged"
            )
        )
        if placeholder:
            self.stdout.write(
                self.style.WARNING(
                    f"{placeholder} row(s) still carry TODO_ placeholder playlist IDs — "
                    "replace with real curated YouTube playlist IDs before release."
                )
            )
