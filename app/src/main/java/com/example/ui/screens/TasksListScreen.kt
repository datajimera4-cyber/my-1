package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.SessionState
import com.example.data.VideoTaskItem
import com.example.service.YouTubeLiveSearchService
import com.example.ui.components.ActiveWatchTimerBanner
import com.example.ui.components.AddVideoTaskDialog
import com.example.ui.components.AdminPostsBannerSection
import com.example.ui.components.DurationSelectionDialog
import com.example.ui.components.SearchLoadingOverlay
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberDark
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessGreen
import com.example.util.PermissionHelper
import com.example.util.TimeFormatter
import com.example.viewmodel.MainViewModel

@Composable
fun TasksListScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tasks by viewModel.videoTasks.collectAsStateWithLifecycle()
    val selectedTaskId by viewModel.selectedTaskId.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val watchedMillis by viewModel.watchedMillis.collectAsStateWithLifecycle()
    val requiredMillis by viewModel.requiredMillis.collectAsStateWithLifecycle()
    val searchProgress by viewModel.searchProgress.collectAsStateWithLifecycle()
    val likedTasks by viewModel.likedTasks.collectAsStateWithLifecycle()
    val commentCounts by viewModel.commentCounts.collectAsStateWithLifecycle()
    val adminPosts by viewModel.adminPosts.collectAsStateWithLifecycle()
    val dismissedPostIds by viewModel.dismissedPostIds.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var taskForTierDialog by remember { mutableStateOf<VideoTaskItem?>(null) }
    var showPermissionGateDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isAccessibilityReady by remember {
        mutableStateOf(
            YouTubeLiveSearchService.isServiceConnected ||
                    PermissionHelper.isAccessibilityServiceEnabled(context)
        )
    }
    var isOverlayReady by remember {
        mutableStateOf(PermissionHelper.canDrawOverlays(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityReady = YouTubeLiveSearchService.isServiceConnected ||
                        PermissionHelper.isAccessibilityServiceEnabled(context)
                isOverlayReady = PermissionHelper.canDrawOverlays(context)
                if (isAccessibilityReady && isOverlayReady) {
                    showPermissionGateDialog = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activeSearchTitle by viewModel.targetTaskTitle.collectAsStateWithLifecycle()
    val activeSearchTask = remember(tasks, selectedTaskId, activeSearchTitle) {
        tasks.find { it.id == selectedTaskId } ?: tasks.find { it.title == activeSearchTitle } ?: tasks.firstOrNull()
    }
    SearchLoadingOverlay(
        searchState = searchProgress,
        targetTitle = activeSearchTitle ?: activeSearchTask?.title ?: "",
        targetChannel = activeSearchTask?.channelName ?: "",
        thumbnailUrl = activeSearchTask?.thumbnailUrl
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (BuildConfig.APP_ROLE == "ADMIN") {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = AmberPrimary,
                    contentColor = Color.Black,
                    modifier = Modifier.testTag("open_add_task_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Video Task", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Clean Header (Real-time mode toggle removed as requested)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Video Tasks",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Watch target videos & earn instant coins",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AmberPrimary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${tasks.count { !it.isLocked }} Active",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = AmberDark,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Admin Banners / Posts targeted at TASKS tab
            AdminPostsBannerSection(
                posts = adminPosts,
                dismissedIds = dismissedPostIds,
                targetTab = "TASKS",
                onDismiss = { viewModel.dismissAdminPost(it) }
            )

            // Active Watch Session Banner
            if (sessionState == SessionState.ACTIVE || sessionState == SessionState.WAITING) {
                ActiveWatchTimerBanner(viewModel = viewModel)
            }

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No video tasks available right now",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("tasks_lazy_column"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        VideoTaskCardItem(
                            task = task,
                            isSelected = task.id == selectedTaskId,
                            isLiked = likedTasks.contains(task.id),
                            commentCount = commentCounts[task.id] ?: 0,
                            onWatchClick = {
                                val accOk = YouTubeLiveSearchService.isServiceConnected ||
                                        PermissionHelper.isAccessibilityServiceEnabled(context)
                                val overOk = PermissionHelper.canDrawOverlays(context)
                                isAccessibilityReady = accOk
                                isOverlayReady = overOk
                                if (!accOk || !overOk) {
                                    showPermissionGateDialog = true
                                } else {
                                    taskForTierDialog = task
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddVideoTaskDialog(
            onDismiss = { showAddDialog = false },
            onAddTask = { newTask ->
                showAddDialog = false
                viewModel.addVideoTask(newTask)
            }
        )
    }

    taskForTierDialog?.let { chosenTask ->
        DurationSelectionDialog(
            videoTitle = chosenTask.title,
            videoChannel = chosenTask.channelName,
            thumbnailUrl = chosenTask.thumbnailUrl,
            durationSeconds = chosenTask.durationSeconds,
            isLive = chosenTask.isLive,
            initialTierSeconds = chosenTask.selectedDurationSeconds,
            onDismiss = { taskForTierDialog = null },
            onConfirmSelection = { tier ->
                taskForTierDialog = null
                viewModel.startTaskWithTier(chosenTask, tier, context)
            }
        )
    }

    if (showPermissionGateDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionGateDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AlertRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Permissions Required",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Please enable both permissions below to start watching and earning coins:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAccessibilityReady) SuccessGreen.copy(alpha = 0.12f) else AlertRed.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("1. Accessibility Service", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = if (isAccessibilityReady) "Enabled ✓" else "Required to verify YouTube video",
                                    fontSize = 11.sp,
                                    color = if (isAccessibilityReady) SuccessGreen else AlertRed
                                )
                            }
                            if (!isAccessibilityReady) {
                                Button(
                                    onClick = { PermissionHelper.openAccessibilitySettings(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Allow", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isOverlayReady) SuccessGreen.copy(alpha = 0.12f) else AlertRed.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("2. Display Over Other Apps", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = if (isOverlayReady) "Enabled ✓" else "Required for live floating timer",
                                    fontSize = 11.sp,
                                    color = if (isOverlayReady) SuccessGreen else AlertRed
                                )
                            }
                            if (!isOverlayReady) {
                                Button(
                                    onClick = { PermissionHelper.openOverlaySettings(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Allow", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermissionGateDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun VideoTaskCardItem(
    task: VideoTaskItem,
    isSelected: Boolean,
    isLiked: Boolean,
    commentCount: Int,
    onWatchClick: () -> Unit
) {
    val taskLocked = task.isLocked
    val lockCountdown = if (taskLocked) task.getLockRemainingFormatted() else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) AmberPrimary else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("task_item_card_${task.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Thumbnail Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .background(Slate900)
            ) {
                if (task.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = task.thumbnailUrl,
                        contentDescription = task.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )

                // Top-left Live or Duration Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(
                            if (task.isLive) AlertRed else Color.Black.copy(alpha = 0.78f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (task.isLive) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (task.isLive) "LIVE" else TimeFormatter.formatSecondsToMmSs(task.durationSeconds),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Top-right Coin Reward Range
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(AmberPrimary, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "+10 to +110 Coins",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Content & Action
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = task.channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Like & Comment Bonus Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isLiked) SuccessGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ThumbUp,
                                contentDescription = null,
                                tint = if (isLiked) SuccessGreen else AmberDark,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isLiked) "Liked (+5c ✓)" else "Like +5 Coins",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isLiked) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (commentCount >= 2) SuccessGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Comment,
                                contentDescription = null,
                                tint = if (commentCount > 0) SuccessGreen else AmberDark,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Comment ($commentCount/2) +5 Coins",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (commentCount > 0) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Watch Task Button
                Button(
                    onClick = onWatchClick,
                    enabled = !taskLocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("watch_task_button_${task.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPrimary,
                        contentColor = Color.Black,
                        disabledContainerColor = if (task.isCompleted) SuccessGreen.copy(alpha = 0.15f) else AlertRed.copy(alpha = 0.14f),
                        disabledContentColor = if (task.isCompleted) SuccessGreen else AlertRed
                    )
                ) {
                    if (taskLocked) {
                        Icon(
                            imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (task.isCompleted) {
                                "Completed (4h Lock) • $lockCountdown left"
                            } else {
                                "Locked (6h) • $lockCountdown left"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Watch Task",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
