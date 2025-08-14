package com.example.nav.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nav.data.model.CoordZipData
import com.example.nav.data.model.CoordZipResult
import com.example.nav.data.model.DistanceResult
import com.example.nav.repository.NavigationRepository
import com.kakaomobility.knsdk.common.util.FloatPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val repo: NavigationRepository
) : ViewModel() {

    private val _coordZipResult = MutableStateFlow(CoordZipResult())
    val coordZipResult = _coordZipResult.asStateFlow()

    private val _distanceData = MutableStateFlow(DistanceResult())
    val distanceData = _distanceData.asStateFlow()

    /** 기존: 경유지 없이 start/end만 변환 */
    fun getCoordConvertData(startLat: Double, startLng: Double, endLat: Double, endLng: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val start = repo.getCoordConvertData(startLat, startLng)?.coordinate
                val end = repo.getCoordConvertData(endLat, endLng)?.coordinate

                android.util.Log.d("NAVI_ROTATION", "변환된 좌표 start: $start")
                android.util.Log.d("NAVI_ROTATION", "변환된 좌표 end: $end")

                if (start == null || end == null) {
                    _coordZipResult.value = CoordZipResult(
                        failure = IllegalStateException("좌표 변환 실패 - start: $start, end: $end")
                    )
                    return@launch
                }

                _coordZipResult.value = CoordZipResult(
                    success = CoordZipData(
                        startLatitude = start.lat,
                        startLongitude = start.lon,
                        endLatitude = end.lat,
                        endLongitude = end.lon
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("NAVI_ROTATION", "좌표 변환 중 예외 발생", e)
                _coordZipResult.value = CoordZipResult(failure = e)
            }
        }
    }

    /** 추가: 경유지(viaLat, viaLng)까지 함께 변환 */
    fun getCoordConvertDataWithVia(
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double,
        viaLat: Double, viaLng: Double
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val start = repo.getCoordConvertData(startLat, startLng)?.coordinate
                val end = repo.getCoordConvertData(endLat, endLng)?.coordinate
                val via = repo.getCoordConvertData(viaLat, viaLng)?.coordinate

                android.util.Log.d("NAVI_ROTATION", "변환된 좌표 start: $start")
                android.util.Log.d("NAVI_ROTATION", "변환된 좌표 end: $end")
                android.util.Log.d("NAVI_ROTATION", "변환된 좌표 via: $via")

                if (start == null || end == null || via == null) {
                    _coordZipResult.value = CoordZipResult(
                        failure = IllegalStateException("좌표 변환 실패 - start: $start, end: $end, via: $via")
                    )
                    return@launch
                }

                _coordZipResult.value = CoordZipResult(
                    success = CoordZipData(
                        startLatitude = start.lat,
                        startLongitude = start.lon,
                        endLatitude = end.lat,
                        endLongitude = end.lon,
                        viaLatitude = via.lat,          // 경유지 세팅
                        viaLongitude = via.lon
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("NAVI_ROTATION", "좌표 변환(경유지 포함) 중 예외 발생", e)
                _coordZipResult.value = CoordZipResult(failure = e)
            }
        }
    }

    fun getDistanceData(start: FloatPoint, end: FloatPoint) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = repo.getDistanceData(start, end)
                _distanceData.value = DistanceResult(success = response)
            } catch (e: Exception) {
                _distanceData.value = DistanceResult(failure = e)
            }
        }
    }
}
