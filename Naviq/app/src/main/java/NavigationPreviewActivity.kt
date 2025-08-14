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
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.route.RouteLine
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class NavigationPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNavigationPreviewBinding
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null

    // 토글 색상
    private val COLOR_DEFAULT = Color.parseColor("#2D2D2D")
    private val COLOR_ORIGIN  = Color.parseColor("#306FD0")
    private val COLOR_DETOUR  = Color.parseColor("#FF4A5A")

    // 라인 핸들
    private var originLine: RouteLine? = null
    private var detourLine: RouteLine? = null

    // 경로 데이터
    private var routePoints: List<LatLng> = emptyList()
    private var baseRouteMeters: Double? = null
    private var detourRouteMeters: Double? = null

    // 내비 전달용 경유지
    private var viaForNav: LatLng? = null

    // 회랑 필터 후 재난
    private var corridorHazards: List<HazardCircle> = emptyList()

    // 배너 표기
    private var bannerReasons: List<String> = emptyList()

    companion object {
        private const val HOUR_WINDOW = 3
        private const val LOOKAHEAD_METERS = 30_000.0
        private const val CORRIDOR_MARGIN_M = 1_200.0
        private const val MIN_RADIUS_M = 120.0
        private const val MAX_RADIUS_M = 2_000.0
        private const val MAX_VIA_TO_ROUTE_M = 12_000.0
        private const val MAX_HAZARDS_USE = 5
        private const val CLUSTER_DIST_M = 2_000.0
        private const val CLUSTER_TIME_MS = 30 * 60 * 1000L
        private const val INTERSECT_MARGIN_M = 30.0
        private const val DETOUR_DISTANCE_RATIO_LIMIT = 1.20
    }

    // 간소화 모델
    data class SimpleDisasterItem(val createdAt: String?, val disasterType: String?, val regionName: String?, val msg: String?)
    data class HazardCircle(
        val center: LatLng,
        val radiusM: Double,
        val type: String,
        val region: String?,
        val msg: String?,
        val createdAtMs: Long,
        val score: Double
    )
    data class GeoHazard(val item: SimpleDisasterItem, val center: LatLng, val radiusM: Double)

    private enum class DetourStatus { NOT_TRIED, FOUND, NOT_FOUND }
    private var detourStatus: DetourStatus = DetourStatus.NOT_TRIED
    private var lastRejectedIncreasePct: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigationPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        mapView = binding.mapView
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(e: Exception?) { e?.printStackTrace() }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                setupRouteToggle()
                drawRouteLine(map) {
                    fetchAndApplyHazards(map)
                }
            }
        })

        binding.btnStartNavigation.setOnClickListener {
            val useDetour = binding.tgRoutes?.checkedButtonId == binding.btnDetour.id
            val navIntent = Intent(this, NavigationActivity::class.java).apply {
                putExtra("startLatitude", intent.getDoubleExtra("startLatitude", 0.0))
                putExtra("startLongitude", intent.getDoubleExtra("startLongitude", 0.0))
                putExtra("endLatitude", intent.getDoubleExtra("endLatitude", 0.0))
                putExtra("endLongitude", intent.getDoubleExtra("endLongitude", 0.0))
                putExtra("useDetour", useDetour)
                if (useDetour) {
                    viaForNav?.let {
                        putExtra("viaLatitude", it.latitude)
                        putExtra("viaLongitude", it.longitude)
                    }
                }
            }
            startActivity(navIntent)
        }
    }

    /** ───────── UI 토글 ───────── */
    private fun setupRouteToggle() {
        binding.tgRoutes?.check(binding.btnOrigin.id)
        updateToggleColors()
        binding.tgRoutes?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                binding.btnOrigin.id -> { originLine?.show(); detourLine?.hide(); updateReasonBanner() }
                binding.btnDetour.id -> {
                    if (detourLine != null) {
                        detourLine?.show(); originLine?.hide(); updateReasonBanner()
                    } else {
                        val map = kakaoMap ?: return@addOnButtonCheckedListener
                        if (corridorHazards.isNotEmpty()) {
                            drawDetourRouteLine(map, corridorHazards) {
                                detourLine?.show(); originLine?.hide(); updateReasonBanner()
                            }
                        } else {
                            originLine?.show(); detourLine?.hide(); updateReasonBanner()
                        }
                    }
                }
            }
            updateToggleColors()
        }
    }

    private fun updateToggleColors() {
        val checked = binding.tgRoutes?.checkedButtonId
        binding.btnOrigin.setTextColor(if (checked == binding.btnOrigin.id) COLOR_ORIGIN else COLOR_DEFAULT)
        binding.btnDetour.setTextColor(if (checked == binding.btnDetour.id) COLOR_DETOUR else COLOR_DEFAULT)
    }

    /** ───────── 본 경로(파랑) 그리기 ───────── */
    private fun drawRouteLine(map: KakaoMap, onReady: () -> Unit) {
        val startLat = intent.getDoubleExtra("startLatitude", 0.0)
        val startLng = intent.getDoubleExtra("startLongitude", 0.0)
        val endLat = intent.getDoubleExtra("endLatitude", 0.0)
        val endLng = intent.getDoubleExtra("endLongitude", 0.0)

        val origin = "$startLng,$startLat,name=출발지"
        val destination = "$endLng,$endLat,name=도착지"

        KakaoApiClient.retrofit.getDirections(kakaoAuthHeader(), origin, destination)
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    val jsonString = response.body()?.string() ?: run { Log.e("ROUTE","응답없음"); return }
                    try {
                        val points = parseRoutePoints(jsonString)
                        if (points.isEmpty()) { Log.e("ROUTE","경로포인트 없음"); return }
                        routePoints = points
                        baseRouteMeters = polylineLengthMeters(points)

                        val style = RouteLineStyle.from(12f, Color.parseColor("#1976D2"))
                        val seg = RouteLineSegment.from(points, style)
                        val layer = kakaoMap?.routeLineManager?.layer ?: return
                        originLine?.hide()
                        originLine = layer.addRouteLine(RouteLineOptions.from(seg)).also {
                            if (binding.tgRoutes?.checkedButtonId == binding.btnDetour.id) it.hide() else it.show()
                        }
                        kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(points.first()))
                        updateReasonBanner()
                        onReady()
                    } catch (e: Exception) {
                        Log.e("ROUTE","파싱 오류: ${e.message}", e)
                    }
                }
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Log.e("ROUTE","API 실패: ${t.message}")
                }
            })
    }

    /** ───────── 재난 로드 → 회랑 필터/우회 ───────── */
    private fun fetchAndApplyHazards(map: KakaoMap) {
        val tz = TimeZone.getTimeZone("Asia/Seoul")
        val ymdFmt = SimpleDateFormat("yyyyMMdd", Locale.KOREA).apply { timeZone = tz }
        val todayYmd = ymdFmt.format(Date())

        SafetyApiClient.service.getDisasterMessages(
            serviceKey = BuildConfig.SAFETY_DATA_KEY,
            returnType = "json",
            pageNo = 1,
            rows = 200,
            crtDt = todayYmd,
            regionName = null
        ).enqueue(object : Callback<SafetyResponse> {
            override fun onResponse(call: Call<SafetyResponse>, response: Response<SafetyResponse>) {
                val rawList: List<Any> = response.body()?.body?.map { it as Any } ?: emptyList()
                if (rawList.isEmpty()) { bannerReasons = emptyList(); detourStatus = DetourStatus.NOT_TRIED; lastRejectedIncreasePct = null; updateReasonBanner(); return }

                lifecycleScope.launch {
                    val simplified: List<SimpleDisasterItem> = rawList.mapNotNull { asSimpleDisasterItem(it) }
                    if (simplified.isEmpty()) { bannerReasons = emptyList(); detourStatus = DetourStatus.NOT_TRIED; lastRejectedIncreasePct = null; updateReasonBanner(); return@launch }

                    val recent = filterByTimeWindow(simplified, HOUR_WINDOW)
                    val geoPairs = geocodeItems(recent)

                    val withRadius = geoPairs.mapNotNull { (item, center) ->
                        center?.let {
                            val r = HazardRadius.fromType(item.disasterType ?: "", item.msg ?: "").coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
                            GeoHazard(item, it, r)
                        }
                    }
                    if (withRadius.isEmpty()) { bannerReasons = emptyList(); detourStatus = DetourStatus.NOT_TRIED; lastRejectedIncreasePct = null; updateReasonBanner(); return@launch }

                    val deduped = dedupCluster(withRadius, CLUSTER_DIST_M, CLUSTER_TIME_MS)

                    val corridor = takePathByDistance(routePoints, LOOKAHEAD_METERS)
                    val scored = scoreAndFilterToCorridor(deduped, corridor, CORRIDOR_MARGIN_M)

                    val top = scored.sortedByDescending { it.score }.take(MAX_HAZARDS_USE)
                    corridorHazards = top
                    bannerReasons = top.map { prettyType(it.type) + (it.region?.let { rg -> "·$rg" } ?: "") }

                    if (top.isNotEmpty()) {
                        drawDetourRouteLine(map, top) {
                            if (binding.tgRoutes?.checkedButtonId == binding.btnDetour.id) { detourLine?.show(); originLine?.hide() }
                            updateReasonBanner()
                        }
                    } else {
                        detourLine?.hide(); viaForNav = null; detourRouteMeters = null
                        detourStatus = DetourStatus.NOT_TRIED; lastRejectedIncreasePct = null
                        updateReasonBanner()
                    }
                }
            }
            override fun onFailure(call: Call<SafetyResponse>, t: Throwable) {
                t.printStackTrace(); updateReasonBanner()
            }
        })
    }

    /** ───────── 우회 경로 그리기(핵심 로직) ───────── */
    private fun drawDetourRouteLine(map: KakaoMap, hazards: List<HazardCircle>, afterDraw: () -> Unit = {}) {
        if (routePoints.isEmpty() || hazards.isEmpty()) { detourLine?.hide(); viaForNav = null; detourRouteMeters = null; afterDraw(); return }

        detourStatus = DetourStatus.NOT_TRIED
        lastRejectedIncreasePct = null

        val start = LatLng.from(intent.getDoubleExtra("startLatitude", 0.0), intent.getDoubleExtra("startLongitude", 0.0))
        val end   = LatLng.from(intent.getDoubleExtra("endLatitude", 0.0),   intent.getDoubleExtra("endLongitude", 0.0))

        val anchor = hazards.maxByOrNull { it.score }!!
        val cluster = clusterOverlaps(hazards, anchor, mergeMarginM = 80.0)
        val useAngular = cluster.size >= 3 || anisotropyRatio(cluster) >= 1.30
        val buffers = arrayOf(70.0, 110.0, 160.0)

        fun requestTwoLegs(a: LatLng, v: LatLng, b: LatLng, onDone: (List<LatLng>?) -> Unit) {
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
                                    if (all.isNotEmpty() && p2.isNotEmpty() && all.last() == p2.first()) all.addAll(p2.drop(1)) else all.addAll(p2)
                                    val safe = !routeIntersectsAnyCircle(all, hazards, INTERSECT_MARGIN_M)
                                    if (!safe) { onDone(null); return }
                                    val mergedLen = polylineLengthMeters(all)
                                    val baseLen = baseRouteMeters ?: mergedLen
                                    val distanceOk = mergedLen <= baseLen * DETOUR_DISTANCE_RATIO_LIMIT
                                    if (!distanceOk) {
                                        lastRejectedIncreasePct = (((mergedLen - baseLen) / baseLen) * 100.0).roundToInt()
                                        onDone(null)
                                    } else onDone(all)
                                }
                                override fun onFailure(call: Call<ResponseBody>, t: Throwable) { onDone(null) }
                            })
                    }
                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) { onDone(null) }
                })
        }

        fun trySimpleUnion(buffIdx: Int = 0) {
            if (buffIdx >= buffers.size) {
                detourLine?.hide(); viaForNav = null; detourRouteMeters = null
                detourStatus = DetourStatus.NOT_FOUND
                updateReasonBanner(); afterDraw(); return
            }
            val unionRadius = cluster.maxOf { h -> distanceMeters(anchor.center, h.center) + h.radiusM }.coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
            val baseRad = atan2(end.latitude - start.latitude, end.longitude - start.longitude)
            val baseDeg = Math.toDegrees(baseRad)
            val offsets = listOf(+90.0, -90.0, +60.0, -60.0, +120.0, -120.0, +150.0, -150.0)
            val r = (unionRadius + buffers[buffIdx]).coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
            val cands = offsets.map { off -> ringPoint(anchor.center, r, baseDeg + off) }
                .filter { v -> minDistanceToPolylineMeters(v, routePoints) <= MAX_VIA_TO_ROUTE_M }

            fun tryIdx(i: Int = 0) {
                if (i >= cands.size) { trySimpleUnion(buffIdx + 1); return }
                val via = cands[i]
                requestTwoLegs(start, via, end) { merged ->
                    if (merged != null) { applyDetourPolyline(merged, via); afterDraw() } else tryIdx(i + 1)
                }
            }
            tryIdx(0)
        }

        fun tryAngularProfile(buffIdx: Int = 0) {
            if (buffIdx >= buffers.size) { trySimpleUnion(0); return }
            val compCenter = weightedCentroid(cluster)
            val profile = angularUnionProfile(compCenter, cluster, numAngles = 48, extraBufferM = 60.0)
            val baseRad = atan2(end.latitude - start.latitude, end.longitude - start.longitude)
            val baseDeg = Math.toDegrees(baseRad)
            val offsets = listOf(+90.0, -90.0, +60.0, -60.0, +120.0, -120.0, +150.0, -150.0)
            val cands = offsets.map { off ->
                val bearing = baseDeg + off
                val r = (radiusAt(profile, bearing) + buffers[buffIdx]).coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
                ringPoint(profile.center, r, bearing)
            }.filter { v -> minDistanceToPolylineMeters(v, routePoints) <= MAX_VIA_TO_ROUTE_M }

            fun tryIdx(i: Int = 0) {
                if (i >= cands.size) { tryAngularProfile(buffIdx + 1); return }
                val via = cands[i]
                requestTwoLegs(start, via, end) { merged ->
                    if (merged != null) { applyDetourPolyline(merged, via); afterDraw() } else tryIdx(i + 1)
                }
            }
            tryIdx(0)
        }

        if (useAngular) tryAngularProfile(0) else trySimpleUnion(0)
    }

    private fun applyDetourPolyline(merged: List<LatLng>, via: LatLng) {
        detourStatus = DetourStatus.FOUND
        lastRejectedIncreasePct = null
        detourRouteMeters = polylineLengthMeters(merged)
        viaForNav = via

        val style = RouteLineStyle.from(12f, Color.parseColor("#FF9800"))
        val seg = RouteLineSegment.from(merged, style)
        val layer = kakaoMap?.routeLineManager?.layer ?: return
        detourLine?.hide()
        detourLine = layer.addRouteLine(RouteLineOptions.from(seg)).also {
            if (binding.tgRoutes?.checkedButtonId == binding.btnDetour.id) it.show() else it.hide()
        }
        updateReasonBanner()
    }

    /** ───────── Kakao Directions JSON → LatLng ───────── */
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
                            if (lat != lastLat || lng != lastLng) {
                                pts.add(LatLng.from(lat, lng))
                                lastLat = lat; lastLng = lng
                            }
                        }
                        j += 2
                    }
                }
            }
            pts
        } catch (e: Exception) {
            Log.e("ROUTE", "파싱 오류: ${e.message}", e)
            emptyList()
        }
    }

    /** ───────── 재난 처리 유틸 ───────── */
    private fun asSimpleDisasterItem(any: Any): SimpleDisasterItem? {
        fun readProp(o: Any, name: String): String? {
            runCatching { o.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(o) as? String }
                .getOrNull()?.trim()?.let { return it }
            val getterName = "get" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            runCatching { o.javaClass.methods.firstOrNull { it.name == getterName && it.parameterCount == 0 }?.invoke(o) as? String }
                .getOrNull()?.trim()?.let { return it }
            return null
        }
        val createdAt = readProp(any, "createdAt") ?: readProp(any, "crtDt")
        val disasterType = readProp(any, "disasterType") ?: readProp(any, "type")
        val regionName = readProp(any, "regionName") ?: readProp(any, "location")
        val msg = readProp(any, "msg") ?: readProp(any, "message")
        if (createdAt == null && disasterType == null && regionName == null && msg == null) return null
        return SimpleDisasterItem(createdAt, disasterType, regionName, msg)
    }

    private fun filterByTimeWindow(items: List<SimpleDisasterItem>, hours: Int): List<SimpleDisasterItem> {
        val tz = TimeZone.getTimeZone("Asia/Seoul")
        val kstFmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.KOREA).apply { timeZone = tz }
        val now = System.currentTimeMillis()
        val windowMs = hours * 60L * 60L * 1000L
        val pairs = items.map { it to parseTime(kstFmt, it.createdAt ?: "") }
        return pairs.filter { (_, ts) -> ts != null && now - ts!! in 0..windowMs }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private suspend fun geocodeItems(items: List<SimpleDisasterItem>): List<Pair<SimpleDisasterItem, LatLng?>> =
        withContext(Dispatchers.IO) {
            items.map { item ->
                async {
                    val rawPlace = DisasterTextParser.fromMessage(item.msg)
                    val place = when {
                        rawPlace.isNullOrBlank() -> DisasterTextParser.firstRegion(item.regionName)
                        Regex("(위험|대피|통제|주의|경보|주의보|호우|태풍)").containsMatchIn(rawPlace) ->
                            DisasterTextParser.firstRegion(item.regionName)
                        else -> rawPlace
                    }
                    val center = Geocoding.toLatLng(
                        place = place,
                        regionFallback = item.regionName,
                        kakaoRestKey = BuildConfig.KAKAO_REST_API_KEY
                    )
                    item to center
                }
            }.awaitAll()
        }

    private fun dedupCluster(list: List<GeoHazard>, distM: Double, timeWindowMs: Long): List<GeoHazard> {
        val tz = TimeZone.getTimeZone("Asia/Seoul")
        val kstFmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.KOREA).apply { timeZone = tz }
        val sorted = list.sortedByDescending { parseTime(kstFmt, it.item.createdAt ?: "") ?: 0L }
        val groups = mutableListOf<GeoHazard>()
        for (gh in sorted) {
            val t = parseTime(kstFmt, gh.item.createdAt ?: "") ?: continue
            val dup = groups.any { g ->
                val gt = parseTime(kstFmt, g.item.createdAt ?: "") ?: 0L
                (abs(gt - t) <= timeWindowMs) &&
                        (gh.item.disasterType == g.item.disasterType) &&
                        (distanceMeters(gh.center, g.center) <= distM)
            }
            if (!dup) groups.add(gh)
        }
        return groups
    }

    private fun scoreAndFilterToCorridor(list: List<GeoHazard>, corridor: List<LatLng>, marginM: Double): List<HazardCircle> {
        val tz = TimeZone.getTimeZone("Asia/Seoul")
        val kstFmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.KOREA).apply { timeZone = tz }
        val now = System.currentTimeMillis()
        val out = ArrayList<HazardCircle>()
        for (gh in list) {
            val createdMs = parseTime(kstFmt, gh.item.createdAt ?: "") ?: continue
            val near = minDistanceToPolylineMeters(gh.center, corridor)
            val inCorridor = near <= (gh.radiusM + marginM)
            if (!inCorridor) continue
            val ageMs = now - createdMs
            val recency = 1.0 - min(1.0, ageMs / (HOUR_WINDOW * 60.0 * 60.0 * 1000.0))
            val severity = when {
                isStrongType(gh.item.disasterType) -> 1.0
                isMediumType(gh.item.disasterType, gh.item.msg) -> 0.6
                else -> 0.3
            }
            val proximity = exp(-near / 1500.0)
            val score = 0.45 * recency + 0.35 * severity + 0.20 * proximity
            out.add(HazardCircle(gh.center, gh.radiusM, gh.item.disasterType ?: "", gh.item.regionName, gh.item.msg, createdMs, score))
        }
        return out
    }

    private fun isStrongType(t: String?) = (t ?: "").contains("통제") || (t ?: "").contains("대피") || (t ?: "").contains("유해") || (t ?: "").contains("화재") || (t ?: "").contains("지진")
    private fun isMediumType(t: String?, msg: String?) = ((t ?: "") + " " + (msg ?: "")).let {
        it.contains("침수") || it.contains("호우") || it.contains("태풍") || it.contains("폭우") || it.contains("사고") || it.contains("공사")
    }
    private fun prettyType(t: String?) = (t ?: "").trim().ifBlank { "재난" }

    // 클러스터/프로파일
    private fun clusterOverlaps(hazards: List<HazardCircle>, anchor: HazardCircle, mergeMarginM: Double): List<HazardCircle> {
        val cluster = ArrayList<HazardCircle>()
        for (h in hazards) {
            val d = distanceMeters(anchor.center, h.center)
            if (d <= anchor.radiusM + h.radiusM + mergeMarginM) cluster.add(h)
        }
        if (!cluster.contains(anchor)) cluster.add(anchor)
        return cluster
    }
    private fun anisotropyRatio(hs: List<HazardCircle>): Double {
        if (hs.isEmpty()) return 1.0
        val lat0 = hs.first().center.latitude
        val lon0 = hs.first().center.longitude
        val mPerLat = 111320.0
        val mPerLng = 88000.0 * cos(Math.toRadians(lat0))
        val xs = hs.map { (it.center.longitude - lon0) * mPerLng }
        val ys = hs.map { (it.center.latitude - lat0) * mPerLat }
        val rAvg = hs.map { it.radiusM }.average()
        val dx = (xs.maxOrNull()!! - xs.minOrNull()!!) + rAvg * 2
        val dy = (ys.maxOrNull()!! - ys.minOrNull()!!) + rAvg * 2
        return max(dx, dy) / max(1.0, min(dx, dy))
    }
    private fun weightedCentroid(hs: List<HazardCircle>): LatLng {
        if (hs.isEmpty()) return LatLng.from(0.0, 0.0)
        var sx = 0.0; var sy = 0.0; var wsum = 0.0
        for (h in hs) {
            val w = 0.5 + h.score
            sx += h.center.longitude * w
            sy += h.center.latitude * w
            wsum += w
        }
        return LatLng.from(sy / wsum, sx / wsum)
    }
    data class AngularProfile(val center: LatLng, val radii: DoubleArray)
    private fun angularUnionProfile(center: LatLng, hazards: List<HazardCircle>, numAngles: Int = 48, extraBufferM: Double = 60.0): AngularProfile {
        val bins = DoubleArray(max(12, numAngles)) { 0.0 }
        val dTheta = 360.0 / bins.size
        val lat0 = center.latitude
        val lon0 = center.longitude
        val mPerLat = 111320.0
        val mPerLng = 88000.0 * cos(Math.toRadians(lat0))
        fun toXY(p: LatLng) = Pair((p.longitude - lon0) * mPerLng, (p.latitude - lat0) * mPerLat)
        fun angleToIdx(deg: Double): Int {
            var a = deg % 360.0; if (a < 0) a += 360.0
            val idx = floor(a / dTheta).toInt()
            return idx.coerceIn(0, bins.lastIndex)
        }
        val zero = 1e-6
        for (h in hazards) {
            val (cx, cy) = toXY(h.center)
            val r = h.radiusM + extraBufferM
            val cNorm2 = cx * cx + cy * cy
            val cNorm = sqrt(cNorm2)
            val coverAngles = if (cNorm <= r + zero) 0 until bins.size else {
                val baseDeg = Math.toDegrees(atan2(cy, cx))
                val alpha = Math.toDegrees(asin(min(1.0, r / cNorm)))
                val start = angleToIdx(baseDeg - alpha - dTheta)
                val end   = angleToIdx(baseDeg + alpha + dTheta)
                if (start <= end) start..end else (start..bins.lastIndex) + (0..end)
            }
            for (idx in coverAngles) {
                val theta = (idx + 0.5) * dTheta
                val rad = Math.toRadians(theta)
                val ux = cos(rad); val uy = sin(rad)
                val proj = cx * ux + cy * uy
                val perp2 = cNorm2 - proj * proj
                if (perp2 <= r * r + zero) {
                    val delta = sqrt(max(0.0, r * r - perp2))
                    val hit = proj + delta
                    if (hit > 0) bins[idx] = max(bins[idx], hit)
                }
            }
        }
        for (i in bins.indices) bins[i] = bins[i].coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
        return AngularProfile(center, bins)
    }
    private fun radiusAt(profile: AngularProfile, bearingDeg: Double): Double {
        val bins = profile.radii; val n = bins.size; val dTheta = 360.0 / n
        var a = bearingDeg % 360.0; if (a < 0) a += 360.0
        val idx = floor(a / dTheta).toInt()
        val t = (a - idx * dTheta) / dTheta
        val i0 = idx; val i1 = (idx + 1) % n
        return bins[i0] * (1 - t) + bins[i1] * t
    }

    /** ───────── 기하 유틸 ───────── */
    private fun takePathByDistance(path: List<LatLng>, limitM: Double): List<LatLng> {
        if (path.size < 2) return path
        var acc = 0.0
        val out = ArrayList<LatLng>()
        out.add(path.first())
        for (i in 0 until path.size - 1) {
            val d = distanceMeters(path[i], path[i + 1])
            if (acc + d >= limitM) {
                val remain = limitM - acc
                val ratio = (remain / d).coerceIn(0.0, 1.0)
                val lat = path[i].latitude + (path[i + 1].latitude - path[i].latitude) * ratio
                val lng = path[i].longitude + (path[i + 1].longitude - path[i].longitude) * ratio
                out.add(LatLng.from(lat, lng))
                break
            } else {
                out.add(path[i + 1]); acc += d
            }
        }
        return out
    }
    private fun routeIntersectsAnyCircle(path: List<LatLng>, hazards: List<HazardCircle>, margin: Double) =
        hazards.any { h -> routeIntersectsCircle(path, h.center, h.radiusM + margin) }
    private fun routeIntersectsCircle(path: List<LatLng>, center: LatLng, radiusMeters: Double): Boolean {
        val mPerLat = 111320.0
        val mPerLng = 88000.0 * cos(Math.toRadians(center.latitude))
        fun toXY(p: LatLng) = Pair((p.longitude - center.longitude) * mPerLng, (p.latitude - center.latitude) * mPerLat)
        val r2 = radiusMeters * radiusMeters
        for (i in 0 until path.size - 1) {
            val (x1, y1) = toXY(path[i]); val (x2, y2) = toXY(path[i + 1])
            val dx = x2 - x1; val dy = y2 - y1
            val denom = dx * dx + dy * dy
            val t = if (denom == 0.0) 0.0 else (-(x1 * dx + y1 * dy)) / denom
            val tt = t.coerceIn(0.0, 1.0)
            val cx = x1 + tt * dx; val cy = y1 + tt * dy
            if (cx * cx + cy * cy <= r2) return true
        }
        return false
    }
    private fun distancePointToSegmentMeters(p: LatLng, a: LatLng, b: LatLng): Double {
        val ab = distanceMeters(a, b); if (ab < 1e-3) return distanceMeters(p, a)
        val mPerLat = 111320.0
        val mPerLng = 88000.0 * cos(Math.toRadians((a.latitude + b.latitude) / 2))
        fun toXY(q: LatLng) = Pair((q.longitude - a.longitude) * mPerLng, (q.latitude - a.latitude) * mPerLat)
        val (px, py) = toXY(p); val (bx, by) = toXY(b)
        val denom = bx * bx + by * by
        val t = if (denom == 0.0) 0.0 else ((px * bx + py * by) / denom).coerceIn(0.0, 1.0)
        val cx = t * bx; val cy = t * by
        return hypot(px - cx, py - cy)
    }
    private fun minDistanceToPolylineMeters(p: LatLng, path: List<LatLng>): Double {
        if (path.size < 2) return Double.POSITIVE_INFINITY
        var best = Double.POSITIVE_INFINITY
        for (i in 0 until path.size - 1) {
            val d = distancePointToSegmentMeters(p, path[i], path[i + 1])
            if (d < best) best = d
        }
        return best
    }
    private fun distanceMeters(a: LatLng, b: LatLng): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val s1 = sin(dLat / 2); val s2 = sin(dLng / 2)
        val aa = s1 * s1 + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * s2 * s2
        return 2 * R * asin(sqrt(aa))
    }
    private fun ringPoint(center: LatLng, radiusMeters: Double, bearingDeg: Double): LatLng {
        val R = 6371000.0
        val brng = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(center.latitude)
        val lon1 = Math.toRadians(center.longitude)
        val dR = radiusMeters / R
        val sinLat1 = sin(lat1); val cosLat1 = cos(lat1)
        val sinLat2 = sinLat1 * cos(dR) + cosLat1 * sin(dR) * cos(brng)
        val lat2 = asin(sinLat2)
        val y = sin(brng) * sin(dR) * cosLat1
        val x = cos(dR) - sinLat1 * sinLat2
        val lon2 = lon1 + atan2(y, x)
        return LatLng.from(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }
    private fun polylineLengthMeters(points: List<LatLng>): Double {
        var sum = 0.0
        for (i in 0 until points.size - 1) sum += distanceMeters(points[i], points[i + 1])
        return sum
    }
    private fun parseTime(fmt: SimpleDateFormat, s: String): Long? =
        try { fmt.parse(s)?.time } catch (_: ParseException) { null }

    /** ───────── 배너 ───────── */
    private fun kakaoAuthHeader(): String = "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}"

    private fun navLikeNoDetourMessage(): String {
        val cause = bannerReasons.take(2).joinToString("/")
        val inc = lastRejectedIncreasePct
        return when {
            detourStatus == DetourStatus.NOT_FOUND && inc != null ->
                "우회 경로 길이 과다(+$inc%) · 기본 경로 유지"
            detourStatus == DetourStatus.NOT_FOUND && cause.isNotBlank() ->
                "우회 불가($cause) · 기본 경로 유지"
            detourStatus == DetourStatus.NOT_FOUND ->
                "우회 불가 · 기본 경로 유지"
            corridorHazards.isEmpty() ->
                "재난 없음 · 기본 경로 안내"
            else -> "우회 미적용 · 기본 경로 진행"
        }
    }

    private fun updateReasonBanner() {
        val tv = binding.tvRouteDelta
        val isDetourSelected = binding.tgRoutes?.checkedButtonId == binding.btnDetour.id
        val base = baseRouteMeters
        val detour = detourRouteMeters

        val reason = buildString {
            if (isDetourSelected && detour != null) {
                append("우회 적용")
                if (bannerReasons.isNotEmpty()) {
                    append(" · 이유: ")
                    append(bannerReasons.take(2).joinToString(" / "))
                    if (bannerReasons.size > 2) append(" 외 ${bannerReasons.size - 2}건")
                }
                if (base != null) {
                    val delta = detour - base
                    append(" · 거리 ")
                    append(formatMetersPretty(detour))
                    append(if (delta >= 0) " (+" else " (-")
                    append(formatMetersPretty(kotlin.math.abs(delta)))
                    append(")")
                }
            } else {
                append(navLikeNoDetourMessage())
                base?.let { append(" · 거리 ").append(formatMetersPretty(it)) }
            }
        }
        tv.text = reason
    }
    private fun formatMetersPretty(m: Double) =
        if (m >= 1000.0) String.format(Locale.KOREA, "%.1fkm", m / 1000.0) else "${m.toInt()}m"

    override fun onResume() { super.onResume(); mapView.resume() }
    override fun onPause()  { super.onPause();  mapView.pause() }
    override fun onDestroy(){ mapView.finish(); super.onDestroy() }
}
