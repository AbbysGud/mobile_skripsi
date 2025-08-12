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
    val userWaktuSelesai: LocalTime?,
    val todayPrediksiWhole: Double,
    val prediksiListWhole: LinkedHashMap<String, Double>,
    val syaratHistory: Boolean = true
)

data class NGROKResponse(
    val id: Int,
    val http_url: String,
    val websocket_url: String,
    val websocket_port: Int,
    val updated_at: String
)

data class BMKGWeatherResponse(
    val lokasi: Lokasi,
    val data: List<WeatherData>
)

data class SuhuResponse(
    val message: String,
    val data: SuhuData
)

data class LatestDataResponse(
    val message: String,
    val user_id: String,
    val last_bottle_fill_or_new: SensorData,
    val last_drink_event: SensorData,
    val previous_drink_event: SensorData,
)

data class Lokasi(
    val adm1: String,
    val adm2: String,
    val adm3: String,
    val adm4: String,
    val provinsi: String,
    val kotkab: String,
    val kecamatan: String,
    val desa: String,
    val lon: Double,
    val lat: Double,
    val timezone: String,
    val type: String?
)

data class WeatherData(
    val lokasi: Lokasi,
    val cuaca: List<List<CuacaItem>>
)

data class CuacaItem(
    val datetime: String,
    val t: Int,
    val tcc: Int,
    val tp: Double,
    val weather: Int,
    val weather_desc: String,
    val weather_desc_en: String,
    val wd_deg: Int,
    val wd: String,
    val wd_to: String,
    val ws: Double,
    val hu: Int,
    val vs: Int,
    val vs_text: String,
    val time_index: String,
    val analysis_date: String,
    val image: String,
    val utc_datetime: String,
    val local_datetime: String
)

data class DaerahResponse(
    val message: String,
    val data: RegionData?
)

data class KodeResponse(
    val kode_kelurahan_lengkap: String
)