package com.example.stationbottle.data

import com.example.stationbottle.client.BMKGRetrofitClient
import com.example.stationbottle.service.ApiService
import com.example.stationbottle.service.convertUtcToWIB
import com.example.stationbottle.service.retry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate

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

        val sorted = todayData.data.sortedBy { it.created_at }

        if (sorted.isNotEmpty()) {
            val first = sorted.first()
            if (
                first.previous_weight != 0.0 &&
                first.previous_weight > first.weight &&
                first.previous_weight - first.weight > 20
            ) {
                val yesterday = LocalDate.parse(today).minusDays(1).toString()
                val yesterdayData = retry(times = 3) {
                    apiService.getSensorData(yesterday, yesterday, userId)
                }

                val yesterdaySorted = yesterdayData.data.sortedBy { it.created_at }
                val lastFromYesterday = yesterdaySorted.lastOrNull()

                if (lastFromYesterday != null) {
                    val startTime = convertUtcToWIB(lastFromYesterday.created_at, timeOnly = true) ?: "-"
                    val endTime = convertUtcToWIB(first.created_at, timeOnly = true) ?: "-"
                    val volume = first.previous_weight - first.weight
                    first.session = Triple(startTime, endTime, volume)
                }
            }
        }

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]

            if (
                curr.previous_weight != 0.0 &&
                curr.previous_weight > curr.weight &&
                curr.previous_weight - curr.weight > 20
            ) {
                val startTime = convertUtcToWIB(prev.created_at, timeOnly = true) ?: "-"
                val endTime = convertUtcToWIB(curr.created_at, timeOnly = true) ?: "-"
                val volume = curr.previous_weight - curr.weight
                curr.session = Triple(startTime, endTime, volume)
            }
        }

        sorted
//        todayData.data.filter {
//            it.previous_weight != 0.0 && it.previous_weight > it.weight && it.previous_weight - it.weight > 20
//        }
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

suspend fun fetchBMKGWeatherData(adm4: String): BMKGWeatherResponse? {
    return try {
        retry(times = 3) {
            val response = BMKGRetrofitClient.bmkgAPIService.getWeatherForecast(adm4)
            if (response.isSuccessful) {
                response.body()
            } else {
                // Tangani respons tidak sukses (misalnya, kode 404, 500)
                println("Error fetching BMKG data: ${response.code()} - ${response.message()}")
                null
            }
        }
    } catch (e: UnknownHostException) {
        println("Kesalahan jaringan: Server BMKG tidak dapat dijangkau.")
        null
    } catch (e: SocketTimeoutException) {
        println("Kesalahan: Waktu permintaan API BMKG habis.")
        null
    } catch (e: HttpException) {
        println("Kesalahan HTTP saat mengambil data BMKG: ${e.code()}")
        null
    } catch (e: Exception) {
        println("Kesalahan tidak diketahui saat mengambil data BMKG: ${e.message}")
        null
    }
}