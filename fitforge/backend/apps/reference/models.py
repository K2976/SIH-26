from django.db import models, transaction


class ReferenceVersion(models.Model):
    """Single-row global counter. Bumped whenever any reference row changes."""

    value = models.PositiveIntegerField(default=0)

    def __str__(self):
        return f"ReferenceVersion<{self.value}>"

    @classmethod
    def bump(cls) -> int:
        with transaction.atomic():
            obj, _ = cls.objects.select_for_update().get_or_create(pk=1)
            obj.value += 1
            obj.save(update_fields=["value"])
            return obj.value

    @classmethod
    def current(cls) -> int:
        obj, _ = cls.objects.get_or_create(pk=1)
        return obj.value


class Exercise(models.Model):
    class MovementPattern(models.TextChoices):
        SQUAT = "squat", "Squat"
        HINGE = "hinge", "Hinge"
        LUNGE = "lunge", "Lunge"
        PUSH_HORIZONTAL = "push_horizontal", "Push (horizontal)"
        PUSH_VERTICAL = "push_vertical", "Push (vertical)"
        PULL_HORIZONTAL = "pull_horizontal", "Pull (horizontal)"
        PULL_VERTICAL = "pull_vertical", "Pull (vertical)"
        CARRY = "carry", "Carry"
        CORE = "core", "Core"
        ISOLATION = "isolation", "Isolation"
        PLYOMETRIC = "plyometric", "Plyometric"
        CARDIO = "cardio", "Cardio"

    class CameraPosition(models.TextChoices):
        FRONT = "front", "Front"
        SIDE = "side", "Side"
        FORTY_FIVE_DEGREE = "45_degree", "45 degree"

    # Natural key. Clients store this, never the numeric id.
    slug = models.SlugField(max_length=64, unique=True)

    # Locale-keyed as {"en": "...", "hi": "..."} so a new language is a data
    # write, never a migration. English is always present; readers fall back
    # to it via localized_name()/localized_instructions().
    name = models.JSONField()
    instructions = models.JSONField()

    primary_muscles = models.JSONField(default=list, blank=True)
    secondary_muscles = models.JSONField(default=list, blank=True)
    equipment = models.CharField(max_length=64, blank=True)
    movement_pattern = models.CharField(max_length=32, choices=MovementPattern.choices)
    form_cues = models.JSONField(default=list, blank=True)

    # Required — strength-session burn cannot be estimated without it.
    met_value = models.DecimalField(max_digits=3, decimal_places=1)

    is_bodyweight = models.BooleanField(default=False)
    pose_config = models.JSONField(null=True, blank=True)
    # null=True is deliberate: unset means "no required camera position",
    # distinct from any choice value.
    camera_position = models.CharField(  # noqa: DJ001
        max_length=16, choices=CameraPosition.choices, null=True, blank=True
    )

    # Never hard-deleted — set False instead. User history points at this row.
    is_active = models.BooleanField(default=True)

    # The version of the global ReferenceVersion counter as of this row's
    # last change. Delta sync is `updated_at_version > since`.
    updated_at_version = models.PositiveIntegerField(default=0, db_index=True)

    def __str__(self):
        return self.slug

    def save(self, *args, **kwargs):
        self.updated_at_version = ReferenceVersion.bump()
        super().save(*args, **kwargs)

    def localized(self, field_name: str, locale: str = "en"):
        value = getattr(self, field_name)
        return value.get(locale) or value.get("en")

    def localized_name(self, locale: str = "en"):
        return self.localized("name", locale)

    def localized_instructions(self, locale: str = "en"):
        return self.localized("instructions", locale)


class FoodItem(models.Model):
    """A raw ingredient reference row used by every food logging path.

    ``FoodItem`` is retained as the API/database name for backwards
    compatibility.  Composite dishes live in ``Recipe`` and never assert a
    second, independent set of nutrition numbers.
    """

    slug = models.SlugField(max_length=64, unique=True)
    name = models.JSONField()
    kcal_per_100g = models.DecimalField(max_digits=8, decimal_places=2)
    protein_g = models.DecimalField(max_digits=8, decimal_places=2)
    carbs_g = models.DecimalField(max_digits=8, decimal_places=2)
    fat_g = models.DecimalField(max_digits=8, decimal_places=2)
    default_portion_g = models.DecimalField(max_digits=8, decimal_places=2)
    portion_label = models.CharField(max_length=64)
    source = models.CharField(max_length=128)
    barcode = models.CharField(max_length=64, unique=True, null=True, blank=True)  # noqa: DJ001
    is_active = models.BooleanField(default=True)
    updated_at_version = models.PositiveIntegerField(default=0, db_index=True)

    def __str__(self):
        return self.slug

    def save(self, *args, **kwargs):
        was_existing = self.pk is not None
        self.updated_at_version = ReferenceVersion.bump()
        super().save(*args, **kwargs)
        if was_existing:
            # Recipe nutrition is derived from current ingredient values, so
            # clients must receive a fresh materialized recipe view whenever
            # an ingredient changes.
            for recipe in Recipe.objects.filter(ingredients__ingredient=self):
                recipe.touch()

    def localized_name(self, locale: str = "en"):
        return self.name.get(locale) or self.name.get("en")


