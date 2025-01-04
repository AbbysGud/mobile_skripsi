package com.example.stationbottle.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.stationbottle.data.RetrofitClient
import com.example.stationbottle.models.UserViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val userViewModel = UserViewModel()
    val userState = userViewModel.getUser(context).collectAsState(initial = null)
    val user = userState.value
    val token = user?.token

    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var sensorData by remember { mutableStateOf<String?>(null) }
    var rfidTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(user) {
        user?.let {
            userViewModel.getUserData(context, it.id, token.toString())
            rfidTag = it.rfid_tag
        }
    }

    LaunchedEffect(rfidTag, fromDate, toDate) {
        if (!rfidTag.isNullOrEmpty()) {
            if(fromDate != "" && toDate != ""){
                try {
                    val response = RetrofitClient.apiService.getSensorData(
                        fromDate = fromDate,
                        toDate = toDate,
                        rfidTag = rfidTag!!
                    )
                    sensorData = response.toString()
                } catch (e: Exception) {
                    println("Error fetching sensor data: ${e.message}")
                    if(e.message == "HTTP 404 "){
                        Toast.makeText(context, "Tidak Ada Data Pada Tanggal Ini", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Halaman History",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Date Picker untuk tanggal mulai
        DatePickerField(
            label = "Dari Tanggal",
            date = fromDate,
            onDateSelected = { selectedDate ->
                fromDate = selectedDate
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Date Picker untuk tanggal akhir
        DatePickerField(
            label = "Sampai Tanggal",
            date = toDate,
            minDate = fromDate.takeIf { it.isNotEmpty() }?.let {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it)?.time
            },
            onDateSelected = { selectedDate ->
                toDate = selectedDate
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Menampilkan data hasil fetch
        Text(
            text = sensorData ?: "Data belum tersedia.",
            fontSize = 14.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun DatePickerField(label: String, date: String, onDateSelected: (String) -> Unit, minDate: Long? = null) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            val selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
                Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }.time
            )
            onDateSelected(selectedDate)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    minDate?.let { datePickerDialog.datePicker.minDate = it }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(8.dp))

        BasicTextField(
            value = date,
            onValueChange = {},
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { datePickerDialog.show() }
                .background(Color.LightGray, shape = MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            singleLine = true
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    val navController = rememberNavController()
    HistoryScreen(navController)
}
