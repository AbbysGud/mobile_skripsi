package com.example.stationbottle.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.stationbottle.models.UserViewModel
import com.example.stationbottle.ui.screens.*
import com.example.stationbottle.R


@Composable
fun AppNavigation(onThemeChange: () -> Unit) {
    val navController = rememberNavController()
    val userViewModel: UserViewModel = viewModel()
    val context = LocalContext.current

    val user by userViewModel.getUser(context).collectAsState(initial = null)
    val isLoggedIn = rememberSaveable { mutableStateOf(user != null) }

    LaunchedEffect(user) {
        isLoggedIn.value = user != null
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
            composable("profile") {
                ProfileScreen(
                    navController, onThemeChange = onThemeChange
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.secondary,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val items = listOf(
        BottomNavItem("Home", R.drawable.outline_home_24, "home"),
        BottomNavItem("Stasiun", R.drawable.outline_sensors_24, "station"),
        BottomNavItem("History", R.drawable.outline_history_24, "history"),
        BottomNavItem("Profile", R.drawable.outline_person_24, "profile")
    )

    // Pantau perubahan rute saat ini
    val currentRoute = navController.currentBackStackEntryFlow.collectAsState(initial = null).value?.destination?.route

    NavigationBar(
        containerColor = backgroundColor,
        contentColor = contentColor
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(id = item.icon),
                        contentDescription = item.title
                    )
                },
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
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = contentColor,
                    unselectedTextColor = contentColor,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

data class BottomNavItem(val title: String, val icon: Int, val route: String)

