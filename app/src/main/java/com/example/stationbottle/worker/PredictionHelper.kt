package com.example.stationbottle.worker

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.stationbottle.data.XGBoost
import com.example.stationbottle.data.fetchSensorDataHistory
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.example.stationbottle.client.RetrofitClient.apiService
import com.example.stationbottle.data.EvaluationMetrics
import com.example.stationbottle.data.PredictionResult
import com.example.stationbottle.data.SensorData
import com.example.stationbottle.data.SensorDataResponse
import com.example.stationbottle.data.User
import com.example.stationbottle.data.UserDataStore
import com.example.stationbottle.data.UserDataStore.getPrediksi
import com.example.stationbottle.data.fetchTodaySensorData
import com.example.stationbottle.service.convertUtcToWIB
import kotlinx.coroutines.flow.first
import simpanKeExcel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.collections.set
import kotlin.math.abs

suspend fun calculatePrediction(
    context: Context,
    user: User,
    waktuMulai: String,
    waktuSelesai: String,
    todayDate: String? = null
): PredictionResult {
    val prediksiStore = getPrediksi(context).first()

    val tanggalList = mutableListOf<String>()

    val waktuList = mutableListOf<String>()
    val waktuListToday = mutableListOf<String>()
    val waktuListPrediksi = mutableListOf<String>()
    val minumList = mutableListOf<Double>()
    val minumListToday = mutableListOf<Double>()
    val minumListPrediksi = mutableListOf<Double>()
    val historyList = linkedMapOf<String, Double>()
    val todayList = linkedMapOf<String, Double>()
    val prediksiList = linkedMapOf<String, Double>()

    var totalPrediksi: Double = 0.0

    var statusHistory: Boolean
    var isBedaHari: Boolean = false

    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
//    val today = LocalDate.now().format(formatter)

//    var today = LocalDate.parse("2025-04-02").format(formatter)
//    if(todayDate != null){
//        today = todayDate
//    }
//
//    val historyData = fetchSensorDataHistory(apiService, user.id, today)
//    statusHistory = historyData != null
//    historyData?.forEach { sensorData ->
//        if (sensorData.previous_weight != 0.0 && sensorData.previous_weight > sensorData.weight) {
//            val tanggal = convertUtcToWIB(sensorData.created_at, includeTime = true).toString()
//            tanggalList.add(tanggal)
//
//            val waktu = convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()
//            waktuList.add(waktu)
//
//            val minum = abs(sensorData.previous_weight - sensorData.weight)
//            minumList.add(minum)
//
//            historyList[convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()] =
//                sensorData.previous_weight - sensorData.weight
//        }
//    }
//
//    var todayData: List<SensorData>? = emptyList()
//
//    if (user.waktu_mulai != null && user.waktu_selesai != null) {
//        val startTime = dateFormat.parse(user.waktu_mulai)!!
//        val endTime = dateFormat.parse(user.waktu_selesai)!!
//        val waktuSekarang = dateFormat.parse(dateFormat.format(Date()))!!
//
////        var from_date = LocalDate.now().format(formatter)
////        var to_date = LocalDate.now().plusDays(1).format(formatter)
//        var from_date = LocalDate.parse("2025-04-01").format(formatter)
//        var to_date = LocalDate.parse("2025-04-02").format(formatter)
//
//        if(startTime.time > endTime.time){
//            isBedaHari = true
//            if(waktuSekarang.time < endTime.time){
//                isBedaHari = false
//                from_date = LocalDate.now().minusDays(1).format(formatter)
//                to_date = LocalDate.now().format(formatter)
//            }
//        }
//
//        todayData = if (startTime.time > endTime.time) {
//            fetchTodaySensorData(apiService, user.id, from_date, to_date, user.waktu_mulai, user.waktu_selesai)
//        } else {
//            fetchTodaySensorData(apiService, user.id, from_date)
//        }
//    }
//
//    todayData?.forEach { sensorData ->
//        if (sensorData.previous_weight != 0.0 && sensorData.previous_weight > sensorData.weight) {
//            val tanggal = convertUtcToWIB(sensorData.created_at, includeTime = true).toString()
//            tanggalList.add(tanggal)
//
//            val waktu = convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()
//            waktuList.add(waktu)
//            waktuListToday.add(waktu)
//
//            val minum = abs(sensorData.previous_weight - sensorData.weight)
//            minumList.add(minum)
//            minumListToday.add(minum)
//
//            todayList[convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()] =
//                sensorData.previous_weight - sensorData.weight
//        }
//    }
//
//    val tanggalArray = tanggalList.toTypedArray()
//    val waktuArray = waktuList.toTypedArray()
//    val minumArray = minumList.toDoubleArray()
//    val waktuArrayToday = waktuListToday.toTypedArray()
//    val minumArrayToday = minumListToday.toDoubleArray()
//
//    val lastTime = if (waktuArrayToday.isNotEmpty()) {
//        waktuArrayToday.last()
//    } else if (waktuMulai != "")  {
//        waktuMulai
//    } else {
//        "00:00:00"
//    }
//
//    val totalAktual = minumArrayToday.sum()
//
//    if (
//        (waktuMulai != "" && waktuSelesai != "") &&
//        (today != prediksiStore.datePrediksi ||
//        totalAktual != prediksiStore.totalAktual ||
//        minumArray.last() != prediksiStore.minumAkhir ||
//        waktuMulai != prediksiStore.waktuPredMulai ||
//        waktuSelesai != prediksiStore.waktuPredSelesai)
//    ) {
//        if (
//            minumArray.size >= 4 &&
//            user.waktu_mulai != null && user.waktu_mulai != "" &&
//            user.waktu_selesai != null && user.waktu_selesai != ""
//        ){
//            val xgboost = XGBoost()
//
//            xgboost.latihModel(tanggalArray, waktuArray, minumArray, maxIterasi = 10)
//
//            val startTime = dateFormat.parse(user.waktu_mulai)
//            val endTime = dateFormat.parse(user.waktu_selesai)
//            val currentTime = dateFormat.parse(dateFormat.format(Date()))
//
//            if (
//                (
//                    startTime.time > endTime.time &&
//                    (currentTime.time > endTime.time || currentTime.time < startTime.time )
//                ) ||
//                (
//                    startTime.time < endTime.time &&
//                    (currentTime.time < endTime.time && currentTime.time > startTime.time)
//                )
//            ) {
//
//                val (prediksiAir, prediksiWaktu) = xgboost.prediksi(
//                    lastTime,
//                    waktuSelesai,
//                    tanggalArray.last(),
//                    isBedaHari
//                )!!
//
//                totalPrediksi = prediksiAir.sum() + minumArrayToday.sum()
//
//                prediksiAir.forEach { minumListPrediksi.add(it) }
//
//                var currentTime = LocalTime.parse(lastTime, timeFormatter)
//                prediksiWaktu.forEach { seconds ->
//                    currentTime = currentTime.plusSeconds(seconds.toLong())
//                    waktuListPrediksi.add(currentTime.format(timeFormatter))
//                }
//
//                waktuListPrediksi.forEachIndexed { index, waktu ->
//                    prediksiList[waktu] = minumListPrediksi[index]
//                }
//
//                UserDataStore.savePrediksi(
//                    context,
//                    prediksiStore.copy(
//                        waktuAkhir = waktuArray.last(),
//                        minumAkhir = minumArray.last(),
//                        prediksiWaktu = prediksiWaktu,
//                        prediksiMinum = prediksiAir,
//                        waktuPredMulai = waktuMulai.toString(),
//                        waktuPredSelesai = waktuSelesai.toString(),
//                        totalPrediksi = totalPrediksi,
//                        totalAktual = totalAktual,
//                        datePrediksi = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
//                    )
//                )
//            }
//        }
//    } else {
//        totalPrediksi = prediksiStore.totalPrediksi ?: 0.0
//
//        prediksiStore.prediksiMinum?.forEach { minumListPrediksi.add(it) }
//
//        var currentTime = LocalTime.parse(lastTime, timeFormatter)
//
//        prediksiStore.prediksiWaktu?.forEach { seconds ->
//            currentTime = currentTime.plusSeconds(seconds.toLong())
//
//            waktuListPrediksi.add(currentTime.format(timeFormatter))
//        }
//
//        waktuListPrediksi.forEachIndexed { index, waktu ->
//            prediksiList[waktu] = minumListPrediksi[index]
//        }
//    }
//
//    return PredictionResult(
//        todayAktual = totalAktual,
//        todayPrediksi = totalPrediksi,
//        todayList = todayList,
//        prediksiList = prediksiList,
//        statusHistory = statusHistory
//    )

//    val startDate = LocalDate.parse("2025-05-29")
//    val endDate = LocalDate.parse("2025-06-05")
    val startDate = LocalDate.parse("2025-04-02")
    val endDate = LocalDate.parse("2025-04-09")
    var totalPrediksiList = linkedMapOf<String, Double>()

    var today = startDate
    var jumlahUser = 1

    val userList = arrayOf(1, 2, 6)
    val minumPerJam = mutableMapOf<Int, MutableList<Double>>()
    val totalPerDayByUser = mutableMapOf<Int, LinkedHashMap<String, Double>>()

    val userHistoryData = mutableMapOf<Int, MutableMap<String, Any>>()
    val userMinumPerJam = mutableMapOf<Int, MutableMap<Int, MutableList<Double>>>()

    val waktuMulai = mutableMapOf<Int, String>()
    val waktuSelesai = mutableMapOf<Int, String>()

    for(user in userList){
        val tanggalList = mutableListOf<String>()
        val waktuList = mutableListOf<String>()
        val minumList = mutableListOf<Double>()
        val minumPerJam = mutableMapOf<Int, MutableList<Double>>()

        val historyData = fetchSensorDataHistory(
            apiService,
            user,
            today.format(formatter)
        )

        statusHistory = historyData != null

        if(historyData == null){
            break
        }

//        val (uwaktuMulai, uwaktuSelesai) = averageTimeOfDay(historyData, formatter)

        val (uwaktuMulai, uwaktuSelesai) = testTime(historyData)

        waktuMulai[user] = uwaktuMulai.toString()
        waktuSelesai[user] = uwaktuSelesai.toString()

        historyData?.forEach { sensorData ->
            if (
                sensorData.previous_weight != 0.0 &&
                sensorData.previous_weight > sensorData.weight
            ) {
                val tanggal = convertUtcToWIB(
                    sensorData.created_at,
                    includeTime = true
                ).toString()
                tanggalList.add(tanggal)

                val waktu =
                    convertUtcToWIB(
                        sensorData.created_at,
                        timeOnly = true
                    ).toString()
                waktuList.add(waktu)

                val minum = abs(sensorData.previous_weight - sensorData.weight)
                minumList.add(minum)

                historyList[convertUtcToWIB(
                    sensorData.created_at,
                    timeOnly = true
                ).toString()] = sensorData.previous_weight - sensorData.weight

                val timeString = convertUtcToWIB(sensorData.created_at, timeOnly = true)
                val hour = timeString?.substringBefore(":")?.toIntOrNull() ?: return@forEach

                val listMinum = minumPerJam.getOrPut(hour) { mutableListOf() }
                listMinum.add(minum)
            }
        }

        userHistoryData[user] = mutableMapOf(
            "tanggalList" to tanggalList,
            "waktuList" to waktuList,
            "minumList" to minumList
        )

        userMinumPerJam[user] = minumPerJam

//        var fromDate = "2025-05-29"
//        var toDate = "2025-06-04"
        var fromDate = "2025-04-02"
        var toDate = "2025-04-08"
        var sensorDataResponse: SensorDataResponse
        var totalPerDay = linkedMapOf<String, Double>()

        val response = apiService.getSensorData(
            fromDate = fromDate,
            toDate = toDate,
            userId = user
        )
        sensorDataResponse = response
        val groupedData = response.data.groupBy {
            convertUtcToWIB(it.created_at)?.substring(0, 10) ?: ""
        }
        totalPerDay.clear()
        groupedData.forEach { (date, dataList) ->
            val totalForDay = dataList.sumOf {
                if (it.previous_weight != 0.0 && it.previous_weight > it.weight) {
                    abs(it.previous_weight - it.weight)
                } else {
                    0.0
                }
            }
            totalPerDay[date] = totalForDay
        }

        totalPerDayByUser[user] = totalPerDay
    }

    val allTanggal = mutableListOf<String>()
    val allWaktu = mutableListOf<String>()
    val allMinum = mutableListOf<Double>()

    userHistoryData.forEach { (_, data) ->
        val tanggalList = data["tanggalList"] as? List<String> ?: emptyList()
        val waktuList = data["waktuList"] as? List<String> ?: emptyList()
        val minumList = data["minumList"] as? List<Double> ?: emptyList()

        allTanggal.addAll(tanggalList)
        allWaktu.addAll(waktuList)
        allMinum.addAll(minumList)
    }

    val combinedMinumPerJam = mutableMapOf<Int, MutableList<Double>>()

    userMinumPerJam.forEach { (_, perJamMap) ->
        perJamMap.forEach { (hour, values) ->
            val list = combinedMinumPerJam.getOrPut(hour) { mutableListOf() }
            list.addAll(values)
        }
    }

    while (jumlahUser <= 3) {
        var idUser = 0
        var uWaktuMulai = ""
        var uWaktuSelesai = ""
        println("SAMPE SINI" + jumlahUser)
//        if (jumlahUser == 1) {
//            idUser = 2
//            uWaktuMulai = "05:00:00"
//            uWaktuSelesai = "23:00:00"
//            idUser = 1
//            uWaktuMulai = "08:00:00"
//            uWaktuSelesai = "23:59:00"
//        } else if (jumlahUser == 2) {
////            idUser = 1
////            uWaktuMulai = "07:00:00"
////            uWaktuSelesai = "23:59:00"
//            idUser = 2
//            uWaktuMulai = "06:00:00"
//            uWaktuSelesai = "21:00:00"
//        } else if (jumlahUser == 3) {
//            idUser = 6
//            uWaktuMulai = "06:00:00"
//            uWaktuSelesai = "20:00:00"
//        } else {
//            break
//        }
        if (jumlahUser == 1){
            idUser = 1
        } else if (jumlahUser == 2) {
            idUser = 2
        } else if (jumlahUser == 3) {
            idUser = 6
        } else {
            break
        }

        val maxDepth = arrayOf(0, 1, 3, 5, 10)
        val allGamma = arrayOf(50.0, 200.0, 0.0)
        val allLambda = arrayOf(1.0, 0.0, 10.0, 100.0)
        val learningRates = arrayOf(0.5, 0.01, 0.3, 0.1)

//        val maxDepth = arrayOf(3, 5, 10)
//        val allGamma = arrayOf(50.0, 200.0, 0.0)
//        val allLambda = arrayOf(1.0, 0.0, 10.0, 100.0)
//        val learningRates = arrayOf(0.5, 0.01, 0.3, 0.1)

        for (depth in maxDepth) {
            for (gamma in allGamma) {
                for (lambda in allLambda) {
                    for (rate in learningRates) {

//                        val tanggalArray = tanggalList.toTypedArray()
//                        val waktuArray = waktuList.toTypedArray()
//                        val minumArray = minumList.toDoubleArray()
//                        val historyDataUser = userHistoryData[idUser]
//                        val tanggalArray = (historyDataUser?.get("tanggalList") as? MutableList<String>)?.toTypedArray()
//                        val waktuArray = (historyDataUser?.get("waktuList") as? MutableList<String>)?.toTypedArray()
//                        val minumArray = (historyDataUser?.get("minumList") as? MutableList<Double>)?.toDoubleArray()
                        val tanggalArray = allTanggal.toTypedArray()
                        val waktuArray = allWaktu.toTypedArray()
                        val minumArray = allMinum.toDoubleArray()
                        val waktuArrayToday = waktuListToday.toTypedArray()
                        val minumArrayToday = minumListToday.toDoubleArray()

//                        val minumPerJam = userMinumPerJam[idUser]!!
                        val minumPerJam = combinedMinumPerJam

                        val lastTime = if (waktuArrayToday.isNotEmpty()) {
                            waktuArrayToday.last()
//                        } else if (waktuMulai != "") {
//                            uWaktuMulai
                        } else {
                            "00:00:00"
                        }

                        val totalAktual = minumArrayToday.sum()

                        val xgboost = XGBoost(depth, gamma, lambda, rate, idUser)

                        xgboost.latihModel(
                            tanggal = tanggalArray,
                            waktu = waktuArray,
                            jumlahAir = minumArray,
                            minumPerJam = minumPerJam,
                            maxIterasi = 10
                        )

                        var dayPrediksi = today
                        var prediksiAirGlobal: DoubleArray = doubleArrayOf(0.0)
                        var prediksiWaktuGlobal: Array<Double> = arrayOf(0.0)
                        while (dayPrediksi.isBefore(endDate)) {
                            val (prediksiAir, prediksiWaktu) = xgboost.prediksi(
//                                lastTime,
//                                uWaktuSelesai.toString(),
                                waktuMulai[idUser]!!,
                                waktuSelesai[idUser]!!,
                                dayPrediksi.toString(),
                                isBedaHari
                            )!!

                            prediksiAirGlobal = prediksiAir

                            prediksiWaktuGlobal = prediksiWaktu

                            totalPrediksi = prediksiAir.sum() + minumArrayToday.sum()

                            totalPrediksiList[dayPrediksi.format(formatter)] = totalPrediksi

                            dayPrediksi = dayPrediksi.plusDays(1)
                        }

                        UserDataStore.savePrediksi(
                            context,
                            prediksiStore.copy(
                                waktuAkhir = waktuArray.last(),
                                minumAkhir = minumArray.last(),
                                prediksiWaktu = prediksiWaktuGlobal,
                                prediksiMinum = prediksiAirGlobal,
                                waktuPredMulai = waktuMulai.toString(),
                                waktuPredSelesai = waktuSelesai.toString(),
                                totalPrediksi = totalPrediksi,
                                totalAktual = totalAktual,
                                datePrediksi = SimpleDateFormat(
                                    "yyyy-MM-dd",
                                    Locale.getDefault()
                                ).format(Date())
                            )
                        )

                        val actualTotal = totalPerDayByUser[idUser]
                        if (actualTotal == null) {
                            println("Data totalPerDay tidak ditemukan untuk user $idUser, menggunakan map kosong.")
                        }

                        val evaluasi = xgboost.evaluasi(actualTotal ?: linkedMapOf(), totalPrediksiList, context)

                        simpanKeExcel(
                            context = context,
                            depth = depth,
                            gamma = gamma,
                            lambda = lambda,
                            learningRate = rate,
                            user = idUser,
                            metrics = EvaluationMetrics(evaluasi.smape, evaluasi.mae, evaluasi.rmse, evaluasi.r2)
                        )

                    }
                }
            }
        }
        jumlahUser += 1
    }

    return PredictionResult(
        todayAktual = 0.0,
        todayPrediksi = totalPrediksi,
        todayList = todayList,
        prediksiList = prediksiList,
        statusHistory = true
    )
}

