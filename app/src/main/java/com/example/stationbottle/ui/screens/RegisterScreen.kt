package com.example.stationbottle.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.stationbottle.ui.theme.AppTheme
import com.example.stationbottle.R
import com.example.stationbottle.data.RegisterRequest
import com.example.stationbottle.models.UserViewModel

@Composable
fun RegisterScreen(navController: NavController) {
    var isLoading by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var confirmPasswordError by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }
    val openDialogState = remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userViewModel = UserViewModel()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
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
                text = "Daftar Akun",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Buat akun untuk melanjutkan",
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

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(text = "Konfirmasi Password") },
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
                isError = confirmPasswordError,
            )

            if (confirmPasswordError) {
                Text(
                    text = "Konfirmasi Password Wajib Diisi dan Harus Sama Dengan Password.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                        emailError = email.isEmpty()
                        passwordError = password.isEmpty()
                        confirmPasswordError = confirmPassword.isEmpty()

                        dialogMessage = "Semua field harus diisi."
                        openDialogState.value = true
                        return@Button
                    }

                    if (password != confirmPassword) {
                        dialogMessage = "Password dan Konfirmasi Password Tidak Cocok."
                        openDialogState.value = true
                        return@Button
                    }

                    val registrationRequest = RegisterRequest(email, password, confirmPassword)
                    userViewModel.registerUser(
                        registerRequest = registrationRequest,
                        context = context,
                        navController = navController,
                        scope = scope,
                        onDialogMessageChange = { message -> dialogMessage = message },
                        onOpenDialogChange = { state -> openDialogState.value = state },
                        isLoading = { loading -> isLoading = loading }
                    )
                },
                modifier = Modifier.fillMaxWidth(0.8f),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(text = "Daftar")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = buildAnnotatedString {
                    append("Sudah punya akun? ")
                    withStyle(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append("Login")
                    }
                },
                modifier = Modifier.clickable {
                    navController.navigate("login")
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

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
        if (isLoading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenPreview() {
    AppTheme {
        val navController = rememberNavController()
        RegisterScreen(navController)
    }
}
