package com.example.stationbottle.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.stationbottle.R
import com.example.stationbottle.data.ResetPasswordRequest
import com.example.stationbottle.data.RetrofitClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

@Composable
fun ResetPasswordScreen(navController: NavController, email: String) {
    var token by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var dialogMessage by remember { mutableStateOf("") }
    val openDialogState = remember { mutableStateOf(false) }
    var tokenError by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Masukkan token dan password baru:", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token") },
            modifier = Modifier.fillMaxWidth(0.8f),
            isError = tokenError
        )

        if (tokenError) {
            Text(
                text = "Token wajib diisi.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("Password Baru") },
            visualTransformation =  if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        painter = painterResource(
                            if (isPasswordVisible) R.drawable.ic_eye_show else R.drawable.ic_eye_off
                        ),
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f),
            isError = passwordError
        )

        if (passwordError) {
            Text(
                text = "Password wajib diisi.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Konfirmasi Password") },
            visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                    Icon(
                        painter = painterResource(
                            if (isConfirmPasswordVisible) R.drawable.ic_eye_show else R.drawable.ic_eye_off
                        ),
                        contentDescription = if (isConfirmPasswordVisible) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f),
            isError = confirmPasswordError
        )

        if (confirmPasswordError) {
            Text(
                text = "Konfirmasi Password Wajib Diisi.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                    tokenError = token.isEmpty()
                    passwordError = newPassword.isEmpty()
                    confirmPasswordError = confirmPassword.isEmpty()

                    dialogMessage = "Semua field harus diisi."
                    openDialogState.value = true
                    return@Button
                }
                if (newPassword == confirmPassword) {
                    scope.launch {
                        try {
                            val resetPasswordRequest = ResetPasswordRequest(email, token, newPassword, confirmPassword)
                            val response = RetrofitClient.apiService.resetPassword(resetPasswordRequest)
                            if (response.isSuccessful) {
                                dialogMessage = "Password berhasil diubah. Silakan login kembali."
                                Toast.makeText(context, "Password berhasil diubah. Silakan login kembali.", Toast.LENGTH_SHORT).show()
                                navController.navigate("login")
                            } else {
                                val gson = Gson()
                                val errorBody = response.errorBody()?.string()
                                dialogMessage = if (!errorBody.isNullOrEmpty()) {
                                    try {
                                        val type = object : TypeToken<Map<String, Any>>() {}.type
                                        val errorMap: Map<String, Any>? = gson.fromJson<Map<String, Any>>(errorBody, type)
                                        errorMap?.get("message")?.toString() ?: "Error Tidak Terduga."
                                    } catch (e: Exception) {
                                        Log.e("Login", "Failed to parse error body: ${e.message}")
                                        "Error Tidak Terduga: Gagal dalam Parsing Data."
                                    }
                                } else {
                                    when (response.code()) {
                                        400 -> "Ada Kesalahan dengan Kode OTP atau Email."
                                        404 -> "User Tidak Ditemukan."
                                        422 -> "Validasi Data Gagal."
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
                    dialogMessage = "Password dan konfirmasi password tidak cocok."
                    openDialogState.value = true
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("Ubah Password")
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
