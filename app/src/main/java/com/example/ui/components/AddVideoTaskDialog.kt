package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.OEmbedResult
import com.example.data.VideoTaskItem
import com.example.data.WATCH_DURATION_TIERS
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberDark
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.SuccessGreen
import com.example.util.TitleMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

private suspend fun fetchYouTubeOEmbed(cleanUrl: String): OEmbedResult = withContext(Dispatchers.IO) {
    try {
        val encoded = URLEncoder.encode(cleanUrl, "UTF-8")
        val endpoint = "https://www.youtube.com/oembed?url=$encoded&format=json"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
        }
        val code = conn.responseCode
        if (code in 200..299) {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            OEmbedResult.Success(
                title = json.optString("title", ""),
                authorName = json.optString("author_name", "YouTube Creator"),
                thumbnailUrl = json.optString("thumbnail_url", "")
            )
        } else {
            OEmbedResult.Error("HTTP $code")
        }
    } catch (e: Exception) {
        OEmbedResult.Error(e.message ?: "Failed")
    }
}

@Composable
fun AddVideoTaskDialog(
    onDismiss: () -> Unit,
    onAddTask: (VideoTaskItem) -> Unit = {},
    onTaskAdded: (VideoTaskItem) -> Unit = onAddTask
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }
    var channelInput by remember { mutableStateOf("") }
    var thumbnailUrl by remember { mutableStateOf("") }
    var durationMinutesInput by remember { mutableStateOf("10") }
    var isLiveStream by remember { mutableStateOf(false) }
    var isPinnedTask by remember { mutableStateOf(false) }
    var limitClicksEnabled by remember { mutableStateOf(false) }
    var maxClicksInput by remember { mutableStateOf("10") }
    var selectedTierIndex by remember { mutableIntStateOf(0) }
    var isFetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }

    fun fetchMetadata(rawUrl: String) {
        val extracted = TitleMatcher.extractCleanYouTubeUrl(rawUrl)
        val clean = TitleMatcher.cleanYouTubeUrl(extracted)
        if (clean.isBlank()) {
            fetchError = "Please enter a valid YouTube link"
            return
        }
        isFetching = true
        fetchError = null
        scope.launch {
            val res = fetchYouTubeOEmbed(clean)
            isFetching = false
            when (res) {
                is OEmbedResult.Success -> {
                    titleInput = res.title
                    channelInput = res.authorName
                    thumbnailUrl = res.thumbnailUrl.ifBlank {
                        TitleMatcher.getThumbnailUrl(clean) ?: ""
                    }
                    if (res.title.contains("live", ignoreCase = true) ||
                        res.title.contains("24/7", ignoreCase = true)
                    ) {
                        isLiveStream = true
                    }
                }
                is OEmbedResult.Error -> {
                    fetchError = "Auto-fetch couldn't read metadata. You can type Title manually."
                    thumbnailUrl = TitleMatcher.getThumbnailUrl(clean) ?: ""
                }
                else -> {}
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_video_task_dialog"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add New Video Task",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // URL Input + Paste
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        fetchError = null
                    },
                    label = { Text("YouTube Video or Live Link") },
                    placeholder = { Text("https://youtu.be/... or watch?v=...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_task_url_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val pasted = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                if (pasted.isNotBlank()) {
                                    val cleanPasted = TitleMatcher.extractCleanYouTubeUrl(pasted)
                                    urlInput = cleanPasted
                                    fetchMetadata(cleanPasted)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste Link",
                                tint = AmberPrimary
                            )
                        }
                    }
                )

                Button(
                    onClick = { fetchMetadata(urlInput) },
                    enabled = urlInput.isNotBlank() && !isFetching,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_fetch_metadata_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetching Video Details...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Fetch Title & Channel", fontWeight = FontWeight.Bold)
                    }
                }

                fetchError?.let {
                    Text(
                        text = it,
                        color = AlertRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (thumbnailUrl.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = "Preview",
                            modifier = Modifier
                                .width(84.dp)
                                .height(50.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = titleInput.ifBlank { "Detected Thumbnail" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = channelInput.ifBlank { "YouTube Channel" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Exact Video Title") },
                    placeholder = { Text("Auto-filled or enter title") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_task_title_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = channelInput,
                    onValueChange = { channelInput = it },
                    label = { Text("Channel Name") },
                    placeholder = { Text("Auto-filled or enter channel") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_task_channel_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Live Stream Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = if (isLiveStream) AlertRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Is this a LIVE Stream?",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Allows all watch tiers (3m to 30m)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isLiveStream,
                        onCheckedChange = { isLiveStream = it },
                        modifier = Modifier.testTag("new_task_live_switch")
                    )
                }

                // Limit Task Clicks (Max Completions) Toggle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (limitClicksEnabled) AmberPrimary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = if (limitClicksEnabled) 1.dp else 0.dp,
                            color = if (limitClicksEnabled) AmberPrimary else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                tint = if (limitClicksEnabled) AmberPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Limit No. of Clicks (Users)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Auto-remove task after N users complete it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = limitClicksEnabled,
                            onCheckedChange = { limitClicksEnabled = it },
                            modifier = Modifier.testTag("new_task_limit_clicks_switch")
                        )
                    }

                    if (limitClicksEnabled) {
                        OutlinedTextField(
                            value = maxClicksInput,
                            onValueChange = { maxClicksInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("No. of Users / Clicks (e.g. 10)") },
                            placeholder = { Text("10") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_task_max_clicks_input")
                        )
                    }
                }

                // Pin Task to Top Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isPinnedTask) AmberPrimary.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = if (isPinnedTask) AmberDark else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Pin Task to Top",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Keep this task fixed at the top of the list",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isPinnedTask,
                        onCheckedChange = { isPinnedTask = it },
                        modifier = Modifier.testTag("new_task_pin_switch")
                    )
                }

                if (!isLiveStream) {
                    OutlinedTextField(
                        value = durationMinutesInput,
                        onValueChange = { durationMinutesInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Video Length (in Minutes)") },
                        placeholder = { Text("e.g. 10") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_task_duration_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Text(
                    text = "Default Watch Goal Tier:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WATCH_DURATION_TIERS.forEachIndexed { index, tier ->
                        val isSelected = selectedTierIndex == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) AmberPrimary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedTierIndex = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${tier.minutes}m",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "+${tier.coins}c",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) Color.Black else AmberDark
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        val extractedUrl = TitleMatcher.extractCleanYouTubeUrl(urlInput)
                        val cleanUrl = TitleMatcher.cleanYouTubeUrl(extractedUrl).ifBlank { "https://www.youtube.com/watch?v=dQw4w9WgXcQ" }
                        val vidId = TitleMatcher.extractVideoId(cleanUrl)
                        val finalTitle = titleInput.trim().ifBlank {
                            if (!vidId.isNullOrBlank()) "YouTube Video ($vidId)" else "YouTube Video Task"
                        }
                        val finalChannel = channelInput.trim().ifBlank { "YouTube Creator" }
                        val finalThumb = thumbnailUrl.ifBlank {
                            TitleMatcher.getThumbnailUrl(cleanUrl) ?: ""
                        }
                        val durMins = durationMinutesInput.toIntOrNull() ?: 10
                        val durSeconds = if (isLiveStream) 0 else (durMins * 60).coerceAtLeast(180)
                        val chosenTier = WATCH_DURATION_TIERS[selectedTierIndex]
                        val maxCompletionsVal = if (limitClicksEnabled) {
                            (maxClicksInput.toIntOrNull() ?: 10).coerceAtLeast(1)
                        } else 0

                        val nowMillis = System.currentTimeMillis()
                        val newTask = VideoTaskItem(
                            id = "task_${nowMillis}",
                            title = finalTitle,
                            channelName = finalChannel,
                            videoUrl = cleanUrl,
                            thumbnailUrl = finalThumb,
                            durationSeconds = durSeconds,
                            isLive = isLiveStream,
                            isCompleted = false,
                            selectedDurationSeconds = chosenTier.seconds,
                            rewardCoins = chosenTier.coins,
                            createdAt = nowMillis,
                            isPinned = isPinnedTask,
                            pinnedAt = if (isPinnedTask) nowMillis else 0L,
                            maxCompletions = maxCompletionsVal,
                            completedCount = 0
                        )
                        onTaskAdded(newTask)
                    },
                    enabled = urlInput.isNotBlank() || titleInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_add_task_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Video to Task List",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
