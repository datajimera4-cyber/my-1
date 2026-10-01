package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PayoutStatus
import com.example.ui.components.AdminPostsBannerSection
import com.example.ui.components.ChangeVideoLinkDialog
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberDark
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate800
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val walletBalance by viewModel.walletBalance.collectAsState()
    val videoTasks by viewModel.videoTasks.collectAsState()
    val adminPosts by viewModel.adminPosts.collectAsState()
    val dismissedPostIds by viewModel.dismissedPostIds.collectAsState()
    val activeUrl by viewModel.currentVideoUrl.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val payoutRequests by viewModel.payoutRequests.collectAsState()

    val completedCount = videoTasks.count { it.isCompleted }
    val pendingPayoutsCount = payoutRequests.count { it.status == PayoutStatus.PENDING }

    var showResetDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }

    // Authentication form state
    var authTabIndex by remember { mutableIntStateOf(0) } // 0 = Login, 1 = Sign Up
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthLoading by remember { mutableStateOf(false) }

    val isUserLoggedIn = currentUser != null && currentUser?.email != "guest@watchearn.com"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Profile & Account",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.testTag("me_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AdminPostsBannerSection(
                    posts = adminPosts,
                    currentTab = "ME",
                    dismissedIds = dismissedPostIds,
                    onDismissPost = { viewModel.dismissAdminPost(it) }
                )
            }

            // Profile / Authentication Section
            if (isUserLoggedIn) {
                // Logged In User Profile Card
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth().testTag("me_profile_header_card")
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(AmberPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = currentUser?.name?.ifBlank { "Kingo Member" } ?: "Member",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(AmberPrimary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "VERIFIED",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AmberDark,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = currentUser?.email ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "ID: ${currentUser?.userId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.logout()
                                        Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Log out", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // Email Sign In / Sign Up Card
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth().testTag("me_auth_card")
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(AmberPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (authTabIndex == 0) "Sign In to Kingo King" else "Create Kingo King Account",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Sync wallet, payout requests & tasks to your email",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            TabRow(
                                selectedTabIndex = authTabIndex,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.background(Color.Transparent, RoundedCornerShape(10.dp))
                            ) {
                                Tab(
                                    selected = authTabIndex == 0,
                                    onClick = { authTabIndex = 0; authError = null },
                                    text = { Text("Sign In", fontWeight = FontWeight.Bold) }
                                )
                                Tab(
                                    selected = authTabIndex == 1,
                                    onClick = { authTabIndex = 1; authError = null },
                                    text = { Text("Create Account", fontWeight = FontWeight.Bold) }
                                )
                            }

                            if (authTabIndex == 1) {
                                OutlinedTextField(
                                    value = nameInput,
                                    onValueChange = { nameInput = it; authError = null },
                                    label = { Text("Your Name") },
                                    leadingIcon = { Icon(Icons.Default.Person, null, tint = AmberPrimary) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it; authError = null },
                                label = { Text("Email Address") },
                                leadingIcon = { Icon(Icons.Default.Email, null, tint = AmberPrimary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("auth_email_input")
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it; authError = null },
                                label = { Text("Password") },
                                leadingIcon = { Icon(Icons.Default.Lock, null, tint = AmberPrimary) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("auth_password_input")
                            )

                            authError?.let { err ->
                                Text(
                                    text = err,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = {
                                    if (emailInput.isBlank() || !emailInput.contains("@")) {
                                        authError = "Please enter a valid email."
                                        return@Button
                                    }
                                    if (passwordInput.length < 4) {
                                        authError = "Password must be at least 4 characters."
                                        return@Button
                                    }

                                    isAuthLoading = true
                                    if (authTabIndex == 0) {
                                        viewModel.login(emailInput.trim(), passwordInput) { success, msg ->
                                            isAuthLoading = false
                                            if (success) {
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                authError = null
                                            } else {
                                                authError = msg
                                            }
                                        }
                                    } else {
                                        viewModel.signUp(emailInput.trim(), passwordInput, nameInput.trim()) { success, msg ->
                                            isAuthLoading = false
                                            if (success) {
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                authError = null
                                            } else {
                                                authError = msg
                                            }
                                        }
                                    }
                                },
                                enabled = !isAuthLoading && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_submit_btn")
                            ) {
                                if (isAuthLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                                } else {
                                    Text(
                                        text = if (authTabIndex == 0) "Sign In with Email" else "Create Account & Sign In",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Stats 3-Column Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$walletBalance",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberDark
                            )
                            Text(
                                text = "Coins Balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$completedCount",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = SuccessGreen
                            )
                            Text(
                                text = "Tasks Done",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${videoTasks.size}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Available Tasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Controls & Settings Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Features & Navigation",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Wallet & Payout Row
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateTo(AppScreen.WALLET) }
                            .testTag("me_wallet_row")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(AmberPrimary.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = AmberDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "My Wallet & Payout History",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Withdraw coins to UPI, Paytm or Bank",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Admin Dashboard Row (Only accessible in ADMIN role flavor)
                    if (com.example.BuildConfig.APP_ROLE == "ADMIN") {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Slate800),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.navigateTo(AppScreen.ADMIN) }
                                .testTag("me_admin_dashboard_row")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(AmberPrimary.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AdminPanelSettings,
                                            contentDescription = null,
                                            tint = AmberPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Admin Control Panel",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White
                                            )
                                            if (pendingPayoutsCount > 0) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(AlertRed, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "$pendingPayoutsCount PENDING",
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "Manage tasks, review payouts, adjust coins & web server",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AmberPrimary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Zero-Permission Privacy Row
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateTo(AppScreen.SETUP) }
                            .testTag("me_setup_row")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(SuccessGreen.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Zero-Permission Video Tracking",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "No notification reading required • 100% Privacy Protected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SuccessGreen,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Diagnostics Screen
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateTo(AppScreen.DIAGNOSTICS) }
                            .testTag("me_diagnostics_row")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Analytics,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Live Diagnostics & Telemetry",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Inspect search events & session logs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Paste Custom Link row
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLinkDialog = true }
                            .testTag("me_paste_link_row")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Paste Any Video Link",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Current URL: ${activeUrl.take(28)}...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Reset Section
            item {
                Button(
                    onClick = { showResetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("me_reset_all_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset App State (Clear Wallet & History)")
                }
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Kingo King?") },
            text = {
                Text("This will clear your local wallet balance, completion flag, and transaction history. You can then test earning coins again.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAll(context)
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Change Video Link Dialog
    if (showLinkDialog) {
        ChangeVideoLinkDialog(
            initialUrl = activeUrl,
            onDismiss = { showLinkDialog = false },
            onSaveLink = { newUrl ->
                viewModel.setVideoUrl(newUrl)
            }
        )
    }
}
