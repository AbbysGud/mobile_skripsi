package com.example.stationbottle.models

import androidx.lifecycle.ViewModel
import com.example.stationbottle.client.RetrofitClient.apiService
import com.example.stationbottle.data.DaerahResponse
import com.example.stationbottle.data.Kecamatan
import com.example.stationbottle.data.Kelurahan
import com.example.stationbottle.data.KodeResponse
import com.example.stationbottle.data.Kota
import com.example.stationbottle.data.Provinsi

class WilayahViewModel : ViewModel() {
    suspend fun getAllProvinsi(): List<Provinsi> {
        return try {
            apiService.getAllProvinsi()
        } catch (e: Exception) {
            e.printStackTrace()
            println(e)
            emptyList()
        }
    }

    suspend fun getKotaByProvinsi(idProvinsi: Int): List<Kota> {
        return try {
            apiService.getKotaByProvinsi(idProvinsi)
        } catch (e: Exception) {
            e.printStackTrace()
            println(e)
            emptyList()
        }
    }

    suspend fun getKecamatanByKota(idKota: Int): List<Kecamatan> {
        return try {
            apiService.getKecamatanByKota(idKota)
        } catch (e: Exception) {
            e.printStackTrace()
            println(e)
            emptyList()
        }
    }

    suspend fun getKelurahanByKecamatan(idKecamatan: Int): List<Kelurahan> {
        return try {
            apiService.getKelurahanByKecamatan(idKecamatan)
        } catch (e: Exception) {
            e.printStackTrace()
            println(e)
            emptyList()
        }
    }

    suspend fun getDaerahByKelurahan(idKelurahan: Int): DaerahResponse {
        return try {
            apiService.getDaerahByKelurahan(idKelurahan)
        } catch (e: Exception) {
            e.printStackTrace()
            // Mengembalikan respons error yang konsisten
            DaerahResponse(
                message = "Failed to fetch region data: ${e.message}",
                data = null
            )
        }
    }

    suspend fun getKodeLengkap(idKelurahan: Int): KodeResponse {
        return try {
            apiService.getKodeLengkap(idKelurahan)
        } catch (e: Exception) {
            e.printStackTrace()
            KodeResponse(
                ""
            )
        }
    }
}