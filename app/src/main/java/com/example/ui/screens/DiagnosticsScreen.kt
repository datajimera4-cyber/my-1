package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LogEvent
import com.example.data.LogType
import com.example.data.MatchResult
import com.example.data.OEmbedResult
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessGreen
import com.example.util.PermissionHelper
import com.example.util.TimeFormatter
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { viewModel.navigateBack() }

    val oEmbedState by viewModel.oEmbedState.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val matchResult by viewModel.matchResult.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val currentTitle by viewModel.currentMediaTitle.collectAsState()
    val currentArtist by viewModel.currentMediaArtist.collectAsState()
    val watchedMillis by viewModel.watchedMillis.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val mediaSessionDetected by viewModel.mediaSessionDetected.collectAsState()
    val eventLogs by viewModel.eventLogs.collectAsState()
    val searchProgress by viewModel.searchProgress.collectAsState()
    val liveSearchMode by viewModel.liveSearchMode.collectAsState()

    // Permissions live check
    val isNotificationAccess = PermissionHelper.isNotificationAccessGranted(context)
    val isNotificationPerm = PermissionHelper.isNotificationPermissionGranted(context)
    val isBatteryOptDisabled = PermissionHelper.isBatteryOptimizationDisabled(context)
    val isYouTubeInstalled = PermissionHelper.isYouTubeAppInstalled(context)

    val oEmbedFetched = oEmbedState is OEmbedResult.Success
    val oEmbedTitle = when (val state = oEmbedState) {
        is OEmbedResult.Success -> state.title
        is OEmbedResult.Error -> "Failed: ${state.message}"
        is OEmbedResult.Loading -> "Fetching..."
        OEmbedResult.Idle -> "Not fetched"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics & Live Telemetry", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("diagnostics_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchOEmbed() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh oEmbed"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier.testTag("diagnostics_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Live Status Checklist
            item {
                Text(
                    text = "System & Detection Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("diagnostics_checklist_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChecklistRow(
                            label = "Internet / oEmbed Title",
                            value = oEmbedTitle,
                            isOk = oEmbedFetched,
                            testTag = "diag_oembed"
                        )
                        ChecklistRow(
                            label = "Notification Access",
                            value = if (isNotificationAccess) "Granted" else "Missing (Enable in Settings)",
                            isOk = isNotificationAccess,
                            testTag = "diag_notif_access"
                        )
                        ChecklistRow(
                            label = "Notifications Permission",
                            value = if (isNotificationPerm) "Granted" else "Denied",
                            isOk = isNotificationPerm,
                            testTag = "diag_notif_perm"
                        )
                        ChecklistRow(
                            label = "Battery Optimization",
                            value = if (isBatteryOptDisabled) "Disabled (Optimal)" else "Active (May sleep)",
                            isOk = isBatteryOptDisabled,
                            testTag = "diag_battery_opt"
                        )
                        ChecklistRow(
                            label = "YouTube App Installed",
                            value = if (isYouTubeInstalled) "Installed" else "Not found (Fallback to browser)",
                            isOk = isYouTubeInstalled,
                            testTag = "diag_youtube_app"
                        )
                        ChecklistRow(
                            label = "YouTube Media Session",
                            value = if (mediaSessionDetected) "Active session detected" else "No active session",
                            isOk = mediaSessionDetected,
                            testTag = "diag_media_session"
                        )
                        ChecklistRow(
                            label = "Playing Title & Artist",
                            value = if (currentTitle != null) "\"$currentTitle\" - $currentArtist" else "None detected",
                            isOk = currentTitle != null,
                            testTag = "diag_playing_metadata"
                        )
                        ChecklistRow(
                            label = "Title Match Result",
                            value = matchResult.name,
                            isOk = matchResult == MatchResult.MATCH,
                            testTag = "diag_match_result"
                        )
                        ChecklistRow(
                            label = "Playback State",
                            value = playbackState.name,
                            isOk = playbackState.name == "PLAYING",
                            testTag = "diag_playback_state"
                        )
                        ChecklistRow(
                            label = "Foreground Service",
                            value = if (isServiceRunning) "Running with ongoing notification" else "Stopped",
                            isOk = isServiceRunning,
                            testTag = "diag_foreground_service"
                        )
                        ChecklistRow(
                            label = "Session State & Watched",
                            value = "$sessionState • ${TimeFormatter.formatMillisToMmSs(watchedMillis)} watched",
                            isOk = sessionState.name == "ACTIVE" || sessionState.name == "COMPLETED",
                            testTag = "diag_session_state"
                        )
                        ChecklistRow(
                            label = "Organic Title Search",
                            value = if (searchProgress.isSearching) "Searching: ${searchProgress.stepText}" else if (searchProgress.foundRank != null) "Verified (Rank #${searchProgress.foundRank})" else "Ready to Search",
                            isOk = true,
                            testTag = "diag_organic_search"
                        )
                        ChecklistRow(
                            label = "Search Mode Setting",
                            value = if (liveSearchMode) "Live Realtime Mode (Direct YouTube Search, No Loading Screen)" else "In-App Simulation Mode (Typing & Radar Simulation)",
                            isOk = true,
                            testTag = "diag_search_mode"
                        )
                    }
                }
            }

            // Dedicated Admin Web Software Tool (Remote PC / Laptop)
            item {
                val adminRunning by viewModel.adminServerRunning.collectAsState()
                val adminUrl by viewModel.adminServerUrl.collectAsState()
                val effectiveAdminUrl = if (adminUrl.isNotBlank()) adminUrl else "http://${com.example.admin.AdminWebServer.getLocalIpAddress(context)}:8888"

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    modifier = Modifier.fillMaxWidth().testTag("diag_admin_web_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(AmberPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Admin Web Software Tool",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Open on PC / Laptop Browser",
                                        fontSize = 11.sp,
                                        color = AmberPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .background(SuccessGreen.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (adminRunning) "LIVE PORT 8888" else "ACTIVE",
                                    color = SuccessGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Text(
                            text = "Admin panel is a separate web dashboard that can be opened anywhere on your PC, Laptop, or Tablet browser on the same network to manage tasks, approve payouts, and edit coins:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate800, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = effectiveAdminUrl,
                                color = AmberPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(effectiveAdminUrl)).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Open in Browser", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("Admin Web Tool URL", effectiveAdminUrl))
                                        Toast.makeText(context, "Admin URL copied! Open in PC browser.", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Copy PC Link", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Section 2: Live Log Events (Last 30)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Event Logs (Last 30)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${eventLogs.size} logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (eventLogs.isEmpty()) {
                item {
                    Text(
                        text = "No logs recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(eventLogs, key = { it.id }) { log ->
                    LogEventItem(log)
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    label: String,
    value: String,
    isOk: Boolean,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = if (isOk) "OK" else "Issue",
                tint = if (isOk) SuccessGreen else AlertRed,
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .background(
                    if (isOk) SuccessGreen.copy(alpha = 0.15f) else AlertRed.copy(alpha = 0.15f),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isOk) "PASS" else "FAIL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isOk) SuccessGreen else AlertRed
            )
        }
    }
}

@Composable
private fun LogEventItem(log: LogEvent) {
    val tagColor = when (log.type) {
        LogType.SUCCESS -> SuccessGreen
        LogType.WARNING -> AmberPrimary
        LogType.ERROR -> AlertRed
        LogType.INFO -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_item_${log.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = log.timestamp,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.width(62.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(tagColor, CircleShape)
                    .align(Alignment.CenterVertically)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
