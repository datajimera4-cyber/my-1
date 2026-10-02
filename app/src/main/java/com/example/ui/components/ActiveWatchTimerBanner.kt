package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SessionState
import com.example.ui.theme.AmberDark
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessGreen
import com.example.util.TimeFormatter
import com.example.viewmodel.MainViewModel

@Composable
fun ActiveWatchTimerBanner(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionState by viewModel.sessionState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val watchedMillis by viewModel.watchedMillis.collectAsState()
    val requiredMillis by viewModel.requiredMillis.collectAsState()
    val targetTitle by viewModel.targetTaskTitle.collectAsState()
    val currentMilestone by viewModel.currentMilestoneTier.collectAsState()
    val searchProgress by viewModel.searchProgress.collectAsState()

    val isVisible = !searchProgress.isSearching &&
            (sessionState == SessionState.ACTIVE || sessionState == SessionState.WAITING || watchedMillis > 0L) &&
            sessionState != SessionState.COMPLETED

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val watchedSecs = (watchedMillis / 1000).toInt()
        val requiredSecs = (requiredMillis / 1000).toInt()
        val progress = if (requiredMillis > 0) (watchedMillis.toFloat() / requiredMillis.toFloat()).coerceIn(0f, 1f) else 0f

        val hasReachedMinimum3Min = watchedSecs >= 180

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    color = if (hasReachedMinimum3Min) SuccessGreen else AmberPrimary,
                    shape = RoundedCornerShape(18.dp)
                )
                .testTag("active_watch_timer_banner")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top row: Status tag + Video title + Stop button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val isPlaying = sessionState == SessionState.ACTIVE && playbackState == com.example.data.VideoPlaybackState.PLAYING
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (isPlaying) SuccessGreen else AmberPrimary,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPlaying) "WATCHING LIVE ▶" else if (sessionState == SessionState.ACTIVE) "PAUSED ⏸ (OPEN YOUTUBE)" else "READY / WAITING",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = if (isPlaying) SuccessGreen else AmberPrimary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = targetTitle ?: "YouTube Video",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Center row: Real-time Timer Counter & Progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = TimeFormatter.formatMillisToMmSs(watchedMillis),
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = " / ${TimeFormatter.formatMillisToMmSs(requiredMillis)}",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Coins unlocked badge
                    if (currentMilestone != null) {
                        Box(
                            modifier = Modifier
                                .background(SuccessGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .border(1.dp, SuccessGreen.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "+${currentMilestone?.coins} Coins Secured!",
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Min 3:00 for coins",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (hasReachedMinimum3Min) SuccessGreen else AmberPrimary,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )

                // Milestone explanation text
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!hasReachedMinimum3Min) {
                        val remainingSecs = 180 - watchedSecs
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Watched ${watchedSecs}s. Continuous watch requires 3 min minimum. $remainingSecs s remaining for 10 coins!",
                            color = AmberPrimary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    } else {
                        val nextMilestoneText = when {
                            watchedSecs < 300 -> "Next: 17 Coins at 5:00 min"
                            watchedSecs < 600 -> "Next: 35 Coins at 10:00 min"
                            watchedSecs < 1200 -> "Next: 72 Coins at 20:00 min"
                            watchedSecs < 1800 -> "Next: 110 Coins at 30:00 min"
                            else -> "Maximum milestone reached!"
                        }
                        Text(
                            text = "✅ ${currentMilestone?.minutes}m continuous milestone achieved! $nextMilestoneText",
                            color = SuccessGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Action buttons: Claim Milestone & Resume in YouTube
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasReachedMinimum3Min) {
                        Button(
                            onClick = { viewModel.claimMilestoneReward(context) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                                .testTag("claim_milestone_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Claim +${currentMilestone?.coins ?: 10}c",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.resumeVideoInYouTube(context) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .testTag("resume_youtube_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasReachedMinimum3Min) AmberPrimary else AmberPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Play in YouTube",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
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
