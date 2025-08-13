package com.example.nav.data.model

data class SKRouteResponse(
    val type: String,
    val features: List<Feature>
)

data class Feature(
    val type: String,
    val geometry: Geometry,
    val properties: Map<String, Any> // 필요시 조정
)

data class Geometry(
    val type: String,
    val coordinates: List<List<Double>> // [[x, y], [x, y], ...]
)
