package com.example.stationbottle.ui.screens

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.stationbottle.R
import com.example.stationbottle.ThemeViewModelFactory
import com.example.stationbottle.client.RetrofitClient
import com.example.stationbottle.data.ModeRequest
import com.example.stationbottle.models.ThemeViewModel
import com.example.stationbottle.models.UserViewModel
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.PrivateChannelEventListener
import com.pusher.client.channel.PusherEvent
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionState
import com.pusher.client.connection.ConnectionStateChange
import com.pusher.client.util.HttpAuthorizer
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun StationScreen() {
    var isLoading by remember { mutableStateOf(true) }

    val context = LocalContext.current

    val userViewModel = UserViewModel()
    val userState = userViewModel.getUser(context).collectAsState(initial = null)
    val user = userState.value
    val scope = rememberCoroutineScope()

    val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(context))
    val isDarkTheme = themeViewModel.isDarkMode.collectAsState(initial = false)

    var options = PusherOptions()
    var pusher: Pusher = Pusher("stationbottlebe_pusher", options)

    val (weight, setWeight) = remember { mutableFloatStateOf(0.0f) }
    val (mode, setMode) = remember { mutableStateOf("") }
    val (rfid, setRFID) = remember { mutableStateOf("") }
    val (message, setMessage) = remember { mutableStateOf("") }
    val (isConnected, setConnected) = remember { mutableStateOf(false) }

    val (temperature, setTemperature) = remember { mutableFloatStateOf(0.0f) }
    val (humidity, setHumidity) = remember { mutableFloatStateOf(0.0f) }
    val (deviceId, setDeviceId) = remember { mutableStateOf("") }

    var userId by remember { mutableStateOf<Int>(0) }
    var userDeviceId by remember { mutableStateOf<String>("") }

    LaunchedEffect(user) {
        if (user == null) {
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true

        userId = user.id
        userDeviceId = user.device_id.toString()

        pusher.disconnect()
        setConnected(false)

        pusher = connectToPusher(setWeight, setMode, setRFID, setMessage, setConnected, setTemperature, setHumidity, setDeviceId)

        isLoading = false
    }

    DisposableEffect(pusher) {
        val currentPusher = pusher
        onDispose {
            if (currentPusher.connection.state != ConnectionState.DISCONNECTED) {
                disconnectFromPusher(currentPusher, setConnected, setWeight, setMode, setRFID, setMessage, setTemperature, setHumidity, setDeviceId)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()){
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
                                if (isConnected == true && mode != "")
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
                            if (isConnected == true && mode != "")
                                "Online"
                            else
                                "Offline",
                            color =
                            if (isConnected == true && mode != "")
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isConnected == false) {
                        Text(
                            text = "Belum Terhubung ke Server",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        )
                    } else if (mode == "") {
                        Text(
                            text = "Sensor Tidak Nyala",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        )
                    } else if (deviceId != userDeviceId) {
                        println("device: $deviceId")
                        println("user: $userDeviceId")
                        Text(
                            text = "Alat Tidak Nyala",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        )
                    } else {
                        Text(
                            text = "Terhubung ke Sensor",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = "Alat: $deviceId",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (isConnected == true) {
                                disconnectFromPusher(pusher, setConnected, setWeight, setMode, setRFID, setMessage, setTemperature, setHumidity, setDeviceId)
                            } else {
                                scope.launch {
                                    pusher = connectToPusher(setWeight, setMode, setRFID, setMessage, setConnected, setTemperature, setHumidity, setDeviceId)
                                }
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
                            if (isConnected == true)
                                "Disconnect"
                            else
                                "Connect"
                        )
                    }

                }
            }

            if (isConnected == true) {
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
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "$weight g",
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Light,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
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
                                                message = "TARE_SCALE",
                                                user_id = userId,
                                                device_id = userDeviceId,
                                            )
                                            scope.launch {
                                                try {
                                                    val response = RetrofitClient.dynamicApiService.sendMode(mode)
                                                    println("Response: $response")
                                                } catch (e: Exception) {
                                                    println("Error sending mode: ${e.message}")
                                                }
                                            }
                                        },
                                    elevation = CardDefaults.elevatedCardElevation(4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.baseline_power_settings_new_24),
                                            contentDescription = "TARE ICON",
                                            modifier = Modifier.size(32.dp),
                                            contentScale = ContentScale.Fit
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = "TARE",
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
                                                message = "GANTI_BOTOL",
                                                user_id = userId,
                                                device_id = userDeviceId,
                                            )
                                            scope.launch {
                                                try {
                                                    val response = RetrofitClient.dynamicApiService.sendMode(mode)
                                                    println("Response: $response")
                                                } catch (e: Exception) {
                                                    println("Error sending mode: ${e.message}")
                                                }
                                            }
                                        },
                                    elevation = CardDefaults.elevatedCardElevation(4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.bottle_white),
                                            contentDescription = "GANTI ICON",
                                            modifier = Modifier.size(32.dp),
                                            contentScale = ContentScale.Fit
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = "GANTI BOTOL",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
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
                    text = "Data Lingkungan",
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround, // Untuk spacing ikon dan teks
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Image(
                                    painter = painterResource(
                                        if (isDarkTheme.value)
                                            R.drawable.outline_thermostat_white
                                        else
                                            R.drawable.outline_thermostat
                                    ),
                                    contentDescription = "Temperature Icon",
                                    modifier = Modifier.size(48.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Suhu",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${String.format("%.1f", temperature)} °C",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp)) // Spacer antara Suhu dan Kelembaban

                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Image(
                                    painter = painterResource(
                                        if (isDarkTheme.value)
                                            R.drawable.outline_opacity_white
                                        else
                                            R.drawable.outline_opacity
                                    ),
                                    contentDescription = "Humidity Icon",
                                    modifier = Modifier.size(48.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Kelembaban",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${String.format("%.1f", humidity)} %",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
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

                Spacer(modifier = Modifier.height(16.dp))

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
                                    user_id = userId,
                                    device_id = userDeviceId,
                                )
                                scope.launch {
                                    try {
                                        val response = RetrofitClient.dynamicApiService.sendMode(mode)
                                        println("Response: $response")
                                    } catch (e: Exception) {
                                        println("Error sending mode: ${e.message}")
                                    }
                                }
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
                                    user_id = userId,
                                    device_id = userDeviceId,
                                )
                                scope.launch {
                                    try {
                                        val response = RetrofitClient.dynamicApiService.sendMode(mode)
                                        println("Response: $response")
                                    } catch (e: Exception) {
                                        println("Error sending mode: ${e.message}")
                                    }
                                }
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

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }

}

