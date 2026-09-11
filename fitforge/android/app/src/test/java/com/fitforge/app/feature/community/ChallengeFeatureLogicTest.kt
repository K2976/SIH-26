package com.fitforge.app.feature.community

import com.fitforge.app.data.remote.generated.models.ChallengeList
import com.fitforge.app.data.remote.generated.models.ChallengeTypeEnum
import com.fitforge.app.data.remote.generated.models.CommunityMemberUser
import com.fitforge.app.data.remote.generated.models.StatusEnum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ChallengeFeatureLogicTest {

    @Test
    fun `challenge target validation rejects non-positive or blank inputs`() {
        fun validateTarget(input: String): Double? {
            val num = input.trim().toDoubleOrNull() ?: return null
            return if (num > 0.0) num else null
        }

        assertNotNull(validateTarget("10"))
        assertNotNull(validateTarget("5000.5"))
        assertEquals(10.0, validateTarget("10")!!, 0.001)

        assertNull(validateTarget(""))
        assertNull(validateTarget("   "))
        assertNull(validateTarget("0"))
        assertNull(validateTarget("-5"))
        assertNull(validateTarget("abc"))
    }

    @Test
    fun `challenge duration validation enforces at least 1 day duration`() {
        fun validateDuration(daysStr: String): Int? {
            val days = daysStr.trim().toIntOrNull() ?: return null
            return if (days in 1..365) days else null
        }

        assertEquals(7, validateDuration("7"))
        assertEquals(30, validateDuration("30"))
        assertEquals(1, validateDuration("1"))
        assertEquals(365, validateDuration("365"))

        assertNull(validateDuration("0"))
        assertNull(validateDuration("-1"))
        assertNull(validateDuration("366"))
        assertNull(validateDuration(""))
        assertNull(validateDuration("xyz"))
    }

    @Test
    fun `end date calculation from start date and duration produces valid range`() {
        val startDate = LocalDate.of(2026, 9, 11)
        val durationDays = 14
        val endDate = startDate.plusDays(durationDays.toLong())

        assertTrue(endDate.isAfter(startDate))
        assertEquals(LocalDate.of(2026, 9, 25), endDate)
    }

    @Test
    fun `challenge progress bar clamping and completion calculation`() {
        fun calculateProgressRatio(current: Double, target: Double): Float {
            if (target <= 0.0) return 0f
            val ratio = (current / target).toFloat()
            return ratio.coerceIn(0f, 1f)
        }

        fun isCompleted(current: Double, target: Double): Boolean {
            return current >= target
        }

        // Under target
        assertEquals(0.5f, calculateProgressRatio(50.0, 100.0), 0.001f)
        assertFalse(isCompleted(50.0, 100.0))

        // Exact target
        assertEquals(1.0f, calculateProgressRatio(100.0, 100.0), 0.001f)
        assertTrue(isCompleted(100.0, 100.0))

        // Exceeding target (clamped to 1.0 for progress bar)
        assertEquals(1.0f, calculateProgressRatio(150.0, 100.0), 0.001f)
        assertTrue(isCompleted(150.0, 100.0))

        // Zero progress
        assertEquals(0.0f, calculateProgressRatio(0.0, 100.0), 0.001f)
        assertFalse(isCompleted(0.0, 100.0))
    }

    @Test
    fun `challenge creation permissions correctly identify community leaders`() {
        fun canCreateChallenge(role: String?): Boolean {
            return role?.lowercase() in listOf("owner", "admin")
        }

        assertTrue(canCreateChallenge("owner"))
        assertTrue(canCreateChallenge("Owner"))
        assertTrue(canCreateChallenge("admin"))
        assertTrue(canCreateChallenge("Admin"))
        assertFalse(canCreateChallenge("member"))
        assertFalse(canCreateChallenge("Member"))
        assertFalse(canCreateChallenge(null))
        assertFalse(canCreateChallenge(""))
    }

    enum class ChallengeFilter { ALL, ACTIVE, UPCOMING, COMPLETED }

    @Test
    fun `challenge filter chip logic accurately filters challenge list`() {
        val dummyCreator = CommunityMemberUser(
            id = 1,
            username = "coach",
            displayName = "Coach",
            bio = "Head Coach",
            profileImage = null,
        )

        val baseDate = OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC)

        val activeChallenge = ChallengeList(
            id = 1,
            title = "Pushup Challenge",
            challengeType = ChallengeTypeEnum.workout_count,
            targetValue = BigDecimal.valueOf(30),
            startDate = baseDate,
            endDate = baseDate.plusDays(30),
            status = StatusEnum.active,
            participantsCount = 10,
            createdBy = dummyCreator,
            isParticipant = true,
        )

        val upcomingChallenge = ChallengeList(
            id = 2,
            title = "October 10K",
            challengeType = ChallengeTypeEnum.steps,
            targetValue = BigDecimal.valueOf(100000),
            startDate = baseDate.plusMonths(1),
            endDate = baseDate.plusMonths(1).plusDays(31),
            status = StatusEnum.upcoming,
            participantsCount = 4,
            createdBy = dummyCreator,
            isParticipant = false,
        )

        val completedChallenge = ChallengeList(
            id = 3,
            title = "August Consistency",
            challengeType = ChallengeTypeEnum.workout_minutes,
            targetValue = BigDecimal.valueOf(1200),
            startDate = baseDate.minusMonths(1),
            endDate = baseDate.minusMonths(1).plusDays(31),
            status = StatusEnum.completed,
            participantsCount = 18,
            createdBy = dummyCreator,
            isParticipant = true,
        )

        val allChallenges = listOf(activeChallenge, upcomingChallenge, completedChallenge)

        fun filterChallenges(list: List<ChallengeList>, filter: ChallengeFilter): List<ChallengeList> {
            return when (filter) {
                ChallengeFilter.ALL -> list
                ChallengeFilter.ACTIVE -> list.filter { it.status == StatusEnum.active }
                ChallengeFilter.UPCOMING -> list.filter { it.status == StatusEnum.upcoming }
                ChallengeFilter.COMPLETED -> list.filter { it.status == StatusEnum.completed }
            }
        }

        assertEquals(3, filterChallenges(allChallenges, ChallengeFilter.ALL).size)
        assertEquals(1, filterChallenges(allChallenges, ChallengeFilter.ACTIVE).size)
        assertEquals("Pushup Challenge", filterChallenges(allChallenges, ChallengeFilter.ACTIVE).first().title)
        assertEquals(1, filterChallenges(allChallenges, ChallengeFilter.UPCOMING).size)
        assertEquals("October 10K", filterChallenges(allChallenges, ChallengeFilter.UPCOMING).first().title)
        assertEquals(1, filterChallenges(allChallenges, ChallengeFilter.COMPLETED).size)
        assertEquals("August Consistency", filterChallenges(allChallenges, ChallengeFilter.COMPLETED).first().title)
    }

    @Test
    fun `challenge unit label resolves correctly by type`() {
        fun getUnitLabel(type: ChallengeTypeEnum): String {
            return when (type) {
                ChallengeTypeEnum.workout_count -> "workouts"
                ChallengeTypeEnum.workout_minutes -> "mins"
                ChallengeTypeEnum.steps -> "steps"
                ChallengeTypeEnum.calories_burned -> "kcal"
                ChallengeTypeEnum.distance_km -> "km"
            }
        }

        assertEquals("workouts", getUnitLabel(ChallengeTypeEnum.workout_count))
        assertEquals("mins", getUnitLabel(ChallengeTypeEnum.workout_minutes))
        assertEquals("steps", getUnitLabel(ChallengeTypeEnum.steps))
        assertEquals("kcal", getUnitLabel(ChallengeTypeEnum.calories_burned))
        assertEquals("km", getUnitLabel(ChallengeTypeEnum.distance_km))
    }
}

