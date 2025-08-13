package com.example.nav

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kakaomobility.knsdk.KNLanguageType

class MainActivity : AppCompatActivity(), View.OnClickListener {

    lateinit var btnGuide: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnGuide = findViewById(R.id.btn_guide)
        btnGuide.setOnClickListener(this)
    }

    /**
     * 버튼 클릭 이벤트
     */
    override fun onClick(v: View?) {
        checkPermission()
    }


    /**
     * GPS 위치 권한을 확인합니다.
     */
    fun checkPermission() {
        when {
            checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED -> {
                // GPS 퍼미션 체크
                gpsPermissionCheck()
            }

            else -> {
                // 길찾기 SDK 인증
                knsdkAuth()
            }
        }
    }
    /**
     * 길찾기 SDK 인증을 진행합니다.
     */
    fun knsdkAuth() {
        KNApplication.knsdk.apply {
            initializeWithAppKey(
                aAppKey = "96d82c504cfe9f84bf4b0fec5dd5d657",       // 카카오디벨로퍼스에서 부여 받은 앱 키
                aClientVersion = "1.0.0",                                               // 현재 앱의 클라이언트 버전
                aLangType = KNLanguageType.KNLanguageType_KOREAN,   // 언어 타입
                aCompletion = {

                    // Toast는 UI를 갱신하는 작업이기 때문에 UIThread에서 동작되도록 해야 합니다.
                    runOnUiThread {
                        if (it != null) {
                            Toast.makeText(applicationContext, "인증에 실패하였습니다", Toast.LENGTH_LONG)
                                .show()

                        } else {
                            Toast.makeText(applicationContext, "인증 성공하였습니다", Toast.LENGTH_LONG)
                                .show()

                            val intent = Intent(this@MainActivity, MapActivity::class.java)
                            startActivity(intent)

                        }
                    }
                })

        }
    }

    /**
     * GPS 위치 권한을 요청합니다.
     */
    fun gpsPermissionCheck() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            1234)
    }

    /**
     * GPS 위치 권한 요청의 실패 여부를 확인합니다.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1234 -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // 다시 권한 요청하는 곳으로 돌아갑니다.
                    checkPermission()
                }
            }
        }
    }

}
