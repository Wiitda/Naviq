package com.example.nav

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kakaomobility.knsdk.KNRoutePriority
import com.kakaomobility.knsdk.common.objects.KNError
import com.kakaomobility.knsdk.common.objects.KNPOI
import com.kakaomobility.knsdk.guidance.knguidance.*
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

    private val SEARCH_REQUEST_CODE = 1001

    lateinit var naviView: KNNaviView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navi)

        naviView = findViewById(R.id.navi_view)

        // ▼ ① 재난 버튼 참조
        val disasterBtn = findViewById<ImageView>(R.id.btn_disaster)
        val searchText = findViewById<TextView>(R.id.search_text)
        val micIcon = findViewById<ImageView>(R.id.mic_icon)

        searchText.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivityForResult(intent, SEARCH_REQUEST_CODE)
        }

        micIcon.setOnClickListener {
            Toast.makeText(this, "음성 검색 준비중...", Toast.LENGTH_SHORT).show()
        }

        // ▼ ② 클릭 리스너 설정
        disasterBtn.setOnClickListener {
            val bottomSheet = DisasterInfoBottomSheet()  // ← 직접 만든 BottomSheetDialogFragment
            bottomSheet.show(supportFragmentManager, bottomSheet.tag)
        }


        window?.apply {
            statusBarColor = Color.TRANSPARENT
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SEARCH_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val startX = data?.getIntExtra("start_x", 0) ?: 0
            val startY = data?.getIntExtra("start_y", 0) ?: 0
            val goalX = data?.getIntExtra("goal_x", 0) ?: 0
            val goalY = data?.getIntExtra("goal_y", 0) ?: 0

            val startPoi = KNPOI("출발지", startX, startY, "출발지")
            val goalPoi = KNPOI("도착지", goalX, goalY, "도착지")

            requestRoute(startPoi, goalPoi)
        }
    }

    fun requestRoute(startPoi: KNPOI, goalPoi: KNPOI) {
        Thread {
            KNApplication.knsdk.makeTripWithStart(
                aStart = startPoi,
                aGoal = goalPoi,
                aVias = null
            ) { aError, aTrip ->
                runOnUiThread {
                    if (aError == null) {
                        startGuide(aTrip)
                    } else {
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
    override fun guidanceGuideStarted(aGuidance: KNGuidance) { naviView.guidanceGuideStarted(aGuidance) }
    override fun guidanceGuideEnded(aGuidance: KNGuidance) { naviView.guidanceGuideEnded(aGuidance) }
    override fun guidanceCheckingRouteChange(aGuidance: KNGuidance) { naviView.guidanceCheckingRouteChange(aGuidance) }
    override fun guidanceOutOfRoute(aGuidance: KNGuidance) { naviView.guidanceOutOfRoute(aGuidance) }
    override fun guidanceRouteChanged(aGuidance: KNGuidance, aFromRoute: KNRoute, aFromLocation: KNLocation, aToRoute: KNRoute, aToLocation: KNLocation, aChangeReason: KNGuideRouteChangeReason) {}

    // 경로 관련 콜백
    override fun guidanceDidUpdateRoutes(aGuidance: KNGuidance, aRoutes: List<KNRoute>, aMultiRouteInfo: KNMultiRouteInfo?) { naviView.guidanceDidUpdateRoutes(aGuidance, aRoutes, aMultiRouteInfo) }
    override fun guidanceRouteUnchanged(aGuidance: KNGuidance) { naviView.guidanceRouteUnchanged(aGuidance) }
    override fun guidanceRouteUnchangedWithError(aGuidnace: KNGuidance, aError: KNError) { naviView.guidanceRouteUnchangedWithError(aGuidnace, aError) }
    override fun guidanceDidUpdateRouteGuide(aGuidance: KNGuidance, aRouteGuide: KNGuide_Route) { naviView.guidanceDidUpdateRouteGuide(aGuidance, aRouteGuide) }

    // 안전/음성/CITS
    override fun guidanceDidUpdateAroundSafeties(aGuidance: KNGuidance, aSafeties: List<KNSafety>?) { naviView.guidanceDidUpdateAroundSafeties(aGuidance, aSafeties) }
    override fun guidanceDidUpdateSafetyGuide(aGuidance: KNGuidance, aSafetyGuide: KNGuide_Safety?) { naviView.guidanceDidUpdateSafetyGuide(aGuidance, aSafetyGuide) }
    override fun didFinishPlayVoiceGuide(aGuidance: KNGuidance, aVoiceGuide: KNGuide_Voice) { naviView.didFinishPlayVoiceGuide(aGuidance, aVoiceGuide) }
    override fun shouldPlayVoiceGuide(aGuidance: KNGuidance, aVoiceGuide: KNGuide_Voice, aNewData: MutableList<ByteArray>): Boolean { return naviView.shouldPlayVoiceGuide(aGuidance, aVoiceGuide, aNewData) }
    override fun willPlayVoiceGuide(aGuidance: KNGuidance, aVoiceGuide: KNGuide_Voice) { naviView.willPlayVoiceGuide(aGuidance, aVoiceGuide) }
    override fun didUpdateCitsGuide(aGuidance: KNGuidance, aCitsGuide: KNGuide_Cits) { naviView.didUpdateCitsGuide(aGuidance, aCitsGuide) }
    override fun guidanceDidUpdateLocation(aGuidance: KNGuidance, aLocationGuide: KNGuide_Location) { naviView.guidanceDidUpdateLocation(aGuidance, aLocationGuide) }
}
