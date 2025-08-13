package com.example.nav.util

object HazardRadius {
    fun fromType(typeText: String?, msgText: String?): Double {
        val t = (typeText ?: msgText ?: "").lowercase()
        return when {
            t.contains("교통통제") -> 250.0
            t.contains("산사태")   -> 700.0
            t.contains("호우")     -> 1200.0
            t.contains("태풍")     -> 1500.0
            t.contains("한파")     -> 400.0
            t.contains("지진")     -> 1200.0
            t.contains("폭발") || t.contains("가스") -> 800.0
            else -> 500.0
        }
    }
}
