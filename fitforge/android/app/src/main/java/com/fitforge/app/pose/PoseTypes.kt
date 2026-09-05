package com.fitforge.app.pose

/**
 * The 12 BlazePose landmarks FitForge's rep-counting exercises need.
 * `mediapipeIndex` matches the official 33-point topology so the Android
 * (impure) layer can map a `PoseLandmarkerResult` onto this enum with a
 * single array lookup. Nothing in this file or package depends on MediaPipe
 * types — this is the pure boundary.
 */
enum class PoseLandmark(val mediapipeIndex: Int) {
    LEFT_SHOULDER(11),
    RIGHT_SHOULDER(12),
    LEFT_ELBOW(13),
    RIGHT_ELBOW(14),
    LEFT_WRIST(15),
    RIGHT_WRIST(16),
    LEFT_HIP(23),
    RIGHT_HIP(24),
    LEFT_KNEE(25),
    RIGHT_KNEE(26),
    LEFT_ANKLE(27),
    RIGHT_ANKLE(28),
    ;

    /** The other side's version of this landmark, e.g. LEFT_KNEE -> RIGHT_KNEE. */
    fun mirror(): PoseLandmark = when (this) {
        LEFT_SHOULDER -> RIGHT_SHOULDER
        RIGHT_SHOULDER -> LEFT_SHOULDER
        LEFT_ELBOW -> RIGHT_ELBOW
        RIGHT_ELBOW -> LEFT_ELBOW
        LEFT_WRIST -> RIGHT_WRIST
        RIGHT_WRIST -> LEFT_WRIST
        LEFT_HIP -> RIGHT_HIP
        RIGHT_HIP -> LEFT_HIP
        LEFT_KNEE -> RIGHT_KNEE
        RIGHT_KNEE -> LEFT_KNEE
        LEFT_ANKLE -> RIGHT_ANKLE
        RIGHT_ANKLE -> LEFT_ANKLE
    }
}

/** Normalized image-space landmark, as MediaPipe emits it (0..1 for x/y). */
data class Landmark(val x: Float, val y: Float, val z: Float, val visibility: Float)

/** One pose detection result, timestamped against the camera frame clock. */
data class PoseFrame(val timestampMs: Long, val landmarks: Map<PoseLandmark, Landmark>)

enum class CameraPosition {
    FRONT,
    SIDE,
    FORTY_FIVE_DEGREE,
}

/**
 * The angle a rep is measured by: the interior angle at [vertex] formed by
 * rays to [from] and [to]. Defined on the left side; the right side is
 * derived via [PoseLandmark.mirror] so symmetry comparison is automatic.
 */
data class AngleJoint(val from: PoseLandmark, val vertex: PoseLandmark, val to: PoseLandmark) {
    fun mirrored(): AngleJoint = AngleJoint(from.mirror(), vertex.mirror(), to.mirror())
}

/**
 * Static, per-exercise definition — what to measure and from where. Ships
 * from the backend in `Exercise.pose_config`. User-specific thresholds live
 * in [CalibrationProfile], derived once from this shape.
 *
 * There's no "direction" flag: whether the rep's working extreme is a
 * smaller or larger angle than rest depends on which joint and which
 * exercise (a squat's knee angle shrinks going down; a pull-up's elbow
 * angle also shrinks, but "down" is the resting hang, not the working
 * extreme). Calibration captures both raw numbers and the state machine
 * works in normalized progress between them, so no exercise-specific sign
 * needs to be hand-authored.
 */
data class ExercisePoseConfig(
    val cameraPosition: CameraPosition,
    val angleJoint: AngleJoint,
    val minRepSeconds: Double,
    val maxRepSeconds: Double,
    val symmetryToleranceDeg: Double,
)

/** One frame's worth of smoothed angle, per side — null when that side wasn't visible. */
data class AngleSample(val timestampMs: Long, val leftDeg: Double?, val rightDeg: Double?)

/**
 * User-specific thresholds derived once from a supervised demo rep, then
 * cached. [restAngleDeg] is the angle at the start of the demo rep (before
 * any movement); [peakAngleDeg] is the extreme reached during it. Which one
 * is numerically larger depends on the exercise/joint — see
 * [ExercisePoseConfig] — so nothing here assumes a sign.
 */
data class CalibrationProfile(
    val exerciseSlug: String,
    val restAngleDeg: Double,
    val peakAngleDeg: Double,
    val calibratedAtMs: Long,
)
