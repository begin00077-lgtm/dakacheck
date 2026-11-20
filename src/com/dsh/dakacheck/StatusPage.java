package com.dsh.dakacheck;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.os.Build;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.content.ComponentName;
import java.util.Calendar;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 签到状态页。 */
public class StatusPage extends BasePage {

    private LinearLayout inCard;    private LinearLayout outCard;
    private TextView inLabel, inValue, outLabel, outValue;
    private LinearLayout infoBox;
    private TextView dayTitle;

    public StatusPage(android.app.Activity host) {
        super(host);
    }

    @Override
    public View build() {
        // 小屏机型也能看全：用滚动容器但不显示滚动条
        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setScrollbarFadingEnabled(true);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(16);
        root.setPadding(pad, (int) dp(12), pad, pad);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        dayTitle = new TextView(ctx);
        dayTitle.setTextSize(30f);
        dayTitle.setTextColor(Color.parseColor("#111111"));
        dayTitle.setTypeface(null, Typeface.BOLD);
        dayTitle.setSingleLine(true);
        dayTitle.setEllipsize(null);
        // 尽量撑满一行，但不换行
        dayTitle.setAutoSizeTextTypeUniformWithConfiguration(18, 32, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        dayTitle.setPadding((int) dp(2), (int) dp(2), (int) dp(2), (int) dp(12));
        root.addView(dayTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        inCard = card();
        inLabel = cardLabel("签到");
        inValue = cardValue("未确认");
        inCard.addView(inLabel);
        inCard.addView(inValue);
        root.addView(inCard, cardLp());

        outCard = card();
        outLabel = cardLabel("签退");
        outValue = cardValue("未确认");
        outCard.addView(outLabel);
        outCard.addView(outValue);
        root.addView(outCard, cardLp());

        infoBox = new LinearLayout(ctx);
        infoBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = (int) dp(8);
        root.addView(infoBox, ilp);

        root.addView(button("打开软通打卡", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = host.getPackageManager().getLaunchIntentForPackage(AttendanceService.TARGET);
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    host.startActivity(i);
                } else {
                    Toast.makeText(ctx, "没找到软通打卡 App", Toast.LENGTH_SHORT).show();
                }
            }
        }));

        // 署名：只在本页展示
        TextView credit = new TextView(ctx);
        credit.setText("design by Karl");
        credit.setTextSize(12f);
        credit.setTextColor(Color.parseColor("#999999"));
        credit.setPadding(0, (int) dp(6), 0, (int) dp(6));
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        crLp.weight = 1f;
        credit.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        root.addView(credit, crLp);

