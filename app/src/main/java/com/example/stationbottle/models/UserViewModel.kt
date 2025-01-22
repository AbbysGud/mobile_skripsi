package com.example.stationbottle.models

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.example.stationbottle.data.LoginRequest
import com.example.stationbottle.data.RegisterRequest
import com.example.stationbottle.data.RetrofitClient
import com.example.stationbottle.data.UpdateUserRequest
import com.example.stationbottle.data.UserDataStore
import com.example.stationbottle.data.convertUtcToWIB
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class User(
    val token: String,
    val id: Int,
    val name: String? = null,
    val email: String,
    val date_of_birth: String? = null,
    val weight: Double? = null,
    val height: Double? = null,
    val gender: String? = null,
    val pregnancy_date: String? = null,
    val breastfeeding_date: String? = null,
    val daily_goal: Double? = null,
    val waktu_mulai: String? = null,
    val waktu_selesai: String? = null,
    val rfid_tag: String? = null
)

data class UserData(
    val id: Int,
    val name: String,
    val email: String,
    val date_of_birth: String,
    val weight: Double,
    val height: Double,
    val gender: String,
    val pregnancy_date: String?,
    val breastfeeding_date: String?,
    val daily_goal: Double,
    val waktu_mulai: String? = null,
    val waktu_selesai: String? = null,
    val rfid_tag: String,
    val email_verified_at: String?,
    val created_at: String,
    val updated_at: String
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
                            date_of_birth = convertUtcToWIB(loginResponse.data.date_of_birth),
                            weight = loginResponse.data.weight,
                            height = loginResponse.data.height,
                            gender = loginResponse.data.gender,
                            pregnancy_date = convertUtcToWIB(loginResponse.data.pregnancy_date),
                            breastfeeding_date = convertUtcToWIB(loginResponse.data.breastfeeding_date),
                            daily_goal = loginResponse.data.daily_goal,
                            waktu_mulai = loginResponse.data.waktu_mulai,
                            waktu_selesai = loginResponse.data.waktu_selesai,
                            rfid_tag = loginResponse.data.rfid_tag
                        )
                        userViewModel.saveUser(context, user)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Login Berhasil, Selamat Datang ${user.name}", Toast.LENGTH_SHORT).show()
                            navController.navigate("home")
                        }
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

    fun logoutUser(context: Context, token: String, navController: NavController) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.logout("Bearer $token")
                if (response.isSuccessful) {
                    Log.i("Logout", "Logout Berhasil")
                    withContext(Dispatchers.Main) {
                        UserDataStore.clearUser(context)
                        Toast.makeText(context, "Logout Berhasil!", Toast.LENGTH_SHORT).show()
                        navController.navigate("login")
                    }
                    _userState.value = null
                } else {
                    Log.i("Logout", "Logout Gagal ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("Logout", "Logout Gagal: ${e.message}")
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

    fun getUserData(context: Context, userId: Int, token: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getUserData(userId)
                val user = User(
                    token = token,
                    id = response.data.id,
                    name = response.data.name,
                    email = response.data.email,
                    date_of_birth = convertUtcToWIB(response.data.date_of_birth),
                    weight = response.data.weight,
                    height = response.data.height,
                    gender = response.data.gender,
                    pregnancy_date = convertUtcToWIB(response.data.pregnancy_date),
                    breastfeeding_date = convertUtcToWIB(response.data.breastfeeding_date),
                    daily_goal = response.data.daily_goal,
                    waktu_mulai = response.data.waktu_mulai,
                    waktu_selesai = response.data.waktu_selesai,
                    rfid_tag = response.data.rfid_tag
                )
                saveUser(context, user)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Failed to fetch user data: ${e.message}")
            }
        }
    }

    fun updateUser(
        navController: NavController,
        context: Context,
        userId: Int,
        updateRequest: UpdateUserRequest,
        token: String
    ) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.updateUserData(userId, updateRequest)
                val currentUser = _userState.value ?: User(
                    token,
                    userId,
                    null,
                    "",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
                val updatedUser = currentUser.copy(
                    name = response.data.name,
                    date_of_birth = response.data.date_of_birth,
                    weight = response.data.weight,
                    height = response.data.height,
                    gender = response.data.gender,
                    pregnancy_date = response.data.pregnancy_date,
                    breastfeeding_date = response.data.breastfeeding_date,
                    daily_goal = response.data.daily_goal,
                    waktu_mulai = response.data.waktu_mulai,
                    waktu_selesai = response.data.waktu_selesai,
                    rfid_tag = response.data.rfid_tag
                )
                saveUser(context, updatedUser)
                withContext(Dispatchers.Main) {
                    navController.navigate("profile")
                    Toast.makeText(context, "Profile berhasil diupdate!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Gagal Update Data: ${e.message}")
                withContext(Dispatchers.Main) {
                    navController.navigate("profile")
                    Toast.makeText(context, "Gagal memperbarui profile.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}