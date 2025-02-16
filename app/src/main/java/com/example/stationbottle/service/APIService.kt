package com.example.stationbottle.service

import com.example.stationbottle.data.ForgotPasswordRequest
import com.example.stationbottle.data.LoginRequest
import com.example.stationbottle.data.LoginResponse
import com.example.stationbottle.data.ModeRequest
import com.example.stationbottle.data.NGROKResponse
import com.example.stationbottle.data.RegisterRequest
import com.example.stationbottle.data.ResetPasswordRequest
import com.example.stationbottle.data.SensorDataResponse
import com.example.stationbottle.data.UpdateUserRequest
import com.example.stationbottle.data.UserResponse
import kotlinx.coroutines.delay
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

interface ApiService {
    @POST("login")
    suspend fun login(
        @Body loginRequest: LoginRequest
    ): Response<LoginResponse>

    @POST("register")
    suspend fun register(
        @Body registerRequest: RegisterRequest
    ): Response<RegisterRequest>

    @POST("logout")
    suspend fun logout(
        @Header("Authorization") token: String
    ): Response<ResponseBody>

    @POST("forgot-password")
    suspend fun forgotPassword(
        @Body request: ForgotPasswordRequest
    ): Response<Any>

    @POST("reset-password")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest
    ): Response<Any>

    @GET("users/{id}")
    suspend fun getUserData(
        @Path("id") id: Int
    ): UserResponse

    @PUT("users/{id}/profile")
    suspend fun updateUserData(
        @Path("id") id: Int,
        @Body updateRequest: UpdateUserRequest
    ): UserResponse

    @GET("sensor-data/date-range")
    suspend fun getSensorData(
        @Query("from_date") fromDate: String,
        @Query("to_date") toDate: String,
        @Query("user_id") userId: Int,
        @Query("waktu_mulai") waktuMulai: String? = null,
        @Query("waktu_selesai") waktuSelesai: String? = null
    ): SensorDataResponse

    @GET("sensor-data/history")
    suspend fun getSensorDataHistory(
        @Query("user_id") userId: Int,
        @Query("today_date") fromDate: String,
    ): SensorDataResponse

    @GET("get-ngrok")
    suspend fun getNGROKUrl(): NGROKResponse

    @POST("send-mode")
    suspend fun sendMode(
        @Body request: ModeRequest
    ): Response<Any>
}

fun convertUtcToWIB(utcDate: String?, includeTime: Boolean = false, timeOnly: Boolean = false): String? {
    return utcDate?.let {
        try {
            if (it.contains("Z")) {
                val utcFormat =
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                utcFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = utcFormat.parse(it)!!

                val wibFormat = when {
                    timeOnly -> SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    includeTime -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                }
                wibFormat.timeZone = TimeZone.getTimeZone("Asia/Jakarta")

                return wibFormat.format(date)
            } else {
                return it
            }
        } catch (e: Exception) {
            println("Error: $e")
            null
        }
    }
}

suspend fun <T> retry(
    times: Int = 3,
    initialDelay: Long = 1000L,
    maxDelay: Long = 5000L,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            if (it == times - 1 || e.message?.contains("timeout", ignoreCase = true) != true) {
                throw e
            }
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    return block() // Last attempt
}