package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberDark
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate900

/**
 * Custom Royal Crown Logo Badge for "KINGO KING" (User) and "KINGO ADMIN" (Admin).
 */
@Composable
fun KingoLogoBadge(
    isAdmin: Boolean = false,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.linearGradient(
                    colors = if (isAdmin) {
                        listOf(Color(0xFF0F172A), Color(0xFF3B0764))
                    } else {
                        listOf(Color(0xFF0F172A), Color(0xFF1E1B4B))
                    }
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFBBF24), Color(0xFFD97706))
                ),
                shape = RoundedCornerShape(size * 0.28f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.68f)) {
            val w = this.size.width
            val h = this.size.height

            // Draw Royal 3-Peak Crown
            val crownPath = Path().apply {
                moveTo(w * 0.14f, h * 0.62f)
                lineTo(w * 0.08f, h * 0.26f)
                lineTo(w * 0.34f, h * 0.46f)
                lineTo(w * 0.50f, h * 0.16f)
                lineTo(w * 0.66f, h * 0.46f)
                lineTo(w * 0.92f, h * 0.26f)
                lineTo(w * 0.86f, h * 0.62f)
                close()
            }
            drawPath(
                path = crownPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFDE047), Color(0xFFF59E0B))
                )
            )

            // Crown Jewels at Peaks
            drawCircle(
                color = Color(0xFFFEF08A),
                radius = w * 0.055f,
                center = Offset(w * 0.50f, h * 0.12f)
            )
            drawCircle(
                color = Color(0xFFFEF08A),
                radius = w * 0.045f,
                center = Offset(w * 0.08f, h * 0.21f)
            )
            drawCircle(
                color = Color(0xFFFEF08A),
                radius = w * 0.045f,
                center = Offset(w * 0.92f, h * 0.21f)
            )

            // Crown Base Bar
            drawRoundRect(
                color = Color(0xFFD97706),
                topLeft = Offset(w * 0.14f, h * 0.67f),
                size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.14f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f, w * 0.04f)
            )

            // Accent Indicator Bar at Bottom
            drawRoundRect(
                color = if (isAdmin) Color(0xFF10B981) else Color(0xFFFBBF24),
                topLeft = Offset(w * 0.26f, h * 0.86f),
                size = androidx.compose.ui.geometry.Size(w * 0.48f, h * 0.08f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f, w * 0.04f)
            )
        }
    }
}

/**
 * Checks if active internet connection (Mobile Data or Wi-Fi) is available.
 */
fun isInternetAvailable(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    } catch (_: Exception) {
        false
    }
}

/**
 * Popup Dialog shown when Mobile Data / Wi-Fi is turned off.
 */
@Composable
fun NoInternetDialog(
    onRetry: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { /* Mandatory until internet is restored or retried */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, AlertRed.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(AlertRed.copy(alpha = 0.15f), CircleShape)
                        .border(1.5.dp, AlertRed.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SignalWifiOff,
                        contentDescription = "No Internet",
                        tint = AlertRed,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Turn On Your Mobile Data",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your internet connection is currently off. Please turn on your Mobile Data or Wi-Fi to load live tasks and sync your account with the server.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Text(
                        text = "Turn On Data / Wi-Fi Settings",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "I Turned On Data • Retry Now",
                            color = AmberPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
