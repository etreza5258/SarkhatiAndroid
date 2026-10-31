package com.sarkhati.android;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private TextView clockText, status;
    private EditText timeInput, countInput, intervalInput;
    private Button syncButton, startButton, stopButton;

    private volatile long ntpOffset = 0;
    private volatile boolean synced = false;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread worker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(25, 25, 25, 25);

        TextView title = new TextView(this);
        title.setText("ربات سرخطی - ساعت NTP");
        title.setTextSize(23);
        layout.addView(title);

        clockText = new TextView(this);
        clockText.setTextSize(18);
        layout.addView(clockText);

        syncButton = new Button(this);
        syncButton.setText("همگام‌سازی با ساعت NTP");
        layout.addView(syncButton);

        timeInput = createField(layout, "ساعت اجرا مثال 08:44:59.900");
        countInput = createField(layout, "تعداد اجرا مثال 10");
        intervalInput = createField(layout, "فاصله میلی‌ثانیه مثال 100");

        startButton = new Button(this);
        startButton.setText("شروع زمان‌بندی آزمایشی");
        layout.addView(startButton);

        stopButton = new Button(this);
        stopButton.setText("توقف");
        layout.addView(stopButton);

        status = new TextView(this);
        status.setText("وضعیت: ابتدا ساعت NTP را همگام کنید.");
        status.setTextSize(16);
        layout.addView(status);

        setContentView(layout);

        syncButton.setOnClickListener(v -> syncNtp());

        startButton.setOnClickListener(v -> startSchedule());

        stopButton.setOnClickListener(v -> {
            running.set(false);
            status.setText("زمان‌بندی متوقف شد.");
        });

        updateClock();
    }

    private EditText createField(LinearLayout layout, String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        layout.addView(field);
        return field;
    }

    private void syncNtp() {
        status.setText("در حال دریافت زمان از NTP...");

        new Thread(() -> {
            try {
                long bestOffset = 0;
                long bestDelay = Long.MAX_VALUE;

                for (int i = 0; i < 5; i++) {
                    long t1 = System.currentTimeMillis();

                    long serverTime = getNtpTime();

                    long t2 = System.currentTimeMillis();
                    long delay = t2 - t1;

                    long offset = serverTime - (t1 + t2) / 2;

                    if (delay < bestDelay) {
                        bestDelay = delay;
                        bestOffset = offset;
                    }
                }

                ntpOffset = bestOffset;
                synced = true;

                runOnUiThread(() -> {
                    status.setText(
                        "همگام‌سازی موفق شد.\nاختلاف ساعت: "
                        + ntpOffset + " میلی‌ثانیه"
                    );
                    updateClock();
                });

            } catch (Exception e) {
                synced = false;
                runOnUiThread(() ->
                    status.setText("خطا در دریافت زمان NTP: " + e.getMessage())
                );
            }
        }).start();
    }

    private long getNtpTime() throws Exception {
        String host = "time.google.com";

        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(3000);

        try {
            byte[] data = new byte[48];
            data[0] = 0x1B;

            InetAddress address = InetAddress.getByName(host);

            DatagramPacket request =
                new DatagramPacket(data, data.length, address, 123);

            socket.send(request);

            DatagramPacket response =
                new DatagramPacket(data, data.length);

            socket.receive(response);

            long seconds = 0;
            long fraction = 0;

            for (int i = 40; i < 44; i++) {
                seconds = (seconds << 8) | (data[i] & 0xffL);
            }

            for (int i = 44; i < 48; i++) {
                fraction = (fraction << 8) | (data[i] & 0xffL);
            }

            long milliseconds =
                (seconds - 2208988800L) * 1000L
                + (fraction * 1000L) / 0x100000000L;

            if (milliseconds <= 0) {
                throw new Exception("زمان دریافتی معتبر نیست");
            }

            return milliseconds;

        } finally {
            socket.close();
        }
    }

    private long getNtpNow() {
        return System.currentTimeMillis() + ntpOffset;
    }

    private void updateClock() {
        SimpleDateFormat format =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

        String phoneTime = format.format(new Date());

        String ntpTime = synced
            ? format.format(new Date(getNtpNow()))
            : "همگام‌سازی نشده";

        clockText.setText(
            "ساعت گوشی: " + phoneTime +
            "\nساعت NTP: " + ntpTime
        );
    }

    private void startSchedule() {
        if (!synced) {
            status.setText("ابتدا ساعت NTP را همگام کنید.");
            return;
        }

        try {
            String time = timeInput.getText().toString().trim();

            int count = Integer.parseInt(
                countInput.getText().toString().trim()
            );

            long interval = Long.parseLong(
                intervalInput.getText().toString().trim()
            );

            if (count < 1 || interval < 1) {
                throw new Exception("تعداد و فاصله باید مثبت باشند.");
            }

            String[] parts = time.split("[:.]");

            if (parts.length != 4) {
                throw new Exception("فرمت ساعت صحیح نیست.");
            }

            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            int second = Integer.parseInt(parts[2]);
            int milli = Integer.parseInt(parts[3]);

            if (hour > 23 || minute > 59 ||
                second > 59 || milli > 999) {
                throw new Exception("ساعت واردشده معتبر نیست.");
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

            final long targetNtp = target;

            running.set(false);
            running.set(true);

            worker = new Thread(() -> {
                long startElapsed = SystemClock.elapsedRealtime();
                long targetElapsed =
                    startElapsed + (targetNtp - getNtpNow());

                while (running.get() &&
                    SystemClock.elapsedRealtime() < targetElapsed) {
                    SystemClock.sleep(1);
                }

                if (!running.get()) return;

                for (int i = 1; i <= count && running.get(); i++) {
                    final int number = i;

                    runOnUiThread(() ->
                        status.setText(
                            "اجرای آزمایشی شماره " + number +
                            " از " + count
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
            });

            worker.start();

            status.setText("زمان‌بندی ثبت شد.");

        } catch (Exception e) {
            status.setText("خطا: " + e.getMessage());
        }
    }
}