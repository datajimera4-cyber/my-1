package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.example.ui.components.ActiveWatchTimerBanner
import com.example.ui.components.ChangeVideoLinkDialog
import com.example.ui.components.SearchLoadingOverlay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.data.OEmbedResult
import com.example.data.SampleTask
import com.example.ui.theme.AmberDark
import com.example.ui.theme.AmberLight
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessGreen
import com.example.util.TimeFormatter
import com.example.util.TitleMatcher
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val walletBalance by viewModel.walletBalance.collectAsState()
    val isCompleted by viewModel.isTaskCompleted.collectAsState()
    val oEmbedState by viewModel.oEmbedState.collectAsState()
    val activeUrl by viewModel.currentVideoUrl.collectAsState()
    val searchProgress by viewModel.searchProgress.collectAsState()
    val liveSearchMode by viewModel.liveSearchMode.collectAsState()

    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showChangeLinkDialog by remember { mutableStateOf(false) }

    val thumbnailUrl = remember(activeUrl) {
        val effectiveUrl = if (activeUrl == "PASTE_MY_YOUTUBE_LINK_HERE") {
            SampleTask.fallbackDemoUrl
        } else {
            activeUrl
        }
        TitleMatcher.getThumbnailUrl(effectiveUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(AmberPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Kingo King",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Sit Back - Watch & Earn",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showChangeLinkDialog = true },
                        modifier = Modifier.testTag("topbar_set_link_button")
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
        modifier = modifier.testTag("home_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Live / Active Watch Session Floating Bar
            ActiveWatchTimerBanner(viewModel = viewModel)

            // 1. Shiny Wallet Balance Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.navigateTo(AppScreen.WALLET) }
                    .testTag("wallet_balance_card"),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Slate900, Slate800, AmberDark.copy(alpha = 0.4f))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "WALLET BALANCE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AmberLight,
                                letterSpacing = 1.sp
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "View Wallet",
                                tint = AmberLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = "Coin Icon",
                                tint = AmberPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "$walletBalance",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Coins",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AmberPrimary
                            )
                        }
                    }
                }
            }

            // 2. Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Featured Task",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .background(
                            if (isCompleted) SuccessGreen.copy(alpha = 0.2f)
                            else AmberPrimary.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isCompleted) "Completed" else "Available",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) SuccessGreen else AmberPrimary
                    )
                }
            }

            // 3. Main Task Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.navigateTo(AppScreen.TASK) }
                    .testTag("task_card"),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    // Thumbnail container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(thumbnailUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Video Thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Placeholder",
                                tint = Color.Gray,
                                modifier = Modifier.size(56.dp)
                            )
                        }

                        // Floating play badge
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isCompleted) SuccessGreen else Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        // Duration pill overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Watch ${TimeFormatter.formatSecondsToMmSs(SampleTask.requiredSeconds)} min",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Reward Badge overlay (top left)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .background(AmberPrimary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "+${SampleTask.rewardCoins} coins",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black
                            )
                        }
                    }

                    // Content below thumbnail
                    Column(modifier = Modifier.padding(16.dp)) {
                        when (val state = oEmbedState) {
                            is OEmbedResult.Success -> {
                                Text(
                                    text = state.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.authorName.ifEmpty { "YouTube Creator" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            is OEmbedResult.Loading -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Fetching YouTube video details...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            is OEmbedResult.Error -> {
                                Text(
                                    text = "Task Video Link",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OEmbedResult.Idle -> {
                                Text(
                                    text = "Ready to load task",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { viewModel.navigateTo(AppScreen.TASK) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_task_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCompleted) SuccessGreen else AmberPrimary
                            )
                        ) {
                            Text(
                                text = if (isCompleted) "View Completed Task" else "Open Task",
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showChangeLinkDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("change_video_link_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste Any Video Link", fontSize = 13.sp)
                        }
                    }
                }
            }

            // View All Video Tasks shortcut button
            Button(
                onClick = { viewModel.switchTab(AppScreen.TASKS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("home_view_all_tasks_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "View All Video Tasks (+ Add New)",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            // 4. Quick Action Buttons
            Text(
                text = "Controls & Settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.navigateTo(AppScreen.WALLET) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("wallet_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Wallet")
                }

                OutlinedButton(
                    onClick = { viewModel.navigateTo(AppScreen.DIAGNOSTICS) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("diagnostics_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Troubleshoot,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Diagnostics")
                }
            }

            // Reset Button
            Button(
                onClick = { showResetConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("reset_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset (Clear Wallet, Completion, History)")
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset WatchEarn?") },
            text = {
                Text("This will clear your local wallet balance, completion flag, saved watch time, and transaction history. You will be able to perform the task again.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAll(context)
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_reset_button")
                ) {
                    Text("Reset All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Change Video Link Dialog
    if (showChangeLinkDialog) {
        ChangeVideoLinkDialog(
            initialUrl = activeUrl,
            onDismiss = { showChangeLinkDialog = false },
            onSaveLink = { newUrl ->
                viewModel.setVideoUrl(newUrl)
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

    if (!liveSearchMode) {
        SearchLoadingOverlay(
            searchState = searchProgress,
            targetTitle = targetTitle,
            targetChannel = targetChannel,
            thumbnailUrl = thumbnailUrl
        )
    }
}
