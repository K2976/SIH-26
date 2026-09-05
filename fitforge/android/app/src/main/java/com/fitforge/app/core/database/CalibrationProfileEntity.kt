package com.fitforge.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached result of the one-time demo-rep calibration (see `pose.CalibrationProfile`).
 * Keyed by exercise slug only — one device, one lifter. Later sessions read this and
 * run only a fast visibility re-check instead of repeating the full flow.
 */
@Entity(tableName = "calibration_profiles")
data class CalibrationProfileEntity(
    @PrimaryKey val exerciseSlug: String,
    val restAngleDeg: Double,
    val peakAngleDeg: Double,
    val calibratedAtMs: Long,
)
