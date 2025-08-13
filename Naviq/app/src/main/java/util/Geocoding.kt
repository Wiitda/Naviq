package com.example.nav.util

import com.example.nav.api.KakaoLocalClient
import com.kakao.vectormap.LatLng

object Geocoding {
    suspend fun toLatLng(place: String?, regionFallback: String?, kakaoRestKey: String): LatLng? {
        val header = "KakaoAK $kakaoRestKey"

        if (!place.isNullOrBlank()) {
            val kw = KakaoLocalClient.retrofit.searchKeyword(header, place)
            kw.documents?.firstOrNull()?.let { return LatLng.from(it.y.toDouble(), it.x.toDouble()) }
        }
        val region = regionFallback?.replace(Regex("\\s+전체$"), "")
        if (!region.isNullOrBlank()) {
            val ad = KakaoLocalClient.retrofit.searchAddress(header, region)
            ad.documents?.firstOrNull()?.let { return LatLng.from(it.y.toDouble(), it.x.toDouble()) }
        }
        return null
    }
}
