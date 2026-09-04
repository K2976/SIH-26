"""Named constants shared across accounts models, serializers, and services."""

from decimal import Decimal

# Safe bounds for Goal.target_rate_kg_per_week. A rate outside this range is
# clamped at input (see services.clamp_target_rate) regardless of what the
# user or the goal's energy_direction requests.
MIN_TARGET_RATE_KG_PER_WEEK = Decimal("0.0")
MAX_TARGET_RATE_KG_PER_WEEK = Decimal("1.0")
