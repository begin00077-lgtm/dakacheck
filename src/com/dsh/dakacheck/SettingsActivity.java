package com.dsh.dakacheck;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 二级页面：权限入口 + 测试入口。 */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(build());
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /** 弹出作者微信二维码。 */
    private void showContact() {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int pd = (int) dp(16);
        box.setPadding(pd, pd, pd, pd);

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

        new android.app.AlertDialog.Builder(this)
                .setTitle("联系作者")
                .setView(box)
                .setPositiveButton("关闭", null)
                .show();
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(16);
        root.setPadding(pad, (int) dp(12), pad, pad);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        View sp = new View(this);
        bar.addView(sp, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 右上角：联系作者
        TextView contact = new TextView(this);
        contact.setText("联系作者");
        contact.setTextSize(14f);
        contact.setTextColor(Color.WHITE);
        contact.setTypeface(null, Typeface.BOLD);
        contact.setGravity(Gravity.CENTER);
        contact.setPadding((int) dp(12), (int) dp(6), (int) dp(12), (int) dp(6));
        android.graphics.drawable.GradientDrawable cbg =
                new android.graphics.drawable.GradientDrawable();
        cbg.setCornerRadius(dp(14));
        cbg.setColor(Color.parseColor("#07C160"));
        contact.setBackground(cbg);
        contact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showContact();
            }
        });
        bar.addView(contact);

        root.addView(bar);

        root.addView(section("权限"));

        root.addView(row("无障碍服务", accessibilityOn(),
                "去开启 / 关闭", new Runnable() {
                    public void run() {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    }
                }));

        root.addView(row("电池优化（允许后台运行）", batteryAllowed(),
                "设为不限制", new Runnable() {
                    public void run() {
                        try {
                            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            i.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        } catch (Exception e) {
                            try {
                                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }));

        root.addView(row("全屏提醒权限", fullScreenIntent(),
                "允许锁屏弹出", new Runnable() {
                    public void run() {
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                            i.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        } catch (Exception e) {
                            Toast.makeText(SettingsActivity.this, "系统没有这个设置页，一般默认允许", Toast.LENGTH_SHORT).show();
                        }
                    }
                }));

        root.addView(row("使用情况访问（防误响）", Usage.hasPermission(this),
                "允许读取应用使用记录", new Runnable() {
                    public void run() {
                        try {
                            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                        } catch (Exception e) {
                            Toast.makeText(SettingsActivity.this, "系统没有这个设置页", Toast.LENGTH_SHORT).show();
                        }
                    }
                }));

        root.addView(section("备份"));

        final boolean canWrite = Archive.canWrite();
        root.addView(row("文件访问权限", canWrite,
                canWrite ? "已授权" : "点此授权", new Runnable() {
                    public void run() {
                        if (Archive.canWrite()) {
                            Toast.makeText(SettingsActivity.this, "已授权",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            i.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        } catch (Throwable t) {
                            try {
                                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                            } catch (Throwable t2) {
                                Toast.makeText(SettingsActivity.this, "系统没有这个设置页",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }));

        root.addView(row("立即同步到「打卡记录」", canWrite,
                canWrite ? "写一次文件" : "需先授权", new Runnable() {
                    public void run() {
                        if (!Archive.canWrite()) {
                            Toast.makeText(SettingsActivity.this, "请先授权文件访问",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Archive.syncAsync(SettingsActivity.this);
                        Toast.makeText(SettingsActivity.this,
                                "已写入 " + Archive.dir().getAbsolutePath(),
                                Toast.LENGTH_LONG).show();
                    }
                }));

        root.addView(section("排查"));

        root.addView(row("系统日志", null,
                "查看无障碍上线/掉线、读取页面记录", new Runnable() {
                    public void run() {
                        startActivity(new Intent(SettingsActivity.this, LogActivity.class));
                    }
                }));

        root.addView(section("测试"));

        root.addView(row("试响一次", null,
                "确认铃声和震动是否正常", new Runnable() {
                    public void run() {
                        Intent i = new Intent(SettingsActivity.this, CheckReceiver.class);
                        i.putExtra(CheckReceiver.EXTRA_KIND, Scheduler.KIND_IN);
                        i.putExtra(CheckReceiver.EXTRA_TEST, true);
                        sendBroadcast(i);
                    }
                }));

        scroll.addView(root);
        return NavBar.attach(this, NavBar.TAB_SETTINGS, scroll);
    }

    /** 手动补录任意一天的上下班时间。 */
    private void showFillDialog() {
        final String[] date = {Store.logicalDate()};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(16);
        box.setPadding(pad, pad, pad, 0);

        final TextView dateView = new TextView(this);
        dateView.setTextSize(17f);
        dateView.setTextColor(Color.parseColor("#2E7D32"));
        dateView.setTypeface(null, Typeface.BOLD);
        dateView.setPadding(0, 0, 0, (int) dp(10));
        dateView.setText("日期  " + date[0]);
        box.addView(dateView);

        final android.widget.EditText in = new android.widget.EditText(this);
        in.setHint("上班时间  如 08:58:47");
        box.addView(in);

        final android.widget.EditText out = new android.widget.EditText(this);
        out.setHint("下班时间  如 17:37:08");
        box.addView(out);

        final TextView hint = new TextView(this);
        hint.setTextSize(12.5f);
        hint.setTextColor(Color.parseColor("#777777"));
        hint.setPadding(0, (int) dp(8), 0, 0);
        box.addView(hint);

        fillExisting(date[0], dateView, in, out, hint);

        dateView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] p = date[0].split("-");
                int y = Integer.parseInt(p[0]);
                int m = Integer.parseInt(p[1]) - 1;
                int d = Integer.parseInt(p[2]);
                new android.app.DatePickerDialog(SettingsActivity.this,
                        new android.app.DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(android.widget.DatePicker dp2, int yy, int mm, int dd) {
                                date[0] = String.format(java.util.Locale.CHINA,
                                        "%04d-%02d-%02d", yy, mm + 1, dd);
                                fillExisting(date[0], dateView, in, out, hint);
                            }
                        }, y, m, d).show();
            }
        });

        new android.app.AlertDialog.Builder(this)
                .setTitle("补录打卡时间")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        String iv = in.getText().toString().trim();
                        String ov = out.getText().toString().trim();
                        if (!iv.isEmpty()) Store.putIn(SettingsActivity.this, date[0], iv);
                        if (!ov.isEmpty()) Store.putOut(SettingsActivity.this, date[0], ov);
                        Widgets.refreshAll(SettingsActivity.this);
                        Archive.syncAsync(SettingsActivity.this);
                        Toast.makeText(SettingsActivity.this,
                                "已保存 " + date[0], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /** 选中某天后，把已有记录带出来。 */
    private void fillExisting(String date, TextView dateView, android.widget.EditText in,
                              android.widget.EditText out, TextView hint) {
        String curIn = Store.getIn(this, date);
        String curOut = Store.getOut(this, date);
        dateView.setText("日期  " + date + "    （点这里换一天）");
        in.setText(curIn == null || curIn.isEmpty() ? "" : curIn);
        out.setText(curOut == null || curOut.isEmpty() ? "" : curOut);
        Double h = Work.hoursOf(this, date);
        hint.setText(curIn == null && curOut == null
                ? "这天还没有记录"
                : (h == null ? "这天的记录不完整" : String.format(java.util.Locale.CHINA,
                "当前工时 %.2f 小时", h)));
    }

    private TextView section(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(14f);
        v.setTextColor(Color.parseColor("#555555"));
        v.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) dp(20);
        lp.bottomMargin = (int) dp(6);
        v.setLayoutParams(lp);
        return v;
    }

    private View row(String name, Boolean ok, String hint, final Runnable action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) dp(16);
        box.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(Color.WHITE);
        box.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) dp(8);
        box.setLayoutParams(lp);

        SpannableStringBuilder sb = new SpannableStringBuilder();
        int a = sb.length();
        sb.append(name);
        sb.setSpan(new StyleSpan(Typeface.BOLD), a, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(0xFF111111), a, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (ok != null) {
            sb.append("  ");
            int b = sb.length();
            sb.append(ok ? "已开启 ✓" : "未开启 ✗");
            sb.setSpan(new StyleSpan(Typeface.BOLD), b, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(ok ? 0xFF2E7D32 : 0xFFC62828), b, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView t1 = new TextView(this);
        t1.setTextSize(16f);
        t1.setText(sb);
        box.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText(hint + " ›");
        t2.setTextSize(13f);
        t2.setTextColor(Color.parseColor("#777777"));
        LinearLayout.LayoutParams t2lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        t2lp.topMargin = (int) dp(4);
        box.addView(t2, t2lp);

        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return box;
    }

    private boolean accessibilityOn() {
        return Guard.accessibilityEnabled(this);
    }

    private boolean batteryAllowed() {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean fullScreenIntent() {
        if (Build.VERSION.SDK_INT < 34) return true;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            java.lang.reflect.Method m = NotificationManager.class.getMethod("canUseFullScreenIntent");
            Object r = m.invoke(nm);
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (Throwable t) {
            return true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setContentView(build());
    }
}
