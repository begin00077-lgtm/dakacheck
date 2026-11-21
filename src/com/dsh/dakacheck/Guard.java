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

    /** 无障碍服务是否已启用（以系统设置为准，全局唯一口径）。 */
    public static boolean accessibilityEnabled(Context c) {
        if (c == null) return false;
        try {
            Context app = c.getApplicationContext();
            String enabled = Settings.Secure.getString(app.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null || enabled.isEmpty()) return false;
            String on = Settings.Secure.getString(app.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
            if (!"1".equals(on)) return false;
            // 直接看服务名是否出现在启用列表里
            for (String item : enabled.split(":")) {
                String s = item.trim();
                int slash = s.indexOf('/');
                if (slash < 0) continue;
                if ("com.dsh.dakacheck".equals(s.substring(0, slash))
                        && s.substring(slash + 1).endsWith("AttendanceService")) {
                    return true;
                }
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
            // 有 WRITE_SECURE_SETTINGS（只能 adb / Shizuku 授予）就自己写回系统设置把服务拉起来
            if (canRevive(c) && reviveAccessibility(c)) {
                Timeline.add(c, "无障碍已自动重开");
                Log.i("Daka", "a11y auto revived");
                return;
            }
            // 没权限或拉起失败：提醒用户手动开
            long now = System.currentTimeMillis();
            if (now - lastA11yNotify > 6 * 3600_000L) {
                lastA11yNotify = now;
                Timeline.add(c, "无障碍未开启");
                Notify.info(c, "打卡监控需要无障碍",
                        "请到系统设置里重新开启「打卡提醒」的无障碍服务。");
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
    private static long lastA11yNotify = 0L;
}
