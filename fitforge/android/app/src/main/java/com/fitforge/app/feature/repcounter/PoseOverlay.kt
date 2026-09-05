package com.fitforge.app.feature.repcounter

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.fitforge.app.pose.Landmark
import com.fitforge.app.pose.PoseFrame
import com.fitforge.app.pose.PoseLandmark

private val SKELETON_LINES = listOf(
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
    PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
    PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
    PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
    PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
    PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
)

private const val MIN_VISIBLE = 0.5f

/** Draws the tracked skeleton over the camera preview from normalized (0..1) landmark coordinates. */
@Composable
fun PoseOverlay(frame: PoseFrame?, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (frame == null) return@Canvas
        val toOffset = { point: Landmark -> Offset(point.x * size.width, point.y * size.height) }

        for ((from, to) in SKELETON_LINES) {
            val a = frame.landmarks[from] ?: continue
            val b = frame.landmarks[to] ?: continue
            if (a.visibility < MIN_VISIBLE || b.visibility < MIN_VISIBLE) continue
            drawLine(Color(0xFF00E5A0), toOffset(a), toOffset(b), strokeWidth = 6f)
        }
        for (point in frame.landmarks.values) {
            if (point.visibility < MIN_VISIBLE) continue
            drawCircle(Color.White, radius = 10f, center = toOffset(point))
        }
    }
}
