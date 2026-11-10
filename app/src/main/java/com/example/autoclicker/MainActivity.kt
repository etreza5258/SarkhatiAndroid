package com.example.autoclicker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var etHour: EditText
    private lateinit var etMinute: EditText
    private lateinit var etCycle: EditText
    private lateinit var etSpeed: EditText
    private lateinit var etClicks: EditText
    private lateinit var tvLog: LinearLayout
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etHour = findViewById(R.id.etHour)
        etMinute = findViewById(R.id.etMinute)
        etCycle = findViewById(R.id.etCycle)
        etSpeed = findViewById(R.id.etSpeed)
        etClicks = findViewById(R.id.etClicks)
        tvLog = findViewById(R.id.logContainer)
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnPermission).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                startOverlay()
            }
        }

        findViewById<Button>(R.id.btnA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "سرویس Auto Clicker را فعال کنید", Toast.LENGTH_LONG).show()
        }

        // دکمه «همین حالا»
        findViewById<Button>(R.id.btnNow).setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            etHour.setText(cal.get(java.util.Calendar.HOUR_OF_DAY).toString())
            etMinute.setText(cal.get(java.util.Calendar.MINUTE).toString())
            Toast.makeText(this, "زمان شروع روی «همین حالا» تنظیم شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startOverlay() {
        val i = Intent(this, OverlayService::class.java)
        i.putExtra("hour", etHour.text.toString().toIntOrNull() ?: -1)
        i.putExtra("minute", etMinute.text.toString().toIntOrNull() ?: -1)
        i.putExtra("cycleMs", (etCycle.text.toString().toLongOrNull() ?: 1L) * 1000L)
        i.putExtra("speedMs", etSpeed.text.toString().toLongOrNull() ?: 30L)
        i.putExtra("clicksPerCycle", etClicks.text.toString().toIntOrNull() ?: 1)
        startForegroundService(i)
        Toast.makeText(this, "پنل شناور فعال شد", Toast.LENGTH_SHORT).show()
    }

    /** از OverlayService صدا زده می‌شود تا لاگ در صفحه اصلی نمایش داده شود */
    fun appendLog(line: String) {
        runOnUiThread {
            val tv = TextView(this).apply {
                text = line
                textSize = 13f
                setPadding(8, 6, 8, 6)
            }
            tvLog.addView(tv, 0)
            while (tvLog.childCount > 20) tvLog.removeViewAt(tvLog.childCount - 1)
        }
    }

    fun setStatus(text: String) {
        runOnUiThread { tvStatus.text = text }
    }
}
