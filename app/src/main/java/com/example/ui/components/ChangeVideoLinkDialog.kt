package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberPrimary
import com.example.util.TitleMatcher

@Composable
fun ChangeVideoLinkDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onSaveLink: (String) -> Unit
) {
    val context = LocalContext.current
    var inputUrl by remember {
        mutableStateOf(if (initialUrl == "PASTE_MY_YOUTUBE_LINK_HERE") "" else initialUrl)
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = AmberPrimary
                )
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text("Set YouTube Video Task", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Paste any YouTube video URL. WatchEarn will record its title, channel name, and thumbnail. During task start, it will search YouTube by title to locate and play it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { raw ->
                        val cleaned = TitleMatcher.extractCleanYouTubeUrl(raw)
                        inputUrl = cleaned
                        errorMessage = null
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
                                    inputUrl = cleaned
                                    errorMessage = null
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste")
                        }
                    },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Supports youtube.com/watch, youtu.be, shorts", fontSize = 11.sp)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_url_input_field"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleaned = TitleMatcher.extractCleanYouTubeUrl(inputUrl.trim())
                    if (cleaned.isBlank()) {
                        errorMessage = "Please enter a valid YouTube link."
                    } else {
                        val finalUrl = if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                            cleaned
                        } else {
                            "https://$cleaned"
                        }
                        onSaveLink(finalUrl)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                modifier = Modifier.testTag("save_video_link_button")
            ) {
                Text("Fetch & Record", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
