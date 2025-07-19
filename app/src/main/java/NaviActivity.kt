package com.example.nav

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kakaomobility.knsdk.KNRoutePriority
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
import com.kakaomobility.knsdk.trip.kntrip.KNTrip
import com.kakaomobility.knsdk.trip.kntrip.knroute.KNRoute
import com.kakaomobility.knsdk.ui.view.KNNaviView

class NaviActivity : AppCompatActivity(),

    KNGuidance_GuideStateDelegate,
    KNGuidance_LocationGuideDelegate,
    KNGuidance_RouteGuideDelegate,
    KNGuidance_SafetyGuideDelegate,
    KNGuidance_VoiceGuideDelegate,
    KNGuidance_CitsGuideDelegate {

    lateinit var naviView: KNNaviView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navi)

        naviView = findViewById(R.id.navi_view)

        window?.apply {
            statusBarColor = Color.TRANSPARENT
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        requestRoute()
    }

    fun requestRoute() {
        Thread {
            // ★ 좌표를 반드시 유효한 값으로 수정 (예시 좌표 사용)
            val startPoi = KNPOI("현위치", 309840, 552483, "현위치")
            val goalPoi = KNPOI("목적지", 321497, 532896, "목적지")

            KNApplication.knsdk.makeTripWithStart(
                aStart = startPoi,
                aGoal = goalPoi,
                aVias = null
            ) { aError, aTrip ->
                runOnUiThread {
                    if (aError == null) {
                        startGuide(aTrip)
                    } else {
                        // ★ 에러 내용을 확인할 수 있도록 Toast로 출력
                        Toast.makeText(this, "경로 요청 실패: $aError", Toast.LENGTH_LONG).show()

                    }
                }
            }
        }.start()
    }


    fun startGuide(trip: KNTrip?) {
        KNApplication.knsdk.sharedGuidance()?.apply {
            guideStateDelegate = this@NaviActivity
            locationGuideDelegate = this@NaviActivity
            routeGuideDelegate = this@NaviActivity
            safetyGuideDelegate = this@NaviActivity
            voiceGuideDelegate = this@NaviActivity
            citsGuideDelegate = this@NaviActivity

            naviView.initWithGuidance(
                this,
                trip,
                KNRoutePriority.KNRoutePriority_Recommand,
                0
            )
        }
    }

    // 가이드 상태 콜백
    override fun guidanceGuideStarted(aGuidance: KNGuidance) {naviView.guidanceGuideStarted(aGuidance)}
    override fun guidanceGuideEnded(aGuidance: KNGuidance) {naviView.guidanceGuideEnded(aGuidance)}
    override fun guidanceCheckingRouteChange(aGuidance: KNGuidance) {naviView.guidanceCheckingRouteChange(aGuidance)}
    override fun guidanceOutOfRoute(aGuidance: KNGuidance) {naviView.guidanceOutOfRoute(aGuidance)}
    override fun guidanceRouteChanged(
        aGuidance: KNGuidance,
        aFromRoute: KNRoute,
        aFromLocation: KNLocation,
        aToRoute: KNRoute,
        aToLocation: KNLocation,
        aChangeReason: KNGuideRouteChangeReason
    ) {
        // 필요시 처리
    }


    // 경로 관련 콜백
    override fun guidanceDidUpdateRoutes(aGuidance: KNGuidance, aRoutes: List<KNRoute>, aMultiRouteInfo: KNMultiRouteInfo?) {naviView.guidanceDidUpdateRoutes(aGuidance, aRoutes, aMultiRouteInfo)}



    override fun guidanceRouteUnchanged(aGuidance: KNGuidance) {naviView.guidanceRouteUnchanged(aGuidance)}
    override fun guidanceRouteUnchangedWithError(aGuidnace: KNGuidance, aError: KNError) {naviView.guidanceRouteUnchangedWithError(aGuidnace, aError)}
    override fun guidanceDidUpdateRouteGuide(aGuidance: KNGuidance, aRouteGuide: KNGuide_Route) {naviView.guidanceDidUpdateRouteGuide(aGuidance, aRouteGuide)}




    // 안전/음성/CITS
    override fun guidanceDidUpdateAroundSafeties(aGuidance: KNGuidance, aSafeties: List<KNSafety>?) {naviView.guidanceDidUpdateAroundSafeties(aGuidance, aSafeties)}
    override fun guidanceDidUpdateSafetyGuide(aGuidance: KNGuidance, aSafetyGuide: KNGuide_Safety?) {naviView.guidanceDidUpdateSafetyGuide(aGuidance, aSafetyGuide)}
    override fun didFinishPlayVoiceGuide(aGuidance: KNGuidance, aVoiceGuide: KNGuide_Voice) {naviView.didFinishPlayVoiceGuide(aGuidance, aVoiceGuide)}
    override fun shouldPlayVoiceGuide(aGuidance: KNGuidance, aVoiceGuide: KNGuide_Voice, aNewData: MutableList<ByteArray>): Boolean {
        return naviView.shouldPlayVoiceGuide(aGuidance, aVoiceGuide, aNewData)
    }
    override fun willPlayVoiceGuide(aGuidance: KNGuidance, aVoiceGuide: KNGuide_Voice) {naviView.willPlayVoiceGuide(aGuidance, aVoiceGuide)}
    override fun didUpdateCitsGuide(aGuidance: KNGuidance, aCitsGuide: KNGuide_Cits) {naviView.didUpdateCitsGuide(aGuidance, aCitsGuide)}
    override fun guidanceDidUpdateLocation(aGuidance: KNGuidance, aLocationGuide: KNGuide_Location) {
        naviView.guidanceDidUpdateLocation(aGuidance, aLocationGuide)
    }
}
