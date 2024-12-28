package com.example.stationbottle.data

import com.example.stationbottle.models.LoginResponse
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

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
}

data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String, val password_confirmation: String)
data class ForgotPasswordRequest(val email: String)
data class ResetPasswordRequest(val email: String, val token: String, val password: String, val password_confirmation: String)