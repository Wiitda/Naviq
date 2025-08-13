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
import java.lang.Exception
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val repo: NavigationRepository
) : ViewModel() {

    private val _coordZipResult = MutableStateFlow(CoordZipResult())
    val coordZipResult = _coordZipResult.asStateFlow()

    private val _distanceData = MutableStateFlow(DistanceResult())
    val distanceData = _distanceData.asStateFlow()

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
                    success = CoordZipData(start?.lat, start?.lon, end?.lat, end?.lon)
                )
            } catch (e: Exception) {
                android.util.Log.e("NAVI_ROTATION", "좌표 변환 중 예외 발생", e)
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
