package com.example.stationbottle.service

import com.example.stationbottle.data.BMKGWeatherResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface BMKGAPIService {
    @GET("publik/prakiraan-cuaca")
    suspend fun getWeatherForecast(
        @Query("adm4") adm4: String
    ): Response<BMKGWeatherResponse>
}