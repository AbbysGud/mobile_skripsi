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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import co.yml.charts.axis.AxisData
import co.yml.charts.axis.DataCategoryOptions
import co.yml.charts.axis.Gravity
import co.yml.charts.common.model.Point
import co.yml.charts.ui.barchart.BarChart
import co.yml.charts.ui.barchart.models.BarChartData
import co.yml.charts.ui.barchart.models.BarData
import co.yml.charts.ui.barchart.models.BarStyle
import com.example.stationbottle.data.RetrofitClient
import com.example.stationbottle.data.SensorDataResponse
import com.example.stationbottle.data.convertUtcToWIB
import com.example.stationbottle.models.UserViewModel
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun HistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val userViewModel = UserViewModel()
    val userState = userViewModel.getUser(context).collectAsState(initial = null)
    val user = userState.value
    val userId = user?.id

    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var sensorDataResponse by remember { mutableStateOf<SensorDataResponse?>(null) }
    var todayData by remember { mutableStateOf<SensorDataResponse?>(null) }
    var todayList = remember { linkedMapOf<String, Double>() }
    var totalPerDay = remember { linkedMapOf<String, Double>() }

    LaunchedEffect(userId) {
        if (userId != null) {
            try {
//                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val today = "2025-01-16"
                val response = RetrofitClient.apiService.getSensorData(today, today, userId)
                todayData = response
                todayData?.data?.forEach { sensorData ->
                    if (sensorData.previous_weight != 0.0  && sensorData.previous_weight > sensorData.weight) {
                        val waktu = convertUtcToWIB(sensorData.created_at, timeOnly = true).toString()
                        val minum = kotlin.math.abs(sensorData.previous_weight - sensorData.weight)

                        todayList[waktu] = minum
                    }
                }
            } catch (e: Exception) {
                println("Error fetching today's data: ${e.message}")
            }
        }
    }

    LaunchedEffect(fromDate, toDate) {
        if (fromDate != "" && toDate != "") {
            try {
                val response = RetrofitClient.apiService.getSensorData(
                    fromDate = fromDate,
                    toDate = toDate,
                    userId = userId!!
                )
                sensorDataResponse = response
                val groupedData = response.data.groupBy {
                    convertUtcToWIB(it.created_at)?.substring(0, 10) ?: ""
                }
                totalPerDay.clear()
                groupedData.forEach { (date, dataList) ->
                    val totalForDay = dataList.sumOf {
                        if (it.previous_weight != 0.0 && it.previous_weight > it.weight) {
                            kotlin.math.abs(it.previous_weight - it.weight)
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

@Composable
private fun BarchartWithSolidBars(todayList: Map<String, Double>) {
    val barData = getCustomBarChartData(todayList, DataCategoryOptions())
    val maxRange = todayList.values.maxOrNull()?.toInt() ?: 50
    val yStepSize = 10

    val xAxisData = AxisData.Builder()
        .axisLineColor(MaterialTheme.colorScheme.onSurface)
        .axisLabelColor(MaterialTheme.colorScheme.onSurface)
        .backgroundColor(MaterialTheme.colorScheme.surfaceContainer)
        .axisStepSize(30.dp)
        .steps(barData.size - 1)
        .bottomPadding(40.dp)
        .axisLabelAngle(20f)
        .startDrawPadding(48.dp)
        .shouldDrawAxisLineTillEnd(true)
        .labelData { index -> barData[index].label }
        .axisLabelDescription { "Waktu Minum" }
        .axisPosition(Gravity.BOTTOM)
        .build()

    val yAxisData = AxisData.Builder()
        .axisLineColor(MaterialTheme.colorScheme.onSurface)
        .axisLabelColor(MaterialTheme.colorScheme.onSurface)
        .backgroundColor(MaterialTheme.colorScheme.surfaceContainer)
        .steps(yStepSize)
        .labelAndAxisLinePadding(20.dp)
        .axisOffset(20.dp)
        .labelData { index -> (index * (maxRange / yStepSize)).toString() }
        .axisLabelDescription { "Jumlah Minum (mL)" }
        .axisPosition(Gravity.LEFT)
        .build()

    val barChartData = BarChartData(
        chartData = barData,
        xAxisData = xAxisData,
        yAxisData = yAxisData,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
        barStyle = BarStyle(
            paddingBetweenBars = 20.dp,
            barWidth = 25.dp
        ),
        showYAxis = true,
        showXAxis = true,
        horizontalExtraSpace = 0.dp
    )

    BarChart(
        modifier = Modifier
            .height(350.dp),
        barChartData = barChartData
    )
}

@Composable
fun getCustomBarChartData(
    drinkData: Map<String, Double>,
    dataCategoryOptions: DataCategoryOptions
): List<BarData> {
    val list = arrayListOf<BarData>()
    drinkData.entries.forEachIndexed { index, entry ->
        val date = entry.key
        val drinkValue = entry.value

        val point = Point(
            x = index.toFloat(),
            y = drinkValue.toFloat()
        )
        list.add(
            BarData(
                point = point,
                color = MaterialTheme.colorScheme.primary,
                dataCategoryOptions = dataCategoryOptions,
                label = date,
            )
        )
    }
    return list
}

@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    val navController = rememberNavController()
    HistoryScreen(navController)
}
