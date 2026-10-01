package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.OEmbedFetcher
import com.example.data.OEmbedResult
import com.example.data.VideoTaskItem
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate800
import com.example.ui.theme.SuccessGreen
import com.example.util.TitleMatcher
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AddVideoTaskDialog(
    onDismiss: () -> Unit,
    onTaskAdded: (VideoTaskItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }
    var channelInput by remember { mutableStateOf("") }
    var thumbnailUrl by remember { mutableStateOf("") }
    var isLive by remember { mutableStateOf(false) }
    var isPinned by remember { mutableStateOf(false) }
    var durationMinutesText by remember { mutableStateOf("10") }
    var isFetching by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun fetchDetails(targetUrl: String) {
        val cleaned = TitleMatcher.extractCleanYouTubeUrl(targetUrl.trim())
        if (cleaned.isEmpty()) return
        val vid = TitleMatcher.extractVideoId(cleaned)
        if (!vid.isNullOrBlank() && thumbnailUrl.isBlank()) {
            thumbnailUrl = "https://img.youtube.com/vi/$vid/hqdefault.jpg"
        }
        isFetching = true
        errorMessage = null

        scope.launch {
            val result = OEmbedFetcher.fetchOEmbed(cleaned)
            isFetching = false
            when (result) {
                is OEmbedResult.Success -> {
                    titleInput = result.title
                    channelInput = result.authorName
                    thumbnailUrl = result.thumbnailUrl.ifEmpty {
                        TitleMatcher.getThumbnailUrl(cleaned) ?: ""
                    }
                    if (result.title.contains("live", ignoreCase = true) ||
                        result.title.contains("24/7", ignoreCase = true)
                    ) {
                        isLive = true
                    }
                }
                is OEmbedResult.Error -> {
                    thumbnailUrl = TitleMatcher.getThumbnailUrl(cleaned) ?: ""
                    if (titleInput.isBlank() && !vid.isNullOrBlank()) {
                        titleInput = "YouTube Video ($vid)"
                    }
                    if (channelInput.isBlank()) {
                        channelInput = "YouTube Creator"
                    }
                }
                else -> {}
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
                .testTag("add_video_task_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(AmberPrimary, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Add Video Task",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = "Enter any YouTube video or live stream link to create a new watching task.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // URL Input Field + Paste Action
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { raw ->
                        val sharedTitle = TitleMatcher.extractSharedTitle(raw)
                        if (!sharedTitle.isNullOrBlank() && titleInput.isBlank()) {
                            titleInput = sharedTitle
                        }
                        val cleaned = if (raw.contains(" ") || raw.contains("\n")) {
                            TitleMatcher.extractCleanYouTubeUrl(raw)
                        } else {
                            raw
                        }
                        urlInput = cleaned
                        if (TitleMatcher.extractVideoId(cleaned) != null || cleaned.contains("youtu", ignoreCase = true)) {
                            fetchDetails(cleaned)
                        }
                    },
                    label = { Text("YouTube Video URL") },
                    placeholder = { Text("https://www.youtube.com/watch?v=...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                                    val cleaned = TitleMatcher.extractCleanYouTubeUrl(pasteText)
                                    urlInput = cleaned
                                    val sharedTitle = TitleMatcher.extractSharedTitle(pasteText)
                                    if (!sharedTitle.isNullOrBlank() && titleInput.isBlank()) {
                                        titleInput = sharedTitle
                                    }
                                    if (cleaned.isNotEmpty()) {
                                        fetchDetails(cleaned)
                                    }
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_task_url_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Fetch Button if not auto-fetched
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { fetchDetails(urlInput) },
                        enabled = urlInput.isNotBlank() && !isFetching,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isFetching) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fetching Details...", fontSize = 12.sp)
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fetch Video Info", fontSize = 12.sp)
                        }
                    }
                }

                // Editable Title & Channel Fields (auto-filled from link, or manually editable)
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Video Title (Auto-filled or Edit)") },
                    placeholder = { Text("Video Title") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_task_title_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = channelInput,
                    onValueChange = { channelInput = it },
                    label = { Text("Channel Name (Auto-filled or Edit)") },
                    placeholder = { Text("YouTube Channel") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_task_channel_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Error notice if any
                errorMessage?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Live Preview Card
                if (titleInput.isNotBlank() || thumbnailUrl.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 80.dp, height = 50.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumbnailUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(thumbnailUrl).crossfade(true).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = titleInput.ifEmpty { "Fetched YouTube Video" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = channelInput.ifEmpty { "YouTube Creator" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Live Stream Toggle
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = if (isLive) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Live Stream Video",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isLive) "All duration options (3m - 30m) available" else "Normal video with fixed duration",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = isLive,
                            onCheckedChange = { isLive = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFE53935)
                            )
                        )
                    }
                }

                // Video Length input (if not Live)
                if (!isLive) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Video Duration (Minutes)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Quick duration preset chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("5", "10", "20", "33").forEach { min ->
                                FilterChip(
                                    selected = durationMinutesText == min,
                                    onClick = { durationMinutesText = min },
                                    label = { Text("${min}m") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AmberPrimary,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = durationMinutesText,
                            onValueChange = { durationMinutesText = it.filter { char -> char.isDigit() } },
                            label = { Text("Exact Duration (Minutes)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Pin Task to Top toggle
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPinned) AmberPrimary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📌 Pin Task to Top",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Keep this task fixed at the top even when new tasks are added",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isPinned,
                            onCheckedChange = { isPinned = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AmberPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Create Task Button
                Button(
                    onClick = {
                        if (isSubmitting) return@Button
                        scope.launch {
                            isSubmitting = true
                            val rawCleaned = TitleMatcher.extractCleanYouTubeUrl(urlInput.trim())
                            val finalUrl = TitleMatcher.cleanYouTubeUrl(rawCleaned).ifEmpty { rawCleaned }
                            var resolvedTitle = titleInput.trim()
                            var resolvedChannel = channelInput.trim()
                            var resolvedThumb = thumbnailUrl.trim()

                            if (resolvedTitle.isBlank()) {
                                val fetched = OEmbedFetcher.fetchOEmbed(finalUrl)
                                if (fetched is OEmbedResult.Success) {
                                    resolvedTitle = fetched.title
                                    if (resolvedChannel.isBlank()) resolvedChannel = fetched.authorName
                                    if (resolvedThumb.isBlank()) resolvedThumb = fetched.thumbnailUrl
                                }
                            }

                            val vid = TitleMatcher.extractVideoId(finalUrl)
                            val finalTitle = resolvedTitle.ifBlank {
                                if (!vid.isNullOrBlank()) "YouTube Video ($vid)" else "YouTube Video Task"
                            }
                            val finalChannel = resolvedChannel.ifBlank { "YouTube Creator" }
                            val finalThumb = resolvedThumb.ifBlank { TitleMatcher.getThumbnailUrl(finalUrl) ?: "" }
                            val minutes = (durationMinutesText.toIntOrNull() ?: 10).coerceAtLeast(3)
                            val durationSec = if (isLive) 0 else minutes * 60
                            val targetTier = com.example.data.WATCH_DURATION_TIERS
                                .filter { isLive || it.minutes <= minutes }
                                .maxByOrNull { it.minutes }
                                ?: com.example.data.WATCH_DURATION_TIERS.first()

                            val now = System.currentTimeMillis()
                            val newTask = VideoTaskItem(
                                id = UUID.randomUUID().toString(),
                                title = finalTitle,
                                channelName = finalChannel,
                                videoUrl = finalUrl,
                                thumbnailUrl = finalThumb,
                                durationSeconds = durationSec,
                                isLive = isLive,
                                isCompleted = false,
                                rewardCoins = targetTier.coins,
                                selectedDurationSeconds = targetTier.seconds,
                                createdAt = now,
                                isPinned = isPinned,
                                pinnedAt = if (isPinned) now else 0L
                            )
                            isSubmitting = false
                            onTaskAdded(newTask)
                        }
                    },
                    enabled = urlInput.isNotBlank() && !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_add_task_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Adding Task...",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 15.sp
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Task to List",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
