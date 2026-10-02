package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.SessionState
import com.example.data.VideoTaskItem
import com.example.data.calculateCoinsForDuration
import com.example.data.getEffectiveDurationTiers
import com.example.service.YouTubeLiveSearchService
import com.example.ui.components.ActiveWatchTimerBanner
import com.example.ui.components.AdminPostsBannerSection
import com.example.ui.components.DurationSelectionDialog
import com.example.ui.components.SearchLoadingOverlay
import com.example.ui.components.SupportChatDialog
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberDark
import com.example.ui.theme.AmberLight
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessGreen
import com.example.util.PermissionHelper
import com.example.util.TimeFormatter
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val walletBalance by viewModel.walletBalance.collectAsStateWithLifecycle()
    val tasks by viewModel.videoTasks.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val watchedMillis by viewModel.watchedMillis.collectAsStateWithLifecycle()
    val requiredMillis by viewModel.requiredMillis.collectAsStateWithLifecycle()
    val searchProgress by viewModel.searchProgress.collectAsStateWithLifecycle()
    val likedTasks by viewModel.likedTasks.collectAsStateWithLifecycle()
    val commentCounts by viewModel.commentCounts.collectAsStateWithLifecycle()
    val adminPosts by viewModel.adminPosts.collectAsStateWithLifecycle()
    val dismissedPostIds by viewModel.dismissedPostIds.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val supportMessages by viewModel.supportMessages.collectAsStateWithLifecycle()

    val featuredTasks = remember(tasks) { tasks.take(3) }
    var taskForTierDialog by remember { mutableStateOf<VideoTaskItem?>(null) }
    var showPermissionGateDialog by remember { mutableStateOf(false) }
    var showSupportChatDialog by remember { mutableStateOf(false) }

    // Live permission check refreshed whenever screen resumes
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

    val allPermissionsReady = isAccessibilityReady && isOverlayReady

    val activeSearchTitle by viewModel.targetTaskTitle.collectAsStateWithLifecycle()
    val activeSearchTask = remember(tasks, activeSearchTitle) {
        tasks.find { it.title == activeSearchTitle } ?: tasks.firstOrNull()
    }
    SearchLoadingOverlay(
        searchState = searchProgress,
        targetTitle = activeSearchTitle ?: activeSearchTask?.title ?: "",
        targetChannel = activeSearchTask?.channelName ?: "",
        thumbnailUrl = activeSearchTask?.thumbnailUrl
    )

    Column(modifier = modifier.fillMaxSize()) {
        // Professional Anchored Top Header Bar (Edge-to-Edge Status Bar Safe)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        com.example.ui.components.KingoLogoBadge(
                            isAdmin = false,
                            size = 40.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Text(
                                text = if (currentUser != null) {
                                    "Welcome, ${currentUser?.name?.ifBlank { currentUser?.email?.substringBefore("@") }}"
                                } else {
                                    "Watch YouTube Videos & Earn Coins"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Admin Banners / Posts (Clean without extra Admin/Pinned badges)
            AdminPostsBannerSection(
                posts = adminPosts,
                dismissedIds = dismissedPostIds,
                targetTab = "HOME",
                onDismiss = { viewModel.dismissAdminPost(it) }
            )

            // Active Watch Session Banner (if currently watching)
            if (sessionState == SessionState.ACTIVE || sessionState == SessionState.WAITING) {
                ActiveWatchTimerBanner(viewModel = viewModel)
            }

            // Hero Coin Balance Card (iOS Wallet Card Style)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_wallet_card"),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1E2433), Color(0xFF121722), Color(0xFF0D111A))
                            )
                        )
                        .border(1.dp, AmberPrimary.copy(alpha = 0.32f), RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(AmberPrimary.copy(alpha = 0.18f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MonetizationOn,
                                        contentDescription = null,
                                        tint = AmberPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "TOTAL BALANCE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = Color.White.copy(alpha = 0.70f)
                                    )
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = "$walletBalance",
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 32.sp,
                                            color = AmberPrimary,
                                            modifier = Modifier.testTag("home_coin_balance_text")
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "COINS",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = AmberLight,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { viewModel.switchTab(AppScreen.WALLET) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberPrimary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("home_open_wallet_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Wallet",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Complete video tasks to earn coins",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.70f),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SuccessGreen.copy(alpha = 0.16f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(SuccessGreen, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "${tasks.count { !it.isLocked }} Available",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Setup Banner ONLY if Accessibility or Overlay Permission is not enabled yet
            if (!allPermissionsReady) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPermissionGateDialog = true }
                        .testTag("home_permission_warning_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = AlertRed.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AlertRed.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AlertRed,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Enable Task Verification Permissions",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AlertRed,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Required once for YouTube timer overlay & auto-verification",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AlertRed
                        ) {
                            Text(
                                text = "Enable",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Featured Tasks Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Featured Tasks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Top ${featuredTasks.size} of ${tasks.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Show up to 3 Featured Tasks (Card click does nothing; only Watch Task button starts task)
            if (featuredTasks.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active video tasks available right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    featuredTasks.forEach { task ->
                        FeaturedHomeTaskCard(
                            task = task,
                            isLiked = likedTasks.contains(task.id),
                            commentCount = commentCounts[task.id] ?: 0,
                            onWatchTaskClick = {
                                val accOk = YouTubeLiveSearchService.isServiceConnected ||
                                        PermissionHelper.isAccessibilityServiceEnabled(context)
                                val overOk = PermissionHelper.canDrawOverlays(context)
                                isAccessibilityReady = accOk
                                isOverlayReady = overOk
                                if (!accOk || !overOk) {
                                    showPermissionGateDialog = true
                                } else {
                                    val effectiveTiers = getEffectiveDurationTiers(task)
                                    if (!task.isLive && task.selectedDurationSeconds > 0 && effectiveTiers.size == 1) {
                                        viewModel.startTaskWithTier(task, effectiveTiers.first(), context)
                                    } else {
                                        taskForTierDialog = task
                                    }
                                }
                            }
                        )
                    }

                    // View All Tasks Button Below the 3 Featured Tasks
                    OutlinedButton(
                        onClick = { viewModel.switchTab(AppScreen.TASKS) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("home_view_all_tasks_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, AmberPrimary)
                    ) {
                        Text(
                            text = "View All Tasks (${tasks.size})",
                            fontWeight = FontWeight.ExtraBold,
                            color = AmberPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // Duration Tier Selection Dialog
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

    // Support Chat Dialog
    if (showSupportChatDialog) {
        SupportChatDialog(
            currentUser = currentUser,
            allMessages = supportMessages,
            onSendMessage = { msg -> viewModel.sendSupportMessage(msg) },
            onDismiss = { showSupportChatDialog = false }
        )
    }

    // Mandatory Permission Gate Dialog
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
                                Text(
                                    text = "1. Accessibility Service",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
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
                                Text(
                                    text = "2. Display Over Other Apps",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
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
private fun FeaturedHomeTaskCard(
    task: VideoTaskItem,
    isLiked: Boolean,
    commentCount: Int,
    onWatchTaskClick: () -> Unit
) {
    val taskLocked = task.isLocked
    val lockCountdown = if (taskLocked) task.getLockRemainingFormatted() else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .testTag("home_featured_task_card_${task.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Video Thumbnail Banner (16:9 Adaptive Ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
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

                // Gradient overlay at bottom of thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )

                // Bottom-left Live / Video Length Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
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
                            text = when {
                                task.isLive -> "LIVE"
                                task.selectedDurationSeconds > 0 -> TimeFormatter.formatSecondsToMmSs(task.selectedDurationSeconds)
                                else -> TimeFormatter.formatSecondsToMmSs(task.durationSeconds)
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Top-right Coin Reward Badge
                val coinBadgeLabel = remember(task.isLive, task.durationSeconds, task.selectedDurationSeconds) {
                    val tiers = getEffectiveDurationTiers(task)
                    val minC = tiers.firstOrNull()?.coins ?: 10
                    val maxC = tiers.lastOrNull()?.coins ?: calculateCoinsForDuration(task.durationSeconds)
                    if (minC == maxC) "+$maxC Coins" else "+$minC to +$maxC Coins"
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(AmberPrimary, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = coinBadgeLabel,
                        color = Color.Black,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Details & Action Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = task.channelName,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Like & Comment Bonus Status Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isLiked) SuccessGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ThumbUp,
                                contentDescription = null,
                                tint = if (isLiked) SuccessGreen else AmberDark,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isLiked) "Liked (+5c ✓)" else "Like +5c",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                color = if (isLiked) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (commentCount >= 2) SuccessGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Comment,
                                contentDescription = null,
                                tint = if (commentCount > 0) SuccessGreen else AmberDark,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Comment ($commentCount/2) +5c",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                color = if (commentCount > 0) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Watch Task Button (Only clicking this button starts the task)
                Button(
                    onClick = onWatchTaskClick,
                    enabled = !taskLocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("home_watch_task_button_${task.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPrimary,
                        contentColor = Color.Black,
                        disabledContainerColor = if (task.isCompleted) SuccessGreen.copy(alpha = 0.14f) else AlertRed.copy(alpha = 0.12f),
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
                                "Completed • Unlocks in $lockCountdown"
                            } else {
                                "Locked • Unlocks in $lockCountdown"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
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
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
