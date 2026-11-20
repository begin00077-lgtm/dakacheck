package com.dsh.dakacheck;

import android.content.Context;
import android.os.PowerManager;
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

/** 设置页：权限入口 + 测试入口。 */
public class SettingsPage extends BasePage {

    public SettingsPage(android.app.Activity host) {
        super(host);
    }

    @Override
    public View build() {
        return buildInner();
    }

    /** 弹出作者微信二维码。 */
    private void showContact() {
        android.widget.LinearLayout box = new android.widget.LinearLayout(ctx);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int pd = (int) dp(16);
        box.setPadding(pd, pd, pd, pd);

        android.widget.ImageView iv = new android.widget.ImageView(ctx);
        iv.setImageResource(com.dsh.dakacheck.R.drawable.wx_qr);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        box.addView(iv, new android.widget.LinearLayout.LayoutParams(
                (int) dp(260), (int) dp(384)));

        TextView tip = new TextView(ctx);
        tip.setText("扫码添加作者微信：卡尔");
        tip.setTextSize(13f);
        tip.setTextColor(Color.parseColor("#666666"));
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, (int) dp(10), 0, 0);
        box.addView(tip);

        new android.app.AlertDialog.Builder(host)
                .setTitle("联系作者")
                .setView(box)
                .setPositiveButton("关闭", null)
                .show();
    }

    private View buildInner() {
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(16);
        root.setPadding(pad, (int) dp(12), pad, pad);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        root.addView(section("权限"));

        root.addView(row("无障碍服务", accessibilityOn(),
                "去开启 / 关闭", new Runnable() {
                    public void run() {
                        host.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    }
                }));

        root.addView(row("电池优化（允许后台运行）", batteryAllowed(),
                "设为不限制", new Runnable() {
                    public void run() {
                        try {
                            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            i.setData(Uri.parse("package:" + ctx.getPackageName()));
                            host.startActivity(i);
                        } catch (Exception e) {
                            try {
                                host.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
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
                            i.setData(Uri.parse("package:" + ctx.getPackageName()));
                            host.startActivity(i);
                        } catch (Exception e) {
                            Toast.makeText(ctx, "系统没有这个设置页，一般默认允许", Toast.LENGTH_SHORT).show();
                        }
                    }
                }));

        root.addView(row("使用情况访问（防误响）", Usage.hasPermission(ctx),
                "允许读取应用使用记录", new Runnable() {
                    public void run() {
                        try {
                            host.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                        } catch (Exception e) {
                            Toast.makeText(ctx, "系统没有这个设置页", Toast.LENGTH_SHORT).show();
                        }
                    }
                }));

        root.addView(section("备份"));

        final boolean canWrite = Archive.canWrite();
        root.addView(row("文件访问权限", canWrite,
                canWrite ? "已授权" : "点此授权", new Runnable() {
                    public void run() {
                        if (Archive.canWrite()) {
                            Toast.makeText(ctx, "已授权",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            i.setData(Uri.parse("package:" + ctx.getPackageName()));
                            host.startActivity(i);
                        } catch (Throwable t) {
                            try {
                                host.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                            } catch (Throwable t2) {
                                Toast.makeText(ctx, "系统没有这个设置页",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }));

        root.addView(row("自动导出打卡记录", canWrite,
                canWrite ? "实时写入「打卡记录」文件夹" : "需先授权", new Runnable() {
                    public void run() {
                        if (!Archive.canWrite()) {
                            Toast.makeText(ctx, "请先授权文件访问",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Archive.syncAsync(ctx);
                        Toast.makeText(ctx,
                                "已写入 " + Archive.dir().getAbsolutePath(),
                                Toast.LENGTH_LONG).show();
                    }
                }));

        root.addView(row("自动导入打卡记录", canWrite,
                canWrite ? "从「打卡记录」补回缺失记录" : "需先授权", new Runnable() {
                    public void run() {
                        if (!Archive.canWrite()) {
                            Toast.makeText(ctx, "请先授权文件访问",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        final int n = Archive.restore(ctx);
                        Archive.syncAsync(ctx);
                        android.app.AlertDialog.Builder b =
                                new android.app.AlertDialog.Builder(host)
                                        .setTitle("导入完成")
                                        .setMessage(n > 0
                                                ? "已从「打卡记录/打卡汇总.csv」导入 "
                                                    + n + " 条记录。"
                                                : "没有需要导入的记录（已是最新）。")
                                        .setPositiveButton("好", null);
                        b.show();
                        if (n > 0) {
                            // 让日历重新渲染
                            android.util.Log.i("Daka", "restored " + n);
                        }
                    }
                }));

        root.addView(section("排查"));

        root.addView(row("系统日志", null,
                "查看无障碍上线/掉线、读取页面记录", new Runnable() {
                    public void run() {
                        host.startActivity(new Intent(ctx, LogActivity.class));
                    }
                }));

        root.addView(section("测试"));

        root.addView(row("试响一次", null,
                "确认铃声和震动是否正常", new Runnable() {
                    public void run() {
                        Intent i = new Intent(ctx, CheckReceiver.class);
                        i.putExtra(CheckReceiver.EXTRA_KIND, Scheduler.KIND_IN);
                        i.putExtra(CheckReceiver.EXTRA_TEST, true);
                        ctx.sendBroadcast(i);
                    }
                }));

        root.addView(section("关于"));

        root.addView(row("检查更新", null,
                "当前版本 v" + versionName(), new Runnable() {
                    public void run() {
                        checkUpdate();
                    }
                }));

        scroll.addView(root);
        return scroll;
    }

    /** 手动补录任意一天的上下班时间。 */
    private void showFillDialog() {
        final String[] date = {Store.logicalDate()};

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(16);
        box.setPadding(pad, pad, pad, 0);

        final TextView dateView = new TextView(ctx);
        dateView.setTextSize(17f);
        dateView.setTextColor(Color.parseColor("#2E7D32"));
        dateView.setTypeface(null, Typeface.BOLD);
        dateView.setPadding(0, 0, 0, (int) dp(10));
        dateView.setText("日期  " + date[0]);
        box.addView(dateView);

        final android.widget.EditText in = new android.widget.EditText(ctx);
        in.setHint("上班时间  如 08:58:47");
        box.addView(in);

        final android.widget.EditText out = new android.widget.EditText(ctx);
        out.setHint("下班时间  如 17:37:08");
        box.addView(out);

        final TextView hint = new TextView(ctx);
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
                new android.app.DatePickerDialog(ctx,
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

        new android.app.AlertDialog.Builder(host)
                .setTitle("补录打卡时间")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        String iv = in.getText().toString().trim();
                        String ov = out.getText().toString().trim();
                        if (!iv.isEmpty()) Store.putIn(ctx, date[0], iv);
                        if (!ov.isEmpty()) Store.putOut(ctx, date[0], ov);
                        Widgets.refreshAll(ctx);
                        Archive.syncAsync(ctx);
                        Toast.makeText(ctx,
                                "已保存 " + date[0], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /** 选中某天后，把已有记录带出来。 */
    private void fillExisting(String date, TextView dateView, android.widget.EditText in,
                              android.widget.EditText out, TextView hint) {
        String curIn = Store.getIn(ctx, date);
        String curOut = Store.getOut(ctx, date);
        dateView.setText("日期  " + date + "    （点这里换一天）");
        in.setText(curIn == null || curIn.isEmpty() ? "" : curIn);
        out.setText(curOut == null || curOut.isEmpty() ? "" : curOut);
        Double h = Work.hoursOf(ctx, date);
        hint.setText(curIn == null && curOut == null
                ? "这天还没有记录"
                : (h == null ? "这天的记录不完整" : String.format(java.util.Locale.CHINA,
                "当前工时 %.2f 小时", h)));
    }

    private View row(String name, Boolean ok, String hint, final Runnable action) {
        LinearLayout box = new LinearLayout(ctx);
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
        TextView t1 = new TextView(ctx);
        t1.setTextSize(16f);
        t1.setText(sb);
        box.addView(t1);

        TextView t2 = new TextView(ctx);
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
        return Guard.accessibilityEnabled(ctx);
    }

    private boolean batteryAllowed() {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean fullScreenIntent() {
        if (Build.VERSION.SDK_INT < 34) return true;
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            java.lang.reflect.Method m = NotificationManager.class.getMethod("canUseFullScreenIntent");
            Object r = m.invoke(nm);
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (Throwable t) {
            return true;
        }
    }



    /** 当前版本名。 */
    private String versionName() {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    private int versionCode() {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 对比 GitHub 上发布的版本，有新版则询问是否更新。 */
    private void checkUpdate() {
        final android.app.ProgressDialog pd = new android.app.ProgressDialog(host);
        pd.setMessage("正在检查更新...");
        pd.setCancelable(false);
        pd.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                String tag = null;
                String apkUrl = null;
                try {
                    java.net.URL u = new java.net.URL(Update.RELEASE_API);
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.setRequestProperty("Accept", "application/vnd.github+json");
                    java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    org.json.JSONObject o = new org.json.JSONObject(sb.toString());
                    tag = o.optString("tag_name", "");
                    org.json.JSONArray assets = o.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            org.json.JSONObject a = assets.getJSONObject(i);
                            String n = a.optString("name", "");
                            if (n.endsWith(".apk")) {
                                apkUrl = a.optString("browser_download_url", null);
                                break;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }

                final String fTag = tag;
                final String fUrl = apkUrl;
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pd.isShowing()) pd.dismiss();
                        if (host.isFinishing() || host.isDestroyed()) return;
                        if (fTag == null || fTag.isEmpty()) {
                            Toast.makeText(ctx, "检查失败，请检查网络",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String remote = fTag.replaceAll("[^0-9.]", "");
                        String local = versionName();
                        boolean newer = compareVersion(remote, local) > 0;
                        if (!newer) {
                            Toast.makeText(ctx, "已是最新版（v" + local + "）",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        new android.app.AlertDialog.Builder(host)
                                .setTitle("发现新版本")
                                .setMessage("当前 v" + local + "，最新 v" + remote
                                        + "\n\n是否立即更新？")
                                .setNegativeButton("不更新", null)
                                .setPositiveButton("更新", new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface d, int w) {
                                        if (fUrl == null) {
                                            Toast.makeText(ctx, "下载地址缺失",
                                                    Toast.LENGTH_SHORT).show();
                                            return;
                                        }
                                        downloadAndInstall(fUrl);
                                    }
                                })
                                .show();
                    }
                });
            }
        }).start();
    }

    /** 比较版本号：a > b 返回正数。 */
    private static int compareVersion(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int p = i < x.length ? parseIntSafe(x[i]) : 0;
            int q = i < y.length ? parseIntSafe(y[i]) : 0;
            if (p != q) return p - q;
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 在 App 内下载 APK，完成后直接调起系统安装器。 */
    private void downloadAndInstall(final String url) {
        final android.app.ProgressDialog pd = new android.app.ProgressDialog(host);
        pd.setTitle("正在下载新版本");
        pd.setMessage("0%");
        pd.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                java.io.File out = new java.io.File(host.getCacheDir(), "update.apk");
                String err = null;
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                            new java.net.URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0");
                    int total = c.getContentLength();
                    java.io.InputStream in = c.getInputStream();
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(out);
                    byte[] buf = new byte[8192];
                    long got = 0;
                    int n;
                    int lastPct = -1;
                    while ((n = in.read(buf)) > 0) {
                        fo.write(buf, 0, n);
                        got += n;
                        if (total > 0) {
                            final int pct = (int) (got * 100 / total);
                            if (pct != lastPct) {
                                lastPct = pct;
                                host.runOnUiThread(new Runnable() {
                                    int p = pct;
                                    public void run() {
                                        pd.setMessage(p + "%");
                                        pd.setProgress(p);
                                    }
                                });
                            }
                        }
                    }
                    fo.close();
                    in.close();
                    if (out.length() < 10000) err = "下载不完整";
                } catch (Throwable t) {
                    err = String.valueOf(t);
                }

                final String fErr = err;
                final java.io.File fOut = out;
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pd.isShowing()) pd.dismiss();
                        if (host.isFinishing() || host.isDestroyed()) return;
                        if (fErr != null) {
                            Toast.makeText(ctx, "下载失败：" + fErr,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        installApk(fOut);
                    }
                });
            }
        }).start();
    }

    /** 调起系统安装器（优先 PackageInstaller API，失败再降级为 Intent）。 */
    private void installApk(final java.io.File apk) {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            try {
                android.content.pm.PackageInstaller pi = host.getPackageManager().getPackageInstaller();
                android.content.pm.PackageInstaller.SessionParams params =
                        new android.content.pm.PackageInstaller.SessionParams(
                                android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(ctx.getPackageName());
                int sid = pi.createSession(params);
                android.content.pm.PackageInstaller.Session session = pi.openSession(sid);
                java.io.FileInputStream in = new java.io.FileInputStream(apk);
                java.io.OutputStream out = session.openWrite("base.apk", 0, apk.length());
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
                out.close();
                in.close();
                android.content.IntentSender cb = android.app.PendingIntent.getBroadcast(
                        ctx, sid,
                        new Intent(ctx, InstallResultReceiver.class),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                | android.app.PendingIntent.FLAG_MUTABLE).getIntentSender();
                session.commit(cb);
                Toast.makeText(ctx, "请在提示中确认安装", Toast.LENGTH_LONG).show();
                return;
            } catch (Throwable t) {
                android.util.Log.i("Daka", "PackageInstaller failed: " + t);
            }
        }
        // 降级：用 content:// 调起系统安装器
        try {
            Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            i.setData(ApkProvider.uriFor(apk));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            host.startActivity(i);
        } catch (Throwable t) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(ApkProvider.uriFor(apk),
                        "application/vnd.android.package-archive");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                host.startActivity(i);
            } catch (Throwable t2) {
                Toast.makeText(ctx, "无法调起安装器：" + t2,
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
