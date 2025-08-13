package com.example.nav.repository

import com.example.nav.api.CoordinateService
import com.example.nav.data.model.CoordConvertResponse
import com.example.nav.data.model.DistanceResponse
import com.kakaomobility.knsdk.common.util.FloatPoint
import com.example.nav.BuildConfig
import javax.inject.Inject
import android.util.Log

class NavigationRepository @Inject constructor(
    private val service: CoordinateService
) {

    fun getCoordConvertData(lat: Double, lon: Double): CoordConvertResponse? {
        Log.d("NAVI_ROTATION", "SK_APP_KEY: ${BuildConfig.SK_APP_KEY}")

        val response = service.coordConvertRequest(
            appKey = BuildConfig.SK_APP_KEY,
            version = "1",
            lat = lat.toString(),
            lon = lon.toString(),
            fromCoord = "WGS84GEO",
            toCoord = "KATECH"
        ).execute()

        Log.d("NAVI_ROTATION", "Raw response: $response")
        Log.d("NAVI_ROTATION", "Response body: ${response.body()}")
        Log.d("NAVI_ROTATION", "Response error: ${response.errorBody()?.string()}")

        return response.body()
    }

    fun getDistanceData(start: FloatPoint, end: FloatPoint): DistanceResponse? {
        Log.d("NAVI_ROTATION", "SK_APP_KEY: ${BuildConfig.SK_APP_KEY}") // 여기!
        return service.distanceRequest(
            appKey = BuildConfig.SK_APP_KEY,
            version = "1",
            startX = start.x.toString(),
            startY = start.y.toString(),
            endX = end.x.toString(),
            endY = end.y.toString(),
            coordinateType = "KATECH"
        ).execute().body()
    }
}