        // 内容不足一屏时不滚动
        scroll.setFillViewport(true);
        scroll.addView(root);
        return scroll;
    }

    protected void refresh() {
        String today = Store.logicalDate();
        boolean inSeen = Store.pageAt(ctx, today) > 0;
        String in = Store.getIn(ctx, today);
        String out = Store.getOut(ctx, today);

        if (in != null) {
            setCard(inCard, inValue, in.isEmpty() ? "已签到" : "已签到 " + in, "#2E7D32");
        } else if (inSeen) {
            setCard(inCard, inValue, "未签到", "#C62828");
        } else {
            setCard(inCard, inValue, "未确认", "#757575");
        }

        if (out != null) {
            setCard(outCard, outValue, out.isEmpty() ? "已签退" : "已签退 " + out, "#2E7D32");
        } else if (inSeen) {
            setCard(outCard, outValue, "未签退", "#C62828");
        } else {
            setCard(outCard, outValue, "未确认", "#757575");
        }

        long seen = Store.seen(ctx);
        infoBox.removeAllViews();
        boolean earlyHours = Store.beforeCycleBoundary();
        dayTitle.setText(today + "  " + weekdayOf(earlyHours));
        infoBox.addView(infoRow("上次读取 ", seen == 0 ? "还没有"
                : new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(new Date(seen))));
        long nextIn = Scheduler.nextTrigger(ctx, Scheduler.KIND_IN);
        long nextOut = Scheduler.nextTrigger(ctx, Scheduler.KIND_OUT);
        infoBox.addView(nextRow("下次签到检查时间：", clock(nextIn),
                "（检查" + Scheduler.targetDateFor(nextIn) + "签到情况）"));
        infoBox.addView(nextRow("下次签退检查时间：", clock(nextOut),
                "（检查" + Scheduler.targetDateFor(nextOut) + "签退情况）"));
        infoBox.addView(statusGrid());
        boolean usageOn = Usage.hasPermission(ctx);
        long lastFg = usageOn
                ? Usage.lastForegroundSince(ctx, AttendanceService.TARGET, System.currentTimeMillis() - 24 * 3600_000L)
                : 0L;
        infoBox.addView(infoRow("使用记录 ", !usageOn ? "未授权（建议开，用来防误响）"
                : (lastFg == 0 ? "软通打卡 24 小时内没被打开过" : "软通打卡最近打开 " + fmt(lastFg))));

    }

    protected LinearLayout card() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = (int) dp(13);
        l.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(Color.parseColor("#9E9E9E"));
        l.setBackground(g);
        return l;
    }

    protected LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) dp(9);
        return lp;
    }

    protected LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) dp(8);
        return lp;
    }

    protected TextView cardLabel(String t) {
        TextView v = new TextView(ctx);
        v.setText(t);
        v.setTextSize(15f);
        v.setTextColor(Color.parseColor("#E8F5E9"));
        return v;
    }

    protected TextView cardValue(String t) {
        TextView v = new TextView(ctx);
        v.setText(t);
        v.setTextSize(28f);
        v.setTextColor(Color.WHITE);
        v.setTypeface(null, Typeface.BOLD);
        v.setPadding(0, (int) dp(4), 0, 0);
        return v;
    }

    protected void setCard(LinearLayout card, TextView value, String text, String color) {
        value.setText(text);
        GradientDrawable g = (GradientDrawable) card.getBackground();
        g.setColor(Color.parseColor(color));
    }

    protected TextView infoRow(String label, String value) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(15f);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int s = sb.length();
        sb.append(label);
        sb.setSpan(new ForegroundColorSpan(0xFF666666), s, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), s, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        int v = sb.length();
        sb.append(value);
        sb.setSpan(new ForegroundColorSpan(0xFF111111), v, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(sb);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) dp(3);
        tv.setLayoutParams(lp);
        return tv;
    }

    protected View statusGrid() {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);

        final View reviveCell = cell("自动拉起", Guard.canRevive(ctx), "已授权", "未授权");
        reviveCell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(ctx)
                        .setTitle("自动拉起权限")
                        .setMessage("这个权限 Android 只允许用 adb 授予，App 内无法申请。\n\n"
                                + "电脑连上手机执行一次：\n\n"
                                + "adb shell pm grant com.dsh.dakacheck android.permission.WRITE_SECURE_SETTINGS\n\n"
                                + "没有它也能正常用，只是无障碍被关掉时，插件只能弹通知提醒你手动开，不能自己写回来。")
                        .setPositiveButton("知道了", null)
                        .show();
            }
        });

        LinearLayout row1 = new LinearLayout(ctx);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        // 以系统设置为准：设置里开着就是开着。
        // 进程内的 connected 只作为「服务是否已接管」的补充，避免重建进程后误判成未连接。
        boolean a11yEnabled = Guard.accessibilityEnabled(ctx);
        boolean a11yOnline = a11yEnabled && AttendanceService.connected;
        row1.addView(cell("无障碍", a11yEnabled,
                a11yOnline ? "在线" : "已开启", "已关闭"), halfLp());
        row1.addView(reviveCell, halfLp());
        col.addView(row1);

        LinearLayout row2 = new LinearLayout(ctx);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(cell("精确闹钟", Scheduler.canExact(ctx), "已允许", "未允许"), halfLp());
        row2.addView(cell("通知", notificationsOn(), "已允许", "未允许"), halfLp());
        col.addView(row2);
        return col;
    }

    protected LinearLayout.LayoutParams halfLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.topMargin = (int) dp(3);
        return lp;
    }

    protected TextView cell(String label, boolean ok, String okText, String badText) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(14f);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int a = sb.length();
        sb.append(label).append(" ");
        sb.setSpan(new ForegroundColorSpan(0xFF666666), a, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), a, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        int b = sb.length();
        sb.append(ok ? okText : badText).append(ok ? " ✓" : " ✗");
        sb.setSpan(new ForegroundColorSpan(ok ? 0xFF2E7D32 : 0xFFC62828), b, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), b, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(sb);
        return tv;
    }

    protected String weekdayOf(boolean previousDay) {
        String[] w = {"日", "一", "二", "三", "四", "五", "六"};
        Calendar c = Calendar.getInstance();
        if (previousDay) c.add(Calendar.DAY_OF_MONTH, -1);
        return "星期" + w[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    protected String clock(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(ms));
    }

    protected String fmt(long ms) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(ms));
    }

    protected String hm(int h, int m) {
        return String.format(Locale.CHINA, "%02d:%02d", h, m);
    }

    protected boolean accessibilityOn() {
        String enabled = Settings.Secure.getString(host.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName cn = new ComponentName(ctx, AttendanceService.class);
        String flat = cn.flattenToString();
        for (String s : enabled.split(":")) {
            if (flat.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    protected boolean notificationsOn() {
        if (Build.VERSION.SDK_INT >= 33) {
            return host.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    protected TextView nextRow(String label, String time, String note) {
        TextView tv = new TextView(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int a = sb.length();
        sb.append(label);
        sb.setSpan(new ForegroundColorSpan(0xFF666666), a, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), a, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new android.text.style.AbsoluteSizeSpan(14, true), a, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        int b = sb.length();
        sb.append(time);
        sb.setSpan(new ForegroundColorSpan(0xFF111111), b, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), b, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new android.text.style.AbsoluteSizeSpan(16, true), b, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        int c = sb.length();
        sb.append(note);
        sb.setSpan(new ForegroundColorSpan(0xFF777777), c, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new android.text.style.AbsoluteSizeSpan(12, true), c, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(sb);
        tv.setTextSize(15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) dp(3);
        tv.setLayoutParams(lp);
        return tv;
    }
}
