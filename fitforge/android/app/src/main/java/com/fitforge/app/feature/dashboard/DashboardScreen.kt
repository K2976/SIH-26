@file:Suppress("CyclomaticComplexMethod", "LongMethod", "LongParameterList", "MagicNumber")

package com.fitforge.app.feature.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.R
import com.fitforge.app.feature.cycle.QuickCycleLogCard
import com.fitforge.app.feature.fitnessage.FitnessAgeCard
import com.fitforge.app.feature.music.WorkoutMusicCard
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onOpenCycle: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_dashboard),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !uiState.isRefreshing,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Sync status indicator
                SyncTimestampHeader(
                    lastSyncedAt = uiState.lastSyncedAt,
                    isRefreshing = uiState.isRefreshing,
                )

                // One-tap daily cycle check-in — renders nothing when tracking
                // is off, discreet mode is on, or it's dismissed for the session.
                QuickCycleLogCard(onOpenCycle = onOpenCycle)

                // Deep link to a YouTube playlist matched to today's session
                // focus (and cycle phase, when available). Renders nothing
                // until a training focus is known.
                WorkoutMusicCard()

                // Error banner if refresh fails
                if (uiState.errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Sync error: ${uiState.errorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                // 1. TODAY SECTION
                TodayContent(today = uiState.today)

                // 2. ACTIVE PLAN DELTA
                PlanDeltaContent(planDelta = uiState.planDelta)

                // 3. THIS WEEK SECTION
                WeekContent(week = uiState.week)

                // 4. TRENDS SECTION
                TrendsContent(trends = uiState.trends)

                // Fitness age — server-computed non-exercise VO2max estimate.
                // Renders a "what's needed" message instead of a number when
                // required profile fields are missing.
                FitnessAgeCard()

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TodayContent(today: TodaySection) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.dashboard_section_today),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        when (today) {
            is TodaySection.Loading -> {
                Card(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading energy balance...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            is TodaySection.Empty -> {
                EmptyStateCard(
                    title = today.title,
                    description = today.message,
                )
            }

            is TodaySection.Data -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Badges for source and confidence
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (today.maintenanceSource != null) {
                                val isEquation = today.maintenanceSource == "equation"
                                StatusBadge(
                                    text = if (isEquation) {
                                        stringResource(R.string.dashboard_source_equation)
                                    } else {
                                        stringResource(R.string.dashboard_source_blended)
                                    },
                                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }

                            if (today.maintenanceConfidence != null) {
                                val isLow = today.maintenanceConfidence < 0.5
                                StatusBadge(
                                    text = if (isLow) {
                                        stringResource(R.string.dashboard_confidence_low)
                                    } else {
                                        stringResource(R.string.dashboard_confidence_high)
                                    },
                                    backgroundColor = if (isLow) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                    contentColor = if (isLow) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                )
                            }
                        }

                        // Calorie ring + 3 metric columns
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CalorieProgressRing(
                                targetKcal = today.targetKcal,
                                consumedKcal = today.consumedKcal,
                                burnedKcal = today.burnedKcal,
                                remainingKcal = today.remainingKcal,
                                isOverTarget = today.isOverTarget,
                                modifier = Modifier.weight(1f),
                            )

                            Column(
                                modifier = Modifier.weight(1.1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MetricRow(
                                    icon = Icons.Default.Restaurant,
                                    label = stringResource(R.string.dashboard_consumed),
                                    value = today.consumedKcal?.let { "${it.roundToInt()} kcal" } ?: "—",
                                    isPartial = today.isConsumedPartial,
                                    tint = MaterialTheme.colorScheme.primary,
                                )

                                MetricRow(
                                    icon = Icons.Default.LocalFireDepartment,
                                    label = stringResource(R.string.dashboard_burned),
                                    value = today.burnedKcal?.let { "${it.roundToInt()} kcal" } ?: "—",
                                    isPartial = today.isBurnedPartial,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )

                                MetricRow(
                                    icon = Icons.Default.ElectricBolt,
                                    label = stringResource(R.string.dashboard_target),
                                    value = today.targetKcal?.let { "${it.roundToInt()} kcal" } ?: "—",
                                    isPartial = false,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isPartial: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (isPartial) {
                StatusBadge(
                    text = stringResource(R.string.dashboard_partial_badge),
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlanDeltaContent(planDelta: PlanDeltaSection) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.dashboard_section_plan_delta),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        when (planDelta) {
            is PlanDeltaSection.Loading -> {}
            is PlanDeltaSection.Active -> PlanDeltaCard(activeDelta = planDelta)
            is PlanDeltaSection.Inactive -> {
                EmptyStateCard(
                    title = "Baseline targets active",
                    description = planDelta.message,
                )
            }
        }
    }
}

@Composable
private fun WeekContent(week: WeekSection) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.dashboard_section_week),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        when (week) {
            is WeekSection.Loading -> {}
            is WeekSection.Empty -> {
                EmptyStateCard(
                    title = "No weekly logs",
                    description = week.message,
                )
            }

            is WeekSection.Data -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // 1. Adherence Progress Bar
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.dashboard_week_adherence),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                )
                                Text(
                                    text = week.adherencePercent?.let { "$it%" } ?: "—",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (week.adherencePercent ?: 0) / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                            )
                        }

                        // 2. Training Volume Completed vs Planned
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.dashboard_week_volume),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                )
                                Text(
                                    text = when {
                                        week.volumeCompletedKg != null && week.volumePlannedKg != null ->
                                            stringResource(
                                                R.string.dashboard_week_volume_format,
                                                week.volumeCompletedKg,
                                                week.volumePlannedKg,
                                            )
                                        week.volumeCompletedKg != null ->
                                            stringResource(
                                                R.string.dashboard_week_volume_completed_only,
                                                week.volumeCompletedKg,
                                            )
                                        else -> "No volume recorded"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (week.isVolumeCompletedPartial) {
                                StatusBadge(
                                    text = stringResource(R.string.dashboard_partial_badge),
                                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // 3. Weekly Energy Balance
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.dashboard_week_balance),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                )
                                Text(
                                    text = when {
                                        week.energyBalanceKcal != null && week.energyBalanceKcal > 0 ->
                                            stringResource(
                                                R.string.dashboard_week_balance_surplus,
                                                week.energyBalanceKcal,
                                            )
                                        week.energyBalanceKcal != null && week.energyBalanceKcal < 0 ->
                                            stringResource(
                                                R.string.dashboard_week_balance_deficit,
                                                kotlin.math.abs(week.energyBalanceKcal),
                                            )
                                        week.energyBalanceKcal != null ->
                                            stringResource(R.string.dashboard_week_balance_neutral)
                                        else -> "—"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (week.energyBalanceKcal != null && week.energyBalanceKcal < 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.tertiary
                                    },
                                )
                            }

                            if (week.isEnergyBalancePartial) {
                                StatusBadge(
                                    text = stringResource(R.string.dashboard_partial_badge),
                                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendsContent(trends: TrendsSection) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.dashboard_section_trends),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        when (trends) {
            is TrendsSection.Loading -> {}
            is TrendsSection.Empty -> {
                EmptyStateCard(
                    title = "Trends unlock with consistency",
                    description = trends.message,
                )
            }

            is TrendsSection.Data -> {
                // Weight trend with confidence band sparkline
                WeightTrendSparkline(
                    latestWeightKg = trends.latestWeightKg,
                    trendKgPerWeek = trends.weightTrendKgPerWeek,
                    confidence = trends.trendConfidence,
                )

                // Volume by muscle group horizontal bar chart
                if (!trends.volumeByMuscleGroup.isNullOrEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.dashboard_trend_muscle_volume),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            MuscleVolumeBarChart(muscleVolumes = trends.volumeByMuscleGroup)
                        }
                    }
                }
            }
        }
    }
}
