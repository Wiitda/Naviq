package com.example.nav

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class SearchActivity : AppCompatActivity() {

    lateinit var editStart: EditText
    lateinit var editGoal: EditText
    lateinit var btnFinish: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        editStart = findViewById(R.id.edit_start)
        editGoal = findViewById(R.id.edit_goal)
        btnFinish = findViewById(R.id.btn_finish)

        btnFinish.setOnClickListener {
            val startInput = editStart.text.toString()
            val goalInput = editGoal.text.toString()

            // 예시 좌표 값 (실제로는 검색 결과에서 받아와야 함)
            val startX = 309840
            val startY = 552483
            val goalX = 321497
            val goalY = 532896

            val resultIntent = Intent().apply {
                putExtra("start_name", startInput)
                putExtra("goal_name", goalInput)
                putExtra("start_x", startX)
                putExtra("start_y", startY)
                putExtra("goal_x", goalX)
                putExtra("goal_y", goalY)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}
