package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SupportMessage
import com.example.data.UserProfile
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessGreen
import com.example.util.TimeFormatter

@Composable
fun SupportChatDialog(
    currentUser: UserProfile?,
    allMessages: List<SupportMessage>,
    onSendMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val activeEmail = currentUser?.email?.lowercase() ?: "guest@watchearn.com"
    val activeUserId = currentUser?.userId ?: "usr_${Math.abs(activeEmail.hashCode()) % 100000}"
    val activeUserName = currentUser?.name?.ifBlank { activeEmail.substringBefore("@") } ?: "Guest User"

    val userMessages = remember(allMessages, activeEmail, activeUserId) {
        allMessages.filter {
            it.userEmail.equals(activeEmail, ignoreCase = true) ||
                    (it.userId.isNotBlank() && it.userId == activeUserId)
        }.sortedBy { it.timestampMillis }
    }

    SupportChatDialog(
        title = "Help & Chat Support",
        subtitle = "$activeUserName • ID: $activeUserId",
        messages = userMessages,
        isAdminViewer = false,
        onSendMessage = onSendMessage,
        onDismiss = onDismiss
    )
}

@Composable
fun SupportChatDialog(
    title: String,
    subtitle: String,
    messages: List<SupportMessage>,
    isAdminViewer: Boolean = false,
    onSendMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sortedMessages = remember(messages) {
        messages.sortedBy { it.timestampMillis }
    }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(sortedMessages.size) {
        if (sortedMessages.isNotEmpty()) {
            listState.animateScrollToItem(sortedMessages.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.82f)
                .testTag("user_support_chat_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate800)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(AmberPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Support",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                // Messages Area
                if (sortedMessages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = null,
                                tint = AmberPrimary.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (isAdminViewer) "Start conversation with User" else "How can we help you today?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isAdminViewer) {
                                    "Send a direct reply below. The user will receive an instant notification."
                                } else {
                                    "Send your query below. Your message goes directly to the Admin team under your User ID."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f),
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sortedMessages, key = { it.id }) { msg ->
                            val isFromMe = if (isAdminViewer) {
                                msg.senderRole.equals("ADMIN", ignoreCase = true)
                            } else {
                                msg.senderRole.equals("USER", ignoreCase = true)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
                            ) {
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = 270.dp)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (isFromMe) 16.dp else 4.dp,
                                                bottomEnd = if (isFromMe) 4.dp else 16.dp
                                            )
                                        )
                                        .background(
                                            if (isFromMe) AmberPrimary else Slate800
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    val senderLabel = when {
                                        isFromMe -> "You"
                                        isAdminViewer -> "${msg.userName.ifBlank { "User" }} (${msg.userId})"
                                        else -> "Admin Support"
                                    }
                                    Text(
                                        text = senderLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isFromMe) Color.Black.copy(alpha = 0.7f) else SuccessGreen
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = msg.message,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isFromMe) Color.Black else Color.White,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = TimeFormatter.formatTimestamp(msg.timestampMillis),
                                        fontSize = 9.sp,
                                        color = if (isFromMe) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
                    }
                }

                // Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate800)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                if (isAdminViewer) "Type reply to user..." else "Write your query or message...",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("support_chat_input"),
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )

                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText.trim())
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("support_chat_send_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = AmberPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message"
                        )
                    }
                }
            }
        }
    }
}
