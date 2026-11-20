package com.dsh.dakacheck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 四页并排的横向分页容器 + 独立底部导航栏。
 * 页面同处一个容器，拖动时相邻页真实补位；导航栏在容器之外，切换时完全静止。
 */
public final class PagerHost extends LinearLayout {

    public static final int TAB_STATUS = 0;
    public static final int TAB_REMIND = 1;
    public static final int TAB_HOURS = 2;
    public static final int TAB_SETTINGS = 3;

    private static final String[] TITLES = {"签到状态", "提醒设置", "工时日历", "设置"};
    private static final int COLOR_ON = 0xFF07C160;
    private static final int COLOR_OFF = 0xFF8A8A8A;

    public interface Bridge {
        View createPage(int tab);
        boolean inCalendarArea(int tab, float rawX, float rawY);
        void onPageShown(int tab);
    }

    private final Bridge bridge;
    private final FrameLayout viewport;
    private final LinearLayout strip;
    private final View[] pages = new View[4];
    private final NavIcon[] icons = new NavIcon[4];
    private final TextView[] labels = new TextView[4];

    private int current = 0;
    private float downX, downY;
    private boolean dragging;
    private float dragStartX;
    private boolean fromCalendar;

    public PagerHost(Context ctx, Bridge bridge) {
        super(ctx);
        this.bridge = bridge;
        setOrientation(VERTICAL);
        setBackgroundColor(Color.parseColor("#F5F5F5"));

        viewport = new FrameLayout(ctx);
        viewport.setClipChildren(true);
        // 四页并排：宽度 = 视口宽 * 4，每页占 1/4
        strip = new LinearLayout(ctx) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int w = MeasureSpec.getSize(widthSpec);
                int h = MeasureSpec.getSize(heightSpec);
                setMeasuredDimension(w * 4, h);
                int childW = w;
                int childH = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
                for (int i = 0; i < getChildCount(); i++) {
                    View c = getChildAt(i);
                    c.measure(MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY), childH);
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int childW = getWidth() / 4;
                for (int i = 0; i < getChildCount(); i++) {
                    View c = getChildAt(i);
                    c.layout(i * childW, 0, (i + 1) * childW, getHeight());
                }
            }
        };
        strip.setOrientation(HORIZONTAL);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        viewport.addView(strip, slp);
        addView(viewport, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        View line = new View(ctx);
        line.setBackgroundColor(Color.parseColor("#E5E5E5"));
        addView(line, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, Math.max(1, (int) dp(0.5f))));
        addView(buildBar(ctx), new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void buildAll() {
        for (int i = 0; i < 4; i++) {
            View p = bridge.createPage(i);
            strip.addView(p, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT));
            pages[i] = p;
        }
        viewport.post(new Runnable() {
            @Override
            public void run() {
                jumpTo(current);
            }
        });
    }

    public View page(int tab) { return pages[tab]; }
    public int currentTab() { return current; }

    private void jumpTo(int tab) {
        float w = Math.max(1, viewport.getWidth());
        current = tab;
        strip.animate().cancel();
        strip.setTranslationX(-tab * w);
        paintTabs();
        bridge.onPageShown(tab);
    }

    public void goTo(int tab) {
        if (tab < 0 || tab > 3) return;
        float w = Math.max(1, viewport.getWidth());
        boolean changed = tab != current;
        current = tab;
        // 即使目标就是当前页，也要回弹到正确位置
        strip.animate().translationX(-tab * w)
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
        if (changed) {
            paintTabs();
            bridge.onPageShown(tab);
        }
    }

    public boolean handleTouch(android.view.MotionEvent e) {
        switch (e.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                dragStartX = strip.getTranslationX();
                fromCalendar = bridge.inCalendarArea(current, e.getRawX(), e.getRawY());
                break;
            case android.view.MotionEvent.ACTION_MOVE: {
                if (fromCalendar) return false;
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!dragging && Math.abs(dx) > 18 && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                    dragging = true;
                    strip.animate().cancel();
                }
                if (dragging) {
                    float w = Math.max(1, viewport.getWidth());
                    float target = dragStartX + dx;
                    float min = -(3 * w);
                    if (target > 0) target = target * 0.25f;
                    if (target < min) target = min + (target - min) * 0.25f;
                    strip.setTranslationX(target);
                    return true;
                }
                break;
            }
            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL: {
                if (!dragging) { fromCalendar = false; break; }
                dragging = false;
                fromCalendar = false;
                float w = Math.max(1, viewport.getWidth());
                float dx = e.getX() - downX;
                int next = current;
                if (Math.abs(dx) > w * 0.2f) {
                    next = dx < 0 ? current + 1 : current - 1;
                    next = Math.max(0, Math.min(3, next));
                }
                goTo(next);
                return true;
            }
        }
        return false;
    }

    private View buildBar(Context ctx) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(HORIZONTAL);
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(dp(8));
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(ctx);
            item.setOrientation(VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, (int) dp(8), 0, (int) dp(8));

            NavIcon icon = new NavIcon(ctx, i);
            icon.setLayoutParams(new LinearLayout.LayoutParams((int) dp(21), (int) dp(21)));

            TextView label = new TextView(ctx);
            label.setText(TITLES[i]);
            label.setTextSize(11f);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            label.setPadding(0, (int) dp(3), 0, 0);

            icons[i] = icon;
            labels[i] = label;

            item.addView(icon);
            item.addView(label, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { goTo(idx); }
            });
            bar.addView(item, new LinearLayout.LayoutParams(0, (int) dp(58), 1f));
        }
        return bar;
    }

    private void paintTabs() {
        for (int i = 0; i < 4; i++) {
            boolean on = i == current;
            if (icons[i] != null) icons[i].setTint(on ? COLOR_ON : COLOR_OFF);
            if (labels[i] != null) {
                labels[i].setTextColor(on ? COLOR_ON : COLOR_OFF);
                labels[i].setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
            }
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    static final class NavIcon extends View {
        private final int kind;
        private int tint = COLOR_OFF;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float stroke;

        NavIcon(Context c, int kind) {
            super(c);
            this.kind = kind;
            stroke = c.getResources().getDisplayMetrics().density * 1.7f;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setTint(int color) { tint = color; invalidate(); }

        @Override
        protected void onDraw(Canvas cv) {
            paint.setColor(tint);
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float r = Math.min(w, h) * 0.34f;
            paint.setStrokeWidth(stroke);

            switch (kind) {
                case 0:
                    paint.setStyle(Paint.Style.FILL);
                    cv.drawCircle(cx, cy, r * 0.6f, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    cv.drawCircle(cx, cy, r, paint);
                    break;
                case 1: {
                    paint.setStyle(Paint.Style.STROKE);
                    RectF body = new RectF(cx - r * 0.88f, cy - r * 0.7f,
                            cx + r * 0.88f, cy + r * 1.0f);
                    cv.drawOval(body, paint);
                    cv.drawLine(cx, cy - r * 0.05f, cx, cy + r * 0.5f, paint);
                    cv.drawLine(cx, cy - r * 0.05f, cx + r * 0.42f, cy - r * 0.05f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    Path p1 = new Path();
                    p1.moveTo(cx - r * 1.0f, cy - r * 0.88f);
                    p1.lineTo(cx - r * 0.45f, cy - r * 1.3f);
                    p1.lineTo(cx - r * 0.16f, cy - r * 0.82f);
                    p1.close();
                    cv.drawPath(p1, paint);
                    Path p2 = new Path();
                    p2.moveTo(cx + r * 1.0f, cy - r * 0.88f);
                    p2.lineTo(cx + r * 0.45f, cy - r * 1.3f);
                    p2.lineTo(cx + r * 0.16f, cy - r * 0.82f);
                    p2.close();
                    cv.drawPath(p2, paint);
                    break;
                }
                case 2: {
                    paint.setStyle(Paint.Style.STROKE);
                    RectF box = new RectF(cx - r, cy - r * 0.82f, cx + r, cy + r);
                    cv.drawRoundRect(box, r * 0.18f, r * 0.18f, paint);
                    cv.drawLine(cx - r, cy - r * 0.28f, cx + r, cy - r * 0.28f, paint);
                    cv.drawLine(cx - r * 0.33f, cy - r * 0.28f, cx - r * 0.33f, cy + r, paint);
                    cv.drawLine(cx + r * 0.33f, cy - r * 0.28f, cx + r * 0.33f, cy + r, paint);
                    break;
                }
                default: {
                    paint.setStyle(Paint.Style.STROKE);
                    cv.drawCircle(cx, cy, r * 0.46f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setStrokeWidth(stroke * 1.5f);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * i / 4;
                        float x1 = (float) (cx + Math.cos(a) * r * 0.68f);
                        float y1 = (float) (cy + Math.sin(a) * r * 0.68f);
                        float x2 = (float) (cx + Math.cos(a) * r * 1.04f);
                        float y2 = (float) (cy + Math.sin(a) * r * 1.04f);
                        cv.drawLine(x1, y1, x2, y2, paint);
                    }
                    break;
                }
            }
        }
    }
}
