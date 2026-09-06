package com.fitforge.app.feature.health

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.domain.model.HealthConnectAvailability
import com.fitforge.app.domain.model.HealthDataSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HealthConnectScreen(
    modifier: Modifier = Modifier,
    viewModel: HealthConnectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Health Connect Permission Request Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { grantedPermissions ->
        viewModel.onPermissionsResult(grantedPermissions)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Health Connect Integration",
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = "Connect FitForge with Android Health Connect to automatically sync daily steps, workouts, heart rate, and wearable data.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Availability & Connection Status Card
        StatusCard(
            availability = uiState.availability,
            hasAllPermissions = uiState.hasAllPermissions,
            grantedCount = uiState.grantedPermissionsCount,
            totalCount = uiState.totalPermissionsCount,
            lastSyncedAt = uiState.lastSyncedAt,
            onConnectClick = {
                permissionLauncher.launch(viewModel.getRequiredPermissions())
            },
            onInstallClick = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                }
                context.startActivity(intent)
            },
        )

        // Sync Actions Row
        if (uiState.availability == HealthConnectAvailability.Available) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Synced Health Data",
                    style = MaterialTheme.typography.titleLarge,
                )

                OutlinedButton(
                    onClick = { viewModel.loadHealthData() },
                    enabled = !uiState.isLoading && uiState.grantedPermissionsCount > 0,
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync Now")
                }
            }
        }

        // Error Banner
        uiState.errorMessage?.let { error ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        // Data Display
        if (uiState.grantedPermissionsCount == 0 && uiState.availability == HealthConnectAvailability.Available) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "No Permissions Granted Yet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap 'Connect Health Data' above to grant permissions so FitForge can display your wearable and phone metrics.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else {
            HealthDataCards(data = uiState.healthData)
        }
    }
}

@Composable
private fun StatusCard(
    availability: HealthConnectAvailability,
    hasAllPermissions: Boolean,
    grantedCount: Int,
    totalCount: Int,
    lastSyncedAt: Instant?,
    onConnectClick: () -> Unit,
    onInstallClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                availability != HealthConnectAvailability.Available -> MaterialTheme.colorScheme.surfaceVariant
                hasAllPermissions -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (availability) {
                        HealthConnectAvailability.Available -> if (hasAllPermissions) Icons.Default.CheckCircle else Icons.Default.HealthAndSafety
                        HealthConnectAvailability.NotInstalled -> Icons.Default.Warning
                        HealthConnectAvailability.NotSupported -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = when (availability) {
                            HealthConnectAvailability.Available -> if (hasAllPermissions) "Connected" else "Permissions Partial ($grantedCount/$totalCount)"
                            HealthConnectAvailability.NotInstalled -> "Health Connect Not Installed"
                            HealthConnectAvailability.NotSupported -> "Health Connect Not Supported"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = when (availability) {
                            HealthConnectAvailability.Available -> if (hasAllPermissions) {
                                val syncText = lastSyncedAt?.let { "\nLast sync: ${it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}" } ?: ""
                                "FitForge has access to all requested health data.$syncText"
                            } else "Grant permissions to import metrics from your wearable or phone."
                            HealthConnectAvailability.NotInstalled -> "Please download Health Connect from the Google Play Store."
                            HealthConnectAvailability.NotSupported -> "This Android device does not support Health Connect."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (availability) {
                HealthConnectAvailability.Available -> {
                    Button(
                        onClick = onConnectClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (hasAllPermissions) "Manage Permissions" else "Connect Health Data")
                    }
                }
                HealthConnectAvailability.NotInstalled -> {
                    Button(
                        onClick = onInstallClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Install Health Connect from Play Store")
                    }
                }
                HealthConnectAvailability.NotSupported -> {
                    // No action possible on unsupported devices
                }
            }
        }
    }
}

@Composable
private fun HealthDataCards(data: HealthDataSummary?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Activity Group
        MetricCard(
            title = "Activity",
            icon = Icons.Default.DirectionsRun,
            items = listOf(
                "Daily Steps" to (data?.steps?.let { "$it steps" } ?: "No data"),
                "Distance" to (data?.distanceMeters?.let { String.format("%.2f km", it / 1000.0) } ?: "No data"),
                "Active Calories" to (data?.activeCaloriesKcal?.let { String.format("%.0f kcal", it) } ?: "No data"),
                "Total Energy" to (data?.totalCaloriesKcal?.let { String.format("%.0f kcal", it) } ?: "No data"),
            ),
        )

        // Heart Group
        MetricCard(
            title = "Heart Rate",
            icon = Icons.Default.Favorite,
            items = listOf(
                "Resting Heart Rate" to (data?.restingHeartRateBpm?.let { "$it bpm" } ?: "No data"),
                "Latest Heart Rate" to (data?.latestHeartRateBpm?.let { "$it bpm" } ?: "No data"),
            ),
        )

        // Sleep Group
        MetricCard(
            title = "Sleep",
            icon = Icons.Default.Nightlight,
            items = listOf(
                "Sleep Duration" to (data?.sleepDurationMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "No data"),
                "Session Details" to (data?.sleepStagesSummary ?: "No data"),
            ),
        )

        // Exercise Sessions
        MetricCard(
            title = "Workouts & Exercises",
            icon = Icons.Default.FitnessCenter,
            items = listOf(
                "Latest Workout" to (data?.latestExerciseSessionTitle ?: "No data"),
                "Duration" to (data?.latestExerciseDurationMinutes?.let { "$it minutes" } ?: "No data"),
            ),
        )

        // Body Composition & Vitals
        MetricCard(
            title = "Body & Vitals",
            icon = Icons.Default.MonitorWeight,
            items = listOf(
                "Weight" to (data?.weightKg?.let { String.format("%.1f kg", it) } ?: "No data"),
                "Height" to (data?.heightMeters?.let { String.format("%.0f cm", it * 100) } ?: "No data"),
                "Body Fat" to (data?.bodyFatPercentage?.let { String.format("%.1f %%", it) } ?: "No data"),
                "Blood Oxygen (SpO2)" to (data?.oxygenSaturationPercentage?.let { String.format("%.0f %%", it) } ?: "No data"),
                "Respiratory Rate" to (data?.respiratoryRateBpm?.let { String.format("%.0f rpm", it) } ?: "No data"),
            ),
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    icon: ImageVector,
    items: List<Pair<String, String>>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
