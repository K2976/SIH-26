@file:Suppress("ReturnCount", "TooManyFunctions")

package com.fitforge.app.feature.repcounter

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.core.database.CalibrationProfileDao
import com.fitforge.app.core.database.CalibrationProfileEntity
import com.fitforge.app.data.repository.CAMERA_SOURCE
import com.fitforge.app.data.repository.WorkoutRepository
import com.fitforge.app.data.repository.toPoseConfig
import com.fitforge.app.feature.repcounter.camera.ModelVariant
import com.fitforge.app.feature.repcounter.camera.PoseLandmarkerHelper
import com.fitforge.app.pose.AngleSample
import com.fitforge.app.pose.CalibrationProfile
import com.fitforge.app.pose.CalibrationResult
import com.fitforge.app.pose.CompletedRep
import com.fitforge.app.pose.ExercisePoseConfig
import com.fitforge.app.pose.PoseFrame
import com.fitforge.app.pose.PoseLandmark
import com.fitforge.app.pose.RepEvent
import com.fitforge.app.pose.RepMachineState
import com.fitforge.app.pose.VisibilityResult
import com.fitforge.app.pose.averageRestAngle
import com.fitforge.app.pose.checkVisibility
import com.fitforge.app.pose.computeAngleSample
import com.fitforge.app.pose.deriveCalibration
import com.fitforge.app.pose.finalizeRepStateMachine
import com.fitforge.app.pose.representativeAngle
import com.fitforge.app.pose.smoothAngleSample
import com.fitforge.app.pose.stepRepStateMachine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

private const val VISIBILITY_CONFIRM_FRAMES = 10
private const val VISIBILITY_TIMEOUT_MS = 8_000L
private const val HOLD_STILL_MS = 1_200L
private const val DEMO_REP_MS = 6_000L

/**
 * Owns the calibrate-then-count flow for one exercise. Frame processing
 * (visibility checks, smoothing, the rep state machine) is delegated
 * entirely to `pose/`'s pure functions — this class only tracks *which*
 * phase we're in and feeds each incoming [PoseFrame] to the right one.
 */
