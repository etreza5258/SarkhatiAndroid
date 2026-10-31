package com.sarkhati.android;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private TextView clockText, status;
    private EditText timeInput, countInput, intervalInput;
    private Button syncButton;

    private volatile long ntpOffset = 0;
    private volatile boolean synced = false;
    private volatile long anchorNtp;
    private volatile long anchorElapsed;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable clockUpdater = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 200);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(25, 25, 25, 25);

        TextView title = new TextView(this);
        title.setText("زمان‌بندی سرخطی");
        title.setTextSize(22);
        layout.addView(title);

        clockText = new TextView(this);
        clockText.setTextSize(18);
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

        timeInput = createField(layout, "ساعت اجرا مثال 08:44:59.900");
        countInput = createField(layout, "تعداد اجرا مثال 10");
        intervalInput = createField(layout, "فاصله میلی‌ثانیه مثال 100");

        Button startButton = new Button(this);
        startButton.setText("شروع زمان‌بندی آزمایشی");
        layout.addView(startButton);

        Button stopButton = new Button(this);
        stopButton.setText("توقف");
        layout.addView(stopButton);

        status = new TextView(this);
        status.setText("وضعیت: آماده");
        status.setTextSize(16);
        layout.addView(status);

        setContentView(layout);

        syncButton.setOnClickListener(v -> syncHttps());

        showMarker.setOnClickListener(v -> showFloatingMarker());

        hideMarker.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            status.setText("نشانگر مخفی شد.");
        });

        startButton.setOnClickListener(v -> startSchedule());

        stopButton.setOnClickListener(v -> {
            running.set(false);
            status.setText("زمان‌بندی متوقف شد.");
        });

        handler.post(clockUpdater);
    }

    private EditText createField(LinearLayout layout, String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        layout.addView(field);
        return field;
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
    }

    private long getHttpsTime() throws Exception {
        URL url = new URL("https://www.google.com/generate_204");

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
                throw new Exception("زمان سرور دریافت نشد");
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
                    long offset = serverTime - (t1 + t2) / 2;

                    if (delay < bestDelay) {
                        bestDelay = delay;
                        bestOffset = offset;
                    }
                }

                final long finalOffset = bestOffset;
                final long finalDelay = bestDelay;

                ntpOffset = finalOffset;
                anchorNtp = System.currentTimeMillis() + finalOffset;
                anchorElapsed = SystemClock.elapsedRealtime();
                synced = true;

                runOnUiThread(() -> {
                    syncButton.setEnabled(true);
                    status.setText(
                            "همگام‌سازی انجام شد.\n" +
                            "اختلاف تخمینی: " + finalOffset + " میلی‌ثانیه\n" +
                            "کمترین تأخیر: " + finalDelay + " میلی‌ثانیه"
                    );
                });

            } catch (Exception e) {
                synced = false;

                runOnUiThread(() -> {
                    syncButton.setEnabled(true);
                    status.setText("خطای دریافت زمان: " + e.getMessage());
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
                new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

        String phoneTime =
                format.format(new Date(System.currentTimeMillis()));

        String internetTime = synced
                ? format.format(new Date(getNtpNow()))
                : "همگام‌سازی نشده";

        clockText.setText(
                "ساعت گوشی: " + phoneTime +
                "\nساعت اینترنت: " + internetTime
        );
    }

    private void startSchedule() {
        if (!synced) {
            status.setText("ابتدا ساعت اینترنت را همگام کنید.");
            return;
        }

        try {
            String[] parts =
                    timeInput.getText().toString().trim().split("[:.]");

            if (parts.length != 4) {
                throw new Exception("فرمت ساعت باید HH:mm:ss.SSS باشد.");
            }

            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            int second = Integer.parseInt(parts[2]);
            int milli = Integer.parseInt(parts[3]);

            int count = Integer.parseInt(
                    countInput.getText().toString().trim()
            );

            long interval = Long.parseLong(
                    intervalInput.getText().toString().trim()
            );

            if (hour > 23 || minute > 59 || second > 59 ||
                    milli > 999 || count < 1 || interval < 1) {
                throw new Exception("مقادیر واردشده معتبر نیستند.");
            }

            java.util.Calendar calendar =
                    java.util.Calendar.getInstance();

            calendar.setTimeInMillis(getNtpNow());
            calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
            calendar.set(java.util.Calendar.MINUTE, minute);
            calendar.set(java.util.Calendar.SECOND, second);
            calendar.set(java.util.Calendar.MILLISECOND, milli);

            long target = calendar.getTimeInMillis();

            if (target <= getNtpNow()) {
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1);
                target = calendar.getTimeInMillis();
            }

            final long targetElapsed =
                    anchorElapsed + (target - anchorNtp);

            running.set(false);
            running.set(true);

            new Thread(() -> {
                while (running.get() &&
                        SystemClock.elapsedRealtime() < targetElapsed) {
                    SystemClock.sleep(1);
                }

                if (!running.get()) return;

                for (int i = 1; i <= count && running.get(); i++) {
                    final int number = i;

                    runOnUiThread(() ->
                            status.setText(
                                    "اجرای آزمایشی " + number + " از " + count
                            )
                    );

                    if (i < count) {
                        SystemClock.sleep(interval);
                    }
                }

                running.set(false);

                runOnUiThread(() ->
                        status.setText("زمان‌بندی آزمایشی تمام شد.")
                );
            }).start();

            status.setText("زمان‌بندی آزمایشی ثبت شد.");

        } catch (Exception e) {
            status.setText("خطا: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        running.set(false);
        handler.removeCallbacks(clockUpdater);
        super.onDestroy();
    }
}