package com.dsh.dakacheck;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 唤醒入口 + 自测。
 * TEST_SCHEDULE：2 分钟后给自己排一个 AlarmClock 闹钟
 * TEST_PING：闹钟到点时被系统投递到这里（进程不在也应该被拉起来）
 */
public class WakeReceiver extends BroadcastReceiver {
    public static final String ACTION_WAKE = "com.dsh.dakacheck.WAKE";
    public static final String ACTION_TEST_SCHEDULE = "com.dsh.dakacheck.TEST_SCHEDULE";
    public static final String ACTION_TEST_PING = "com.dsh.dakacheck.TEST_PING";
    public static final String ACTION_FILL = "com.dsh.dakacheck.FILL";
    public static final String ACTION_WARDEN_TEST = "com.dsh.dakacheck.WARDEN_TEST";
    public static final String ACTION_WARDEN_CHECK = "com.dsh.dakacheck.WARDEN_CHECK";
    public static final String ACTION_WARDEN_ON = "com.dsh.dakacheck.WARDEN_ON";
    public static final String ACTION_WORKDAY_TEST = "com.dsh.dakacheck.WORKDAY_TEST";
    public static final String ACTION_CLEAR_COOKIE = "com.dsh.dakacheck.CLEAR_COOKIE";
    public static final String ACTION_BG_GRAB = "com.dsh.dakacheck.BG_GRAB";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        Log.i("Daka", "wake up by " + action);

        if (ACTION_FILL.equals(action)) {
            String date = intent.getStringExtra("date");
            if (date == null || date.trim().isEmpty()) date = Store.logicalDate();
            String in = intent.getStringExtra("in");
            String out = intent.getStringExtra("out");
            if (in != null) Store.putIn(context, date, in.trim());
            if (out != null) Store.putOut(context, date, out.trim());
            Log.i("Daka", "FILL " + date + " in=" + in + " out=" + out);
            Widgets.refreshAll(context);
            return;
        }
        if (ACTION_BG_GRAB.equals(action)) {
            String m = intent.getStringExtra("month");
            if (m == null) m = "";
            BackgroundGrab.start(context, m);
            return;
        }
        if (ACTION_CLEAR_COOKIE.equals(action)) {
            try {
                android.webkit.CookieManager.getInstance().removeAllCookies(null);
                android.webkit.CookieManager.getInstance().flush();
                android.webkit.WebStorage.getInstance().deleteAllData();
                Log.i("Daka", "cookies cleared");
            } catch (Throwable t) {
                Log.i("Daka", "clear cookie failed " + t);
            }
            return;
        }
        if (ACTION_WORKDAY_TEST.equals(action)) {
            String d = Store.logicalDate();
            boolean w = CheckReceiver.isWorkday(context, d);
            Log.i("Daka", "WORKDAY " + d + " workday=" + w);
            Timeline.add(context, "自查 " + d + " 工作日=" + w);
            return;
        }
        if (ACTION_WARDEN_ON.equals(action)) {
            Config.setKaoqin(context, true);
            String pwd = intent.getStringExtra("pwd");
            if (pwd != null) Config.setKaoqinPwd(context, pwd);
            String user = intent.getStringExtra("user");
            if (user != null) Config.setKaoqinUser(context, user);
            Warden.reschedule(context);
            Log.i("Daka", "warden enabled");
            return;
        }
        if (ACTION_WARDEN_CHECK.equals(action)) {
            Warden.check(context);
            return;
        }
        if (ACTION_TEST_SCHEDULE.equals(action)) {
            scheduleTest(context);
            return;
        }
        if (ACTION_TEST_PING.equals(action)) {
            Log.i("Daka", "TEST_PING delivered: process was started by alarm");
        }
        Guard.ensure(context);
    }

    private void scheduleTest(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = PendingIntent.getBroadcast(c, 77,
                    new Intent(c, WakeReceiver.class).setAction(ACTION_TEST_PING),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long at = System.currentTimeMillis() + 120_000L;
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, null), pi);
            Log.i("Daka", "TEST alarm scheduled at " + at);
        } catch (Throwable t) {
            Log.i("Daka", "TEST schedule failed: " + t);
        }
    }
}