@HiltViewModel
class RepCounterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutRepository: WorkoutRepository,
    private val calibrationDao: CalibrationProfileDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), ImageAnalysis.Analyzer {

    private val sessionUuid: String = checkNotNull(savedStateHandle["sessionUuid"])
    private val exerciseSlug: String = checkNotNull(savedStateHandle["slug"])

    private val _uiState = MutableStateFlow<RepCounterUiState>(RepCounterUiState.Loading)
    val uiState: StateFlow<RepCounterUiState> = _uiState.asStateFlow()

    private val _overlayFrame = MutableStateFlow<PoseFrame?>(null)
    val overlayFrame: StateFlow<PoseFrame?> = _overlayFrame.asStateFlow()

    private var poseConfig: ExercisePoseConfig? = null
    private var calibration: CalibrationProfile? = null
    private var landmarkerHelper: PoseLandmarkerHelper? = null

    private var consecutiveVisibleFrames = 0
    private var visibilityCheckStartMs = 0L

    private var demoPhaseStartMs = 0L
    private var pendingRestAngle: Double? = null
    private val restSamples = mutableListOf<AngleSample>()
    private val demoRepAngles = mutableListOf<Double>()

    private var previousSmoothed: AngleSample? = null
    private var machineState = RepMachineState()
    private val completedReps = mutableListOf<CompletedRep>()

    init {
        viewModelScope.launch {
            val exercise = workoutRepository.exerciseBySlug(exerciseSlug)
            val config = exercise?.toPoseConfig()
            if (config == null) {
                _uiState.value = RepCounterUiState.Refused("This exercise doesn't support camera counting yet.")
                return@launch
            }
            poseConfig = config
            val cached = calibrationDao.getForExercise(exerciseSlug)
            calibration = cached?.toCalibrationProfile()
            _uiState.value = RepCounterUiState.PositionGuide(config.cameraPosition, cached != null)
        }
    }

    /** User confirmed they're positioned per the on-screen camera_position guidance. */
    fun onPositioned() {
        if (poseConfig == null) return
        consecutiveVisibleFrames = 0
        visibilityCheckStartMs = System.currentTimeMillis()
        _uiState.value = RepCounterUiState.VisibilityCheck(hasCachedCalibration = calibration != null)
        if (landmarkerHelper == null) {
            landmarkerHelper = PoseLandmarkerHelper(
                context = context,
                modelVariant = ModelVariant.DEFAULT,
                onResult = ::handleFrame,
                onError = { message -> _uiState.value = RepCounterUiState.Refused(message) },
            )
        }
    }

    /** [ImageAnalysis.Analyzer] entry point — called on CameraX's analysis executor thread, not the main thread. */
    override fun analyze(image: ImageProxy) {
        val helper = landmarkerHelper
        if (helper == null) {
            image.close()
            return
        }
        helper.detectAsync(image)
    }

    private fun handleFrame(frame: PoseFrame) {
        _overlayFrame.value = frame
        val config = poseConfig ?: return
        when (_uiState.value) {
            is RepCounterUiState.VisibilityCheck -> handleVisibilityCheck(frame, config)
            is RepCounterUiState.DemoRep -> handleDemoRep(frame, config)
            is RepCounterUiState.Counting -> handleCounting(frame, config)
            else -> Unit
        }
    }

    private fun handleVisibilityCheck(frame: PoseFrame, config: ExercisePoseConfig) {
        when (val visibility = checkVisibility(frame, config)) {
            is VisibilityResult.Visible -> {
                consecutiveVisibleFrames++
                if (consecutiveVisibleFrames >= VISIBILITY_CONFIRM_FRAMES) proceedPastVisibilityCheck()
            }
            is VisibilityResult.NotVisible -> {
                consecutiveVisibleFrames = 0
                if (System.currentTimeMillis() - visibilityCheckStartMs > VISIBILITY_TIMEOUT_MS) {
                    _uiState.value = RepCounterUiState.Refused(missingJointsMessage(visibility.missing))
                }
            }
        }
    }

    private fun proceedPastVisibilityCheck() {
        val cal = calibration
        if (cal != null) {
            _uiState.value = RepCounterUiState.Ready(cal.restAngleDeg, cal.peakAngleDeg)
        } else {
            restSamples.clear()
            demoPhaseStartMs = System.currentTimeMillis()
            _uiState.value = RepCounterUiState.DemoRep(DemoRepPhase.HOLD_STILL)
        }
    }

    private fun handleDemoRep(frame: PoseFrame, config: ExercisePoseConfig) {
        val phase = (_uiState.value as? RepCounterUiState.DemoRep)?.phase ?: return
        val raw = computeAngleSample(frame, config)
        val elapsed = System.currentTimeMillis() - demoPhaseStartMs

        when (phase) {
            DemoRepPhase.HOLD_STILL -> {
                restSamples += raw
                if (elapsed >= HOLD_STILL_MS) {
                    val rest = averageRestAngle(restSamples)
                    if (rest == null) {
                        restartHoldStill("Lost sight of you — hold still, in frame, and try again.")
                    } else {
                        pendingRestAngle = rest
                        demoRepAngles.clear()
                        demoPhaseStartMs = System.currentTimeMillis()
                        _uiState.value = RepCounterUiState.DemoRep(DemoRepPhase.PERFORMING)
                    }
                }
            }
            DemoRepPhase.PERFORMING -> {
                representativeAngle(raw)?.let { demoRepAngles += it }
                if (elapsed >= DEMO_REP_MS) {
                    val rest = pendingRestAngle ?: return restartHoldStill(null)
                    val result = deriveCalibration(
                        exerciseSlug,
                        rest,
                        demoRepAngles,
                        System.currentTimeMillis(),
                    )
                    when (result) {
                        is CalibrationResult.Success -> {
                            calibration = result.profile
                            viewModelScope.launch { calibrationDao.upsert(result.profile.toEntity()) }
                            _uiState.value = RepCounterUiState.Ready(
                                result.profile.restAngleDeg,
                                result.profile.peakAngleDeg,
                            )
                        }
                        is CalibrationResult.InsufficientRange -> {
                            val achieved = result.achievedDeg.roundToInt()
                            restartHoldStill(
                                "Only ${achieved}° of movement detected — repeat the rep, " +
                                    "moving through your full range.",
                            )
                        }
                    }
                }
            }
        }
    }

    private fun restartHoldStill(message: String?) {
        restSamples.clear()
        demoPhaseStartMs = System.currentTimeMillis()
        _uiState.value = RepCounterUiState.DemoRep(DemoRepPhase.HOLD_STILL, message)
    }

    /** From [RepCounterUiState.Ready]: begin counting reps for a new set. */
    fun startSet() {
        previousSmoothed = null
        machineState = RepMachineState()
        completedReps.clear()
        _uiState.value = RepCounterUiState.Counting(repCount = 0)
    }

    private fun handleCounting(frame: PoseFrame, config: ExercisePoseConfig) {
        val cal = calibration ?: return
        val raw = computeAngleSample(frame, config)
        val smoothed = smoothAngleSample(previousSmoothed, raw)
        previousSmoothed = smoothed
        val result = stepRepStateMachine(machineState, config, cal, smoothed)
        machineState = result.state
        when (val event = result.event) {
            is RepEvent.Completed -> {
                completedReps += event.rep
                _uiState.value = RepCounterUiState.Counting(completedReps.size, event.rep.toFeedback(config))
            }
            is RepEvent.Rejected -> {
                val current = _uiState.value as? RepCounterUiState.Counting ?: return
                _uiState.value = current.copy(
                    lastFeedback = RepFeedback(
                        depthReached = false,
                        lockoutReached = false,
                        symmetryDeviationDeg = null,
                        symmetryToleranceDeg = config.symmetryToleranceDeg,
                        cameraPosition = config.cameraPosition,
                        rejectionReason = event.rep.reason,
                    ),
                )
            }
            null -> Unit
        }
    }

    /** Ends the set, finalizing whatever rep was still in progress, and shows the summary. */
    fun finishSet() {
        val config = poseConfig ?: return
        val cal = calibration ?: return
        (finalizeRepStateMachine(machineState, config) as? RepEvent.Completed)?.let { completedReps += it.rep }
        machineState = RepMachineState()

        if (completedReps.isEmpty()) {
            _uiState.value = RepCounterUiState.Ready(cal.restAngleDeg, cal.peakAngleDeg)
            return
        }
        val romAchieved = if (completedReps.all { it.depthReached && it.lockoutReached }) "full" else "partial"
        val avgTempoMs = completedReps.map { it.tempoMs }.average().roundToInt()
        _uiState.value = RepCounterUiState.SetComplete(completedReps.size, romAchieved, avgTempoMs)
    }

    /** Writes the finished set exactly like manual entry, except `source`. */
    fun saveSet() {
        val state = _uiState.value as? RepCounterUiState.SetComplete ?: return
        val cal = calibration ?: return
        viewModelScope.launch {
            workoutRepository.addSet(
                sessionUuid = sessionUuid,
                exerciseSlug = exerciseSlug,
                reps = state.repCount,
                weightKg = null,
                source = CAMERA_SOURCE,
                romAchieved = state.romAchieved,
                tempoMs = state.avgTempoMs,
            )
            _uiState.value = RepCounterUiState.Ready(cal.restAngleDeg, cal.peakAngleDeg)
        }
    }

    fun discardSet() {
        val cal = calibration ?: return
        _uiState.value = RepCounterUiState.Ready(cal.restAngleDeg, cal.peakAngleDeg)
    }

    override fun onCleared() {
        landmarkerHelper?.close()
    }
}

private fun CompletedRep.toFeedback(config: ExercisePoseConfig) = RepFeedback(
    depthReached = depthReached,
    lockoutReached = lockoutReached,
    symmetryDeviationDeg = symmetryDeviationDeg,
    symmetryToleranceDeg = config.symmetryToleranceDeg,
    cameraPosition = config.cameraPosition,
)

private fun CalibrationProfileEntity.toCalibrationProfile() = CalibrationProfile(
    exerciseSlug,
    restAngleDeg,
    peakAngleDeg,
    calibratedAtMs,
)

private fun CalibrationProfile.toEntity() = CalibrationProfileEntity(
    exerciseSlug,
    restAngleDeg,
    peakAngleDeg,
    calibratedAtMs,
)

private fun missingJointsMessage(missing: Set<PoseLandmark>): String {
    val names = missing.joinToString(", ") { it.name.lowercase().replace('_', ' ') }
    return "Can't start: $names not visible. Reposition so your whole side is in frame, then try again."
}
