package com.example.stationbottle.ui.screens

import android.widget.Space
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import com.example.stationbottle.ui.theme.AppTheme
import com.example.stationbottle.R
import com.example.stationbottle.ThemeViewModelFactory
import com.example.stationbottle.data.MQTTClient
import com.example.stationbottle.models.ThemeViewModel
import com.example.stationbottle.models.UserViewModel
import com.example.stationbottle.ui.theme.onPrimaryContainerLight
import com.example.stationbottle.ui.theme.primaryLight
import org.json.JSONException
import org.json.JSONObject

@Composable
fun StationScreen(navController: NavController) {
    val context = LocalContext.current
    val userViewModel = UserViewModel()
    val userState = userViewModel.getUser(context).collectAsState(initial = null)
    val user = userState.value

    val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(context))
    val isDarkTheme = themeViewModel.isDarkMode.collectAsState(initial = false)

    val mqttClient = remember { MQTTClient("tcp://broker.hivemq.com:1883") }
    val topicSubscribe = "smartstation/weight"
    val topicPublish = "smartstation/mode"
    val isConnected = remember { mutableStateOf(false) }

    val (weight, setWeight) = remember { mutableFloatStateOf(0.0f) }
    val (mode, setMode) = remember { mutableStateOf("") }
    val (rfid, setRFID) = remember { mutableStateOf("") }
    val (message, setMessage) = remember { mutableStateOf("") }

    fun connectMqtt() {
        mqttClient.connect(context)
        if(mqttClient.isConnected){
            mqttClient.subscribe(
                context = context,
                topic = topicSubscribe,
                callback = { message ->
                    try {
                        val data = JSONObject(message)
                        setWeight(data.getDouble("weight").toFloat())
                        setMode(data.getString("mode"))
                        setRFID(data.getString("RFID"))
                        setMessage(data.getString("message"))
                    } catch (e: JSONException) {
                        println("Error parsing message: ${e.message}")
                    }
                }
            )
            isConnected.value = true
        }
    }

    fun disconnectMqtt() {
        mqttClient.disconnect(context)
        isConnected.value = false
    }

    DisposableEffect(Unit) {
        onDispose {
            mqttClient.disconnect(context)
            println("MQTT disconnected on page exit")
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
            text = "Halaman Stasiun",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Sensor Status",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 16.dp),
            elevation = CardDefaults.elevatedCardElevation(4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .border(
                            width = 2.dp,
                            color =
                                if (isConnected.value == true && mode != "")
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.error,
                            shape = CircleShape
                        )
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text =
                            if (isConnected.value == true && mode != "")
                                "Online"
                            else
                                "Offline",
                        color =
                        if (isConnected.value == true && mode != "")
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isConnected.value == false) {
                    Text(
                        text = "Belum Terhubung ke Sensor",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                } else if (mode == "") {
                    Text(
                        text = "Sensor Tidak Nyala",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                } else {
                    Text(
                        text = "Terhubung ke Sensor",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (mqttClient.isConnected) {
                            disconnectMqtt()
                        } else {
                            connectMqtt()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(
                        text =
                            if (mqttClient.isConnected)
                                "Disconnect"
                            else
                                "Connect"
                    )
                }

            }
        }

        if (isConnected.value == true) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Sensor Data",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 16.dp),
                elevation = CardDefaults.elevatedCardElevation(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (mode == "WEIGH_MODE") {
                        Text(
                            text = "Berat:",
                            fontSize = 16.sp,
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "$weight g",
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val tareMessage = JSONObject().apply {
                                    put("message", "TARE_SCALE")
                                    put("user_id", user?.id ?: 0)
                                }.toString()
                                mqttClient.publish(context, topicPublish, tareMessage)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(text = "Tare")
                        }
                    } else if (mode == "RFID_MODE") {
                        Text(
                            text = "RFID:",
                            fontSize = 16.sp,
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = rfid,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "Pesan:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Start
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = message,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Sensor Mode",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            val modeMessage = JSONObject().apply {
                                put("message", "WEIGH_MODE")
                                put("user_id", user?.id ?: 0)
                            }.toString()
                            mqttClient.publish(context, topicPublish, modeMessage)
                        },
                    elevation = CardDefaults.elevatedCardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor =
                        if (mode == "WEIGH_MODE")
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainer,
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(
                                if (mode == "WEIGH_MODE")
                                    R.drawable.scale_white
                                else
                                    if (isDarkTheme.value)
                                        R.drawable.scale_white
                                    else
                                        R.drawable.scale
                            ),
                            contentDescription = "SCALE ICON",
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "MODE\nTIMBANGAN",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            val modeMessage = JSONObject().apply {
                                put("message", "RFID_MODE")
                                put("user_id", user?.id ?: 0)
                            }.toString()
                            mqttClient.publish(context, topicPublish, modeMessage)
                        },
                    elevation = CardDefaults.elevatedCardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor =
                        if (mode == "RFID_MODE")
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainer,
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(
                                if (mode == "RFID_MODE")
                                    R.drawable.rfid_white
                                else
                                    if (isDarkTheme.value)
                                        R.drawable.rfid_white
                                    else
                                        R.drawable.rfid
                            ),
                            contentDescription = "RFID ICON",
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "MODE RFID",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StationScreenPreview() {
    AppTheme {
        val navController = rememberNavController()
        StationScreen(navController)
    }
}
