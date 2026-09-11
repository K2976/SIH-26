package com.fitforge.app.feature.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitforge.app.data.remote.generated.models.ChallengeTypeEnum

@Composable
fun CreateChallengeDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (title: String, description: String, type: ChallengeTypeEnum, target: Double, durationDays: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ChallengeTypeEnum.workout_count) }
    var targetText by remember { mutableStateOf("15") }
    var durationDaysText by remember { mutableStateOf("30") }
    var isTypeMenuExpanded by remember { mutableStateOf(false) }

    val targetValue = targetText.toDoubleOrNull() ?: 0.0
    val durationDays = durationDaysText.toIntOrNull() ?: 0

    val isTitleValid = title.trim().length in 2..128
    val isTargetValid = targetValue > 0.0
    val isDurationValid = durationDays in 1..365
    val canSubmit = isTitleValid && isTargetValid && isDurationValid && !isSubmitting

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Create Challenge",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    placeholder = { Text("e.g. 30-Day Workout Blitz") },
                    singleLine = true,
                    isError = title.isNotEmpty() && !isTitleValid,
                    supportingText = {
                        if (title.isNotEmpty() && !isTitleValid) {
                            Text("Title must be 2-128 characters")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Challenge Type Dropdown
                Column {
                    Text(
                        text = "Challenge Type *",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    OutlinedTextField(
                        value = when (selectedType) {
                            ChallengeTypeEnum.workout_count -> "Workout Count"
                            ChallengeTypeEnum.workout_minutes -> "Workout Minutes"
                            ChallengeTypeEnum.steps -> "Steps"
                            ChallengeTypeEnum.calories_burned -> "Calories Burned"
                            ChallengeTypeEnum.distance_km -> "Distance (km)"
                        },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Type",
                                modifier = Modifier.clickable { isTypeMenuExpanded = true },
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTypeMenuExpanded = true },
                    )

                    DropdownMenu(
                        expanded = isTypeMenuExpanded,
                        onDismissRequest = { isTypeMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Workout Count") },
                            onClick = {
                                selectedType = ChallengeTypeEnum.workout_count
                                if (targetText.isEmpty() || targetText == "500" || targetText == "10000") targetText = "15"
                                isTypeMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Workout Minutes") },
                            onClick = {
                                selectedType = ChallengeTypeEnum.workout_minutes
                                if (targetText.isEmpty() || targetText == "15" || targetText == "10000") targetText = "600"
                                isTypeMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Steps") },
                            onClick = {
                                selectedType = ChallengeTypeEnum.steps
                                if (targetText.isEmpty() || targetText == "15" || targetText == "600") targetText = "100000"
                                isTypeMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Calories Burned") },
                            onClick = {
                                selectedType = ChallengeTypeEnum.calories_burned
                                if (targetText.isEmpty() || targetText == "15") targetText = "5000"
                                isTypeMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Distance (km)") },
                            onClick = {
                                selectedType = ChallengeTypeEnum.distance_km
                                if (targetText.isEmpty() || targetText == "15") targetText = "50"
                                isTypeMenuExpanded = false
                            },
                        )
                    }
                }

                // Target Value
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Target Goal *") },
                    supportingText = {
                        Text("Units: %s".format(getChallengeUnit(selectedType)))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = targetText.isNotEmpty() && !isTargetValid,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Duration in Days
                OutlinedTextField(
                    value = durationDaysText,
                    onValueChange = { durationDaysText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Duration (Days) *") },
                    placeholder = { Text("e.g. 7, 14, 30") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = durationDaysText.isNotEmpty() && !isDurationValid,
                    supportingText = {
                        if (durationDaysText.isNotEmpty() && !isDurationValid) {
                            Text("Must be 1-365 days")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Description (Optional)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Rules, motivation, tips...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canSubmit) {
                        onSubmit(
                            title.trim(),
                            description.trim(),
                            selectedType,
                            targetValue,
                            durationDays,
                        )
                    }
                },
                enabled = canSubmit,
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier,
    )
}
