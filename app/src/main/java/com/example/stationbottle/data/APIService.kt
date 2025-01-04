package com.example.stationbottle.data

import com.example.stationbottle.models.SensorData
import com.example.stationbottle.models.UserData
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
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

    @GET("sensor-data/date-range")
    suspend fun getSensorData(
        @Query("from_date") fromDate: String,
        @Query("to_date") toDate: String,
        @Query("rfid_tag") rfidTag: String
    ): SensorDataResponse
}

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val password_confirmation: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class ResetPasswordRequest(
    val email: String,
    val token: String,
    val password: String,
    val password_confirmation: String
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

data class LoginResponse(
    val message: String,
    val token: String,
    val data: UserData
)

data class UserResponse(
    val message: String,
    val data: UserData
)

data class SensorDataResponse(
    val message: String,
    val from_date: String,
    val to_date: String,
    val input_rfid: String,
    val data: List<SensorData>
)

fun convertUtcToWIB(utcDate: String?, includeTime: Boolean = false): String? {
    return utcDate?.let {
        try {
            if (it.contains("Z")) {
                val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                utcFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = utcFormat.parse(it)

                val wibFormat = if (includeTime) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                } else {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                }
                wibFormat.timeZone = TimeZone.getTimeZone("Asia/Jakarta")

                return wibFormat.format(date)
            } else {
                return it
            }
        } catch (e: Exception) {
            null
        }
    }
}