suspend fun connectToPusher(
    setWeight: (Float) -> Unit,
    setMode: (String) -> Unit,
    setRFID: (String) -> Unit,
    setMessage: (String) -> Unit,
    setConnected: (Boolean) -> Unit,
    setTemperature: (Float) -> Unit,
    setHumidity: (Float) -> Unit,
    setDeviceId: (String) -> Unit,
): Pusher {
    val response = RetrofitClient.apiService.getNGROKUrl()

    RetrofitClient.setDynamicBaseUrl("${response.http_url}/api/")

    val options = PusherOptions().apply {
        setCluster("mt1")
        setHost("0.tcp.ap.ngrok.io")
        setWsPort(response.websocket_port)
        isUseTLS = false
        setAuthorizer(
            HttpAuthorizer("${response.http_url}/broadcasting/auth").apply {
                setHeaders(
                    mapOf(
                        "Accept" to "application/json",
                        "X-Requested-With" to "XMLHttpRequest"
                    )
                )
            }
        )
    }

    val pusher = Pusher("stationbottlebe_pusher", options)
    println("Pusher instance created")

    val channel = pusher.subscribePrivate("private-weight-channel")

    channel.bind("client-WeightEvent", object : PrivateChannelEventListener {
        override fun onEvent(event: PusherEvent) {
            try {
                val jsonObject = JSONObject(event.data)
                val weight = jsonObject.optDouble("weight", 0.0).toFloat()
                val mode = jsonObject.optString("mode", "WEIGH_MODE")
                val rfid = jsonObject.optString("rfid", "")
                val message = jsonObject.optString("message", "")
                val temperature = jsonObject.optDouble("temperature", 0.0).toFloat()
                val humidity = jsonObject.optDouble("humidity", 0.0).toFloat()
                val deviceId = jsonObject.optString("device_id", "")

                setWeight(weight)
                setMode(mode)
                setRFID(rfid)
                setMessage(message)
                setTemperature(temperature)
                setHumidity(humidity)
                setDeviceId(deviceId)

            } catch (e: JSONException) {
                setWeight(0.0f)
                setMode("")
                setRFID("")
                setMessage("Error parsing data")
                setTemperature(0.0f)
                setHumidity(0.0f)
                setDeviceId("")
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
            if (change.currentState == ConnectionState.CONNECTED) {
                setConnected(true)
            }
        }

        override fun onError(message: String?, code: String?, e: Exception?) {
            println("Error: $message, Code: $code, Exception: $e")
            setMessage("Connection error: $message")
        }
    })

    return pusher
}

fun disconnectFromPusher(
    pusher: Pusher?,
    setConnected: (Boolean) -> Unit,
    setWeight: (Float) -> Unit,
    setMode: (String) -> Unit,
    setRFID: (String) -> Unit,
    setMessage: (String) -> Unit,
    setTemperature: (Float) -> Unit,
    setHumidity: (Float) -> Unit,
    setDeviceId: (String) -> Unit,
) {
    pusher?.disconnect()
    setConnected(false)
    setWeight(0.0f)
    setMode("")
    setRFID("")
    setMessage("Disconnected from Pusher")
    setTemperature(0.0f)
    setHumidity(0.0f)
    setDeviceId("")

    println("Disconnected from Pusher")
}
