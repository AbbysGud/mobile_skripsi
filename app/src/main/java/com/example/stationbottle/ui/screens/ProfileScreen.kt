package com.example.stationbottle.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.stationbottle.R
import com.example.stationbottle.ThemeViewModelFactory
import com.example.stationbottle.data.UpdateUserRequest
import com.example.stationbottle.models.ThemeViewModel
import com.example.stationbottle.models.UserViewModel
import com.example.stationbottle.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow

@Composable
fun ProfileScreen(
    navController: NavController,
    onThemeChange: () -> Unit
) {
    val context = LocalContext.current

    val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(context))
    val isDarkTheme = themeViewModel.isDarkMode.collectAsState(initial = false)

    val userViewModel = UserViewModel()
    val userState = userViewModel.getUser(context).collectAsState(initial = null)
    val user = userState.value
    val token = user?.token
    var email = user?.email

    var name by remember { mutableStateOf<String?>(null) }
    var dateOfBirth by remember { mutableStateOf<String?>(null) }
    var weight by remember { mutableStateOf<Double?>(null) }
    var height by remember { mutableStateOf<Double?>(null) }
    var gender by remember { mutableStateOf<String?>(null) }
    var dailyGoal by remember { mutableStateOf<Double?>(null) }
    var pregnancyDate by remember { mutableStateOf<String?>(null) }
    var breastfeedingDate by remember { mutableStateOf<String?>(null) }
    var showTargetInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(user) {
        user?.let {
            userViewModel.getUserData(context, it.id, token.toString())
            name = it.name
            email = it.email
            dateOfBirth = it.date_of_birth
            weight = it.weight
            height = it.height
            gender = it.gender
            dailyGoal = it.daily_goal
            pregnancyDate = it.pregnancy_date
            breastfeedingDate = it.breastfeeding_date
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 32.dp, top = 32.dp, end = 32.dp, bottom = 0.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(text = "Halaman Profil", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Image(
            painter = painterResource(id = R.drawable.default_profile),
            contentDescription = "Default Profile Image",
            modifier = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(Color.Gray),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (user != null) {
            Text(
                text = name?.ifEmpty { "Nama Anda di Sini" } ?: "",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = email?.ifEmpty { "Email Anda di Sini" } ?: "",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = name?.ifEmpty { "" } ?: "",
                onValueChange = { name = it },
                label = { Text("Nama") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            DatePickerOutlinedField(
                label = "Tanggal Lahir",
                date = dateOfBirth?.ifEmpty { "" } ?: "",
                onDateSelected = { dateOfBirth = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = if (weight == 0.0) "" else weight.toString(),
                onValueChange = { weight = it.toDoubleOrNull() ?: 0.0 },
                label = { Text("Berat Badan (kg)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = if (height == 0.0) "" else height.toString(),
                onValueChange = { height = it.toDoubleOrNull() ?: 0.0 },
                label = { Text("Tinggi Badan (cm)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Jenis Kelamin",
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Start)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = (gender == "male"),
                    onClick = { gender = "male" }
                )
                Text(
                    text = "Pria",
                    modifier = Modifier.clickable { gender = "male" }
                )

                Spacer(modifier = Modifier.width(16.dp))

                RadioButton(
                    selected = (gender == "female"),
                    onClick = { gender = "female" }
                )
                Text(
                    text = "Wanita",
                    modifier = Modifier.clickable { gender = "female" }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (gender == "female") {
                OutlinedTextField(
                    value = pregnancyDate?.ifEmpty { "" } ?: "",
                    onValueChange = { pregnancyDate = it },
                    label = { Text("Tanggal Kehamilan (Opsional)") },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = breastfeedingDate?.ifEmpty { "" } ?: "",
                    onValueChange = { breastfeedingDate = it },
                    label = { Text("Tanggal Menyusui (Opsional)") },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = if (dailyGoal == 0.0) "" else dailyGoal.toString(),
                onValueChange = { dailyGoal = it.toDoubleOrNull() ?: 0.0 },
                label = { Text("Target Harian (L)") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { showTargetInfoDialog = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.outline_info_24),
                            contentDescription = "Informasi Target Harian"
                        )
                    }
                },
            )

            if (showTargetInfoDialog) {
                TargetDailyGoalInfoDialog(onDismissRequest = { showTargetInfoDialog = false })
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "Mode Gelap",
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isDarkTheme.value,
                    onCheckedChange = {
                        onThemeChange()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 64.dp)
            ) {
                Button(
                    onClick = {
                        if (dateOfBirth != null && dateOfBirth!!.isNotEmpty() && weight != null && weight != 0.0 && height != null && height != 0.0 && gender != null && gender!!.isNotEmpty()) {
                            dailyGoal = calculateDailyGoal(
                                age = calculateAge(dateOfBirth!!),
                                weight = weight!!,
                                height = height!!,
                                gender = gender!!,
                                pregnancyDate = pregnancyDate.toString(),
                                breastfeedingDate = breastfeedingDate.toString()
                            )
                        }

                        val updateRequest = UpdateUserRequest(
                            name = name,
                            date_of_birth = dateOfBirth,
                            weight = if (weight == 0.0) null else weight,
                            height = if (height == 0.0) null else height,
                            gender = gender,
                            daily_goal = if (dailyGoal == 0.0) null else dailyGoal,
                            pregnancy_date = pregnancyDate,
                            breastfeeding_date = breastfeedingDate
                        )
                        userViewModel.updateUser(navController, context, user.id, updateRequest, token.toString())
                    },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("Update Profile")
                }

                Button(
                    onClick = {
                        userViewModel.logoutUser(context, token.toString(), navController)
                    },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(text = "Logout")
                }
            }
        }
    }
}

@Composable
fun DatePickerOutlinedField(
    label: String,
    date: String,
    onDateSelected: (String) -> Unit,
    minDate: Long? = null
) {
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

    OutlinedTextField(
        value = date,
        onValueChange = {},
        label = { Text(label) },
        enabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { datePickerDialog.show() },
        colors = TextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onBackground,
            disabledPrefixColor = MaterialTheme.colorScheme.onBackground,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            disabledIndicatorColor = MaterialTheme.colorScheme.outline,
            disabledPlaceholderColor = MaterialTheme.colorScheme.onBackground,
            disabledLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha =0.8f),
            disabledSuffixColor = MaterialTheme.colorScheme.onBackground,
            disabledLeadingIconColor = MaterialTheme.colorScheme.onBackground,
            disabledSupportingTextColor = MaterialTheme.colorScheme.onBackground,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onBackground
        )
    )
}


@Composable
fun TargetDailyGoalInfoDialog(
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.padding(vertical = 64.dp),
        title = {
            Text("Penjelasan Target Harian")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Berikut adalah kebutuhan air harian berdasarkan usia, berat badan, dan tinggi badan:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Pria",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Table(
                    headers = listOf("Usia", "Berat Badan (Kg)", "Tinggi (cm)", "Air (mL)"),
                    rows = listOf(
                        listOf("10-12", "34", "142", "1800"),
                        listOf("13-15", "46", "158", "2000"),
                        listOf("16-18", "56", "165", "2200"),
                        listOf("19-29", "60", "168", "2500"),
                        listOf("30-49", "62", "168", "2600"),
                        listOf("50-64", "62", "168", "2600"),
                        listOf("65-80", "60", "168", "1900")
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Wanita",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Table(
                    headers = listOf("Usia", "Berat Badan (Kg)", "Tinggi (cm)", "Air (mL)"),
                    rows = listOf(
                        listOf("10-12", "36", "145", "1800"),
                        listOf("13-15", "46", "155", "2000"),
                        listOf("16-18", "50", "158", "2100"),
                        listOf("19-29", "54", "159", "2300"),
                        listOf("30-49", "55", "159", "2300"),
                        listOf("50-64", "55", "159", "2300"),
                        listOf("65-80", "54", "159", "1600")
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Hamil dan Menyusui",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Trisemester Pertama")
                        Text("+300 mL")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Trisemester Kedua")
                        Text("+300 mL")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Trisemester Ketiga")
                        Text("+300 mL")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Menyusui (0-6 bulan)")
                        Text("+800 mL")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Menyusui (7-12 bulan)")
                        Text("+650 mL")
                    }
                }

            }
        },
        confirmButton = {
            Button(
                onClick = onDismissRequest,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Tutup")
            }
        }
    )
}

@Composable
fun Table(
    headers: List<String>,
    rows: List<List<String>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.primaryContainer)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            headers.forEach { header ->
                Text(
                    text = header,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}



fun calculateAge(dateOfBirth: String): Int {
    val birthDate = dateOfBirth.split("-")
    val yearOfBirth = birthDate[0].toInt()
    val monthOfBirth = birthDate[1].toInt() - 1
    val dayOfBirth = birthDate[2].toInt()

    val currentDate = Calendar.getInstance()

    val age = currentDate.get(Calendar.YEAR) - yearOfBirth

    val currentMonth = currentDate.get(Calendar.MONTH)
    val currentDay = currentDate.get(Calendar.DAY_OF_MONTH)

    if (currentMonth < monthOfBirth || (currentMonth == monthOfBirth && currentDay < dayOfBirth)) {
        return age - 1
    }
    return age
}

fun calculatePregnancyTrimester(pregnancyDate: String): Int? {
    if (pregnancyDate.isEmpty()) return null

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val pregnancyDateParsed = dateFormat.parse(pregnancyDate) ?: return null
    val currentDate = Calendar.getInstance()

    val diffInMillis = currentDate.timeInMillis - pregnancyDateParsed.time
    val diffInMonths = diffInMillis / (1000 * 60 * 60 * 24 * 30)

    return when {
        diffInMonths < 3 -> 1
        diffInMonths in 3..6 -> 2
        diffInMonths in 7..9 -> 3
        else -> null
    }
}

fun calculateBreastfeedingMonths(breastfeedingDate: String): Int {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val breastfeedingDateParsed = dateFormat.parse(breastfeedingDate) ?: return 0
    val currentDate = Calendar.getInstance()

    val diffInMillis = currentDate.timeInMillis - breastfeedingDateParsed.time
    val diffInMonths = diffInMillis / (1000 * 60 * 60 * 24 * 30)

    return diffInMonths.toInt()
}

fun calculateHollidaySegar(weight: Double): Double {
    return when {
        weight <= 10 -> {
            weight * 100
        }

        weight <= 20 -> {
            (10 * 100) + ((weight - 10) * 50)
        }

        else -> {
            (10 * 100) + (10 * 50) + ((weight - 20) * 20)
        }
    }
}

fun calculateAdult(weight: Double, height: Double, gender: String, age: Int): Double {
    var BMI = weight / (height / 100).pow(2.0)
    var percentage = 0.0
    if (gender == "male") {
        if (BMI < 24.9) {
            val weightPercentage = listOf(
                40.0 to 0.017,
                45.0 to 0.0175,
                50.0 to 0.018,
                55.0 to 0.0185,
                60.0 to 0.019,
                65.0 to 0.0195,
                70.0 to 0.02
            )

            percentage = when {
                weight <= weightPercentage.first().first -> weightPercentage.first().second
                weight >= weightPercentage.last().first -> weightPercentage.last().second
                else -> {
                    val lower = weightPercentage.last { it.first <= weight }
                    val upper = weightPercentage.first { it.first > weight }
                    lower.second + (weight - lower.first) * (upper.second - lower.second) / (upper.first - lower.first)
                }
            }
        } else {
            percentage = 0.0255
        }
    } else if (gender == "female") {
        percentage = 0.023
    }


    var watsonTBW = 0.0

    if (gender == "male") {
        watsonTBW = 2.447 - (0.09145 * age) + (0.1074 * height) + (0.3362 * weight)
    } else if (gender == "female") {
        watsonTBW = -2.097 + (0.1069 * height) + (0.2466 * weight)
    }

    println("BMI : $BMI")
    println("percentage : $percentage")
    println("watsonTBW : $watsonTBW")
    println("w30 : ${weight * 30}")
    println("tes : ${watsonTBW * percentage * 1000}")
    return (weight * 30) + (watsonTBW * percentage * 1000)
}

fun calculateElderly(weight: Double, height: Double, gender: String, age: Int): Double {
    var goals = 0.0

    if (gender == "male") {
        var watsonTBW = 2.447 - (0.09145 * age) + (0.1074 * height) + (0.3362 * weight)
        goals = (weight * 30) + (watsonTBW * 0.003 * 1000)
    } else if (gender == "female") {
        goals = weight * 30
    }

    return goals
}

fun calculateDailyGoal(
    age: Int,
    weight: Double,
    height: Double,
    gender: String,
    pregnancyDate: String = "",
    breastfeedingDate: String = ""
): Double {
    var dailyGoal = 0.0

    when (gender) {
        "male" -> {
            dailyGoal = when (age) {
                in 0..18 -> {
                    calculateHollidaySegar(weight)
                }

                in 19..64 -> {
                    calculateAdult(weight, height, gender, age)
                }

                in 65..80 -> {
                    calculateElderly(weight, height, gender, age)
                }

                in 81..200 -> {
                    var watsonTBW = 2.447 - (0.09145 * age) + (0.1074 * height) + (0.3362 * weight)
                    (weight * 30) - (watsonTBW * 0.003 * 1000)
                }
                else -> 2500.0
            }
        }
        "female" -> {
            dailyGoal = when (age) {
                in 0..18 -> {
                    calculateHollidaySegar(weight)
                }

                in 19..64 -> {
                    calculateAdult(weight, height, gender, age)
                }

                in 65..80 -> {
                    calculateElderly(weight, height, gender, age)
                }

                in 81..200 -> {
                    weight * 30
                }
                else -> 2500.0
            }
        }
    }

    println("dailygoal : $dailyGoal")

    val pregnancyTrimester = calculatePregnancyTrimester(pregnancyDate)

    if (pregnancyTrimester != null) {
        val pregnancyAdditional = when (pregnancyTrimester) {
            1 -> 300
            2 -> 300
            3 -> 300
            else -> 0
        }
        dailyGoal += pregnancyAdditional
    }

    if (breastfeedingDate.isNotEmpty()) {
        val breastfeedingMonths = calculateBreastfeedingMonths(breastfeedingDate)
        when {
            breastfeedingMonths <= 6 -> dailyGoal += 800
            breastfeedingMonths in 7..12 -> dailyGoal += 650
        }
    }

    return dailyGoal
}
