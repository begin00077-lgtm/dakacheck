package com.dsh.dakacheck;

import android.content.Context;
import android.util.Log;

/** 统一裁决：确认没打 → 响铃；确认打了 → 静默；读不到页面 → 看使用记录再决定。 */
public final class Decide {
    private Decide() {
    }

    public static void confirmedMissing(Context c, String kind, String date) {
        Log.i("Daka", "decide missing " + kind + " " + date);
        if (Scheduler.KIND_IN.equals(kind)) {
            Notify.alarm(c, "还没打上班卡", date + " 的考勤页上看不到「已签到」，去打个卡。", kind);
        } else {
            Notify.alarm(c, "还没签退", date + " 的考勤页上看不到「已签退」，记得签退。", kind);
        }
    }

    public static void confirmedDone(Context c, String kind, String date, String time) {
        Log.i("Daka", "decide done " + kind + " " + date + " " + time);
        if (Scheduler.KIND_IN.equals(kind)) {
            Notify.info(c, "上班卡已打", date + " " + (time == null || time.isEmpty() ? "已签到" : time) + "，不用提醒。");
        } else {
            Notify.info(c, "下班卡已打", date + " " + (time == null || time.isEmpty() ? "已签退" : time) + "，不用提醒。");
        }
    }

    /**
     * 没能亲眼读到页面（锁屏、加载失败等）。
     * 先查系统使用记录：如果在我们最后一次读取之后，软通打卡根本没被打开过，
     * 那基本可以断定是真没打卡；如果打开过却没读到，就只发通知，避免半夜误响。
     */
    public static void unverified(Context c, String kind, String date) {
        long lastRead = Store.pageAt(c, date);
        long baseline = lastRead > 0 ? lastRead : Store.attendanceDayStart();
        boolean usageKnown = Usage.hasPermission(c);
        long lastFg = usageKnown
                ? Usage.lastForegroundSince(c, AttendanceService.TARGET, baseline)
                : 0L;
        // 之后你打开过软通打卡却没被读到 → 很可能只是漏读，别误炸
        boolean mayHavePunched = usageKnown && lastFg > baseline;
        Log.i("Daka", "decide unverified " + kind + " " + date + " lastRead=" + lastRead
                + " baseline=" + baseline + " lastFg=" + lastFg
                + " usageKnown=" + usageKnown + " mayHavePunched=" + mayHavePunched);

        if (mayHavePunched) {
            Notify.unknown(c, Scheduler.KIND_IN.equals(kind) ? "没读到上班卡记录" : "没读到签退记录",
                    date + " 你打开过软通打卡，但插件没读到记录，可能只是漏读。点开确认一下，免得误判。");
            return;
        }

        String title = Scheduler.KIND_IN.equals(kind) ? "还没打上班卡" : "还没签退";
        String body = Scheduler.KIND_IN.equals(kind)
                ? "读不到考勤页，而且今天之后没再打开过软通打卡。如果已经打过卡，点「我已打卡」即可。"
                : date + " 没读到签退记录，之后也没再打开过软通打卡。如果已经签退，点「我已打卡」即可。";
        Notify.alarm(c, title, body, kind);
    }
}
