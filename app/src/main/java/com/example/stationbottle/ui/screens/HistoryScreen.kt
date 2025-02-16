package com.example.stationbottle.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stationbottle.client.RetrofitClient.apiService
import com.example.stationbottle.data.SensorData
import com.example.stationbottle.data.SensorDataResponse
import com.example.stationbottle.data.fetchTodaySensorData
import com.example.stationbottle.service.convertUtcToWIB
import com.example.stationbottle.models.UserViewModel
import com.example.stationbottle.ui.screens.component.BarchartWithSolidBars
import com.example.stationbottle.ui.screens.component.DatePickerOutlinedField
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.forEach
import kotlin.math.abs

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val userViewModel = UserViewModel()
    val userState = userViewModel.getUser(context).collectAsState(initial = null)
    val user = userState.value

    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var sensorDataResponse by remember { mutableStateOf<SensorDataResponse?>(null) }
    var todayData by remember { mutableStateOf<List<SensorData>?>(null) }
    var todayList = remember { linkedMapOf<String, Double>() }
    var totalPerDay = remember { linkedMapOf<String, Double>() }

    LaunchedEffect(user) {
        if (user?.waktu_mulai != null && user.waktu_selesai != null) {
            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val startTime = dateFormat.parse(user.waktu_mulai)!!
            val endTime = dateFormat.parse(user.waktu_selesai)!!
            val waktuSekarang = dateFormat.parse(dateFormat.format(Date()))!!

            var from_date = LocalDate.now().format(formatter)
            var to_date = LocalDate.now().plusDays(1).format(formatter)

            if(startTime.time > endTime.time && waktuSekarang.time < endTime.time){
                from_date = LocalDate.now().minusDays(1).format(formatter)
                to_date = LocalDate.now().format(formatter)
            }

            todayData = if (startTime.time > endTime.time) {
                fetchTodaySensorData(apiService, user.id, from_date, to_date, user.waktu_mulai, user.waktu_selesai)
            } else {
                fetchTodaySensorData(apiService, user.id, from_date)
            }
        }

        todayData?.forEach { sensorData ->
            if (sensorData.previous_weight != 0.0 && sensorData.previous_weight > sensorData.weight) {
                val waktu = convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()
                val minum = abs(sensorData.previous_weight - sensorData.weight)
                todayList[waktu] = minum
            }
        }
    }

    LaunchedEffect(fromDate, toDate) {
        if (fromDate != "" && toDate != "") {
            try {
                val response = apiService.getSensorData(
                    fromDate = fromDate,
                    toDate = toDate,
                    userId = user?.id!!
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
            } catch (e: Exception) {
                println("Error fetching sensor data: ${e.message}")
                if (e.message == "HTTP 404 ") {
                    Toast.makeText(context, "Tidak Ada Data Pada Tanggal Ini", Toast.LENGTH_SHORT).show()
                }
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
            text = "Halaman History",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Minum Hari Ini",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (todayData.toString().isEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tidak ada data untuk hari ini.",
                fontSize = 14.sp,
                color = Color.Gray
            )
        } else {
            if (todayList.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp, horizontal = 16.dp),
                    elevation = CardDefaults.elevatedCardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BarchartWithSolidBars(todayList)
                    }
                }
            } else {
                Text(
                    text = "Tidak Ada Data Untuk di Chart.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Data Historis",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DatePickerOutlinedField(
                label = "Mulai Dari",
                date = fromDate,
                onDateSelected = { selectedDate ->
                    fromDate = selectedDate
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            DatePickerOutlinedField(
                label = "Hingga",
                date = toDate,
                minDate = fromDate.takeIf { it.isNotEmpty() }?.let {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it)?.time
                },
                onDateSelected = { selectedDate ->
                    toDate = selectedDate
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (sensorDataResponse == null) {

                Text(
                    text = "Data belum tersedia.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

            } else {

                if (totalPerDay.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        elevation = CardDefaults.elevatedCardElevation(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BarchartWithSolidBars(totalPerDay)
                        }
                    }
                } else {
                    Text(
                        text = "Tidak Ada Data Untuk di Chart.",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
