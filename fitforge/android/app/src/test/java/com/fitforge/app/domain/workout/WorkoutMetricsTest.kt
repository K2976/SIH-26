package com.fitforge.app.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutMetricsTest {
    @Test
    fun `volume sums weight times reps and excludes warmups`() {
        val sets = listOf(
            SetMetricInput(weightKg = 100.0, reps = 5, isWarmup = false, exerciseMetValue = 5.0),
            SetMetricInput(weightKg = 20.0, reps = 10, isWarmup = true),
            SetMetricInput(weightKg = null, reps = 8, isWarmup = false, exerciseMetValue = 3.0),
        )
        assertEquals(500.0, sessionVolumeKg(sets), 0.001)
    }

    @Test
    fun `burn is null when bodyweight is unknown`() {
        val sets = listOf(SetMetricInput(weightKg = 100.0, reps = 5, isWarmup = false, exerciseMetValue = 5.0))
        assertNull(estimatedSessionKcal(sets, bodyweightKg = null, elapsedMinutes = 30.0))
    }

    @Test
    fun `burn uses mean MET across logged sets`() {
        val sets = listOf(
            SetMetricInput(weightKg = 100.0, reps = 5, isWarmup = false, exerciseMetValue = 6.0),
            SetMetricInput(weightKg = 50.0, reps = 8, isWarmup = false, exerciseMetValue = 4.0),
        )
        // mean MET = 5.0; 80kg bodyweight; 60 min = 1 hour -> 5.0 * 80 * 1
        assertEquals(400.0, estimatedSessionKcal(sets, bodyweightKg = 80.0, elapsedMinutes = 60.0)!!, 0.001)
    }
}
