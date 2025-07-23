package com.example.nav

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class SearchActivity : AppCompatActivity() {

    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        val apiKey = BuildConfig.KAKAO_API_KEY
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var editStart: EditText
    private lateinit var editGoal: EditText
    private lateinit var btnFinish: Button

    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        editStart = findViewById(R.id.edit_start)
        editGoal = findViewById(R.id.edit_goal)
        btnFinish = findViewById(R.id.btn_finish)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 위치 권한 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            fillCurrentLocation()
        }

        btnFinish.setOnClickListener {
            val goalAddress = editGoal.text.toString()

            val geocoder = Geocoder(this, Locale.KOREA)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val addressList = geocoder.getFromLocationName(goalAddress, 1)
                    if (!addressList.isNullOrEmpty()) {
                        val goalLat = addressList[0].latitude
                        val goalLng = addressList[0].longitude

                        // 위경도 → TM 좌표 변환
                        val startTM = convertToTM(currentLng, currentLat)
                        val goalTM = convertToTM(goalLng, goalLat)

                        withContext(Dispatchers.Main) {
                            if (startTM != null && goalTM != null) {
                                val resultIntent = Intent().apply {
                                    putExtra("start_name", "현재 위치")
                                    putExtra("goal_name", goalAddress)
                                    putExtra("start_x", startTM.first)
                                    putExtra("start_y", startTM.second)
                                    putExtra("goal_x", goalTM.first)
                                    putExtra("goal_y", goalTM.second)
                                }
                                setResult(Activity.RESULT_OK, resultIntent)
                                finish()
                            } else {
                                Toast.makeText(applicationContext, "좌표 변환 실패", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            editGoal.error = "목적지 주소를 찾을 수 없습니다."
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SearchActivity", "Geocoding or TM convert error", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "오류 발생", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            fillCurrentLocation()
        }
    }

    private fun fillCurrentLocation() {
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        currentLat = it.latitude
                        currentLng = it.longitude
                        val address = getAddressFromLocation(currentLat, currentLng)
                        editStart.setText("현재 위치: $address")
                    } ?: run {
                        editStart.setText("현재 위치 정보를 가져올 수 없습니다.")
                    }
                }
        } catch (e: SecurityException) {
            e.printStackTrace()
            editStart.setText("위치 권한 오류")
        }
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double): String {
        val geocoder = Geocoder(this, Locale.KOREA)
        return try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                addresses[0].getAddressLine(0)
            } else {
                "주소를 찾을 수 없음"
            }
        } catch (e: IOException) {
            e.printStackTrace()
            "주소 변환 오류"
        }
    }

    // 위경도 → TM 좌표로 변환 (Kakao REST API 호출)
    private fun convertToTM(longitude: Double, latitude: Double): Pair<Double, Double>? {
        val url =
            "https://dapi.kakao.com/v2/local/geo/transcoord.json?x=$longitude&y=$latitude&input_coord=WGS84&output_coord=TM"

        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "KakaoAK $apiKey") // ✅ 수정된 부분

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val response = reader.readText()
            reader.close()

            val json = JSONObject(response)
            val documents = json.getJSONArray("documents")
            if (documents.length() > 0) {
                val obj = documents.getJSONObject(0)
                val x = obj.getDouble("x")
                val y = obj.getDouble("y")
                Pair(x, y)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("TMConvert", "API error", e)
            null
        }
    }
}
