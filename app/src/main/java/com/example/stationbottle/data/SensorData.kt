package com.example.stationbottle.data

data class SensorData(
    val id: Int,
    val user_id: Int,
    val rfid_tag: String,
    val weight: Double,
    val previous_weight: Double,
    val created_at: String,
    val updated_at: String,
    val normalizedDiff: Double? = null
)