package com.example.autoclicker

import android.app.*
import android.content.*
import android.graphics.*
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private var clickX = 0f
    private var clickY = 0f
    private var engine: ClickEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
    }

    private fun setupOverlay() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null) // نکته: باید فایل XML مربوطه ساخته شود یا کدنویسی شود

        // در اینجا برای سادگی، ویو را به صورت برنامه‌نویسی شده می‌سازیم تا نیاز به فایل XML نباشد
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#AA000000"))
            setPadding(20, 20, 20, 20)
        }

        val btnTarget = Button(this).apply { text = "Set Target" }
        val btnStart = Button(this).apply { text = "Start" }
        val btnStop = Button(this).apply { text = "Stop" }
        val txtLog = TextView(this).apply { 
            setTextColor(Color.WHITE)
            textSize = 12f
        }

        layout.addView(btnTarget)
        layout.addView(btnStart)
        layout.addView(btnStop)
        layout.addView(txtLog)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        // منطق دکمه‌ها
        btnTarget.setOnClickListener {
            // در نسخه کامل، این دکمه باید یک نشانگر جابجا شونده باز کند
            Toast.makeText(this, "Target Set at Current Position", Toast.LENGTH_SHORT).show()
        }

        btnStart.setOnClickListener {
            engine = ClickEngine(1000, 50, 2) { log ->
                txtLog.text = log
            }
            engine?.start(500f, 1000f) // مثال: مختصات ثابت
        }

        btnStop.setOnClickListener {
            engine?.stop()
        }

        overlayView = layout
        windowManager.addView(overlayView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(overlayView)
    }
}
