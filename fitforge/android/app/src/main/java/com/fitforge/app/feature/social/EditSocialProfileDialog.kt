package com.fitforge.app.feature.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitforge.app.data.remote.generated.models.SocialProfile

private const val MAX_HANDLE_CHARS = 32
private const val MAX_DISPLAY_NAME_CHARS = 64
private const val MAX_BIO_CHARS = 256
private val AvailableGreenColor = Color(0xFF2E7D32)

@Composable
fun EditSocialProfileDialog(
    initialProfile: SocialProfile?,
    usernameCheckState: UsernameCheckUiState,
    isSubmitting: Boolean,
    errorMessage: String?,
    onCheckUsername: (String) -> Unit,
    onSubmit: (username: String, displayName: String, bio: String, isPrivate: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val isCreating = initialProfile == null

    var username by remember { mutableStateOf(initialProfile?.username ?: "") }
    var displayName by remember { mutableStateOf(initialProfile?.displayName ?: "") }
    var bio by remember { mutableStateOf(initialProfile?.bio ?: "") }
    var isPrivate by remember { mutableStateOf(initialProfile?.isPrivate ?: false) }

    LaunchedEffect(username) {
        onCheckUsername(username)
    }

    val isUsernameValid = when (usernameCheckState) {
        is UsernameCheckUiState.Available -> true
        else -> false
    }

    val canSubmit = isUsernameValid && displayName.trim().isNotEmpty() && !isSubmitting

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = if (isCreating) "Create Social Profile" else "Edit Social Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                // Public Username
                OutlinedTextField(
                    value = username,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isLetterOrDigit() || it == '_' }
                        if (filtered.length <= MAX_HANDLE_CHARS) {
                            username = filtered
                        }
                    },
                    label = { Text("Public Username (Handle)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.AlternateEmail, contentDescription = null)
                    },
                    trailingIcon = {
                        when (usernameCheckState) {
                            is UsernameCheckUiState.Checking -> {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                            is UsernameCheckUiState.Available -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Available",
                                    tint = AvailableGreenColor,
                                )
                            }
                            is UsernameCheckUiState.Taken, is UsernameCheckUiState.Invalid -> {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Unavailable",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                            is UsernameCheckUiState.Idle -> Unit
                        }
                    },
                    supportingText = {
                        when (usernameCheckState) {
                            is UsernameCheckUiState.Checking -> Text("Checking availability...")
                            is UsernameCheckUiState.Available -> Text(
                                text = usernameCheckState.message,
                                color = AvailableGreenColor,
                            )
                            is UsernameCheckUiState.Taken -> Text(
                                text = usernameCheckState.message,
                                color = MaterialTheme.colorScheme.error,
                            )
                            is UsernameCheckUiState.Invalid -> Text(
                                text = usernameCheckState.message,
                                color = MaterialTheme.colorScheme.error,
                            )
                            is UsernameCheckUiState.Idle -> Text("3-32 letters, numbers, underscores")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Display Name
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= MAX_DISPLAY_NAME_CHARS) displayName = it },
                    label = { Text("Display Name") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null)
                    },
                    supportingText = { Text("${displayName.length}/$MAX_DISPLAY_NAME_CHARS") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Bio
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= MAX_BIO_CHARS) bio = it },
                    label = { Text("Bio (Optional)") },
                    supportingText = { Text("${bio.length}/$MAX_BIO_CHARS") },
                    minLines = 3,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Private Account Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Private Profile", fontWeight = FontWeight.Medium)
                            Text(
                                "Only approved followers can view your activities",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(username, displayName, bio, isPrivate)
                },
                enabled = canSubmit,
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isCreating) "Create Profile" else "Save Changes")
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
    )
}
