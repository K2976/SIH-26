package com.fitforge.app.feature.repcounter.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.fitforge.app.pose.Landmark
import com.fitforge.app.pose.PoseFrame
import com.fitforge.app.pose.PoseLandmark
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

/**
 * The only file in this app allowed to import `com.google.mediapipe.*`.
 * Everything downstream of [onResult] works with the plain [PoseFrame] data
 * class from `pose/`, which has no Android or MediaPipe dependency and is
 * unit-testable on the JVM.
 *
 * Runs in [RunningMode.LIVE_STREAM]: frames are submitted via [detectAsync]
 * and results come back on MediaPipe's own thread through [onResult].
 */
class PoseLandmarkerHelper(
    context: Context,
    modelVariant: ModelVariant,
    private val onResult: (PoseFrame) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val landmarker: PoseLandmarker = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(modelVariant.assetPath)
                    .setDelegate(Delegate.CPU)
                    .build(),
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setResultListener { result, _ ->
                val landmarks = result.landmarks().firstOrNull()
                if (landmarks != null) {
                    onResult(PoseFrame(result.timestampMs(), landmarks.toPoseMap()))
                }
            }
            .setErrorListener { error -> onError(error.message ?: "pose detection failed") }
            .build(),
    )

    private var lastTimestampMs = 0L

    /** Submits one CameraX frame for detection and closes it. Results arrive asynchronously via [onResult]. */
    fun detectAsync(imageProxy: ImageProxy) {
        val mpImage = BitmapImageBuilder(imageProxy.toRotatedBitmap()).build()
        val processingOptions = ImageProcessingOptions.builder().build()
        // LIVE_STREAM requires a strictly increasing timestamp; the sensor clock's ms-truncated
        // value can repeat under fast bursts, so clamp forward rather than let detectAsync throw.
        val timestampMs = maxOf(imageProxy.imageInfo.timestamp / 1_000_000, lastTimestampMs + 1)
        lastTimestampMs = timestampMs
        imageProxy.close()
        landmarker.detectAsync(mpImage, processingOptions, timestampMs)
    }

    fun close() = landmarker.close()
}

private fun List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>.toPoseMap(): Map<PoseLandmark, Landmark> =
    buildMap {
        for (target in PoseLandmark.entries) {
            val point = getOrNull(target.mediapipeIndex) ?: continue
            put(target, Landmark(point.x(), point.y(), point.z(), point.visibility().orElse(0f)))
        }
    }

private fun ImageProxy.toRotatedBitmap(): Bitmap {
    val bitmap = toBitmap()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
