package com.dsh.dakacheck;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 通知 + 全屏闹钟页。Android 10+ 后台不能直接起 Activity，用 fullScreenIntent 拉起来。 */
public final class Notify {
    public static final String CH_ALARM = "alarm_v2";
    public static final String CH_INFO = "info";
    public static final String CH_UNKNOWN = "unknown";
    private static final int ID_ALARM = 2001;
    private static final int ID_INFO = 3001;
    private static final int ID_UNKNOWN = 4001;

    private Notify() {
    }

    private static final java.util.Map<Integer, String> LAST_KEY = new java.util.HashMap<Integer, String>();
    private static final java.util.Map<Integer, Long> LAST_TS = new java.util.HashMap<Integer, Long>();

    /** 同样的内容 30 分钟内只弹一次，避免反复弹窗。 */
    private static boolean shouldPost(int id, String key) {
        long now = System.currentTimeMillis();
        String k = LAST_KEY.get(id);
        Long t = LAST_TS.get(id);
        if (key.equals(k) && t != null && now - t < 30 * 60_000L) return false;
        LAST_KEY.put(id, key);
        LAST_TS.put(id, now);
        return true;
    }

    public static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel a = new NotificationChannel(CH_ALARM, "打卡响铃", NotificationManager.IMPORTANCE_HIGH);
        // 注意：渠道一旦创建，声音/震动就不能再改，所以旧版本用过 "alarm" 的机器必须换新 ID。
        a.setSound(android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_ALARM),
                new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        a.enableVibration(true);
        a.setVibrationPattern(new long[]{0, 800, 600});
        a.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        a.setBypassDnd(true);
        nm.createNotificationChannel(a);
        NotificationChannel i = new NotificationChannel(CH_INFO, "打卡状态", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(i);
        NotificationChannel u = new NotificationChannel(CH_UNKNOWN, "没读到的打卡", NotificationManager.IMPORTANCE_DEFAULT);
        u.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(u);
    }

    public static void alarm(Context c, String title, String body, String kind) {
        ensureChannels(c);
        Intent it = new Intent(c, AlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(AlarmActivity.EXTRA_TITLE, title)
                .putExtra(AlarmActivity.EXTRA_BODY, body)
                .putExtra(AlarmActivity.EXTRA_KIND, kind);
        PendingIntent pi = PendingIntent.getActivity(c, ID_ALARM, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CH_ALARM)
                : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_MAX);

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_ALARM, b.build());

        // ⚠ Android 10+ 后台禁止 startActivity，靠它响铃经常失败。
        // 改由前台服务 RingService 发声 + 震动，不依赖 Activity 能不能拉起。
        try {
            c.startActivity(it);
        } catch (Exception ignored) {
        }
        RingService.start(c, title, body, kind);
    }

    public static void info(Context c, String title, String body) {
        ensureChannels(c);
        Intent it = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, ID_INFO, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CH_INFO)
                : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (!shouldPost(ID_INFO, title + "|" + body)) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_INFO, b.build());
    }

    /** 读不到考勤页：提醒但不炸响，避免半夜误吵。 */
    public static void unknown(Context c, String title, String body) {
        ensureChannels(c);
        Intent it = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, ID_UNKNOWN, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CH_UNKNOWN)
                : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_DEFAULT);
        if (!shouldPost(ID_UNKNOWN, title + "|" + body)) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_UNKNOWN, b.build());
    }

    public static void cancelAlarm(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(ID_ALARM);
    }
}
