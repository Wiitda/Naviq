package com.example.nav

import android.app.Application
import com.kakaomobility.knsdk.KNSDK
import com.kakao.vectormap.KakaoMapSdk
import dagger.hilt.android.HiltAndroidApp


@HiltAndroidApp
class KNApplication : Application() {

    companion object {
        lateinit var knsdk: KNSDK
    }

    override fun onCreate() {
        super.onCreate()
        initializeKNSDK()
        initializeKakaoMap()
    }

    // 카카오내비 SDK 초기화
    private fun initializeKNSDK() {
        knsdk = KNSDK.apply {
            install(this@KNApplication, "$filesDir/KNSample")
        }
    }

    // 카카오맵 SDK 초기화
    private fun initializeKakaoMap() {
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }
}
