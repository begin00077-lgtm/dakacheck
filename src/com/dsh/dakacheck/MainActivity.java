package com.dsh.dakacheck;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout inCard;    private LinearLayout outCard;
    private TextView inLabel, inValue, outLabel, outValue;
    private LinearLayout infoBox;
    private Button inTimeBtn, outTimeBtn;
    private static long lastAutoSync = 0L;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(build());
        Scheduler.ensure(this);
        Notify.ensureChannels(this);
        // 不再常驻通知栏：停掉以前的前台服务并清掉残留通知
        try {
            stopService(new Intent(this, WatchService.class));
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(WatchService.ID);
        } catch (Throwable ignored) {
        }
        Guard.ensure(this);
        Widgets.refreshAll(this);
        askNotificationPermission();
        // 先从「打卡记录」文件夹恢复（清数据后可救回来），再回写一次
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int n = Archive.restore(MainActivity.this);
                Archive.sync(MainActivity.this);
                if (n > 0) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            refresh();
                            Widgets.refreshAll(MainActivity.this);
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(AttendanceService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, f);
        }
        refresh();
        ui.post(tick);
    }


    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
        try {
            unregisterReceiver(statusReceiver);
        } catch (Exception ignored) {
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 11);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private View build() {
        // 小屏机型也能看全：用滚动容器但不显示滚动条
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setScrollbarFadingEnabled(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(16);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("打卡提醒");
        title.setTextSize(24f);
        title.setTextColor(Color.parseColor("#111111"));
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView menu = new TextView(this);
        menu.setText("⚠ 权限设置与测试 ›");
        menu.setTextSize(15f);
        menu.setTextColor(Color.parseColor("#8B0000"));
        menu.setTypeface(null, Typeface.BOLD);
        menu.setPadding((int) dp(12), (int) dp(8), 0, (int) dp(8));
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        header.addView(menu);
        root.addView(header);

        inCard = card();
        inLabel = cardLabel("上班卡");
        inValue = cardValue("未确认");
        inCard.addView(inLabel);
        inCard.addView(inValue);
        root.addView(inCard, cardLp());

        outCard = card();
        outLabel = cardLabel("下班卡");
        outValue = cardValue("未确认");
        outCard.addView(outLabel);
        outCard.addView(outValue);
        root.addView(outCard, cardLp());

        infoBox = new LinearLayout(this);
        infoBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = (int) dp(8);
        root.addView(infoBox, ilp);

        TextView sec1 = section("核对时间");
        root.addView(sec1);

        inTimeBtn = new Button(this);
        inTimeBtn.setAllCaps(false);
        inTimeBtn.setTextSize(16f);
        inTimeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(true);
            }
        });
        root.addView(inTimeBtn, rowLp());

        outTimeBtn = new Button(this);
        outTimeBtn.setAllCaps(false);
        outTimeBtn.setTextSize(16f);
        outTimeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(false);
            }
        });
        root.addView(outTimeBtn, rowLp());

        TextView hint = new TextView(this);
        hint.setText("考勤日按 04:00 划分：0–4 点之间页面显示的是前一天的考勤，其余时间查当天。");
        hint.setTextSize(13.5f);
        hint.setTextColor(Color.parseColor("#444444"));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = (int) dp(2);
        root.addView(hint, hlp);

        root.addView(button("打开软通打卡", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = getPackageManager().getLaunchIntentForPackage(AttendanceService.TARGET);
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } else {
                    Toast.makeText(MainActivity.this, "没找到软通打卡 App", Toast.LENGTH_SHORT).show();
                }
            }
        }));

        root.addView(button("工时日历", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HoursActivity.class));
            }
        }));

        // 署名
        TextView credit = new TextView(this);
        credit.setText("design by Karl");
        credit.setTextSize(12f);
        credit.setTextColor(Color.parseColor("#999999"));
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, (int) dp(6), 0, (int) dp(6));
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        crLp.weight = 1f;
        crLp.gravity = Gravity.BOTTOM;
        credit.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        root.addView(credit, crLp);

        // 内容不足一屏时不滚动
        scroll.setFillViewport(true);
        scroll.addView(root);
        return scroll;
    }

    /** 弹出作者微信二维码。 */
    private void showContact() {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int p = (int) dp(16);
        box.setPadding(p, p, p, p);

        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageResource(com.dsh.dakacheck.R.drawable.wx_qr);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        box.addView(iv, new android.widget.LinearLayout.LayoutParams(
                (int) dp(260), (int) dp(384)));

        TextView tip = new TextView(this);
        tip.setText("扫码添加作者微信：卡尔");
        tip.setTextSize(13f);
        tip.setTextColor(Color.parseColor("#666666"));
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, (int) dp(10), 0, 0);
        box.addView(tip);

        new AlertDialog.Builder(this)
                .setTitle("联系作者")
                .setView(box)
                .setPositiveButton("关闭", null)
                .show();
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = (int) dp(13);
        l.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(Color.parseColor("#9E9E9E"));
        l.setBackground(g);
        return l;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) dp(9);
        return lp;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) dp(8);
        return lp;
    }

    private TextView cardLabel(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(15f);
        v.setTextColor(Color.parseColor("#E8F5E9"));
        return v;
    }

    private TextView cardValue(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(28f);
        v.setTextColor(Color.WHITE);
        v.setTypeface(null, Typeface.BOLD);
        v.setPadding(0, (int) dp(4), 0, 0);
        return v;
    }

    private TextView section(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(14f);
        v.setTextColor(Color.parseColor("#555555"));
        v.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) dp(12);
        v.setLayoutParams(lp);
        return v;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        b.setLayoutParams(rowLp());
        return b;
    }

    private void pickTime(final boolean isIn) {
        final int h0 = isIn ? Config.inHour(this) : Config.outHour(this);
        final int m0 = isIn ? Config.inMin(this) : Config.outMin(this);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        int pad = (int) dp(18);
        box.setPadding(pad, pad, pad, pad);

        final NumberPicker hp = new NumberPicker(this);
        hp.setMinValue(0);
        hp.setMaxValue(23);
        hp.setValue(h0);
        hp.setFormatter(TWO_DIGITS);
        hp.setWrapSelectorWheel(true);

        final NumberPicker mp = new NumberPicker(this);
        mp.setMinValue(0);
        mp.setMaxValue(59);
        mp.setValue(m0);
        mp.setFormatter(TWO_DIGITS);
        mp.setWrapSelectorWheel(true);

        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        box.addView(hp, np);

        TextView colon = new TextView(this);
        colon.setText(":");
        colon.setTextSize(28f);
        colon.setGravity(Gravity.CENTER);
        box.addView(colon);

        box.addView(mp, np);

        new AlertDialog.Builder(this)
                .setTitle(isIn ? "上班卡核对时间" : "下班卡核对时间")
                .setView(box)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (isIn) {
                            Config.setIn(MainActivity.this, hp.getValue(), mp.getValue());
                        } else {
                            Config.setOut(MainActivity.this, hp.getValue(), mp.getValue());
                        }
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static final NumberPicker.Formatter TWO_DIGITS = new NumberPicker.Formatter() {
        @Override
        public String format(int value) {
            return String.format(Locale.CHINA, "%02d", value);
        }
    };

    private void fireTest(String kind) {
        Intent i = new Intent(this, CheckReceiver.class);
        i.putExtra(CheckReceiver.EXTRA_KIND, kind);
        i.putExtra(CheckReceiver.EXTRA_TEST, true);
        sendBroadcast(i);
    }

    private void fireCheck(String kind) {
        Intent i = new Intent(this, CheckReceiver.class);
        i.putExtra(CheckReceiver.EXTRA_KIND, kind);
        sendBroadcast(i);
    }

    private void setCard(LinearLayout card, TextView value, String text, String color) {
        value.setText(text);
        GradientDrawable g = (GradientDrawable) card.getBackground();
        g.setColor(Color.parseColor(color));
    }

    private void refresh() {
        String today = Store.logicalDate();
        boolean inSeen = Store.pageAt(this, today) > 0;
        String in = Store.getIn(this, today);
        String out = Store.getOut(this, today);

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

        long seen = Store.seen(this);
        infoBox.removeAllViews();
        boolean earlyHours = Store.beforeCycleBoundary();
        infoBox.addView(infoRow(earlyHours ? "考勤日 " : "今天 ",
                today + " " + weekdayOf(earlyHours)
                        + (earlyHours ? "（0–4 点算前一天）" : "")));
        infoBox.addView(infoRow("上次读取 ", seen == 0 ? "还没有"
                : new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(new Date(seen))));
        long nextIn = Scheduler.nextTrigger(this, Scheduler.KIND_IN);
        long nextOut = Scheduler.nextTrigger(this, Scheduler.KIND_OUT);
        infoBox.addView(nextRow("下次上班卡核对时间：", clock(nextIn),
                "（检查" + Scheduler.targetDateFor(nextIn) + "上班卡情况）"));
        infoBox.addView(nextRow("下次下班卡核对时间：", clock(nextOut),
                "（检查" + Scheduler.targetDateFor(nextOut) + "下班卡情况）"));
        infoBox.addView(statusGrid());
        boolean usageOn = Usage.hasPermission(this);
        long lastFg = usageOn
                ? Usage.lastForegroundSince(this, AttendanceService.TARGET, System.currentTimeMillis() - 24 * 3600_000L)
                : 0L;
        infoBox.addView(infoRow("使用记录 ", !usageOn ? "未授权（建议开，用来防误响）"
                : (lastFg == 0 ? "软通打卡 24 小时内没被打开过" : "软通打卡最近打开 " + fmt(lastFg))));

        inTimeBtn.setText("上班卡核对时间　" + hm(Config.inHour(this), Config.inMin(this)));
        outTimeBtn.setText("下班卡核对时间　" + hm(Config.outHour(this), Config.outMin(this))
                + (Config.outTargetsPreviousDay(this) ? "（查前一天）" : "（查当天）"));
    }

    private TextView infoRow(String label, String value) {
        TextView tv = new TextView(this);
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

    private TextView markRow(String label, boolean ok, String okText, String badText) {
        TextView tv = new TextView(this);
        tv.setTextSize(15f);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int s = sb.length();
        sb.append(label);
        sb.setSpan(new ForegroundColorSpan(0xFF666666), s, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), s, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        int v = sb.length();
        sb.append(ok ? okText : badText);
        sb.setSpan(new ForegroundColorSpan(ok ? 0xFF2E7D32 : 0xFFC62828), v, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), v, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(sb);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) dp(3);
        tv.setLayoutParams(lp);
        return tv;
    }

    /** 四项状态，一行两个。 */
    private View statusGrid() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        final View reviveCell = cell("自动拉起", Guard.canRevive(this), "已授权", "未授权");
        reviveCell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("自动拉起权限")
                        .setMessage("这个权限 Android 只允许用 adb 授予，App 内无法申请。\n\n"
                                + "电脑连上手机执行一次：\n\n"
                                + "adb shell pm grant com.dsh.dakacheck android.permission.WRITE_SECURE_SETTINGS\n\n"
                                + "没有它也能正常用，只是无障碍被关掉时，插件只能弹通知提醒你手动开，不能自己写回来。")
                        .setPositiveButton("知道了", null)
                        .show();
            }
        });

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        boolean a11yOnline = AttendanceService.connected;
        boolean a11yEnabled = accessibilityOn();
        row1.addView(cell("无障碍", a11yOnline,
                "在线", a11yEnabled ? "未连接" : "已关闭"), halfLp());
        row1.addView(reviveCell, halfLp());
        col.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(cell("精确闹钟", Scheduler.canExact(this), "已允许", "未允许"), halfLp());
        row2.addView(cell("通知", notificationsOn(), "已允许", "未允许"), halfLp());
        col.addView(row2);
        return col;
    }

    private LinearLayout.LayoutParams halfLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.topMargin = (int) dp(3);
        return lp;
    }

    private TextView cell(String label, boolean ok, String okText, String badText) {
        TextView tv = new TextView(this);
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

    private TextView nextRow(String label, String time, String note) {
        TextView tv = new TextView(this);
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

    private String hm(int h, int m) {
        return String.format(Locale.CHINA, "%02d:%02d", h, m);
    }

    private String clock(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(ms));
    }

    private String fmt(long ms) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(ms));
    }

    /** 按考勤日算星期：0–4 点算前一天，避免凌晨显示成第二天的星期。 */
    private String weekdayOf(boolean previousDay) {
        String[] w = {"日", "一", "二", "三", "四", "五", "六"};
        Calendar c = Calendar.getInstance();
        if (previousDay) c.add(Calendar.DAY_OF_MONTH, -1);
        return "星期" + w[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    private boolean accessibilityOn() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName cn = new ComponentName(this, AttendanceService.class);
        String flat = cn.flattenToString();
        for (String s : enabled.split(":")) {
            if (flat.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    private boolean notificationsOn() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}
