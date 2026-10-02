package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SearchProgressState
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate900

@Composable
fun SearchLoadingOverlay(
    searchState: SearchProgressState,
    @Suppress("UNUSED_PARAMETER") targetTitle: String,
    @Suppress("UNUSED_PARAMETER") targetChannel: String,
    @Suppress("UNUSED_PARAMETER") thumbnailUrl: String?
) {
    if (!searchState.isSearching) return

    Dialog(
        onDismissRequest = { /* Modal during opening */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Slate900,
            tonalElevation = 12.dp,
            modifier = Modifier
                .widthIn(min = 180.dp, max = 240.dp)
                .padding(8.dp)
                .testTag("human_search_simulation_dialog")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = AmberPrimary,
                    strokeWidth = 3.5.dp,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Opening...",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
