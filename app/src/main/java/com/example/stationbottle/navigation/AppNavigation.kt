package com.example.stationbottle.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.stationbottle.models.UserViewModel
import com.example.stationbottle.ui.screens.*

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val isLoggedIn = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val userViewModel: UserViewModel = viewModel()

    LaunchedEffect(Unit) {
        userViewModel.getUser(context).collect { user ->
            isLoggedIn.value = user != null
        }
    }

    Scaffold(
        bottomBar = {
            if (isLoggedIn.value) {
                BottomNavigationBar(navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isLoggedIn.value) "home" else "login",
            modifier = Modifier.padding(innerPadding) // Memberikan padding untuk konten
        ) {
            // Rute untuk otentikasi
            composable("login") { LoginScreen(navController) }
            composable("register") { RegisterScreen(navController) }
            composable("forgot_password") { ForgotPasswordScreen(navController) }
            composable("reset_password/{email}") { backStackEntry ->
                val email = backStackEntry.arguments?.getString("email") ?: ""
                ResetPasswordScreen(navController, email)
            }

            // Rute untuk layar utama
            composable("home") { HomeScreen(navController) }
            composable("station") { StationScreen(navController) }
            composable("history") { HistoryScreen(navController) }
            composable("profile") { ProfileScreen(navController) }
        }
    }
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    selectedContentColor: Color = MaterialTheme.colorScheme.secondary
) {
    val items = listOf(
        BottomNavItem("Home", Icons.Default.Home, "home"),
        BottomNavItem("Stasiun", Icons.Default.Build, "station"),
        BottomNavItem("History", Icons.Default.Info, "history"),
        BottomNavItem("Profile", Icons.Default.Person, "profile"),
    )

    // Pantau perubahan rute saat ini
    val currentRoute = navController.currentBackStackEntryFlow.collectAsState(initial = null).value?.destination?.route

    NavigationBar(
        containerColor = backgroundColor,
        contentColor = contentColor
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = selectedContentColor,
                    selectedTextColor = MaterialTheme.colorScheme.onSecondary,
                    unselectedIconColor = contentColor.copy(alpha = 0.7f),
                    unselectedTextColor = contentColor.copy(alpha = 0.7f)
                )
            )
        }
    }
}

data class BottomNavItem(val title: String, val icon: ImageVector, val route: String)

