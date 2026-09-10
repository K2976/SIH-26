package com.fitforge.app.pose

import org.junit.Assert.assertEquals
import org.junit.Test

private fun rep(
    depthReached: Boolean = true,
    lockoutReached: Boolean = true,
    symmetryDeviationDeg: Double? = null,
) = CompletedRep(
    startMs = 0,
    endMs = 1000,
    tempoMs = 1000,
    depthReached = depthReached,
    lockoutReached = lockoutReached,
    symmetryDeviationDeg = symmetryDeviationDeg,
)

class RepQualityLabelTest {
    @Test
    fun `a full-depth, full-lockout, symmetric rep is clean`() {
        assertEquals(RepQuality.CLEAN, repQuality(rep(symmetryDeviationDeg = 2.0), symmetryToleranceDeg = 10.0))
    }

    @Test
    fun `insufficient depth is shallow regardless of anything else`() {
        val shallow = rep(depthReached = false, lockoutReached = false, symmetryDeviationDeg = 50.0)
        assertEquals(RepQuality.SHALLOW, repQuality(shallow, symmetryToleranceDeg = 10.0))
    }

    @Test
    fun `depth reached but symmetry over tolerance is asymmetric`() {
        val asymmetric = rep(symmetryDeviationDeg = 15.0)
        assertEquals(RepQuality.ASYMMETRIC, repQuality(asymmetric, symmetryToleranceDeg = 10.0))
    }

    @Test
    fun `depth and symmetry fine but lockout missed is partial lockout`() {
        val partial = rep(lockoutReached = false, symmetryDeviationDeg = 2.0)
        assertEquals(RepQuality.PARTIAL_LOCKOUT, repQuality(partial, symmetryToleranceDeg = 10.0))
    }

    @Test
    fun `symmetry unassessable from this camera angle never registers as asymmetric`() {
        assertEquals(RepQuality.CLEAN, repQuality(rep(symmetryDeviationDeg = null), symmetryToleranceDeg = 10.0))
    }
}
