package com.example.stationbottle.models

data class LoginResponse(
    val message: String,
    val token: String,
    val data: UserData
)

data class UserData(
    val id: Int,
    val email: String,
    val name: String,
    val age: Int,
    val weight: Int,
    val daily_goal: Double,
    val rfid_tag: String,
    val email_verified_at: String?,
    val created_at: String,
    val updated_at: String
)