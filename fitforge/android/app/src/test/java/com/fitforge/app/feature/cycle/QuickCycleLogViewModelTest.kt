package com.fitforge.app.feature.cycle

import com.fitforge.app.core.database.BaseDiaryColumns
import com.fitforge.app.core.database.CycleLogEntity
import com.fitforge.app.core.database.SyncStatus
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun log(energyRating: Int? = null, symptoms: List<String> = emptyList()) = CycleLogEntity(
    diary = BaseDiaryColumns(
        clientUuid = "test-uuid",
        occurredAt = Instant.fromEpochMilliseconds(0),
        utcOffset = "+00:00",
        source = "user_created",
        createdAt = Instant.fromEpochMilliseconds(0),
        syncStatus = SyncStatus.PENDING,
    ),
    date = LocalDate(2026, 9, 8),
    symptoms = symptoms,
    energyRating = energyRating,
)

class QuickCycleLogViewModelTest {

    @Test
    fun `hidden when cycle tracking is disabled`() {
        val state = computeQuickCycleLogUiState(
            cycleEnabled = false,
            discreetMode = false,
            dismissedThisSession = false,
            todaysLogs = emptyList(),
            showSymptomPicker = false,
        )
        assertFalse(state.visible)
    }

    @Test
    fun `hidden in discreet mode even when enabled`() {
        val state = computeQuickCycleLogUiState(
            cycleEnabled = true,
            discreetMode = true,
            dismissedThisSession = false,
            todaysLogs = emptyList(),
            showSymptomPicker = false,
        )
        assertFalse(state.visible)
    }

    @Test
    fun `hidden after being skipped this session`() {
        val state = computeQuickCycleLogUiState(
            cycleEnabled = true,
            discreetMode = false,
            dismissedThisSession = true,
            todaysLogs = emptyList(),
            showSymptomPicker = false,
        )
        assertFalse(state.visible)
    }

    @Test
    fun `visible and prompting when nothing logged today`() {
        val state = computeQuickCycleLogUiState(
            cycleEnabled = true,
            discreetMode = false,
            dismissedThisSession = false,
            todaysLogs = emptyList(),
            showSymptomPicker = false,
        )
        assertTrue(state.visible)
        assertFalse(state.hasLoggedToday)
        assertEquals(null, state.loggedEnergyRating)
    }

    @Test
    fun `reflects a logged energy rating`() {
        val state = computeQuickCycleLogUiState(
            cycleEnabled = true,
            discreetMode = false,
            dismissedThisSession = false,
            todaysLogs = listOf(log(energyRating = 2)),
            showSymptomPicker = false,
        )
        assertTrue(state.hasLoggedToday)
        assertEquals(2, state.loggedEnergyRating)
        assertEquals(emptySet<String>(), state.loggedSymptoms)
    }

    @Test
    fun `an explicit empty symptom list still counts as logged, not missing`() {
        val state = computeQuickCycleLogUiState(
            cycleEnabled = true,
            discreetMode = false,
            dismissedThisSession = false,
            todaysLogs = listOf(log(energyRating = 4, symptoms = emptyList())),
            showSymptomPicker = false,
        )
        assertTrue(state.hasLoggedToday)
    }

    @Test
    fun `unions symptoms across multiple log rows the same day`() {
        val state = computeQuickCycleLogUiState(
            cycleEnabled = true,
            discreetMode = false,
            dismissedThisSession = false,
            todaysLogs = listOf(
                log(energyRating = 3),
                log(symptoms = listOf("cramps", "fatigue")),
            ),
            showSymptomPicker = false,
        )
        assertEquals(3, state.loggedEnergyRating)
        assertEquals(setOf("cramps", "fatigue"), state.loggedSymptoms)
    }
}
