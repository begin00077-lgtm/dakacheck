package com.dsh.dakacheck;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

/**
 * 自检 / 自愈：确认无障碍服务还在、闹钟还在，掉了就重新拉起。
 * 拉起无障碍需要 WRITE_SECURE_SETTINGS（用 adb 一次性授予），
 * 没有这个权限时只能发通知提醒人手点。
 */
public final class Guard {
    private static final String SERVICE =
            "com.dsh.dakacheck/com.dsh.dakacheck.AttendanceService";

    private Guard() {
    }

    public static boolean accessibilityEnabled(Context c) {
        try {
            String enabled = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            String flat = new ComponentName(c, AttendanceService.class).flattenToString();
            for (String s : enabled.split(":")) {
                if (flat.equalsIgnoreCase(s)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 是否拿到「自己写回系统设置」的能力（只能 adb 授予）。 */
    public static boolean canRevive(Context c) {
        try {
            return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 没有无障碍就写回系统设置，把服务拉起来。返回是否成功。 */
    public static boolean reviveAccessibility(Context c) {
        try {
            Settings.Secure.putString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, SERVICE);
            Settings.Secure.putString(c.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, "1");
            Log.i("Daka", "revived accessibility setting");
            return true;
        } catch (SecurityException e) {
            Log.i("Daka", "revive failed: no WRITE_SECURE_SETTINGS");
            return false;
        } catch (Throwable t) {
            Log.i("Daka", "revive failed: " + t);
            return false;
        }
    }

    /** 每次自检：闹钟在不在、无障碍开没开。**绝不主动关闭无障碍**（会造成系统报错）。 */
    public static void ensure(Context c) {
        Scheduler.ensure(c);
        Scheduler.ensureKeepAlive(c);
        Scheduler.ensurePunchWake(c);
        // 每天 14:30 同步考勤
        if (Config.kaoqinOn(c)) {
            Scheduler.ensureWarden(c);
        }
        if (!accessibilityEnabled(c)) {
            if (!reviveAccessibility(c)) {
                Timeline.add(c, "开关未开，且重开失败");
                Notify.info(c, "打卡监控掉线了", "无障碍服务被关闭，插件读不到打卡状态，点开重新开启。");
            }
            return;
        }
        // 开关开着但服务没连上：只记录，交给系统自己重连（强行关开会触发系统报错）
        if (!AttendanceService.connected) {
            long now = System.currentTimeMillis();
            if (now - lastRebind > 5 * 60_000L) {
                lastRebind = now;
                Timeline.add(c, "无障碍未连接（等待系统重连）");
            }
        }
    }

    private static long lastRebind = 0L;
}
