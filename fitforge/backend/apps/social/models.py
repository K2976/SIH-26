from django.conf import settings
from django.core.validators import RegexValidator
from django.db import models

USERNAME_REGEX = r"^[a-zA-Z0-9_]{3,32}$"
username_validator = RegexValidator(
    regex=USERNAME_REGEX,
    message=(
        "Username must be 3-32 characters long and contain only letters, numbers, and underscores."
    ),
)


class SocialProfile(models.Model):
    """Public user identity for social features.

    Independent of auth_user.username and auth_user.email to preserve
    backward compatibility with the existing website authentication.
    """

    user = models.OneToOneField(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="social_profile",
    )
    username = models.CharField(
        max_length=32,
        unique=True,
        db_index=True,
        validators=[username_validator],
        help_text="Public unique @handle (e.g. iron_lifter99)",
    )
    display_name = models.CharField(max_length=64)
    bio = models.TextField(blank=True, default="")
    profile_image = models.ImageField(upload_to="profiles/", null=True, blank=True)
    is_private = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "social_profile"
        ordering = ["-created_at"]

    def __str__(self):
        return f"@{self.username} ({self.display_name})"
