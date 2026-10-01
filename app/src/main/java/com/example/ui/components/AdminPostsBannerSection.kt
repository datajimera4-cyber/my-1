package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AdminPostItem
import com.example.ui.theme.AlertRed as ErrorRed
import com.example.ui.theme.AmberPrimary as GoldAmber
import com.example.ui.theme.Slate800 as NavyCard
import com.example.ui.theme.SuccessGreen as SuccessEmerald

private val TextSecondary = Color(0xFF94A3B8)

@Composable
fun AdminPostsBannerSection(
    posts: List<AdminPostItem>,
    currentTab: String,
    dismissedIds: Set<String>,
    onDismissPost: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val relevantPosts = posts.filter { post ->
        !dismissedIds.contains(post.id) &&
                (post.targetTab.equals("ALL", ignoreCase = true) ||
                        post.targetTab.equals(currentTab, ignoreCase = true))
    }

    if (relevantPosts.isEmpty()) return

    // Show immediate Alert Dialog popup for the newest unread ALERT post on this tab
    val topAlertPost = relevantPosts.firstOrNull { it.postType.equals("ALERT", ignoreCase = true) }
    var shownAlertDialogForId by rememberSaveable { mutableStateOf("") }

    if (topAlertPost != null && shownAlertDialogForId != topAlertPost.id) {
        AlertDialog(
            onDismissRequest = {
                shownAlertDialogForId = topAlertPost.id
            },
            containerColor = NavyCard,
            icon = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(ErrorRed.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Admin Alert",
                        tint = GoldAmber,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = topAlertPost.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (topAlertPost.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = topAlertPost.imageUrl,
                            contentDescription = topAlertPost.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Text(
                        text = topAlertPost.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (topAlertPost.actionUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                shownAlertDialogForId = topAlertPost.id
                                try {
                                    val url = if (topAlertPost.actionUrl.startsWith("http")) {
                                        topAlertPost.actionUrl
                                    } else {
                                        "https://${topAlertPost.actionUrl}"
                                    }
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldAmber,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Link", fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(
                        onClick = {
                            shownAlertDialogForId = topAlertPost.id
                        }
                    ) {
                        Text("OK, Got It", color = SuccessEmerald, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        relevantPosts.forEach { post ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val isAlert = post.postType.equals("ALERT", ignoreCase = true)
                val isBanner = post.postType.equals("BANNER", ignoreCase = true)

                val accentColor = when {
                    isAlert -> ErrorRed
                    isBanner -> GoldAmber
                    else -> SuccessEmerald
                }

                val gradientColors = when {
                    isAlert -> listOf(Color(0xFF3B1018), Color(0xFF1E1B2E))
                    isBanner -> listOf(Color(0xFF2E230B), Color(0xFF151E32))
                    else -> listOf(Color(0xFF0D2E26), Color(0xFF151E32))
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.2.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable(enabled = post.actionUrl.isNotBlank()) {
                            try {
                                val url = if (post.actionUrl.startsWith("http")) {
                                    post.actionUrl
                                } else {
                                    "https://${post.actionUrl}"
                                }
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (_: Exception) {}
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(gradientColors))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(accentColor.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isAlert -> Icons.Default.NotificationsActive
                                            isBanner -> Icons.Default.Campaign
                                            else -> Icons.Default.Verified
                                        },
                                        contentDescription = post.postType,
                                        tint = accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = accentColor.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = when {
                                                    isAlert -> "🔔 ADMIN ALERT"
                                                    isBanner -> "📢 BANNER"
                                                    else -> "📌 ADMIN POST"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = accentColor,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (!post.targetTab.equals("ALL", ignoreCase = true)) {
                                            Text(
                                                text = "• ${post.targetTab.uppercase()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = post.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDismissPost(post.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (post.imageUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            AsyncImage(
                                model = post.imageUrl,
                                contentDescription = post.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }

                        if (post.message.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = post.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.88f),
                                lineHeight = 18.sp
                            )
                        }

                        if (post.actionUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = accentColor.copy(alpha = 0.22f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Open Now",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
