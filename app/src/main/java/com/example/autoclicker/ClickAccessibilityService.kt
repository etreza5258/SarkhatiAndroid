package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ClickAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ClickAccessibilityService? = null
            private set
    }

    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun tap(x: Float, y: Float, durationMs: Long = 1L, onDone: (Boolean) -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(1L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                main.post { onDone(true) }
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                main.post { onDone(false) }
            }
        }, null)

        if (!dispatched) {
            main.post { onDone(false) }
        }
    }

    /**
     * اجرای چندین کلیک پشت‌سرهم با فاصله زمانی مشخص (مخصوص ClickEngine)
     */
    fun tapMany(x: Float, y: Float, count: Int, intervalMs: Long, onDone: (Int) -> Unit) {
        Thread {
            var completed = 0
            try {
                for (i in 0 until count) {
                    if (instance == null) break

                    val latch = CountDownLatch(1)
                    var success = false

                    // استفاده از متد tap برای هر کلیک تک‌گانه
                    tap(x, y) { isOk ->
                        success = isOk
                        latch.countDown()
                    }

                    // منتظر ماندن برای پاسخ سیستم به Gesture
                    latch.await(500, TimeUnit.MILLISECONDS)

                    if (success) {
                        completed++
                    } else {
                        break
                    }

                    // فاصله بین کلیک‌ها در طول همان سایکل
                    if (i < count - 1 && intervalMs > 0) {
                        SystemClock.sleep(intervalMs)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                main.post {
                    onDone(completed)
                }
            }
        }.start()
    }
}
