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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import com.example.ui.components.ActiveWatchTimerBanner
import com.example.ui.components.ChangeVideoLinkDialog
import com.example.ui.components.DurationSelectionDialog
import com.example.ui.components.SearchLoadingOverlay
import com.example.ui.theme.Slate800
import com.example.util.TitleMatcher
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
    val isCompleted by viewModel.isTaskCompleted.collectAsState()
    val currentUrl by viewModel.currentVideoUrl.collectAsState()
    val searchProgress by viewModel.searchProgress.collectAsState()
    val liveSearchMode by viewModel.liveSearchMode.collectAsState()
    val selectedTierSeconds by viewModel.selectedTierSeconds.collectAsState()
    val selectedTierCoins by viewModel.selectedTierCoins.collectAsState()

    var showChangeLinkDialog by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }

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
        isCompleted || sessionState == SessionState.COMPLETED -> {
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
                actions = {
                    IconButton(
                        onClick = { showChangeLinkDialog = true },
                        modifier = Modifier.testTag("task_set_link_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Paste YouTube Video Link",
                            tint = AmberPrimary
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
                                text = "+${SampleTask.rewardCoins} coins",
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
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showChangeLinkDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("task_change_link_btn"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Change Video Link", fontSize = 12.sp)
                            }
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
                        color = if (isCompleted) SuccessGreen else AmberPrimary,
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
                        onCheckedChange = { viewModel.toggleLiveSearchMode(it) },
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

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // 5. "Start Task" Button
            Button(
                onClick = { viewModel.startTask(context) },
                enabled = !isCompleted && sessionState != SessionState.COMPLETED,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("start_task_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isCompleted -> "Task Already Completed"
                        sessionState == SessionState.ACTIVE -> "Resume in YouTube"
                        sessionState == SessionState.WAITING -> "Re-open YouTube"
                        sessionState == SessionState.INVALID -> "Restart Task"
                        else -> "Start Task"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else Color.Black
                )
            }
        }
    }

    // Change Video Link Dialog
    if (showChangeLinkDialog) {
        ChangeVideoLinkDialog(
            initialUrl = currentUrl,
            onDismiss = { showChangeLinkDialog = false },
            onSaveLink = { newUrl ->
                viewModel.setVideoUrl(newUrl)
            }
        )
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
}
