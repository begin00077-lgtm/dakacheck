package com.dsh.dakacheck;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 底部导航栏（仿微信）：签到状态 / 提醒设置 / 工时日历 / 设置。
 *
 * 每个页面把自己的内容放进 {@link #attach} 返回的容器里，导航栏固定在底部。
 */
public final class NavBar {

    public static final int TAB_STATUS = 0;
    public static final int TAB_REMIND = 1;
    public static final int TAB_HOURS = 2;
    public static final int TAB_SETTINGS = 3;

    private static final String[] TITLES = {"签到状态", "提醒设置", "工时日历", "设置"};
    // 用纯符号字形：emoji 会忽略 setTextColor，始终带颜色
    private static final String[] ICONS = {"●", "◔", "▦", "⚙"};

    /** 这些 tab 自带左右手势（如日历翻月），不参与整页切换。 */
    private static final java.util.Set<Integer> SWIPE_DISABLED =
            new java.util.HashSet<Integer>();

    private static final int COLOR_ON = 0xFF07C160;   // 微信绿
    private static final int COLOR_OFF = 0xFF9A9A9A;

    private NavBar() {
    }

    /**
     * 生成带底部导航栏的根布局。
     *
     * @param ctx    上下文
     * @param cur    当前选中的 tab
     * @param content 页面内容会被 add 进中间区域
     */
    public static LinearLayout attach(final Context ctx, int cur, View content) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // 内容区：占满剩余空间（自定义容器才能抢跨 ScrollView 拿手势）
        SwipeFrame swipeFrame = SWIPE_DISABLED.contains(cur) ? null : new SwipeFrame(ctx, cur);
        FrameLayout body = swipeFrame != null ? swipeFrame : new FrameLayout(ctx);
        body.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 导航栏
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.parseColor("#FFFFFF"));
        bar.setElevation(dp(ctx, 8));

        for (int i = 0; i < TITLES.length; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(ctx);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, (int) dp(ctx, 8), 0, (int) dp(ctx, 8));

            TextView icon = new TextView(ctx);
            icon.setText(ICONS[i]);
            icon.setTextSize(19f);
            icon.setGravity(Gravity.CENTER);
            icon.setIncludeFontPadding(false);
            icon.setTextColor(i == cur ? COLOR_ON : COLOR_OFF);
            icon.setTypeface(null, i == cur ? Typeface.BOLD : Typeface.NORMAL);
            icon.setAlpha(1f);   // 用纯灰代替彩色，不用半透明

            TextView label = new TextView(ctx);
            label.setText(TITLES[i]);
            label.setTextSize(11f);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            label.setPadding(0, (int) dp(ctx, 3), 0, 0);
            label.setTextColor(i == cur ? COLOR_ON : COLOR_OFF);
            if (i == cur) label.setTypeface(null, Typeface.BOLD);

            item.addView(icon, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            item.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (idx == cur) return;
                    NavBar.open(ctx, idx, idx > cur ? 1 : -1);
                }
            });

            bar.addView(item, new LinearLayout.LayoutParams(0, (int) dp(ctx, 58), 1f));
        }

        // 一条细分割线
        View line = new View(ctx);
        line.setBackgroundColor(Color.parseColor("#E5E5E5"));
        root.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) dp(ctx, 0.5f))));
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return root;
    }

    /** 切换到某个 tab（每个 tab 是独立 Activity，切换时关掉栈里的其它页）。 */
    public static void open(Context ctx, int tab) {
        open(ctx, tab, 0);
    }

    /**
     * @param dir 0 = 无方向（点击导航栏）；+1 = 新页从右滑入；-1 = 新页从左滑入
     */
    public static void open(Context ctx, int tab, int dir) {
        Class<?> target;
        switch (tab) {
            case TAB_REMIND:
                target = RemindActivity.class;
                break;
            case TAB_HOURS:
                target = HoursActivity.class;
                break;
            case TAB_SETTINGS:
                target = SettingsActivity.class;
                break;
            default:
                target = MainActivity.class;
                break;
        }
        android.content.Intent i = new android.content.Intent(ctx, target);
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION);
        ctx.startActivity(i);
        // 卡片式切换：新页顺着滑入，旧页反方向退出（和日历翻月一致）
        if (ctx instanceof android.app.Activity) {
            android.app.Activity a = (android.app.Activity) ctx;
            if (dir > 0) {
                a.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            } else if (dir < 0) {
                a.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            } else {
                a.overridePendingTransition(0, 0);
            }
        }
    }

    /**
     * 可拖拽的内容容器：横向拖动时页面跟手平移，
     * 松手后超过阈值则切页，否则弹回。
     */
    private static final class SwipeFrame extends FrameLayout {
        private final int cur;
        private float downX, downY;
        private boolean horizontal;
        private boolean inExempt;
        private int width;
        private boolean targetLaunched;
        private float pageAlpha = 1f;

        SwipeFrame(Context ctx, int cur) {
            super(ctx);
            this.cur = cur;
        }

        /** 点击位置是否落在豁免区域（如日历）。 */
        private boolean hitExempt(float x, float y) {
            return hitExemptIn(this, x, y);
        }

        private boolean hitExemptIn(View node, float x, float y) {
            if (node == null) return false;
            Object tag = node.getTag();
            if (tag != null && "nav_no_swipe".equals(tag.toString())) {
                int[] loc = new int[2];
                node.getLocationInWindow(loc);
                int[] self = new int[2];
                getLocationInWindow(self);
                float lx = loc[0] - self[0];
                float ly = loc[1] - self[1];
                if (x >= lx && x <= lx + node.getWidth()
                        && y >= ly && y <= ly + node.getHeight()) {
                    return true;
                }
            }
            if (node instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) node;
                for (int i = 0; i < g.getChildCount(); i++) {
                    if (hitExemptIn(g.getChildAt(i), x, y)) return true;
                }
            }
            return false;
        }

        /** 当前页面是否能向该方向切换（首尾页到边不能再走）。 */
        private boolean canGo(float dx) {
            if (dx < 0) return cur < TITLES.length - 1;
            if (dx > 0) return cur > 0;
            return false;
        }

        @Override
        public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    horizontal = false;
                    width = Math.max(1, getWidth());
                    inExempt = hitExempt(downX, downY);
                    break;
                case android.view.MotionEvent.ACTION_MOVE: {
                    if (inExempt) return false;
                    float dx = e.getX() - downX;
                    float dy = e.getY() - downY;
                    if (Math.abs(dx) > 24 && Math.abs(dx) > Math.abs(dy) * 1.6f) {
                        horizontal = true;
                        return true;
                    }
                    break;
                }
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            if (inExempt) return false;
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_MOVE: {
                    if (!horizontal) break;
                    float dx = e.getX() - downX;
                    // 首尾页到边：做阻尼感
                    setTranslationX(canGo(dx) ? dx : dx * 0.2f);
                    // 只做极轻的透明变化，不让页面看起来消失
                    pageAlpha = 1f - Math.min(0.12f, Math.abs(dx) / width * 0.15f);
                    setAlpha(pageAlpha);
                    return true;
                }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL: {
                    if (!horizontal) break;
                    float dx = e.getX() - downX;
                    float dy = e.getY() - downY;
                    horizontal = false;

                    boolean far = Math.abs(dx) > width * 0.25f
                            && Math.abs(dx) > Math.abs(dy) * 1.6f;

                    if (far && canGo(dx)) {
                        // 当前页顺手指方向继续快速移出，
                        // 目标页同时以卡片动画滑入补位
                        final int next = dx < 0 ? cur + 1 : cur - 1;
                        final int dir = dx < 0 ? 1 : -1;
                        float target = dx < 0 ? -getWidth() * 0.45f : getWidth() * 0.45f;
                        animate().translationX(target).alpha(0.85f)
                                .setDuration(170)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        setTranslationX(0f);
                                        setAlpha(1f);
                                        open(getContext(), next, dir);
                                    }
                                })
                                .start();
                    } else {
                        animate().translationX(0f).alpha(1f).setDuration(200).start();
                    }
                    return true;
                }
            }
            return true;
        }
    }

    private static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }
}
