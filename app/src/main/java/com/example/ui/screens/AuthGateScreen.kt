package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.KingoLogoBadge
import com.example.ui.components.isInternetAvailable
import com.example.ui.theme.AmberDark
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.MainViewModel

/**
 * Mandatory Login / Sign-Up Gate Screen for Kingo King User App.
 * Users must sign in or create an account before accessing tasks & rewards.
 * Automatically syncs the user's account and statistics with the Google Drive Cloud Server.
 */
@Composable
fun AuthGateScreen(
    viewModel: MainViewModel,
    onRequireInternetPopup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cloudServerUrl by viewModel.cloudServerUrl.collectAsState()
    val cloudServerStatus by viewModel.cloudServerStatus.collectAsState()

    var authTabIndex by remember { mutableIntStateOf(0) } // 0 = Sign In, 1 = Sign Up
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var authFeedback by remember { mutableStateOf<String?>(null) }
    var isSuccessFeedback by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    var serverUrlInput by remember(cloudServerUrl) { mutableStateOf(cloudServerUrl) }
    var showServerConfig by remember { mutableStateOf(cloudServerUrl.isBlank()) }
    var isTestingServer by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("auth_gate_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Royal KINGO KING Header & Logo
            KingoLogoBadge(isAdmin = false, size = 76.dp)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "KINGO KING",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sign in or create your account to access live tasks & sync your personal wallet on the cloud server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // Login / Sign Up Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    TabRow(
                        selectedTabIndex = authTabIndex,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = authTabIndex == 0,
                            onClick = {
                                authTabIndex = 0
                                authFeedback = null
                            },
                            text = { Text("Sign In", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = authTabIndex == 1,
                            onClick = {
                                authTabIndex = 1
                                authFeedback = null
                            },
                            text = { Text("Create Account", fontWeight = FontWeight.Bold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (authTabIndex == 1) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_gate_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_gate_email_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password (min 4 chars)") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_gate_password_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    authFeedback?.let { msg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = msg,
                            color = if (isSuccessFeedback) SuccessGreen else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (!isInternetAvailable(context)) {
                                onRequireInternetPopup()
                                return@Button
                            }
                            if (serverUrlInput.isNotBlank() && serverUrlInput.trim() != cloudServerUrl) {
                                viewModel.saveCloudServerUrl(serverUrlInput.trim())
                            }
                            isLoading = true
                            authFeedback = null
                            if (authTabIndex == 0) {
                                viewModel.login(emailInput, passwordInput) { success, msg ->
                                    isLoading = false
                                    isSuccessFeedback = success
                                    authFeedback = msg
                                    if (success) {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                viewModel.signUp(emailInput, passwordInput, nameInput) { success, msg ->
                                    isLoading = false
                                    isSuccessFeedback = success
                                    authFeedback = msg
                                    if (success) {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_gate_submit_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Syncing with Cloud Server...",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = if (authTabIndex == 0) "Sign In to Kingo King" else "Create Account & Continue",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            // Cloud Server Connection Card (for connecting to the Google Drive Server)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AmberPrimary.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showServerConfig = !showServerConfig },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (cloudServerUrl.isNotBlank()) SuccessGreen.copy(alpha = 0.2f)
                                        else AmberPrimary.copy(alpha = 0.2f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (cloudServerUrl.isNotBlank()) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = if (cloudServerUrl.isNotBlank()) SuccessGreen else AmberPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Google Drive Cloud Server",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (cloudServerUrl.isNotBlank()) cloudServerStatus else "Tap to connect your Google Drive Server URL",
                                    color = if (cloudServerUrl.isNotBlank()) SuccessGreen else AmberPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Text(
                            text = if (showServerConfig) "Hide" else "Configure",
                            color = AmberPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    if (showServerConfig || cloudServerUrl.isBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = serverUrlInput,
                            onValueChange = { serverUrlInput = it },
                            label = { Text("Google Apps Script Web App URL (/exec)", color = Color(0xFF94A3B8)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                if (!isInternetAvailable(context)) {
                                    onRequireInternetPopup()
                                    return@OutlinedButton
                                }
                                val clean = serverUrlInput.trim()
                                if (clean.isBlank()) {
                                    Toast.makeText(context, "Please paste your Google Script Web App URL", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                isTestingServer = true
                                viewModel.saveCloudServerUrl(clean)
                                viewModel.syncWithGoogleDriveServer { ok, msg ->
                                    isTestingServer = false
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    if (ok) showServerConfig = false
                                }
                            },
                            enabled = !isTestingServer,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (isTestingServer) "Connecting to Drive..." else "Save & Connect Cloud Server",
                                color = AmberPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
