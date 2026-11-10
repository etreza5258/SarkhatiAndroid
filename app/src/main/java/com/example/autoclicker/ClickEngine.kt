package com.example.autoclicker

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.*

/**
 * ClickEngine — مدیریت هوشمند چرخه کلیک
 * 
 * ویژگی‌ها:
 * 1. زمان‌بندی مطلق: هر سایکل بر اساس ساعت سیستم در یک نقطه دقیق از زمان شروع می‌شود.
 * 2. جلوگیری از انباشت تأخیر: اگر یک سایکل دیر اجرا شود، سایکل بعد دقیقاً سر وقت اجرا می‌شود.
 * 3. اجرای فشرده: کلیک‌ها در ابتدای سایکل و با فاصله بسیار کم انجام می‌شوند.
 * 4. اعتبارسنجی: جلوگیری از تنظیماتی که باعث تداخل کلیک‌ها در یک سایکل می‌شود.
 */
class ClickEngine(
    private val cycleDurationMs: Long,
    private val clickSpeedMs: Long,
    private val clicksPerCycle: Int,
    /** زمان شروع مطلق بر حسب SystemClock.elapsedRealtime(). اگر 0 باشد یعنی همین حالا. */
    private val targetStartElapsedMs: Long = 0L,
    private val onCycleComplete: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var isRunning = false
    @Volatile private var generation = 0
    @Volatile private var markerX = 0f
    @Volatile private var markerY = 0f

    private var epochStart = 0L
    private var cycleIndex = 0L

    private val cycleHistory: Queue<String> = LinkedList()

    // ---------- API عمومی ----------

    fun start(x: Float, y: Float): Boolean {
        // ۱. اعتبارسنجی ورودی‌ها
        if (clicksPerCycle <= 0) return false
        if (cycleDurationMs <= 0) return false
        if (clickSpeedMs < 0) return false
        
        // اگر مجموع زمان کلیک‌ها از مدت سایکل بیشتر باشد، کلیک‌ها با هم تداخل پیدا می‌کنند
        if (clicksPerCycle > 1 && (clicksPerCycle - 1) * clickSpeedMs >= cycleDurationMs) {
            return false 
        }

        if (isRunning) return false
        
        markerX = x
        markerY = y

        // ۲. تعیین مبدأ مطلق زمان
        val now = SystemClock.elapsedRealtime()
        epochStart = if (targetStartElapsedMs > now) targetStartElapsedMs else now
        
        cycleIndex = 0L
        isRunning = true
        val myGen = ++generation

        // ۳. شروع اولین چرخه
        scheduleCycle(myGen)
        return true
    }

    fun stop() {
        isRunning = false
        generation++ // تمام تسک‌های در جریان فعلی با این تغییر باطل می‌شوند
        handler.removeCallbacksAndMessages(null)
    }

    fun updateMarker(x: Float, y: Float) {
        markerX = x
        markerY = y
    }

    fun isRunning(): Boolean = isRunning

    fun history(): List<String> = cycleHistory.toList()

    // ---------- منطق اصلی چرخه ----------

    private fun scheduleCycle(myGen: Int) {
        if (!isRunning || myGen != generation) return

        val cycleStart = cycleStartElapsed(cycleIndex)
        val now = SystemClock.elapsedRealtime()
        val waitUntilStart = cycleStart - now

        if (waitUntilStart > 0) {
            // هنوز زمان شروع سایکل نرسیده است -> صبر کن
            handler.postDelayed({
                if (!isRunning || myGen != generation) return@postDelayed
                runClicksOfCycle(myGen, cycleStart)
            }, waitUntilStart)
        } else {
            // زمان شروع گذشته است (یا همین الان است) -> بلافاصله اجرا کن
            runClicksOfCycle(myGen, cycleStart)
        }
    }

    private fun runClicksOfCycle(myGen: Int, cycleStart: Long) {
        if (!isRunning || myGen != generation) return

        val x = markerX
        val y = markerY
        val actualStart = SystemClock.elapsedRealtime()

        // اجرای سری کلیک‌ها در یک ترد پس‌زمینه برای دقت بالا و عدم بلاک کردن UI
        ClickAccessibilityService.instance?.tapMany(
            x = x,
            y = y,
            count = clicksPerCycle,
            intervalMs = clickSpeedMs
        ) { doneCount -> 
            // وقتی تمام کلیک‌های این سایکل تمام شد، بازگرد به Main Thread برای لاگ و شروع بعدی
            handler.post {
                if (isRunning && myGen == generation) {
                    onClicksDone(myGen, cycleStart, actualStart, doneCount)
                }
            }
        }
    }

    private fun onClicksDone(myGen: Int, cycleStart: Long, actualStart: Long, doneCount: Int) {
        if (!isRunning || myGen != generation) return

        val endTime = SystemClock.elapsedRealtime()

        // لاگ دقیق برای عیب‌یابی
        // drift: اختلاف زمان شروع واقعی با زمانی که باید شروع می‌شد (مثبت = دیر شروع شده)
        val drift = actualStart - cycleStart
        
        val entry = String.format(
            Locale.US,
            "C:%d | %s | %d | D:%+dms",
            cycleIndex + 1,
            formatClock(cycleStart),
            doneCount,
            drift
        )
        
        addToHistory(entry)

        // آماده‌سازی برای سایکل بعدی
        cycleIndex++
        scheduleCycle(myGen)
    }

    private fun addToHistory(entry: String) {
        if (cycleHistory.size >= 20) cycleHistory.poll()
        cycleHistory.add(entry)
        onCycleComplete(cycleHistory.joinToString("\n"))
    }

    /** تبدیل میلی‌ثانیهِ SystemClock به فرمت ساعت دیواری (HH:mm:ss.SSS) */
    private fun formatClock(elapsedMs: Long): String {
        val wallTime = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - elapsedMs)
        val ms = wallTime % 1000
        val s = (wallTime / 1000) % 60
        val m = (wallTime / 60000) % 60
        val h = (wallTime / 3600000) % 24
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, s, ms)
    }

    private fun cycleStartElapsed(n: Long): Long = epochStart + n * cycleDurationMs
}
