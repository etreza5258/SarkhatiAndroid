package com.sarkhati.android;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Locale;

public class OverlayService extends Service {

    private static final String CHANNEL_ID = "sarkhati_countdown";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager windowManager;
    private View marker;
    private TextView countdownView;

    private WindowManager.LayoutParams markerParams;
    private WindowManager.LayoutParams countdownParams;

    private float touchX;
    private float touchY;
    private int startX;
    private int startY;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private long targetElapsed;
    private boolean countdownRunning = false;

    private final Runnable countdownUpdater = new Runnable() {
        @Override
        public void run() {

            if (!countdownRunning || countdownView == null) {
                return;
            }

            long remaining = Math.max(
                    0,
                    targetElapsed - SystemClock.elapsedRealtime()
            );

            long hours = remaining / 3600000;
            long minutes = (remaining / 60000) % 60;
            long seconds = (remaining / 1000) % 60;
            long millis = remaining % 1000;

            countdownView.setText(String.format(
                    Locale.US,
                    "%02d : %02d : %02d : %03d",
                    hours, minutes, seconds, millis
            ));

            if (remaining == 0) {
                countdownRunning = false;
                countdownView.setText("زمان شروع فرا رسید");
                stopForeground(true);
                return;
            }

            handler.postDelayed(this, 20);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager)
                getSystemService(Context.WINDOW_SERVICE);

        createNotificationChannel();
        createMarker();
        createCountdown();
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "شمارش معکوس سرخطی",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription(
                    "نمایش وضعیت شمارش معکوس"
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("شمارش معکوس سرخطی")
                .setContentText("شمارش معکوس فعال است.")
                .setOngoing(true)
                .build();
    }

    private void createMarker() {

        marker = new TextView(this);

        ((TextView) marker).setText("●");
        ((TextView) marker).setTextSize(36);
        ((TextView) marker).setTextColor(Color.RED);
        ((TextView) marker).setGravity(Gravity.CENTER);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(0x33FF0000);
        background.setStroke(3, Color.RED);

        marker.setBackground(background);

        markerParams = new WindowManager.LayoutParams(
                65,
                65,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        markerParams.gravity = Gravity.TOP | Gravity.LEFT;

        SharedPreferences prefs =
                getSharedPreferences("marker_position", MODE_PRIVATE);

        markerParams.x = prefs.getInt("x", 150);
        markerParams.y = prefs.getInt("y", 300);

        marker.setOnTouchListener((view, event) -> {

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                    touchX = event.getRawX();
                    touchY = event.getRawY();

                    startX = markerParams.x;
                    startY = markerParams.y;

                    return true;

                case MotionEvent.ACTION_MOVE:

                    markerParams.x = startX +
                            (int) (event.getRawX() - touchX);

                    markerParams.y = startY +
                            (int) (event.getRawY() - touchY);

                    windowManager.updateViewLayout(
                            marker,
                            markerParams
                    );

                    return true;

                case MotionEvent.ACTION_UP:
                    savePosition();
                    return true;
            }

            return false;
        });

        windowManager.addView(marker, markerParams);
    }

    private void createCountdown() {

        countdownView = new TextView(this);

        countdownView.setText("00 : 00 : 00 : 000");
        countdownView.setTextSize(19);
        countdownView.setTextColor(Color.WHITE);
        countdownView.setGravity(Gravity.CENTER);
        countdownView.setPadding(18, 12, 18, 12);

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xEE202020);
        background.setCornerRadius(18);

        countdownView.setBackground(background);

        countdownParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        countdownParams.gravity =
                Gravity.TOP | Gravity.CENTER_HORIZONTAL;

        countdownParams.y = 100;

        windowManager.addView(countdownView, countdownParams);
    }

    private void savePosition() {

        SharedPreferences prefs =
                getSharedPreferences("marker_position", MODE_PRIVATE);

        prefs.edit()
                .putInt("x", markerParams.x)
                .putInt("y", markerParams.y)
                .apply();
    }

    public static void setCountdown(
            Context context,
            long remainingMillis
    ) {
        Intent intent = new Intent(context, OverlayService.class);
        intent.setAction("START_COUNTDOWN");
        intent.putExtra("remaining", remainingMillis);

        context.startService(intent);
    }

    public static void stopCountdown(Context context) {

        Intent intent = new Intent(context, OverlayService.class);
        intent.setAction("STOP_COUNTDOWN");

        context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        if ("START_COUNTDOWN".equals(intent.getAction())) {

            long remaining =
                    intent.getLongExtra("remaining", 0);

            if (remaining <= 0) {
                return START_NOT_STICKY;
            }

            startForeground(
                    NOTIFICATION_ID,
                    createNotification()
            );

            targetElapsed =
                    SystemClock.elapsedRealtime() + remaining;

            countdownRunning = true;

            handler.removeCallbacks(countdownUpdater);
            handler.post(countdownUpdater);
        }

        if ("STOP_COUNTDOWN".equals(intent.getAction())) {

            countdownRunning = false;
            handler.removeCallbacks(countdownUpdater);

            if (countdownView != null) {
                countdownView.setText("متوقف شد");
            }

            stopForeground(true);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        countdownRunning = false;
        handler.removeCallbacks(countdownUpdater);

        if (marker != null) {
            windowManager.removeView(marker);
            marker = null;
        }

        if (countdownView != null) {
            windowManager.removeView(countdownView);
            countdownView = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}