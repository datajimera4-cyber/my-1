package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.PayoutRequest
import com.example.data.PayoutStatus
import com.example.data.UserProfile
import com.example.data.VideoTaskItem
import com.example.ui.components.AddVideoTaskDialog
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberDark
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate800
import com.example.ui.theme.SuccessGreen
import com.example.util.TimeFormatter
import com.example.viewmodel.MainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { viewModel.navigateBack() }

    val videoTasks by viewModel.videoTasks.collectAsState()
    val payoutRequests by viewModel.payoutRequests.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val serverRunning by viewModel.adminServerRunning.collectAsState()
    val serverUrl by viewModel.adminServerUrl.collectAsState()
    val walletBalance by viewModel.walletBalance.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showAddTaskDialog by remember { mutableStateOf(false) }

    // Dialogs for Admin actions
    var adjustCoinsUser by remember { mutableStateOf<UserProfile?>(null) }
    var adjustCoinsInput by remember { mutableStateOf("") }

    val pendingPayoutsCount = payoutRequests.count { it.status == PayoutStatus.PENDING }

    LaunchedEffect(Unit) {
        if (!serverRunning) {
            viewModel.toggleAdminServer(context, true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(AmberPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Admin Control Panel",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "Realtime Management & Web Access",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberPrimary,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("admin_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddTaskDialog = true },
                        modifier = Modifier.testTag("admin_add_task_topbar_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Task", tint = AmberPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.testTag("admin_dashboard_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Quick Stat Header Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminStatCard(
                    title = "Pending Payouts",
                    value = "$pendingPayoutsCount",
                    color = if (pendingPayoutsCount > 0) AlertRed else SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    title = "Active Tasks",
                    value = "${videoTasks.size}",
                    color = AmberPrimary,
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    title = "Users",
                    value = "${maxOf(1, allUsers.size)}",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Tasks (${videoTasks.size})", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Payouts", fontWeight = FontWeight.Bold)
                            if (pendingPayoutsCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(AlertRed, CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$pendingPayoutsCount",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Users", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    text = { Text("PC / Laptop", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Laptop, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> TasksTabContent(
                    tasks = videoTasks,
                    onAddTask = { showAddTaskDialog = true },
                    onDeleteTask = { viewModel.adminDeleteTask(it) }
                )
                1 -> PayoutsTabContent(
                    requests = payoutRequests,
                    onApprove = { id, note -> viewModel.approvePayout(id, note) },
                    onReject = { id, reason -> viewModel.rejectPayout(id, reason) }
                )
                2 -> UsersTabContent(
                    users = allUsers,
                    currentBalance = walletBalance,
                    onAdjustCoins = { user ->
                        adjustCoinsUser = user
                        adjustCoinsInput = user.coinsBalance.toString()
                    }
                )
                3 -> LaptopAccessTabContent(
                    context = context,
                    serverRunning = serverRunning,
                    serverUrl = serverUrl,
                    onToggleServer = { enabled -> viewModel.toggleAdminServer(context, enabled) }
                )
            }
        }
    }

    // Add Video Task Dialog
    if (showAddTaskDialog) {
        AddVideoTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onTaskAdded = { newTask ->
                viewModel.addVideoTask(newTask)
                showAddTaskDialog = false
            }
        )
    }

    // Adjust Coins Dialog
    if (adjustCoinsUser != null) {
        val user = adjustCoinsUser!!
        AlertDialog(
            onDismissRequest = { adjustCoinsUser = null },
            title = { Text("Adjust Coins for ${user.email}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter new total coin balance for this user account:")
                    OutlinedTextField(
                        value = adjustCoinsInput,
                        onValueChange = { adjustCoinsInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Coins Balance") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.MonetizationOn, null, tint = AmberPrimary) }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newCoins = adjustCoinsInput.toIntOrNull() ?: 0
                        viewModel.adminUpdateUserCoins(user.email, newCoins)
                        adjustCoinsUser = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Text("Save Coins", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { adjustCoinsUser = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdminStatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
private fun TasksTabContent(
    tasks: List<VideoTaskItem>,
    onAddTask: () -> Unit,
    onDeleteTask: (String) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = onAddTask,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_add_task_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("+ Add New YouTube Task in Realtime", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }

        if (tasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No video tasks configured yet. Tap above to add one.")
                    }
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().testTag("admin_task_item_${task.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp, 44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.thumbnailUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(task.thumbnailUrl).crossfade(true).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.Gray)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${task.channelName} • ${task.selectedDurationSeconds / 60} min",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "+${task.rewardCoins} coins",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberPrimary
                            )
                        }

                        IconButton(
                            onClick = { onDeleteTask(task.id) },
                            modifier = Modifier.testTag("admin_delete_task_${task.id}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PayoutsTabContent(
    requests: List<PayoutRequest>,
    onApprove: (String, String) -> Unit,
    onReject: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (requests.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(modifier = Modifier.padding(30.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No payout requests yet.", fontWeight = FontWeight.Bold)
                            Text("When users request cash withdrawal, it appears here instantly.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            items(requests, key = { it.id }) { req ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().testTag("admin_payout_item_${req.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = req.userEmail,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        when (req.status) {
                                            PayoutStatus.PENDING -> AmberPrimary.copy(alpha = 0.2f)
                                            PayoutStatus.APPROVED -> SuccessGreen.copy(alpha = 0.2f)
                                            PayoutStatus.REJECTED -> AlertRed.copy(alpha = 0.2f)
                                        },
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = req.status.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when (req.status) {
                                        PayoutStatus.PENDING -> AmberDark
                                        PayoutStatus.APPROVED -> SuccessGreen
                                        PayoutStatus.REJECTED -> AlertRed
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${req.amountCoins} Coins → ₹${String.format(Locale.US, "%.2f", req.amountInr)} INR",
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                            Text(
                                text = "${req.method}: ${req.destination}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!req.adminNote.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Note: ${req.adminNote}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (req.status == PayoutStatus.PENDING) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onApprove(req.id, "Approved & Dispatched via ${req.method}") },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Approve & Pay", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { onReject(req.id, "Incorrect payment destination") },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = AlertRed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reject & Refund", color = AlertRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersTabContent(
    users: List<UserProfile>,
    currentBalance: Int,
    onAdjustCoins: (UserProfile) -> Unit
) {
    val effectiveUsers = if (users.isEmpty()) {
        listOf(UserProfile("user_app", "guest@watchearn.com", "VIP Watcher", coinsBalance = currentBalance))
    } else users

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(effectiveUsers) { user ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("admin_user_${user.email}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(AmberPrimary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, tint = AmberDark, modifier = Modifier.size(22.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = user.email, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = "ID: ${user.userId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${user.coinsBalance} Coins (≈ ₹${String.format(Locale.US, "%.2f", user.coinsBalance / 10.0)})",
                            fontWeight = FontWeight.Bold,
                            color = AmberPrimary,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { onAdjustCoins(user) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Edit Coins", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LaptopAccessTabContent(
    context: Context,
    serverRunning: Boolean,
    serverUrl: String,
    onToggleServer: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Remote PC / Laptop Web Dashboard", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Control Kingo King from any laptop browser", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Switch(
                        checked = serverRunning,
                        onCheckedChange = { onToggleServer(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = SuccessGreen)
                    )
                }

                if (serverRunning && serverUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SuccessGreen.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .border(1.dp, SuccessGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text("Open this URL on your laptop/computer:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = serverUrl,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )

                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Admin URL", serverUrl))
                                        Toast.makeText(context, "URL copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", tint = SuccessGreen)
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Turn ON the switch above to start the embedded web server. Your phone and laptop should be on the same Wi-Fi or hotspot network.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What can you do from your Laptop?", fontWeight = FontWeight.Bold)
                Text("• 🎬 Add and delete video tasks with instant real-time sync into user's app.", fontSize = 13.sp)
                Text("• 💸 View live incoming payout requests and approve / pay with 1 click.", fontSize = 13.sp)
                Text("• 👥 View all registered user accounts and modify coin balances.", fontSize = 13.sp)
                Text("• ⏱️ Realtime WebSocket/Polling automatically keeps phone and laptop in sync.", fontSize = 13.sp)
            }
        }
    }
}
