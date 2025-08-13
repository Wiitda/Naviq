package com.example.nav.data.model

data class DistanceResponse(
    val distance: String
)

data class DistanceResult(
    val success: DistanceResponse? = null,
    val failure: Throwable? = null
)