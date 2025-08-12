package com.example.stationbottle.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stationbottle.R
import com.example.stationbottle.client.RetrofitClient.apiService
import com.example.stationbottle.data.CuacaItem
import com.example.stationbottle.data.HistoryWeeklyEntry
import com.example.stationbottle.data.PredictionResult
import com.example.stationbottle.data.SensorData
import com.example.stationbottle.data.SuhuResponse
import com.example.stationbottle.data.UserDataStore.getWeeklyHistory
import com.example.stationbottle.data.UserDataStore.getWeeklyPrediction
import com.example.stationbottle.data.UserDataStore.saveWeeklyHistory
import com.example.stationbottle.data.UserDataStore.saveWeeklyPrediction
import com.example.stationbottle.data.WeeklyPredictionEntry
import com.example.stationbottle.data.fetchBMKGWeatherData
import com.example.stationbottle.data.fetchSensorDataHistory
import com.example.stationbottle.models.SensorViewModel
import com.example.stationbottle.models.UserViewModel
import com.example.stationbottle.models.WilayahViewModel
import com.example.stationbottle.service.convertUtcToWIB
import com.example.stationbottle.ui.screens.component.MPLineChartForDailyIntake
import com.example.stationbottle.worker.RecommendationSchedule
import com.example.stationbottle.worker.calculatePrediction
import com.example.stationbottle.worker.predictWholeDay
import com.example.stationbottle.worker.scheduleDrinkingReminders
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.SortedMap
import kotlin.math.roundToInt


const val OUTDOOR_HOT_TEMP_THRESHOLD = 31 // °C
const val OUTDOOR_VERY_HOT_TEMP_THRESHOLD = 32 // °C
const val INDOOR_COOL_TEMP_THRESHOLD = 24 // °C
const val INDOOR_HOT_TEMP_THRESHOLD = 27 // °C
const val OUTDOOR_LOW_HUMIDITY_THRESHOLD = 65 // %
const val OUTDOOR_HIGH_HUMIDITY_THRESHOLD = 85 // %
const val INDOOR_LOW_HUMIDITY_THRESHOLD = 50 // %
const val INDOOR_HIGH_HUMIDITY_THRESHOLD = 70 // %


