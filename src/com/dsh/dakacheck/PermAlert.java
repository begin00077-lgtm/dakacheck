package com.dsh.dakacheck;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 权限掉线提醒。
 *
 * 每项权限单独判断：该权限被关掉、且用户在设置页打开了「掉线提醒」，就发一条通知。
 * 同一种权限 12 小时内不会重复提醒，避免刷屏。
 */
public final class PermAlert {

    private static final String PREF = "perm_alert_last";
    private static final long COOLDOWN = 12 * 3600_000L;
    private static final String CH = "perm_alert_ch";

    private PermAlert() {
    }

    /** 检查全部 8 项权限，掉线的发通知。 */
    public static void check(Context c) {
        if (c == null) return;
        try {
            for (int i = 0; i < SettingsPage.permCount(); i++) {
                if (!SettingsPage.shouldNotify(c, i)) {
                    // 权限已恢复：清掉记录，下次掉了能立刻再提醒
                    if (SettingsPage.checkPermStatic(c, i)) {
                        clearLast(c, i);
                    }
                    continue;
                }
                // 需要提醒
                if (!expired(c, i)) continue;
                post(c, i);
                markLast(c, i);
            }
        } catch (Throwable t) {
            Log.i("Daka", "PermAlert check failed: " + t);
        }
    }

    /** 单发一条「某项权限掉了」的通知。 */
    public static void post(Context c, int idx) {
        try {
            NotificationManager nm = (NotificationManager)
                    c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new android.app.NotificationChannel(
                        CH, "权限掉线提醒", NotificationManager.IMPORTANCE_HIGH));
            }

            String name = SettingsPage.permName(idx);
            String title = "「" + name + "」权限掉了";
            String body = tipOf(idx);

            Intent it = new Intent(c, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, 3000 + idx, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(c, CH)
                    : new Notification.Builder(c);
            b.setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true);
            nm.notify(3000 + idx, b.build());
            Log.i("Daka", "PermAlert notified: " + name);
        } catch (Throwable t) {
            Log.i("Daka", "PermAlert post failed: " + t);
        }
    }

    private static String tipOf(int idx) {
        switch (idx) {
            case 0:
                return "打卡监控读不到打卡状态了，点开 App 重新开启无障碍服务。";
            case 1:
                return "通知权限被关掉，打卡提醒将收不到提示。";
            case 2:
                return "精确闹钟权限被关掉，提醒时间可能不准。";
            case 3:
                return "电池优化限制回来了，后台可能被杀，提醒可能不响。";
            case 4:
                return "全屏提醒被关掉，锁屏时看不到报警。";
            case 5:
                return "使用情况访问被关掉，可能误报打卡状态。";
            case 6:
                return "文件访问权限被关掉，「打卡记录」无法自动备份。";
            case 7:
                return "安装未知应用被关掉，App 内自动更新无法安装。";
            default:
                return "该权限被关闭，相关功能可能失效。";
        }
    }

    private static boolean expired(Context c, int idx) {
        long last = c.getSharedPreferences(PREF, 0).getLong("t" + idx, 0L);
        return System.currentTimeMillis() - last > COOLDOWN;
    }

    private static void markLast(Context c, int idx) {
        c.getSharedPreferences(PREF, 0).edit()
                .putLong("t" + idx, System.currentTimeMillis()).apply();
    }

    private static void clearLast(Context c, int idx) {
        c.getSharedPreferences(PREF, 0).edit().remove("t" + idx).apply();
    }
}
