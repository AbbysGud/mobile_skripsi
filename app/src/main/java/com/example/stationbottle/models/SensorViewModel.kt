package com.example.stationbottle.models

import androidx.lifecycle.ViewModel
import com.example.stationbottle.client.RetrofitClient.apiService
import com.example.stationbottle.data.LatestDataResponse
import com.example.stationbottle.data.Provinsi
import com.example.stationbottle.data.SuhuResponse

class SensorViewModel : ViewModel() {
    suspend fun getLastSuhuByDeviceId(deviceId: String): SuhuResponse? {
        return try {
            apiService.getLastSuhuByDeviceId(deviceId)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun getLastDrinkEvent(userId: Int): LatestDataResponse? {
        return try {
            apiService.getLastDrinkEvent(userId)
        } catch (e: Exception) {
            e.printStackTrace()
            println(e)
            return null
        }
    }
}