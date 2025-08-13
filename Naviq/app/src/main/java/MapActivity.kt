package com.example.nav

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakaomobility.knsdk.KNLanguageType
import com.kakaomobility.knsdk.KNSDK
import com.kakaomobility.knsdk.common.objects.KNError_Code_C103
import com.kakaomobility.knsdk.common.objects.KNError_Code_C302

class MapActivity : AppCompatActivity() {

    private var currentLat: Double? = null
    private var currentLng: Double? = null
    private var destinationLat: Double? = null
    private var destinationLng: Double? = null
    private var destinationName: String? = null

    private lateinit var mapView: MapView
    private lateinit var kakaoMap: KakaoMap

    private val PERMISSION_REQUEST_CODE = 100
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            destinationLat = result.data?.getDoubleExtra("end_lat", 0.0)
            destinationLng = result.data?.getDoubleExtra("end_lng", 0.0)
            destinationName = result.data?.getStringExtra("place_name")

            if (destinationLat != null && destinationLng != null) {
                val position = LatLng.from(destinationLat!!, destinationLng!!)
                addDestinationMarker(position)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.map_view)

        // ── 재난 정보 BottomSheet ───────────────────────────────────────────────
        findViewById<ImageView>(R.id.btn_disaster).setOnClickListener {
            // 파일명 정확: res/layout/bottomsheet_disaster_info.xml
            val dialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.bottomsheet_disaster_info, null)
            dialog.setContentView(view)
            dialog.show()
        }

        // ── 검색바: 검색 화면 이동 ───────────────────────────────────────────────
        val searchBar = findViewById<LinearLayout>(R.id.search_bar_container)
        searchBar.setOnClickListener {
            val intent = Intent(this, MapSearchActivity::class.java)
            currentLat?.let { intent.putExtra("start_lat", it) }
            currentLng?.let { intent.putExtra("start_lng", it) }
            searchLauncher.launch(intent)
        }

        // ── 길찾기 시작 버튼: 내비 SDK 초기화 후 미리보기로 이동 ────────────────
        findViewById<ImageView>(R.id.btn_route_start)?.setOnClickListener {
            if (currentLat != null && currentLng != null &&
                destinationLat != null && destinationLng != null
            ) {
                KNSDK.initializeWithAppKey(
                    BuildConfig.KAKAO_NATIVE_APP_KEY,
                    BuildConfig.VERSION_NAME,
                    null,
                    KNLanguageType.KNLanguageType_KOREAN
                ) { error ->
                    if (error != null) {
                        when (error.code) {
                            KNError_Code_C103 -> {
                                Log.d("KAKAO_NAV", "내비 인증 실패: $error")
                            }
                            KNError_Code_C302 -> {
                                Log.d("KAKAO_NAV", "내비 권한 오류: $error")
                                ActivityCompat.requestPermissions(
                                    this,
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                                    1
                                )
                            }
                            else -> {
                                Log.d("KAKAO_NAV", "내비 초기화 실패: $error")
                            }
                        }
                    } else {
                        Log.d("KAKAO_NAV", "내비 초기화 성공")
                        Handler(Looper.getMainLooper()).post {
                            val intent = Intent(this, NavigationPreviewActivity::class.java).apply {
                                putExtra("startLatitude", currentLat!!)
                                putExtra("startLongitude", currentLng!!)
                                putExtra("endLatitude", destinationLat!!)
                                putExtra("endLongitude", destinationLng!!)
                            }
                            startActivity(intent)
                        }
                    }
                }
            } else {
                Toast.makeText(this, "도착지를 지정해주세요", Toast.LENGTH_SHORT).show()
            }
        }

        // ── KakaoMap 준비 ───────────────────────────────────────────────────────
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(error: Exception?) {
                Toast.makeText(this@MapActivity, "지도 오류: $error", Toast.LENGTH_SHORT).show()
            }
        }, object : com.kakao.vectormap.KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                checkLocationPermissionAndMove()
            }
            override fun getZoomLevel(): Int = 15
            override fun getPosition(): LatLng = LatLng.from(37.5665, 126.9780)
            override fun isVisible(): Boolean = true
            override fun getViewName(): String = "MapView"
        })
    }

    private fun checkLocationPermissionAndMove() {
        if (REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            moveToCurrentLocation()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
        }
    }

    private fun moveToCurrentLocation() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        val lastKnown: Location? =
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        lastKnown?.let {
            val latLng = LatLng.from(it.latitude, it.longitude)
            currentLat = it.latitude
            currentLng = it.longitude
            updateMapLocation(latLng)
        }

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                currentLat = location.latitude
                currentLng = location.longitude
                updateMapLocation(LatLng.from(location.latitude, location.longitude))
                locationManager.removeUpdates(this)
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 0L, 0f, locationListener
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 0L, 0f, locationListener
            )
        }
    }

    private fun updateMapLocation(latLng: LatLng) {
        kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(latLng))
        val style = LabelStyle.from(R.drawable.ic_marker)
        val styles = kakaoMap.labelManager?.addLabelStyles(LabelStyles.from(style))
        val layer = kakaoMap.labelManager?.layer
        layer?.removeAll()
        layer?.addLabel(LabelOptions.from(latLng).setStyles(styles))
    }

    private fun addDestinationMarker(latLng: LatLng) {
        val style = LabelStyle.from(R.drawable.ic_marker)
        val styles = kakaoMap.labelManager?.addLabelStyles(LabelStyles.from(style))
        val layer = kakaoMap.labelManager?.layer
        layer?.removeAll()
        layer?.addLabel(LabelOptions.from(latLng).setStyles(styles))
        kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(latLng))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            moveToCurrentLocation()
        } else {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    override fun onPause() {
        mapView.pause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
