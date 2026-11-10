package com.example.autoclicker

import android.os.Handler
import android.os.Looper
import java.util.*

class ClickEngine(
    private val cycleDurationMs: Long,
    private val clickSpeedMs: Long,
    private val clicksPerCycle: Int,
    private val onCycleComplete: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private val cycleHistory: Queue<String> = LinkedList()

    fun start(x: Float, y: Float) {
        if (isRunning) return
        isRunning = true
        runCycle(x, y)
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun runCycle(x: Float, y: Float) {
        if (!isRunning) return

        val startTime = System.currentTimeMillis()
        var currentClicks = 0

        // تابع داخلی برای انجام تعداد کلیک‌های مشخص شده در یک چرخه
        fun performClicks() {
            if (!isRunning || currentClicks >= clicksPerCycle) {
                // پایان کلیک‌ها، حالا باید تا پایان زمان چرخه صبر کنیم
                val elapsed = System.currentTimeMillis() - startTime
                val remainingWait = (cycleDurationMs - elapsed).coerceAtLeast(0L)
                
                handler.postDelayed({
                    val endTime = System.currentTimeMillis()
                    val logEntry = "Cycle ${System.currentTimeMillis() % 10000} | Duration: ${endTime - startTime}ms"
                    addToHistory(logEntry)
                    
                    if (isRunning) runCycle(x, y) // شروع چرخه بعدی
                }, remainingWait)
                return
            }

            ClickAccessibilityService.instance?.tap(x, y) { success ->
                if (success) {
                    currentClicks++
                    handler.postDelayed({ performClicks() }, clickSpeedMs)
                } else {
                    stop()
                }
            }
        }

        performClicks()
    }

    private fun addToHistory(entry: String) {
        if (cycleHistory.size >= 20) cycleHistory.poll()
        cycleHistory.add(entry)
        onCycleComplete(cycleHistory.joinToString("\n"))
    }
}