fun averageTimeOfDay(sensorDataList: List<SensorData>, formatter: DateTimeFormatter): Pair<LocalTime?, LocalTime?> {
    val timesInSeconds = sensorDataList.mapNotNull { data ->
        val wibTime = convertUtcToWIB(data.created_at, timeOnly = true) ?: return@mapNotNull null
        val parts = wibTime.split(":").map { it.toIntOrNull() }
        if (parts.size >= 2 && parts[0] != null && parts[1] != null) {
            val hours = parts[0]!!
            val minutes = parts[1]!!
            val seconds = if (parts.size > 2) parts[2] ?: 0 else 0
            hours * 3600 + minutes * 60 + seconds
        } else null
    }

    if (timesInSeconds.isEmpty()) return null to null

    val sorted = timesInSeconds.sorted()
    val startAverage = sorted.take(sorted.size / 2).average()
    val endAverage = sorted.takeLast(sorted.size / 2).average()

    fun secondsToLocalTime(avg: Double): LocalTime {
        val totalSeconds = avg.toInt()
        val hour = totalSeconds / 3600
        val minute = (totalSeconds % 3600) / 60
        val second = totalSeconds % 60
        return LocalTime.of(hour, minute, second)
    }

    return secondsToLocalTime(startAverage) to secondsToLocalTime(endAverage)
}

