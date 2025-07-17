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
import com.example.stationbottle.data.TrainingEvaluationResults
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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.example.stationbottle.data.SerializableXGBoostModel
import java.time.DayOfWeek
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

object AppPreferences {
    private val LAST_TRAINED_DATE = stringPreferencesKey("last_trained_date")
    private val MODEL_TRAINED_STATUS = booleanPreferencesKey("model_trained_status")

    suspend fun saveLastTrainedDate(context: Context, date: String) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[LAST_TRAINED_DATE] = date
        }
    }

    fun getLastTrainedDate(context: Context): Flow<String?> {
        return context.appPreferencesDataStore.data.map { preferences ->
            preferences[LAST_TRAINED_DATE]
        }
    }

    suspend fun saveModelTrainedStatus(context: Context, status: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[MODEL_TRAINED_STATUS] = status
        }
    }

    fun getModelTrainedStatus(context: Context): Flow<Boolean?> {
        return context.appPreferencesDataStore.data.map { preferences ->
            preferences[MODEL_TRAINED_STATUS]
        }
    }
}

suspend fun saveXGBoostModel(context: Context, model: XGBoost) = withContext(Dispatchers.IO) {
    val modelFileName = "xgboost_model.json"
    val file = File(context.filesDir, modelFileName)
    val jsonString = Json.encodeToString(SerializableXGBoostModel.serializer(), model.toSerializableModel())
    FileOutputStream(file).use {
        it.write(jsonString.toByteArray())
    }
    println("Model XGBoost berhasil disimpan ke $modelFileName")
}

suspend fun loadXGBoostModel(context: Context): XGBoost? = withContext(Dispatchers.IO) {
    val modelFileName = "xgboost_model.json"
    val file = File(context.filesDir, modelFileName)
    if (!file.exists()) {
        println("File model tidak ditemukan: $modelFileName")
        return@withContext null
    }
    return@withContext try {
        FileInputStream(file).use {
            val jsonString = it.readBytes().toString(Charsets.UTF_8)
            val serializableModel = Json.decodeFromString(SerializableXGBoostModel.serializer(), jsonString)
            println("Model XGBoost berhasil dimuat dari $modelFileName")
            XGBoost.fromSerializableModel(serializableModel)
        }
    } catch (e: Exception) {
        println("Error memuat model XGBoost dari file: ${e.message}")
        file.delete()
        null
    }
}

