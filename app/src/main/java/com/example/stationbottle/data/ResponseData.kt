package com.example.stationbottle.data

import java.time.LocalTime

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
    val user_id: String,
    val data: List<SensorData>
)

data class PredictionResult(
    val todayAktual: Double,
    val todayPrediksi: Double,
    val todayList: LinkedHashMap<String, Double>,
    val prediksiList: LinkedHashMap<String, Double>,
    val statusHistory: Boolean,
    val drinkSessionList: MutableList<Triple<String, String, Double>?>?,
    val userWaktuMulai: LocalTime?,
    val userWaktuSelesai: LocalTime?
)

data class NGROKResponse(
    val id: Int,
    val http_url: String,
    val websocket_url: String,
    val websocket_port: Int,
    val updated_at: String
)