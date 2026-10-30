package com.sarkhati.android;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private EditText timeInput;
    private EditText countInput;
    private EditText intervalInput;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(25, 30, 25, 30);

        TextView title = new TextView(this);
        title.setText("ربات سرخطی بورس");
        title.setTextSize(24);
        layout.addView(title);

        timeInput = createField(layout, "ساعت اجرا مثال 08:44:59");
        countInput = createField(layout, "تعداد درخواست");
        intervalInput = createField(layout, "فاصله زمانی میلی‌ثانیه");

        Button start = new Button(this);
        start.setText("شروع");
        layout.addView(start);

        Button stop = new Button(this);
        stop.setText("توقف");
        layout.addView(stop);

        status = new TextView(this);
        status.setText("وضعیت: آماده");
        layout.addView(status);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                status.setText("تنظیمات ثبت شد؛ اتصال کارگزاری هنوز فعال نیست.");
            }
        });

        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                status.setText("برنامه متوقف شد.");
            }
        });

        setContentView(layout);
    }

    private EditText createField(LinearLayout layout, String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        layout.addView(field);
        return field;
    }
}