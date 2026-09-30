package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.SampleTask
import com.example.service.NotificationChannels
import com.example.ui.components.AppBottomNavBar
import com.example.ui.components.SuccessDialog
import com.example.ui.components.TaskIncompleteDialog
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MeScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.TaskScreen
import com.example.ui.screens.TasksListScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.WatchEarnTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationChannels.createChannels(this)
        com.example.admin.AdminWebServer.startServer(this, com.example.data.DataStoreManager(this)) { _, _ -> }

        setContent {
            WatchEarnTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WatchEarnApp()
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        com.example.repository.WatchSessionRepository.setAppInForeground(true)
    }

    override fun onPause() {
        super.onPause()
        com.example.repository.WatchSessionRepository.setAppInForeground(false)
    }
}

@Composable
fun WatchEarnApp(viewModel: MainViewModel = viewModel()) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val showSuccessDialog by viewModel.showSuccessDialog.collectAsState()
    val activeRewardCoins by viewModel.activeRewardCoins.collectAsState()
    val videoTasks by viewModel.videoTasks.collectAsState()
    val taskIncompleteMessage by viewModel.taskIncompleteMessage.collectAsState()

    val isPrimaryTab = currentScreen == AppScreen.TASKS ||
            currentScreen == AppScreen.HOME ||
            currentScreen == AppScreen.WALLET ||
            currentScreen == AppScreen.ME

    // Handle back button for secondary screens
    if (!isPrimaryTab) {
        BackHandler {
            viewModel.navigateBack()
        }
    }

    val activeTaskCount = videoTasks.count { !it.isCompleted }

    Scaffold(
        bottomBar = {
            if (isPrimaryTab) {
                AppBottomNavBar(
                    currentScreen = currentScreen,
                    onTabSelected = { selectedScreen ->
                        viewModel.switchTab(selectedScreen)
                    },
                    activeTaskCount = activeTaskCount
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                AppScreen.HOME -> HomeScreen(viewModel = viewModel)
                AppScreen.TASKS -> TasksListScreen(viewModel = viewModel)
                AppScreen.WALLET -> WalletScreen(viewModel = viewModel)
                AppScreen.ME -> MeScreen(viewModel = viewModel)
                AppScreen.TASK, AppScreen.TASK_DETAIL -> TaskScreen(viewModel = viewModel)
                AppScreen.SETUP -> SetupScreen(viewModel = viewModel)
                AppScreen.DIAGNOSTICS -> DiagnosticsScreen(viewModel = viewModel)
                AppScreen.ADMIN -> com.example.ui.screens.AdminDashboardScreen(viewModel = viewModel)
            }
        }
    }

    if (showSuccessDialog) {
        SuccessDialog(
            rewardCoins = activeRewardCoins,
            onDismiss = { viewModel.dismissSuccessDialog() }
        )
    }

    taskIncompleteMessage?.let { msg ->
        TaskIncompleteDialog(
            message = msg,
            onDismiss = { viewModel.dismissTaskIncompleteMessage() }
        )
    }
}
