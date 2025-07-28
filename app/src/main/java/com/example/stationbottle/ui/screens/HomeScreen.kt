package com.example.stationbottle.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.example.stationbottle.models.UserViewModel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.stationbottle.R
import com.example.stationbottle.client.RetrofitClient.apiService
import com.example.stationbottle.data.PredictionResult
import com.example.stationbottle.data.fetchSensorDataHistory
import com.example.stationbottle.ui.screens.component.DatePickerOutlinedField
import com.example.stationbottle.worker.NotificationWorker
import com.example.stationbottle.worker.calculatePrediction
import com.example.stationbottle.worker.loadXGBoostModel
import com.example.stationbottle.worker.testTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.text.count

@SuppressLint("MutableCollectionMutableState")
@Composable
fun HomeScreen(
    fromDate: String,
    onFromDateChanged: (String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val userViewModel = UserViewModel()
    val userState = userViewModel.getUser(context).collectAsState(initial = null)
    val user = userState.value
    val token = user?.token

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    var todayList by remember { mutableStateOf(linkedMapOf<String, Double>()) }
    var prediksiList by remember { mutableStateOf(linkedMapOf<String, Double>()) }
    var prediksiListAll by remember { mutableStateOf(linkedMapOf<String, Double>()) }
    var sessionList by remember { mutableStateOf<MutableList<Triple<String, String, Double>?>?>(null) }

    var name by remember { mutableStateOf<String?>(null) }
    var dailyGoal by remember { mutableStateOf<Double?>(null) }
    var waktuMulai by remember { mutableStateOf<String?>(null) }
    var waktuSelesai by remember { mutableStateOf<String?>(null) }
    var dataBasedWaktuMulai by remember { mutableStateOf<String?>(null) }
    var dataBasedWaktuSelesai by remember { mutableStateOf<String?>(null) }

    var userWaktuMulai by remember { mutableStateOf<LocalTime?>(null) }
    var userWaktuSelesai by remember { mutableStateOf<LocalTime?>(null) }

    var totalAktual by remember { mutableDoubleStateOf(0.0) }
    var totalPrediksi by remember { mutableDoubleStateOf(0.0) }
    var statusHistory by remember { mutableStateOf<Boolean?>(null) }

    var hasilPred by remember { mutableStateOf<PredictionResult?>(null) }

    val onPermissionGranted = {
        val workManager = WorkManager.getInstance(context)

        workManager.cancelAllWorkByTag("hydration_notifications")

        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        val seconds = user?.frekuensi_notifikasi?.takeIf { it != 0 } ?: 3600

        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val sekarang = LocalTime.now()
        val timeMulai = LocalTime.parse(waktuMulai, timeFormatter)
        val timeSelesai = LocalTime.parse(waktuSelesai, timeFormatter)

        var fromDateTime = LocalDateTime.of(
            when {
                timeMulai > timeSelesai && sekarang < timeSelesai -> today.minusDays(1)
                timeMulai > timeSelesai -> today
                else -> today
            },
            timeMulai
        )

        var toDateTime = LocalDateTime.of(
            when {
                timeMulai > timeSelesai && sekarang < timeSelesai -> today
                timeMulai > timeSelesai -> today.plusDays(1)
                else -> today
            },
            timeSelesai
        )

        val timeList = mutableListOf<String>()
        var current = fromDateTime
        var selisih: Long = 0L

        while (!current.isAfter(toDateTime)) {
            timeList.add(current.format(timeFormatter))
            if (current.isAfter(now)) {
                selisih = Duration.between(now, current).toMillis()
                break
            }

            current = current.plusSeconds(seconds.toLong())
        }

        if(selisih > 0L){
            val initialWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(selisih - 1000L, TimeUnit.MILLISECONDS)
                .addTag("hydration_notifications")
                .build()

            WorkManager.getInstance(context).enqueue(initialWorkRequest)
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                onPermissionGranted()
            } else {
                println("Izin notifikasi ditolak.")
            }
        }
    )

    LaunchedEffect(user) {
        user?.let {
            isLoading = true

            userViewModel.getUserData(context, it.id, token.toString())

            name = it.name
            dailyGoal = it.daily_goal
            waktuMulai = it.waktu_mulai
            waktuSelesai = it.waktu_selesai

            if (
                it.waktu_mulai != null && it.waktu_mulai != "" &&
                it.waktu_selesai != null && it.waktu_selesai != ""
            ){
                hasilPred = calculatePrediction(
                    context = context,
                    user = it,
                    waktuMulai = it.waktu_mulai,
                    waktuSelesai = it.waktu_selesai,
                    todayDate = fromDate,
                )

                hasilPred?.let {
                    totalAktual = it.todayAktual
                    totalPrediksi = it.todayPrediksi
                    todayList = it.todayList
                    prediksiList = it.prediksiList
                    statusHistory = it.statusHistory
                    sessionList = it.drinkSessionList
                    userWaktuMulai = it.userWaktuMulai
                    userWaktuSelesai = it.userWaktuSelesai
                }

                println(sessionList)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onPermissionGranted()
                }

                val xgboost = loadXGBoostModel(context, it.id)

                if (xgboost != null) {
                    var isBedaHari = false
                    if(dateFormat.parse(it.waktu_mulai)!!.time > dateFormat.parse(it.waktu_selesai)!!.time){
                        isBedaHari = true
                    }

                    var baseDate = LocalDate.parse(fromDate, formatter)

                    dataBasedWaktuMulai = userWaktuMulai.toString()
                    dataBasedWaktuSelesai = userWaktuSelesai.toString()

                    var waktuPred = userWaktuMulai.toString()

                    var dayPrediksi = baseDate.format(formatter)

                    val (prediksiAir, prediksiWaktu) = xgboost.prediksi(
                        waktuPred.toString(),
                        userWaktuSelesai.toString(),
                        dayPrediksi.toString(),
                        isBedaHari
                    )!!

                    // Hasil prediksiAir = array jumlah minum
                    // prediksiWaktu = array waktu delta dalam detik dari waktuMulai
                    val waktuPrediksiList = mutableListOf<String>()

                    val formatterPrediksi = if (waktuPred.count { it == ':' } == 2) {
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

                    prediksiListAll = waktuPrediksiList.zip(prediksiAir.asList())
                        .toMap(LinkedHashMap())

                    // Anda bisa tampilkan atau simpan hasil ini
                    println("Prediksi total minum hari ini: ${prediksiAir.sum()} mL")
                }
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
                text = "Home Screen",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            DatePickerOutlinedField(
                label = "Data Hari",
                date = fromDate,
                onDateSelected = { selectedDate ->
                    onFromDateChanged(selectedDate)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Progress Minum Anda",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (dailyGoal != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Aktual",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val progress = if (dailyGoal!! != 0.0) (totalAktual / dailyGoal!!).toFloat() else 0f
                        val percentage = (progress * 100).roundToInt()

                        Box(
                            modifier = Modifier.size(100.dp),
                            contentAlignment = Alignment.Center
                        ) {

                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(100.dp),
                                strokeWidth = 8.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.inversePrimary
                            )

                            Text(
                                text = "$percentage%",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = buildAnnotatedString {
                                append("${"%.0f".format(totalAktual)} mL")
                                addStyle(SpanStyle(fontWeight = FontWeight.Medium), 0, length)

                                append(" / ")

                                val startOfDailyGoal = length
                                append("${"%.0f".format(dailyGoal)} mL")
                                addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), startOfDailyGoal, length)
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Prediksi",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        var textError =
                            if (waktuMulai == null || waktuMulai == "" ||
                                waktuSelesai == null || waktuSelesai == ""
                            ) {
                                "Isi Semua Data di Profil untuk Prediksi AI"
                            } else if (statusHistory == false) {
                                "Data Historis Tidak Tersedia"
                            } else {
                                "Error"
                            }

                        if (waktuMulai == null || waktuMulai == "" ||
                            waktuSelesai == null || waktuSelesai == ""
                            || (statusHistory == false && totalPrediksi == 0.0)
                        ) {
                            Box(
                                modifier = Modifier.size(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = textError,
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {

                            val progress = if (dailyGoal!! != 0.0) (totalPrediksi / dailyGoal!!).toFloat() else 0f
                            val percentage = (progress * 100).roundToInt()

                            Box(
                                modifier = Modifier.size(100.dp),
                                contentAlignment = Alignment.Center
                            ) {

                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(100.dp),
                                    strokeWidth = 8.dp,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    trackColor = MaterialTheme.colorScheme.tertiaryContainer
                                )

                                Text(
                                    text = "$percentage%",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = buildAnnotatedString {
                                    append("${"%.0f".format(totalPrediksi)} mL")
                                    addStyle(SpanStyle(fontWeight = FontWeight.Medium), 0, length)

                                    append(" / ")

                                    val startOfDailyGoal = length
                                    append("${"%.0f".format(dailyGoal)} mL")
                                    addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), startOfDailyGoal, length)
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Minum Hari Ini",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (todayList.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Data Hari Ini belum tersedia.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    todayList.entries.toList().forEachIndexed { index, (waktu, minum) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.elevatedCardElevation(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            border = BorderStroke(
                                width = 0.75.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(modifier = Modifier.padding(horizontal = 4.dp))

                                Icon(
                                    painter = painterResource(id = R.drawable.outline_access_time_24),
                                    contentDescription = "Icon",
                                    modifier = Modifier
                                        .size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                                Column(
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    val session = sessionList?.getOrNull(index)
                                    val sessionBefore = sessionList?.getOrNull(index - 1)

                                    val inputFormat =
                                        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    val outputFormat =
                                        SimpleDateFormat("hh:mm a", Locale.getDefault())
                                    val waktuBiasa = inputFormat.parse(waktu)
                                    val waktuBiasaFormat = outputFormat.format(waktuBiasa!!)
                                    var waktuFormat = waktuBiasaFormat

                                    if (session != null){
                                        val waktuMulai = inputFormat.parse(session.first)
                                        val waktuAkhir = inputFormat.parse(session.second)
                                        var waktuMulaiBefore: Date
                                        var waktuMulaiBeforeFormat: String

                                        if (userWaktuMulai == null) {
                                            waktuMulaiBeforeFormat = "Memuat..."
                                        } else if (index == 0) {
                                            val formatterToString = DateTimeFormatter.ofPattern("HH:mm:ss")
                                            val waktuMulaiString = userWaktuMulai?.format(formatterToString)
                                            waktuMulaiBefore = inputFormat.parse(waktuMulaiString)
                                            waktuMulaiBeforeFormat = outputFormat.format(waktuMulaiBefore)
                                        } else {
                                            waktuMulaiBefore = inputFormat.parse(sessionBefore?.second)!!
                                            waktuMulaiBeforeFormat = outputFormat.format(waktuMulaiBefore)
                                        }

                                        val waktuMulaiFormat = outputFormat.format(waktuMulai!!)
                                        val waktuAkhirFormat = outputFormat.format(waktuAkhir!!)

                                        val selisihMillis = waktuAkhir.time - waktuMulai.time
                                        val selisihMenit = selisihMillis / (60 * 1000)

                                        if (selisihMenit < 5L && session.third > 100) {
                                            waktuFormat = "$waktuMulaiBeforeFormat - $waktuAkhirFormat"
                                        } else {
                                            waktuFormat = "$waktuMulaiFormat - $waktuAkhirFormat"
                                        }
                                    }

                                    Text(
                                        text = waktuFormat,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Aktual",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "${minum.toInt()} ml",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    textAlign = TextAlign.End
                                )

                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Prediksi Minum Berikutnya",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (prediksiList.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Data Prediksi belum tersedia.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    prediksiList.entries.toList().forEachIndexed { index, (waktu, minum) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.elevatedCardElevation(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            border = BorderStroke(
                                width = 0.75.dp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(modifier = Modifier.padding(horizontal = 4.dp))

                                Icon(
                                    painter = painterResource(id = R.drawable.outline_insights_24),
                                    contentDescription = "Icon",
                                    modifier = Modifier
                                        .size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                                Column(
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.Center
                                ) {

                                    val sessionBefore = sessionList?.lastOrNull()

                                    val inputFormat =
                                        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    val outputFormat =
                                        SimpleDateFormat("hh:mm a", Locale.getDefault())
                                    val waktuBiasa = inputFormat.parse(waktu)
                                    var waktuFormat = outputFormat.format(waktuBiasa!!)

                                    var waktuMulaiBefore: Date
                                    var waktuMulaiBeforeFormat: String

                                    if (sessionBefore != null && index == 0){
                                        val waktuMulai = inputFormat.parse(sessionBefore.second)

                                        val waktuMulaiFormat = outputFormat.format(waktuMulai!!)
//                                        val waktuAkhirFormat = outputFormat.format(waktuAkhir!!)

                                        waktuFormat = "$waktuMulaiFormat - $waktuFormat"
                                    } else if (index == 0) {
                                        if (userWaktuMulai == null) {
                                            waktuMulaiBeforeFormat = "Memuat..."
                                        } else {
                                            val formatterToString = DateTimeFormatter.ofPattern("HH:mm:ss")
                                            val waktuMulaiString = userWaktuMulai?.format(formatterToString)
                                            waktuMulaiBefore = inputFormat.parse(waktuMulaiString)
                                            waktuMulaiBeforeFormat = outputFormat.format(waktuMulaiBefore)

                                            waktuFormat = "$waktuMulaiBeforeFormat - $waktuFormat"
                                        }
                                    } else {
                                        waktuMulaiBefore = inputFormat.parse(prediksiList.entries.toList().getOrNull(index - 1)?.key)
                                        waktuMulaiBeforeFormat = outputFormat.format(waktuMulaiBefore)

                                        waktuFormat = "$waktuMulaiBeforeFormat - $waktuFormat"
                                    }

                                    Text(
                                        text = waktuFormat,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Prediksi",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "${minum.toInt()} ml",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Tips Mencapai Target Hidrasi Harian",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (prediksiListAll.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Perlu Data Prediksi Untuk Mendapatkan Tips",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
            } else {
//                val prediksiEntries =
//                    prediksiListAll.entries.toList().take(2) // Konversi ke List lalu ambil 2 pertama
//                val minumTips = prediksiEntries.firstOrNull()?.value ?: 0.0
//                var minumTotal = 0.0
//                val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
//
//                val waktuTips = if (prediksiEntries.size == 2) {
//                    val waktu1 = dateFormat.parse(prediksiEntries[0].key)?.time ?: 0L
//                    val waktu2 = dateFormat.parse(prediksiEntries[1].key)?.time ?: 0L
//                    waktu2 - waktu1
//                } else {
//                    0L
//                }
                val prediksiList = prediksiListAll.entries.toList().sortedBy { it.key }
                val waktuSelisihList = prediksiList.zipWithNext().map { (a, b) ->
                    val waktu1 = dateFormat.parse(a.key)?.time ?: 0L
                    val waktu2 = dateFormat.parse(b.key)?.time ?: 0L
                    waktu2 - waktu1
                }
                val waktuTips = waktuSelisihList.average().toLong()
                val minumTips = prediksiList.map { it.value }.average()
                var minumTotal = 0.0

                if (waktuTips > 0) {
                    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

                    val today = LocalDate.now()
                    val sekarang = LocalTime.now()
                    val timeMulai = LocalTime.parse(dataBasedWaktuMulai, timeFormatter)
                    val timeSelesai = LocalTime.parse(dataBasedWaktuSelesai, timeFormatter)

                    var fromDateTime = LocalDateTime.of(
                        when {
                            timeMulai > timeSelesai && sekarang < timeSelesai -> today.minusDays(1)
                            timeMulai > timeSelesai -> today
                            else -> today
                        },
                        timeMulai
                    )

                    var toDateTime = LocalDateTime.of(
                        when {
                            timeMulai > timeSelesai && sekarang < timeSelesai -> today
                            timeMulai > timeSelesai -> today.plusDays(1)
                            else -> today
                        },
                        timeSelesai
                    )

                    val timeList = mutableListOf<String>()
                    var current = fromDateTime

                    while (!current.isAfter(toDateTime)) {
                        current = current.plusSeconds(waktuTips.div(1000).toLong())

                        if (current.isAfter(toDateTime)) {
                            break
                        }

                        timeList.add(current.format(timeFormatter))
                    }

                    timeList.forEach {
                        minumTotal += minumTips
                    }

                    val waktuDetik = waktuTips.div(1000)
                    val jam = waktuDetik.div(3600)
                    val menit = (waktuDetik.rem(3600)).div(60)

                    val minumRekomendasi =
                        ceil(dailyGoal!!.div(timeList.size.toDouble())).toInt()

                    val selisih = Duration.between(fromDateTime, toDateTime)
                    val selisihJam = selisih.toMinutes()
                    val frekuensi = dailyGoal!!.div(minumTips).toInt()
                    val rekomendasi =
                        ceil(selisihJam.div(frekuensi.toDouble())).toInt()

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.elevatedCardElevation(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            border = BorderStroke(
                                width = 0.75.dp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = buildAnnotatedString {
                                        append(
                                            "Untuk saat ini, Kecerdasan Buatan pada sistem kami memprediksi " +
                                            "bahwa setiap Anda minum, Anda akan minum sebanyak "
                                        )
                                        withStyle(style = SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        )) {
                                            append("${minumTips.toInt()} mL")
                                        }
                                        append(" dan anda akan minum setiap ")
                                        withStyle(style = SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        )) {
                                            append(
                                                (if (jam != 0L) "$jam jam " else "") +
                                                (if (menit != 0L) "$menit menit" else "")
                                            )
                                        }
                                        append(". Berdasarkan data tersebut total minum Anda perhari diprediksi sebanyak ")
                                        withStyle(style = SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        )) {
                                            append("${minumTotal.toInt()} mL")
                                        }
                                        append(".")
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Light,
                                    textAlign = TextAlign.Justify
                                )

                                if (minumTotal < dailyGoal!!) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = buildAnnotatedString {
                                            append("Karena prediksi minum anda dibawah target hidrasi harian Anda yaitu ")
                                            withStyle(style = SpanStyle(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            )) {
                                                append("${minumTotal.toInt()} mL")
                                            }
                                            append(" dari ")
                                            withStyle(style = SpanStyle(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            )) {
                                                append("${dailyGoal!!.toInt()} mL")
                                            }
                                            append(", kami sarankan Anda untuk melakukan salah satu atau dua tips berikut:")
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.Justify
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Menurut Kecerdasan Buatan, Target hidrasi harian anda akan" +
                                                " terpenuhi, pertahankan kebiasaan anda!",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.Justify
                                    )
                                }
                            }
                        }
                        if (minumTotal < dailyGoal!!) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                elevation = CardDefaults.elevatedCardElevation(4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                border = BorderStroke(
                                    width = 0.75.dp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "1. Tingkatkan Konsumsi Air",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.Justify
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Jika Anda merasa sulit untuk lebih sering minum, solusi " +
                                                "yang dapat dilakukan adalah meningkatkan jumlah air yang " +
                                                "diminum setiap kali Anda minum.",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.Justify
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = buildAnnotatedString{
                                            append(
                                                "Berdasarkan perhitungan Kecerdasan Buatan, untuk mencapai " +
                                                "target hidrasi harian Anda, Anda perlu mengonsumsi sekitar "
                                            )
                                            withStyle(style = SpanStyle(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            )) {
                                                append("$minumRekomendasi mL")
                                            }
                                            append(" setiap Anda minum.")
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.Justify
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Dengan cara ini, meskipun frekuensi minum tetap sama, " +
                                                "kebutuhan hidrasi Anda tetap terpenuhi.",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.Justify
                                    )
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                elevation = CardDefaults.elevatedCardElevation(4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                border = BorderStroke(
                                    width = 0.75.dp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "2. Tingkatkan Frekuensi Minum Air",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.Justify
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Apabila Anda merasa jumlah air yang diminum sudah cukup " +
                                                "banyak setiap kali minum, tetapi masih belum mencapai " +
                                                "target harian, maka meningkatkan frekuensi minum bisa " +
                                                "menjadi solusi.",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.Justify
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = buildAnnotatedString {
                                            append("Berdasarkan kalkulasi, Anda perlu minum setiap ")
                                            withStyle(style = SpanStyle(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            )) {
                                                append("$rekomendasi menit")
                                            }
                                            append(", agar mencapai target hidrasi harian Anda.")
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.Justify
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Coba ganti frekuensi notifikasi anda di halaman profil " +
                                                "untuk membantu Anda membentuk kebiasaan ini secara bertahap.",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.Justify
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Data Prediksi Tidak Mencukupi untuk Mendapatkan Tips",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

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