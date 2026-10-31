package com.sarkhati.android;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.content.SharedPreferences;

public class OverlayService extends Service {

    private WindowManager windowManager;
    private View marker;
    private WindowManager.LayoutParams params;
    private float touchX;
    private float touchY;
    private int startX;
    private int startY;

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager)
                getSystemService(Context.WINDOW_SERVICE);

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

        params = new WindowManager.LayoutParams(
                65,
                65,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 150;
        params.y = 300;

        marker.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    startX = params.x;
                    startY = params.y;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    params.x = startX +
                            (int) (event.getRawX() - touchX);
                    params.y = startY +
                            (int) (event.getRawY() - touchY);

                    windowManager.updateViewLayout(marker, params);
                    return true;

                case MotionEvent.ACTION_UP:
                    savePosition();
                    return true;
            }
            return false;
        });

        windowManager.addView(marker, params);
    }

    private void savePosition() {
        SharedPreferences prefs =
                getSharedPreferences("marker_position", MODE_PRIVATE);

        prefs.edit()
                .putInt("x", params.x)
                .putInt("y", params.y)
                .apply();
    }

    @Override
    public void onDestroy() {
        if (marker != null && windowManager != null) {
            windowManager.removeView(marker);
            marker = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}