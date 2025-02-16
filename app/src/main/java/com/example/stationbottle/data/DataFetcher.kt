package com.example.stationbottle.data

import com.example.stationbottle.service.ApiService
import com.example.stationbottle.service.retry
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

suspend fun fetchSensorDataHistory(
    apiService: ApiService,
    userId: Int,
    today: String
): List<SensorData>? {
    return try {
        val historyData = retry(times = 3) {
            apiService.getSensorDataHistory(userId, today)
        }
        historyData.data.filter {
            it.previous_weight != 0.0 && it.previous_weight > it.weight
        }
    } catch (e: UnknownHostException) {
        println("Kesalahan jaringan: Server tidak dapat dijangkau.")
        null
    } catch (e: SocketTimeoutException) {
        println("Kesalahan: Waktu permintaan habis.")
        null
    } catch (e: HttpException) {
        if (e.code() == 404) {
            println("Data historis tidak ditemukan")
            null
        } else {
            println("Kesalahan HTTP: ${e.message}")
            null
        }
    } catch (e: Exception) {
        println("Kesalahan tak terduga: ${e.message}")
        null
    }
}


suspend fun fetchTodaySensorData(
    apiService: ApiService,
    userId: Int,
    today: String,
    tommorow: String? = null,
    waktuMulai: String? = null,
    waktuSelesai: String? = null
): List<SensorData>? {
    return try {
        val todayData = retry(times = 3) {
            if (tommorow != null && waktuMulai != null && waktuSelesai != null) {
                apiService.getSensorData(today, tommorow, userId, waktuMulai, waktuSelesai)
            } else {
                apiService.getSensorData(today, today, userId)
            }
        }
        todayData.data.filter {
            it.previous_weight != 0.0 && it.previous_weight > it.weight
        }
    } catch (e: UnknownHostException) {
        println("Kesalahan jaringan: Server tidak dapat dijangkau.")
        null
    } catch (e: SocketTimeoutException) {
        println("Kesalahan: Waktu permintaan habis.")
        null
    } catch (e: HttpException) {
        if (e.code() == 404) {
            println("Data hari ini tidak ditemukan, hanya menggunakan data historis.")
            null
        } else {
            println("Kesalahan HTTP: ${e.message}")
            null
        }
    } catch (e: Exception) {
        println("Kesalahan tak terduga: ${e.message}")
        null
    }
}