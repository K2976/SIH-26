package com.fitforge.app.feature.community

import com.fitforge.app.data.remote.generated.models.CommunityDetail
import com.fitforge.app.data.remote.generated.models.CommunityMemberUser
import com.fitforge.app.data.remote.generated.models.VisibilityEnum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock

class CommunityFeatureLogicTest {

    @Test
    fun `community name validation rejects empty and over-length names`() {
        fun isValidCommunityName(name: String): Boolean {
            val trimmed = name.trim()
            return trimmed.isNotEmpty() && trimmed.length <= 100
        }

        assertTrue(isValidCommunityName("Iron Lifters Gym"))
        assertTrue(isValidCommunityName("A"))
        assertTrue(isValidCommunityName("A".repeat(100)))

        assertFalse(isValidCommunityName(""))
        assertFalse(isValidCommunityName("   "))
        assertFalse(isValidCommunityName("A".repeat(101)))
    }

    @Test
    fun `visibility enum resolves correctly between public and private`() {
        assertEquals(VisibilityEnum.`public`, VisibilityEnum.decode("public"))
        assertEquals(VisibilityEnum.`private`, VisibilityEnum.decode("private"))
        assertEquals("public", VisibilityEnum.`public`.value)
        assertEquals("private", VisibilityEnum.`private`.value)
    }

    @Test
    fun `member role helper functions correctly identify owner and admin`() {
        fun isOwnerOrAdmin(role: String?): Boolean {
            return role?.lowercase() in listOf("owner", "admin")
        }

        assertTrue(isOwnerOrAdmin("owner"))
        assertTrue(isOwnerOrAdmin("Owner"))
        assertTrue(isOwnerOrAdmin("admin"))
        assertTrue(isOwnerOrAdmin("Admin"))
        assertFalse(isOwnerOrAdmin("member"))
        assertFalse(isOwnerOrAdmin(null))
        assertFalse(isOwnerOrAdmin(""))
    }

    @Test
    fun `owner leave validation logic correctly guards against abandoning members`() {
        fun canOwnerLeaveDirectly(membersCount: Int): Boolean {
            // Owner can leave only if they are the sole remaining member (which triggers community deletion)
            return membersCount <= 1
        }

        assertTrue(canOwnerLeaveDirectly(1))
        assertTrue(canOwnerLeaveDirectly(0))
        assertFalse(canOwnerLeaveDirectly(2))
        assertFalse(canOwnerLeaveDirectly(15))
    }

    @Test
    fun `initials extraction for community member handles display names and usernames`() {
        fun getMemberInitials(displayName: String, username: String): String {
            return displayName.trim().take(2).uppercase().ifEmpty {
                username.take(2).uppercase()
            }
        }

        assertEquals("MA", getMemberInitials("Max Power", "maxp"))
        assertEquals("SA", getMemberInitials("Sam", "sammy"))
        assertEquals("AL", getMemberInitials("", "alex99"))
        assertEquals("BO", getMemberInitials("   ", "bob"))
    }

    @Test
    fun `community detail model accurately reflects membership and role`() {
        val now = Clock.System.now()
        val ownerUser = CommunityMemberUser(
            id = 10,
            username = "coach_carter",
            displayName = "Coach Carter",
            profileImage = null,
            bio = "Head Coach",
        )

        val detail = CommunityDetail(
            id = 1,
            name = "Varsity Track & Field",
            description = "Daily 5K sprints and hurdle drills.",
            visibility = VisibilityEnum.`public`,
            owner = ownerUser,
            membersCount = 24,
            currentUserRole = "member",
            isMember = true,
            createdAt = now,
            updatedAt = now,
        )

        assertEquals(1, detail.id)
        assertEquals("Varsity Track & Field", detail.name)
        assertEquals(VisibilityEnum.`public`, detail.visibility)
        assertEquals(24, detail.membersCount)
        assertEquals("member", detail.currentUserRole)
        assertTrue(detail.isMember)
        assertEquals("coach_carter", detail.owner.username)
    }
}
