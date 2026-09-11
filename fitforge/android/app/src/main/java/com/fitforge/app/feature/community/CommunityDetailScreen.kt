package com.fitforge.app.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitforge.app.data.remote.generated.models.CommunityDetail
import com.fitforge.app.data.remote.generated.models.VisibilityEnum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDetailScreen(
    communityId: Int,
    onBackClick: () -> Unit,
    onChallengeClick: (challengeId: Int) -> Unit = {},
    viewModel: CommunityDetailViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(communityId) {
        viewModel.loadCommunity(communityId)
    }

    val detailState by viewModel.detailState.collectAsState()
    val membersState by viewModel.membersState.collectAsState()
    val challengesState by viewModel.challengesState.collectAsState()
    val selectedFilter by viewModel.selectedChallengeFilter.collectAsState()
    val actionInProgress by viewModel.actionInProgress.collectAsState()
    val actionError by viewModel.actionError.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var showCreateChallengeDialog by remember { mutableStateOf(false) }

    val currentDetail = (detailState as? CommunityDetailUiState.Success)?.community
    val isOwnerOrAdmin = currentDetail?.currentUserRole?.lowercase() in listOf("owner", "admin")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentDetail?.name ?: "Community",
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (isOwnerOrAdmin && currentDetail != null) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Community",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 1 && isOwnerOrAdmin) {
                FloatingActionButton(
                    onClick = { showCreateChallengeDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Challenge")
                        Text("New Challenge", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val state = detailState) {
                is CommunityDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is CommunityDetailUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Button(onClick = { viewModel.loadCommunity(communityId) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is CommunityDetailUiState.Success -> {
                    val community = state.community
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Navigation Tabs
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = { Text("Overview") },
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                text = { Text("Challenges") },
                            )
                            Tab(
                                selected = selectedTabIndex == 2,
                                onClick = { selectedTabIndex = 2 },
                                text = { Text("Members (${community.membersCount})") },
                            )
                        }

                        // Tab Contents
                        when (selectedTabIndex) {
                            0 -> {
                                CommunityOverviewContent(
                                    community = community,
                                    actionInProgress = actionInProgress,
                                    actionError = actionError,
                                    onJoinClick = { viewModel.joinCommunity() },
                                    onLeaveClick = { showLeaveConfirmDialog = true },
                                    onNavigateToChallenges = { selectedTabIndex = 1 },
                                )
                            }
                            1 -> {
                                CommunityChallengesTab(
                                    challengesState = challengesState,
                                    selectedFilter = selectedFilter,
                                    onFilterSelected = { viewModel.setChallengeFilter(it) },
                                    onChallengeClick = onChallengeClick,
                                    onJoinChallenge = { viewModel.joinChallenge(it) },
                                    isActionInProgress = actionInProgress,
                                    onCreateChallengeClick = { showCreateChallengeDialog = true },
                                    isOwnerOrAdmin = isOwnerOrAdmin,
                                    onRetry = { viewModel.setChallengeFilter(selectedFilter) },
                                )
                            }
                            2 -> {
                                CommunityMembersTab(
                                    membersState = membersState,
                                    onRetry = { viewModel.refresh() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Leave Confirmation Dialog
    if (showLeaveConfirmDialog && currentDetail != null) {
        val isOwner = currentDetail.currentUserRole?.lowercase() == "owner"
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            title = { Text("Leave Community") },
            text = {
                Text(
                    if (isOwner && currentDetail.membersCount > 1) {
                        "As the owner, you cannot leave while other members remain. Please transfer ownership or delete the community."
                    } else if (isOwner) {
                        "Leaving as the sole member will permanently delete this community. Are you sure?"
                    } else {
                        "Are you sure you want to leave \"${currentDetail.name}\"?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirmDialog = false
                        viewModel.leaveCommunity(
                            onSuccess = {
                                if (isOwner && currentDetail.membersCount <= 1) {
                                    onBackClick()
                                }
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(if (isOwner && currentDetail.membersCount <= 1) "Leave & Delete" else "Leave")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLeaveConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Edit Community Dialog
    if (showEditDialog && currentDetail != null) {
        EditCommunityDialog(
            community = currentDetail,
            isSubmitting = actionInProgress,
            onDismiss = { showEditDialog = false },
            onSave = { name, desc, vis ->
                viewModel.updateCommunity(name, desc, vis) {
                    showEditDialog = false
                }
            },
            onDelete = {
                viewModel.deleteCommunity {
                    showEditDialog = false
                    onBackClick()
                }
            },
        )
    }

    // Create Challenge Dialog
    if (showCreateChallengeDialog) {
        CreateChallengeDialog(
            isSubmitting = actionInProgress,
            onDismiss = { showCreateChallengeDialog = false },
            onSubmit = { title, desc, type, target, days ->
                viewModel.createChallenge(title, desc, type, target, days) {
                    showCreateChallengeDialog = false
                }
            },
        )
    }
}

@Composable
private fun CommunityOverviewContent(
    community: CommunityDetail,
    actionInProgress: Boolean,
    actionError: String?,
    onJoinClick: () -> Unit,
    onLeaveClick: () -> Unit,
    onNavigateToChallenges: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = community.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (community.visibility == VisibilityEnum.`public`) {
                                                Icons.Default.Public
                                            } else {
                                                Icons.Default.Lock
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Text(
                                            text = if (community.visibility == VisibilityEnum.`public`) "Public" else "Private",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }

                                Text(
                                    text = "${community.membersCount} members",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        community.currentUserRole?.let { role ->
                            RoleBadge(role = role)
                        }
                    }

                    if (community.description.isNotBlank()) {
                        Text(
                            text = community.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        text = "Founded by @${community.owner.username}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    // Action Button (Join / Leave)
                    if (!community.isMember) {
                        if (community.visibility == VisibilityEnum.`public`) {
                            Button(
                                onClick = onJoinClick,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !actionInProgress,
                            ) {
                                if (actionInProgress) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Text("Join Community")
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "Private community. Membership by invitation only.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onLeaveClick,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !actionInProgress,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            if (actionInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                Text("Leave Community")
                            }
                        }
                    }

                    // Error Banner
                    if (actionError != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = actionError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Challenge Access Banner
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToChallenges() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Community Challenges",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Join workouts, track verified progress, and hit milestones together.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityChallengesTab(
    challengesState: CommunityChallengesUiState,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    onChallengeClick: (Int) -> Unit,
    onJoinChallenge: (Int) -> Unit,
    isActionInProgress: Boolean,
    onCreateChallengeClick: () -> Unit,
    isOwnerOrAdmin: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val filters = listOf("all" to "All", "active" to "Active", "upcoming" to "Upcoming", "completed" to "Completed")
            items(filters) { (key, label) ->
                FilterChip(
                    selected = selectedFilter == key,
                    onClick = { onFilterSelected(key) },
                    label = { Text(label) },
                )
            }
        }

        when (challengesState) {
            is CommunityChallengesUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is CommunityChallengesUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = challengesState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }
            is CommunityChallengesUiState.Success -> {
                val challenges = challengesState.challenges
                if (challenges.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                text = "No challenges in this category yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (isOwnerOrAdmin) {
                                Button(onClick = onCreateChallengeClick) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("Create Challenge")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(challenges, key = { it.id }) { challenge ->
                            ChallengeCard(
                                challenge = challenge,
                                onClick = { onChallengeClick(challenge.id) },
                                onJoinClick = { onJoinChallenge(challenge.id) },
                                isActionInProgress = isActionInProgress,
                            )
                        }
                        item { Spacer(modifier = Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityMembersTab(
    membersState: CommunityMembersUiState,
    onRetry: () -> Unit,
) {
    when (membersState) {
        is CommunityMembersUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        is CommunityMembersUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = membersState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }
        is CommunityMembersUiState.Success -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(membersState.members, key = { it.id }) { member ->
                    CommunityMemberItem(member = member)
                }
            }
        }
    }
}

@Composable
fun RoleBadge(role: String) {
    val (label, bg, fg) = when (role.lowercase()) {
        "owner" -> Triple("Owner", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        "admin" -> Triple("Admin", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        else -> Triple("Member", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = when (role.lowercase()) {
                    "owner" -> Icons.Default.Star
                    "admin" -> Icons.Default.Shield
                    else -> Icons.Default.People
                },
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = fg,
            )
        }
    }
}
