package com.example.stationbottle.ui.screens.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.stationbottle.ui.screens.Table

@Composable
fun TargetDailyGoalInfoDialog(
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.Companion.padding(vertical = 64.dp),
        title = {
            Text("Penjelasan Target Harian")
        },
        text = {
            Column(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Berikut adalah kebutuhan air harian berdasarkan usia, berat badan, dan tinggi badan:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.Companion.height(8.dp))

                Text(
                    text = "Pria",
                    fontWeight = FontWeight.Companion.Bold,
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    textAlign = TextAlign.Companion.Center
                )
                Spacer(modifier = Modifier.Companion.height(4.dp))
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
                Spacer(modifier = Modifier.Companion.height(8.dp))

                Text(
                    text = "Wanita",
                    fontWeight = FontWeight.Companion.Bold,
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    textAlign = TextAlign.Companion.Center
                )
                Spacer(modifier = Modifier.Companion.height(4.dp))
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
                Spacer(modifier = Modifier.Companion.height(8.dp))

                Text(
                    text = "Hamil dan Menyusui",
                    fontWeight = FontWeight.Companion.Bold,
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    textAlign = TextAlign.Companion.Center
                )
                Spacer(modifier = Modifier.Companion.height(4.dp))
                Column(
                    modifier = Modifier.Companion.fillMaxWidth().padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.Companion.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Trisemester Pertama")
                        Text("+300 mL")
                    }
                    Row(
                        modifier = Modifier.Companion.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Trisemester Kedua")
                        Text("+300 mL")
                    }
                    Row(
                        modifier = Modifier.Companion.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Trisemester Ketiga")
                        Text("+300 mL")
                    }
                    Row(
                        modifier = Modifier.Companion.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Menyusui (0-6 bulan)")
                        Text("+800 mL")
                    }
                    Row(
                        modifier = Modifier.Companion.fillMaxWidth(),
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