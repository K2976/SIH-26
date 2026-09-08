@file:Suppress("LongParameterList")

package com.fitforge.app.pose

import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds synthetic landmarks that produce an exact, chosen joint angle, so
 * tests can drive the angle-calculation + state-machine pipeline with known
 * ground truth instead of guessing coordinates.
 *
 * For an angle A at the vertex: place `from` straight "up" from the vertex,
 * and `to` at a bearing of A degrees off that same ray. `angleBetweenDeg`
 * then reports exactly A by construction.
 */
private const val LIMB_LENGTH = 0.3f
private const val FULL_VISIBILITY = 1.0f

private fun pointAtAngle(vertex: Pair<Float, Float>, angleDeg: Double): Pair<Float, Float> {
    val rad = Math.toRadians(angleDeg)
    return vertex.first + (LIMB_LENGTH * sin(rad)).toFloat() to vertex.second - (LIMB_LENGTH * cos(rad)).toFloat()
}

private fun landmark(point: Pair<Float, Float>) = Landmark(point.first, point.second, 0f, FULL_VISIBILITY)

/** A side-camera squat frame: only the left hip/knee/ankle triple is visible, at the given knee angle. */
fun squatFrame(timestampMs: Long, kneeAngleDeg: Double): PoseFrame {
    val knee = 0.5f to 0.5f
    val hip = knee.first to knee.second - LIMB_LENGTH
    val ankle = pointAtAngle(knee, kneeAngleDeg)
    return PoseFrame(
        timestampMs,
        mapOf(
            PoseLandmark.LEFT_HIP to landmark(hip),
            PoseLandmark.LEFT_KNEE to landmark(knee),
            PoseLandmark.LEFT_ANKLE to landmark(ankle),
        ),
    )
}

/** A front-camera curl frame: both elbows visible, independently, for symmetry tests. */
fun curlFrame(timestampMs: Long, leftElbowDeg: Double, rightElbowDeg: Double): PoseFrame {
    val leftElbow = 0.4f to 0.5f
    val rightElbow = 0.6f to 0.5f
    return PoseFrame(
        timestampMs,
        mapOf(
            PoseLandmark.LEFT_SHOULDER to landmark(leftElbow.first to leftElbow.second - LIMB_LENGTH),
            PoseLandmark.LEFT_ELBOW to landmark(leftElbow),
            PoseLandmark.LEFT_WRIST to landmark(pointAtAngle(leftElbow, leftElbowDeg)),
            PoseLandmark.RIGHT_SHOULDER to landmark(rightElbow.first to rightElbow.second - LIMB_LENGTH),
            PoseLandmark.RIGHT_ELBOW to landmark(rightElbow),
            PoseLandmark.RIGHT_WRIST to landmark(pointAtAngle(rightElbow, rightElbowDeg)),
        ),
    )
}

val SQUAT_CONFIG = ExercisePoseConfig(
    cameraPosition = CameraPosition.SIDE,
    angleJoint = AngleJoint(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
    minRepSeconds = 0.6,
    maxRepSeconds = 8.0,
    symmetryToleranceDeg = 10.0,
)

val CURL_CONFIG = ExercisePoseConfig(
    cameraPosition = CameraPosition.FRONT,
    angleJoint = AngleJoint(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
    minRepSeconds = 0.6,
    maxRepSeconds = 8.0,
    symmetryToleranceDeg = 10.0,
)

/** rest ~standing/arm-extended, peak ~squat depth/full curl — shared by the squat-shaped tests. */
val SQUAT_CALIBRATION = CalibrationProfile(
    "bodyweight-squat",
    restAngleDeg = 170.0,
    peakAngleDeg = 90.0,
    calibratedAtMs = 0,
)

/** Linear ramp of representative angle from [fromDeg] to [toDeg] over [durationMs], sampled every [dtMs]. */
fun rampAngles(fromDeg: Double, toDeg: Double, startMs: Long, durationMs: Long, dtMs: Long = 33): List<AngleSample> {
    val steps = (durationMs / dtMs).toInt().coerceAtLeast(1)
    return (0..steps).map { i ->
        val t = i.toDouble() / steps
        val angle = fromDeg + (toDeg - fromDeg) * t
        AngleSample(startMs + i * dtMs, angle, null)
    }
}

fun holdAngle(angleDeg: Double, startMs: Long, durationMs: Long, dtMs: Long = 33): List<AngleSample> {
    val steps = (durationMs / dtMs).toInt().coerceAtLeast(1)
    return (0..steps).map { i -> AngleSample(startMs + i * dtMs, angleDeg, null) }
}

/** Like [rampAngles], but drives both sides independently — for symmetry tests. */
fun twoSidedRamp(
    leftFrom: Double,
    leftTo: Double,
    rightFrom: Double,
    rightTo: Double,
    startMs: Long,
    durationMs: Long,
    dtMs: Long = 33,
): List<AngleSample> {
    val steps = (durationMs / dtMs).toInt().coerceAtLeast(1)
    return (0..steps).map { i ->
        val t = i.toDouble() / steps
        AngleSample(startMs + i * dtMs, leftFrom + (leftTo - leftFrom) * t, rightFrom + (rightTo - rightFrom) * t)
    }
}

/** Runs the real per-frame pipeline: raw landmarks -> angle calc -> smoothing -> state machine. */
fun runFramePipeline(
    config: ExercisePoseConfig,
    calibration: CalibrationProfile,
    frames: List<PoseFrame>,
): RepMachineResult {
    var previousSmoothed: AngleSample? = null
    var state = RepMachineState()
    val completed = mutableListOf<CompletedRep>()
    val rejected = mutableListOf<RejectedRep>()
    for (frame in frames) {
        val raw = computeAngleSample(frame, config)
        val smoothed = smoothAngleSample(previousSmoothed, raw)
        previousSmoothed = smoothed
        val result = stepRepStateMachine(state, config, calibration, smoothed)
        state = result.state
        when (val event = result.event) {
            is RepEvent.Completed -> completed += event.rep
            is RepEvent.Rejected -> rejected += event.rep
            null -> Unit
        }
    }
    when (val last = finalizeRepStateMachine(state, config)) {
        is RepEvent.Completed -> completed += last.rep
        is RepEvent.Rejected -> rejected += last.rep
        null -> Unit
    }
    return RepMachineResult(completed, rejected)
}

/** One down-then-up squat cycle, in landmark form, starting and ending at [restDeg]. */
fun squatCycleFrames(
    startMs: Long,
    downMs: Long = 600,
    upMs: Long = 600,
    bottomDeg: Double = 90.0,
    restDeg: Double = 170.0,
    dtMs: Long = 33,
): List<PoseFrame> {
    val downSteps = (downMs / dtMs).toInt().coerceAtLeast(1)
    val upSteps = (upMs / dtMs).toInt().coerceAtLeast(1)
    val down = (0..downSteps).map { i ->
        val t = i.toDouble() / downSteps
        squatFrame(startMs + i * dtMs, restDeg + (bottomDeg - restDeg) * t)
    }
    val upStart = startMs + downSteps * dtMs
    val up = (1..upSteps).map { i ->
        val t = i.toDouble() / upSteps
        squatFrame(upStart + i * dtMs, bottomDeg + (restDeg - bottomDeg) * t)
    }
    return down + up
}
