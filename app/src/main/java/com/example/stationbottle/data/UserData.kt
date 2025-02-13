package com.example.stationbottle.data

data class User(
    val token: String,
    val id: Int,
    val name: String? = null,
    val email: String,
    val date_of_birth: String? = null,
    val weight: Double? = null,
    val height: Double? = null,
    val gender: String? = null,
    val pregnancy_date: String? = null,
    val breastfeeding_date: String? = null,
    val daily_goal: Double? = null,
    val waktu_mulai: String? = null,
    val waktu_selesai: String? = null,
    val rfid_tag: String? = null,
    val frekuensi_notifikasi: Int? = null
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
    val waktu_mulai: String? = null,
    val waktu_selesai: String? = null,
    val rfid_tag: String,
    val frekuensi_notifikasi: Int? = null,
    val email_verified_at: String?,
    val created_at: String,
    val updated_at: String
)

data class UserPrediksi(
    val waktuAkhir: String,
    val minumAkhir: Double?,
    val prediksiWaktu: Array<Double>?,
    val prediksiMinum: DoubleArray?,
    val waktuPredMulai: String,
    val waktuPredSelesai: String,
    val totalPrediksi: Double?,
    val totalAktual: Double?,
    val datePrediksi: String
)