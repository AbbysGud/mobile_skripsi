package com.example.stationbottle.client

import com.example.stationbottle.service.BMKGAPIService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object BMKGRetrofitClient {
    private const val BASE_URL_BMKG = "https://api.bmkg.go.id/"

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val bmkgAPIService: BMKGAPIService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_BMKG)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BMKGAPIService::class.java)
    }
}