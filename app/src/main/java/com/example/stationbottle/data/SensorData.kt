package com.example.stationbottle.data

data class SensorData(
    val id: Int,
    val user_id: Int,
    val rfid_tag: String,
    val weight: Double,
    val previous_weight: Double,
    val temperature: Float? = null,
    val humidity: Float? = null,
    val device_id: String? = null,
    val created_at: String,
    val updated_at: String,
    val normalizedDiff: Double? = null,
    var session: Triple<String, String, Double>? = null
)

data class SuhuData(
    val id: Int,
    val temperature: Float,
    val humidity: Float,
    val device_id: String,
    val created_at: String,
    val updated_at: String,
)