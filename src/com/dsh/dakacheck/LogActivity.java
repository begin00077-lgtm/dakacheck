package com.dsh.dakacheck;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 三级页面：系统日志（无障碍上线/掉线、读到页面、强制重绑等）。 */
public class LogActivity extends Activity {
    private TextView body;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(build());
        reload();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(16);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = new TextView(this);
        back.setText("‹ 返回");
        back.setTextSize(16f);
        back.setTextColor(Color.parseColor("#2E7D32"));
        back.setPadding(0, (int) dp(6), (int) dp(16), (int) dp(6));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText("系统日志");
        title.setTextSize(22f);
        title.setTextColor(Color.parseColor("#111111"));
        title.setTypeface(null, Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clear = new TextView(this);
        clear.setText("清空");
        clear.setTextSize(15f);
        clear.setTextColor(Color.parseColor("#8B0000"));
        clear.setPadding((int) dp(12), (int) dp(6), 0, (int) dp(6));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Timeline.clear(LogActivity.this);
                reload();
                Toast.makeText(LogActivity.this, "已清空", Toast.LENGTH_SHORT).show();
            }
        });
        bar.addView(clear);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        body = new TextView(this);
        body.setTextSize(13f);
        body.setTextColor(Color.parseColor("#222222"));
        body.setLineSpacing(0f, 1.35f);
        body.setTypeface(Typeface.MONOSPACE);
        int p = (int) dp(12);
        body.setPadding(p, p, p, p);
        body.setBackgroundColor(Color.WHITE);
        scroll.addView(body);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = (int) dp(12);
        root.addView(scroll, slp);

        return root;
    }

    private void reload() {
        body.setText(Timeline.dump(this));
    }
}
