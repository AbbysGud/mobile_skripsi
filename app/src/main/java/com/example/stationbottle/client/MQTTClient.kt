package com.example.stationbottle.client

import android.content.Context
import android.widget.Toast
import org.eclipse.paho.client.mqttv3.*

class MQTTClient(private val serverUri: String) {
    private val clientId = "SmartStationApp"
    private lateinit var mqttClient: MqttClient

    val isConnected: Boolean
        get() = this::mqttClient.isInitialized && mqttClient.isConnected

    fun connect(
        context: Context
    ) {
        try {
            if (::mqttClient.isInitialized && mqttClient.isConnected) {
                println("Already connected to MQTT broker")
                Toast.makeText(context, "Sudah Terkoneksi dengan MQTT", Toast.LENGTH_SHORT).show()
                return
            }
            mqttClient = MqttClient(serverUri, clientId, null)

            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
            }

            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    println("Connection lost: ${cause?.message}")
                    Toast.makeText(context, "Koneksi dengan MQTT Terputus: ${cause?.message}", Toast.LENGTH_SHORT).show()
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    println("Message arrived: $topic -> ${message?.toString()}")
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    println("Delivery complete")
                }
            })

            mqttClient.connect(options)
            println("Connected to MQTT broker")
            Toast.makeText(context, "Terkoneksi dengan MQTT", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            println("Failed to connect: ${e.message}")
            Toast.makeText(context, "Gagal Terkoneksi dengan MQTT: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun subscribe(
        context: Context,
        topic: String,
        callback: (String) -> Unit)
    {
        try {
            mqttClient.subscribe(topic, 0)
            println("Subscribed to $topic with QoS 0")
            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    println("Connection lost: ${cause?.message}")
                    Toast.makeText(context, "Koneksi dengan MQTT Terputus: ${cause?.message}", Toast.LENGTH_SHORT).show()
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (message != null) {
                        callback(String(message.payload))
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    println("Delivery complete")
                }
            })
        } catch (e: Exception) {
            println("Failed to subscribe: ${e.message}")
            Toast.makeText(context, "Gagal Menerima Data dari Sensor: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun publish(
        context: Context,
        topic: String,
        payload: String
    ) {
        try {
            val message = MqttMessage(payload.toByteArray()).apply { qos = 0 }
            mqttClient.publish(topic, message)
            println("Message published to $topic: $payload")
            Toast.makeText(context, "Pesan Terkirim ke Sensor", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            println("Error publishing message: ${e.message}")
            Toast.makeText(context, "Gagal Mengirim Pesan ke Sensor: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun disconnect(
        context: Context
    ) {
        try {
            if (::mqttClient.isInitialized && mqttClient.isConnected) {
                mqttClient.disconnect()
                println("Disconnected from MQTT broker")
                Toast.makeText(context, "Koneksi dengan MQTT Terputus", Toast.LENGTH_SHORT).show()
            }
        } catch (e: MqttException) {
            println("Error disconnecting: ${e.message}")
            Toast.makeText(context, "Gagal Memutus Koneksi dengan MQTT: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}