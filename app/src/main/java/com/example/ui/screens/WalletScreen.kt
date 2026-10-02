package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.COINS_PER_INR
import com.example.data.WalletTransaction
import com.example.ui.components.AdminPostsBannerSection
import com.example.ui.components.KingoLogoBadge
import com.example.ui.components.WithdrawDialog
import com.example.ui.theme.AmberDark
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessGreen
import com.example.util.TimeFormatter
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel
import java.util.Locale

private fun resolveEffectiveCoins(item: WalletTransaction): Int {
    return if (item.coins != 0) {
        item.coins
    } else if (item.title.contains("Payout", ignoreCase = true) || item.title.contains("Withdrawal", ignoreCase = true)) {
        val coinsMatch = Regex("""(\d+)\s*Coins""", RegexOption.IGNORE_CASE).find(item.title)
        val inrMatch = Regex("""₹\s*(\d+(?:\.\d+)?)""").find(item.title)
        when {
            coinsMatch != null -> -(coinsMatch.groupValues[1].toIntOrNull() ?: 1000)
            inrMatch != null -> -(((inrMatch.groupValues[1].toDoubleOrNull() ?: 10.0) * COINS_PER_INR).toInt())
            else -> -1000
        }
    } else {
        0
    }
}

@Composable
fun WalletScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val walletBalance by viewModel.walletBalance.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val adminPosts by viewModel.adminPosts.collectAsState()
    val dismissedPostIds by viewModel.dismissedPostIds.collectAsState()

    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showHistoryPage by remember { mutableStateOf(false) }
    var withdrawalSuccessMessage by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (showHistoryPage) {
            showHistoryPage = false
        } else {
            viewModel.switchTab(AppScreen.HOME)
        }
    }

    val inrEquivalent = walletBalance.toDouble() / COINS_PER_INR.toDouble()
    val formattedInr = String.format(Locale.US, "%.2f", inrEquivalent)

    val totalEarnedCoins = remember(transactions) {
        transactions.map { resolveEffectiveCoins(it) }.filter { it > 0 }.sum()
    }
    val withdrawalCount = remember(transactions) {
        transactions.count { resolveEffectiveCoins(it) < 0 }
    }

    if (showHistoryPage) {
        WalletHistoryPage(
            transactions = transactions,
            onBack = { showHistoryPage = false },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("wallet_screen")
    ) {
        // Professional Anchored Top Header Bar (Edge-to-Edge Status Bar Safe)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        KingoLogoBadge(
                            isAdmin = false,
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "My Wallet & Payouts",
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "1000 Coins = ₹10 INR • Instant UPI Payout",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Top-Right History Button
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = AmberPrimary.copy(alpha = 0.16f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, AmberPrimary.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .clickable { showHistoryPage = true }
                            .testTag("wallet_history_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Open History",
                                tint = AmberDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "History",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberDark
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AdminPostsBannerSection(
                posts = adminPosts,
                currentTab = "WALLET",
                dismissedIds = dismissedPostIds,
                onDismissPost = { viewModel.dismissAdminPost(it) }
            )

            // Balance Hero Card with INR Conversion & Payout Button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallet_hero_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Slate900, Slate800)
                            )
                        )
                        .border(1.dp, AmberPrimary.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "TOTAL EARNINGS BALANCE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = "Coin",
                                tint = AmberPrimary,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$walletBalance",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Coins",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = AmberPrimary
                            )
                        }

                        // INR Real Cash Value Strip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "≈ ₹$formattedInr INR",
                                color = SuccessGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(AmberPrimary, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "1000 Coins = 10 INR",
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Withdraw / Payout Button
                        Button(
                            onClick = { showWithdrawDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("withdraw_action_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Withdraw via UPI",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            // Dedicated Transaction & Payout History Button Card (opens separate full-screen History Page)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showHistoryPage = true }
                    .border(
                        1.2.dp,
                        AmberPrimary.copy(alpha = 0.45f),
                        RoundedCornerShape(18.dp)
                    )
                    .testTag("wallet_open_history_button"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(AmberPrimary.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = AmberDark,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Transaction & Payout History",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${transactions.size} records • View all earnings & UPI withdrawals",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AmberPrimary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "View",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Quick Summary Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(SuccessGreen.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Total Earned",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "+$totalEarnedCoins c",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = SuccessGreen
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(AmberPrimary.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = AmberDark,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "UPI Payouts",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "$withdrawalCount Requests",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Clean UPI Payout Information Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Safe & Fast UPI Withdrawals",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "• Minimum withdrawal is 1000 Coins (₹10 INR).\n• Enter any valid UPI ID (PhonePe, GPay, Paytm) to request payout.\n• Tap the 'History' button anytime to check live payout status.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }

    // Payout Dialog
    if (showWithdrawDialog) {
        WithdrawDialog(
            currentBalance = walletBalance,
            onDismiss = { showWithdrawDialog = false },
            onSubmitWithdrawal = { amount, method, dest ->
                viewModel.withdrawCoins(
                    coins = amount,
                    method = method,
                    destination = dest
                ) { success, msg ->
                    showWithdrawDialog = false
                    if (success) {
                        withdrawalSuccessMessage = msg
                    }
                }
            }
        )
    }

    // Payout Confirmation Dialog
    withdrawalSuccessMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { withdrawalSuccessMessage = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = { Text("Payout Successful!", fontWeight = FontWeight.Bold) },
            text = { Text(msg) },
            confirmButton = {
                Button(
                    onClick = { withdrawalSuccessMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun WalletHistoryPage(
    transactions: List<WalletTransaction>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filterTab by remember { mutableIntStateOf(0) } // 0 = All, 1 = Withdrawals, 2 = Task Earnings

    val filteredTransactions = remember(transactions, filterTab) {
        when (filterTab) {
            1 -> transactions.filter { resolveEffectiveCoins(it) < 0 }
            2 -> transactions.filter { resolveEffectiveCoins(it) >= 0 }
            else -> transactions
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("wallet_history_page")
    ) {
        // Dedicated History Page Top Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("wallet_history_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Wallet"
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Transaction & Payout History",
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${filteredTransactions.size} records shown",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Filter Chips Row (All / Withdrawals / Earnings)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(
                        "All (${transactions.size})",
                        "Withdrawals (${transactions.count { resolveEffectiveCoins(it) < 0 }})",
                        "Earnings (${transactions.count { resolveEffectiveCoins(it) >= 0 }})"
                    )
                    tabs.forEachIndexed { idx, label ->
                        FilterChip(
                            selected = filterTab == idx,
                            onClick = { filterTab = idx },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (filterTab == idx) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberPrimary,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }
        }

        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "No transactions found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Complete video tasks or make a UPI withdrawal to see records here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTransactions, key = { it.id }) { item ->
                    val effectiveCoins = remember(item.coins, item.title) { resolveEffectiveCoins(item) }
                    val isWithdrawal = effectiveCoins < 0
                    val statusBadge = remember(item.title) {
                        when {
                            item.title.contains("DONE", ignoreCase = true) || item.title.contains("Paid to", ignoreCase = true) ->
                                Triple("PAYMENT DONE ✓", SuccessGreen.copy(alpha = 0.16f), SuccessGreen)
                            item.title.contains("APPROVED", ignoreCase = true) ->
                                Triple("APPROVED • PROCESSING", AmberPrimary.copy(alpha = 0.18f), AmberPrimary)
                            item.title.contains("PENDING", ignoreCase = true) ->
                                Triple("PENDING REVIEW", AmberPrimary.copy(alpha = 0.15f), AmberPrimary)
                            item.title.contains("REJECTED", ignoreCase = true) ->
                                Triple("REJECTED • REFUNDED", Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFEF4444))
                            else -> null
                        }
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transaction_item_${item.id}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            if (isWithdrawal) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isWithdrawal) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = if (isWithdrawal) MaterialTheme.colorScheme.error else SuccessGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (statusBadge != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = statusBadge.second
                                        ) {
                                            Text(
                                                text = statusBadge.first,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = statusBadge.third,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = TimeFormatter.formatTimestamp(item.timestampMillis),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isWithdrawal) "$effectiveCoins Coins" else "+$effectiveCoins Coins",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isWithdrawal) MaterialTheme.colorScheme.error else SuccessGreen
                            )
                        }
                    }
                }
            }
        }
    }
}
