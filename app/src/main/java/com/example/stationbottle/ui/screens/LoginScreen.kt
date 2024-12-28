package com.example.stationbottle.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.compose.AppTheme
import com.example.stationbottle.R
import com.example.stationbottle.data.ForgotPasswordRequest
import com.example.stationbottle.data.LoginRequest
import com.example.stationbottle.data.RetrofitClient
import com.example.stationbottle.models.UserViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val openDialogState = remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }
    var isForgotPasswordDialogOpen by remember { mutableStateOf(false) }
    var emailForgot by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userViewModel = UserViewModel()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp, vertical = 64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.login_image),
            contentDescription = "login image",
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Selamat Datang",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Login ke akun anda",
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(text = "Email") },
            modifier = Modifier.fillMaxWidth(0.8f),
            isError = emailError,
        )

        if (emailError) {
            Text(
                text = "Email wajib diisi.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(text = "Password") },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
            isError = passwordError,
        )

        if (passwordError) {
            Text(
                text = "Password wajib diisi.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (email.isEmpty() || password.isEmpty()) {
                    emailError = email.isEmpty()
                    passwordError = password.isEmpty()

                    dialogMessage = "Email dan Password wajib diisi."
                    openDialogState.value = true
                    return@Button
                }

                val loginRequest = LoginRequest(email, password)
                userViewModel.loginUser(
                    loginRequest = loginRequest,
                    context = context,
                    navController = navController,
                    userViewModel = userViewModel,
                    scope = scope,
                    onDialogMessageChange = { message -> dialogMessage = message },
                    onOpenDialogChange = { state -> openDialogState.value = state }
                )
            },
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(text = "Login")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Lupa Password?",
            modifier = Modifier.clickable {
                isForgotPasswordDialogOpen = true
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = buildAnnotatedString {
                append("Belum Punya Akun? ")
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append("Registrasi")
                }
            },
            modifier = Modifier.clickable {
                navController.navigate("register")
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        if (isForgotPasswordDialogOpen) {
            AlertDialog(
                onDismissRequest = { isForgotPasswordDialogOpen = false },
                title = { Text("Lupa Password") },
                text = {
                    Column {
                        Text("Masukkan email untuk reset password:")
                        OutlinedTextField(
                            value = emailForgot,
                            onValueChange = { emailForgot = it },
                            label = { Text("Email") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                    if (emailForgot.isNotEmpty()) {
                        val forgotPasswordRequest = ForgotPasswordRequest(emailForgot)
                        scope.launch {
                            try {
                                val response = RetrofitClient.apiService.forgotPassword(forgotPasswordRequest)
                                if (response.isSuccessful) {
                                    dialogMessage = "Link reset password telah dikirim ke email Anda."
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
                                            "Error Tidak Terduga.: Gagal dalam Parsing Data."
                                        }
                                    } else {
                                        when (response.code()) {
                                            400 -> "Invalid email or password. Please try again."
                                            401 -> "Unauthorized: Incorrect email or password."
                                            500 -> "Server error. Please try again later."
                                            else -> "Unexpected error occurred: ${response.message()}."
                                        }
                                    }
                                    dialogMessage = "Terjadi kesalahan saat mengirim email reset password."
                                }
                                openDialogState.value = true
                            } catch (e: Exception) {
                                dialogMessage = "Terjadi kesalahan: ${e.message}."
                                openDialogState.value = true
                            }
                        }
                    } else {
                        dialogMessage = "Email wajib diisi."
                        openDialogState.value = true
                    }
                    isForgotPasswordDialogOpen = false
                    }) {
                        Text("Kirim")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isForgotPasswordDialogOpen = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Error Dialog
        if (openDialogState.value) {
            AlertDialog(
                onDismissRequest = { openDialogState.value = false },
                title = {
                    Text(text = "Peringatan!")
                },
                text = {
                    Text(text = dialogMessage)
                },
                confirmButton = {
                    TextButton(
                        onClick = { openDialogState.value = false }
                    ) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    AppTheme {
        val navController = rememberNavController()
        LoginScreen(navController)
    }
}