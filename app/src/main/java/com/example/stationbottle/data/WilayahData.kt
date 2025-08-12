package com.example.stationbottle.data

data class Provinsi(
    val id: Int,
    val nama: String?,
    val kode_provinsi: String?
)

data class Kota(
    val id: Int,
    val nama: String?,
    val kode_kota: String?,
    val id_provinsi: String
)

data class Kecamatan(
    val id: Int,
    val nama: String?,
    val kode_kecamatan: String?,
    val id_kota: String
)

data class Kelurahan(
    val id: Int,
    val nama: String?,
    val kode_kelurahan: String?,
    val id_kecamatan: String
)