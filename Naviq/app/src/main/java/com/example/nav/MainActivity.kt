package com.example.nav

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kakaomobility.knsdk.KNLanguageType

class MainActivity : AppCompatActivity(), View.OnClickListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQ_LOCATION_PERMS = 1234
    }

    private lateinit var btnGuide: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnGuide = findViewById(R.id.btn_guide)
        btnGuide.setOnClickListener(this)
    }

    /** 버튼 클릭 */
    override fun onClick(v: View?) {
        checkPermissionAndProceed()
    }

    /** 위치 권한 & 위치 설정 확인 후 진행 */
    private fun checkPermissionAndProceed() {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        if (!isLocationEnabled()) {
            Toast.makeText(this, "위치 서비스가 꺼져 있습니다. 켜주세요.", Toast.LENGTH_SHORT).show()
            // 설정화면으로 이동 (선택)
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        // 모든 준비 OK → KNSDK 인증
        knsdkAuth()
    }

    /** FINE 또는 COARSE 권한 보유 여부 */
    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /** 권한 요청 */
    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQ_LOCATION_PERMS
        )
    }

    /** 기기 위치 서비스가 켜져 있는지 */
    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /** 길찾기 SDK 인증 */
    private fun knsdkAuth() {
        // ⚠️ INTERNET / ACCESS_NETWORK_STATE 권한은 AndroidManifest.xml에 선언되어 있어야 합니다.
        // <uses-permission android:name="android.permission.INTERNET"/>
        // <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>

        KNApplication.knsdk.apply {
            initializeWithAppKey(
                aAppKey = "96d82c504cfe9f84bf4b0fec5dd5d657",         // Kakao Developers 네이티브 앱 키
                aClientVersion = "1.0.0",                              // 앱 버전 (문자열)
                aLangType = KNLanguageType.KNLanguageType_KOREAN,      // 언어
                aCompletion = { err ->
                    runOnUiThread {
                        if (err != null) {
                            // 에러 내용을 로그/토스트로 표출 → Logcat에서 정확한 원인 확인
                            Log.e(TAG, "KNSDK Auth failed: $err")
                            Toast.makeText(
                                applicationContext,
                                "인증 실패: $err",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                applicationContext,
                                "인증 성공하였습니다",
                                Toast.LENGTH_LONG
                            ).show()
                            startActivity(Intent(this@MainActivity, MapActivity::class.java))
                        }
                    }
                }
            )
        }
    }

    /** 권한 요청 결과 처리 */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION_PERMS) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                checkPermissionAndProceed()
            } else {
                val showRationaleFine = ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                )
                val showRationaleCoarse = ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (!showRationaleFine && !showRationaleCoarse) {
                    // "다시 묻지 않음" 상태: 설정으로 안내
                    Toast.makeText(
                        this,
                        "위치 권한이 필요합니다. 설정에서 권한을 허용해 주세요.",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    })
                } else {
                    Toast.makeText(this, "위치 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
