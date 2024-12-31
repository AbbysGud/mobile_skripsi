package com.example.stationbottle.models

data class LoginResponse(
    val message: String,
    val token: String,
    val data: UserData
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
    val rfid_tag: String,
    val email_verified_at: String?,
    val created_at: String,
    val updated_at: String
)