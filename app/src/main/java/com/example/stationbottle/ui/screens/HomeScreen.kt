package com.example.stationbottle.ui.screens

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
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.stationbottle.R
import com.example.stationbottle.data.RetrofitClient
import com.example.stationbottle.data.SensorDataResponse
import com.example.stationbottle.data.XGBoost
import com.example.stationbottle.data.convertUtcToWIB
import com.example.stationbottle.data.retry
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.collections.set

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val userViewModel = UserViewModel()
    val userState = userViewModel.getUser(context).collectAsState(initial = null)
    val user = userState.value
    val userId = user?.id
    val token = user?.token

    var todayData by remember { mutableStateOf<SensorDataResponse?>(null) }
    var todayList = remember { linkedMapOf<String, Double>() }
    var prediksiList = remember { linkedMapOf<String, Double>() }
    var historyData by remember { mutableStateOf<SensorDataResponse?>(null) }
    var historyList = remember { linkedMapOf<String, Double>() }
    var tanggalList = remember { mutableListOf<String>() }
    var waktuList = remember { mutableListOf<String>() }
    var minumList = remember { mutableListOf<Double>() }
    var waktuListToday = remember { mutableListOf<String>() }
    var minumListToday = remember { mutableListOf<Double>() }
    var waktuListPrediksi = remember { mutableListOf<String>() }
    var minumListPrediksi = remember { mutableListOf<Double>() }

    var name by remember { mutableStateOf<String?>(null) }
    var dailyGoal by remember { mutableStateOf<Double?>(null) }
    var waktuMulai by remember { mutableStateOf<String?>(null) }
    var waktuSelesai by remember { mutableStateOf<String?>(null) }
    var totalAktual by remember { mutableDoubleStateOf(0.0) }
    var totalPrediksi by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(user) {
        user?.let {
            userViewModel.getUserData(context, it.id, token.toString())
            name = it.name
            dailyGoal = it.daily_goal
            waktuMulai = it.waktu_mulai
            waktuSelesai = it.waktu_selesai
        }
    }

    LaunchedEffect(userId) {
        if (userId != null) {
            try {
//                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val today = "2025-01-16"

                historyData = retry(times = 3) {
                    RetrofitClient.apiService.getSensorDataHistory(userId, today)
                }

                historyData?.data?.forEach { sensorData ->
                    if (sensorData.previous_weight != 0.0 && sensorData.previous_weight > sensorData.weight) {
                        val tanggal = convertUtcToWIB(sensorData.created_at, includeTime = true).toString()
                        tanggalList.add(tanggal)

                        val waktu = convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()
                        waktuList.add(waktu)

                        val minum = kotlin.math.abs(sensorData.previous_weight - sensorData.weight)
                        minumList.add(minum)

                        historyList[convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()] =
                            sensorData.previous_weight - sensorData.weight
                    }
                }

                try {
                    todayData = retry(times = 3) {
                        RetrofitClient.apiService.getSensorData(today, today, userId)
                    }

                    todayData?.data?.forEach { sensorData ->
                        if (sensorData.previous_weight != 0.0 && sensorData.previous_weight > sensorData.weight) {
                            val tanggal = convertUtcToWIB(sensorData.created_at, includeTime = true).toString()
                            tanggalList.add(tanggal)

                            val waktu = convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()
                            waktuList.add(waktu)
                            waktuListToday.add(waktu)

                            val minum = kotlin.math.abs(sensorData.previous_weight - sensorData.weight)
                            minumList.add(minum)
                            minumListToday.add(minum)

                            todayList[convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()] =
                                sensorData.previous_weight - sensorData.weight
                        }
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("404") == true) {
                        println("Data hari ini tidak ditemukan, hanya menggunakan data historis.")
                    } else {
                        throw e
                    }
                }

                val tanggalArray = tanggalList.toTypedArray()
                val waktuArray = waktuList.toTypedArray()
                val minumArray = minumList.toDoubleArray()
                val waktuArrayToday = waktuListToday.toTypedArray()
                val minumArrayToday = minumListToday.toDoubleArray()

                val model = XGBoost()

                model.latihModel(tanggalArray, waktuArray, minumArray, maxIterasi = 10)

                if (
                    waktuMulai != null && waktuMulai != ""
                    && waktuSelesai != null && waktuSelesai != ""
                ) {
                    val lastTime = if (waktuArrayToday.isNotEmpty()) waktuArrayToday.last() else waktuMulai
                    val (prediksiAir, prediksiWaktu) = model.prediksi(
                        lastTime.toString(),
                        waktuSelesai.toString(),
                        tanggalArray.last()
                    )!!

                    totalPrediksi = prediksiAir.sum() + minumArrayToday.sum()
                    totalAktual = minumArrayToday.sum()

                    prediksiAir.forEach { minumListPrediksi.add(it) }

                    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                    var currentTime = LocalTime.parse(lastTime, formatter)

                    prediksiWaktu.forEach { seconds ->
                        currentTime = currentTime.plusSeconds(seconds.toLong())

                        waktuListPrediksi.add(currentTime.format(formatter))
                    }

                    waktuListPrediksi.forEachIndexed { index, waktu ->
                        prediksiList[waktu] = minumListPrediksi[index]
                    }
                }
            } catch (e: Exception) {
                println("Error fetching data: ${e.message}")
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

                if (
                    waktuMulai != null && waktuMulai != ""
                    && waktuSelesai != null && waktuSelesai != ""
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Prediksi",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Prediksi",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.size(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Isi Semua Data di Profil untuk Prediksi AI",
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
                                val waktuFormat = outputFormat.format(waktuBiasa)

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
                                val waktuFormat = outputFormat.format(waktuBiasa)

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