class Recipe(models.Model):
    """A composed dish whose nutrition is derived from its ingredients."""

    slug = models.SlugField(max_length=64, unique=True)
    name = models.JSONField()
    serving_description = models.CharField(max_length=128)
    serving_grams_total = models.DecimalField(max_digits=8, decimal_places=2)
    is_active = models.BooleanField(default=True)
    updated_at_version = models.PositiveIntegerField(default=0, db_index=True)

    class Meta:
        constraints = [
            models.CheckConstraint(
                condition=models.Q(serving_grams_total__gt=0),
                name="recipe_serving_grams_positive",
            )
        ]

    def __str__(self):
        return self.slug

    def save(self, *args, **kwargs):
        self.updated_at_version = ReferenceVersion.bump()
        super().save(*args, **kwargs)

    def localized_name(self, locale: str = "en"):
        return self.name.get(locale) or self.name.get("en")

    @property
    def default_portion_g(self):
        return self.serving_grams_total

    @property
    def portion_label(self):
        return self.serving_description

    def touch(self):
        """Publish an ingredient-composition change through reference sync."""
        version = ReferenceVersion.bump()
        type(self).objects.filter(pk=self.pk).update(updated_at_version=version)
        self.updated_at_version = version


class RecipeIngredient(models.Model):
    recipe = models.ForeignKey(Recipe, related_name="ingredients", on_delete=models.CASCADE)
    ingredient = models.ForeignKey(FoodItem, on_delete=models.PROTECT)
    grams_in_serving = models.DecimalField(max_digits=8, decimal_places=2)

    class Meta:
        constraints = [
            models.UniqueConstraint(
                fields=["recipe", "ingredient"], name="unique_recipe_ingredient"
            ),
            models.CheckConstraint(
                condition=models.Q(grams_in_serving__gt=0),
                name="recipe_ingredient_grams_positive",
            ),
        ]

    def __str__(self):
        return f"{self.recipe.slug}:{self.ingredient.slug}"

    def save(self, *args, **kwargs):
        with transaction.atomic():
            super().save(*args, **kwargs)
            self.recipe.touch()

    def delete(self, *args, **kwargs):
        recipe = self.recipe
        with transaction.atomic():
            result = super().delete(*args, **kwargs)
            recipe.touch()
            return result


class TrainingMusicMapping(models.Model):
    """(training_focus, cycle_phase) -> curated genre + YouTube playlist.

    A row with cycle_phase=None is the focus-only fallback, used when phase
    isn't available (not natural tracking, or insufficient cycle history —
    see apps/engine/cycle.py). Deep-linking only: no YouTube API, no OAuth,
    no quota. The client reads UserState for training_focus/phase and looks
    the playlist up locally — it never calls this app's tables directly
    (CLAUDE.md rule 5), only through the same reference-sync path Exercise
    uses.
    """

    # Mirrors apps.accounts.models.Goal.TrainingFocus. Duplicated rather than
    # imported: reference/ has zero cross-app dependencies today, and this is
    # a lookup key, not a foreign key to a Goal row.
    class TrainingFocus(models.TextChoices):
        STRENGTH = "strength", "Strength"
        HYPERTROPHY = "hypertrophy", "Hypertrophy"
        ENDURANCE = "endurance", "Endurance"
        GENERAL = "general", "General"

    # Mirrors the phase labels apps.engine.cycle.derive_phase returns.
    class CyclePhase(models.TextChoices):
        MENSTRUAL = "menstrual", "Menstrual"
        FOLLICULAR = "follicular", "Follicular"
        OVULATORY = "ovulatory", "Ovulatory"
        LUTEAL = "luteal", "Luteal"

    training_focus = models.CharField(max_length=16, choices=TrainingFocus.choices)
    # null=True: the focus-only fallback row. Distinct from any phase value.
    cycle_phase = models.CharField(  # noqa: DJ001
        max_length=16, choices=CyclePhase.choices, null=True, blank=True
    )
    genre = models.CharField(max_length=64)
    youtube_playlist_id = models.CharField(max_length=64)

    is_active = models.BooleanField(default=True)
    updated_at_version = models.PositiveIntegerField(default=0, db_index=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(
                fields=["training_focus", "cycle_phase"],
                name="unique_training_focus_cycle_phase",
            )
        ]

    def __str__(self):
        return f"TrainingMusicMapping<{self.training_focus}/{self.cycle_phase}:{self.genre}>"

    def save(self, *args, **kwargs):
        self.updated_at_version = ReferenceVersion.bump()
        super().save(*args, **kwargs)
