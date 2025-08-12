package com.example.stationbottle.data

import java.util.SortedMap

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
    val frekuensi_notifikasi: Int? = null,
    val id_kelurahan: String? = null,
    val device_id: String? = null,
)

data class UserData(
    val id: Int,
    val name: String?,
    val email: String,
    val date_of_birth: String?,
    val weight: Double?,
    val height: Double?,
    val gender: String?,
    val pregnancy_date: String?,
    val breastfeeding_date: String?,
    val daily_goal: Double?,
    val waktu_mulai: String? = null,
    val waktu_selesai: String? = null,
    val rfid_tag: String?,
    val frekuensi_notifikasi: Int? = null,
    val email_verified_at: String?,
    val created_at: String,
    val updated_at: String,
    val id_kelurahan: String? = null,
    val daerah: RegionData? = null,
    val device_id: String? = null,
)

data class RegionData(
    val id_kelurahan: Int?,
    val nama_kelurahan: String?,
    val kode_kelurahan: String?,
    val id_kecamatan: Int?,
    val nama_kecamatan: String?,
    val kode_kecamatan: String?,
    val id_kota: Int?,
    val nama_kota: String?,
    val kode_kota: String?,
    val id_provinsi: Int?,
    val nama_provinsi: String?,
    val kode_provinsi: String?,
    val kode_lengkap_wilayah: String?
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
    val datePrediksi: String,
    val prediksiWaktuWhole: Array<Double>?,
    val prediksiAirWhole: DoubleArray?,
    val totalPrediksiWhole: Double?
)

data class WeeklyPredictionEntry(
    val date: String,
    val weeklyPrediction: SortedMap<String, Double>
)

data class HistoryWeeklyEntry(
    val date: String,
    val weeklyHistory: SortedMap<String, Double>
)