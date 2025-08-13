package com.example.nav.util

object DisasterTextParser {
    private val keywordRegex = Regex(
        """([가-힣A-Za-z0-9]+(역|로|길|대로|거리|터널|IC|JCT|교차로|시장|공원|병원|대학교|고등학교|중학교|초등학교|지하차도|세월교))"""
    )
    private val adminRegex = Regex("""([가-힣A-Za-z0-9]+(시|군|구)\s*[가-힣A-Za-z0-9]+(동|읍|면))""")

    fun fromMessage(msg: String?): String? {
        if (msg.isNullOrBlank()) return null
        keywordRegex.find(msg)?.let { return it.value }
        adminRegex.find(msg)?.let { return it.value }
        val cleaned = msg.replace(Regex("(인근|주변|부근|일대)"), "")
        keywordRegex.find(cleaned)?.let { return it.value }
        return null
    }

    fun firstRegion(regionField: String?): String? {
        if (regionField.isNullOrBlank()) return null
        return regionField.split(',', '，', '\n')
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.replace(Regex("\\s+전체$"), "") // "부산광역시 전체" -> "부산광역시"
    }
}
