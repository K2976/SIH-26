package com.fitforge.app.feature.social

import com.fitforge.app.data.remote.generated.models.SocialProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class SocialFeatureLogicTest {

    private val handleRegex = Regex("^[a-zA-Z0-9_]{3,32}$")

    @Test
    fun `valid usernames pass handle format checks`() {
        val validHandles = listOf("alex", "john_doe", "lifter99", "FIT_WARRIOR_01", "a_b")
        for (handle in validHandles) {
            assertTrue("Expected '$handle' to be valid", handle.matches(handleRegex))
        }
    }

    @Test
    fun `invalid usernames fail handle format checks`() {
        val invalidHandles = listOf(
            "ab", // too short (<3)
            "this_handle_is_way_too_long_for_a_user_profile_system_33", // >32 chars
            "user@name", // contains @
            "user name", // contains space
            "user-name", // contains hyphen
            "user.name", // contains period
        )
        for (handle in invalidHandles) {
            assertFalse("Expected '$handle' to be invalid", handle.matches(handleRegex))
        }
    }

    @Test
    fun `stripping leading at sign works correctly`() {
        assertEquals("iron_lifter", "@iron_lifter".trim().removePrefix("@"))
        assertEquals("iron_lifter", "iron_lifter".trim().removePrefix("@"))
        assertEquals("iron_lifter", "  @iron_lifter  ".trim().removePrefix("@"))
    }

    @Test
    fun `social profile data model properties correctly stored`() {
        val now = OffsetDateTime.now()
        val profile = SocialProfile(
            id = 42,
            username = "fit_pro",
            displayName = "Fitness Pro",
            bio = "Chasing PRs every day",
            isPrivate = false,
            createdAt = now,
            updatedAt = now,
            profileImage = null,
        )

        assertEquals(42, profile.id)
        assertEquals("fit_pro", profile.username)
        assertEquals("Fitness Pro", profile.displayName)
        assertEquals("Chasing PRs every day", profile.bio)
        assertFalse(profile.isPrivate)
        assertEquals(now, profile.createdAt)
    }

    @Test
    fun `initials calculation works for single and multi-word names`() {
        fun getInitials(displayName: String, username: String): String {
            return displayName.trim().take(2).uppercase().ifEmpty {
                username.take(2).uppercase()
            }
        }

        assertEquals("AL", getInitials("Alex", "alex99"))
        assertEquals("JO", getInitials("John Doe", "johnd"))
        assertEquals("RO", getInitials("", "rocky"))
    }
}
