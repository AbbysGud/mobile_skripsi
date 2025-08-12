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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.stationbottle.client.RetrofitClient
import com.example.stationbottle.client.RetrofitClient.apiService
import com.example.stationbottle.data.ModeRequest
import com.example.stationbottle.data.PredictionResult
import com.example.stationbottle.data.fetchSensorDataHistory
import com.example.stationbottle.ui.screens.component.DatePickerOutlinedField
import com.example.stationbottle.worker.NotificationWorker
import com.example.stationbottle.worker.calculatePrediction
import com.example.stationbottle.worker.loadXGBoostModel
import com.example.stationbottle.worker.predictWholeDay
import com.example.stationbottle.worker.testTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
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

    var userWaktuMulai by remember { mutableStateOf<LocalTime?>(null) }
    var userWaktuSelesai by remember { mutableStateOf<LocalTime?>(null) }

    var totalAktual by remember { mutableDoubleStateOf(0.0) }
    var totalPrediksi by remember { mutableDoubleStateOf(0.0) }
    var totalPrediksiWhole by remember { mutableDoubleStateOf(0.0) }
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
//                prediksiListAll = predictWholeDay(
//                    context = context,
//                    user = it,
//                    fromDate =
//                )

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
                    totalPrediksiWhole = it.todayPrediksiWhole
                    prediksiListAll = it.prediksiListWhole
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onPermissionGranted()
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            val progress =
                                if (dailyGoal!! != 0.0) (totalAktual / dailyGoal!!).toFloat() else 0f
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

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = buildAnnotatedString {
                                    append("${"%.0f".format(totalAktual)} mL")
                                    addStyle(SpanStyle(fontWeight = FontWeight.Medium), 0, length)

                                    append(" / ")

                                    val startOfDailyGoal = length
                                    append("${"%.0f".format(dailyGoal)} mL")
                                    addStyle(
                                        SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        ),
                                        startOfDailyGoal,
                                        length
                                    )
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                val remainingAktual = dailyGoal!! - totalAktual
                                Text(
                                    text = if (todayList.isEmpty()) {
                                        "Anda belum minum hari ini!"
                                    } else if (remainingAktual > 0) {
                                        "${"%.0f".format(remainingAktual)} mL menuju target"
                                    } else {
                                        "Target tercapai!"
                                    },
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold,
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

                            if (session != null || LocalDate.now() == LocalDate.parse(fromDate, formatter)) {
                                val inputFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                val waktuAkhirDate: Date? = try {
                                    if (LocalDate.now() == LocalDate.parse(fromDate, formatter)){
                                        val currentTimeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
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

                                    println(selisihDuration.toMinutes())

                                    if (selisihDuration.isNegative) {
                                        selisihDuration = selisihDuration.plusDays(1)
                                    }

                                    val selisihMalam = Duration.between(waktuAkhirLocalTime, waktuBeres)

                                    val formattedSelisihMalam = if (selisihMalam.isNegative) {
                                        "Sudah lewat tengah malam"
                                    } else {
                                        formatDurationToReadableString(selisihMalam)
                                    }


                                    Spacer(modifier = Modifier.height(14.dp))
                                    Divider(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(),
//                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "Sisa waktu minum hingga \npukul ${userWaktuSelesai?.format(DateTimeFormatter.ofPattern("HH:mm"))}:",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = formatDurationToReadableString(selisihDuration),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Sisa waktu hingga dini hari \n(pukul 23:59):",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = formattedSelisihMalam,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
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
                    fontSize = 16.sp,
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
                                        .size(28.dp),
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

                                    var jam = 0L
                                    var menit = 0L

                                    if (session != null){
                                        val waktuMulai = inputFormat.parse(session.first)
                                        val waktuAkhir = inputFormat.parse(session.second)
                                        var waktuMulaiBefore: Date = Date()
                                        var waktuMulaiBeforeFormat: String

                                        if (userWaktuMulai == null) {
                                            waktuMulaiBeforeFormat = "Memuat..."
                                        } else if (index == 0) {
                                            val formatterToString = DateTimeFormatter.ofPattern("HH:mm:ss")
                                            val waktuMulaiString = userWaktuMulai?.format(formatterToString)
                                            waktuMulaiBefore = inputFormat.parse(waktuMulaiString)
                                            waktuMulaiBeforeFormat = outputFormat.format(waktuMulaiBefore)
                                        } else {
                                            waktuMulaiBefore = inputFormat.parse(sessionBefore?.second)
                                            waktuMulaiBeforeFormat = outputFormat.format(waktuMulaiBefore)
                                        }

                                        val waktuMulaiFormat = outputFormat.format(waktuMulai!!)
                                        val waktuAkhirFormat = outputFormat.format(waktuAkhir!!)

                                        val selisihMillis = waktuAkhir.time - waktuMulai.time
                                        val selisihMenit = selisihMillis / (60 * 1000)

//                                        if (selisihMenit < 5L && session.third > 100) {
                                        waktuFormat = "$waktuMulaiBeforeFormat - $waktuAkhirFormat"

                                        val millis = waktuAkhir.time - waktuMulaiBefore.time
                                        val fullMenit = millis / (1000 * 60)
                                        menit = fullMenit % 60
                                        jam = millis / (1000 * 60 * 60)

//                                        } else {
//                                            waktuFormat = "$waktuMulaiBeforeFormat - $waktuAkhirFormat"
//
//                                            val millis = waktuAkhir.time - waktuMulai.time
//                                            val fullMenit = millis / (1000 * 60)
//                                            menit = fullMenit % 60
//                                            jam = millis / (1000 * 60 * 60)
//                                        }
                                    }

                                    Text(
                                        text = waktuFormat,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$jam jam $menit menit",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                                Text(
                                    text = "${minum.toInt()} ml",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Thin,
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
                text = "Prediksi Total Minum Anda",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (dailyGoal != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.elevatedCardElevation(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.
                            fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
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
                                        fontSize = 16.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "",
                                    fontSize = 16.sp,
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

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = buildAnnotatedString {
                                        append("${"%.0f".format(totalPrediksi)} mL")
                                        addStyle(SpanStyle(fontWeight = FontWeight.Medium), 0, length)

                                        append(" / ")

                                        val startOfDailyGoal = length
                                        append("${"%.0f".format(dailyGoal)} mL")
                                        addStyle(
                                            SpanStyle(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.tertiary
                                            ),
                                            startOfDailyGoal,
                                            length)
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
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
                    fontSize = 16.sp,
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
                                        .size(28.dp),
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

                                    var jam = 0L
                                    var menit = 0L

                                    if (sessionBefore != null && index == 0){
                                        val waktuMulai = inputFormat.parse(sessionBefore.second)

                                        val waktuMulaiFormat = outputFormat.format(waktuMulai!!)
//                                        val waktuAkhirFormat = outputFormat.format(waktuAkhir!!)

                                        waktuFormat = "$waktuMulaiFormat - $waktuFormat"

                                        val millis = waktuBiasa.time - waktuMulai.time
                                        val fullMenit = millis / (1000 * 60)
                                        menit = fullMenit % 60
                                        jam = millis / (1000 * 60 * 60)
                                    } else if (index == 0) {
                                        if (userWaktuMulai == null) {
                                            waktuMulaiBeforeFormat = "Memuat..."
                                        } else {
                                            val formatterToString = DateTimeFormatter.ofPattern("HH:mm:ss")
                                            val waktuMulaiString = userWaktuMulai?.format(formatterToString)
                                            waktuMulaiBefore = inputFormat.parse(waktuMulaiString)
                                            waktuMulaiBeforeFormat = outputFormat.format(waktuMulaiBefore)

                                            waktuFormat = "$waktuMulaiBeforeFormat - $waktuFormat"

                                            val millis = waktuBiasa.time - waktuMulaiBefore.time
                                            val fullMenit = millis / (1000 * 60)
                                            menit = fullMenit % 60
                                            jam = millis / (1000 * 60 * 60)
                                        }
                                    } else {
                                        waktuMulaiBefore = inputFormat.parse(prediksiList.entries.toList().getOrNull(index - 1)?.key)
                                        waktuMulaiBeforeFormat = outputFormat.format(waktuMulaiBefore)

                                        waktuFormat = "$waktuMulaiBeforeFormat - $waktuFormat"

                                        val millis = waktuBiasa.time - waktuMulaiBefore.time
                                        val fullMenit = millis / (1000 * 60)
                                        menit = fullMenit % 60
                                        jam = millis / (1000 * 60 * 60)
                                    }

                                    Text(
                                        text = waktuFormat,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$jam jam $menit menit",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                                Text(
                                    text = "${minum.toInt()} ml",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Thin,
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