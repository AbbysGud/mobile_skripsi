package com.example.stationbottle.worker

import okhttp3.*
import org.json.JSONObject

class WebSocketManager {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    fun connectWebSocket(serverUrl: String) {
        val request = Request.Builder()
            .url(serverUrl) // URL dari ngrok
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                println("✅ WebSocket Connected")

                // Subscribe ke channel yang menerima data dari Arduino
                val subscribeMessage = """{
                    "event": "pusher:subscribe",
                    "data": { "channel": "private-weight-channel" }
                }""".trimIndent()
                webSocket.send(subscribeMessage)
                println("📡 Subscribed to private-weight-channel")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                println("📩 Received Message: $text")

                // Jika pesan dari event "client-WeightEvent", proses datanya
                val jsonData = JSONObject(text)
                if (jsonData.has("event") && jsonData.getString("event") == "client-WeightEvent") {
                    val data = JSONObject(jsonData.getString("data"))
                    val weight = data.getDouble("weight")
                    val mode = data.getString("mode")
                    val rfid = data.getString("rfid")

                    println("➡️ Weight: $weight, Mode: $mode, RFID: $rfid")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("❌ WebSocket Error: ${t.message}")
            }
        })
    }

    fun sendModeData(mode: String) {
        val jsonPayload = """{
            "event": "client-ModeEvent",
            "channel": "mode-channel",
            "data": { "mode": "$mode" }
        }""".trimIndent()

        webSocket?.send(jsonPayload)
        println("📤 Sent Mode Data: $jsonPayload")
    }
}