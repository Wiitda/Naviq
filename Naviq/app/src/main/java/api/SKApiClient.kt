package com.example.nav.api

import com.example.nav.data.model.SKRouteResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*
import retrofit2.Callback

object SKApiClient {
    private const val BASE_URL = "https://apis.openapi.sk.com/tmap/routes/"

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }).build()
    }

    val retrofit: SKRouteService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SKRouteService::class.java)
    }
}
