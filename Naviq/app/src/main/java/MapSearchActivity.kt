package com.example.nav

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

class MapSearchActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PlaceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_search)

        searchInput = findViewById(R.id.search_input)
        recyclerView = findViewById(R.id.recycler_view)

        // 뒤로가기 버튼: 이전 화면으로
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        // 검색박스 전체를 눌러도 EditText에 포커스가 가도록
        findViewById<LinearLayout>(R.id.search_box)?.setOnClickListener {
            searchInput.requestFocus()
            showKeyboard()
        }

        // 화면 진입 시 자동으로 포커스 + 키보드 표시
        searchInput.requestFocus()
        showKeyboard()

        adapter = PlaceAdapter { place ->
            val resultIntent = Intent().apply {
                putExtra("end_lat", place.y.toDouble())
                putExtra("end_lng", place.x.toDouble())
                putExtra("place_name", place.place_name)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val keyword = s?.toString().orEmpty()
                if (keyword.length > 1) searchPlace(keyword)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun showKeyboard() {
        // 상태바 플래그로 키보드 즉시 표시
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun searchPlace(keyword: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://dapi.kakao.com/")
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }).build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(KakaoPlaceService::class.java)
        service.searchPlaces("KakaoAK c794276b1eba611b6ee540c31fdda093", keyword)
            .enqueue(object : Callback<PlaceResponse> {
                override fun onResponse(
                    call: Call<PlaceResponse>,
                    response: Response<PlaceResponse>
                ) {
                    if (response.isSuccessful) {
                        response.body()?.documents?.let { adapter.submitList(it) }
                    }
                }

                override fun onFailure(call: Call<PlaceResponse>, t: Throwable) {
                    Toast.makeText(this@MapSearchActivity, "검색 실패: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}

// Retrofit API
interface KakaoPlaceService {
    @GET("v2/local/search/keyword.json")
    fun searchPlaces(
        @Header("Authorization") auth: String,
        @Query("query") query: String
    ): Call<PlaceResponse>
}

// 응답 모델
data class PlaceResponse(val documents: List<Place>)
data class Place(
    val place_name: String,
    val address_name: String,
    val road_address_name: String,
    val x: String,
    val y: String
)
