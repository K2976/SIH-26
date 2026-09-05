package com.fitforge.app.feature.repcounter.camera

/** The three bundled pose_landmarker models — see docs/DECISIONS.md for the FPS/accuracy tradeoff measured per variant. */
enum class ModelVariant(val assetPath: String, val label: String) {
    LITE("models/pose_landmarker_lite.task", "Lite"),
    FULL("models/pose_landmarker_full.task", "Full"),
    HEAVY("models/pose_landmarker_heavy.task", "Heavy"),
    ;

    companion object {
        /** Chosen from the on-device benchmark in docs/DECISIONS.md — the fastest variant with acceptable joint-angle accuracy. */
        val DEFAULT = FULL
    }
}
