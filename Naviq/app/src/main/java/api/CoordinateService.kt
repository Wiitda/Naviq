package com.example.nav.api

import com.example.nav.data.model.CoordConvertResponse
import com.example.nav.data.model.DistanceResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query


interface CoordinateService {
    /**
     * SK 좌표 변환
     */
    @GET("coordconvert")
    fun coordConvertRequest(
        @Header("appKey") appKey: String,
        @Query("version") version: String,
        @Query("lat") lat: String,
        @Query("lon") lon: String,
        @Query("fromCoord") fromCoord: String,
        @Query("toCoord") toCoord: String
    ): Call<CoordConvertResponse>


    /**
     * SK 직선 거리 계산
     */
    @GET("routes/distance")
    fun distanceRequest(
        @Header("appKey") appKey: String,
        @Query("version") version: String,
        @Query("startX") startX: String,
        @Query("startY") startY: String,
        @Query("endX") endX: String,
        @Query("endY") endY: String,
        @Query("coordinateType") coordinateType: String
    ): Call<DistanceResponse>
}
