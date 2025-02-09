package com.example.stationbottle.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.stationbottle.client.RetrofitClient.apiService
import com.example.stationbottle.data.ForgotPasswordRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

@Composable
fun ForgotPasswordScreen(navController: NavController) {
    var emailForgot by remember { mutableStateOf("") }
    var dialogMessage by remember { mutableStateOf("") }
    val openDialogState = remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Masukkan email untuk reset password:", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = emailForgot,
            onValueChange = { emailForgot = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(0.8f),
            isError = emailError
        )

        if (emailError) {
            Text(
                text = "Email wajib diisi.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (emailForgot.isNotEmpty()) {
                    scope.launch {
                        try {
                            val forgotPasswordRequest = ForgotPasswordRequest(emailForgot)
                            val response = apiService.forgotPassword(forgotPasswordRequest)
                            if (response.isSuccessful) {
                                dialogMessage = "Token reset telah dikirim ke email Anda."
                                Toast.makeText(context, "Token reset telah dikirim ke email Anda.", Toast.LENGTH_SHORT).show()
                                navController.navigate("reset_password/$emailForgot")
                            } else {
                                val gson = Gson()
                                val errorBody = response.errorBody()?.string()
                                dialogMessage = if (!errorBody.isNullOrEmpty()) {
                                    try {
                                        val type = object : TypeToken<Map<String, Any>>() {}.type
                                        val errorMap: Map<String, Any>? = gson.fromJson<Map<String, Any>>(errorBody, type)
                                        errorMap?.get("error")?.toString() ?: "Error Tidak Terduga."
                                    } catch (e: Exception) {
                                        Log.e("ForgotPassword", "Failed to parse error body: ${e.message}")
                                        "Error Tidak Terduga: Gagal dalam Parsing Data."
                                    }
                                } else {
                                    when (response.code()) {
                                        400 -> "Email Tidak Ditemukan."
                                        500 -> "Server error. Coba Lagi Nanti."
                                        else -> "Error Tidak Terduga: ${response.message()}."
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            dialogMessage = "Terjadi kesalahan: ${e.message}"
                        }
                        openDialogState.value = true
                    }
                } else {
                    emailError = emailForgot.isEmpty()

                    dialogMessage = "Email wajib diisi."
                    openDialogState.value = true
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Kirim Token")
        }

        if (openDialogState.value) {
            AlertDialog(
                onDismissRequest = { openDialogState.value = false },
                title = { Text("Peringatan!") },
                text = { Text(dialogMessage) },
                confirmButton = {
                    TextButton(onClick = { openDialogState.value = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}