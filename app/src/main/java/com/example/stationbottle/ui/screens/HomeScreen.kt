package com.example.stationbottle.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.compose.AppTheme
import com.example.stationbottle.models.UserViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val userViewModel = UserViewModel()
    val userState = userViewModel.getUser(context).collectAsState(initial = null)
    val user = userState.value

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (user != null) {
            Text(text = "Token: ${user.token}")
            Text(text = "Id: ${user.id}")
            Text(text = "Email: ${user.email}")
            Text(text = "Nama: ${user.name}")
        }

        Button(
            onClick = {
                user?.token?.let { token ->
                    userViewModel.logoutUser(context, token)
                    navController.navigate("login")
                }
            },
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Text(text = "Logout")
        }

    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    AppTheme {
        val navController = rememberNavController()
        HomeScreen(navController)
    }
}