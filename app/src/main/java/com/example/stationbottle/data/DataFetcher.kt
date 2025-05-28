package com.example.stationbottle.data

import com.example.stationbottle.service.ApiService
import com.example.stationbottle.service.retry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

//suspend fun fetchSensorDataHistory(
//    apiService: ApiService,
//    userId: Int,
//    today: String
//): List<SensorData>? {
//    return try {
//        val historyData = retry(times = 3) {
//            apiService.getSensorDataHistory(userId, today)
//        }
//        historyData.data
//            .filter {
//                it.previous_weight != 0.0 &&
//                    it.previous_weight > it.weight &&
//                    it.previous_weight - it.weight > 20
//            }
//            .distinctBy { it.created_at }
//    } catch (e: UnknownHostException) {
//        println("Kesalahan jaringan: Server tidak dapat dijangkau.")
//        null
//    } catch (e: SocketTimeoutException) {
//        println("Kesalahan: Waktu permintaan habis.")
//        null
//    } catch (e: HttpException) {
//        if (e.code() == 404) {
//            println("Data historis tidak ditemukan")
//            null
//        } else {
//            println("Kesalahan HTTP: ${e.message}")
//            null
//        }
//    } catch (e: Exception) {
//        println("Kesalahan tak terduga: ${e.message}")
//        null
//    }
//}

suspend fun fetchSensorDataHistory(
    apiService: ApiService,
    userId: Int,
    today: String
): List<SensorData>? = withContext(Dispatchers.IO) {
    try {
        val historyData = retryUntilSuccess {
            apiService.getSensorDataHistory(userId, today)
        }

        historyData.data
            .filter {
                it.previous_weight != 0.0 &&
                        it.previous_weight > it.weight &&
                        it.previous_weight - it.weight > 20
            }
            .distinctBy { it.created_at }
    } catch (e: UnknownHostException) {
        println("Kesalahan jaringan: Server tidak dapat dijangkau.")
        null
    } catch (e: SocketTimeoutException) {
        println("Kesalahan: Waktu permintaan habis.")
        null
    } catch (e: HttpException) {
        println("Kesalahan HTTP (${e.code()}): ${e.message}")
        null
    } catch (e: Exception) {
        println("Kesalahan tak terduga: ${e.message}")
        null
    }
}


suspend fun <T> retryUntilSuccess(
    delaySeconds: Long = 30,
    block: suspend () -> T
): T {
    while (true) {
        try {
            return block()
        } catch (e: HttpException) {
            if (e.code() == 429) {
                val retryAfter = e.response()?.headers()?.get("Retry-After")?.toLongOrNull() ?: delaySeconds
                println("HTTP 429: Menunggu $retryAfter detik sebelum mencoba lagi...")
                delay(retryAfter * 1000)
            } else {
                throw e // kesalahan selain 429, lempar ke luar
            }
        }
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
            it.previous_weight != 0.0 && it.previous_weight > it.weight && it.previous_weight - it.weight > 20
        }
    } catch (e: UnknownHostException) {
        println("Kesalahan jaringan: Server tidak dapat dijangkau.")
        null
    } catch (e: SocketTimeoutException) {
        println("Kesalahan: Waktu permintaan habis.")
        null
    } catch (e: HttpException) {
        if (e.code() == 404) {
//            println("Data hari ini tidak ditemukan, hanya menggunakan data historis.")
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