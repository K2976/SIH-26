@file:Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber")

package com.fitforge.app.feature.repcounter

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.pose.CameraPosition
import com.fitforge.app.pose.RejectionReason
import java.util.concurrent.Executors

@Composable
fun RepCounterScreen(
    onDone: () -> Unit,
    viewModel: RepCounterViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreviewWithOverlay(viewModel)
        }

        Surface(color = Color.Black.copy(alpha = 0.55f), modifier = Modifier.fillMaxSize()) {
            RepCounterControls(
                viewModel,
                onDone,
                hasCameraPermission,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
            )
        }
    }
}

@Composable
private fun CameraPreviewWithOverlay(viewModel: RepCounterViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val overlayFrame by viewModel.overlayFrame.collectAsStateWithLifecycle()
    val isFrontCamera by viewModel.isFrontCamera.collectAsStateWithLifecycle()
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(isFrontCamera, previewViewRef) {
        val previewView = previewViewRef ?: return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, viewModel) }

                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    analysis,
                )
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewViewRef = it }
            },
            modifier = Modifier.fillMaxSize(),
        )
        PoseOverlay(
            frame = overlayFrame,
            modifier = Modifier.fillMaxSize(),
            mirror = isFrontCamera,
        )
    }
}

@Composable
private fun RepCounterControls(
    viewModel: RepCounterViewModel,
    onDone: () -> Unit,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isFrontCamera by viewModel.isFrontCamera.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom,
    ) {
        if (!hasCameraPermission) {
            Text(
                "Camera access is required to count reps.",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Grant camera permission")
            }
            return@Column
        }

        when (val state = uiState) {
            is RepCounterUiState.Loading -> Text("Loading…", color = Color.White)

            is RepCounterUiState.Refused -> {
                Text(state.reason, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Back")
                }
            }

            is RepCounterUiState.PositionGuide -> {
                Text(
                    positionGuideText(state.cameraPosition),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!state.hasCachedCalibration) {
                    Text(
                        "First time with this exercise: after we confirm we can see you, " +
                            "you'll do one slow demo rep so we can learn your range of motion.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Button(onClick = viewModel::onPositioned, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("I'm in position")
                }
                OutlinedButton(
                    onClick = viewModel::toggleCameraLens,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(if (isFrontCamera) "Switch to Back Camera" else "Switch to Front Camera")
                }
                CancelButton(onDone)
            }

            is RepCounterUiState.VisibilityCheck -> {
                Text("Checking we can see you…", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                CancelButton(onDone)
            }

            is RepCounterUiState.DemoRep -> {
                Text(
                    when (state.phase) {
                        DemoRepPhase.HOLD_STILL -> "Hold your starting position still…"
                        DemoRepPhase.PERFORMING -> "Now do one slow rep, through your full range of motion."
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                state.message?.let {
                    Text(it, color = Color(0xFFFFB4A9), modifier = Modifier.padding(top = 8.dp))
                }
                CancelButton(onDone)
            }

            is RepCounterUiState.Ready -> {
                Text("Ready. Start when you are.", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = viewModel::startSet, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Start set")
                }
                OutlinedButton(
                    onClick = viewModel::recalibrate,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("Recalibrate range of motion")
                }
                OutlinedButton(
                    onClick = viewModel::toggleCameraLens,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(if (isFrontCamera) "Switch to Back Camera" else "Switch to Front Camera")
                }
                CancelButton(onDone)
            }

            is RepCounterUiState.Counting -> {
                Text("${state.repCount}", color = Color.White, style = MaterialTheme.typography.displayLarge)
                state.lastFeedback?.let { feedback ->
                    Text(feedbackText(feedback), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = viewModel::finishSet, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Finish set")
                }
                CancelButton(onDone)
            }

            is RepCounterUiState.SetComplete -> {
                Text("${state.repCount} reps", color = Color.White, style = MaterialTheme.typography.displaySmall)
                Text(
                    "Range of motion: ${state.romAchieved}" + (state.avgTempoMs?.let { " · avg tempo ${it}ms" } ?: ""),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = viewModel::saveSet,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text("Save set")
                }
                OutlinedButton(
                    onClick = viewModel::discardSet,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("Discard")
                }
            }
        }
    }
}

/**
 * Explicit exit for every mid-flow state that previously had none —
 * PositionGuide, VisibilityCheck, DemoRep, Ready, Counting. Exits the same
 * way system back already does (discarding whatever this attempt has built
 * up so far); this only makes that exit visible instead of implicit.
 */
@Composable
private fun CancelButton(onDone: () -> Unit) {
    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Cancel")
    }
}

private fun positionGuideText(cameraPosition: CameraPosition): String = when (cameraPosition) {
    CameraPosition.SIDE -> "Prop your phone up so it sees your full body from the side."
    CameraPosition.FRONT -> "Prop your phone up so it sees your full body from the front."
    CameraPosition.FORTY_FIVE_DEGREE -> "Prop your phone up at a 45° angle so it sees your full body."
}

private fun feedbackText(feedback: RepFeedback): String {
    if (feedback.rejectionReason != null) {
        return when (feedback.rejectionReason) {
            RejectionReason.INSUFFICIENT_DEPTH -> "Rep not counted — didn't reach full depth."
            RejectionReason.INCOMPLETE_LOCKOUT -> "Rep not counted — didn't fully return to lockout."
            RejectionReason.ASYMMETRIC_FORM -> "Rep not counted — one side moved differently than the other."
            RejectionReason.TOO_FAST -> "Rep not counted — too fast to measure."
            RejectionReason.TOO_SLOW -> "Rep timed out — reset."
            RejectionReason.LOST_TRACKING -> "Rep not counted — can't see you clearly. Reposition and try again."
        }
    }
    val depth = if (feedback.depthReached) "depth ✓" else "depth ✗"
    val lockout = if (feedback.lockoutReached) "lockout ✓" else "lockout ✗"
    val symmetry = when {
        feedback.cameraPosition == CameraPosition.SIDE -> "symmetry: can't assess from this angle"
        feedback.symmetryDeviationDeg == null -> "symmetry: can't assess from this angle"
        feedback.symmetryDeviationDeg <= feedback.symmetryToleranceDeg -> "symmetry ✓"
        else -> "symmetry off by ${feedback.symmetryDeviationDeg.toInt()}°"
    }
    return "$depth · $lockout · $symmetry"
}
