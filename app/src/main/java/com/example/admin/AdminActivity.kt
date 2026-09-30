package com.example.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.theme.WatchEarnTheme
import com.example.viewmodel.MainViewModel

/**
 * Separate Standalone Admin Application Activity.
 * Runs as an independent app icon in the Android Launcher ("Kingo Admin"),
 * keeping the user-facing app clean and free from admin controls.
 */
class AdminActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WatchEarnTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: MainViewModel = viewModel()
                    AdminDashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}
