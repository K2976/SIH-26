package com.fitforge.app.feature.music

import com.fitforge.app.core.database.NO_CYCLE_PHASE
import com.fitforge.app.core.database.TrainingMusicMappingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun mapping(focus: String, phase: String, genre: String, playlistId: String) =
    TrainingMusicMappingEntity(
        trainingFocus = focus,
        cyclePhase = phase,
        genre = genre,
        youtubePlaylistId = playlistId,
        isActive = true,
        updatedAtVersion = 1,
    )

private val MAPPINGS = listOf(
    mapping("strength", NO_CYCLE_PHASE, "Power Rock", "DEFAULT_STRENGTH"),
    mapping("strength", "luteal", "Steady Power Rock", "LUTEAL_STRENGTH"),
    mapping("endurance", NO_CYCLE_PHASE, "Electronic Cardio", "DEFAULT_ENDURANCE"),
)

class MusicViewModelTest {

    @Test
    fun `no training focus yields no recommendation`() {
        val result = computeMusicRecommendation(
            trainingFocus = null,
            cyclePhase = "luteal",
            mappings = MAPPINGS,
        )
        assertNull(result)
    }

    @Test
    fun `null phase falls back to the focus-only row`() {
        val result = computeMusicRecommendation(
            trainingFocus = "strength",
            cyclePhase = null,
            mappings = MAPPINGS,
        )
        assertEquals("Power Rock", result?.genre)
        assertEquals("https://www.youtube.com/playlist?list=DEFAULT_STRENGTH", result?.deepLinkUrl)
        assertNull(result?.cyclePhase)
    }

    @Test
    fun `matching phase row is preferred over the fallback`() {
        val result = computeMusicRecommendation(
            trainingFocus = "strength",
            cyclePhase = "luteal",
            mappings = MAPPINGS,
        )
        assertEquals("Steady Power Rock", result?.genre)
        assertEquals("luteal", result?.cyclePhase)
    }

    @Test
    fun `phase with no dedicated row falls back to the focus-only row`() {
        val result = computeMusicRecommendation(
            trainingFocus = "endurance",
            cyclePhase = "ovulatory",
            mappings = MAPPINGS,
        )
        assertEquals("Electronic Cardio", result?.genre)
        assertNull(result?.cyclePhase)
    }

    @Test
    fun `no mapping at all for the focus yields no recommendation`() {
        val result = computeMusicRecommendation(
            trainingFocus = "hypertrophy",
            cyclePhase = "luteal",
            mappings = MAPPINGS,
        )
        assertNull(result)
    }
}
