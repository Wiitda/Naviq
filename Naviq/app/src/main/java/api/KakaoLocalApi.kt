package com.example.nav.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class KakaoDoc(val x: String, val y: String, val place_name: String?)
data class KakaoSearchResp(val documents: List<KakaoDoc>?)

interface KakaoLocalApi {
    @GET("v2/local/search/keyword.json")
    suspend fun searchKeyword(
        @Header("Authorization") authorization: String, // "KakaoAK {REST_KEY}"
        @Query("query") query: String
    ): KakaoSearchResp

    @GET("v2/local/search/address.json")
    suspend fun searchAddress(
        @Header("Authorization") authorization: String,
        @Query("query") query: String
    ): KakaoSearchResp
}
