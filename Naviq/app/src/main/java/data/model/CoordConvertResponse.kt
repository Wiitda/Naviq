package com.example.nav.data.model

data class CoordConvertResponse(
    val coordinate: Coordinate
)

data class Coordinate(
    val lat: String,
    val lon: String
)

data class CoordZipResult(
    val success: CoordZipData? = null,
    val failure: Throwable? = null
)

data class CoordZipData(
    val startLatitude: String?,
    val startLongitude: String?,
    val endLatitude: String?,
    val endLongitude: String?
)
