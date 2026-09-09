package com.fitforge.app.feature.repcounter.camera

/**
 * The three bundled pose_landmarker models — see docs/DECISIONS.md for the
 * FPS/accuracy tradeoff measured per variant.
 */
enum class ModelVariant(val assetPath: String, val label: String) {
    LITE("models/pose_landmarker_lite.task", "Lite"),
    FULL("models/pose_landmarker_full.task", "Full"),
    HEAVY("models/pose_landmarker_heavy.task", "Heavy"),
    ;

    companion object {
        // ponytail: provisional pick, not an empirical one — no device was available to run
        // PoseLandmarkerBenchmarkTest in the environment this slice was built in (see
        // docs/DECISIONS.md). FULL is MediaPipe's own documented middle ground between LITE
        // and HEAVY. Re-pick after running the benchmark on a real target device.
        val DEFAULT = FULL
    }
}
