package com.example.nav

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.nav.api.KakaoApiClient
import com.example.nav.api.SafetyApiClient
import com.example.nav.data.model.SafetyResponse
import com.example.nav.databinding.ActivityNavigationPreviewBinding
import com.example.nav.util.DisasterTextParser
import com.example.nav.util.Geocoding
import com.example.nav.util.HazardRadius
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.route.RouteLine
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

class NavigationPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNavigationPreviewBinding
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null

    // ── 토글 텍스트 색상 상수 ─────────────────────────────────────────────────
    private val COLOR_DEFAULT = Color.parseColor("#2D2D2D")
    private val COLOR_ORIGIN  = Color.parseColor("#306FD0") // 기존 경로 선택 색
    private val COLOR_DETOUR  = Color.parseColor("#FF4A5A") // 우회 경로 선택 색

    // 라인 핸들 (토글용)
    private var originLine: RouteLine? = null
    private var detourLine: RouteLine? = null

    // 우회 계산용 마지막 재난 원 정보 (표시는 안 하지만 계산엔 사용)
    private var lastHazardCenter: LatLng? = null
    private var lastHazardRadius: Double = 0.0

    // “변경 이유” 표시용 정보
    private var lastHazardType: String? = null
    private var lastHazardRegion: String? = null
    private var lastHazardMsg: String? = null

    // 길이 비교용
    private var baseRouteMeters: Double? = null
    private var detourRouteMeters: Double? = null

    // 기본 경로 좌표(근접 재난 선별 등 확장용)
    private var routePoints: List<LatLng> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigationPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapView = binding.mapView

        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(e: Exception?) { e?.printStackTrace() }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                setupRouteToggle()
                loadDisasterAndRender(map)
            }
        })

        binding.btnStartNavigation.setOnClickListener {
            val navIntent = Intent(this, NavigationActivity::class.java).apply {
                putExtra("startLatitude", intent.getDoubleExtra("startLatitude", 0.0))
                putExtra("startLongitude", intent.getDoubleExtra("startLongitude", 0.0))
                putExtra("endLatitude", intent.getDoubleExtra("endLatitude", 0.0))
                putExtra("endLongitude", intent.getDoubleExtra("endLongitude", 0.0))
            }
            startActivity(navIntent)
        }
    }

    // 상단 토글 설정: 기본 ‘기존 경로’
    private fun setupRouteToggle() {
        binding.tgRoutes?.check(binding.btnOrigin.id)
        updateToggleColors() // ✅ 초기 색상 적용

        binding.tgRoutes?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                binding.btnOrigin.id -> {
                    originLine?.show()
                    detourLine?.hide()
                    updateReasonBanner()
                }
                binding.btnDetour.id -> {
                    if (detourLine != null) {
                        detourLine?.show(); originLine?.hide()
                        updateReasonBanner()
                    } else {
                        val c = lastHazardCenter
                        val r = lastHazardRadius
                        if (c == null || r <= 0.0) {
                            originLine?.show(); detourLine?.hide()
                            updateReasonBanner()
                        } else {
                            drawDetourRouteLine(kakaoMap ?: return@addOnButtonCheckedListener, c, r) {
                                detourLine?.show(); originLine?.hide()
                                updateReasonBanner()
                            }
                        }
                    }
                }
            }
            updateToggleColors() // ✅ 선택 변경 시마다 색 갱신
        }
    }

    // ✅ 토글 텍스트 색상 갱신
    private fun updateToggleColors() {
        val checked = binding.tgRoutes?.checkedButtonId
        binding.btnOrigin.setTextColor(if (checked == binding.btnOrigin.id) COLOR_ORIGIN else COLOR_DEFAULT)
        binding.btnDetour.setTextColor(if (checked == binding.btnDetour.id) COLOR_DETOUR else COLOR_DEFAULT)
    }

    /** 재난문자 → 지오코딩 → (표시는 생략) + 기본 경로 + 필요 시 우회 계산 준비 */
    private fun loadDisasterAndRender(map: KakaoMap) {
        val zone = java.time.ZoneId.of("Asia/Seoul")
        val now = java.time.ZonedDateTime.now(zone)
        val fromIso = now.minusHours(2).toLocalDateTime().withNano(0).toString()
        val toIso   = now.toLocalDateTime().withNano(0).toString()

        val tz = TimeZone.getTimeZone("Asia/Seoul")
        val ymdFmt = SimpleDateFormat("yyyyMMdd", Locale.KOREA).apply { timeZone = tz }
        val todayYmd = ymdFmt.format(Date())

        SafetyApiClient.service.getDisasterMessages(
            serviceKey = BuildConfig.SAFETY_DATA_KEY,
            returnType = "json",
            pageNo = 1,
            rows = 50,
            crtDt = todayYmd,
            regionName = null
        ).enqueue(object : Callback<SafetyResponse> {
            override fun onResponse(call: Call<SafetyResponse>, response: Response<SafetyResponse>) {
                val kstFmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.KOREA).apply { timeZone = tz }
                val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA).apply { timeZone = tz }
                val fromDate = runCatching { isoFmt.parse(fromIso) }.getOrNull()
                val toDate   = runCatching { isoFmt.parse(toIso) }.getOrNull()

                val items = response.body()?.body.orEmpty()
                    .filter { it.createdAt != null }
                    .filter { item ->
                        val d = runCatching { kstFmt.parse(item.createdAt!!) }.getOrNull()
                        d != null && fromDate != null && toDate != null && !d.before(fromDate) && !d.after(toDate)
                    }
                    .sortedByDescending { runCatching { kstFmt.parse(it.createdAt!!)?.time ?: 0L }.getOrDefault(0L) }

                if (items.isEmpty()) {
                    drawRouteLine(map)
                    return
                }

                val item = items.first()
                val rawPlace = DisasterTextParser.fromMessage(item.msg)
                val place = when {
                    rawPlace.isNullOrBlank() -> DisasterTextParser.firstRegion(item.regionName)
                    Regex("(위험|대피|통제|주의|경보|주의보|호우|태풍)").containsMatchIn(rawPlace) ->
                        DisasterTextParser.firstRegion(item.regionName)
                    else -> rawPlace
                }

                // “변경 이유” 정보 저장
                lastHazardType = item.disasterType
                lastHazardRegion = item.regionName
                lastHazardMsg = item.msg

                lifecycleScope.launch {
                    val center = Geocoding.toLatLng(
                        place = place,
                        regionFallback = item.regionName,
                        kakaoRestKey = BuildConfig.KAKAO_REST_API_KEY
                    )
                    if (center != null) {
                        val radius = HazardRadius.fromType(item.disasterType, item.msg)
                        lastHazardCenter = center
                        lastHazardRadius = radius
                        drawRouteLine(map) // 기본 경로부터 그림
                    } else {
                        drawRouteLine(map)
                    }
                }
            }

            override fun onFailure(call: Call<SafetyResponse>, t: Throwable) {
                t.printStackTrace()
                drawRouteLine(map)
            }
        })
    }

    /** 기존 경로: A -> B (RouteLine 사용, 파랑 굵게) */
    private fun drawRouteLine(map: KakaoMap) {
        val startLat = intent.getDoubleExtra("startLatitude", 0.0)
        val startLng = intent.getDoubleExtra("startLongitude", 0.0)
        val endLat = intent.getDoubleExtra("endLatitude", 0.0)
        val endLng = intent.getDoubleExtra("endLongitude", 0.0)

        val origin = "$startLng,$startLat,name=출발지"
        val destination = "$endLng,$endLat,name=도착지"

        KakaoApiClient.retrofit.getDirections(kakaoAuthHeader(), origin, destination)
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    val jsonString = response.body()?.string() ?: run {
                        Log.e("ROUTE", "응답 없음"); return
                    }
                    try {
                        val points = parseRoutePoints(jsonString)
                        if (points.isEmpty()) { Log.e("ROUTE", "경로 포인트 없음"); return }

                        routePoints = points
                        baseRouteMeters = polylineLengthMeters(points)

                        val style = RouteLineStyle.from(12f, Color.parseColor("#1976D2")) // 파랑 굵게
                        val seg = RouteLineSegment.from(points, style)
                        val layer = kakaoMap?.routeLineManager?.layer ?: return
                        originLine?.hide()
                        originLine = layer.addRouteLine(RouteLineOptions.from(seg)).also {
                            if (binding.tgRoutes?.checkedButtonId == binding.btnDetour.id) it.hide() else it.show()
                        }
                        kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(points.first()))
                        updateReasonBanner()
                    } catch (e: Exception) {
                        Log.e("ROUTE", "파싱 오류: ${e.message}"); e.printStackTrace()
                    }
                }
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Log.e("ROUTE", "API 실패: ${t.message}")
                }
            })
    }

    /** 우회 경로 탐색(여러 후보 시도) → 주황 굵게로 그림 */
    private fun drawDetourRouteLine(
        map: KakaoMap,
        hazardCenter: LatLng,
        hazardRadius: Double,
        afterDraw: () -> Unit = {}
    ) {
        val start = LatLng.from(
            intent.getDoubleExtra("startLatitude", 0.0),
            intent.getDoubleExtra("startLongitude", 0.0)
        )
        val end = LatLng.from(
            intent.getDoubleExtra("endLatitude", 0.0),
            intent.getDoubleExtra("endLongitude", 0.0)
        )

        findDetourWithCandidates(start, end, hazardCenter, hazardRadius) { detour ->
            if (detour == null) {
                Log.w("DETOUR", "우회 경로 없음 — 기본 경로 유지")
                afterDraw()
                return@findDetourWithCandidates
            }
            detourRouteMeters = polylineLengthMeters(detour)

            val style = RouteLineStyle.from(12f, Color.parseColor("#FF9800")) // 주황 굵게
            val seg = RouteLineSegment.from(detour, style)
            val layer = kakaoMap?.routeLineManager?.layer ?: return@findDetourWithCandidates
            detourLine?.hide()
            detourLine = layer.addRouteLine(RouteLineOptions.from(seg)).also {
                if (binding.tgRoutes?.checkedButtonId == binding.btnDetour.id) it.show() else it.hide()
            }
            updateReasonBanner()
            afterDraw()
        }
    }

    /** Kakao Directions 응답 JSON → LatLng 리스트 (모든 섹션 합치기 + 중복 제거) */
    private fun parseRoutePoints(jsonString: String?): List<LatLng> {
        if (jsonString == null) return emptyList()
        return try {
            val json = JSONObject(jsonString)
            val routes = json.optJSONArray("routes") ?: return emptyList()
            if (routes.length() == 0) return emptyList()

            val sections = routes.getJSONObject(0).optJSONArray("sections") ?: return emptyList()
            val pts = ArrayList<LatLng>()
            var lastLat = Double.NaN
            var lastLng = Double.NaN

            for (s in 0 until sections.length()) {
                val roads = sections.getJSONObject(s).optJSONArray("roads") ?: continue
                for (i in 0 until roads.length()) {
                    val vtx = roads.getJSONObject(i).optJSONArray("vertexes") ?: continue
                    var j = 0
                    while (j + 1 < vtx.length()) {
                        val lng = vtx.optDouble(j)
                        val lat = vtx.optDouble(j + 1)
                        if (!lng.isNaN() && !lat.isNaN()) {
                            // 연속 중복 좌표 방지
                            if (lat != lastLat || lng != lastLng) {
                                pts.add(LatLng.from(lat, lng))
                                lastLat = lat; lastLng = lng
                            }
                        }
                        j += 2
                    }
                }
            }
            Log.d("ROUTE", "parsed points=${pts.size}")
            pts
        } catch (e: Exception) {
            Log.e("ROUTE", "파싱 오류: ${e.message}", e)
            emptyList()
        }
    }

    // ────────────── 우회 로직(원 가시화는 생략) ──────────────

    /** 경로가 원(위험 구역)과 교차하는지 검사 */
    private fun routeIntersectsCircle(path: List<LatLng>, center: LatLng, radiusMeters: Double): Boolean {
        val mPerLat = 111320.0
        val mPerLng = 88000.0 * cos(Math.toRadians(center.latitude))
        fun toXY(p: LatLng) = Pair(
            (p.longitude - center.longitude) * mPerLng,
            (p.latitude - center.latitude) * mPerLat
        )
        val r2 = radiusMeters * radiusMeters
        for (i in 0 until path.size - 1) {
            val (x1, y1) = toXY(path[i])
            val (x2, y2) = toXY(path[i + 1])
            val dx = x2 - x1
            val dy = y2 - y1
            val denom = dx*dx + dy*dy
            val t = if (denom == 0.0) 0.0 else (-(x1*dx + y1*dy)) / denom
            val tt = t.coerceIn(0.0, 1.0)
            val cx = x1 + tt*dx
            val cy = y1 + tt*dy
            if (cx*cx + cy*cy <= r2) return true
        }
        return false
    }

    /** 중심점에서 반경 r, 방위각 bearingDeg 위치의 점 */
    private fun ringPoint(center: LatLng, radiusMeters: Double, bearingDeg: Double): LatLng {
        val R = 6371000.0
        val brng = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(center.latitude)
        val lon1 = Math.toRadians(center.longitude)
        val dR = radiusMeters / R
        val sinLat1 = sin(lat1)
        val cosLat1 = cos(lat1)
        val sinLat2 = sinLat1 * cos(dR) + cosLat1 * sin(dR) * cos(brng)
        val lat2 = asin(sinLat2)
        val y = sin(brng) * sin(dR) * cosLat1
        val x = cos(dR) - sinLat1 * sinLat2
        val lon2 = lon1 + atan2(y, x)
        return LatLng.from(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    /** 원 둘레(반경+버퍼)에 후보 경유지 여러 개 생성 */
    private fun buildViaCandidates(
        start: LatLng, end: LatLng,
        center: LatLng, radiusMeters: Double,
        bufferMeters: Double
    ): List<LatLng> {
        val baseRad = atan2(end.latitude - start.latitude, end.longitude - start.longitude)
        val baseDeg = Math.toDegrees(baseRad)
        val offsets = listOf(+90.0, -90.0, +60.0, -60.0, +120.0, -120.0, +150.0, -150.0)
        val r = radiusMeters + bufferMeters
        return offsets.map { ringPoint(center, r, baseDeg + it) }
    }

    /** Kakao로 A->via, via->B 두 구간을 요청해 포인트 병합해서 리턴 */
    private fun requestTwoLegs(a: LatLng, v: LatLng, b: LatLng, onDone: (List<LatLng>?) -> Unit) {
        val origin1 = "${a.longitude},${a.latitude},name=출발지"
        val dest1   = "${v.longitude},${v.latitude},name=경유지"
        val origin2 = "${v.longitude},${v.latitude},name=경유지"
        val dest2   = "${b.longitude},${b.latitude},name=도착지"

        KakaoApiClient.retrofit.getDirections(kakaoAuthHeader(), origin1, dest1)
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, res1: Response<ResponseBody>) {
                    val p1 = parseRoutePoints(res1.body()?.string())
                    if (p1.isEmpty()) { onDone(null); return }

                    KakaoApiClient.retrofit.getDirections(kakaoAuthHeader(), origin2, dest2)
                        .enqueue(object : Callback<ResponseBody> {
                            override fun onResponse(call: Call<ResponseBody>, res2: Response<ResponseBody>) {
                                val p2 = parseRoutePoints(res2.body()?.string())
                                if (p2.isEmpty()) { onDone(null); return }

                                val all = ArrayList<LatLng>(p1.size + p2.size)
                                all.addAll(p1)
                                if (all.isNotEmpty() && p2.isNotEmpty() && all.last() == p2.first()) {
                                    all.addAll(p2.drop(1))
                                } else {
                                    all.addAll(p2)
                                }
                                onDone(all)
                            }
                            override fun onFailure(call: Call<ResponseBody>, t: Throwable) { onDone(null) }
                        })
                }
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) { onDone(null) }
            })
    }

    /** 후보들을 시도해 원과 교차하지 않는 경로를 찾아준다 */
    private fun findDetourWithCandidates(
        start: LatLng, end: LatLng,
        center: LatLng, radius: Double,
        onDone: (List<LatLng>?) -> Unit
    ) {
        val buffers = arrayOf(70.0, 110.0, 160.0)
        var bufferIdx = 0
        var candidates = emptyList<LatLng>()
        var idx = 0

        fun tryNext() {
            if (idx >= candidates.size) {
                if (bufferIdx >= buffers.size) { onDone(null); return }
                candidates = buildViaCandidates(start, end, center, radius, buffers[bufferIdx++])
                idx = 0
            }
            val via = candidates[idx++]
            requestTwoLegs(start, via, end) { merged ->
                // 교차 검사에 +30m 안전 마진
                if (merged != null && !routeIntersectsCircle(merged, center, radius + 30.0)) {
                    onDone(merged)
                } else {
                    tryNext()
                }
            }
        }
        tryNext()
    }

    /** 폴리라인 총 길이(m) */
    private fun polylineLengthMeters(points: List<LatLng>): Double {
        var sum = 0.0
        for (i in 0 until points.size - 1) {
            sum += haversineMeters(points[i], points[i + 1])
        }
        return sum
    }

    /** 대원거리(m) */
    private fun haversineMeters(a: LatLng, b: LatLng): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val s1 = sin(dLat / 2)
        val s2 = sin(dLng / 2)
        val aa = s1*s1 + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * s2*s2
        return 2 * R * asin(sqrt(aa))
    }

    /** 상단 배너(텍스트뷰)로 “변경 이유 / 거리 차이” 표시 */
    private fun updateReasonBanner() {
        val tv = binding.tvRouteDelta
        val isDetourSelected = binding.tgRoutes?.checkedButtonId == binding.btnDetour.id
        val base = baseRouteMeters
        val detour = detourRouteMeters

        val reason = buildString {
            if (isDetourSelected) {
                append("우회 적용")
                val t = lastHazardType?.trim().orEmpty()
                val r = lastHazardRegion?.trim().orEmpty()
                if (t.isNotEmpty() || r.isNotEmpty()) {
                    append(" · 이유: ")
                    if (t.isNotEmpty()) append(t)
                    if (t.isNotEmpty() && r.isNotEmpty()) append(" / ")
                    if (r.isNotEmpty()) append(r)
                }
                lastHazardMsg?.let {
                    val s = it.replace("\n", " ").trim()
                    if (s.isNotEmpty()) {
                        val cut = if (s.length > 36) s.take(36) + "…" else s
                        append(" (“").append(cut).append("”)")
                    }
                }
                if (base != null && detour != null) {
                    val delta = detour - base
                    append(" · 거리 ")
                    append(formatMetersPretty(detour))
                    append(if (delta >= 0) " (+" else " (-")
                    append(formatMetersPretty(kotlin.math.abs(delta)))
                    append(")")
                }
            } else {
                append("기본 경로")
                base?.let { append(" · 거리 ").append(formatMetersPretty(it)) }
            }
        }
        tv.text = reason
    }

    private fun formatMetersPretty(m: Double): String {
        return if (m >= 1000.0) String.format(Locale.KOREA, "%.1fkm", m / 1000.0)
        else "${m.toInt()}m"
    }

    /** Kakao REST API 헤더 문자열 */
    private fun kakaoAuthHeader(): String = "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}"

    override fun onResume() { super.onResume(); mapView.resume() }
    override fun onPause() { super.onPause(); mapView.pause() }
    override fun onDestroy() { mapView.finish(); super.onDestroy() }
}
