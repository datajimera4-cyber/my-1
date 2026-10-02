package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AdminPostItem
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun AdminPostsBannerSection(
    posts: List<AdminPostItem>,
    dismissedIds: Set<String>,
    targetTab: String = "HOME",
    currentTab: String = targetTab,
    onDismiss: (String) -> Unit = {},
    onDismissPost: (String) -> Unit = onDismiss,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val effectiveTab = if (currentTab.isNotBlank()) currentTab else targetTab
    val visiblePosts = posts.filter { post ->
        !post.postType.equals("PUSH_ONLY", ignoreCase = true) &&
                !post.targetTab.equals("NONE", ignoreCase = true) &&
                (post.targetTab.equals(effectiveTab, ignoreCase = true) || post.targetTab.equals("ALL", ignoreCase = true))
    }

    if (visiblePosts.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        visiblePosts.forEach { post ->
            val isAlert = post.postType.equals("ALERT", ignoreCase = true)
            val borderColor = if (isAlert) AlertRed.copy(alpha = 0.45f) else AmberPrimary.copy(alpha = 0.35f)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_post_banner_${post.id}")
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .then(
                        if (post.actionUrl.isNotBlank()) {
                            Modifier.clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(post.actionUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        } else Modifier
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = if (isAlert) {
                                    listOf(Color(0xFF2B1218), Slate900)
                                } else {
                                    listOf(Slate800, Slate900)
                                }
                            )
                        )
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (post.imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = post.imageUrl,
                                contentDescription = post.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 110.dp, max = 170.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (post.title.isNotBlank()) {
                                Text(
                                    text = post.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                            if (post.message.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = post.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        if (post.actionUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AmberPrimary.copy(alpha = 0.18f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Open Link",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
