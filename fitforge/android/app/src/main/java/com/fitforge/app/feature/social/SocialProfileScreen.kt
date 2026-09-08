package com.fitforge.app.feature.social

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SocialProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: SocialProfileViewModel = hiltViewModel(),
) {
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val usernameCheckState by viewModel.usernameCheckState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val selectedPublicProfile by viewModel.selectedPublicProfile.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val actionErrorMessage by viewModel.actionErrorMessage.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when (val state = profileState) {
            is MyProfileUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is MyProfileUiState.Error -> {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Unable to load social profile",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        OutlinedButton(
                            onClick = { viewModel.loadMyProfile() },
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Retry")
                        }
                    }
                }
            }
            is MyProfileUiState.NoProfile -> {
                SocialProfileCard(
                    profile = null,
                    onOpenEditDialog = {
                        viewModel.clearActionError()
                        showEditDialog = true
                    },
                )
            }
            is MyProfileUiState.Success -> {
                SocialProfileCard(
                    profile = state.profile,
                    onOpenEditDialog = {
                        viewModel.clearActionError()
                        showEditDialog = true
                    },
                )
            }
        }

        SocialUserSearchSection(
            searchState = searchState,
            onSearch = { query -> viewModel.searchUsers(query) },
            onClear = { viewModel.clearSearch() },
            onUserClick = { user -> viewModel.selectPublicProfile(user) },
        )
    }

    if (showEditDialog) {
        val currentProfile = (profileState as? MyProfileUiState.Success)?.profile
        EditSocialProfileDialog(
            initialProfile = currentProfile,
            usernameCheckState = usernameCheckState,
            isSubmitting = isSubmitting,
            errorMessage = actionErrorMessage,
            onCheckUsername = { candidate ->
                viewModel.checkUsernameDebounced(candidate, currentProfile?.username)
            },
            onSubmit = { username, displayName, bio, isPrivate ->
                if (currentProfile == null) {
                    viewModel.createProfile(username, displayName, bio, isPrivate) {
                        showEditDialog = false
                    }
                } else {
                    viewModel.updateProfile(username, displayName, bio, isPrivate) {
                        showEditDialog = false
                    }
                }
            },
            onDismiss = { showEditDialog = false },
        )
    }

    selectedPublicProfile?.let { publicProfile ->
        PublicProfileDialog(
            profile = publicProfile,
            onDismiss = { viewModel.selectPublicProfile(null) },
        )
    }
}
