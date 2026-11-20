package com.dsh.dakacheck;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 页面基类：提供统一的 dp、卡片、按钮等小工具。 */
public abstract class BasePage {

    protected final Activity host;
    protected final Context ctx;

    protected BasePage(Activity host) {
        this.host = host;
        this.ctx = host;
    }

    /** 构建页面内容（不含导航栏）。 */
    public abstract View build();

    /** 页面被切到时调用。 */
    public void onShown() {
    }

    protected float dp(float v) {
        return v * ctx.getResources().getDisplayMetrics().density;
    }

    protected ScrollView scroll() {
        ScrollView s = new ScrollView(ctx);
        s.setVerticalScrollBarEnabled(false);
        s.setScrollbarFadingEnabled(true);
        s.setFillViewport(true);
        return s;
    }

    protected LinearLayout column(int topPad) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(16);
        root.setPadding(pad, (int) dp(topPad), pad, (int) dp(16));
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));
        return root;
    }

    protected LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    protected TextView section(String t) {
        TextView v = new TextView(ctx);
        v.setText(t);
        v.setTextSize(12.5f);
        v.setTextColor(Color.parseColor("#888888"));
        v.setPadding((int) dp(4), (int) dp(18), 0, (int) dp(6));
        return v;
    }

    protected Button button(String text, View.OnClickListener l) {
        Button b = new Button(ctx);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(16f);
        b.setOnClickListener(l);
        return b;
    }

    protected TextView helpDot(View.OnClickListener l) {
        TextView help = new TextView(ctx);
        help.setText("?");
        help.setTextSize(11f);
        help.setTextColor(Color.parseColor("#666666"));
        help.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#E0E0E0"));
        bg.setStroke((int) dp(1), Color.parseColor("#BDBDBD"));
        help.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) dp(16), (int) dp(16));
        lp.leftMargin = (int) dp(6);
        help.setLayoutParams(lp);
        help.setOnClickListener(l);
        return help;
    }

    protected TextView title(String t, float size, String color) {
        TextView v = new TextView(ctx);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(Color.parseColor(color));
        v.setTypeface(null, Typeface.BOLD);
        return v;
    }
}
