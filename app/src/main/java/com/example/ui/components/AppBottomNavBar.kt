package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberDark
import com.example.ui.theme.AmberPrimary
import com.example.viewmodel.AppScreen

data class NavTabItem(
    val screen: AppScreen,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

val NAV_TAB_ITEMS = listOf(
    NavTabItem(
        screen = AppScreen.HOME,
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_tab_home"
    ),
    NavTabItem(
        screen = AppScreen.TASKS,
        title = "Tasks",
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary,
        testTag = "nav_tab_tasks"
    ),
    NavTabItem(
        screen = AppScreen.WALLET,
        title = "Wallet",
        selectedIcon = Icons.Filled.AccountBalanceWallet,
        unselectedIcon = Icons.Outlined.AccountBalanceWallet,
        testTag = "nav_tab_wallet"
    ),
    NavTabItem(
        screen = AppScreen.ME,
        title = "Me",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        testTag = "nav_tab_me"
    )
)

@Composable
fun AppBottomNavBar(
    currentScreen: AppScreen,
    onTabSelected: (AppScreen) -> Unit,
    activeTaskCount: Int = 0,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .border(
                width = 0.8.dp,
                color = Color.White.copy(alpha = 0.08f)
            )
            .testTag("app_bottom_nav_bar"),
        containerColor = Color(0xFF0F172A),
        tonalElevation = 6.dp
    ) {
        NAV_TAB_ITEMS.forEach { item ->
            val isSelected = when (item.screen) {
                AppScreen.HOME -> currentScreen == AppScreen.HOME
                AppScreen.TASKS -> currentScreen == AppScreen.TASKS || currentScreen == AppScreen.TASK_DETAIL
                AppScreen.WALLET -> currentScreen == AppScreen.WALLET
                AppScreen.ME -> currentScreen == AppScreen.ME || currentScreen == AppScreen.SETUP || currentScreen == AppScreen.DIAGNOSTICS
                else -> false
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(item.screen) },
                icon = {
                    if (item.screen == AppScreen.TASKS && activeTaskCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = AmberPrimary,
                                    contentColor = Color.Black
                                ) {
                                    Text(
                                        text = "$activeTaskCount",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.title
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title
                        )
                    }
                },
                label = {
                    Text(
                        text = item.title,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = AmberPrimary,
                    indicatorColor = AmberPrimary,
                    unselectedIconColor = Color.White.copy(alpha = 0.55f),
                    unselectedTextColor = Color.White.copy(alpha = 0.55f)
                ),
                modifier = Modifier.testTag(item.testTag)
            )
        }
    }
}
