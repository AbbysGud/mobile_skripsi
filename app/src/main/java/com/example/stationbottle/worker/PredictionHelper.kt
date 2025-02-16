package com.example.stationbottle.worker

import android.content.Context
import com.example.stationbottle.data.XGBoost
import com.example.stationbottle.data.fetchSensorDataHistory
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.example.stationbottle.client.RetrofitClient.apiService
import com.example.stationbottle.data.PredictionResult
import com.example.stationbottle.data.SensorData
import com.example.stationbottle.data.User
import com.example.stationbottle.data.UserDataStore
import com.example.stationbottle.data.UserDataStore.getPrediksi
import com.example.stationbottle.data.fetchTodaySensorData
import com.example.stationbottle.service.convertUtcToWIB
import kotlinx.coroutines.flow.first
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

    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    val historyData = fetchSensorDataHistory(apiService, user.id, today)
    statusHistory = historyData != null
    historyData?.forEach { sensorData ->
        if (sensorData.previous_weight != 0.0 && sensorData.previous_weight > sensorData.weight) {
            val tanggal = convertUtcToWIB(sensorData.created_at, includeTime = true).toString()
            tanggalList.add(tanggal)

            val waktu = convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()
            waktuList.add(waktu)

            val minum = abs(sensorData.previous_weight - sensorData.weight)
            minumList.add(minum)

            historyList[convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()] =
                sensorData.previous_weight - sensorData.weight
        }
    }

    var todayData: List<SensorData>? = emptyList()

    if (user.waktu_mulai != null && user.waktu_selesai != null) {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val startTime = dateFormat.parse(user.waktu_mulai)!!
        val endTime = dateFormat.parse(user.waktu_selesai)!!
        val waktuSekarang = dateFormat.parse(dateFormat.format(Date()))!!

        var from_date = LocalDate.now().format(formatter)
        var to_date = LocalDate.now().plusDays(1).format(formatter)

        if(startTime.time > endTime.time){
            isBedaHari = true
            if(waktuSekarang.time < endTime.time){
                isBedaHari = false
                from_date = LocalDate.now().minusDays(1).format(formatter)
                to_date = LocalDate.now().format(formatter)
            }
        }

        todayData = if (startTime.time > endTime.time) {
            fetchTodaySensorData(apiService, user.id, from_date, to_date, user.waktu_mulai, user.waktu_selesai)
        } else {
            fetchTodaySensorData(apiService, user.id, from_date)
        }
    }

    todayData?.forEach { sensorData ->
        if (sensorData.previous_weight != 0.0 && sensorData.previous_weight > sensorData.weight) {
            val tanggal = convertUtcToWIB(sensorData.created_at, includeTime = true).toString()
            tanggalList.add(tanggal)

            val waktu = convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()
            waktuList.add(waktu)
            waktuListToday.add(waktu)

            val minum = abs(sensorData.previous_weight - sensorData.weight)
            minumList.add(minum)
            minumListToday.add(minum)

            todayList[convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()] =
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
    } else if (waktuMulai != "")  {
        waktuMulai
    } else {
        "00:00:00"
    }

    val totalAktual = minumArrayToday.sum()

    if (
        (waktuMulai != "" && waktuSelesai != "") &&
        (today != prediksiStore.datePrediksi ||
        totalAktual != prediksiStore.totalAktual ||
        minumArray.last() != prediksiStore.minumAkhir ||
        waktuMulai != prediksiStore.waktuPredMulai ||
        waktuSelesai != prediksiStore.waktuPredSelesai)
    ) {
        if (minumArray.size >= 4){
            val xgboost = XGBoost()

            xgboost.latihModel(tanggalArray, waktuArray, minumArray, maxIterasi = 10)

            val (prediksiAir, prediksiWaktu) = xgboost.prediksi(
                lastTime,
                waktuSelesai,
                tanggalArray.last(),
                isBedaHari
            )!!

            totalPrediksi = prediksiAir.sum() + minumArrayToday.sum()

            prediksiAir.forEach { minumListPrediksi.add(it) }

            var currentTime = LocalTime.parse(lastTime, formatter)
            prediksiWaktu.forEach { seconds ->
                currentTime = currentTime.plusSeconds(seconds.toLong())
                waktuListPrediksi.add(currentTime.format(formatter))
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
                    datePrediksi = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                )
            )
        }
    } else {
        totalPrediksi = prediksiStore.totalPrediksi ?: 0.0

        prediksiStore.prediksiMinum?.forEach { minumListPrediksi.add(it) }

        var currentTime = LocalTime.parse(lastTime, formatter)

        prediksiStore.prediksiWaktu?.forEach { seconds ->
            currentTime = currentTime.plusSeconds(seconds.toLong())

            waktuListPrediksi.add(currentTime.format(formatter))
        }

        waktuListPrediksi.forEachIndexed { index, waktu ->
            prediksiList[waktu] = minumListPrediksi[index]
        }
    }

    return PredictionResult(
        todayAktual = totalAktual,
        todayPrediksi = totalPrediksi,
        todayList = todayList,
        prediksiList = prediksiList,
        statusHistory = statusHistory
    )
}