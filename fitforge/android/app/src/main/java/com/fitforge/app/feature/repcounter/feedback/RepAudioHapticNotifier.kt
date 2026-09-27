package com.fitforge.app.feature.repcounter.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.fitforge.app.pose.RejectionReason

/**
 * Manages tactile (vibration) and auditory (tone) cues during live rep counting.
 *
 * Designed for exercises (e.g. squats, pushups) where lifters cannot look directly
 * at the screen:
 * - Depth reached: short tick so the lifter knows they can reverse upward.
 * - Rep completed: celebratory double-tick / high tone.
 * - Rep rejected: heavier buzz / low double-beep.
 */
class RepAudioHapticNotifier(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to initialize ToneGenerator", e)
        null
    }

    /** Fired when lifter crosses the depth threshold (P >= 0.80) on descent. */
    fun onDepthReached() {
        vibrateOneShot(durationMs = 45, amplitude = 180)
        playTone(ToneGenerator.TONE_PROP_BEEP, durationMs = 60)
    }

    /** Fired when a rep is successfully validated and counted. */
    fun onRepCompleted() {
        vibratePattern(timings = longArrayOf(0, 60, 50, 90), amplitudes = intArrayOf(0, 200, 0, 255))
        playTone(ToneGenerator.TONE_PROP_PROMPT, durationMs = 120)
    }

    /** Fired when a completed excursion is rejected (insufficient depth, lockout, tempo, etc.). */
    fun onRepRejected(reason: RejectionReason) {
        vibrateOneShot(durationMs = 200, amplitude = 220)
        playTone(ToneGenerator.TONE_PROP_BEEP2, durationMs = 180)
    }

    private fun vibrateOneShot(durationMs: Long, amplitude: Int) {
        try {
            vibrator?.let { v ->
                if (!v.hasVibrator()) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Vibration failed", e)
        }
    }

    private fun vibratePattern(timings: LongArray, amplitudes: IntArray) {
        try {
            vibrator?.let { v ->
                if (!v.hasVibrator()) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(timings, -1)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Vibration pattern failed", e)
        }
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        try {
            toneGenerator?.startTone(toneType, durationMs)
        } catch (e: Exception) {
            Log.d(TAG, "Tone playback failed", e)
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.d(TAG, "Error releasing ToneGenerator", e)
        }
    }

    companion object {
        private const val TAG = "RepAudioHaptic"
    }
}
