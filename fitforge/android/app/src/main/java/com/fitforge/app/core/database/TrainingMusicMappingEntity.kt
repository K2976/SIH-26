package com.fitforge.app.core.database

import androidx.room.Entity

/** Sentinel for the focus-only fallback row — Room primary-key columns
 * can't be nullable, so the server's null cycle_phase is stored as "" and
 * converted back to null at the repository boundary (see MusicRecommender). */
const val NO_CYCLE_PHASE = ""

/**
 * Local cache of the (training_focus, cycle_phase) -> genre/playlist lookup.
 * Seeded from the assets bundle on first launch, kept fresh by the same
 * daily delta-sync worker that refreshes exercises. cyclePhase == NO_CYCLE_PHASE
 * is the focus-only fallback row, used when phase isn't available.
 */
@Entity(tableName = "training_music_mappings", primaryKeys = ["trainingFocus", "cyclePhase"])
data class TrainingMusicMappingEntity(
    val trainingFocus: String,
    val cyclePhase: String,
    val genre: String,
    val youtubePlaylistId: String,
    val isActive: Boolean,
    val updatedAtVersion: Int,
)