suspend fun calculatePrediction(
    context: Context,
    user: User,
    waktuMulai: String,
    waktuSelesai: String,
    todayDate: String? = null
): PredictionResult {

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val prediksiStore = getPrediksi(context).first()

    var baseDate = LocalDate.parse(todayDate, formatter)

    val idUser = user.id

    val waktuListToday = mutableListOf<String>()
    val minumListToday = mutableListOf<Double>()
    val historyList = linkedMapOf<String, Double>()
    val todayList = linkedMapOf<String, Double>()

    val tanggalListUser = mutableListOf<String>()
    val waktuListUser = mutableListOf<String>()
    val minumListUser = mutableListOf<Double>()

    var totalPrediksi: Double = 0.0
    var totalPrediksiTest: Double = 0.0

    var statusHistory: Boolean
    var isBedaHari: Boolean = false

    val userHistoryData = fetchSensorDataHistory(
        apiService,
        idUser,
        todayDate.toString()
    )

    statusHistory = userHistoryData != null

    var waktuMulaiUser: String = "00:00"
    var waktuSelesaiUser: String = "23:59"
    val minumPerJamUser = mutableMapOf<Int, MutableList<Double>>()

    if (userHistoryData != null) {
        val (uwaktuMulai, uwaktuSelesai) = testTime(userHistoryData)

        waktuMulaiUser = uwaktuMulai.toString()
        waktuSelesaiUser = uwaktuSelesai.toString()

        userHistoryData.forEach { sensorData ->
            if (
                sensorData.previous_weight != 0.0 &&
                sensorData.previous_weight > sensorData.weight
            ) {
                val tanggal = convertUtcToWIB(
                    sensorData.created_at,
                    includeTime = true
                ).toString()
                tanggalListUser.add(tanggal)

                val waktu =
                    convertUtcToWIB(
                        sensorData.created_at,
                        timeOnly = true
                    ).toString()
                waktuListUser.add(waktu)

                val minum = abs(sensorData.previous_weight - sensorData.weight)
                minumListUser.add(minum)

                historyList[convertUtcToWIB(
                    sensorData.created_at,
                    timeOnly = true
                ).toString()] = sensorData.previous_weight - sensorData.weight

                val timeString = convertUtcToWIB(sensorData.created_at, timeOnly = true)
                val hour = timeString?.substringBefore(":")?.toIntOrNull() ?: return@forEach

                val listMinum = minumPerJamUser.getOrPut(hour) { mutableListOf() }
                listMinum.add(minum)
            }
        }
    }

    var todayData: List<SensorData>? = emptyList()

    if (user.waktu_mulai != null && user.waktu_selesai != null) {
        val startTime = dateFormat.parse(user.waktu_mulai)!!
        val endTime = dateFormat.parse(user.waktu_selesai)!!
        val waktuSekarang = dateFormat.parse(dateFormat.format(Date()))!!

        var from_date = baseDate.format(formatter)
        var to_date = baseDate.plusDays(1).format(formatter)

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
            tanggalListUser.add(tanggal)

            val waktu = convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()
            waktuListUser.add(waktu)
            waktuListToday.add(waktu)

            val minum = abs(sensorData.previous_weight - sensorData.weight)
            minumListUser.add(minum)
            minumListToday.add(minum)

            todayList[convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()] =
                sensorData.previous_weight - sensorData.weight
        }
    }

    val waktuArrayToday = waktuListToday.toTypedArray()
    val minumArrayToday = minumListToday.toDoubleArray()

    var prediksiListResult = LinkedHashMap<String, Double>()

    if (
        statusHistory &&
        (waktuMulai != "" && waktuSelesai != "") &&
        (todayDate != prediksiStore.datePrediksi ||
                minumArrayToday.sum() != prediksiStore.totalAktual ||
                (minumArrayToday.lastOrNull() != null && minumArrayToday.last() != prediksiStore.minumAkhir))
//        true
    ) {
        val startDate = LocalDate.parse("2025-04-02")
        val endDate = LocalDate.parse("2025-04-09")
        var totalPrediksiList = linkedMapOf<String, Double>()
        var totalPrediksiListTest = linkedMapOf<String, Double>()

        var today = startDate

        val userList = arrayOf(1, 2, 6)
        val totalPerDayByUser = mutableMapOf<Int, LinkedHashMap<String, Double>>()
        val totalPerDayByUserTest = mutableMapOf<Int, LinkedHashMap<String, Double>>()

        val userHistoryData = mutableMapOf<Int, MutableMap<String, Any>>()
        val userMinumPerJam = mutableMapOf<Int, MutableMap<Int, MutableList<Double>>>()

        val waktuMulaiMap = mutableMapOf<Int, String>()
        val waktuSelesaiMap = mutableMapOf<Int, String>()

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

            val (uwaktuMulai, uwaktuSelesai) = testTime(historyData)

            waktuMulaiMap[user] = uwaktuMulai.toString()
            waktuSelesaiMap[user] = uwaktuSelesai.toString()

            historyData.forEach { sensorData ->
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

            var fromDateTest = "2025-05-29"
            var toDateTest = "2025-06-07"
            var sensorDataResponseTest: SensorDataResponse
            var totalPerDayTest = linkedMapOf<String, Double>()

            val responseTest = apiService.getSensorData(
                fromDate = fromDateTest,
                toDate = toDateTest,
                userId = user
            )
            sensorDataResponseTest = responseTest
            val groupedDataTest = responseTest.data.groupBy {
                convertUtcToWIB(it.created_at)?.substring(0, 10) ?: ""
            }
            totalPerDayTest.clear()
            groupedDataTest.forEach { (date, dataList) ->
                val totalForDay = dataList.sumOf {
                    if (it.previous_weight != 0.0 && it.previous_weight > it.weight) {
                        abs(it.previous_weight - it.weight)
                    } else {
                        0.0
                    }
                }
                totalPerDayTest[date] = totalForDay
            }

            totalPerDayByUserTest[user] = totalPerDayTest
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

        var bestSmapeOverall = mutableMapOf<Int, Double>()
        var bestR2Overall = mutableMapOf<Int, Double>()
        var bestDepthOverall = mutableMapOf<Int, Int>()
        var bestGammaOverall = mutableMapOf<Int, Double>()
        var bestLambdaOverall = mutableMapOf<Int, Double>()
        var bestLearningRateOverall = mutableMapOf<Int, Double>()
        var bestTrainingMetricsOverall = mutableMapOf<Int, TrainingEvaluationResults>()
        var bestPredictionMetricsOverall = mutableMapOf<Int, EvaluationMetrics>()

        val lastTrainedDate = AppPreferences.getLastTrainedDate(context).first()
        val modelTrainedStatus = AppPreferences.getModelTrainedStatus(context).first() ?: false

        var xgboost: XGBoost? = null

        val maxPatience = 3

//        val idUserList = arrayOf(1, 2, 6)
        val idUserList = arrayOf(idUser)

        val todayActualDate = LocalDate.now()

        val shouldRetrain = !modelTrainedStatus ||
                (todayActualDate.dayOfWeek == DayOfWeek.SUNDAY && lastTrainedDate != todayActualDate.format(formatter))
//        val shouldRetrain = true

        if (shouldRetrain) {
            println("Model perlu dilatih ulang atau belum dilatih. Melakukan pelatihan...")

            val maxDepth = arrayOf(0, 10, 5, 3, 1)
            val allGamma = arrayOf(200.0, 50.0, 0.0)
            val allLambda = arrayOf(100.0, 10.0, 1.0, 0.0)
            val learningRates = arrayOf(0.5, 0.3, 0.1, 0.01)

//            val maxDepth = arrayOf(3)
//            val allGamma = arrayOf(50.0)
//            val allLambda = arrayOf(10.0)
//            val learningRates = arrayOf(0.5)

            for (idUser in idUserList) {
                bestSmapeOverall[idUser] = Double.MAX_VALUE
                bestR2Overall[idUser] = -Double.MAX_VALUE
                bestDepthOverall[idUser] = 0
                bestGammaOverall[idUser] = 0.0
                bestLambdaOverall[idUser] = 0.0
                bestLearningRateOverall[idUser] = 0.0
                bestTrainingMetricsOverall[idUser] = TrainingEvaluationResults(EvaluationMetrics(0.0,0.0,0.0,0.0), EvaluationMetrics(0.0,0.0,0.0,0.0))
                bestPredictionMetricsOverall[idUser] = EvaluationMetrics(0.0, 0.0, 0.0, 0.0)
            }

            val tanggalArray = allTanggal.toTypedArray()
            val waktuArray = allWaktu.toTypedArray()
            val minumArray = allMinum.toDoubleArray()

            for (depth in maxDepth) {

                for (gamma in allGamma) {

                    for (lambda in allLambda) {

                        var currentRatePatience = 0
                        var bestSmapeForCurrentRate = Double.MAX_VALUE

                        for (rate in learningRates) {

                            val currentXGBoost = XGBoost(depth, gamma, lambda, rate)

                            val trainingMetrics = currentXGBoost.latihModel(
                                tanggal = tanggalArray,
                                waktu = waktuArray,
                                jumlahAir = minumArray,
                                minumPerJam = combinedMinumPerJam,
                                maxIterasi = 10
                            )

                            totalPrediksiList.clear()

                            for (idUser in idUserList){
                                var dayPrediksi = today
                                var prediksiAirGlobal: DoubleArray = doubleArrayOf(0.0)
                                var prediksiWaktuGlobal: Array<Double> = arrayOf(0.0)
                                while (dayPrediksi.isBefore(endDate)) {
                                    val (prediksiAir, prediksiWaktu) = currentXGBoost.prediksi(
                                        waktuMulaiMap[idUser]!!,
                                        waktuSelesaiMap[idUser]!!,
                                        dayPrediksi.toString(),
                                        isBedaHari
                                    )!!

                                    prediksiAirGlobal = prediksiAir

                                    prediksiWaktuGlobal = prediksiWaktu

                                    totalPrediksi = prediksiAir.sum()

                                    totalPrediksiList[dayPrediksi.format(formatter)] = totalPrediksi

                                    dayPrediksi = dayPrediksi.plusDays(1)
                                }

                                val actualTotal = totalPerDayByUser[idUser]
                                if (actualTotal == null) {
                                    println("Data totalPerDay tidak ditemukan untuk user $idUser, menggunakan map kosong.")
                                }

                                val evaluasi = currentXGBoost.evaluasi(actualTotal ?: linkedMapOf(), totalPrediksiList)

                                println("Evaluasi Data:")
                                println("   max_depth: $depth | gamma: $gamma | lambda: $lambda | learning_rate: $rate | user: $idUser")
                                println("   MAE: ${evaluasi.mae} | RMSE: ${evaluasi.rmse} | SMAPE: ${evaluasi.smape} | R2: ${evaluasi.r2}")

                                val currentSmape = evaluasi.smape
                                val currentR2 = evaluasi.r2

                                if (currentR2 < -1.0) {
                                    trainingMetrics?.let {
                                        println("  Training Metrics (Air): MAE=${it.airMetrics.mae} | RMSE=${it.airMetrics.rmse} | SMAPE=${it.airMetrics.smape} | R2=${it.airMetrics.r2}")
                                        println("  Training Metrics (Waktu): MAE=${it.waktuMetrics.mae} | RMSE=${it.waktuMetrics.rmse} | SMAPE=${it.waktuMetrics.smape} | R2=${it.waktuMetrics.r2}")
                                    }
                                    println("Skip: R2 ($currentR2) terlalu kecil (< -1.0) untuk depth=$depth, gamma=$gamma, lambda=$lambda, rate=$rate")
                                    continue
                                }

                                if (currentSmape < (bestSmapeOverall[idUser]
                                        ?: 0.0) || currentR2 > (bestR2Overall[idUser] ?: 0.0)
                                ) {
                                    bestSmapeOverall[idUser] = currentSmape
                                    bestR2Overall[idUser] = currentR2
                                    bestDepthOverall[idUser] = depth
                                    bestGammaOverall[idUser] = gamma
                                    bestLambdaOverall[idUser] = lambda
                                    bestLearningRateOverall[idUser] = rate
                                    trainingMetrics?.let {
                                        bestTrainingMetricsOverall[idUser] = it
                                    }
                                    bestPredictionMetricsOverall[idUser] = evaluasi

                                    currentRatePatience = 0

                                    println("BEST MODEL FOUND FOR USER $idUser (depth=$depth, gamma=$gamma, lambda=$lambda, rate=$rate):")
                                }

                                if (currentSmape < bestSmapeForCurrentRate) {
                                    bestSmapeForCurrentRate = currentSmape
                                    currentRatePatience = 0
                                } else {
                                    currentRatePatience++
                                }

                                val fitur = "timeDayInteraction|jamFreqZScoreDecay(14)|hourDayInteraction"

                                simpanKeExcel(
                                    name = "Evaluasi_Eval",
                                    context = context,
                                    depth = depth,
                                    gamma = gamma,
                                    lambda = lambda,
                                    learningRate = rate,
                                    user = idUser,
                                    metrics = EvaluationMetrics(evaluasi.smape, evaluasi.mae, evaluasi.rmse, evaluasi.r2),
                                    fitur = fitur,
                                    jenis = "Evaluation Data"
                                )

                                var dayPrediksiTest = LocalDate.parse("2025-05-29")
                                var prediksiAirGlobalTest: DoubleArray = doubleArrayOf(0.0)
                                var prediksiWaktuGlobalTest: Array<Double> = arrayOf(0.0)

                                totalPrediksiListTest.clear()

                                while (dayPrediksiTest.isBefore(LocalDate.parse("2025-06-08"))) {
                                    val (prediksiAirTest, prediksiWaktuTest) = currentXGBoost.prediksi(
                                        waktuMulaiMap[idUser]!!,
                                        waktuSelesaiMap[idUser]!!,
                                        dayPrediksiTest.toString(),
                                        isBedaHari
                                    )!!

                                    prediksiAirGlobalTest = prediksiAirTest

                                    prediksiWaktuGlobalTest = prediksiWaktuTest

                                    totalPrediksiTest = prediksiAirTest.sum()

                                    totalPrediksiListTest[dayPrediksiTest.format(formatter)] = totalPrediksiTest

                                    dayPrediksiTest = dayPrediksiTest.plusDays(1)
                                }

                                val actualTotalTest = totalPerDayByUserTest[idUser]
                                if (actualTotalTest == null) {
                                    println("Data totalPerDay tidak ditemukan untuk user $idUser, menggunakan map kosong.")
                                }

                                val evaluasiTest = currentXGBoost.evaluasi(actualTotalTest ?: linkedMapOf(), totalPrediksiListTest)

                                println("Test Data:")
                                println("   max_depth: $depth | gamma: $gamma | lambda: $lambda | learning_rate: $rate | user: $idUser")
                                println("   MAE= ${evaluasiTest.mae} | RMSE= ${evaluasiTest.rmse} | SMAPE= ${evaluasiTest.smape} | R2= ${evaluasiTest.r2}")

                                simpanKeExcel(
                                    name = "Evaluasi_Uji",
                                    context = context,
                                    depth = depth,
                                    gamma = gamma,
                                    lambda = lambda,
                                    learningRate = rate,
                                    user = idUser,
                                    metrics = EvaluationMetrics(evaluasiTest.smape, evaluasiTest.mae, evaluasiTest.rmse, evaluasiTest.r2),
                                    fitur = fitur,
                                    jenis = "Test Data"
                                )

                                if (currentRatePatience >= maxPatience) {
                                    println("Skipping remaining rates for depth=$depth, gamma=$gamma, lambda=$lambda due to lack of improvement (Patience limit reached for Rates).")
                                    break
                                }

                            }

                        }

                    }

                }

            }

            val finalDepth = bestDepthOverall[idUser] ?: 5
            val finalGamma = bestGammaOverall[idUser] ?: 50.0
            val finalLambda = bestLambdaOverall[idUser] ?: 0.0
            val finalRate = bestLearningRateOverall[idUser] ?: 0.3

            xgboost = XGBoost(finalDepth, finalGamma, finalLambda, finalRate)
            xgboost.latihModel(
                tanggal = tanggalArray,
                waktu = waktuArray,
                jumlahAir = minumArray,
                minumPerJam = combinedMinumPerJam,
                maxIterasi = 10
            )


            saveXGBoostModel(context, xgboost)

            AppPreferences.saveLastTrainedDate(context, todayActualDate.format(formatter))
            AppPreferences.saveModelTrainedStatus(context, true)
        } else {
            println("Model sudah dilatih dan tidak perlu dilatih ulang. Memuat model dari penyimpanan...")
            xgboost = loadXGBoostModel(context)
            if (xgboost == null) {
                println("Gagal memuat model dari file, melatih ulang sebagai fallback.")

                val defaultDepth = bestDepthOverall[idUser] ?: 5
                val defaultGamma = bestGammaOverall[idUser] ?: 50.0
                val defaultLambda = bestLambdaOverall[idUser] ?: 0.0
                val defaultRate = bestLearningRateOverall[idUser] ?: 0.3

                xgboost = XGBoost(defaultDepth, defaultGamma, defaultLambda, defaultRate)
                xgboost.latihModel(
                    tanggal = allTanggal.toTypedArray(),
                    waktu = allWaktu.toTypedArray(),
                    jumlahAir = allMinum.toDoubleArray(),
                    minumPerJam = combinedMinumPerJam,
                    maxIterasi = 10
                )
                saveXGBoostModel(context, xgboost)
                AppPreferences.saveLastTrainedDate(context, todayActualDate.format(formatter))
                AppPreferences.saveModelTrainedStatus(context, true)
            }
        }

        xgboost.printFeatureImportanceWithFrequency()

        if (xgboost == null) {
            println("Model XGBoost tidak tersedia setelah semua upaya (pelatihan/pemuatan). Mengembalikan hasil kosong.")
            return PredictionResult(0.0, 0.0, linkedMapOf(), linkedMapOf(), false)
        }

        var waktuPred = waktuMulaiMap[idUser]!!
        if(todayList.isNotEmpty()){
            waktuPred = waktuArrayToday.last().substring(0, 5)
        }

        var dayPrediksi = baseDate.format(formatter)
        var prediksiAirGlobal: DoubleArray = doubleArrayOf(0.0)
        var prediksiWaktuGlobal: Array<Double> = arrayOf(0.0)

        val (prediksiAir, prediksiWaktu) = xgboost.prediksi(
            waktuPred,
            waktuSelesaiMap[idUser]!!,
            dayPrediksi.toString(),
            isBedaHari
        )!!

        prediksiAirGlobal = prediksiAir

        prediksiWaktuGlobal = prediksiWaktu

        totalPrediksi = prediksiAir.sum() + minumArrayToday.sum()

        val waktuPrediksiList = mutableListOf<String>()

        val formatterPrediksi = if (waktuMulaiMap[idUser]?.count { it == ':' } == 2) {
            DateTimeFormatter.ofPattern("HH:mm:ss")
        } else {
            DateTimeFormatter.ofPattern("HH:mm")
        }

        val waktuMulaiTime = LocalTime.parse(waktuPred, formatterPrediksi)
        var totalDetik = waktuMulaiTime.toSecondOfDay()

        for (delta in prediksiWaktu) {
            totalDetik += delta.toInt()
            val jam = totalDetik / 3600
            val menit = (totalDetik % 3600) / 60
            val detik = totalDetik % 60
            waktuPrediksiList.add(String.format("%02d:%02d:%02d", jam, menit, detik))
        }

        prediksiListResult = waktuPrediksiList.zip(prediksiAir.asList())
            .toMap(LinkedHashMap())

        UserDataStore.savePrediksi(
            context,
            prediksiStore.copy(
                waktuAkhir = waktuArrayToday.lastOrNull() ?: todayDate!!,
                minumAkhir = minumArrayToday.lastOrNull() ?: 0.0,
                prediksiWaktu = prediksiWaktu,
                prediksiMinum = prediksiAir,
                waktuPredMulai = waktuMulai.toString(),
                waktuPredSelesai = waktuSelesai.toString(),
                totalPrediksi = totalPrediksi,
                totalAktual = minumArrayToday.sum(),
                datePrediksi = baseDate.toString()
            )
        )
    } else {
        totalPrediksi = prediksiStore.totalPrediksi ?: 0.0

        val prediksiAirSum = prediksiStore.prediksiMinum?.sum() ?: 0.0

        totalPrediksi = prediksiAirSum + minumArrayToday.sum()

        val waktuPrediksiList = mutableListOf<String>()

        val formatterPrediksi = if (waktuMulaiUser.count { it == ':' } == 2) {
            DateTimeFormatter.ofPattern("HH:mm:ss")
        } else {
            DateTimeFormatter.ofPattern("HH:mm")
        }

        var waktuPred = waktuMulaiUser
        if(todayList.isNotEmpty()){
            waktuPred = waktuArrayToday.last().substring(0, 5)
        }

        val waktuMulaiTime = LocalTime.parse(waktuPred, formatterPrediksi)
        var totalDetik = waktuMulaiTime.toSecondOfDay()

        for (delta in prediksiStore.prediksiWaktu!!) {
            totalDetik += delta.toInt()
            val jam = totalDetik / 3600
            val menit = (totalDetik % 3600) / 60
            val detik = totalDetik % 60
            waktuPrediksiList.add(String.format("%02d:%02d:%02d", jam, menit, detik))
        }

        prediksiListResult = waktuPrediksiList.zip(prediksiStore.prediksiMinum!!.asList())
            .toMap(LinkedHashMap())
    }

    return PredictionResult(
        todayAktual = minumArrayToday.sum(),
        todayPrediksi = totalPrediksi,
        todayList = todayList,
        prediksiList = prediksiListResult,
        statusHistory = statusHistory
    )
}

fun testTime(
    sensorDataList: List<SensorData>
): Pair<LocalTime, LocalTime> {
    val threshold = 20.0

    val parsedData = sensorDataList.mapNotNull {
        try {
            val utcDateTime = OffsetDateTime.parse(it.created_at)
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