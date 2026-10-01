package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.SessionState
import com.example.data.VideoTaskItem
import com.example.data.WATCH_DURATION_TIERS
import com.example.repository.WatchSessionRepository
import com.example.ui.components.ActiveWatchTimerBanner
import com.example.ui.components.AddVideoTaskDialog
import com.example.ui.components.AdminPostsBannerSection
import com.example.ui.components.DurationSelectionDialog
import com.example.ui.components.SearchLoadingOverlay
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberDark
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessGreen
import com.example.util.PermissionHelper
import com.example.util.TimeFormatter
import com.example.util.TitleMatcher
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksListScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val videoTasks by viewModel.videoTasks.collectAsState()
    val walletBalance by viewModel.walletBalance.collectAsState()
    val adminPosts by viewModel.adminPosts.collectAsState()
    val dismissedPostIds by viewModel.dismissedPostIds.collectAsState()
    val searchProgress by viewModel.searchProgress.collectAsState()
    val liveSearchMode by viewModel.liveSearchMode.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val currentWatchedMillis by viewModel.watchedMillis.collectAsState()
    val activeTaskId by com.example.repository.WatchSessionRepository.activeTaskId.collectAsState()
    val likedTasks by viewModel.likedTasks.collectAsState()
    val commentCounts by viewModel.commentCounts.collectAsState()
    val sessionInterruptedMessage by viewModel.sessionInterruptedMessage.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var taskForDurationSelection by remember { mutableStateOf<VideoTaskItem?>(null) }
    var pendingTaskAndTier by remember { mutableStateOf<Pair<VideoTaskItem, com.example.data.WatchDurationTier>?>(null) }
    var showOverlayPromptDialog by remember { mutableStateOf(false) }
    var showAccessibilityPromptDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredTasks = remember(videoTasks, selectedFilter) {
        when (selectedFilter) {
            "Completed" -> videoTasks.filter { it.isCompleted }
            "Pending" -> videoTasks.filter { !it.isCompleted }
            else -> videoTasks
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(AmberPrimary, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Kingo King Tasks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Sit Back - Watch & Earn",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    // Balance quick badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AmberPrimary.copy(alpha = 0.2f))
                            .clickable { viewModel.navigateTo(AppScreen.WALLET) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("topbar_wallet_badge")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$walletBalance",
                                fontWeight = FontWeight.Bold,
                                color = AmberPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Plus icon action in top bar
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("topbar_add_task_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add New Video Task",
                            tint = AmberPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AmberPrimary,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier.testTag("fab_add_task")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Video Task")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Real-time Watch Session Floating Bar
            item {
                ActiveWatchTimerBanner(viewModel = viewModel)
            }

            // Admin Banners / Posts / Alerts for TASKS tab
            item {
                AdminPostsBannerSection(
                    posts = adminPosts,
                    currentTab = "TASKS",
                    dismissedIds = dismissedPostIds,
                    onDismissPost = { viewModel.dismissAdminPost(it) }
                )
            }

            // Live Search Mode Switch Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (liveSearchMode) Slate800 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("tasks_live_search_toggle_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (liveSearchMode) SuccessGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (liveSearchMode) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = if (liveSearchMode) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Live Realtime Mode",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (liveSearchMode) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                if (liveSearchMode) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(SuccessGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "LIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (liveSearchMode) "Opens YouTube search live without in-app loading screen" else "Shows in-app animated typing & radar search",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = liveSearchMode,
                            onCheckedChange = { viewModel.toggleLiveSearchMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = SuccessGreen
                            ),
                            modifier = Modifier.testTag("tasks_live_switch")
                        )
                    }
                }
            }

            // Filter Chips Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("All", "Pending", "Completed").forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = {
                                Text(
                                    text = when (filter) {
                                        "All" -> "All Videos (${videoTasks.size})"
                                        "Pending" -> "Pending (${videoTasks.count { !it.isCompleted }})"
                                        "Completed" -> "Completed (${videoTasks.count { it.isCompleted }})"
                                        else -> filter
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberPrimary,
                                selectedLabelColor = Color.Black
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // Task Items
            items(filteredTasks, key = { it.id }) { task ->
                val isCurrentlyActive = activeTaskId == task.id && sessionState == SessionState.ACTIVE
                val isLiked = likedTasks.contains(task.id)
                val comments = commentCounts[task.id] ?: 0

                VideoTaskCard(
                    task = task,
                    isCurrentlyActive = isCurrentlyActive,
                    isLiked = isLiked,
                    commentsCount = comments,
                    activeWatchedMillis = if (isCurrentlyActive) currentWatchedMillis else task.watchedMillis,
                    onStartClick = {
                        if (task.isLocked) {
                            val remainStr = task.getLockRemainingFormatted()
                            if (task.isCompleted) {
                                WatchSessionRepository.showTaskIncompleteMessage(
                                    "🔒 Yeh task complete ho chuka hai aur 8 ghante ke liye lock hai ($remainStr remaining). 8 ghante baad yeh task rewatch hoga."
                                )
                            } else {
                                WatchSessionRepository.showTaskIncompleteMessage(
                                    "⚠️ Yeh task incomplete hone ki wajah se 12 ghante ke liye lock hai ($remainStr remaining). 12 ghante baad yeh task rewatch hoga."
                                )
                            }
                        } else {
                            taskForDurationSelection = task
                        }
                    },
                    onLikeClick = {
                        android.widget.Toast.makeText(
                            context,
                            "Start Watch karein aur YouTube par asli video Like karein (+5 coins 1st time auto-add honge)!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    onCommentClick = {
                        android.widget.Toast.makeText(
                            context,
                            "Start Watch karein aur YouTube par asli Comment post karein (+5 coins auto-add honge)!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    onCardClick = {
                        viewModel.selectTask(task)
                        viewModel.navigateTo(AppScreen.TASK)
                    }
                )
            }

            if (filteredTasks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No tasks found in this section",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add a YouTube Video", color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // Add Video Dialog
    if (showAddDialog) {
        AddVideoTaskDialog(
            onDismiss = { showAddDialog = false },
            onTaskAdded = { newTask ->
                viewModel.addVideoTask(newTask)
                showAddDialog = false
            }
        )
    }

    // Duration & Tier Selection Dialog
    taskForDurationSelection?.let { task ->
        DurationSelectionDialog(
            videoTitle = task.title,
            videoChannel = task.channelName,
            thumbnailUrl = task.thumbnailUrl,
            durationSeconds = task.durationSeconds,
            isLive = task.isLive,
            initialTierSeconds = task.selectedDurationSeconds,
            onDismiss = { taskForDurationSelection = null },
            onConfirmSelection = { tier ->
                val chosenTask = task
                taskForDurationSelection = null
                if (!PermissionHelper.isOverlayPermissionGranted(context)) {
                    pendingTaskAndTier = Pair(chosenTask, tier)
                    showOverlayPromptDialog = true
                } else if (!PermissionHelper.isAccessibilityServiceEnabled(context)) {
                    pendingTaskAndTier = Pair(chosenTask, tier)
                    showAccessibilityPromptDialog = true
                } else {
                    viewModel.startTaskWithTier(chosenTask, tier, context)
                }
            }
        )
    }

    if (showOverlayPromptDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOverlayPromptDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Enable Floating Timer Overlay", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("To see the live floating timer and like/comment buttons on the side of YouTube while watching, please enable 'Display over other apps' for Kingo King.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayPromptDialog = false
                        try {
                            context.startActivity(PermissionHelper.createOverlaySettingsIntent(context))
                        } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Text("Enable in Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showOverlayPromptDialog = false
                        if (!PermissionHelper.isAccessibilityServiceEnabled(context)) {
                            showAccessibilityPromptDialog = true
                        } else {
                            pendingTaskAndTier?.let { (task, tier) ->
                                viewModel.startTaskWithTier(task, tier, context)
                            }
                        }
                    }
                ) {
                    Text("Continue Without Overlay")
                }
            }
        )
    }

    if (showAccessibilityPromptDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAccessibilityPromptDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Enable YouTube Task Monitor", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("To verify that the target video is playing, detect likes & comments inside YouTube, and stop the timer if the video changes, please enable 'Kingo King' in Accessibility Settings.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityPromptDialog = false
                        try {
                            context.startActivity(PermissionHelper.createAccessibilitySettingsIntent())
                        } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Text("Enable in Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showAccessibilityPromptDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (sessionInterruptedMessage != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissInterruptedMessage() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Continuous Watch Interrupted", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(sessionInterruptedMessage ?: "")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissInterruptedMessage() },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Text("Understood", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // In-App Simulation Overlay
    if (!liveSearchMode) {
        SearchLoadingOverlay(
            searchState = searchProgress,
            targetTitle = "Selected Video Task",
            targetChannel = "Target YouTube Channel",
            thumbnailUrl = null
        )
    }
}

@Composable
fun VideoTaskCard(
    task: VideoTaskItem,
    isCurrentlyActive: Boolean,
    isLiked: Boolean,
    commentsCount: Int,
    activeWatchedMillis: Long,
    onStartClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyActive) AmberPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isCurrentlyActive) 1.5.dp else 1.dp,
                color = if (isCurrentlyActive) AmberPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onCardClick() }
            .testTag("task_card_${task.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Video Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 70.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(task.thumbnailUrl).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Duration pill overlay on thumbnail bottom right
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (task.isLive) "LIVE" else TimeFormatter.formatMillisToMmSs(task.durationSeconds * 1000L),
                            color = if (task.isLive) Color(0xFFFF5252) else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }

                    if (task.isLocked) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .background(Color(0xDDDC2626), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "LOCKED",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title, Channel & Rewards info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = task.channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Coins reward tag
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (task.isPinned) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFEF3C7), RoundedCornerShape(12.dp))
                                    .border(1.dp, AmberPrimary, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "📌 PINNED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = AmberDark,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .background(AmberPrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MonetizationOn,
                                    contentDescription = null,
                                    tint = AmberDark,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                val maxCoins = when {
                                    task.isLive || task.durationSeconds >= 1800 -> 110
                                    task.durationSeconds >= 1200 -> 72
                                    task.durationSeconds >= 600 -> 35
                                    task.durationSeconds >= 300 -> 17
                                    else -> 10
                                }
                                Text(
                                    text = if (task.isCompleted) "+${task.rewardCoins} Earned" else "Up to +$maxCoins Coins",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AmberDark,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (task.isCompleted) {
                            Box(
                                modifier = Modifier
                                    .background(SuccessGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Completed",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bonus actions strip: Like (+5c) & Comment (+5c, max 2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isLiked) SuccessGreen.copy(alpha = 0.15f) else AmberPrimary.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = !isLiked) { onLikeClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isLiked) "✓ Liked (+5c)" else "👍 Like (+5c)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isLiked) SuccessGreen else AmberDark,
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            if (commentsCount >= 2) SuccessGreen.copy(alpha = 0.15f) else Color(0xFF0284C7).copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = commentsCount < 2) { onCommentClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (commentsCount >= 2) "✓ Comments (+10c)" else "💬 Comment (+5c, $commentsCount/2)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (commentsCount >= 2) SuccessGreen else Color(0xFF0284C7),
                        fontSize = 11.sp
                    )
                }
            }

            // Bottom action strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tier range info
                Text(
                    text = when {
                        task.isLocked && task.isCompleted -> "Completed • Rewatch in ${task.getLockRemainingFormatted()} (8h Lock)"
                        task.isLocked -> "Incomplete • Rewatch in ${task.getLockRemainingFormatted()} (12h Lock)"
                        task.isLive -> "Live Stream • Goals: 3m - 30m"
                        task.durationSeconds >= 1800 -> "Goals: 3m, 5m, 10m, 20m, 30m"
                        task.durationSeconds >= 600 -> "Goals: 3m, 5m, 10m"
                        else -> "Goals: 3m"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.isLocked) AlertRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Start / Watch Button
                Button(
                    onClick = onStartClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (task.isLocked) MaterialTheme.colorScheme.surfaceVariant else if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant else AmberPrimary
                    ),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("task_action_button_${task.id}")
                ) {
                    Icon(
                        imageVector = if (task.isLocked) Icons.Default.Lock else if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (task.isLocked) AlertRed else if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (task.isLocked) "Locked (${task.getLockRemainingFormatted()})" else if (task.isCompleted) "Rewatch" else "Start Watch",
                        fontWeight = FontWeight.Bold,
                        color = if (task.isLocked) AlertRed else if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else Color.Black,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