fun modaTimeRange(waktuList: List<String>): Pair<String?, String?> {
    val jamFrekuensi = mutableMapOf<Int, Int>()

    for (waktu in waktuList) {
        val jam = waktu.substringBefore(":").toIntOrNull()
        if (jam != null) {
            jamFrekuensi[jam] = jamFrekuensi.getOrDefault(jam, 0) + 1
        }
    }

    if (jamFrekuensi.isEmpty()) return null to null

    val sortedFrekuensi = jamFrekuensi.entries.sortedByDescending { it.value }
    val jamTerbanyak = sortedFrekuensi.map { it.key }

    // Ambil rentang jam aktif berdasarkan jam dengan frekuensi tinggi
    val waktuMulai = jamTerbanyak.minOrNull()?.let { String.format("%02d:00:00", it) }
    val waktuSelesai = jamTerbanyak.maxOrNull()?.let { String.format("%02d:59:59", it) }

    return waktuMulai to waktuSelesai
}

fun testTime(
    sensorDataList: List<SensorData>
): Pair<LocalTime, LocalTime> {
    val threshold = 20.0

    val parsedData = sensorDataList.mapNotNull {
        try {
            val utcDateTime = OffsetDateTime.parse(it.created_at) // jika ISO 8601 string
            val wibDateTime = utcDateTime.atZoneSameInstant(ZoneId.of("Asia/Jakarta")).toLocalDateTime()

            Triple(wibDateTime, it.previous_weight, it.weight)
        } catch (e: Exception) {
            println("Parse error for: ${it.created_at} (${e.message})")
            null
        }
    }

    val validData = parsedData.filter {
        it.second > it.third && (it.second - it.third) > threshold
    }

    val groupedByDate = validData.groupBy { it.first.toLocalDate() }

    val startHourFrequency = mutableMapOf<Int, Int>()
    val endHourFrequency = mutableMapOf<Int, Int>()

    for ((date, entries) in groupedByDate) {
        val sorted = entries.sortedBy { it.first }
        val startHour = sorted.first().first.hour
        val endHour = sorted.last().first.hour

        startHourFrequency[startHour] = startHourFrequency.getOrDefault(startHour, 0) + 1
        endHourFrequency[endHour] = endHourFrequency.getOrDefault(endHour, 0) + 1
    }

    val mostFrequentStartHour = startHourFrequency.entries.maxByOrNull { it.value }?.key ?: 0
    val mostFrequentEndHour = endHourFrequency.entries.maxByOrNull { it.value }?.key ?: 23

    return Pair(
        LocalTime.of(mostFrequentStartHour, 0),
        LocalTime.of(mostFrequentEndHour, 0)
    )
}