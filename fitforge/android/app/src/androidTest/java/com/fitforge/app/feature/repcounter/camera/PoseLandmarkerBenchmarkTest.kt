package com.fitforge.app.feature.repcounter.camera

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val WARMUP_ITERATIONS = 5
private const val TIMED_ITERATIONS = 20

/**
 * Measures real inference throughput and per-model landmark agreement for
 * all three bundled pose_landmarker variants, on whatever device this runs
 * on. This is the harness docs/DECISIONS.md's benchmark section refers to —
 * see that file for the actual recorded numbers (or their absence) and why.
 *
 * Run with:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     --tests "com.fitforge.app.feature.repcounter.camera.PoseLandmarkerBenchmarkTest"
 *
 * Results are written to <app-external-files-dir>/pose_benchmark_results.txt
 * on the device — `adb pull` it, or read it straight from logcat (tag
 * "PoseBenchmark"). Requires a connected device or running emulator; there
 * is no fallback that fabricates numbers when one isn't available.
 */
@RunWith(AndroidJUnit4::class)
class PoseLandmarkerBenchmarkTest {

    @Test
    fun benchmarkAllModelVariants() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = context.assets.open("pose_benchmark_sample.jpg").use { BitmapFactory.decodeStream(it) }
        val mpImage = BitmapImageBuilder(bitmap).build()

        val report = StringBuilder()
        report.appendLine("Pose landmarker benchmark — device: ${android.os.Build.MODEL} (${android.os.Build.HARDWARE}), Android ${android.os.Build.VERSION.RELEASE}")
        report.appendLine("Sample image: pose_benchmark_sample.jpg (${bitmap.width}x${bitmap.height}), CPU delegate, ${TIMED_ITERATIONS} timed iterations after ${WARMUP_ITERATIONS} warmup")
        report.appendLine()

        for (variant in ModelVariant.entries) {
            val landmarker = PoseLandmarker.createFromOptions(
                context,
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(variant.assetPath)
                            .setDelegate(Delegate.CPU)
                            .build(),
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumPoses(1)
                    .build(),
            )

            repeat(WARMUP_ITERATIONS) { landmarker.detect(mpImage) }

            val start = System.nanoTime()
            var lastResult = landmarker.detect(mpImage)
            repeat(TIMED_ITERATIONS - 1) { lastResult = landmarker.detect(mpImage) }
            val elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0
            val fps = TIMED_ITERATIONS / elapsedSeconds

            val landmarks = lastResult.landmarks().firstOrNull()
            val avgVisibility = landmarks
                ?.mapNotNull { it.visibility().orElse(null) }
                ?.average()

            report.appendLine("## ${variant.name}")
            report.appendLine("- model: ${variant.assetPath}")
            report.appendLine("- fps: %.1f (%.1fms/frame)".format(fps, 1000.0 / fps))
            report.appendLine("- pose detected: ${landmarks != null}")
            report.appendLine("- mean landmark visibility: ${avgVisibility?.let { "%.3f".format(it) } ?: "n/a"}")
            report.appendLine()

            landmarker.close()
        }

        val output = report.toString()
        android.util.Log.i("PoseBenchmark", output)
        File(context.getExternalFilesDir(null), "pose_benchmark_results.txt").writeText(output)
    }
}
