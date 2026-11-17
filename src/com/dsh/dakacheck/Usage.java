package com.dsh.dakacheck;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

/**
 * 借系统的「应用使用情况」判断软通打卡最近一次被打开的时间。
 * 用来区分两种「没读到记录」：
 *   - 打卡 App 在我们最后一次读取之后根本没被打开过 → 基本可以断定真没打
 *   - 之后打开过但没读到 → 可能是我们漏了，不能当成没打
 */
public final class Usage {
    private Usage() {
    }

    public static boolean hasPermission(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode;
            if (Build.VERSION.SDK_INT >= 29) {
                mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), c.getPackageName());
            } else {
                mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), c.getPackageName());
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 目标 App 在 since 之后的最后一次前台时间；没查到返回 0。 */
    public static long lastForegroundSince(Context c, String pkg, long since) {
        if (!hasPermission(c)) return 0L;
        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return 0L;
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(Math.max(since - 60_000L, now - 7L * 24 * 3600_000L), now);
            if (events == null) return 0L;
            UsageEvents.Event e = new UsageEvents.Event();
            long last = 0L;
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                if (!pkg.equals(e.getPackageName())) continue;
                boolean fg;
                if (Build.VERSION.SDK_INT >= 29) {
                    fg = e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED;
                } else {
                    fg = e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND;
                }
                if (fg && e.getTimeStamp() > last) last = e.getTimeStamp();
            }
            return last;
        } catch (Throwable t) {
            return 0L;
        }
    }
}
