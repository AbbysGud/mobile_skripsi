package com.example.stationbottle.models

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.example.stationbottle.data.ApiService
import com.example.stationbottle.data.LoginRequest
import com.example.stationbottle.data.RegisterRequest
import com.example.stationbottle.data.RetrofitClient
import com.example.stationbottle.data.UserDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.awaitResponse


data class User(
    val token: String,
    val id: Int,
    val email: String,
    val name: String? = null,
    val age: Int? = null,
    val weight: Float? = null,
    val dailyGoal: Float? = null,
    val rfidTag: String? = null
)

class UserViewModel : ViewModel() {
    private val _userState = MutableStateFlow<User?>(null)

    fun saveUser(context: Context, user: User) {
        viewModelScope.launch {
            UserDataStore.saveUser(context, user)
            _userState.value = user
        }
    }

    fun getUser(context: Context): Flow<User?> {
        return UserDataStore.getUser(context)
    }

    fun loginUser(
        loginRequest: LoginRequest,
        context: Context,
        navController: NavController,
        userViewModel: UserViewModel,
        scope: CoroutineScope,
        onDialogMessageChange: (String) -> Unit,
        onOpenDialogChange: (Boolean) -> Unit
    ) {
        scope.launch {
            try {
                val response = RetrofitClient.apiService.login(loginRequest)
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    if (loginResponse != null) {
                        val user = User(
                            token = loginResponse.token,
                            id = loginResponse.data.id,
                            email = loginResponse.data.email,
                            name = loginResponse.data.name,
                            age = loginResponse.data.age,
                            weight = loginResponse.data.weight.toFloat(),
                            dailyGoal = loginResponse.data.daily_goal.toFloat(),
                            rfidTag = loginResponse.data.rfid_tag
                        )
                        userViewModel.saveUser(context, user)
                        Toast.makeText(context, "Login Berhasil, Selamat Datang ${user.name}", Toast.LENGTH_SHORT).show()
                        navController.navigate("home")
                    } else {
                        onDialogMessageChange("Login Gagal: Data Anda Kosong.")
                        onOpenDialogChange(true)
                    }
                } else {
                    val gson = Gson()
                    val errorBody = response.errorBody()?.string()
                    val dialogMessage = if (!errorBody.isNullOrEmpty()) {
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
                            400 -> "Invalid email or password. Please try again."
                            401 -> "Unauthorized: Incorrect email or password."
                            500 -> "Server error. Please try again later."
                            else -> "Unexpected error occurred: ${response.message()}."
                        }
                    }
                    onDialogMessageChange(dialogMessage.toString())
                    onOpenDialogChange(true)
                }
            } catch (e: Exception) {
                onDialogMessageChange("Internet error: ${e.message}. Cek Koneksi Anda dan Coba Lagi.")
                onOpenDialogChange(true)
            }
        }
    }

    fun logoutUser(context: Context, token: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.logout("Bearer $token")

                if (response.isSuccessful) {
                    Log.i("Logout", "Logout Berhasil")
                    Toast.makeText(context, "Logout Berhasil!", Toast.LENGTH_SHORT).show()
                    UserDataStore.clearUser(context)
                    _userState.value = null
                } else {
                    Log.i("Logout", "Logout Gagal ${response.errorBody()?.string()}")
                }
            } catch (e: HttpException) {
                Log.i("Logout", "Logout Gagal 1, $e")
            } catch (e: Exception) {
                Log.i("Logout", "Logout Gagal 2, $e")
            }
        }
    }

    fun registerUser(
        registerRequest: RegisterRequest,
        context: Context,
        navController: NavController,
        scope: CoroutineScope,
        onDialogMessageChange: (String) -> Unit,
        onOpenDialogChange: (Boolean) -> Unit
    ) {
        scope.launch {
            try {
                val response = RetrofitClient.apiService.register(registerRequest)
                if (response.isSuccessful) {
                    val registerResponse = response.body()
                    if (registerResponse != null) {
                        onDialogMessageChange("Registrasi Berhasil")
                        Toast.makeText(context, "Registrasi Berhasil!", Toast.LENGTH_SHORT).show()
                        navController.navigate("login")
                    } else {
                        onDialogMessageChange("Registrasi Gagal: Data Anda Kosong.")
                        onOpenDialogChange(true)
                    }
                } else {
                    val gson = Gson()
                    val errorBody = response.errorBody()?.string()
                    val dialogMessage = if (!errorBody.isNullOrEmpty()) {
                        try {
                            val type = object : TypeToken<Map<String, Any>>() {}.type
                            val errorMap: Map<String, Any>? = gson.fromJson<Map<String, Any>>(errorBody, type)

                            val errors = errorMap?.get("errors") as? Map<String, List<String>>
                            val errorMessages = errors?.values?.flatten()?.joinToString("\n") ?: "Error Tidak Terduga."
                            errorMessages

                        } catch (e: Exception) {
                            Log.e("Register", "Failed to parse error body: ${e.message}")
                            "Error Tidak Terduga: Gagal dalam Parsing Data."
                        }
                    } else {
                        when (response.code()) {
                            500 -> "Server error. Please try again later."
                            else -> "Unexpected error occurred: ${response.message()}."
                        }
                    }
                    onDialogMessageChange(dialogMessage.toString())
                    onOpenDialogChange(true)
                }
            } catch (e: Exception) {
                onDialogMessageChange("Internet error: ${e.message}. Cek Koneksi Anda dan Coba Lagi.")
                onOpenDialogChange(true)
            }
        }
    }
}