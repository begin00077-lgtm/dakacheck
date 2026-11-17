package com.dsh.dakacheck;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 考勤获取成功提示：屏幕中央弹窗，全屏灰色半透明背景，1.5 秒后自动关闭。
 */
public class KaoqinOkActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float d = getResources().getDisplayMetrics().density;

        // 全屏灰色半透明底
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x99000000);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 中央卡片
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding((int) (34 * d), (int) (28 * d), (int) (34 * d), (int) (28 * d));

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(16 * d);
        bg.setColor(0xFFFFFFFF);
        card.setBackground(bg);
        card.setElevation(12 * d);

        TextView title = new TextView(this);
        title.setText("考勤信息获取成功");
        title.setTextSize(19f);
        title.setTextColor(0xFF1B1B1B);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);

        TextView sub = new TextView(this);
        sub.setText("已读取本月考勤情况");
        sub.setTextSize(14f);
        sub.setTextColor(0xFF666666);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, (int) (10 * d), 0, 0);

        card.addView(title);
        card.addView(sub);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        root.addView(card, lp);

        setContentView(root);

        new android.os.Handler(getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 2000);
    }
}
