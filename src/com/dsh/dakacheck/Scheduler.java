package com.dsh.dakacheck;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Calendar;

/** 按配置的时刻排闹钟：上班卡查当天，下班卡按时刻决定查哪天。 */
public final class Scheduler {
    public static final String KIND_IN = "in";
    public static final String KIND_OUT = "out";

    private Scheduler() {
    }

    public static void ensure(Context c) {
        schedule(c, KIND_IN);
        schedule(c, KIND_OUT);
    }

    private static int reqCode(String kind) {
        return KIND_IN.equals(kind) ? 1001 : 1002;
    }

    private static int hour(Context c, String kind) {
        return KIND_IN.equals(kind) ? Config.inHour(c) : Config.outHour(c);
    }

    private static int minute(Context c, String kind) {
        return KIND_IN.equals(kind) ? Config.inMin(c) : Config.outMin(c);
    }

    public static long nextTrigger(Context c, String kind) {
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, hour(c, kind));
        t.set(Calendar.MINUTE, minute(c, kind));
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        if (t.getTimeInMillis() <= now.getTimeInMillis()) {
            t.add(Calendar.DAY_OF_MONTH, 1);
        }
        // 非工作日不核对打卡：直接跳到下一个工作日，周末/节假日根本不排闹钟
        int skipped = 0;
        for (int i = 0; i < 40; i++) {
            String date = String.format(java.util.Locale.CHINA, "%04d-%02d-%02d",
                    t.get(Calendar.YEAR), t.get(Calendar.MONTH) + 1,
                    t.get(Calendar.DAY_OF_MONTH));
            if (CheckReceiver.isWorkday(c, date)) break;
            skipped++;
            t.add(Calendar.DAY_OF_MONTH, 1);
        }
        if (skipped > 0) {
            Log.i("Daka", "alarm " + kind + " skipped " + skipped + " non-workday(s)");
        }
        return t.getTimeInMillis();
    }

    public static void schedule(Context c, String kind) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        // 总开关关掉：撤掉核对闹钟，不再提醒
        if (!Config.remindOn(c)) {
            PendingIntent old = PendingIntent.getBroadcast(c, reqCode(kind),
                    new Intent(c, CheckReceiver.class).putExtra(CheckReceiver.EXTRA_KIND, kind),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(old);
            Log.i("Daka", "remind off, alarm cancelled " + kind);
            return;
        }
        long at = nextTrigger(c, kind);
        PendingIntent pi = PendingIntent.getBroadcast(c, reqCode(kind),
                new Intent(c, CheckReceiver.class).putExtra(CheckReceiver.EXTRA_KIND, kind),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent show = PendingIntent.getActivity(c, 9000,
                new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            // 系统闹钟级精确：Doze 下也准点，且不依赖精确闹钟权限
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), pi);
            Log.i("Daka", "alarmClock " + kind + " at " + at);
        } catch (Throwable t) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                Log.i("Daka", "exact " + kind + " at " + at);
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                Log.i("Daka", "inexact " + kind + " at " + at);
            }
        }
    }

    /** 稍后再提醒。 */
    public static void snooze(Context c, int minutes, String kind) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, 1003,
                new Intent(c, CheckReceiver.class)
                        .putExtra(CheckReceiver.EXTRA_KIND, kind)
                        .putExtra(CheckReceiver.EXTRA_TEST, true),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long at = System.currentTimeMillis() + minutes * 60_000L;
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, null), pi);
        } catch (Throwable t) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        }
    }

    public static boolean canExact(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    /** 某次触发时刻，核对的是「哪一天」的考勤：0–4 点算前一天。 */
    public static String targetDateFor(long fireAt) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fireAt);
        if (cal.get(Calendar.HOUR_OF_DAY) < 4) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        return new java.text.SimpleDateFormat("MM-dd", java.util.Locale.CHINA).format(cal.getTime());
    }

    /** 触发时刻本身，展示用。 */
    public static String fireTimeFor(long fireAt) {
        return new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                .format(new java.util.Date(fireAt));
    }

    /** 没核对出来（比如锁屏）时，过一会儿再试。 */
    public static void scheduleRetry(Context c, String kind, int retry) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long delay = retry <= 1 ? 10 * 60_000L : 30 * 60_000L;
        long at = System.currentTimeMillis() + delay;
        PendingIntent pi = PendingIntent.getBroadcast(c, 1200 + retry,
                new Intent(c, CheckReceiver.class)
                        .putExtra(CheckReceiver.EXTRA_KIND, kind)
                        .putExtra(CheckReceiver.EXTRA_RETRY, retry),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, null), pi);
        } catch (Throwable t) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    /** 打卡高峰前把 App 叫醒一次，让无障碍重新绑定（否则打卡那一刻可能没人在听）。 */
    public static final int[] KEEPALIVE_HOURS = {8, 17};
    public static final int[] KEEPALIVE_MINUTES = {40, 20};

    /**
     * 打卡时段密集唤醒：7:00–9:30、17:00–20:00 每 10 分钟一次。
     * 用 setExactAndAllowWhileIdle —— 不会在状态栏留闹钟图标。
     */
    public static final int[] WIN_START = {7 * 60, 17 * 60};
    public static final int[] WIN_END = {9 * 60 + 30, 20 * 60};

    public static void ensurePunchWake(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = nextPunchSlot(System.currentTimeMillis());
        PendingIntent pi = PendingIntent.getBroadcast(c, 1400,
                new Intent(c, KeepAliveReceiver.class).putExtra("punch", true),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            Log.i("Daka", "punch wake at " + at);
        } catch (Throwable t) {
            Log.i("Daka", "punch wake failed: " + t);
        }
    }

    /** 测试模式：true 时每 30 秒唤醒一次（用于验证能否拉起死进程）。 */
    public static final boolean TEST_FAST = false;

    private static long nextPunchSlot(long now) {
        if (TEST_FAST) return now + 30_000L;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int m = c.get(Calendar.MINUTE);
        c.set(Calendar.MINUTE, m - m % 10);
        for (int i = 0; i < 1008; i++) {
            c.add(Calendar.MINUTE, 10);
            int mins = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            for (int w = 0; w < WIN_START.length; w++) {
                if (mins >= WIN_START[w] && mins <= WIN_END[w]) {
                    return c.getTimeInMillis();
                }
            }
        }
        return now + 10 * 60_000L;
    }

    /** 用 JobScheduler 保活（系统压制严重，仅作兜底）；同时撤掉老的 08:40 / 17:20 闹钟。 */
    public static void ensureKeepAlive(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            for (int i = 0; i < KEEPALIVE_HOURS.length; i++) {
                PendingIntent pi = PendingIntent.getBroadcast(c, 1300 + i,
                        new Intent(c, KeepAliveReceiver.class).putExtra("slot", i),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                try {
                    am.cancel(pi);
                } catch (Throwable ignored) {
                }
            }
        }
        android.app.job.JobScheduler js =
                (android.app.job.JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        try {
            android.app.job.JobInfo job = new android.app.job.JobInfo.Builder(
                    KeepAliveJob.JOB_ID, new android.content.ComponentName(c, KeepAliveJob.class))
                    // 一次性任务 + 执行完自排队：setPeriodic 最小 15 分钟，这样做才能跑到 10 秒
                    .setMinimumLatency(KeepAliveJob.INTERVAL_MS)
                    .setPersisted(true)
                    .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_NONE)
                    .build();
            js.schedule(job);
            Log.i("Daka", "keepalive job scheduled");
        } catch (Throwable t) {
            Log.i("Daka", "keepalive job failed: " + t);
        }
    }

    /** 旷工检查：每天 10:00 一次。 */
    public static void ensureWarden(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        // 考勤数据凌晨 4 点后刷新，14:30 再查才能判断准
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, 14);
        t.set(Calendar.MINUTE, 30);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis()) {
            t.add(Calendar.DAY_OF_MONTH, 1);
        }
        PendingIntent pi = PendingIntent.getBroadcast(c, 1600,
                new Intent(c, WardenReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
            }
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA);
            Log.i("Daka", "warden exact alarm at " + f.format(new java.util.Date(t.getTimeInMillis())));
        } catch (Throwable e) {
            Log.i("Daka", "warden alarm failed: " + e);
        }
    }
}
