package com.example.stationbottle.data

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
    val daily_goal: Double? = null,
    val waktu_mulai: String? = null,
    val waktu_selesai: String? = null
)

data class ModeRequest(
    val message: String,
    val user_id: Int
)