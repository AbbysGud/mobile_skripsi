package com.example.stationbottle.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.stationbottle.ui.theme.AppTheme
import com.example.stationbottle.models.UserViewModel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.stationbottle.R
import com.example.stationbottle.data.PredictionResult
import com.example.stationbottle.worker.NotificationWorker
import com.example.stationbottle.worker.calculatePrediction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@SuppressLint("MutableCollectionMutableState")
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val userViewModel = UserViewModel()
    val userState = userViewModel.getUser(context).collectAsState(initial = null)
    val user = userState.value
    val userId = user?.id
    val token = user?.token

    var todayList by remember { mutableStateOf(linkedMapOf<String, Double>()) }
    var prediksiList by remember { mutableStateOf(linkedMapOf<String, Double>()) }

    var selisihList by remember { mutableStateOf(mutableListOf<Long?>()) }

    var name by remember { mutableStateOf<String?>(null) }
    var dailyGoal by remember { mutableStateOf<Double?>(null) }
    var waktuMulai by remember { mutableStateOf<String?>(null) }
    var waktuSelesai by remember { mutableStateOf<String?>(null) }

    var totalAktual by remember { mutableDoubleStateOf(0.0) }
    var totalPrediksi by remember { mutableDoubleStateOf(0.0) }
    var statusHistory by remember { mutableStateOf<Boolean?>(null) }

    var hasilPred by remember { mutableStateOf<PredictionResult?>(null) }

    LaunchedEffect(user) {
        user?.let {
            userViewModel.getUserData(context, it.id, token.toString())
            name = it.name
            dailyGoal = it.daily_goal
            waktuMulai = it.waktu_mulai
            waktuSelesai = it.waktu_selesai

            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val startTime = dateFormat.parse(waktuMulai!!)
            val endTime = dateFormat.parse(waktuSelesai!!)
            val waktuSekarang = dateFormat.format(Date())

            val calendar = Calendar.getInstance()
            calendar.time = startTime!!

            val timeList = mutableListOf<String>()

            while (calendar.time.before(endTime)) {
                val formattedTime = dateFormat.format(calendar.time)
                timeList.add(formattedTime)

                calendar.add(Calendar.SECOND, 3600)
            }

            timeList.forEach { time ->
                selisihList.add(dateFormat.parse(time)!!.time - dateFormat.parse(waktuSekarang)!!.time)
            }
        }
    }

    LaunchedEffect(userId) {
        if (userId != null) {
            hasilPred = calculatePrediction(
                context = context,
                userId = userId,
                waktuMulai = waktuMulai!!,
                waktuSelesai = waktuSelesai!!,
            )
            totalAktual = hasilPred!!.todayAktual
            totalPrediksi = hasilPred!!.todayPrediksi
            todayList = hasilPred!!.todayList
            prediksiList = hasilPred!!.prediksiList
            statusHistory = hasilPred!!.statusHistory
        }
    }

    val onPermissionGranted = {
        val workManager = WorkManager.getInstance(context)

        workManager.cancelAllWorkByTag("hydration_notifications")

        selisihList.forEach { selisih ->
            if (selisih != null && selisih > 0) {
                val initialWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                    .setInitialDelay(selisih, TimeUnit.MILLISECONDS)
                    .addTag("hydration_notifications")
                    .build()

                WorkManager.getInstance(context).enqueue(initialWorkRequest)
            }
        }
    }

    if (hasilPred?.isNotif == true) {
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

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onPermissionGranted()
            }
        }
    }

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
                    CircularProgressIndicator(
                        progress = { (totalAktual / dailyGoal!!).toFloat() },
                        modifier = Modifier.size(100.dp),
                        strokeWidth = 8.dp,
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${"%.1f".format(totalAktual)} / ${"%.1f".format(dailyGoal!!)} mL",
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
                        if(waktuMulai == null || waktuMulai == "" ||
                            waktuSelesai == null || waktuSelesai == "") {
                            "Isi Semua Data di Profil untuk Prediksi AI"
                        } else if (statusHistory == false) {
                            "Data Historis Tidak Tersedia"
                        } else {
                            "Error"
                        }

                    if(waktuMulai == null || waktuMulai == "" ||
                        waktuSelesai == null || waktuSelesai == ""
                        || statusHistory == false){
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
                        CircularProgressIndicator(
                            progress = { (totalPrediksi / dailyGoal!!).toFloat() },
                            modifier = Modifier.size(100.dp),
                            strokeWidth = 8.dp,
                            color = MaterialTheme.colorScheme.primaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${"%.1f".format(totalPrediksi)} / ${"%.1f".format(dailyGoal!!)} mL",
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
            ){
                todayList.forEach { (waktu, minum) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.elevatedCardElevation(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                                val inputFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                val waktuBiasa = inputFormat.parse(waktu)
                                val waktuFormat = outputFormat.format(waktuBiasa!!)

                                Text(
                                    text = waktuFormat,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Aktual",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "${minum.toInt()} ml",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium,
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
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            ){
                prediksiList.forEach { (waktu, minum) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.elevatedCardElevation(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                                val inputFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                val waktuBiasa = inputFormat.parse(waktu)
                                val waktuFormat = outputFormat.format(waktuBiasa!!)

                                Text(
                                    text = waktuFormat,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Prediksi",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "${minum.toInt()} ml",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium,
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
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    AppTheme {
        val navController = rememberNavController()
        HomeScreen(navController)
    }
}