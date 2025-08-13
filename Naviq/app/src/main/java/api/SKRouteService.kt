package com.example.nav.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface SKRouteService {
    @POST("routeSequential30")
    fun getRoutesRaw(
        @Header("appKey") appKey: String,
        @Header("Accept") accept: String = "application/json",
        @Header("Content-Type") contentType: String = "application/json",
        @Query("version") version: Int = 1,
        @Body body: RequestBody
    ): Call<ResponseBody>

}
