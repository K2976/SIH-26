from django.core.management.base import BaseCommand

from apps.engine.cycle_service import recompute_all_cycle_patterns


class Command(BaseCommand):
    help = (
        "Rebuild CyclePattern from scratch for every user who has opted into "
        "cycle tracking (§10). Intended for the same nightly cron schedule as "
        "rebuild_daily_state (not Celery — see CLAUDE.md 'Not used'). "
        "CyclePattern is a pure cache: this command only ever recomputes it "
        "from CycleLog / DailyState history."
    )

    def handle(self, *args, **options):
        count = recompute_all_cycle_patterns()
        self.stdout.write(
            self.style.SUCCESS(f"recompute_cycle_patterns: recomputed {count} cycle-day row(s)")
        )
