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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.compose.AppTheme
import com.example.stationbottle.R
import com.example.stationbottle.data.MQTTClient
import com.example.stationbottle.models.UserViewModel
import org.json.JSONException
import org.json.JSONObject

@Composable
fun StationScreen(navController: NavController) {
    val context = LocalContext.current
    val userViewModel = UserViewModel()
    val userState = userViewModel.getUser(context).collectAsState(initial = null)
    val user = userState.value

    val mqttClient = remember { MQTTClient("tcp://broker.hivemq.com:1883") }
    val topicSubscribe = "smartstation/weight"
    val topicPublish = "smartstation/mode"

    val (weight, setWeight) = remember { mutableFloatStateOf(0.0f) }
    val (mode, setMode) = remember { mutableStateOf("") }
    val (rfid, setRFID) = remember { mutableStateOf("") }
    val (message, setMessage) = remember { mutableStateOf("") }

    fun connectMqtt() {
        mqttClient.connect(context)
    }

    LaunchedEffect(Unit) {
        connectMqtt()
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
        }
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
            .padding(start = 32.dp, top = 32.dp, end = 32.dp, bottom = 0.dp)
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

        Box(
            modifier = Modifier
                .size(64.dp)
                .border(
                    width = 2.dp,
                    color = if (mqttClient.isConnected  && message != "") Color.Green else Color.Red,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (mqttClient.isConnected && message != "") "Online" else "Offline",
                color = if (mqttClient.isConnected && message != "") Color.Green else Color.Red,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sensor Status",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        if (!mqttClient.isConnected) {
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    connectMqtt()
                },
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text(text = "Reconnect")
            }
        }

        if (message == "") {
            Text(
                text = "Sensor Tidak Nyala",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .border(
                    width = 1.dp,
                    color = Color.Gray,
                    shape = RoundedCornerShape(5.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column (
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (mode == "WEIGH_MODE") {
                    Text(
                        text = "Berat: \n$weight g",
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val tareMessage = JSONObject().apply {
                                put("message", "TARE_SCALE")
                                put("user_id", user?.id ?: 0)
                            }.toString()
                            mqttClient.publish(context, topicPublish, tareMessage)
                        },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(text = "Tare")
                    }
                } else if (mode == "RFID_MODE") {
                    Text(
                        text = "RFID: \n$rfid",
                        fontSize = 20.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .border(
                    width = 1.dp,
                    color = Color.Gray,
                    shape = RoundedCornerShape(5.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = "Pesan: \n$message",
                fontSize = 16.sp,
                fontWeight = FontWeight.Light
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Column (
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .padding(8.dp)
                        .background(Color.White)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = if (mode == "RFID_MODE") Color.Green else Color.Black,
                            shape = CircleShape
                        )
                ) {
                    Image(
                        painter = painterResource(R.drawable.rfid),
                        contentDescription = "RFID ICON",
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.Center)
                            .clickable {
                                val modeMessage = JSONObject().apply {
                                    put("message", "RFID_MODE")
                                    put("user_id", user?.id ?: 0)
                                }.toString()
                                mqttClient.publish(context, topicPublish, modeMessage)
                            },
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    text = "MODE RFID",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column (
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .padding(8.dp)
                        .background(Color.White)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = if (mode == "WEIGH_MODE") Color.Green else Color.Black,
                            shape = CircleShape
                        )
                ) {
                    Image(
                        painter = painterResource(R.drawable.scale),
                        contentDescription = "SCALE ICON",
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.Center)
                            .clickable {
                                val modeMessage = JSONObject().apply {
                                    put("message", "WEIGH_MODE")
                                    put("user_id", user?.id ?: 0)
                                }.toString()
                                mqttClient.publish(context, topicPublish, modeMessage)
                            },
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    text = "MODE TIMBANGAN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
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
