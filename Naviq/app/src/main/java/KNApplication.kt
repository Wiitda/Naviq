package com.example.nav

import android.app.Application
import android.util.Log
import com.kakaomobility.knsdk.KNSDK
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.sdk.common.util.Utility
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class KNApplication : Application() {

    companion object {
        lateinit var knsdk: KNSDK
            private set
        private const val TAG = "KNApplication"
    }

    override fun onCreate() {
        super.onCreate()
        initializeKNSDK()
        initializeKakaoMap()

        // 앱 실행 시 내 키 해시 출력 (콘솔/모빌리티 콘솔에 등록할 값)
        try {
            val keyHash = com.kakao.sdk.common.util.Utility.getKeyHash(this)
            Log.d("KeyHash", keyHash)
        } catch (e: Exception) {
            Log.e(TAG, "Print KeyHash failed", e)
        }
    }

    // 카카오내비 SDK 설치/초기화
    private fun initializeKNSDK() {
        try {
            // 설치 경로 보장
            val installDir = File(filesDir, "KNSample").apply { mkdirs() }

            // KNSDK가 싱글톤인 경우: install 호출 후 참조 보관
            KNSDK.install(this, installDir.absolutePath)
            knsdk = KNSDK

            Log.i(TAG, "KNSDK installed at: ${installDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "KNSDK install failed", e)
        }
    }

    // 카카오맵 SDK 초기화
    private fun initializeKakaoMap() {
        try {
            // BuildConfig.KAKAO_NATIVE_APP_KEY 는 build.gradle 에서 buildConfigField 로 주입되어 있어야 합니다.
            KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
            Log.i(TAG, "KakaoMap SDK initialized")
        } catch (e: Exception) {
            Log.e(TAG, "KakaoMap init failed", e)
        }
    }
}
