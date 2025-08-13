package com.example.nav.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface KakaoRouteService {
    @GET("v1/directions")
    fun getDirections(
        @Header("Authorization") auth: String,  // KakaoAK {API_KEY}
        @Query("origin") origin: String,        // "127.111,37.394,name=출발지"
        @Query("destination") destination: String, // "127.122,37.398,name=도착지"
        @Query("priority") priority: String = "RECOMMEND"
    ): Call<ResponseBody>
}
