package com.fitforge.app.feature.repcounter

import com.fitforge.app.pose.CameraPosition
import com.fitforge.app.pose.PoseLandmark
import com.fitforge.app.pose.RejectionReason

enum class DemoRepPhase { HOLD_STILL, PERFORMING }

/** Per-rep feedback shown live during a set — depth/lockout/symmetry only, per the scoring rules. No form score, ever. */
data class RepFeedback(
    val depthReached: Boolean,
    val lockoutReached: Boolean,
    val symmetryDeviationDeg: Double?,
    val symmetryToleranceDeg: Double,
    val cameraPosition: CameraPosition,
    val rejectionReason: RejectionReason? = null,
)

sealed interface RepCounterUiState {
    data object Loading : RepCounterUiState

    /** Exercise has no pose_config, or a real detection failure — the message says why. */
    data class Refused(val reason: String) : RepCounterUiState

    data class PositionGuide(val cameraPosition: CameraPosition, val hasCachedCalibration: Boolean) : RepCounterUiState

    /** Cached calibration -> this is the whole flow for a repeat session. No cache -> falls through to [DemoRep]. */
    data class VisibilityCheck(val hasCachedCalibration: Boolean) : RepCounterUiState

    data class DemoRep(val phase: DemoRepPhase, val message: String? = null) : RepCounterUiState

    data class Ready(val restAngleDeg: Double, val peakAngleDeg: Double) : RepCounterUiState

    data class Counting(val repCount: Int, val lastFeedback: RepFeedback? = null) : RepCounterUiState

    data class SetComplete(val repCount: Int, val romAchieved: String, val avgTempoMs: Int?) : RepCounterUiState
}
