package com.example.stationbottle.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.stationbottle.models.UserViewModel
import com.example.stationbottle.ui.screens.LoginScreen
import com.example.stationbottle.ui.screens.HomeScreen
import com.example.stationbottle.ui.screens.RegisterScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val userViewModel: UserViewModel = viewModel()

    val isLoggedIn = remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        userViewModel.getUser(context).collect { user ->
            isLoggedIn.value = user != null
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn.value) "home" else "login"
    ) {
        composable("login") {
            LoginScreen(navController)
        }
        composable("home") {
            HomeScreen(navController)
        }
        composable("register") {
            RegisterScreen(navController)
        }
    }
}
