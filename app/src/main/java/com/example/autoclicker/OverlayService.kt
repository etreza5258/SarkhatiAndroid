package com.example.autoclicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var markerView: View? = null
    private var panelView: View? = null
    private var markerParams: WindowManager.LayoutParams? = null

    private var engine: ClickEngine? = null
    private val ui = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private var startHour = -1
    private var startMinute = -1
    private var cycleMs = 1000L
    private var speedMs = 30L
    private var clicksPerCycle = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(1, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            startHour = it.getIntExtra("hour", -1)
            startMinute = it.getIntExtra("minute", -1)
            cycleMs = it.getLongExtra("cycleMs", 1000L)
            speedMs = it.getLongExtra("speedMs", 30L)
            clicksPerCycle = it.getIntExtra("clicksPerCycle", 1)
        }
        if (markerView == null) addMarker()
        if (panelView == null) addPanel()
        return START_STICKY
    }

    // ---------- نشانگر متحرک ----------
    private fun addMarker() {
        val v = LayoutInflater.from(this).inflate(R.layout.overlay_marker, null)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 200
            y = 500
        }

        var dx = 0f
        var dy = 0f
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX - lp.x; dy = e.rawY - lp.y; true }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (e.rawX - dx).toInt()
                    lp.y = (e.rawY - dy).toInt()
                    wm.updateViewLayout(v, lp)
                    true
                }
                else -> false
            }
        }

        wm.addView(v, lp)
        markerView = v
        markerParams = lp
    }

    // ---------- پنل شناور ----------
    private fun addPanel() {
        val v = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        val tvClock = v.findViewById<TextView>(R.id.tvClock)
        val tvCountdown = v.findViewById<TextView>(R.id.tvCountdown)
        val btnPlay = v.findViewById<Button>(R.id.btnPlay)
        val btnPause = v.findViewById<Button>(R.id.btnPause)
        val btnClose = v.findViewById<Button>(R.id.btnClose)

        // ساعت گوشی + شمارش معکوس
        ui.post(object : Runnable {
            override fun run() {
                tvClock.text = timeFmt.format(Date())
                tvCountdown.text = countdownText()
                ui.postDelayed(this, 500)
            }
        })

        btnPlay.setOnClickListener { startClicking() }
        btnPause.setOnClickListener { engine?.pause() }
        btnClose.setOnClickListener { stopSelf() }

        // کشیدن پنل
        var dx = 0f
        var dy = 0f
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX - lp.x; dy = e.rawY - lp.y; true }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (e.rawX - dx).toInt()
                    lp.y = (e.rawY - dy).toInt()
                    wm.updateViewLayout(v, lp)
                    true
                }
                else -> false
            }
        }

        wm.addView(v, lp)
        panelView = v
    }

    private fun countdownText(): String {
        if (startHour < 0 || startMinute < 0) return "شروع: دستی"
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        var diff = (target.timeInMillis - now.timeInMillis) / 1000
        if (diff < 0) diff = 0
        return String.format(Locale.US, "شروع تا %02d:%02d", diff / 60, diff % 60)
    }

    private fun startClicking() {
        val svc = ClickAccessibilityService.instance
        if (svc == null) {
            (application as? MainActivity)?.setStatus("سرویس دسترسی فعال نیست")
            return
        }
        val mp = markerParams ?: return
        val x = mp.x.toFloat() + 40f
        val y = mp.y.toFloat() + 40f

        engine = ClickEngine(
            service = svc,
            x = x, y = y,
            cycleMs = cycleMs,
            speedMs = speedMs,
            clicksPerCycle = clicksPerCycle,
            startHour = startHour,
            startMinute = startMinute,
            onCycleDone = { index, count ->
                // لاگ در صفحه اصلی برنامه، نه پنل شناور
                (application as? MainActivity)?.appendLog(
                    "${timeFmt.format(Date())}  سایکل #$index  ($count کلیک)"
                )
            }
        )
        engine?.start()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        engine?.stop()
        markerView?.let { runCatching { wm.removeView(it) } }
        panelView?.let { runCatching { wm.removeView(it) } }
        markerView = null
        panelView = null
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("ac", "AutoClicker", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, "ac")
        else
            @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("SarkhatiAndroid")
            .setContentText("پنل شناور فعال است")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }
}
