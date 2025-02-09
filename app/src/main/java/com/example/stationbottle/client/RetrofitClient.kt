package com.example.stationbottle.client

import com.example.stationbottle.service.ApiService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "http://ariqbs.my.id/api/"
    private var dynamicBaseUrl: String = BASE_URL

    private val headerInterceptor = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .apply {
                    header(
                        "Cookie",
                        "XSRF-TOKEN=eyJpdiI6ImY4dHg5RVZLakZTaVcyeDhsTWFxc3c9PSIsInZhbHVlIjoicmdPNkc3N0lTRWxCb1VhdUp4OHQ2VmYyZlphSVc4Q2U3OTh0S0RBTDV0cXRoUzRzbEQwcXJPWkNXRGlmSlRuWW8waEI5S0toalM1VkczRWhtSFVIblJMS2FpUUl1MVRiaUprNWlRSDk4VzJlV2JnNDJ2NTVndUhDd3VqNDZBSTkiLCJtYWMiOiIwMzBkOWFiOTY2ZGFmZWFiMjM1MTM5YjVlNTk4MjJhYTIzMDBmOWU4NjdkYTcxMzQwNTgyMDQwMGExODdmNGE3IiwidGFnIjoiIn0%3D; " +
                                "stationbottle_session=eyJpdiI6InpQZzJmZ1lvTnpzOHlSTFNTNkgvamc9PSIsInZhbHVlIjoiODJNb2hrbVpvTHdXT0RKS0F5Wm1JQ0NlWGRvZkJBSTlkMU9YQUZuNU1mOWJpT2kzRVczcitDYmxERVpjeUZEcDNQUUNFaURSTnljNlJjRzk4Q3lkRGxVZVFvTDJGL3RJNitscUppOU5kc1MzMThRaGJ3bEkxakZwY2Z0T2ZheXAiLCJtYWMiOiI0MTVmOWVmYWNmNzM1YTMzYTE1NDlmYWJkNTk0ZjUwODkxM2MzMmVjMmUwYTdjMjUwZTRkOWI1NzZhNTIxMWU5IiwidGFnIjoiIn0%3D"
                    )
                    header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
                    )
                }
                .build()
            return chain.proceed(newRequest)
        }
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    val dynamicApiService: ApiService
        get() {
            return Retrofit.Builder()
                .baseUrl(dynamicBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }

    fun setDynamicBaseUrl(newUrl: String) {
        dynamicBaseUrl = newUrl
    }
}
