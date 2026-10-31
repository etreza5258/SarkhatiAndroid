package com.sarkhati.android;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private TextView clockText;
    private TextView status;
    private TextView countdownText;
    private TextView logText;

    private EditText timeInput;
    private EditText countInput;
    private EditText intervalInput;

    private Button syncButton;

    private volatile boolean synced = false;
    private volatile long anchorNtp;
    private volatile long anchorElapsed;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SharedPreferences logs;

    private long targetElapsed;
    private int totalCount;
    private long interval;

    private Thread scheduleThread;

    private final Runnable clockUpdater = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 200);
        }
    };

    private final Runnable countdownUpdater = new Runnable() {
        @Override
        public void run() {

            if (!running.get()) {
                return;
            }

            long remaining = Math.max(
                    0,
                    targetElapsed - SystemClock.elapsedRealtime()
            );

            countdownText.setText(
                    "زمان باقی‌مانده: " + formatTime(remaining)
            );

            if (remaining > 0) {
                handler.postDelayed(this, 20);
            } else {
                countdownText.setText("زمان شروع فرا رسید.");
            }
        }
    };

    private String formatTime(long millis) {

        long hours = millis / 3600000;
        long minutes = (millis / 60000) % 60;
        long seconds = (millis / 1000) % 60;
        long milliseconds = millis % 1000;

        return String.format(
                Locale.US,
                "%02d : %02d : %02d : %03d",
                hours,
                minutes,
                seconds,
                milliseconds
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        logs = getSharedPreferences(
                "execution_logs",
                MODE_PRIVATE
        );

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(25, 25, 25, 25);

        TextView title = new TextView(this);
        title.setText("زمان‌بندی سرخطی");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        clockText = new TextView(this);
        clockText.setTextSize(17);
        clockText.setPadding(0, 15, 0, 15);
        layout.addView(clockText);

        syncButton = new Button(this);
        syncButton.setText("همگام‌سازی ساعت اینترنت");
        layout.addView(syncButton);

        Button showMarker = new Button(this);
        showMarker.setText("نمایش نشانگر شناور");
        layout.addView(showMarker);

        Button hideMarker = new Button(this);
        hideMarker.setText("مخفی‌کردن نشانگر");
        layout.addView(hideMarker);

        timeInput = createField(
                layout,
                "ساعت اجرا مثال 08:44:59.900"
        );

        countInput = createField(
                layout,
                "تعداد اجرا مثال 10"
        );

        intervalInput = createField(
                layout,
                "فاصله میلی‌ثانیه مثال 100"
        );

        countdownText = new TextView(this);
        countdownText.setText("زمان باقی‌مانده: --");
        countdownText.setTextSize(20);
        countdownText.setPadding(0, 15, 0, 15);
        layout.addView(countdownText);

        Button startButton = new Button(this);
        startButton.setText("شروع زمان‌بندی");
        layout.addView(startButton);

        Button stopButton = new Button(this);
        stopButton.setText("توقف");
        layout.addView(stopButton);

        status = new TextView(this);
        status.setText("وضعیت: آماده");
        status.setTextSize(17);
        status.setPadding(0, 15, 0, 15);
        layout.addView(status);

        TextView reportTitle = new TextView(this);
        reportTitle.setText("گزارش اجراها");
        reportTitle.setTextSize(22);
        reportTitle.setPadding(0, 20, 0, 10);
        layout.addView(reportTitle);

        logText = new TextView(this);
        logText.setTextSize(14);
        logText.setPadding(5, 10, 5, 10);
        layout.addView(logText);

        Button clearLogs = new Button(this);
        clearLogs.setText("پاک‌کردن گزارش‌ها");
        layout.addView(clearLogs);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);

        syncButton.setOnClickListener(v -> syncHttps());

        showMarker.setOnClickListener(v -> showFloatingMarker());

        hideMarker.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            status.setText("نشانگر مخفی شد.");
        });

        startButton.setOnClickListener(v -> startSchedule());

        stopButton.setOnClickListener(v -> stopSchedule());

        clearLogs.setOnClickListener(v -> {
            logs.edit().clear().apply();
            refreshLogs();
            status.setText("گزارش‌ها پاک شدند.");
        });

        refreshLogs();
        handler.post(clockUpdater);
    }

    private EditText createField(
            LinearLayout layout,
            String hint
    ) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        layout.addView(field);
        return field;
    }

    private String currentTime() {
        return new SimpleDateFormat(
                "yyyy/MM/dd HH:mm:ss.SSS",
                Locale.US
        ).format(new Date());
    }

    private void addLog(String message) {

        String entry = currentTime() + " - " + message;

        String old = logs.getString("entries", "");
        String updated = entry + "\n" + old;

        if (updated.length() > 20000) {
            updated = updated.substring(0, 20000);
        }

        logs.edit().putString("entries", updated).apply();

        runOnUiThread(this::refreshLogs);
    }

    private void refreshLogs() {

        if (logText == null) {
            return;
        }

        String entries = logs.getString(
                "entries",
                "هنوز گزارشی ثبت نشده است."
        );

        logText.setText(entries);
    }

    private void showFloatingMarker() {

        if (!Settings.canDrawOverlays(this)) {

            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );

            startActivity(intent);

            status.setText(
                    "مجوز نمایش روی برنامه‌های دیگر را فعال کن."
            );

            return;
        }

        startService(new Intent(this, OverlayService.class));

        status.setText("نشانگر شناور نمایش داده شد.");
        addLog("نشانگر شناور نمایش داده شد.");
    }

    private long getHttpsTime() throws Exception {

        URL url = new URL(
                "https://www.google.com/generate_204"
        );

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("HEAD");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setUseCaches(false);

        try {

            connection.connect();

            long time = connection.getDate();

            if (time <= 0) {
                throw new Exception("زمان سرور دریافت نشد.");
            }

            return time;

        } finally {
            connection.disconnect();
        }
    }

    private void syncHttps() {

        status.setText("در حال دریافت زمان اینترنت...");
        syncButton.setEnabled(false);

        new Thread(() -> {

            try {

                long bestOffset = 0;
                long bestDelay = Long.MAX_VALUE;

                for (int i = 0; i < 5; i++) {

                    long t1 = System.currentTimeMillis();

                    long serverTime = getHttpsTime();

                    long t2 = System.currentTimeMillis();

                    long delay = t2 - t1;

                    long offset =
                            serverTime - (t1 + t2) / 2;

                    if (delay < bestDelay) {
                        bestDelay = delay;
                        bestOffset = offset;
                    }
                }

                final long finalOffset = bestOffset;
                final long finalDelay = bestDelay;

                anchorNtp =
                        System.currentTimeMillis() + finalOffset;

                anchorElapsed = SystemClock.elapsedRealtime();

                synced = true;

                runOnUiThread(() -> {

                    syncButton.setEnabled(true);

                    status.setText(
                            "همگام‌سازی انجام شد.\n" +
                            "اختلاف تخمینی: " +
                            finalOffset + " میلی‌ثانیه\n" +
                            "کمترین تأخیر: " +
                            finalDelay + " میلی‌ثانیه"
                    );

                    addLog("همگام‌سازی ساعت اینترنت انجام شد.");
                });

            } catch (Exception e) {

                synced = false;

                runOnUiThread(() -> {

                    syncButton.setEnabled(true);

                    status.setText(
                            "خطای دریافت زمان: " +
                            e.getMessage()
                    );

                    addLog("خطا در همگام‌سازی ساعت.");
                });
            }

        }).start();
    }

    private long getNtpNow() {

        return anchorNtp +
                (SystemClock.elapsedRealtime() - anchorElapsed);
    }

    private void updateClock() {

        SimpleDateFormat format =
                new SimpleDateFormat(
                        "HH:mm:ss.SSS",
                        Locale.US
                );

        String phoneTime = format.format(
                new Date(System.currentTimeMillis())
        );

        String internetTime = synced
                ? format.format(new Date(getNtpNow()))
                : "همگام‌سازی نشده";

        clockText.setText(
                "ساعت گوشی: " + phoneTime +
                "\nساعت اینترنت: " + internetTime
        );
    }

    private void stopSchedule() {

        if (running.getAndSet(false)) {

            handler.removeCallbacks(countdownUpdater);

            OverlayService.stopCountdown(this);

            if (scheduleThread != null) {
                scheduleThread.interrupt();
            }

            countdownText.setText("زمان‌بندی متوقف شد.");
            status.setText("زمان‌بندی متوقف شد.");

            addLog("زمان‌بندی توسط کاربر متوقف شد.");

        } else {

            status.setText("زمان‌بندی فعالی وجود ندارد.");
        }
    }

    private void startSchedule() {

        if (!synced) {
            status.setText(
                    "ابتدا ساعت اینترنت را همگام کنید."
            );
            return;
        }

        if (running.get()) {
            status.setText(
                    "ابتدا زمان‌بندی قبلی را متوقف کنید."
            );
            return;
        }

        try {

            String[] parts = timeInput
                    .getText()
                    .toString()
                    .trim()
                    .split("[:.]");

            if (parts.length != 4) {
                throw new Exception(
                        "فرمت ساعت باید HH:mm:ss.SSS باشد."
                );
            }

            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            int second = Integer.parseInt(parts[2]);
            int milli = Integer.parseInt(parts[3]);

            totalCount = Integer.parseInt(
                    countInput.getText().toString().trim()
            );

            interval = Long.parseLong(
                    intervalInput.getText().toString().trim()
            );

            if (hour < 0 || hour > 23 ||
                    minute < 0 || minute > 59 ||
                    second < 0 || second > 59 ||
                    milli < 0 || milli > 999 ||
                    totalCount < 1 ||
                    interval < 1) {

                throw new Exception(
                        "مقادیر واردشده معتبر نیستند."
                );
            }

            Calendar calendar = Calendar.getInstance();

            calendar.setTimeInMillis(getNtpNow());

            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, second);
            calendar.set(Calendar.MILLISECOND, milli);

            long target = calendar.getTimeInMillis();

            if (target <= getNtpNow()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                target = calendar.getTimeInMillis();
            }

            targetElapsed =
                    anchorElapsed + (target - anchorNtp);

            long remaining =
                    targetElapsed - SystemClock.elapsedRealtime();

            if (!Settings.canDrawOverlays(this)) {
                throw new Exception(
                        "ابتدا مجوز نمایش شناور را فعال کن."
                );
            }

            running.set(true);

            Intent serviceIntent =
                    new Intent(this, OverlayService.class);

            serviceIntent.setAction("START_COUNTDOWN");
            serviceIntent.putExtra("remaining", remaining);

            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            addLog(
                    "زمان‌بندی ثبت شد؛ تعداد اجرا: " +
                    totalCount +
                    "؛ فاصله: " +
                    interval + " میلی‌ثانیه"
            );

            status.setText("زمان‌بندی ثبت شد.");

            handler.removeCallbacks(countdownUpdater);
            handler.post(countdownUpdater);

            scheduleThread = new Thread(() -> {

                try {

                    while (running.get() &&
                            SystemClock.elapsedRealtime() < targetElapsed) {

                        SystemClock.sleep(1);
                    }

                    if (!running.get()) {
                        return;
                    }

                    addLog("زمان شروع آزمایش فرا رسید.");

                    for (int i = 1;
                         i <= totalCount && running.get();
                         i++) {

                        final int number = i;

                        runOnUiThread(() -> {

                            status.setText(
                                    "اجرای آزمایشی " +
                                    number + " از " + totalCount
                            );

                            addLog(
                                    "اجرای آزمایشی شماره " + number
                            );
                        });

                        if (i < totalCount) {
                            SystemClock.sleep(interval);
                        }
                    }

                    if (running.get()) {

                        running.set(false);

                        runOnUiThread(() -> {

                            handler.removeCallbacks(
                                    countdownUpdater
                            );

                            OverlayService.stopCountdown(
                                    MainActivity.this
                            );

                            countdownText.setText(
                                    "زمان‌بندی تمام شد."
                            );

                            status.setText(
                                    "زمان‌بندی تمام شد."
                            );

                            addLog(
                                    "پایان زمان‌بندی."
                            );
                        });
                    }

                } catch (Exception e) {

                    if (running.get()) {

                        running.set(false);

                        runOnUiThread(() -> {

                            OverlayService.stopCountdown(
                                    MainActivity.this
                            );

                            status.setText(
                                    "خطا در زمان‌بندی: " +
                                    e.getMessage()
                            );

                            addLog("خطا در زمان‌بندی.");
                        });
                    }
                }
            });

            scheduleThread.start();

        } catch (Exception e) {

            running.set(false);

            status.setText("خطا: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacks(clockUpdater);
        handler.removeCallbacks(countdownUpdater);

        if (scheduleThread != null &&
                !running.get()) {
            scheduleThread.interrupt();
        }

        super.onDestroy();
    }
}