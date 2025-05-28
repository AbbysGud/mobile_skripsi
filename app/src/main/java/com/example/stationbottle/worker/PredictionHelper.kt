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

    val startDate = LocalDate.parse("2025-04-02")
    val endDate = LocalDate.parse("2025-04-09")
    var totalPrediksiList = linkedMapOf<String, Double>()

    var today = startDate
    var jumlahUser = 1

    val userList = arrayOf(1, 2, 6)
    val minumPerJam = mutableMapOf<Int, MutableList<Double>>()

    for(user in userList){
        val historyData = fetchSensorDataHistory(
            apiService,
            user,
            today.format(formatter)
        )

        statusHistory = historyData != null

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
    }

    while (jumlahUser <= 3) {
        var idUser = 0
        var uWaktuMulai = ""
        var uWaktuSelesai = ""
        println("SAMPE SINI" + jumlahUser)
        if (jumlahUser == 1) {
//            idUser = 2
//            uWaktuMulai = "05:00:00"
//            uWaktuSelesai = "23:00:00"
            idUser = 1
            uWaktuMulai = "07:00:00"
            uWaktuSelesai = "23:59:00"
        } else if (jumlahUser == 2) {
//            idUser = 1
//            uWaktuMulai = "07:00:00"
//            uWaktuSelesai = "23:59:00"
            idUser = 2
            uWaktuMulai = "05:00:00"
            uWaktuSelesai = "23:00:00"
        } else if (jumlahUser == 3) {
            idUser = 6
            uWaktuMulai = "05:00:00"
            uWaktuSelesai = "21:00:00"
        } else {
            break
        }

        val maxDepth = arrayOf(0, 1, 3, 5, 10)
        val allGamma = arrayOf(50.0, 200.0, 0.0)
        val allLambda = arrayOf(1.0, 0.0, 10.0, 100.0)
        val learningRates = arrayOf(0.5, 0.01, 0.3, 0.1)

        for (depth in maxDepth) {
            for (gamma in allGamma) {
                for (lambda in allLambda) {
                    for (rate in learningRates) {
                        today = startDate
                        totalPrediksiList.clear()
                        while (today.isBefore(endDate)) {
                            tanggalList.clear()
                            waktuList.clear()
                            waktuListToday.clear()
                            waktuListPrediksi.clear()
                            minumList.clear()
                            minumListToday.clear()
                            minumListPrediksi.clear()
                            historyList.clear()
                            todayList.clear()
                            prediksiList.clear()
                            totalPrediksi = 0.0
                            isBedaHari = false

//                            val historyData = fetchSensorDataHistory(
//                                apiService,
//                                idUser,
//                                today.format(formatter)
//                            )
//
//                            statusHistory = historyData != null
//                            val minumPerJam = mutableMapOf<Int, MutableList<Double>>()
//
//                            historyData?.forEach { sensorData ->
//                                if (
//                                    sensorData.previous_weight != 0.0 &&
//                                    sensorData.previous_weight > sensorData.weight
//                                ) {
//                                    val tanggal = convertUtcToWIB(
//                                        sensorData.created_at,
//                                        includeTime = true
//                                    ).toString()
//                                    tanggalList.add(tanggal)
//
//                                    val waktu =
//                                        convertUtcToWIB(
//                                            sensorData.created_at,
//                                            timeOnly = true
//                                        ).toString()
//                                    waktuList.add(waktu)
//
//                                    val minum = abs(sensorData.previous_weight - sensorData.weight)
//                                    minumList.add(minum)
//
//                                    historyList[convertUtcToWIB(
//                                        sensorData.created_at,
//                                        timeOnly = true
//                                    ).toString()] = sensorData.previous_weight - sensorData.weight
//
//                                    val timeString = convertUtcToWIB(sensorData.created_at, timeOnly = true)
//                                    val hour = timeString?.substringBefore(":")?.toIntOrNull() ?: return@forEach
//
//                                    val listMinum = minumPerJam.getOrPut(hour) { mutableListOf() }
//                                    listMinum.add(minum)
//                                }
//                            }

                            var todayData: List<SensorData>? = emptyList()

                            if (user.waktu_mulai != null && user.waktu_selesai != null) {
                                val startTime = dateFormat.parse(user.waktu_mulai)!!
                                val endTime = dateFormat.parse(user.waktu_selesai)!!
                                val waktuSekarang = dateFormat.parse(dateFormat.format(Date()))!!

//        var from_date = LocalDate.now().format(formatter)
//        var to_date = LocalDate.now().plusDays(1).format(formatter)
                                var from_date = LocalDate.parse("2025-04-01").format(formatter)
                                var to_date = LocalDate.parse("2025-04-02").format(formatter)

                                if (startTime.time > endTime.time) {
                                    isBedaHari = true
                                    if (waktuSekarang.time < endTime.time) {
                                        isBedaHari = false
                                        from_date = LocalDate.now().minusDays(1).format(formatter)
                                        to_date = LocalDate.now().format(formatter)
                                    }
                                }

                                todayData = null

//                            todayData = if (startTime.time > endTime.time) {
//                                fetchTodaySensorData(
//                                    apiService,
//                                    idUser,
//                                    from_date,
//                                    to_date,
//                                    user.waktu_mulai,
//                                    user.waktu_selesai
//                                )
//                            } else {
//                                fetchTodaySensorData(apiService, idUser, from_date)
//                            }
                            }

                            todayData?.forEach { sensorData ->
                                if (sensorData.previous_weight != 0.0 && sensorData.previous_weight > sensorData.weight) {
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
                                    waktuListToday.add(waktu)

                                    val minum = abs(sensorData.previous_weight - sensorData.weight)
                                    minumList.add(minum)
                                    minumListToday.add(minum)

                                    todayList[convertUtcToWIB(
                                        sensorData.created_at,
                                        timeOnly = true
                                    ).toString()] =
                                        sensorData.previous_weight - sensorData.weight
                                }
                            }

                            val tanggalArray = tanggalList.toTypedArray()
                            val waktuArray = waktuList.toTypedArray()
                            val minumArray = minumList.toDoubleArray()
                            val waktuArrayToday = waktuListToday.toTypedArray()
                            val minumArrayToday = minumListToday.toDoubleArray()

                            val lastTime = if (waktuArrayToday.isNotEmpty()) {
                                waktuArrayToday.last()
                            } else if (waktuMulai != "") {
                                uWaktuMulai
                            } else {
                                "00:00:00"
                            }

                            val totalAktual = minumArrayToday.sum()

                            if (
                                (waktuMulai != "" && waktuSelesai != "") &&
                                (today.format(formatter) != prediksiStore.datePrediksi ||
                                        totalAktual != prediksiStore.totalAktual ||
                                        minumArray.last() != prediksiStore.minumAkhir ||
                                        waktuMulai != prediksiStore.waktuPredMulai ||
                                        waktuSelesai != prediksiStore.waktuPredSelesai)
                            ) {
                                if (
                                    minumArray.size >= 4 &&
                                    user.waktu_mulai != null && user.waktu_mulai != "" &&
                                    user.waktu_selesai != null && user.waktu_selesai != ""
                                ) {
                                    val xgboost = XGBoost(depth, gamma, lambda, rate, idUser)

                                    xgboost.latihModel(
                                        tanggal = tanggalArray,
                                        waktu = waktuArray,
                                        jumlahAir = minumArray,
                                        minumPerJam = minumPerJam,
                                        maxIterasi = 10
                                    )

                                    val startTime = dateFormat.parse(user.waktu_mulai)
                                    val endTime = dateFormat.parse(user.waktu_selesai)
                                    val currentTime = dateFormat.parse(dateFormat.format(Date()))

                                    if (
                                            (
                                                startTime.time > endTime.time &&
                                                (
                                                    currentTime.time > endTime.time ||
                                                    currentTime.time < startTime.time
                                                )
                                            ) || (
                                                startTime.time < endTime.time
                                            )
//                        ) || (
//                            startTime.time < endTime.time &&
//                                (currentTime.time < endTime.time && currentTime.time > startTime.time)
//                        )
                                    ) {

                                        val (prediksiAir, prediksiWaktu) = xgboost.prediksi(
                                            lastTime,
                                            uWaktuSelesai.toString(),
                                            tanggalArray.last(),
                                            isBedaHari
                                        )!!

                                        totalPrediksi = prediksiAir.sum() + minumArrayToday.sum()

                                        prediksiAir.forEach { minumListPrediksi.add(it) }

                                        var currentTime = LocalTime.parse(lastTime, timeFormatter)
                                        prediksiWaktu.forEach { seconds ->
                                            currentTime = currentTime.plusSeconds(seconds.toLong())
                                            waktuListPrediksi.add(currentTime.format(timeFormatter))
                                        }

                                        waktuListPrediksi.forEachIndexed { index, waktu ->
                                            prediksiList[waktu] = minumListPrediksi[index]
                                        }

                                        UserDataStore.savePrediksi(
                                            context,
                                            prediksiStore.copy(
                                                waktuAkhir = waktuArray.last(),
                                                minumAkhir = minumArray.last(),
                                                prediksiWaktu = prediksiWaktu,
                                                prediksiMinum = prediksiAir,
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
                                    }
                                }
                            } else {
                                totalPrediksi = prediksiStore.totalPrediksi ?: 0.0

                                prediksiStore.prediksiMinum?.forEach { minumListPrediksi.add(it) }

                                var currentTime = LocalTime.parse(lastTime, timeFormatter)

                                prediksiStore.prediksiWaktu?.forEach { seconds ->
                                    currentTime = currentTime.plusSeconds(seconds.toLong())

                                    waktuListPrediksi.add(currentTime.format(timeFormatter))
                                }

                                waktuListPrediksi.forEachIndexed { index, waktu ->
                                    prediksiList[waktu] = minumListPrediksi[index]
                                }
                            }

                            totalPrediksiList[today.format(formatter)] = totalPrediksi

                            today = today.plusDays(1)
                        }

                        var fromDate = "2025-04-02"
                        var toDate = "2025-04-08"
                        var sensorDataResponse: SensorDataResponse
                        var totalPerDay = linkedMapOf<String, Double>()

                        val response = apiService.getSensorData(
                            fromDate = fromDate,
                            toDate = toDate,
                            userId = idUser
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

                        val xgboost = XGBoost(depth, gamma, lambda, rate, idUser)
                        val evaluasi = xgboost.evaluasi(totalPerDay, totalPrediksiList, context)

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