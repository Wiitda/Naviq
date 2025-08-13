package com.example.nav

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.nav.api.SafetyApiClient
import com.example.nav.data.model.SafetyResponse
import kotlinx.coroutines.launch
import retrofit2.awaitResponse
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.isActive

class SafetyApiTestActivity : AppCompatActivity() {

    private lateinit var tv: TextView
    private var pollingJob: kotlinx.coroutines.Job? = null
    private var lastToMillis: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tv = TextView(this).apply { text = "재난문자 호출 중…"; textSize = 16f; setPadding(32,64,32,32) }
        setContentView(tv)

        // 최초 1회만 즉시 호출 (최근 2시간)
        val (from, to) = buildTimeRangeKST(2)
        lifecycleScope.launch { callSafetyApiOnce(from, to) }
    }

    override fun onStart() {
        super.onStart()
        // 화면 보이는 동안만 90초 주기로 폴링
        pollingJob = lifecycleScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val to = formatIsoKst(now)
                val from = formatIsoKst(lastToMillis?.plus(1000) ?: (now - 2 * 60 * 60 * 1000))
                val ok = callSafetyApiOnce(from, to)
                if (ok) lastToMillis = now
                kotlinx.coroutines.delay(90_000)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        pollingJob?.cancel()
    }

    // ───── 여기부터 기존 함수들 ─────

    private suspend fun callSafetyApiOnce(fromTmIso: String, toTmIso: String): Boolean {
        val tz = TimeZone.getTimeZone("Asia/Seoul")
        // 오늘 날짜(YYYYMMDD)로 조회
        val ymdFmt = SimpleDateFormat("yyyyMMdd", Locale.KOREA).apply { timeZone = tz }
        val todayYmd = ymdFmt.format(Calendar.getInstance(tz).time)

        // 서버는 하루치 등을 줄 수 있으니, 클라에서 from/to(ISO)로 한 번 더 필터
        val kstFmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.KOREA).apply { timeZone = tz }
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA).apply { timeZone = tz }
        val fromDate = runCatching { isoFmt.parse(fromTmIso) }.getOrNull()
        val toDate   = runCatching { isoFmt.parse(toTmIso) }.getOrNull()

        return try {
            val res = SafetyApiClient.service
                .getDisasterMessages(
                    serviceKey = BuildConfig.SAFETY_DATA_KEY,
                    returnType = "json",
                    pageNo = 1,
                    rows = 50,
                    crtDt = todayYmd,   // 오늘 날짜
                    regionName = null
                )
                .awaitResponse()

            if (res.isSuccessful) {
                val body: SafetyResponse? = res.body()
                val all = body?.body.orEmpty()

                // 클라이언트 측 2시간 범위 필터
                val items = all.filter { it.createdAt != null }.filter { it ->
                    val d = runCatching { kstFmt.parse(it.createdAt!!) }.getOrNull()
                    d != null && fromDate != null && toDate != null && !d.before(fromDate) && !d.after(toDate)
                }.sortedByDescending { it.createdAt }

                val msg = buildString {
                    appendLine("성공 (${items.size}건, ${todayYmd})")
                    items.firstOrNull()?.let {
                        appendLine(" - 생성일시: ${it.createdAt}")
                        appendLine(" - 지역: ${it.regionName}")
                        appendLine(" - 유형: ${it.disasterType}")
                        appendLine(" - 내용: ${it.msg}")
                    } ?: append("최근 범위 내 항목 없음")
                }
                tv.text = msg
                Log.d("SafetyTest", msg)
                true
            } else {
                val err = "실패 code=${res.code()} msg=${res.message()}"
                tv.text = err
                Log.e("SafetyTest", err)
                false
            }
        } catch (e: Exception) {
            val err = "예외: ${e.message}"
            tv.text = err
            Log.e("SafetyTest", err, e)
            Toast.makeText(this, "예외 발생, 로그 확인", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun buildTimeRangeKST(hoursBack: Int): Pair<String, String> {
        val tz = TimeZone.getTimeZone("Asia/Seoul")
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA).apply { timeZone = tz }
        val calTo = Calendar.getInstance(tz).apply { set(Calendar.MILLISECOND, 0) }
        val calFrom = calTo.clone() as Calendar
        calFrom.add(Calendar.HOUR_OF_DAY, -hoursBack)
        return fmt.format(calFrom.time) to fmt.format(calTo.time)
    }

    private fun formatIsoKst(millis: Long): String {
        val tz = TimeZone.getTimeZone("Asia/Seoul")
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA).apply { timeZone = tz }
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = millis
            set(Calendar.MILLISECOND, 0)
        }
        return fmt.format(cal.time)
    }
}
