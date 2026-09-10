package com.fitforge.app.feature.repcounter

import com.fitforge.app.pose.CompletedRep
import com.fitforge.app.pose.PoseFrame
import com.fitforge.app.pose.RejectedRep
import com.fitforge.app.pose.repQuality
import kotlinx.serialization.Serializable

/**
 * Training-data shape for a completed camera-tracked set — see Prompt C /
 * `fitforge/ml/README.md`. Captured alongside, never instead of, the
 * deterministic count and feedback from Prompt A; nothing here is shown to
 * the user or read back by this app. One frame's raw landmark positions,
 * `PoseLandmark` name -> `[x, y, z, visibility]`.
 */
@Serializable
data class RecordedLandmarkFrame(val tMs: Long, val landmarks: Map<String, List<Float>>)

/**
 * One rep's deterministic outcome, recorded as a training label. [quality]
 * is only ever the rule-based proxy from [repQuality] — see that function's
 * doc comment for why it's a label, not a new measurement.
 */
@Serializable
data class RecordedRepLabel(
    val startMs: Long,
    val endMs: Long,
    val outcome: String,
    val depthReached: Boolean? = null,
    val lockoutReached: Boolean? = null,
    val symmetryDeviationDeg: Double? = null,
    val tempoMs: Long? = null,
    val quality: String? = null,
    val reason: String? = null,
)

fun PoseFrame.toRecordedFrame(): RecordedLandmarkFrame = RecordedLandmarkFrame(
    tMs = timestampMs,
    landmarks = landmarks.entries.associate { (landmark, point) ->
        landmark.name to listOf(point.x, point.y, point.z, point.visibility)
    },
)

fun CompletedRep.toRecordedRepLabel(symmetryToleranceDeg: Double): RecordedRepLabel = RecordedRepLabel(
    startMs = startMs,
    endMs = endMs,
    outcome = "completed",
    depthReached = depthReached,
    lockoutReached = lockoutReached,
    symmetryDeviationDeg = symmetryDeviationDeg,
    tempoMs = tempoMs,
    quality = repQuality(this, symmetryToleranceDeg).name.lowercase(),
)

fun RejectedRep.toRecordedRepLabel(): RecordedRepLabel = RecordedRepLabel(
    startMs = startMs,
    endMs = endMs,
    outcome = "rejected",
    reason = reason.name.lowercase(),
)
