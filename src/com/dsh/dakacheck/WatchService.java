package com.dsh.dakacheck;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * 常驻前台服务：保持进程活着（无障碍服务跟着活），每 60 秒自检一次，
 * 掉线就重新拉起；通知栏常显当前状态，掉线你能一眼看见。
 */
public class WatchService extends Service {
    public static final String CH = "watch";
    public static final int ID = 5001;
    private static final long TICK_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                Guard.ensure(WatchService.this);
                updateNotification(WatchService.this);
            } catch (Throwable t) {
                Log.i("Daka", "tick failed: " + t);
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel(this);
        startForegroundCompat(this, build());
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannel(this);
        startForegroundCompat(this, build());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void start(Context c) {
        try {
            Intent i = new Intent(c, WatchService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable t) {
            Log.i("Daka", "start watch failed: " + t);
        }
    }

    private static String lastText = "";

    public static void updateNotification(Context c) {
        try {
            String text = buildTitle(c) + "|" + buildText(c);
            if (text.equals(lastText)) return;
            lastText = text;
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(ID, build());
        } catch (Throwable ignored) {
        }
    }

    private static String buildTitle(Context c) {
        return "打卡提醒 · " + (Guard.accessibilityEnabled(c) ? "监控正常" : "监控已掉线");
    }

    private static String buildText(Context c) {
        String date = Store.logicalDate();
        String in = Store.getIn(c, date);
        String out = Store.getOut(c, date);
        return date + "　" + (in == null ? "上班 未读到" : "上班 " + (in.isEmpty() ? "已签" : in))
                + " · " + (out == null ? "下班 未读到" : (out.isEmpty() ? "下班 已签" : "下班 " + out));
    }

    private static void startForegroundCompat(Service s, Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                s.startForeground(ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                s.startForeground(ID, n);
            }
        } catch (Throwable t) {
            Log.i("Daka", "startForeground failed: " + t);
        }
    }

    private static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CH, "常驻状态", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private static Notification build() {
        Context c = App.get();
        String date = Store.logicalDate();
        String in = Store.getIn(c, date);
        String out = Store.getOut(c, date);
        String state = (in == null ? "上班 未读到" : "上班 " + (in.isEmpty() ? "已签" : in))
                + " · " + (out == null ? "下班 未读到" : (out.isEmpty() ? "下班 已签" : "下班 " + out));
        String health = Guard.accessibilityEnabled(c) ? "监控正常" : "监控已掉线（正在尝试恢复）";

        Intent it = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 7001, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("打卡提醒 · " + health)
                .setContentText(date + "　" + state)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi);
        return b.build();
    }
}