@Composable
fun InsightScreen(){
    var isLoading by remember { mutableStateOf(true) }

    val context = LocalContext.current

    val userViewModel = UserViewModel()
    val wilayahViewModel = WilayahViewModel()
    val sensorViewModel = SensorViewModel()
    val userState = userViewModel.getUser(context).collectAsState(initial = null)
    val user = userState.value
    val token = user?.token

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    val todayDate = LocalDate.now().toString()

    var showDialog by remember { mutableStateOf(false) }
    var selectedTip by remember { mutableStateOf<InsightItem?>(null) }

    var kodeLengkap by remember { mutableStateOf<String>("") }
    var userDeviceId by remember { mutableStateOf<String>("") }
    var lastSuhu by remember { mutableStateOf<SuhuResponse?>(null) }

    val weatherForecastList = remember { mutableStateListOf<CuacaItem>() }

    var insightTips = remember { mutableStateListOf<InsightItem>() }

    var todayList by remember { mutableStateOf(linkedMapOf<String, Double>()) }
    var prediksiList by remember { mutableStateOf(linkedMapOf<String, Double>()) }
    var prediksiListWhole by remember { mutableStateOf(linkedMapOf<String, Double>()) }
    var sessionList by remember { mutableStateOf<MutableList<Triple<String, String, Double>?>?>(null) }

    var totalAktual by remember { mutableDoubleStateOf(0.0) }
    var totalPrediksi by remember { mutableDoubleStateOf(0.0) }
    var totalPrediksiWhole by remember { mutableDoubleStateOf(0.0) }

    var userWaktuMulai by remember { mutableStateOf<LocalTime?>(null) }
    var userWaktuSelesai by remember { mutableStateOf<LocalTime?>(null) }

    var dailyGoal by remember { mutableDoubleStateOf(0.0) }
    var waktuMulai by remember { mutableStateOf<LocalTime?>(null) }
    var waktuSelesai by remember { mutableStateOf<LocalTime?>(null) }
    var statusHistory by remember { mutableStateOf<Boolean?>(null) }

    var hasilPred by remember { mutableStateOf<PredictionResult?>(null) }

    var dailyIntakeLast7DaysFromApi by remember { mutableStateOf<SortedMap<String, Double>>(sortedMapOf()) }
    var predictedDailyIntakeNext7Days by remember { mutableStateOf<SortedMap<String, Double>>(sortedMapOf()) }

    var averageDrinkIntervalHours by remember { mutableDoubleStateOf(0.0) }
    var nextPredictedDrinkTimeHours by remember { mutableDoubleStateOf(0.0) }

    var globalMaxChartValue by remember { mutableDoubleStateOf(0.0) }

    var drinkFrequency by remember { mutableIntStateOf(0) }
    var avgDrinkVolume by remember { mutableDoubleStateOf(0.0) }
    var drinkTimes by remember { mutableStateOf<List<LocalTime>?>(null) }

    var comparisonInsightLocal by remember { mutableStateOf<InsightItem?>(null) }
    var durasiSisaGlobal by remember { mutableStateOf<Duration?>(null) }

    var latestBottleFillData by remember { mutableStateOf<SensorData?>(null) }
    var latestDrinkData by remember { mutableStateOf<SensorData?>(null) }
    var previousDrinkData by remember { mutableStateOf<SensorData?>(null) }

    var statusHidrasi by remember { mutableStateOf<String?>(null) }
    var syaratHistory by remember { mutableStateOf<Boolean>(false) }

    var hydrationAdjustment by remember { mutableDoubleStateOf(0.0) }
    var adjustmentMessage by remember { mutableStateOf("") }

    val planOptions = mutableListOf<RecommendationOption>()

    LaunchedEffect(user) {
        user?.let { currentUser ->
            isLoading = true

            userViewModel.getUserData(context, currentUser.id, token.toString())

            val latestResponse = sensorViewModel.getLastDrinkEvent(currentUser.id)

            if (latestResponse != null) {
                latestBottleFillData = latestResponse.last_bottle_fill_or_new
                latestDrinkData = latestResponse.last_drink_event
                previousDrinkData = latestResponse.previous_drink_event
            }

            dailyGoal = currentUser.daily_goal ?: 0.0
            waktuMulai = currentUser.waktu_mulai?.let { timeStr -> LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm:ss")) }
            waktuSelesai = currentUser.waktu_selesai?.let { timeStr -> LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm:ss")) }

            if (currentUser.id_kelurahan.isNullOrEmpty() || currentUser.device_id.isNullOrEmpty()) {
                Toast.makeText(
                    context,
                    "Lengkapi profil Anda: isi Kelurahan dan Device ID agar dapat info suhu",
                    Toast.LENGTH_LONG
                ).show()
            }

            if (waktuMulai != null && waktuSelesai != null && dailyGoal > 0) {
                hasilPred = calculatePrediction(
                    context = context,
                    user = currentUser,
                    waktuMulai = currentUser.waktu_mulai.toString(),
                    waktuSelesai = currentUser.waktu_selesai.toString(),
                    todayDate = todayDate,
                )

                hasilPred?.let { predictionResult ->
                    totalAktual = predictionResult.todayAktual
                    totalPrediksi = predictionResult.todayPrediksi
                    todayList = predictionResult.todayList
                    prediksiList = predictionResult.prediksiList
                    statusHistory = predictionResult.statusHistory
                    syaratHistory = predictionResult.syaratHistory

                    sessionList = predictionResult.drinkSessionList
                    userWaktuMulai = predictionResult.userWaktuMulai
                    userWaktuSelesai = predictionResult.userWaktuSelesai
                    totalPrediksiWhole = predictionResult.todayPrediksiWhole
                    prediksiListWhole = predictionResult.prediksiListWhole

                    val predictionTimes = predictionResult.prediksiListWhole.keys.map { timeStr ->
                        val parts = timeStr.split(":")
                        val hour = parts[0].toInt() % 24
                        val minute = parts[1].toInt()
                        val second = parts[2].toInt()
                        LocalTime.of(hour, minute, second)
                    }.sorted()

                    if (predictionTimes.size > 1) {
                        val intervals = predictionTimes.zipWithNext { a, b ->
                            val duration = Duration.between(a, b)
                            if (duration.isNegative) duration.plusDays(1) else duration
                        }.map { it.toMinutes().toDouble() }

                        averageDrinkIntervalHours = intervals.average() / 60.0
                    } else {
                        averageDrinkIntervalHours = 0.0
                    }

                    val nowTime = LocalTime.now()
                    val nextPrediction = predictionTimes.firstOrNull { it.isAfter(nowTime) }
                    if (nextPrediction != null) {
                        val duration = Duration.between(nowTime, nextPrediction)
                        nextPredictedDrinkTimeHours = if (duration.isNegative) duration.plusDays(1).toMinutes() / 60.0 else duration.toMinutes() / 60.0
                    } else {
                        nextPredictedDrinkTimeHours = 0.0
                    }

                    val drinkVolumes = prediksiListWhole.values.toList()
                    drinkTimes = prediksiListWhole.keys.map {
//                        LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm:ss"))

                        val parts = it.split(":")
                        val hour = parts[0].toInt() % 24
                        val minute = parts[1].toInt()
                        val second = parts[2].toInt()
                        LocalTime.of(hour, minute, second)
                    }

                    drinkFrequency = drinkVolumes.size
                    avgDrinkVolume = if (drinkVolumes.isNotEmpty()) drinkVolumes.average() else 0.0
                }

                val weeklyPredictionEntry = getWeeklyPrediction(context).first()
                if (
                    weeklyPredictionEntry == null || weeklyPredictionEntry.date != todayDate || weeklyPredictionEntry.weeklyPrediction.values.all { it == 0.0 }
//                    true
                ) {
                    val tempPredictedDailyIntake = mutableMapOf<String, Double>()
                    val startDate = LocalDate.now().plusDays(1)

                    for (i in 0 until 7) {
                        val date = startDate.plusDays(i.toLong())
                        val dateString = date.toString()

                        println(userWaktuMulai)
                        println(userWaktuSelesai)

                        val wholeDayPrediction = predictWholeDay(
                            context = context,
                            user = currentUser,
                            fromDate = dateString,
                            userWaktuMulai = userWaktuMulai!!,
                            userWaktuSelesai = userWaktuSelesai!!,
                            syaratHistory = syaratHistory
                        )

                        val totalPredictionForDay = wholeDayPrediction.values.sum()
                        tempPredictedDailyIntake[dateString] = totalPredictionForDay
                    }
                    predictedDailyIntakeNext7Days = tempPredictedDailyIntake.toSortedMap()
                    val data = WeeklyPredictionEntry(
                        date = todayDate,
                        weeklyPrediction = tempPredictedDailyIntake.toSortedMap()
                    )
                    saveWeeklyPrediction(
                        context = context,
                        data = data
                    )
                } else {
                    predictedDailyIntakeNext7Days = weeklyPredictionEntry!!.weeklyPrediction
                }

                val allIntakeValues = dailyIntakeLast7DaysFromApi.values + predictedDailyIntakeNext7Days.values
                val overallMax = allIntakeValues.maxOrNull() ?: 0.0

                globalMaxChartValue = (kotlin.math.ceil(overallMax / 500.0) * 500.0).coerceAtLeast(1000.0)
                if (overallMax > 0.0 && globalMaxChartValue < overallMax) {
                    globalMaxChartValue += 500.0
                }
            }

            val weeklyHistoryEntry = getWeeklyHistory(context).first()
            if (weeklyHistoryEntry == null || weeklyHistoryEntry.date != todayDate || weeklyHistoryEntry.weeklyHistory.values.all { it == 0.0 }) {
                val userHistoryData = fetchSensorDataHistory(
                    apiService,
                    currentUser.id,
                    todayDate
                )

                val parsedFormatterForApi = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")

                val sortedData = userHistoryData?.sortedByDescending {
                    LocalDateTime.parse(it.created_at, parsedFormatterForApi)
                }

                val tempDailyIntakeMap = mutableMapOf<String, Double>()

                val processedDates = mutableSetOf<String>()

                sortedData?.forEach { item ->
                    try {
                        val itemDateWIB = convertUtcToWIB(item.created_at)

                        if (itemDateWIB != null && (processedDates.size < 7 || processedDates.contains(itemDateWIB))) {

                            val currentWeight = item.weight
                            val previousWeight = item.previous_weight

                            val intake = if (previousWeight > 0.0 && currentWeight < previousWeight) {
                                (previousWeight - currentWeight).coerceAtLeast(0.0)
                            } else {
                                0.0
                            }

                            if (intake > 0) {
                                tempDailyIntakeMap.merge(itemDateWIB, intake) { oldVal, newVal -> oldVal + newVal }
                            }
                            processedDates.add(itemDateWIB)
                        }
                    } catch (e: Exception) {
                        println("Error processing sensor data item ${item.id} in HomeScreen: ${e.message}")
                    }
                }

                dailyIntakeLast7DaysFromApi = tempDailyIntakeMap.toSortedMap()

                val data = HistoryWeeklyEntry(
                    date = todayDate,
                    weeklyHistory = tempDailyIntakeMap.toSortedMap()
                )
                saveWeeklyHistory(
                    context = context,
                    data = data
                )
            } else {
                dailyIntakeLast7DaysFromApi = weeklyHistoryEntry.weeklyHistory
            }

            val idKelurahan = currentUser.id_kelurahan
            userDeviceId = currentUser.device_id.toString()

            if (idKelurahan != null && idKelurahan != "") {
                val kodeLengkapResponse = wilayahViewModel.getKodeLengkap(idKelurahan.toInt())
                kodeLengkap = kodeLengkapResponse.kode_kelurahan_lengkap
            }

            val weatherResponse = fetchBMKGWeatherData(kodeLengkap)

            weatherForecastList.clear()

            weatherResponse?.data?.forEach { weatherData ->
                weatherData.cuaca.forEach { cuacaList ->
                    cuacaList.forEach { cuacaItem ->
                        try {
                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val dateTime = LocalDateTime.parse(cuacaItem.local_datetime, formatter)
                            val now = LocalDateTime.now()

                            val duration = Duration.between(now, dateTime)

                            if (dateTime.isAfter(now) && duration.toHours() <= 24) {
                                weatherForecastList.add(cuacaItem)
                            }
                        } catch (e: Exception) {
                            println("Error parsing date for weather item: ${cuacaItem.local_datetime}, Error: ${e.message}")
                        }
                    }
                }
            }

            weatherForecastList.sortBy {
                try {
                    LocalDateTime.parse(it.local_datetime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                } catch (e: Exception) {
                    LocalDateTime.MIN
                }
            }

            lastSuhu = sensorViewModel.getLastSuhuByDeviceId(userDeviceId)

            insightTips.clear()

            val currentWeather = weatherForecastList.firstOrNull()
            val suhuOutdoor = currentWeather?.t?.toDouble()
            val kelembabanOutdoor = currentWeather?.hu?.toDouble()
            val suhuIndoor = lastSuhu?.data?.temperature
            val kelembabanIndoor = lastSuhu?.data?.humidity

            val suhuOutdoorStr = suhuOutdoor?.roundToInt()?.toString() ?: "-"
            val kelembabanOutdoorStr = kelembabanOutdoor?.roundToInt()?.toString() ?: "-"
            val suhuIndoorStr = suhuIndoor?.roundToInt()?.toString() ?: "-"
            val kelembabanIndoorStr = kelembabanIndoor?.roundToInt()?.toString() ?: "-"

            val isOutdoorHot = suhuOutdoor != null && suhuOutdoor >= OUTDOOR_HOT_TEMP_THRESHOLD
            val isIndoorHot = suhuIndoor != null && suhuIndoor >= INDOOR_HOT_TEMP_THRESHOLD
            val isIndoorDry = kelembabanIndoor != null && kelembabanIndoor <= INDOOR_LOW_HUMIDITY_THRESHOLD

            val kondisiOutdoor = when {
                suhuOutdoor == null -> "Tidak tersedia"
                suhuOutdoor >= OUTDOOR_VERY_HOT_TEMP_THRESHOLD -> "sangat panas"
                suhuOutdoor >= OUTDOOR_HOT_TEMP_THRESHOLD -> "panas"
                else -> "normal"
            }

            val kondisiIndoor = when {
                suhuIndoor == null -> "Tidak tersedia"
                suhuIndoor >= INDOOR_HOT_TEMP_THRESHOLD -> "panas"
                suhuIndoor <= INDOOR_COOL_TEMP_THRESHOLD -> "sejuk"
                else -> "normal"
            }

            val kelembabanIndoorLevel = when {
                kelembabanIndoor == null -> "Tidak tersedia"
                kelembabanIndoor >= INDOOR_HIGH_HUMIDITY_THRESHOLD -> "lembap"
                kelembabanIndoor <= INDOOR_LOW_HUMIDITY_THRESHOLD -> "kering"
                else -> "normal"
            }

            val kelembabanOutdoorLevel = when {
                kelembabanOutdoor == null -> "Tidak tersedia"
                kelembabanOutdoor >= OUTDOOR_HIGH_HUMIDITY_THRESHOLD -> "lembap"
                kelembabanOutdoor <= OUTDOOR_LOW_HUMIDITY_THRESHOLD -> "kering"
                else -> "normal"
            }

            when {
                kondisiOutdoor == "sangat panas" -> {
                    hydrationAdjustment += 600.0
                    adjustmentMessage += "Penting: Target Anda disesuaikan karena kondisi lingkungan luar ruangan sangat panas. " +
                            "Sistem memperkirakan kebutuhan cairan Anda meningkat signifikan."
                }
                kondisiOutdoor == "panas" -> {
                    hydrationAdjustment += 300.0
                    adjustmentMessage += "Penting: Target Anda disesuaikan karena kondisi lingkungan luar ruangan panas. " +
                            "Sistem memperkirakan kebutuhan cairan Anda meningkat."
                }
                kondisiIndoor == "panas" -> {
                    hydrationAdjustment += 175.0
                    adjustmentMessage = "Penting: Target Anda disesuaikan karena kondisi lingkungan dalam ruangan yang panas. " +
                            "Sistem memperkirakan kebutuhan cairan Anda meningkat."
                }
                kelembabanIndoorLevel == "kering" -> {
                    hydrationAdjustment += 175.0
                    adjustmentMessage += "Penting: Target Anda disesuaikan karena kelembaban dalam ruangan yang kering. " +
                            "Sistem memperkirakan kebutuhan cairan Anda meningkat."
                }
                else -> {
                    hydrationAdjustment = 0.0
                    adjustmentMessage = ""
                }
            }


            val rekomendasi = when {
                kondisiOutdoor == "sangat panas" && kelembabanOutdoorLevel == "lembap" ->
                    "- Penuhi asupan cairan yang hilang melalui keringat dengan minum air mineral yang cukup. " +
                            "Pekerja di lingkungan panas disarankan mengonsumsi rata-rata 2.7 liter/hari untuk wanita dan 3.7 liter/hari untuk pria.\n" +
                            "- Minumlah setidaknya 1 gelas air (sekitar 200-250 mL) setiap 15-20 menit atau 20-30 menit, " +
                            "terutama saat beraktivitas di luar ruangan.\n" +
                            "- Pertimbangkan air yang relatif dingin (sekitar 10°C-15°C) dan dekatkan botol minum Anda ke tempat beraktivitas."

                kondisiIndoor == "panas" ->
                    "- Minumlah secara teratur setiap 1 hingga 1.5 jam, bahkan jika tidak merasa haus, " +
                            "untuk mengimbangi kehilangan cairan yang meningkat karena suhu ruangan yang hangat.\n" +
                            "- Pertimbangkan untuk menambah 1-2 sesi minum ekstra dengan volume kecil (sekitar 150-200 mL per sesi) " +
                            "untuk memastikan hidrasi optimal sepanjang hari."

                kondisiIndoor == "sejuk" ->
                    "- Tetap minum secara proaktif setiap 1.5–2 jam, meskipun tidak merasa haus. " +
                            "Mengandalkan rasa haus saja bisa menyebabkan dehidrasi ringan karena sensasi haus " +
                            "seringkali muncul setelah tubuh mengalami kekurangan cairan 1-2%."

                kelembabanIndoorLevel == "kering" ->
                    "- Tingkatkan asupan cairan dengan menambahkan 1–2 sesi minum kecil (sekitar 150–200 mL) secara proaktif. " +
                            "Kelembaban rendah meningkatkan penguapan cairan dari tubuh.\n" +
                            "- Jika Anda merasakan mulut kering, ini adalah indikator awal dehidrasi; " +
                            "segera minum untuk mengatasinya."

                else ->
                    "- Pertahankan jadwal hidrasi seperti biasa."
            }

            val detailMessage = buildAnnotatedString{
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("""📍 Kondisi Outdoor (BMKG)""")
                }
                append("\n- Suhu: ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(suhuOutdoorStr)
                }
                append("°C -> ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(kondisiOutdoor)
                }
                append("\n- Kelembaban: ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(kelembabanOutdoorStr)
                }
                append("% -> ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(kelembabanOutdoorLevel)
                }
                append("\n\n")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("""📊 Analisis Outdoor: """)
                }
                append("\n")
                append(
                    when {
                        suhuOutdoor == null || kelembabanOutdoor == null -> "Data cuaca luar tidak tersedia saat ini."
                        kondisiOutdoor == "sangat panas" && kelembabanOutdoorLevel == "lembap" ->
                            "Cuaca sangat panas dan lembap meningkatkan beban panas pada tubuh. " +
                                    "Risiko dehidrasi tinggi karena laju keringat dan penguapan cairan yang drastis, " +
                                    "yang dapat memengaruhi keseimbangan cairan tubuh dan termoregulasi."
                        kondisiOutdoor == "panas" ->
                            "Suhu luar panas. Tetap waspadai dehidrasi jika Anda banyak beraktivitas di luar ruangan."
                        kondisiOutdoor == "normal" ->
                            "Cuaca luar tergolong normal. Hidrasi standar cukup jika tidak ada aktivitas berat."
                        else -> "Cuaca luar tidak ekstrem, tetapi tetap jaga hidrasi."
                    }
                )
                append("\n\n")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("""🏠 Kondisi Indoor (Perangkat)""")
                }
                append("\n- Suhu: ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(suhuIndoorStr)
                }
                append("°C -> ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(kondisiIndoor)
                }
                append("\n- Kelembaban: ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(kelembabanIndoorStr)
                }
                append("% -> ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(kelembabanIndoorLevel)
                }
                append("\n\n")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("""📊 Analisis Indoor: """)
                }
                append("\n")
                append(
                    when {
                        suhuIndoor == null || kelembabanIndoor == null -> "Data suhu/kelembaban ruangan tidak tersedia."
                        kondisiIndoor == "panas" && kelembabanIndoorLevel == "lembap" ->
                            "Ruangan terasa panas dan lembap. Kondisi ini bisa membuat tubuh cepat berkeringat."

                        kondisiIndoor == "panas" ->
                            "Ruangan cukup panas meningkatkan risiko dehidrasi karena tubuh tetap kehilangan cairan melalui keringat. " +
                                    "Kondisi ini bisa membuat Anda lupa untuk minum secara teratur saat fokus beraktivitas."

                        kondisiIndoor == "sejuk" ->
                            "Ruangan sejuk dapat menurunkan sensasi haus, " +
                                    "padahal tubuh tetap kehilangan cairan secara tidak terasa melalui pernapasan dan penguapan. " +
                                    "Mengandalkan rasa haus saja tidak cukup untuk hidrasi optimal dalam kondisi ini."

                        kelembabanIndoorLevel == "kering" ->
                            "Udara dalam ruangan yang kering meningkatkan laju penguapan cairan dari kulit dan saluran pernapasan, " +
                                    "mempercepat kehilangan cairan tubuh dan dapat menyebabkan sensasi mulut kering."

                        else -> "Kondisi ruangan cukup ideal untuk menjaga hidrasi seperti biasa."
                    }
                )
                append("\n\n")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("""💡 Rekomendasi: """)
                }
                append("\n")
                append(rekomendasi)
            }

            var percentIntake = (totalAktual / dailyGoal) * 100

            var THRESHOLD_LOW = 60
            var THRESHOLD_GOOD = 90

            if (
                kondisiIndoor == "panas" ||
                kondisiOutdoor == "sangat panas" ||
                kelembabanIndoorLevel == "lembap" ||
                kelembabanOutdoorLevel == "kering"
            ) {
                THRESHOLD_LOW += 10
                THRESHOLD_GOOD += 5
            } else if (
                kondisiIndoor == "sejuk" &&
                kelembabanIndoorLevel == "kering" &&
                kondisiOutdoor == "normal"
            ) {
                THRESHOLD_LOW -= 5
                THRESHOLD_GOOD -= 5
            }

            statusHidrasi = when {
                percentIntake < THRESHOLD_LOW -> "Rendah"
                percentIntake < THRESHOLD_GOOD -> "Cukup"
                else -> "Baik"
            }

            fun formatDurationToReadableString(duration: Duration): String {
                if (duration.isNegative) {
                    return "Sudah terlewat"
                }

                val hours = duration.toHours()
                val minutes = duration.toMinutes() % 60

                return when {
                    hours > 0 && minutes > 0 -> "$hours jam $minutes menit"
                    hours > 0 -> "$hours jam"
                    minutes > 0 -> "$minutes menit"
                    else -> "Kurang dari 1 menit"
                }
            }

            val session = sessionList?.lastOrNull()

            if (session != null || LocalDate.now() == LocalDate.parse(todayDate, formatter)) {

                val inputFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val waktuAkhirDate: Date? = try {
                    if (LocalDate.now() == LocalDate.parse(todayDate, formatter)) {
                        val currentTimeString =
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        inputFormat.parse(currentTimeString)
                    } else {
                        inputFormat.parse(session?.second)
                    }
                } catch (e: Exception) {
                    null
                }

                if (waktuAkhirDate != null && userWaktuSelesai != null) {
                    val waktuAkhirLocalTime: LocalTime = waktuAkhirDate.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()

                    val waktuBeres: LocalTime = LocalTime.of(23, 59, 59)

                    var selisihDuration = Duration.between(waktuAkhirLocalTime, userWaktuSelesai)

                    if (selisihDuration.isNegative) {
                        selisihDuration = selisihDuration.plusDays(1)
                    }

                    durasiSisaGlobal = selisihDuration

                    val selisihMalam = Duration.between(waktuAkhirLocalTime, waktuBeres)

                    val formattedSelisihMalam = if (selisihMalam.isNegative) {
                        "Sudah lewat tengah malam"
                    } else {
                        formatDurationToReadableString(selisihMalam)
                    }

                    if (dailyGoal > 0 && hasilPred != null && statusHistory == true) {
                        if (totalAktual < dailyGoal) {
                            val remainingVolume = (dailyGoal - totalAktual).coerceAtLeast(0.0)
                            val remainingVolumeAdjusted = remainingVolume + hydrationAdjustment

                            // Durasi sisa waktu dari sekarang hingga waktu selesai
                            val currentTime = LocalTime.now()
                            val endTime = userWaktuSelesai ?: LocalTime.of(23, 59)
                            var remainingDuration = Duration.between(currentTime, endTime)
                            if (remainingDuration.isNegative) remainingDuration = remainingDuration.plusDays(1)

                            val freqToSelesai = when {
                                drinkFrequency <= 2 -> 2
                                drinkFrequency in 3..4 -> 3
                                else -> 4
                            }
                            val volPerSessionSelesai = (remainingVolume / freqToSelesai).roundToInt()
                            val intervalSelesai = (remainingDuration.toMinutes().toInt() / freqToSelesai).coerceAtLeast(1)
                            val waktuMulaiSelesai = endTime.minusMinutes(((freqToSelesai - 1) * intervalSelesai).toLong())
                            val timesToSelesai = (0 until freqToSelesai).map {
                                waktuMulaiSelesai.plusMinutes((it * intervalSelesai).toLong()).truncatedTo(ChronoUnit.MINUTES)
                            }
                            val strTimesToSelesai = timesToSelesai.joinToString(", ") { it.toString() }

                            val endTimeMidnight = LocalTime.of(23, 59)
                            var durationToMidnight = Duration.between(currentTime, endTimeMidnight)
                            if (durationToMidnight.isNegative) durationToMidnight = durationToMidnight.plusDays(1)

                            val freqToMidnight = when {
                                drinkFrequency <= 2 -> 3
                                drinkFrequency in 3..4 -> 4
                                else -> 5
                            }
                            val volPerSessionMidnight = (remainingVolume / freqToMidnight).roundToInt()
                            val intervalMidnight = (durationToMidnight.toMinutes().toInt() / freqToMidnight).coerceAtLeast(1)
                            val waktuMulaiMidnight = endTimeMidnight.minusMinutes(((freqToMidnight - 1) * intervalMidnight).toLong())
                            val timesToMidnight = (0 until freqToMidnight).map {
                                waktuMulaiMidnight.plusMinutes((it * intervalMidnight).toLong()).truncatedTo(ChronoUnit.MINUTES)
                            }
                            val strTimesToMidnight = timesToMidnight.joinToString(", ") { it.toString() }

                            val volPerSessionSelesaiAdjusted = (remainingVolumeAdjusted / freqToSelesai).roundToInt()
                            val volPerSessionMidnightAdjusted = (remainingVolumeAdjusted / freqToMidnight).roundToInt()

                            // --- OPSI KEDUA: Minum sedikit per sesi (sekitar 200-250 mL) tapi lebih sering ---
                            val targetVolumePerSip = 200.0 // mL per sesi, sesuai diskusi
                            val freqMoreSipsSelesai = (remainingVolume / targetVolumePerSip).roundToInt().coerceAtLeast(1)
                            val volPerSessionMoreSipsSelesai = (remainingVolume / freqMoreSipsSelesai).roundToInt()
                            val intervalMoreSipsSelesai = (remainingDuration.toMinutes().toInt() / freqMoreSipsSelesai).coerceAtLeast(1)
                            val waktuMulaiMoreSipsSelesai = endTime.minusMinutes(((freqMoreSipsSelesai - 1) * intervalMoreSipsSelesai).toLong())
                            val timesMoreSipsSelesai = (0 until freqMoreSipsSelesai).map {
                                waktuMulaiMoreSipsSelesai.plusMinutes((it * intervalMoreSipsSelesai).toLong()).truncatedTo(ChronoUnit.MINUTES)
                            }
                            val strTimesMoreSipsSelesai = timesMoreSipsSelesai.joinToString(", ") { it.toString() }

                            val freqMoreSipsMidnight = (remainingVolume / targetVolumePerSip).roundToInt().coerceAtLeast(1)
                            val volPerSessionMoreSipsMidnight = (remainingVolume / freqMoreSipsMidnight).roundToInt()
                            val intervalMoreSipsMidnight = (durationToMidnight.toMinutes().toInt() / freqMoreSipsMidnight).coerceAtLeast(1)
                            val waktuMulaiMoreSipsMidnight = endTimeMidnight.minusMinutes(((freqMoreSipsMidnight - 1) * intervalMoreSipsMidnight).toLong())
                            val timesMoreSipsMidnight = (0 until freqMoreSipsMidnight).map {
                                waktuMulaiMoreSipsMidnight.plusMinutes((it * intervalMoreSipsMidnight).toLong()).truncatedTo(ChronoUnit.MINUTES)
                            }
                            val strTimesMoreSipsMidnight = timesMoreSipsMidnight.joinToString(", ") { it.toString() }

                            val freqMoreSipsSelesaiAdjusted = (remainingVolumeAdjusted / targetVolumePerSip).roundToInt().coerceAtLeast(1)
                            val volPerSessionMoreSipsSelesaiAdjusted = (remainingVolumeAdjusted / freqMoreSipsSelesaiAdjusted).roundToInt()
                            val freqMoreSipsMidnightAdjusted = (remainingVolumeAdjusted / targetVolumePerSip).roundToInt().coerceAtLeast(1)
                            val volPerSessionMoreSipsMidnightAdjusted = (remainingVolumeAdjusted / freqMoreSipsMidnightAdjusted).roundToInt()

                            insightTips.add(
                                InsightItem(
                                    iconRes = R.drawable.outline_warning,
                                    title = "Target Belum Tercapai",
                                    level = "danger",
                                    color = InsightItemLevelColor["danger"]!!,
                                    message = "Target minum harian Anda (${dailyGoal.roundToInt()}mL) diprediksi belum tercapai dengan pola saat ini " +
                                            "(${(totalPrediksi).roundToInt()}mL). Klik untuk rincian.",
                                    detail = buildAnnotatedString {
                                        append("Anda masih kekurangan ")
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("${remainingVolume.roundToInt()}mL")
                                        }
                                        append(" dari target harian.\n\n")
                                        append("Kekurangan cairan dapat menurunkan kinerja fisik dan konsentrasi, serta meningkatkan risiko kelelahan panas dan gangguan ginjal.\n\n")
                                        append("Waktu tersisa hingga target harian tercapai:\n")
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append(formatDurationToReadableString(selisihDuration))
                                        }
                                        append(" (batas waktu Anda pukul $userWaktuSelesai), dan ")
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append(formattedSelisihMalam)
                                        }
                                        append(" (hingga pukul 23:59).\n\n")
                                        append("Minumlah secara bertahap dalam sesi kecil untuk hasil lebih efektif daripada sekaligus dalam jumlah besar.")
                                    }
                                )
                            )

                            planOptions.add(
                                RecommendationOption(
                                    title = buildAnnotatedString { append("Opsi 1: Rencana Berdasarkan Kebiasaan (hingga ${userWaktuSelesai})") },
                                    details = buildAnnotatedString {
                                        append("• Frekuensi: $freqToSelesai kali\n")
                                        append("• Volume tiap sesi: ~${volPerSessionSelesaiAdjusted}mL\n")
                                        append("• Perkiraan Waktu: $strTimesToSelesai")
                                    },
                                    schedule = RecommendationSchedule(times = timesToSelesai, volumePerSession = volPerSessionSelesaiAdjusted)
                                )
                            )

                            planOptions.add(
                                RecommendationOption(
                                    title = buildAnnotatedString { append("Opsi 2: Rencana Berdasarkan Kebiasaan (hingga 23:59)") },
                                    details = buildAnnotatedString {
                                        append("• Frekuensi: $freqToMidnight kali\n")
                                        append("• Volume tiap sesi: ~${volPerSessionMidnightAdjusted}mL\n")
                                        append("• Perkiraan Waktu: $strTimesToMidnight")
                                    },
                                    schedule = RecommendationSchedule(times = timesToMidnight, volumePerSession = volPerSessionMidnightAdjusted)
                                )
                            )

                            planOptions.add(
                                RecommendationOption(
                                    title = buildAnnotatedString { append("Opsi 3: Minum Lebih Sering (hingga ${userWaktuSelesai})") },
                                    details = buildAnnotatedString {
                                        append("• Frekuensi: $freqMoreSipsSelesaiAdjusted kali\n")
                                        append("• Volume tiap sesi: ~${volPerSessionMoreSipsSelesaiAdjusted}mL\n")
                                        append("• Perkiraan Waktu: $strTimesMoreSipsSelesai")
                                    },
                                    schedule = RecommendationSchedule(times = timesMoreSipsSelesai, volumePerSession = volPerSessionMoreSipsSelesaiAdjusted)
                                )
                            )

                            planOptions.add(
                                RecommendationOption(
                                    title = buildAnnotatedString { append("Opsi 4: Minum Lebih Sering (hingga 23:59)") },
                                    details = buildAnnotatedString {
                                        append("• Frekuensi: $freqMoreSipsMidnightAdjusted kali\n")
                                        append("• Volume tiap sesi: ~${volPerSessionMoreSipsMidnightAdjusted}mL\n")
                                        append("• Perkiraan Waktu: $strTimesMoreSipsMidnight")
                                    },
                                    schedule = RecommendationSchedule(times = timesMoreSipsMidnight, volumePerSession = volPerSessionMoreSipsMidnightAdjusted)
                                )
                            )

                            insightTips.add(
                                InsightItem(
                                    iconRes = R.drawable.outline_access_time_24,
                                    title = "Rencana Minum",
                                    level = "info",
                                    color = InsightItemLevelColor["info"]!!,
                                    message = "Pilih rencana minum yang paling sesuai untuk Anda. Klik untuk rincian.",
                                    detail = buildAnnotatedString {
                                        // Tambahkan pesan penyesuaian di awal
                                        if (adjustmentMessage.isNotEmpty()) {
                                            append(adjustmentMessage)
                                            append("Sistem telah memperkirakan tambahan asupan sebanyak ")
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append("${hydrationAdjustment.roundToInt()}mL")
                                            }
                                            append(" untuk menjaga hidrasi optimal pada kondisi cuaca saat ini.\n\n")
                                            append("Anda masih perlu minum ")
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append("${remainingVolumeAdjusted.roundToInt()}mL")
                                            }
                                            append(" lagi.")
                                        } else {
                                            append("Untuk mencapai target harian, Anda masih perlu minum ")
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append("${remainingVolume.roundToInt()}mL")
                                            }
                                            append(" lagi.\n\n")
                                        }
                                    },
                                    recommendationOptions = planOptions


//                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
//                                            append("Opsi 1: Rencana Minum Berdasarkan Kebiasaan (Rekomendasi Utama)")
//                                        }
//                                        append("\nPendekatan ini menyesuaikan frekuensi minum berdasarkan pola konsumsi Anda, dengan volume per sesi yang dihitung secara proporsional.\n\n")
//
//                                        append("📆 Hingga pukul ${userWaktuSelesai}:\n")
//                                        append("- Frekuensi: $freqToSelesai kali\n")
//                                        append("- Volume tiap sesi: ${volPerSessionSelesaiAdjusted}mL\n")
//                                        append("- Waktu: $strTimesToSelesai\n\n")
//
//                                        append("🌙 Hingga 23:59:\n")
//                                        append("- Frekuensi: $freqToMidnight kali\n")
//                                        append("- Volume tiap sesi: ${volPerSessionMidnightAdjusted}mL\n")
//                                        append("- Waktu: $strTimesToMidnight\n\n")
//
//                                        append("---\n\n")
//
//                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
//                                            append("Opsi 2: Minum Lebih Sering dengan Volume Kecil (Disarankan untuk Penyerapan Optimal)")
//                                        }
//                                        append("\nStrategi ini mengutamakan frekuensi minum yang lebih tinggi dengan volume yang lebih kecil per sesi (sekitar 200-250 mL). Ini sesuai dengan saran ahli bahwa \"minum sedikit demi sedikit sepanjang hari\" lebih efektif untuk hidrasi dan retensi cairan tubuh. \n\n")
//
//                                        append("📆 Hingga pukul ${userWaktuSelesai}:\n")
//                                        append("- Frekuensi: $freqMoreSipsSelesaiAdjusted kali\n")
//                                        append("- Volume tiap sesi: ${volPerSessionMoreSipsSelesaiAdjusted}mL\n")
//                                        append("- Waktu: $strTimesMoreSipsSelesai\n\n")
//
//                                        append("🌙 Hingga 23:59:\n")
//                                        append("- Frekuensi: $freqMoreSipsMidnightAdjusted kali\n")
//                                        append("- Volume tiap sesi: ${volPerSessionMoreSipsMidnightAdjusted}mL\n")
//                                        append("- Waktu: $strTimesMoreSipsMidnight")
//                                    }
                                )
                            )
                        }
                    }
                }

                if (dailyGoal > 0 && prediksiListWhole.isEmpty() && waktuAkhirDate != null && currentUser.waktu_selesai != null) {
                    if  (totalAktual < dailyGoal)   {
                        val waktuAkhirUserManual: LocalTime = LocalTime.parse(currentUser.waktu_selesai)
                        val waktuSekarang = LocalTime.now()

                        var durasiSisa = Duration.between(waktuSekarang, waktuAkhirUserManual)
                        if (durasiSisa.isNegative) durasiSisa = durasiSisa.plusDays(1)

                        val sisaVolume = (dailyGoal - totalAktual).coerceAtLeast(0.0)
                        val adjustedSisaVolume = sisaVolume + hydrationAdjustment

                        val freqRekomendasi = when {
                            sisaVolume < 400 -> 2
                            sisaVolume < 700 -> 3
                            else -> 4
                        }
                        val volPerSession = (sisaVolume / freqRekomendasi).roundToInt()
                        val interval = (durasiSisa.toMinutes().toInt() / freqRekomendasi).coerceAtLeast(1)
                        val waktuSaran = waktuAkhirUserManual.minusMinutes(((freqRekomendasi - 1) * interval).toLong())
                        val timesToSelesai = (0 until freqRekomendasi).map {
                            waktuSaran.plusMinutes((it * interval).toLong()).truncatedTo(ChronoUnit.MINUTES)
                        }
                        val waktuSaranStr = timesToSelesai.joinToString(", ") { it.toString() }

                        val volTargetPerSesi = 220.0 // rata-rata 200–250
                        val freqPerSesiKecil = (sisaVolume / volTargetPerSesi).roundToInt().coerceAtLeast(1)
                        val volPerKecil = (sisaVolume / freqPerSesiKecil).roundToInt()
                        val intervalKecil = (durasiSisa.toMinutes().toInt() / freqPerSesiKecil).coerceAtLeast(1)
                        val waktuSaranKecil = waktuAkhirUserManual.minusMinutes(((freqPerSesiKecil - 1) * intervalKecil).toLong())
                        val timesMoreSipsSelesai = (0 until freqPerSesiKecil).map {
                            waktuSaranKecil.plusMinutes((it * intervalKecil).toLong()).truncatedTo(ChronoUnit.MINUTES)
                        }
                        val waktuSaranKecilStr = timesMoreSipsSelesai.joinToString(", ") { it.toString() }

                        val volPerSessionAdjusted = (adjustedSisaVolume / freqRekomendasi).roundToInt()
                        val volPerKecilAdjusted = (adjustedSisaVolume / freqPerSesiKecil).roundToInt()

                        insightTips.add(
                            InsightItem(
                                iconRes = R.drawable.outline_warning,
                                title = "Target Belum Tercapai",
                                level = "danger",
                                color = InsightItemLevelColor["danger"]!!,
                                message = "Target minum harian Anda (${dailyGoal.roundToInt()}mL) belum tercapai, saat ini anda baru minum" +
                                        "(${(totalAktual).roundToInt()}mL). Klik untuk rincian.",
                                detail = buildAnnotatedString {
                                    append("Target hidrasi Anda belum tercapai. ")
                                    append("Kekurangan cairan tubuh sebesar ")
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("${sisaVolume.roundToInt()}mL")
                                    }
                                    append(" dapat menyebabkan penurunan kinerja fisik dan kognitif, serta meningkatkan risiko kondisi kesehatan tertentu seperti kelelahan panas atau masalah ginjal.")
                                    append("\n\nMempertahankan keseimbangan cairan tubuh penting untuk fungsi fisiologis optimal. Anda masih memiliki waktu:")
                                    append("\n\n")
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(formatDurationToReadableString(durasiSisa))
                                    }
                                    append(" sebelum pukul ${currentUser.waktu_selesai} \nuntuk mencapai target hidrasi anda.")
                                    append("\n\nStrategi minum teratur, memecah total asupan menjadi sesi-sesi kecil, terbukti lebih efektif dalam mencapai target hidrasi dan mempertahankan keseimbangan cairan dibandingkan asupan yang jarang dan dalam volume besar.")
                                }
                            )
                        )

                        insightTips.add(
                            InsightItem(
                                iconRes = R.drawable.outline_access_time_24,
                                title = "Rencana Minum",
                                level = "info",
                                color = InsightItemLevelColor["info"]!!,
                                message = "Masih ada ${(sisaVolume).roundToInt()}mL yang perlu diminum. Klik untuk rincian rencana minum.",
                                detail = buildAnnotatedString {
                                    if (adjustmentMessage.isNotEmpty()) {
                                        append(adjustmentMessage)
                                        append("Tambahan asupan ")
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("${hydrationAdjustment.roundToInt()}mL")
                                        }
                                        append(" telah dimasukkan ke dalam perhitungan.\n\n")
                                    }

                                    append("Untuk mencapai target hidrasi harian Anda (${dailyGoal.roundToInt()}mL), masih perlu minum ")
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("${sisaVolume.roundToInt()}mL")
                                    }
                                    append(" lagi dalam waktu ")
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(formatDurationToReadableString(durasiSisa))
                                    }
                                    append(" hingga pukul ${currentUser.waktu_selesai}.\n\n")

                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("Opsi 1: Minum Berdasarkan Frekuensi Ringan\n")
                                    }
                                    append("- Frekuensi: $freqRekomendasi kali\n")
                                    append("- Volume tiap sesi: ${volPerSessionAdjusted}mL\n")
                                    append("- Waktu: $waktuSaranStr\n\n")

                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("Opsi 2: Minum Lebih Sering dengan Volume Kecil\n")
                                    }
                                    append("- Frekuensi: $freqPerSesiKecil kali\n")
                                    append("- Volume tiap sesi: ${volPerKecilAdjusted}mL\n")
                                    append("- Waktu: $waktuSaranKecilStr\n")
                                }
                            )
                        )
                    }
                }
            }

            insightTips.add(
                InsightItem(
                    iconRes = R.drawable.outline_device_thermostat_24,
                    title = "Kondisi \nSuhu & Kelembaban",
                    level = "warning",
                    color = InsightItemLevelColor["warning"]!!,
                    message = "Kondisi suhu dan kelembaban perlu perhatian. Klik untuk tips hidrasi.",
                    detail = detailMessage
                )
            )

            insightTips.add(
                InsightItem(
                    iconRes = R.drawable.outline_fitness_center_24,
                    title = "Tingkat Aktivitas",
                    level = "info",
                    color = InsightItemLevelColor["info"]!!,
                    message = "Panduan hidrasi berdasarkan aktivitas fisik. Klik untuk rincian.",
                    detail = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)){
                            append("""‍🧍 Aktivitas Ringan: jalan santai, pekerjaan ringan""")
                        }
                        append("\n- Kehilangan cairan sangat rendah; cukup ikuti target hidrasi harian.\n" +
                                "- Tidak perlu menambah asupan khusus.\n\n")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)){
                            append("""‍🚴‍♂️ Aktivitas Sedang: jogging ringan, berkebun, pekerjaan rumah tangga""")
                        }
                        append("\n- Kehilangan cairan ringan hingga sedang.\n" +
                                """- 💧 Tambah sekitar 200–300 mL setelah aktivitas.""" +
                        "\n\n")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)){
                            append("""‍🏋️‍♂️ Aktivitas Tinggi: olahraga intens >1 jam, kerja fisik berat""")
                        }
                        append("\n- Potensi kehilangan cairan besar (>1–2% berat badan).\n" +
                                """- 💧 Disarankan minum 400–800 mL selama aktivitas atau segera setelahnya.""")
                    }
                )
            )

            val avgHistoricalIntake = dailyIntakeLast7DaysFromApi.values.average()
            val avgPredictedIntake = predictedDailyIntakeNext7Days.values.average()

            if (avgHistoricalIntake.isNaN() || avgHistoricalIntake <= 0.0) {
                comparisonInsightLocal = InsightItem(
                    iconRes = R.drawable.outline_trending_up,
                    title = "Tren Hidrasi",
                    level = "info",
                    color = InsightItemLevelColor["info"]!!,
                    message = "Tren konsumsi air Anda di masa mendatang dapat dianalisis. Klik untuk rincian.",
                    detail = buildAnnotatedString {
                        append("Belum ada data historis konsumsi air yang cukup untuk menganalisis pola minum Anda secara mendalam. ")
                        append("Tetap konsisten dengan target harian Anda (")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("${dailyGoal.roundToInt()}mL")
                        }
                        append(") yang dihitung sistem berdasarkan profil Anda. Catat asupan Anda secara teratur untuk melihat tren di masa mendatang dan mendapatkan analisis yang lebih akurat.")
                    }
                )
            } else {
                val percentageChange = ((avgPredictedIntake - avgHistoricalIntake) / avgHistoricalIntake) * 100

                val trendMessage: String
                val trendDetail: AnnotatedString
                val trendColor: Color
                val trendIcon: Int
                val trendLevel: String

                when {
                    percentageChange > 10.0 -> {
                        if (avgPredictedIntake < dailyGoal * 0.95) {
                            trendMessage = "Konsumsi air harian Anda diprediksi meningkat, namun masih di bawah target."
                            trendDetail = buildAnnotatedString {
                                append("Rata-rata konsumsi air harian Anda diprediksi akan ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("meningkat sekitar ${percentageChange.roundToInt()}%")
                                }
                                append(" di masa mendatang (rata-rata ${avgPredictedIntake.roundToInt()}mL/hari) dibandingkan pola historis Anda (rata-rata ${avgHistoricalIntake.roundToInt()}mL/hari). ")
                                append("\nMeskipun ada peningkatan, prediksi asupan Anda masih berada ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("di bawah target harian Anda (${dailyGoal.roundToInt()}mL)")
                                }
                                append(" yang dihitung sistem. Penting untuk terus meningkatkan asupan Anda secara proaktif untuk mencapai hidrasi optimal.")
                            }
                            trendColor = InsightItemLevelColor["warning"]!!
                            trendIcon = R.drawable.outline_trending_up
                            trendLevel = "warning"
                        } else {
                            trendMessage = "Konsumsi air harian Anda diprediksi meningkat signifikan dan memenuhi target."
                            trendDetail = buildAnnotatedString {
                                append("Rata-rata konsumsi air harian Anda diprediksi akan ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("meningkat sekitar ${percentageChange.roundToInt()}%")
                                }
                                append(" di masa mendatang (rata-rata ${avgPredictedIntake.roundToInt()}mL/hari) dibandingkan pola historis Anda (rata-rata ${avgHistoricalIntake.roundToInt()}mL/hari). ")
                                append("Peningkatan ini menunjukkan bahwa prediksi asupan Anda kini lebih selaras dengan kebutuhan hidrasi optimal yang dihitung sistem berdasarkan profil Anda. ")
                                append("Pastikan untuk memenuhi target ini untuk hidrasi optimal dan kesehatan yang lebih baik.")
                            }
                            trendColor = InsightItemLevelColor["positive"]!!
                            trendIcon = R.drawable.outline_trending_up
                            trendLevel = "positive"
                        }
                    }
                    percentageChange < -10.0 -> {
                        if (avgPredictedIntake < dailyGoal * 0.95) {
                            trendMessage = "Konsumsi air harian Anda diprediksi menurun drastis dan jauh di bawah target!"
                            trendDetail = buildAnnotatedString {
                                append("Rata-rata konsumsi air harian Anda diprediksi akan ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("menurun sekitar ${(-percentageChange).roundToInt()}%")
                                }
                                append(" di masa mendatang (rata-rata ${avgPredictedIntake.roundToInt()}mL/hari) dibandingkan pola historis Anda (rata-rata ${avgHistoricalIntake.roundToInt()}mL/hari). ")
                                append("Penurunan ini sangat mengkhawatirkan karena prediksi asupan Anda akan jauh di bawah target harian (")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("${dailyGoal.roundToInt()}mL")
                                }
                                append(") yang dihitung sistem. Risiko dehidrasi tinggi dan dapat mempengaruhi kesehatan serta kinerja Anda. ")
                                append("Penting untuk segera meningkatkan asupan air Anda secara signifikan.")
                            }
                            trendColor = InsightItemLevelColor["danger"]!!
                            trendIcon = R.drawable.outline_trending_down
                            trendLevel = "danger"
                        } else {
                            trendMessage = "Konsumsi air harian Anda diprediksi menurun, namun masih di sekitar target."
                            trendDetail = buildAnnotatedString {
                                append("Rata-rata konsumsi air harian Anda diprediksi akan ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("menurun sekitar ${(-percentageChange).roundToInt()}%")
                                }
                                append(" di masa mendatang (rata-rata ${avgPredictedIntake.roundToInt()}mL/hari) dibandingkan pola historis Anda (rata-rata ${avgHistoricalIntake.roundToInt()}mL/hari). ")
                                append("Meskipun ada penurunan, prediksi asupan Anda masih berada di level yang mendekati target harian (")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("${dailyGoal.roundToInt()}mL")
                                }
                                append("). Disarankan untuk tetap memantau asupan Anda agar tidak terus menurun di bawah kebutuhan optimal.")
                            }
                            trendColor = InsightItemLevelColor["warning"]!!
                            trendIcon = R.drawable.outline_trending_down
                            trendLevel = "warning"
                        }
                    }
                    else -> {
                        if (avgPredictedIntake < dailyGoal * 0.95) {
                            trendMessage = "Pola konsumsi air harian Anda diprediksi stabil, namun masih di bawah target."
                            trendDetail = buildAnnotatedString {
                                append("Prediksi konsumsi air harian Anda (rata-rata ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("${avgPredictedIntake.roundToInt()}mL/hari")
                                }
                                append(") konsisten dengan pola historis Anda (rata-rata ${avgHistoricalIntake.roundToInt()}mL/hari). ")
                                append("Namun, kedua pola ini masih berada ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("di bawah target harian Anda (${dailyGoal.roundToInt()}mL)")
                                }
                                append(" yang dihitung sistem berdasarkan profil Anda. Penting untuk meningkatkan asupan Anda secara konsisten untuk mencapai target dan menjaga hidrasi optimal.")
                            }
                            trendColor = InsightItemLevelColor["warning"]!!
                            trendIcon = R.drawable.outline_warning
                            trendLevel = "warning"
                        } else {
                            trendMessage = "Pola konsumsi air harian Anda diprediksi stabil dan memenuhi target."
                            trendDetail = buildAnnotatedString {
                                append("Prediksi konsumsi air harian Anda (rata-rata ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("${avgPredictedIntake.roundToInt()}mL/hari")
                                }
                                append(") konsisten dengan pola historis Anda (rata-rata ${avgHistoricalIntake.roundToInt()}mL/hari). ")
                                append("Ini menunjukkan bahwa target harian Anda (")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("${dailyGoal.roundToInt()}mL")
                                }
                                append(") yang dihitung sistem berdasarkan profil Anda, dan kebiasaan minum Anda saat ini sudah selaras. ")
                                append("Pertahankan konsistensi ini untuk menjaga hidrasi optimal.")
                            }
                            trendColor = InsightItemLevelColor["positive"]!!
                            trendIcon = R.drawable.outline_trending_flat
                            trendLevel = "positive"
                        }
                    }
                }
                comparisonInsightLocal = InsightItem(
                    iconRes = trendIcon,
                    title = "Tren Hidrasi",
                    level = trendLevel,
                    color = trendColor,
                    message = trendMessage,
                    detail = trendDetail
                )
            }

            isLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 32.dp, end = 16.dp, bottom = 0.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Halaman Insight",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Tentang Anda",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Justify,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (latestDrinkData != null || latestBottleFillData != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    if (statusHidrasi != null){
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.elevatedCardElevation(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.outline_info_24),
                                        contentDescription = "Tentang Anda",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Status Hidrasi Anda",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                val hydrationInfo = buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                                        append("💧 Status Hidrasi: ")
                                    }

                                    when (statusHidrasi) {
                                        "Rendah" -> {
                                            append("\nStatus Anda saat ini: ")
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color(0xFFBA1A1A))) {
                                                append("Rendah")
                                            }
                                            append(".\nAsupan air Anda masih jauh di bawah target harian.")
                                            append("\nRisiko dehidrasi meningkat, terutama jika berada di lingkungan panas atau lembap.")
                                            append("\n\nSaran: Segera tingkatkan asupan minum Anda. ")
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold, color = Color(0xFF2196F3))) {
                                                append("(Lihat Rencana Minum untuk keterangan lebih rinci)")
                                            }
                                        }

                                        "Cukup" -> {
                                            append("\nStatus Anda saat ini: ")
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFA000))) {
                                                append("Cukup")
                                            }
                                            append(".\nAnda telah memenuhi sebagian besar kebutuhan cairan harian, ")
                                            append("namun belum mencapai hidrasi optimal.")
                                            append("\n\nSaran: Tambahkan 1–2 sesi minum kecil. ")
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold, color = Color(0xFF2196F3))) {
                                                append("(Lihat Rencana Minum untuk keterangan lebih rinci)")
                                            }
                                        }

                                        "Baik" -> {
                                            append("\nStatus Anda saat ini: ")
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color(0xFF4CAF50))) {
                                                append("Baik")
                                            }
                                            append(".\nAsupan air Anda sudah mendekati atau mencapai target harian.")
                                            append("\nTubuh Anda berada pada tingkat hidrasi optimal.")
                                            append("\n\nPertahankan pola minum Anda. ")
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold, color = Color(0xFF2196F3))) {
                                                append("(Lihat Rencana Minum untuk tips lanjutan)")
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = hydrationInfo,
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.elevatedCardElevation(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.outline_info_24), // Ikon info umum
                                    contentDescription = "Tentang Anda",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Aktivitas Hidrasi Terkini",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            val drinkTime = latestDrinkData?.created_at?.let { convertUtcToWIB(it, timeOnly = true) } ?: "-"
                            val prevDrinkTime = previousDrinkData?.created_at?.let { convertUtcToWIB(it, timeOnly = true) } ?: "-"
                            val drinkVolume = if (latestDrinkData != null) {
                                (latestDrinkData!!.previous_weight - latestDrinkData!!.weight).roundToInt()
                            } else 0
                            val timeSinceLastDrink: String = if (latestDrinkData?.created_at != null) {
                                val parsedDrinkDateTime = LocalDateTime.parse(latestDrinkData?.created_at, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                                    .atZone(ZoneId.of("UTC"))
                                    .withZoneSameInstant(ZoneId.of("Asia/Jakarta"))
                                    .toLocalDateTime()

                                val now = LocalDateTime.now(ZoneId.of("Asia/Jakarta"))

                                val duration = Duration.between(parsedDrinkDateTime, now)

                                when {
                                    duration.toMinutes() < 1 -> "Baru saja"
                                    duration.toMinutes() < 60 -> "${duration.toMinutes()} menit yang lalu"
                                    duration.toHours() < 24 -> "${duration.toHours()} jam ${duration.toMinutes() % 60} menit yang lalu"
                                    else -> "${duration.toDays()} hari yang lalu"
                                }
                            } else {
                                "Tidak diketahui"
                            }
                            val drinkInfo = buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                                    append("💧 Terakhir minum: ")
                                }
                                if (latestDrinkData != null) {
                                    append("\nAnda terakhir minum sebanyak ")
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                                        append("${drinkVolume}mL")
                                    }
                                    append(" pada pukul ")
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                                        append("${prevDrinkTime.substring(0,5)} - ${drinkTime.substring(0, 5)}")
                                    }
                                    append("\n (sekitar $timeSinceLastDrink)")
                                    append("\nSisa berat botol: ")
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                                        append("${latestDrinkData?.weight?.roundToInt()}g")
                                    }
                                } else {
                                    append("\nAnda belum terdeteksi minum hari ini.")
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = drinkInfo,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            val fillTime = latestBottleFillData?.created_at?.let { convertUtcToWIB(it, timeOnly = true) } ?: "-"
                            val fillWeight = latestBottleFillData?.weight?.roundToInt() ?: 0
                            val fillInfo = buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                                    append("""🥤 Terakhir isi ulang: """)
                                }
                                if (latestBottleFillData != null) {
                                    append("\nAnda terakhir")
                                    if(latestBottleFillData?.previous_weight == 0.0){
                                        append(" scan botol penuh")
                                    } else {
                                        append(" isi ulang botol")
                                    }
                                    append(" dengan berat ")
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                                        append("${fillWeight}g")
                                    }
                                    append(" pada pukul ")
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                                        append(fillTime.substring(0, 5))
                                    }
                                } else {
                                    append("\nTidak ada pengisian botol yang terdeteksi hari ini.")
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = fillInfo,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(16.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Anda Belum Minum Hari Ini, ayo Minum!!",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tips Kesehatan",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Justify,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (insightTips.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    insightTips.forEach { tip ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                                .clickable {
                                    selectedTip = tip
                                    showDialog = true
                                },
                            elevation = CardDefaults.elevatedCardElevation(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(start = 16.dp, end = 8.dp, bottom = 8.dp, top = 8.dp)
                                        .width(100.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(tip.color.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = tip.iconRes),
                                            contentDescription = tip.title,
                                            tint = tip.color,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = tip.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tip.color,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Text(
                                    text = tip.message,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier
                                        .padding(vertical = 8.dp, horizontal = 16.dp)
                                        .weight(1f),
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Tidak ada tips khusus saat ini. Tetap hidrasi!",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Data Historis",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (dailyIntakeLast7DaysFromApi.isEmpty()) {
                    Text(
                        text = "Data historis belum tersedia.",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        elevation = CardDefaults.elevatedCardElevation(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MPLineChartForDailyIntake(
                                dailyIntakeData = dailyIntakeLast7DaysFromApi,
                                colorInput = android.graphics.Color.rgb(0x00, 0x55, 0x7C),
                                globalMaxYAxisValue = globalMaxChartValue.toFloat()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Data Prediksi",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (prediksiListWhole.isEmpty()) {
                Text(
                    text = "Data prediksi belum tersedia.",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (predictedDailyIntakeNext7Days.isEmpty()) {
                        Text(
                            text = "Data prediksi belum tersedia.",
                            fontSize = 16.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        )
                    } else {
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            elevation = CardDefaults.elevatedCardElevation(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                MPLineChartForDailyIntake(
                                    dailyIntakeData = predictedDailyIntakeNext7Days,
                                    colorInput = android.graphics.Color.rgb(0x70, 0x35, 0x8C),
                                    globalMaxYAxisValue = globalMaxChartValue.toFloat()
                                )
                            }
                        }
                    }
                }
            }

            if (comparisonInsightLocal != null){
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Analisis Data Hidrasi",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                        .clickable {
                            selectedTip = comparisonInsightLocal
                            showDialog = true
                        },
                    elevation = CardDefaults.elevatedCardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .width(100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(comparisonInsightLocal!!.color.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = comparisonInsightLocal!!.iconRes),
                                    contentDescription = comparisonInsightLocal!!.title,
                                    tint = comparisonInsightLocal!!.color,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = comparisonInsightLocal!!.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = comparisonInsightLocal!!.color,
                                textAlign = TextAlign.Center
                            )
                        }

                        Text(
                            text = comparisonInsightLocal!!.message,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                                .weight(1f),
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        InsightDetailDialog(openDialog = showDialog, onDismiss = { showDialog = false }, tip = selectedTip)

        if (isLoading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

data class RecommendationOption(
    val title: AnnotatedString,
    val details: AnnotatedString,
    val schedule: RecommendationSchedule
)

val InsightItemLevelColor = mapOf(
    "danger" to Color(0xFFBA1A1A),
    "warning" to Color(0xFFFFA000),
    "info" to Color(0xFF2196F3),
    "positive" to Color(0xFF4CAF50)
)

data class InsightItem(
    val iconRes: Int,
    val title: String,
    val color: Color,
    val message: String,
    val level: String = "info",
    val detail: AnnotatedString = buildAnnotatedString { "" },
    val recommendationOptions: List<RecommendationOption> = emptyList()
)

@Composable
fun InsightDetailDialog(openDialog: Boolean, onDismiss: () -> Unit, tip: InsightItem?) {
    if (openDialog && tip != null) {
        val context = LocalContext.current
        var showConfirmationDialog by remember { mutableStateOf(false) }
        var selectedSchedule by remember { mutableStateOf<RecommendationSchedule?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(enabled = false){ },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.75f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = tip.iconRes),
                            contentDescription = tip.title,
                            tint = tip.color,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tip.title,
                            color = tip.color,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = tip.detail.ifBlank { AnnotatedString(tip.message) },
                            textAlign = TextAlign.Justify,
                            fontSize = 14.sp,
                            color = Color.Black.copy(alpha = 0.85f),
                            lineHeight = 20.sp
                        )
                        if (tip.recommendationOptions.isNotEmpty()) {
//                            Spacer(modifier = Modifier.height(16.dp))
                            tip.recommendationOptions.forEach { option ->
                                RecommendationOptionUI(
                                    option = option,
                                    onSetReminderClick = { schedule ->
                                        selectedSchedule = schedule
                                        showConfirmationDialog = true
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            text = "Tutup",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(onClick = onDismiss)
                        )
                    }
                }
            }
        }

        ScheduleConfirmationDialog(
            show = showConfirmationDialog,
            schedule = selectedSchedule,
            onDismiss = { showConfirmationDialog = false },
            onConfirm = {
                selectedSchedule?.let {
                    scheduleDrinkingReminders(context, it)
                    Toast.makeText(context, "Pengingat berhasil diatur!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun RecommendationOptionUI(
    option: RecommendationOption,
    onSetReminderClick: (RecommendationSchedule) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(option.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(option.details, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { onSetReminderClick(option.schedule) },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(painterResource(id = R.drawable.outline_add_alert_24), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text("Atur Pengingat")
        }
    }
}

@Composable
fun ScheduleConfirmationDialog(
    show: Boolean,
    schedule: RecommendationSchedule?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (show && schedule != null) {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val scheduleTimes = schedule.times.joinToString(", ") { it.format(timeFormatter) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Konfirmasi Notifikasi") },
            text = { Text("Anda yakin ingin mengatur pengingat minum untuk ${schedule.times.size} kali pada jam: $scheduleTimes?") },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirm()
                        onDismiss()
                    }
                ) {
                    Text("Ya, Atur")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Batal")
                }
            }
        )
    }
}