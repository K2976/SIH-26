from drf_spectacular.utils import extend_schema_field
from rest_framework import serializers

from apps.reference.models import Exercise, FoodItem, Recipe, RecipeIngredient, TrainingMusicMapping
from apps.reference.nutrition import recipe_nutrition_per_100g


@extend_schema_field({"type": "object"})
class NullableJSONField(serializers.JSONField):
    """A bare `JSONField(allow_null=True)` — untyped and nullable, with no
    $ref or type keyword — makes drf-spectacular emit `{nullable: true}`
    with nothing else. The openapi-generator Kotlin template can't infer a
    type for that and falls back to generating NullEnum, a zero-entry enum
    that's a syntax error on its own and fails the whole module's compile.
    Forcing `type: object` here doesn't change runtime behavior at all."""


class ExerciseSerializer(serializers.ModelSerializer):
    # Plain CharField, not the auto-derived ChoiceField — see the identical
    # note on TrainingMusicMappingSerializer.cycle_phase below. Read-only
    # endpoint (ExerciseDeltaView is a ListAPIView), so there's no write-side
    # validation to preserve.
    camera_position = serializers.CharField(allow_null=True, required=False)
    pose_config = NullableJSONField(allow_null=True, required=False)

    class Meta:
        model = Exercise
        # No `id` — slug is the identifier clients store.
        fields = [
            "slug",
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
            "updated_at_version",
        ]


class FoodItemSerializer(serializers.ModelSerializer):
    item_type = serializers.SerializerMethodField()

    @extend_schema_field(serializers.CharField())
    def get_item_type(self, obj):
        return "ingredient"

    class Meta:
        model = FoodItem
        fields = [
            "slug",
            "name",
            "kcal_per_100g",
            "protein_g",
            "carbs_g",
            "fat_g",
            "default_portion_g",
            "portion_label",
            "source",
            "barcode",
            "is_active",
            "updated_at_version",
            "item_type",
        ]


class RecipeIngredientSerializer(serializers.ModelSerializer):
    ingredient_slug = serializers.SlugField(source="ingredient.slug")

    class Meta:
        model = RecipeIngredient
        fields = ["ingredient_slug", "grams_in_serving"]


class RecipeSerializer(serializers.ModelSerializer):
    kcal_per_100g = serializers.SerializerMethodField()
    protein_g = serializers.SerializerMethodField()
    carbs_g = serializers.SerializerMethodField()
    fat_g = serializers.SerializerMethodField()
    default_portion_g = serializers.DecimalField(
        source="serving_grams_total", max_digits=8, decimal_places=2, read_only=True
    )
    portion_label = serializers.CharField(source="serving_description", read_only=True)
    source = serializers.SerializerMethodField()
    barcode = serializers.SerializerMethodField()
    item_type = serializers.SerializerMethodField()
    ingredients = RecipeIngredientSerializer(many=True, read_only=True)

    def _nutrition(self, obj):
        cached = getattr(obj, "_serialized_nutrition", None)
        if cached is None:
            cached = recipe_nutrition_per_100g(obj)
            obj._serialized_nutrition = cached
        return cached

    @extend_schema_field(serializers.DecimalField(max_digits=8, decimal_places=2))
    def get_kcal_per_100g(self, obj):
        return str(self._nutrition(obj).calories)

    @extend_schema_field(serializers.DecimalField(max_digits=8, decimal_places=2))
    def get_protein_g(self, obj):
        return str(self._nutrition(obj).protein_g)

    @extend_schema_field(serializers.DecimalField(max_digits=8, decimal_places=2))
    def get_carbs_g(self, obj):
        return str(self._nutrition(obj).carbs_g)

    @extend_schema_field(serializers.DecimalField(max_digits=8, decimal_places=2))
    def get_fat_g(self, obj):
        return str(self._nutrition(obj).fat_g)

    @extend_schema_field(serializers.CharField())
    def get_source(self, obj):
        return "recipe_computed"

    @extend_schema_field(serializers.CharField(allow_null=True))
    def get_barcode(self, obj):
        return None

    @extend_schema_field(serializers.CharField())
    def get_item_type(self, obj):
        return "recipe"

    class Meta:
        model = Recipe
        fields = [
            "slug",
            "name",
            "serving_description",
            "serving_grams_total",
            "ingredients",
            "kcal_per_100g",
            "protein_g",
            "carbs_g",
            "fat_g",
            "default_portion_g",
            "portion_label",
            "source",
            "barcode",
            "item_type",
            "is_active",
            "updated_at_version",
        ]


class TrainingMusicMappingSerializer(serializers.ModelSerializer):
    # Plain CharField, not the auto-derived ChoiceField: a *nullable* choices
    # field makes drf-spectacular emit an enum+blank oneOf union that the
    # Kotlin generator turns into an empty wrapper class with no accessible
    # value (see Exercise.camera_position for the same pre-existing shape).
    # A plain nullable string sidesteps that landmine for this new field.
    cycle_phase = serializers.CharField(allow_null=True, required=False)

    class Meta:
        model = TrainingMusicMapping
        fields = [
            "training_focus",
            "cycle_phase",
            "genre",
            "youtube_playlist_id",
            "is_active",
            "updated_at_version",
        ]


class ManifestSerializer(serializers.Serializer):
    reference_version = serializers.IntegerField()
    table_counts = serializers.DictField(child=serializers.IntegerField())
