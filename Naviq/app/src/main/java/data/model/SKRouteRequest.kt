package com.example.nav.data.model

import com.google.gson.annotations.SerializedName

data class SKRouteRequest(
    @SerializedName("reqCoordType") val reqCoordType: String = "WGS84GEO",
    @SerializedName("resCoordType") val resCoordType: String = "WGS84GEO",
    @SerializedName("startName") val startName: String,
    @SerializedName("startX") val startX: String,
    @SerializedName("startY") val startY: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endName") val endName: String,
    @SerializedName("endX") val endX: String,
    @SerializedName("endY") val endY: String,
    @SerializedName("searchOption") val searchOption: String = "0",
    @SerializedName("carType") val carType: String = "1",
    @SerializedName("viaPoints") val viaPoints: List<ViaPoint>? = null
){
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "reqCoordType" to reqCoordType,
            "resCoordType" to resCoordType,
            "startName" to startName,
            "startX" to startX,
            "startY" to startY,
            "startTime" to startTime,
            "endName" to endName,
            "endX" to endX,
            "endY" to endY,
            "searchOption" to searchOption,
            "carType" to carType
        )
        viaPoints?.let { map["viaPoints"] = it }
        return map
    }
}

data class ViaPoint(
    @SerializedName("viaPointId") val viaPointId: String,
    @SerializedName("viaPointName") val viaPointName: String,
    @SerializedName("viaX") val viaX: String,
    @SerializedName("viaY") val viaY: String
)

