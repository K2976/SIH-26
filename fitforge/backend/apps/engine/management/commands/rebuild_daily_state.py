from django.core.management.base import BaseCommand

from apps.engine.service import rebuild_all_daily_states


class Command(BaseCommand):
    help = (
        "Rebuild DailyState from scratch for every user, from their first "
        "diary entry to their last. Intended for a nightly cron schedule "
        "(not Celery — see CLAUDE.md 'Not used'). DailyState is a pure "
        "cache: this command only ever recomputes it from the diary."
    )

    def handle(self, *args, **options):
        count = rebuild_all_daily_states()
        self.stdout.write(self.style.SUCCESS(f"rebuild_daily_state: recomputed {count} day(s)"))
