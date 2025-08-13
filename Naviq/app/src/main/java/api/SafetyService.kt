package com.example.nav.api

import com.example.nav.data.model.SafetyResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface SafetyService {
    @GET("V2/api/DSSP-IF-00247")
    fun getDisasterMessages(
        @Query("serviceKey") serviceKey: String,
        @Query("returnType") returnType: String = "json",
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") rows: Int = 50,
        @Query("crtDt") crtDt: String? = null,        // YYYYMMDD
        @Query("rgnNm") regionName: String? = null
    ): Call<SafetyResponse>
}
