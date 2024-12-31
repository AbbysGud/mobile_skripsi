package com.example.stationbottle.data

import com.example.stationbottle.models.LoginResponse
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import java.text.SimpleDateFormat
import java.util.*

interface ApiService {
    @POST("login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<LoginResponse>

    @POST("register")
    suspend fun register(@Body registerRequest: RegisterRequest): Response<RegisterRequest>

    @POST("logout")
    suspend fun logout(@Header("Authorization") token: String): Response<ResponseBody>

    @POST("forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<Any>

    @POST("reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<Any>

    @GET("users/{id}")
    suspend fun getUserData(@Path("id") id: Int): UserResponse

    @PUT("users/{id}/profile")
    suspend fun updateUserData(
        @Path("id") id: Int,
        @Body updateRequest: UpdateUserRequest
    ): UserResponse
}

data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String, val password_confirmation: String)
data class ForgotPasswordRequest(val email: String)
data class ResetPasswordRequest(val email: String, val token: String, val password: String, val password_confirmation: String)
data class UserResponse(val message: String, val data: FullUser)
data class FullUser(
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
    val rfid_tag: String? = null,
    val email_verified_at: String? = null,
    val created_at: String,
    val updated_at: String
)

data class UpdateUserRequest(
    val name: String? = null,
    val date_of_birth: String? = null,
    val weight: Double? = null,
    val height: Double? = null,
    val gender: String? = null,
    val pregnancy_date: String? = null,
    val breastfeeding_date: String? = null,
    val daily_goal: Double? = null
)

fun convertUtcToWIB(utcDate: String?): String? {
    return utcDate?.let {
        try {
            // Jika tanggal sudah memiliki penanda UTC ('Z'), maka proses konversi ke WIB
            if (it.contains("Z")) {
                val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()) // Format UTC
                utcFormat.timeZone = TimeZone.getTimeZone("UTC") // Set timezone UTC
                val date = utcFormat.parse(it) // Parse UTC date string

                // Set TimeZone ke WIB (Asia/Jakarta)
                val wibFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                wibFormat.timeZone = TimeZone.getTimeZone("Asia/Jakarta")

                // Format tanggal ke WIB
                return wibFormat.format(date)
            } else {
                // Jika tidak ada 'Z' pada tanggal, anggap itu sudah dalam format lokal dan return langsung
                return it
            }
        } catch (e: Exception) {
            null
        }
    }
}
