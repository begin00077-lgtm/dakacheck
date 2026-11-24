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

    private boolean pendingPermRefresh = false;
    private View rootView;
    private ViewGroup container;

    @Override
    public View build() {
        rootView = buildInner();
        return rootView;
    }

    /** 页面被切到时重建，保证权限状态是最新的。 */
    @Override
    public void onShown() {
        rebuild();
        if (pendingPermRefresh) {
            pendingPermRefresh = false;
            // 从系统授权页返回：重开「所有授权项」，开关已按真实状态重建
            showAllPerms();
        }
    }

    /** 把新内容换进父容器。 */
    private void rebuild() {
        try {
            if (rootView == null || rootView.getParent() == null) return;
            ViewGroup parent = (ViewGroup) rootView.getParent();
            int idx = parent.indexOfChild(rootView);
            parent.removeView(rootView);
            View fresh = buildInner();
            parent.addView(fresh, idx);
            rootView = fresh;
        } catch (Throwable t) {
            android.util.Log.i("Daka", "settings rebuild failed: " + t);
        }
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

        root.addView(row("权限申请", null,
                "查看并单独授权每一项", new Runnable() {
                    public void run() {
                        showAllPerms();
                    }
                }));

        final boolean alertsAll = alertsAllOn();
        root.addView(row("掉线提醒", alertsAll,
                alertsAll ? "权限被关掉会发通知" : "已关闭",
                new Runnable() {
                    public void run() {
                        showAlertSettings();
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

        root.addView(row("清理更新缓存", null,
                "删除已下载的安装包", new Runnable() {
                    public void run() {
                        final int n = ApkProvider.purgeAll(ctx);
                        new android.app.AlertDialog.Builder(host)
                                .setTitle("清理完成")
                                .setMessage(n > 0
                                        ? "已删除 " + n + " 个安装包。"
                                        : "没有需要清理的安装包。")
                                .setPositiveButton("知道了", null)
                                .show();
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
                java.io.File out = new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS),
                        "打卡提醒-更新.apk");
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
                            Notify.info(ctx, "下载失败",
                                    "无法下载更新包：" + fErr);
                            return;
                        }
                        // 先告知文件位置，万一安装界面没弹出也能自己找到
                        Toast.makeText(ctx,
                                "已下载到：Download/打卡提醒-更新.apk",
                                Toast.LENGTH_LONG).show();
                        Notify.info(ctx, "新版本已下载",
                                "安装包在文件管理器→Download→「打卡提醒-更新.apk」");
                        installApk(fOut);
                    }
                });
            }
        }).start();
    }

    /** 调起系统安装器（优先 PackageInstaller API，失败再降级为 Intent）。 */
    private java.io.File pendingApk;

    private void installApk(final java.io.File apk) {
        pendingApk = apk;
        ApkProvider.markPending(ctx, apk);
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

    /** 全部需要开的权限，顺序弹出。 */
    private final String[] PERM_NAMES = {
            "无障碍服务",
            "通知",
            "精确闹钟",
            "电池优化",
            "全屏提醒",
            "使用情况访问",
            "文件访问",
            "安装未知应用"
    };

    private final String[] PERM_TIPS = {
            "在列表里找到「打卡提醒」，打开开关。\n\n这是最重要的一项，没它读不到打卡状态。",
            "允许发送通知，否则提醒时看不到提示。",
            "允许设置精确闹钟，否则提醒可能延迟。",
            "选择「不限制」，防止后台被杀。",
            "允许锁屏时弹出报警。",
            "找到「打卡提醒」，打开。",
            "允许访问所有文件，用来自动导出/导入打卡记录。",
            "允许安装应用，App 内自动更新需要。"
    };

        /** 权限申请：只展示授权状态，点一行去系统授权。 */
    private void showAllPerms() {
        final java.util.List<android.widget.Switch> authSwitches =
                new java.util.ArrayList<>();
        final SettingsPage self = this;

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pd = (int) dp(12);
        box.setPadding(pd, pd, pd, 0);

        for (int i = 0; i < PERM_NAMES.length; i++) {
            final int idx = i;
            boolean granted = checkPerm(i);

            LinearLayout item = new LinearLayout(ctx);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding((int) dp(10), (int) dp(14), (int) dp(6), (int) dp(14));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(10));
            bg.setColor(Color.WHITE);
            item.setBackground(bg);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.bottomMargin = (int) dp(6);

            TextView name = new TextView(ctx);
            name.setText(PERM_NAMES[i]);
            name.setTextSize(15f);
            name.setTextColor(Color.parseColor("#111111"));
            item.addView(name, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final android.widget.Switch sw = new android.widget.Switch(ctx);
            sw.setChecked(granted);
            sw.setClickable(false);
            sw.setFocusable(false);
            sw.setTag(Integer.valueOf(idx));
            authSwitches.add(sw);
            item.addView(sw);

            item.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    openPermSettings(idx);
                }
            });
            box.addView(item, ilp);
        }

        TextView tip = new TextView(ctx);
        tip.setText("点一行去系统授权，回来自动刷新。\n"
                + "权限只能在系统设置里关闭，App 不会主动关。");
        tip.setTextSize(12f);
        tip.setTextColor(Color.parseColor("#888888"));
        tip.setPadding((int) dp(4), (int) dp(10), (int) dp(4), (int) dp(4));
        box.addView(tip);

        ScrollView sv = new ScrollView(ctx);
        sv.addView(box);

        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(host)
                .setTitle("权限申请")
                .setView(sv)
                .setPositiveButton("关闭", null)
                .create();

        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable ticker = new Runnable() {
            @Override
            public void run() {
                try {
                    for (android.widget.Switch sw : authSwitches) {
                        int idx = ((Integer) sw.getTag()).intValue();
                        boolean real = self.checkPerm(idx);
                        if (sw.isChecked() != real) {
                            sw.setChecked(real);
                        }
                    }
                } catch (Throwable ignored) {
                }
                if (dlg.isShowing()) {
                    h.postDelayed(this, 800);
                }
            }
        };

        dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                h.removeCallbacks(ticker);
            }
        });
        dlg.show();
        h.postDelayed(ticker, 500);
    }

    /** 掉线提醒设置：逐项开关 + 一键全开。 */
    private void showAlertSettings() {
        final java.util.List<android.widget.Switch> switches =
                new java.util.ArrayList<>();

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pd = (int) dp(12);
        box.setPadding(pd, pd, pd, 0);

        // 一键全开
        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding((int) dp(10), (int) dp(10), (int) dp(6), (int) dp(10));
        GradientDrawable tbg = new GradientDrawable();
        tbg.setCornerRadius(dp(10));
        tbg.setColor(Color.parseColor("#E8F5E9"));
        top.setBackground(tbg);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = (int) dp(10);

        TextView tName = new TextView(ctx);
        tName.setText("全部开启");
        tName.setTextSize(15f);
        tName.setTypeface(null, Typeface.BOLD);
        tName.setTextColor(Color.parseColor("#2E7D32"));
        top.addView(tName, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final android.widget.Switch allSw = new android.widget.Switch(ctx);
        allSw.setChecked(alertsAllOn());
        allSw.setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        setAlertsAll(on);
                        for (int k = 0; k < switches.size(); k++) {
                            switches.get(k).setChecked(on);
                        }
                    }
                });
        top.addView(allSw);
        box.addView(top, tlp);

        for (int i = 0; i < PERM_NAMES.length; i++) {
            final int idx = i;
            boolean on = alertEnabled(i);

            LinearLayout item = new LinearLayout(ctx);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding((int) dp(10), (int) dp(10), (int) dp(6), (int) dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(10));
            bg.setColor(Color.WHITE);
            item.setBackground(bg);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.bottomMargin = (int) dp(6);

            TextView name = new TextView(ctx);
            name.setText(PERM_NAMES[i]);
            name.setTextSize(15f);
            name.setTextColor(Color.parseColor("#111111"));
            item.addView(name, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            android.widget.Switch sw = new android.widget.Switch(ctx);
            sw.setChecked(on);
            sw.setOnCheckedChangeListener(
                    new android.widget.CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                            setAlertEnabled(idx, v);
                            // 同步「全部开启」
                            allSw.setOnCheckedChangeListener(null);
                            allSw.setChecked(alertsAllOn());
                            allSw.setOnCheckedChangeListener(
                                    new android.widget.CompoundButton.OnCheckedChangeListener() {
                                        @Override
                                        public void onCheckedChanged(
                                                android.widget.CompoundButton bb, boolean o) {
                                            setAlertsAll(o);
                                            for (int k = 0; k < switches.size(); k++) {
                                                switches.get(k).setChecked(o);
                                            }
                                        }
                                    });
                        }
                    });
            switches.add(sw);
            item.addView(sw);

            box.addView(item, ilp);
        }

        TextView tip = new TextView(ctx);
        tip.setText("开启后，对应权限被系统关掉时，通知栏会提醒你。\n"
                + "同一项 12 小时内不会重复提醒。");
        tip.setTextSize(12f);
        tip.setTextColor(Color.parseColor("#888888"));
        tip.setPadding((int) dp(4), (int) dp(8), (int) dp(4), (int) dp(4));
        box.addView(tip);

        ScrollView sv = new ScrollView(ctx);
        sv.addView(box);

        new android.app.AlertDialog.Builder(host)
                .setTitle("掉线提醒")
                .setView(sv)
                .setPositiveButton("关闭", null)
                .show();
    }

    // ---------- 掉线提醒开关存储 ----------

    private static final String P_PREF = "perm_alert";
    private static final String P_ALL = "all";

    private static String alertKey(int idx) {
        return "p" + idx;
    }

    /** 某项掉线提醒是否开启（默认开）。 */
    static boolean alertEnabled(Context c, int idx) {
        return c.getSharedPreferences(P_PREF, 0).getBoolean(alertKey(idx), true);
    }

    private boolean alertEnabled(int idx) {
        return alertEnabled(ctx, idx);
    }

    static void setAlertEnabled(Context c, int idx, boolean on) {
        c.getSharedPreferences(P_PREF, 0).edit()
                .putBoolean(alertKey(idx), on).apply();
    }

    private void setAlertEnabled(int idx, boolean on) {
        setAlertEnabled(ctx, idx, on);
    }

    /** 全部提醒都开着？ */
    static boolean alertsAllOn(Context c) {
        for (int i = 0; i < 8; i++) {
            if (!alertEnabled(c, i)) return false;
        }
        return true;
    }

    private boolean alertsAllOn() {
        return alertsAllOn(ctx);
    }

    private void setAlertsAll(boolean on) {
        android.content.SharedPreferences.Editor e =
                ctx.getSharedPreferences(P_PREF, 0).edit();
        for (int i = 0; i < 8; i++) {
            e.putBoolean(alertKey(i), on);
        }
        e.apply();
    }

    /** 供监控类调用：该项权限现在是否需要发提醒。 */
    static boolean shouldNotify(Context c, int idx) {
        return alertEnabled(c, idx) && !checkPermStatic(c, idx);
    }

    /** 静态版权限检查，供后台监控使用。 */
    static boolean checkPermStatic(Context c, int idx) {
        try {
            switch (idx) {
                case 0:
                    return Guard.accessibilityEnabled(c);
                case 1: {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        return c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                                == android.content.pm.PackageManager.PERMISSION_GRANTED;
                    }
                    android.app.NotificationManager nm = (android.app.NotificationManager)
                            c.getSystemService(Context.NOTIFICATION_SERVICE);
                    return nm != null && nm.areNotificationsEnabled();
                }
                case 2:
                    return Scheduler.canExact(c);
                case 3: {
                    android.os.PowerManager pm = (android.os.PowerManager)
                            c.getSystemService(Context.POWER_SERVICE);
                    return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
                }
                case 4:
                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                        android.app.NotificationManager nm2 = (android.app.NotificationManager)
                                c.getSystemService(Context.NOTIFICATION_SERVICE);
                        return nm2 != null && nm2.canUseFullScreenIntent();
                    }
                    return true;
                case 5:
                    return Usage.hasPermission(c);
                case 6:
                    return Archive.canWrite();
                case 7:
                    return c.getPackageManager().canRequestPackageInstalls();
                default:
                    return true;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /** 名称列表，供提醒文案使用。 */
    static String permName(int idx) {
        String[] names = {"无障碍服务", "通知", "精确闹钟", "电池优化",
                "全屏提醒", "使用情况访问", "文件访问", "安装未知应用"};
        return idx >= 0 && idx < names.length ? names[idx] : "?";
    }

    static int permCount() {
        return 8;
    }

    private boolean checkPerm(int idx) {
        switch (idx) {
            case 0: return accessibilityOn();
            case 1: return notificationsOn();
            case 2: return Scheduler.canExact(ctx);
            case 3: return batteryAllowed();
            case 4: return fullScreenIntent();
            case 5: return Usage.hasPermission(ctx);
            case 6: return Archive.canWrite();
            case 7: return canInstallPackages();
            default: return true;
        }
    }

    /** 是否已允许发通知。 */
    private boolean notificationsOn() {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                return host.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
            }
            return NotificationManagerCompatFromContext();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Android 13 之前的通知开关判定。 */
    private boolean NotificationManagerCompatFromContext() {
        try {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.areNotificationsEnabled();
        } catch (Throwable t) {
            return true;
        }
    }

    /** 是否允许安装未知应用。 */
    private boolean canInstallPackages() {
        try {
            if (android.os.Build.VERSION.SDK_INT < 26) return true;
            return host.getPackageManager().canRequestPackageInstalls();
        } catch (Throwable t) {
            return false;
        }
    }

    private void openPermSettings(int idx) {
        try {
            switch (idx) {
                case 0: {
                    // 无障碍：先试直接定位到本 App 的页面
                    Intent i = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
                    i.setData(Uri.parse("package:" + ctx.getPackageName()));
                    if (!tryStart(i)) {
                        tryStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    }
                    break;
                }
                case 1: {
                    // 通知：始终跳系统通知设置页（已授权时 requestPermissions 不会弹窗）
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    i.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
                    if (!tryStart(i)) {
                        Intent i2 = new Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        i2.setData(Uri.parse("package:" + ctx.getPackageName()));
                        if (!tryStart(i2)) {
                            // 最后手段：还未授权就直接请求
                            if (!checkPerm(1) && android.os.Build.VERSION.SDK_INT >= 33) {
                                host.requestPermissions(
                                        new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
                            }
                        }
                    }
                    break;
                }
                case 2: {
                    Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    i.setData(Uri.parse("package:" + ctx.getPackageName()));
                    if (!tryStart(i)) {
                        openAppDetails();
                    }
                    break;
                }
                case 3: {
                    // 已经在白名单里时，再请求会被系统忽略（不跳转）；
                    // 这种情况直接去电池优化列表页。
                    boolean ignoring = false;
                    try {
                        android.os.PowerManager pm = (android.os.PowerManager)
                                ctx.getSystemService(Context.POWER_SERVICE);
                        ignoring = pm != null
                                && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
                    } catch (Throwable ignored) {
                    }

                    if (!ignoring) {
                        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        i.setData(Uri.parse("package:" + ctx.getPackageName()));
                        if (tryStart(i)) break;
                    }
                    // 已忽略 或 申请页打不开：跳列表页
                    Intent i2 = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    if (!tryStart(i2)) {
                        openAppDetails();
                    }
                    break;
                }
                case 4: {
                    Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                    i.setData(Uri.parse("package:" + ctx.getPackageName()));
                    if (!tryStart(i)) {
                        openAppDetails();
                    }
                    break;
                }
                case 5: {
                    if (!tryStart(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))) {
                        openAppDetails();
                    }
                    break;
                }
                case 6: {
                    Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    i.setData(Uri.parse("package:" + ctx.getPackageName()));
                    if (!tryStart(i)) {
                        Intent i2 = new Intent(
                                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        if (!tryStart(i2)) {
                            openAppDetails();
                        }
                    }
                    break;
                }
                case 7: {
                    Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    i.setData(Uri.parse("package:" + ctx.getPackageName()));
                    if (!tryStart(i)) {
                        openAppDetails();
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            Toast.makeText(ctx, "无法打开设置页：" + t, Toast.LENGTH_SHORT).show();
        }
    }

    /** 尝试打开一个设置页，成功返回 true。 */
    private boolean tryStart(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            host.startActivity(i);
            return true;
        } catch (Throwable t) {
            android.util.Log.i("Daka", "open settings failed: " + t);
            return false;
        }
    }

    /** 兜底：打开本应用的系统详情页。 */
    private void openAppDetails() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.parse("package:" + ctx.getPackageName()));
        if (!tryStart(i)) {
            Toast.makeText(ctx, "无法打开系统设置", Toast.LENGTH_SHORT).show();
        }
    }
}
