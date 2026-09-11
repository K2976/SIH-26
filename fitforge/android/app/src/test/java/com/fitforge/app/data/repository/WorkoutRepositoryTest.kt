package com.fitforge.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutRepositoryTest {
    @Test
    fun `a camera-logged set is identical to a manual one except source, rom, and tempo`() {
        val manual = buildSetLogEntity(
            sessionUuid = "session-1",
            exerciseSlug = "bodyweight-squat",
            setIndex = 0,
            reps = 10,
            weightKg = null,
            isWarmup = false,
            source = "user_created",
            romAchieved = null,
            tempoMs = null,
        )
        val camera = buildSetLogEntity(
            sessionUuid = "session-1",
            exerciseSlug = "bodyweight-squat",
            setIndex = 0,
            reps = 10,
            weightKg = null,
            isWarmup = false,
            source = CAMERA_SOURCE,
            romAchieved = "full",
            tempoMs = 1400,
        )

        // Every shared column matches — same shape, same table, same write path.
        assertEquals(manual.sessionClientUuid, camera.sessionClientUuid)
        assertEquals(manual.exerciseSlug, camera.exerciseSlug)
        assertEquals(manual.setIndex, camera.setIndex)
        assertEquals(manual.reps, camera.reps)
        assertEquals(manual.weightKg, camera.weightKg)
        assertEquals(manual.isWarmup, camera.isWarmup)
        assertEquals(manual.diary.utcOffset, camera.diary.utcOffset)
        assertEquals(manual.diary.syncStatus, camera.diary.syncStatus)
        assertNull(manual.diary.supersedes)
        assertNull(camera.diary.supersedes)

        // Manual entry never populates rom/tempo — only the camera path can.
        assertNull(manual.romAchieved)
        assertNull(manual.tempoMs)

        // The only fields that are allowed to differ.
        assertEquals("user_created", manual.diary.source)
        assertEquals(CAMERA_SOURCE, camera.diary.source)
        assertEquals("full", camera.romAchieved)
        assertEquals(1400, camera.tempoMs)
        assertNotEquals(manual.diary.source, camera.diary.source)
    }
}
