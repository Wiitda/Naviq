package com.example.nav

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.example.nav.databinding.ActivityNavigationBinding
import com.example.nav.viewmodel.NavigationViewModel
import com.kakaomobility.knsdk.KNCarType
import com.kakaomobility.knsdk.KNRouteAvoidOption
import com.kakaomobility.knsdk.KNRoutePriority
import com.kakaomobility.knsdk.KNSDK
import com.kakaomobility.knsdk.common.objects.KNError
import com.kakaomobility.knsdk.common.objects.KNPOI
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_CitsGuideDelegate
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_GuideStateDelegate
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_LocationGuideDelegate
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_RouteGuideDelegate
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_SafetyGuideDelegate
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_VoiceGuideDelegate
import com.kakaomobility.knsdk.guidance.knguidance.KNGuideRouteChangeReason
import com.kakaomobility.knsdk.guidance.knguidance.citsguide.KNGuide_Cits
import com.kakaomobility.knsdk.guidance.knguidance.common.KNLocation
import com.kakaomobility.knsdk.guidance.knguidance.locationguide.KNGuide_Location
import com.kakaomobility.knsdk.guidance.knguidance.routeguide.KNGuide_Route
import com.kakaomobility.knsdk.guidance.knguidance.routeguide.objects.KNMultiRouteInfo
import com.kakaomobility.knsdk.guidance.knguidance.safetyguide.KNGuide_Safety
import com.kakaomobility.knsdk.guidance.knguidance.safetyguide.objects.KNSafety
import com.kakaomobility.knsdk.guidance.knguidance.voiceguide.KNGuide_Voice
import com.kakaomobility.knsdk.trip.kntrip.knroute.KNRoute
import com.kakaomobility.knsdk.ui.component.MapViewCameraMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class NavigationActivity :
    AppCompatActivity(),
    KNGuidance_GuideStateDelegate,
    KNGuidance_LocationGuideDelegate,
    KNGuidance_SafetyGuideDelegate,
    KNGuidance_RouteGuideDelegate,
    KNGuidance_VoiceGuideDelegate,
    KNGuidance_CitsGuideDelegate {

    private lateinit var binding: ActivityNavigationBinding
    private val viewModel: NavigationViewModel by viewModels()
    private val TAG = "NAVI_ROTATION"

    private var startLatitude = 0.0
    private var startLongitude = 0.0
    private var endLatitude = 0.0
    private var endLongitude = 0.0

    // 미리보기에서 전달된 우회(경유지) 정보
    private var useDetour: Boolean = false
    private var viaLatitude: Double? = null
    private var viaLongitude: Double? = null

    private var rgCode = ""

    // ───────────────────────────────── onCreate ─────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_navigation)

        // 인텐트 읽기
        readIntent(intent)

        Log.d(
            TAG,
            "받은 좌표 start=($startLatitude,$startLongitude) end=($endLatitude,$endLongitude) " +
                    "detour=$useDetour via=(${viaLatitude ?: "-"},${viaLongitude ?: "-"})"
        )

        // 지도/차량 설정
        settingMap()

        // 스트림 구독은 한 번만
        observeFlow()

        // 좌표 변환부터 시작
        requestCoordinateConvert()
    }

    // ─────────────────────────────── onNewIntent (핵심) ───────────────────────────────
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)                 // 현재 액티비티의 인텐트를 새 것으로 교체

        // 1) 기존 가이던스 연결/뷰 렌더링 먼저 끊기 (중요)
        runCatching {
            KNSDK.sharedGuidance()?.let { g ->
                // 내비 뷰에 연결된 가이던스를 '종료됨' 상태로 처리해 UI/상태를 리셋
                binding.naviView.guidanceGuideEnded(g, /* showDialog = */ false)
            }
        }.onFailure { e -> Log.w(TAG, "guidance detach 실패(무시 가능): $e") }

        // 2) 새 파라미터 반영
        readIntent(intent)
        Log.i(TAG, "onNewIntent: detour=$useDetour via=($viaLatitude,$viaLongitude)")

        // 3) 좌표 변환 재요청 → 새로운 경로로 갱신
        requestCoordinateConvert()
    }

    // ─────────────────────────────── 인텐트 파싱/요청 ───────────────────────────────
    private fun readIntent(src: Intent) {
        startLatitude  = src.getDoubleExtra("startLatitude", 0.0)
        startLongitude = src.getDoubleExtra("startLongitude", 0.0)
        endLatitude    = src.getDoubleExtra("endLatitude", 0.0)
        endLongitude   = src.getDoubleExtra("endLongitude", 0.0)

        useDetour = src.getBooleanExtra("useDetour", false)
        viaLatitude = if (src.hasExtra("viaLatitude")) src.getDoubleExtra("viaLatitude", 0.0) else null
        viaLongitude = if (src.hasExtra("viaLongitude")) src.getDoubleExtra("viaLongitude", 0.0) else null
    }

    private fun requestCoordinateConvert() {
        if (useDetour && viaLatitude != null && viaLongitude != null) {
            viewModel.getCoordConvertDataWithVia(
                startLatitude, startLongitude,
                endLatitude, endLongitude,
                viaLatitude!!, viaLongitude!!
            )
        } else {
            viewModel.getCoordConvertData(startLatitude, startLongitude, endLatitude, endLongitude)
        }
    }

    // ─────────────────────────────── 스트림 구독 ───────────────────────────────
    private fun observeFlow() {
        lifecycleScope.launch {
            viewModel.coordZipResult.collectLatest {
                Log.d(TAG, "좌표 변환 결과: $it")
                val result = it.success ?: run {
                    Log.e(TAG, "좌표 변환 실패: ${it.failure?.message ?: "unknown"}")
                    return@collectLatest
                }

                // Double 문자열 → Int (반올림)
                val sx = result.startLongitude?.toDoubleOrNull()?.roundToInt()
                val sy = result.startLatitude?.toDoubleOrNull()?.roundToInt()
                val ex = result.endLongitude?.toDoubleOrNull()?.roundToInt()
                val ey = result.endLatitude?.toDoubleOrNull()?.roundToInt()

                if (sx == null || sy == null || ex == null || ey == null) {
                    Log.e(TAG, "정수 좌표 변환 실패(sx=$sx, sy=$sy, ex=$ex, ey=$ey)")
                    return@collectLatest
                }

                val start = KNPOI("", sx, sy, null)
                val end = KNPOI("", ex, ey, null)

                // 경유지 구성
                val vx = result.viaLongitude?.toDoubleOrNull()?.roundToInt()
                val vy = result.viaLatitude?.toDoubleOrNull()?.roundToInt()
                val viaList: MutableList<KNPOI>? = if (useDetour) {
                    if (vx != null && vy != null) mutableListOf(KNPOI("경유지", vx, vy, null)) else null
                } else null

                // 우회 요청했는데 변환 실패 시 사용자 피드백
                if (useDetour && (vx == null || vy == null)) {
                    Log.w(
                        TAG,
                        "우회 요청됐지만 경유지 좌표 변환 실패 → 기본 경로로 진행 (viaRaw=${result.viaLatitude},${result.viaLongitude})"
                    )
                    Toast.makeText(this@NavigationActivity, "우회 지점 변환 실패 · 기본 경로로 진행", Toast.LENGTH_SHORT).show()
                }

                val curRoutePriority = KNRoutePriority.KNRoutePriority_Recommand
                val curAvoidOptions = KNRouteAvoidOption.KNRouteAvoidOption_None.value

                // 실제 서버에 던지는 경유지 개수/상태 로그
                Log.i(
                    TAG,
                    "Trip build: vias=${viaList?.size ?: 0} useDetour=$useDetour viaIntent=$viaLatitude,$viaLongitude"
                )

                // (선택) 캐시 무력화를 위한 세션 태그
                val extras = android.os.Bundle().apply {
                    putLong("routeSessionTag", SystemClock.elapsedRealtimeNanos())
                }

                KNSDK.makeTripWithStart(start, end, /* vias */ viaList, /* extras */ null) { knError, knTrip ->
                    if (knError != null) {
                        Log.e(TAG, "경로(Trip) 생성 에러: $knError")
                        return@makeTripWithStart
                    }
                    if (knTrip == null) {
                        Log.e(TAG, "경로(Trip) 생성 실패: Trip=null")
                        return@makeTripWithStart
                    }

                    // 내비 뷰를 재초기화하기 전에 안전하게 카메라/차종 등을 재설정(시각적 리셋)
                    settingMap()

                    knTrip.routeWithPriority(curRoutePriority, curAvoidOptions) { error, _ ->
                        if (error != null) {
                            Log.e(TAG, "경로 요청 실패: $error")
                        } else {
                            KNSDK.sharedGuidance()?.apply {
                                guideStateDelegate = this@NavigationActivity
                                locationGuideDelegate = this@NavigationActivity
                                routeGuideDelegate = this@NavigationActivity
                                safetyGuideDelegate = this@NavigationActivity
                                voiceGuideDelegate = this@NavigationActivity
                                citsGuideDelegate = this@NavigationActivity

                                // 여기서 새 Trip으로 연결
                                binding.naviView.initWithGuidance(this, knTrip, curRoutePriority, curAvoidOptions)
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.distanceData.collectLatest {
                Log.d(TAG, "SK 직선 거리 : ${it.success?.distance}")
            }
        }
    }

    /** 지도 설정 */
    private fun settingMap() {
        binding.naviView.mapViewMode = MapViewCameraMode.Top   // 2D 모드
        binding.naviView.carType = KNCarType.KNCarType_1       // 자동차 모드
    }

    // ─────────────────────────────── Delegates ───────────────────────────────
    override fun guidanceDidUpdateRouteGuide(aGuidance: KNGuidance, aRouteGuide: KNGuide_Route) {
        binding.naviView.guidanceDidUpdateRouteGuide(aGuidance, aRouteGuide)

        if (aRouteGuide.curDirection?.location?.pos != null &&
            aRouteGuide.nextDirection?.location?.pos != null) {
            rgCode = aRouteGuide.nextDirection?.rgCode.toString()
            when (rgCode) {
                "KNRGCode_Straight" -> rgCode = "직진"
                "KNRGCode_LeftTurn" -> rgCode = "좌회전"
                "KNRGCode_RightTurn" -> rgCode = "우회전"
                "KNRGCode_UTurn" -> rgCode = "유턴"
            }
            viewModel.getDistanceData(
                aRouteGuide.curDirection!!.location!!.pos!!.toFloatPoint(),
                aRouteGuide.nextDirection!!.location!!.pos!!.toFloatPoint()
            )
        }
    }

    override fun guidanceGuideEnded(aGuidance: KNGuidance) {
        Log.d(TAG, "guidanceGuideEnded 내비게이션 종료")
        binding.naviView.guidanceGuideEnded(aGuidance, false) // 종료 팝업 노출x
        finish()
    }

    override fun guidanceCheckingRouteChange(aGuidance: KNGuidance) {
        binding.naviView.guidanceCheckingRouteChange(aGuidance)
    }

    override fun guidanceDidUpdateRoutes(
        aGuidance: KNGuidance,
        aRoutes: List<KNRoute>,
        aMultiRouteInfo: KNMultiRouteInfo?
    ) {
        binding.naviView.guidanceDidUpdateRoutes(aGuidance, aRoutes, aMultiRouteInfo)
    }

    override fun guidanceGuideStarted(aGuidance: KNGuidance) {
        binding.naviView.guidanceGuideStarted(aGuidance)
    }

    override fun guidanceOutOfRoute(aGuidance: KNGuidance) {
        binding.naviView.guidanceOutOfRoute(aGuidance)
    }

    override fun guidanceRouteChanged(
        aGuidance: KNGuidance,
        aFromRoute: KNRoute,
        aFromLocation: KNLocation,
        aToRoute: KNRoute,
        aToLocation: KNLocation,
        aChangeReason: KNGuideRouteChangeReason
    ) {
        binding.naviView.guidanceRouteChanged(aGuidance)
    }

    override fun guidanceRouteUnchanged(aGuidance: KNGuidance) {
        binding.naviView.guidanceRouteUnchanged(aGuidance)
    }

    override fun guidanceRouteUnchangedWithError(aGuidnace: KNGuidance, aError: KNError) {
        binding.naviView.guidanceRouteUnchangedWithError(aGuidnace, aError)
    }

    override fun guidanceDidUpdateLocation(aGuidance: KNGuidance, aLocationGuide: KNGuide_Location) {
        binding.naviView.guidanceDidUpdateLocation(aGuidance, aLocationGuide)
    }

    override fun guidanceDidUpdateAroundSafeties(aGuidance: KNGuidance, aSafeties: List<KNSafety>?) {
        binding.naviView.guidanceDidUpdateAroundSafeties(aGuidance, aSafeties)
    }

    override fun guidanceDidUpdateSafetyGuide(aGuidance: KNGuidance, aSafetyGuide: KNGuide_Safety?) {
        binding.naviView.guidanceDidUpdateSafetyGuide(aGuidance, aSafetyGuide)
    }

    override fun didFinishPlayVoiceGuide(aGuidance: KNGuidance, aVoiceGuide: KNGuide_Voice) {
        binding.naviView.didFinishPlayVoiceGuide(aGuidance, aVoiceGuide)
    }

    override fun shouldPlayVoiceGuide(
        aGuidance: KNGuidance,
        aVoiceGuide: KNGuide_Voice,
        aNewData: MutableList<ByteArray>
    ): Boolean {
        return binding.naviView.shouldPlayVoiceGuide(aGuidance, aVoiceGuide, aNewData)
    }

    override fun willPlayVoiceGuide(aGuidance: KNGuidance, aVoiceGuide: KNGuide_Voice) {
        binding.naviView.willPlayVoiceGuide(aGuidance, aVoiceGuide)
    }

    override fun didUpdateCitsGuide(aGuidance: KNGuidance, aCitsGuide: KNGuide_Cits) {
        binding.naviView.didUpdateCitsGuide(aGuidance, aCitsGuide)
    }
}
