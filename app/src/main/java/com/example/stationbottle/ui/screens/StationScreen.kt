package com.example.stationbottle.ui.screens

import android.util.Log
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
import com.example.stationbottle.client.MQTTClient
import com.example.stationbottle.client.RetrofitClient
import com.example.stationbottle.data.ModeRequest
import com.example.stationbottle.data.NGROKResponse
import com.example.stationbottle.models.ThemeViewModel
import com.example.stationbottle.models.UserViewModel
import com.example.stationbottle.service.ApiService
import com.example.stationbottle.worker.WebSocketManager
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.PrivateChannelEventListener
import com.pusher.client.channel.PusherEvent
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionStateChange
import com.pusher.client.util.HttpAuthorizer
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Composable
fun StationScreen(navController: NavController) {
    val context = LocalContext.current
    val userViewModel = UserViewModel()
    val userState = userViewModel.getUser(context).collectAsState(initial = null)
    val user = userState.value
    val scope = rememberCoroutineScope()

    val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(context))
    val isDarkTheme = themeViewModel.isDarkMode.collectAsState(initial = false)

    var response: NGROKResponse = NGROKResponse(
        id = 1,
        http_url = "http://localhost",
        websocket_url = "http://localhost",
        websocket_port = 6001,
        updated_at = "",
    )

    var options = PusherOptions()
    var pusher: Pusher = Pusher("stationbottlebe_pusher", options)

    val (weight, setWeight) = remember { mutableFloatStateOf(0.0f) }
    val (mode, setMode) = remember { mutableStateOf("") }
    val (rfid, setRFID) = remember { mutableStateOf("") }
    val (message, setMessage) = remember { mutableStateOf("") }
    val isConnected = remember { mutableStateOf(false) }

    LaunchedEffect(Unit){
        response = RetrofitClient.apiService.getNGROKUrl()
        println("response $response")

        RetrofitClient.setDynamicBaseUrl("${response.http_url}/api/")

        val authUrl = "${response.http_url}/broadcasting/auth"
        val authorizer = HttpAuthorizer(authUrl).apply {
            setHeaders(
                mapOf(
                    "Accept" to "application/json",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            )
        }

        options = PusherOptions().apply {
            setCluster("mt1")
            setHost("0.tcp.ap.ngrok.io")
            setWsPort(response.websocket_port)
            isUseTLS = false
            setAuthorizer(authorizer)
        }

        pusher = Pusher("stationbottlebe_pusher", options)
        println("Pusher instance created")

        val channel = pusher.subscribePrivate("private-weight-channel")

        channel.bind("client-WeightEvent", object : PrivateChannelEventListener {
            override fun onEvent(event: PusherEvent) {
                try {
                    val jsonObject = JSONObject(event.data)

                    val weight = if (jsonObject.has("weight") && !jsonObject.isNull("weight")) {
                        jsonObject.getDouble("weight").toFloat()
                    } else {
                        0.0f
                    }
                    setWeight(weight)

                    val mode = if (jsonObject.has("mode") && !jsonObject.isNull("mode")) {
                        jsonObject.getString("mode")
                    } else {
                        "WEIGH_MODE"
                    }
                    setMode(mode)

                    val rfid = if (jsonObject.has("rfid") && !jsonObject.isNull("rfid")) {
                        jsonObject.getString("rfid")
                    } else {
                        ""
                    }
                    setRFID(rfid)

                    val message = if (jsonObject.has("message") && !jsonObject.isNull("message")) {
                        jsonObject.getString("message")
                    } else {
                        ""
                    }
                    setMessage(message)

                } catch (e: JSONException) {
                    setWeight(0.0f)
                    setMode("")
                    setRFID("")
                    setMessage("Error parsing data")
                    println("Error parsing JSON: ${e.message}")
                }
            }

            override fun onSubscriptionSucceeded(channelName: String) {
                println("Subscription succeeded to $channelName")
            }

            override fun onAuthenticationFailure(message: String, e: Exception) {
                println("Authentication failed: $message, Exception: $e")
            }
        })

        pusher.connect(object : ConnectionEventListener {
            override fun onConnectionStateChange(change: ConnectionStateChange) {
                println("State changed from ${change.previousState} to ${change.currentState}")
            }

            override fun onError(message: String?, code: String?, e: Exception?) {
                println("Error: $message, Code: $code, Exception: $e")
            }
        })

        isConnected.value = true
    }

    DisposableEffect(Unit) {
        onDispose {
            pusher.disconnect()
            println("Disconnect from pusher")
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
                        if (isConnected.value == true) {
                            pusher.disconnect()
                            isConnected.value = false
                        } else {
                            pusher.connect()
                            isConnected.value = true
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
                            if (isConnected.value == true)
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
//                                val tareMessage = JSONObject().apply {
//                                    put("message", "TARE_SCALE")
//                                    put("user_id", user?.id ?: 0)
//                                }.toString()
                                val mode = ModeRequest(
                                    message = "TARE_SCALE",
                                    user_id = user?.id!!
                                )
                                println(mode)

                                scope.launch {
                                    try {
                                        val response = RetrofitClient.dynamicApiService.sendMode(mode)
                                        println("Response: $response")
                                    } catch (e: Exception) {
                                        println("Error sending mode: ${e.message}")
                                    }
                                }


//                                apiServiceNgrok?.let { service ->
//                                    scope.launch {
//                                        try {
//                                            val response = service.sendMode(mode)
//                                            println("Response: $response")
//                                        } catch (e: Exception) {
//                                            println("Error sending mode: ${e.message}")
//                                        }
//                                    }
//                                } ?: println("apiServiceNgrok is null")


//                                mqttClient.publish(context, topicPublish, tareMessage)
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
                            val mode = ModeRequest(
                                message = "WEIGH_MODE",
                                user_id = user?.id!!
                            )
                            scope.launch {
                                try {
                                    val response = RetrofitClient.dynamicApiService.sendMode(mode)
                                    println("Response: $response")
                                } catch (e: Exception) {
                                    println("Error sending mode: ${e.message}")
                                }
                            }
//                            mqttClient.publish(context, topicPublish, modeMessage)
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
                            val mode = ModeRequest(
                                message = "RFID_MODE",
                                user_id = user?.id!!
                            )
                            scope.launch {
                                try {
                                    val response = RetrofitClient.dynamicApiService.sendMode(mode)
                                    println("Response: $response")
                                } catch (e: Exception) {
                                    println("Error sending mode: ${e.message}")
                                }
                            }
//                            mqttClient.publish(context, topicPublish, modeMessage)
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
