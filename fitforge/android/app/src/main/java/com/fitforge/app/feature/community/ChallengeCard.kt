package com.fitforge.app.feature.community

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitforge.app.data.remote.generated.models.ChallengeList
import com.fitforge.app.data.remote.generated.models.ChallengeTypeEnum
import com.fitforge.app.data.remote.generated.models.StatusEnum

@Composable
fun ChallengeCard(
    challenge: ChallengeList,
    onClick: () -> Unit,
    onJoinClick: () -> Unit,
    isActionInProgress: Boolean = false,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header Row: Type Icon + Title + Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = getChallengeTypeIcon(challenge.challengeType),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Column {
                        Text(
                            text = challenge.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = getChallengeTypeDisplay(challenge.challengeType, challenge.targetValue.toDouble()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ChallengeStatusBadge(status = challenge.status)
            }

            // User Progress or Join Prompt
            if (challenge.isParticipant) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Joined • Tap to view progress",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            } else if (challenge.status == StatusEnum.active || challenge.status == StatusEnum.upcoming) {
                Button(
                    onClick = onJoinClick,
                    enabled = !isActionInProgress,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Text(
                        text = "Join Challenge",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Footer: Participant Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "${challenge.participantsCount} participants",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ChallengeStatusBadge(status: StatusEnum) {
    val (label, bg, fg) = when (status) {
        StatusEnum.active -> Triple("Active", Color(0xFF1B5E20).copy(alpha = 0.15f), Color(0xFF2E7D32))
        StatusEnum.upcoming -> Triple("Upcoming", Color(0xFF0D47A1).copy(alpha = 0.15f), Color(0xFF1976D2))
        StatusEnum.completed -> Triple("Completed", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        StatusEnum.cancelled -> Triple("Cancelled", Color(0xFFB71C1C).copy(alpha = 0.15f), Color(0xFFC62828))
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

fun getChallengeTypeIcon(type: ChallengeTypeEnum): ImageVector {
    return when (type) {
        ChallengeTypeEnum.workout_count -> Icons.Default.FitnessCenter
        ChallengeTypeEnum.workout_minutes -> Icons.Default.Timer
        ChallengeTypeEnum.steps -> Icons.Default.DirectionsWalk
        ChallengeTypeEnum.calories_burned -> Icons.Default.LocalFireDepartment
        ChallengeTypeEnum.distance_km -> Icons.Default.DirectionsRun
    }
}

fun getChallengeTypeDisplay(type: ChallengeTypeEnum, target: Double): String {
    return when (type) {
        ChallengeTypeEnum.workout_count -> "Target: ${target.toInt()} workouts"
        ChallengeTypeEnum.workout_minutes -> "Target: ${target.toInt()} minutes"
        ChallengeTypeEnum.steps -> "Target: ${target.toInt()} steps"
        ChallengeTypeEnum.calories_burned -> "Target: ${target.toInt()} kcal"
        ChallengeTypeEnum.distance_km -> "Target: ${target.toInt()} km"
    }
}

fun getChallengeUnit(type: ChallengeTypeEnum): String {
    return when (type) {
        ChallengeTypeEnum.workout_count -> "workouts"
        ChallengeTypeEnum.workout_minutes -> "mins"
        ChallengeTypeEnum.steps -> "steps"
        ChallengeTypeEnum.calories_burned -> "kcal"
        ChallengeTypeEnum.distance_km -> "km"
    }
}
