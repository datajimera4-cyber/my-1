package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import com.example.ui.components.ActiveWatchTimerBanner
import com.example.ui.components.DurationSelectionDialog
import com.example.ui.components.SearchLoadingOverlay
import com.example.ui.theme.Slate800
import com.example.util.TitleMatcher
import com.example.util.PermissionHelper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MatchResult
import com.example.data.OEmbedResult
import com.example.data.SampleTask
import com.example.data.SessionState
import com.example.data.VideoPlaybackState
import com.example.repository.WatchSessionRepository
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AlertRedDark
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.SuccessGreen
import com.example.util.TimeFormatter
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { viewModel.navigateBack() }

    val oEmbedState by viewModel.oEmbedState.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val matchResult by viewModel.matchResult.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val watchedMillis by viewModel.watchedMillis.collectAsState()
    val requiredMillis by viewModel.requiredMillis.collectAsState()
    val isGraceActive by viewModel.isGracePeriodActive.collectAsState()
    val graceSeconds by viewModel.graceSecondsRemaining.collectAsState()
    val redAlertMessage by viewModel.redAlertMessage.collectAsState()
    val currentPlayingTitle by viewModel.currentMediaTitle.collectAsState()
    val videoTasks by viewModel.videoTasks.collectAsState()
    val currentUrl by viewModel.currentVideoUrl.collectAsState()
    val searchProgress by viewModel.searchProgress.collectAsState()
    val liveSearchMode by viewModel.liveSearchMode.collectAsState()
    val selectedTierSeconds by viewModel.selectedTierSeconds.collectAsState()
    val selectedTierCoins by viewModel.selectedTierCoins.collectAsState()
    val likedTasks by viewModel.likedTasks.collectAsState()
    val commentCounts by viewModel.commentCounts.collectAsState()
    val sessionInterruptedMessage by viewModel.sessionInterruptedMessage.collectAsState()
    val selectedTaskId by viewModel.selectedTaskId.collectAsState()

    val currentSelectedTask = remember(videoTasks, selectedTaskId) {
        videoTasks.find { it.id == selectedTaskId }
    }
    val isTaskLocked = currentSelectedTask?.isLocked == true
    val isTaskCompletedEffective = currentSelectedTask?.isCompleted == true || sessionState == SessionState.COMPLETED

    var showDurationDialog by remember { mutableStateOf(false) }
    var showAccessibilityPromptDialog by remember { mutableStateOf(false) }
    var showOverlayPromptDialog by remember { mutableStateOf(false) }

    // On open, ensure oEmbed is loaded
    LaunchedEffect(currentUrl) {
        if (oEmbedState is OEmbedResult.Idle) {
            viewModel.fetchOEmbed()
        }
    }

    val progressFraction = if (requiredMillis > 0) {
        (watchedMillis.toFloat() / requiredMillis.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(targetValue = progressFraction, label = "progress")

    // Determine Big Status Text
    val (statusText, statusColor, statusIcon) = when {
        isTaskLocked -> {
            val remainStr = currentSelectedTask?.getLockRemainingFormatted() ?: "12h"
            Triple("Task Locked ($remainStr)", AlertRed, Icons.Default.Lock)
        }
        isTaskCompletedEffective -> {
            Triple("Completed", SuccessGreen, Icons.Default.CheckCircle)
        }
        sessionState == SessionState.INVALID || matchResult == MatchResult.MISMATCH && !isGraceActive -> {
            Triple("Wrong video", AlertRed, Icons.Default.ErrorOutline)
        }
        isGraceActive -> {
            Triple("Wrong video (Grace: ${graceSeconds}s)", AlertRed, Icons.Default.Warning)
        }
        sessionState == SessionState.WAITING -> {
            Triple("Waiting for video", AmberPrimary, Icons.Default.HourglassTop)
        }
        sessionState == SessionState.ACTIVE && playbackState == VideoPlaybackState.PLAYING -> {
            Triple("Watching", SuccessGreen, Icons.Default.PlayArrow)
        }
        sessionState == SessionState.ACTIVE && playbackState != VideoPlaybackState.PLAYING -> {
            Triple("Paused", AmberPrimary, Icons.Default.Pause)
        }
        else -> {
            Triple("Ready to Start", MaterialTheme.colorScheme.primary, Icons.Default.PlayArrow)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watch Task", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("task_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier.testTag("task_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Red Alert Banner (for wrong-video alerts or timeouts)
            AnimatedVisibility(visible = redAlertMessage != null || isGraceActive) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("red_alert_banner"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AlertRedDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alert",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isGraceActive) "Wrong Video Detected!" else "Task Cancelled",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isGraceActive) {
                                    "Playing: \"$currentPlayingTitle\". Return to target video in ${graceSeconds}s or task will cancel."
                                } else {
                                    redAlertMessage ?: "Different video detected. Please tap Start Task to retry."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // Active Session Live Timer Floating Bar
            ActiveWatchTimerBanner(viewModel = viewModel)

            // 2. Video Details Card (Title Fetch with loading/error/retry)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_details_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TARGET VIDEO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )

                        // Reward Tag
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(AmberPrimary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+$selectedTierCoins coins",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AmberPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    when (val state = oEmbedState) {
                        is OEmbedResult.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Fetching YouTube title via oEmbed...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        is OEmbedResult.Success -> {
                            Text(
                                text = state.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Channel: ${state.authorName.ifEmpty { "YouTube Creator" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is OEmbedResult.Error -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Failed to fetch video title",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.fetchOEmbed() },
                                        modifier = Modifier.testTag("retry_oembed_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry")
                                    }

                                    Button(
                                        onClick = { viewModel.useDemoVideo() },
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                                        modifier = Modifier.testTag("use_demo_video_button")
                                    ) {
                                        Text("Use Demo Video", color = Color.Black)
                                    }
                                }
                            }
                        }

                        OEmbedResult.Idle -> {
                            Button(onClick = { viewModel.fetchOEmbed() }) {
                                Text("Load Video Details")
                            }
                        }
                    }
                }
            }

            // 3. Status & Live Timer Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("timer_status_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Big Status Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Live Timer Display "Watched mm:ss / 03:00"
                    val watchedStr = TimeFormatter.formatMillisToMmSs(watchedMillis)
                    val requiredStr = TimeFormatter.formatMillisToMmSs(requiredMillis)

                    Text(
                        text = "Watched $watchedStr / $requiredStr",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .testTag("watch_progress_bar"),
                        color = if (isTaskCompletedEffective) SuccessGreen else AmberPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "${(progressFraction * 100).toInt()}% completed",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. Instructions / Notice Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "WatchEarn simulates natural human searching: types the title letter-by-letter, browses the search list, verifies the channel & thumbnail, and opens the video in the YouTube app without direct bot links. Media session tracking ensures only real watch time counts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            // 4b. Live Search Mode Toggle Switch
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (liveSearchMode) Slate800 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_search_toggle_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
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
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
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
                            text = if (liveSearchMode)
                                "ON: Opens YouTube live to search & locate video in real-time (no loading screen)"
                            else
                                "OFF: Shows in-app animated search typing & candidate verification",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = liveSearchMode,
                        onCheckedChange = { enabled ->
                            viewModel.toggleLiveSearchMode(enabled)
                            if (enabled && !PermissionHelper.isAccessibilityServiceEnabled(context)) {
                                showAccessibilityPromptDialog = true
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = SuccessGreen
                        ),
                        modifier = Modifier.testTag("live_search_switch")
                    )
                }
            }

            // 4c. Selected Watch Goal & Coins Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AmberPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable { showDurationDialog = true }
                    .testTag("task_selected_goal_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Selected Watch Goal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${selectedTierSeconds / 60} Min Watch",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(AmberPrimary, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "+$selectedTierCoins Coins",
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showDurationDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Change Goal", fontSize = 11.sp)
                    }
                }
            }

            // 4d. Like & Comment Bonus Rewards Card (+5 Coins each)
            val currentActiveTaskId = selectedTaskId ?: "default_rick"
            val isTaskLiked = likedTasks.contains(currentActiveTaskId)
            val currentComments = commentCounts[currentActiveTaskId] ?: 0
            val targetVideoTitle = when (val state = oEmbedState) {
                is OEmbedResult.Success -> state.title
                else -> "YouTube Video Task"
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_bonus_activities_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXTRA TASK BONUSES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "+15 COINS POSSIBLE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = AmberPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Like Bonus Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Like Video (+5 Coins)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isTaskLiked) "✓ Earned +5 coins for liking on YouTube" else "Like the video on YouTube while watching (1st time only)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isTaskLiked) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                android.widget.Toast.makeText(
                                    context,
                                    "Like the video on YouTube to earn +5 bonus coins.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            enabled = !isTaskLiked,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTaskLiked) SuccessGreen.copy(alpha = 0.2f) else AmberPrimary.copy(alpha = 0.25f),
                                contentColor = if (isTaskLiked) SuccessGreen else AmberPrimary
                            ),
                            modifier = Modifier.height(34.dp).testTag("task_like_bonus_btn")
                        ) {
                            Text(
                                text = if (isTaskLiked) "✓ Liked" else "👍 Auto +5c",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Comment Bonus Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Comment on Video (+5 Coins each)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (currentComments >= 2)
                                    "✓ Maximum 2 comments completed (+10c)"
                                else
                                    "Post a comment on YouTube ($currentComments/2 done, max 2 = +10c)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (currentComments >= 2) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                android.widget.Toast.makeText(
                                    context,
                                    "Post a comment on YouTube to earn +5 bonus coins.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            enabled = currentComments < 2,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentComments >= 2) SuccessGreen.copy(alpha = 0.2f) else AmberPrimary.copy(alpha = 0.25f),
                                contentColor = if (currentComments >= 2) SuccessGreen else AmberPrimary
                            ),
                            modifier = Modifier.height(34.dp).testTag("task_comment_bonus_btn")
                        ) {
                            Text(
                                text = if (currentComments >= 2) "✓ Done (2/2)" else "💬 Auto +5c ($currentComments/2)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Continuous Watch Rule Warning Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AmberPrimary.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Strict Continuous Watch: You must watch uninterrupted in YouTube until the milestone is reached. Exiting YouTube or returning to the app before reaching the milestone resets watch progress to 00:00 without coins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp
                    )
                }
            }

            // 4d. Floating Side Timer Overlay Status Card
            val isOverlayGranted = remember(sessionState) { PermissionHelper.isOverlayPermissionGranted(context) }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOverlayGranted) Slate800 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("floating_overlay_status_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isOverlayGranted) SuccessGreen.copy(alpha = 0.2f) else AmberPrimary.copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (isOverlayGranted) SuccessGreen else AmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Side Floating Watch Timer",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isOverlayGranted) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isOverlayGranted) SuccessGreen.copy(alpha = 0.2f) else AmberPrimary.copy(alpha = 0.2f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isOverlayGranted) "ACTIVE" else "SETUP NEEDED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOverlayGranted) SuccessGreen else AmberPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Text(
                            text = if (isOverlayGranted)
                                "Timer floats on side of YouTube showing real-time seconds & coins"
                            else
                                "Requires 'Display over other apps' to show timer on side of YouTube",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                    if (!isOverlayGranted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(PermissionHelper.createOverlaySettingsIntent(context))
                                } catch (_: Exception) {}
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp).testTag("enable_overlay_btn")
                        ) {
                            Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // 5. "Start Task" Button
            Button(
                onClick = {
                    if (isTaskLocked) {
                        val remainStr = currentSelectedTask?.getLockRemainingFormatted() ?: "12h"
                        if (currentSelectedTask?.isCompleted == true) {
                            WatchSessionRepository.showTaskIncompleteMessage(
                                "This task is completed and locked for 8 hours ($remainStr remaining)."
                            )
                        } else {
                            WatchSessionRepository.showTaskIncompleteMessage(
                                "This task is locked for 12 hours ($remainStr remaining) due to an incomplete session."
                            )
                        }
                    } else if (!PermissionHelper.isOverlayPermissionGranted(context)) {
                        showOverlayPromptDialog = true
                    } else if (!PermissionHelper.isAccessibilityServiceEnabled(context)) {
                        showAccessibilityPromptDialog = true
                    } else {
                        viewModel.startTask(context)
                    }
                },
                enabled = !isTaskLocked && !isTaskCompletedEffective,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("start_task_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTaskLocked) Color(0xFF334155) else AmberPrimary,
                    disabledContainerColor = Color(0xFF334155)
                )
            ) {
                Icon(
                    imageVector = when {
                        isTaskLocked -> Icons.Default.Lock
                        isTaskCompletedEffective -> Icons.Default.CheckCircle
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    tint = if (isTaskCompletedEffective || isTaskLocked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isTaskLocked && currentSelectedTask?.isCompleted == true -> "Completed • Rewatch in ${currentSelectedTask?.getLockRemainingFormatted()}"
                        isTaskLocked -> "Task Locked (${currentSelectedTask?.getLockRemainingFormatted()})"
                        isTaskCompletedEffective -> "Task Already Completed"
                        sessionState == SessionState.ACTIVE -> "Resume in YouTube"
                        sessionState == SessionState.WAITING -> "Re-open YouTube"
                        sessionState == SessionState.INVALID -> "Restart Task"
                        else -> "Start Task"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isTaskCompletedEffective || isTaskLocked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Black
                )
            }
        }
    }

    // Duration Goal Selection Dialog
    if (showDurationDialog) {
        val titleStr = when (val state = oEmbedState) {
            is OEmbedResult.Success -> state.title
            else -> "YouTube Video Task"
        }
        val authorStr = when (val state = oEmbedState) {
            is OEmbedResult.Success -> state.authorName
            else -> "YouTube Creator"
        }

        DurationSelectionDialog(
            videoTitle = titleStr,
            videoChannel = authorStr,
            thumbnailUrl = TitleMatcher.getThumbnailUrl(currentUrl) ?: "",
            durationSeconds = 1980, // Default 33m or full range
            isLive = currentUrl.contains("live", ignoreCase = true) || titleStr.contains("live", ignoreCase = true),
            initialTierSeconds = selectedTierSeconds,
            onDismiss = { showDurationDialog = false },
            onConfirmSelection = { tier ->
                showDurationDialog = false
                if (!PermissionHelper.isOverlayPermissionGranted(context)) {
                    showOverlayPromptDialog = true
                } else if (!PermissionHelper.isAccessibilityServiceEnabled(context)) {
                    showAccessibilityPromptDialog = true
                } else {
                    viewModel.startTaskWithTier(
                        task = com.example.data.VideoTaskItem(
                            id = viewModel.selectedTaskId.value ?: "active_task",
                            title = titleStr,
                            channelName = authorStr,
                            videoUrl = currentUrl,
                            thumbnailUrl = TitleMatcher.getThumbnailUrl(currentUrl) ?: "",
                            durationSeconds = 1980,
                            selectedDurationSeconds = tier.seconds,
                            rewardCoins = tier.coins
                        ),
                        tier = tier,
                        context = context
                    )
                }
            }
        )
    }

    // Organic Search Discovery Loading Overlay
    val targetTitle = when (val state = oEmbedState) {
        is OEmbedResult.Success -> state.title
        else -> "Target YouTube Video"
    }
    val targetChannel = when (val state = oEmbedState) {
        is OEmbedResult.Success -> state.authorName
        else -> "YouTube Channel"
    }
    val thumbnailUrl = remember(currentUrl) {
        val effectiveUrl = if (currentUrl == "PASTE_MY_YOUTUBE_LINK_HERE") {
            SampleTask.fallbackDemoUrl
        } else {
            currentUrl
        }
        TitleMatcher.getThumbnailUrl(effectiveUrl)
    }

    if (!liveSearchMode) {
        SearchLoadingOverlay(
            searchState = searchProgress,
            targetTitle = targetTitle,
            targetChannel = targetChannel,
            thumbnailUrl = thumbnailUrl
        )
    }

    if (showAccessibilityPromptDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityPromptDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Accessibility,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Enable YouTube Task Monitor",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "To verify that the target video is playing, detect likes & comments inside YouTube, and automatically stop the timer if you change the video, please enable 'Kingo King' in Android Accessibility settings."
                )
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
                TextButton(
                    onClick = {
                        showAccessibilityPromptDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showOverlayPromptDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPromptDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Enable Floating Timer Overlay",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "To see the live countdown timer running on the side of your screen while watching videos on YouTube, please enable 'Display over other apps' for Kingo King.\n\nYou can drag the floating timer anywhere on the screen so it doesn't block video controls."
                )
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
                TextButton(
                    onClick = {
                        showOverlayPromptDialog = false
                        if (!PermissionHelper.isAccessibilityServiceEnabled(context)) {
                            showAccessibilityPromptDialog = true
                        } else {
                            viewModel.startTask(context)
                        }
                    }
                ) {
                    Text("Continue Without Overlay")
                }
            }
        )
    }

    if (sessionInterruptedMessage != null) {
        AlertDialog(
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
                Text(
                    text = "Continuous Watch Interrupted",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = sessionInterruptedMessage ?: ""
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissInterruptedMessage() },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
