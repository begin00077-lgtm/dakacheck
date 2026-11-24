package com.dsh.dakacheck;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Calendar;

/**
 * 补检：闹钟可能因为省电策略、进程被杀、系统清理而没响。
 *
 * 在几个「用户一定会用到手机」的时机各检查一次：
 *   - 手机解锁
 *   - 打开本 App
 *   - App 进程被拉起 / 无障碍服务重连
 *
 * 判断规则：
 *   1. 今天必须是工作日
 *   2. 对应的核对时刻已经过去
 *   3. 这次核对今天还没执行过
 * 三条都满足 -> 立刻补一次核对（走和闹钟完全相同的流程）
 */
public final class CatchUp {

    /** 考勤周期起点：04:00 → 次日 04:00。 */
    static final int CYCLE_START_HOUR = 4;

    private CatchUp() {
    }

    /** 三个时机统一调用；不会重复响铃，一天一次。 */
    public static void check(Context c) {
        try {
            Calendar now = Calendar.getInstance();
            String date = Store.logicalDate();
            boolean workday = CheckReceiver.isWorkday(c, date);
            Log.i("Daka", "catch-up probe date=" + date + " workday=" + workday
                    + " inPassed=" + passed(c, now, true)
                    + " outPassed=" + passed(c, now, false)
                    + " inDone=" + Store.checkedDone(c, date, Scheduler.KIND_IN)
                    + " outDone=" + Store.checkedDone(c, date, Scheduler.KIND_OUT));

            if (!workday) {
                return;
            }

            // 上班卡核对时刻
            if (passed(c, now, true) && !Store.checkedDone(c, date, Scheduler.KIND_IN)) {
                Log.i("Daka", "catch-up: 上班卡核对漏掉了，补一次");
                Timeline.add(c, "补检：上班卡核对（闹钟可能没响）");
                fire(c, Scheduler.KIND_IN);
            }

            // 下班卡核对时刻（下班卡查的是前一天，用同一个 logicalDate）
            if (passed(c, now, false) && !Store.checkedDone(c, date, Scheduler.KIND_OUT)) {
                Log.i("Daka", "catch-up: 下班卡核对漏掉了，补一次");
                Timeline.add(c, "补检：下班卡核对（闹钟可能没响）");
                fire(c, Scheduler.KIND_OUT);
            }
        } catch (Throwable t) {
            Log.i("Daka", "catch-up failed " + t);
        }
    }

    /**
     * 本次核对是否已经到时间。
     *
     * 考勤周期是 04:00 → 次日 04:00。
     * 签退核对时刻通常在凌晨（02:30），它检查的是「当前考勤周期那一天」的签退。
     *
     * 例：11-24 08:48 上班，其签退要到 <b>11-25 02:30</b> 才核对。
     * 因此 11-24 白天（04:00 之后）绝不能就判定「没签退」，否则下午一解锁就会误响铃。
     */
    private static boolean passed(Context c, Calendar now, boolean kindIn) {
        int h = kindIn ? Config.inHour(c) : Config.outHour(c);
        int m = kindIn ? Config.inMin(c) : Config.outMin(c);
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int targetMin = h * 60 + m;
        int cycle = CYCLE_START_HOUR * 60;

        if (kindIn) {
            // 上班卡：当天的核对时刻过了就算过了
            return nowMin >= targetMin;
        }

        // 签退卡：只有「凌晨 04:00 前」才可能已经到时间。
        // 04:00 之后的白天时间，签退核对要等到次日凌晨，现在不能提前核对。
        if (nowMin >= cycle) {
            return false;
        }
        return nowMin >= targetMin;
    }

    /** 直接走 CheckReceiver 的完整流程。 */
    private static void fire(Context c, String kind) {
        try {
            Intent i = new Intent(c, CheckReceiver.class);
            i.putExtra(CheckReceiver.EXTRA_KIND, kind);
            i.putExtra(CheckReceiver.EXTRA_CATCHUP, true);
            c.sendBroadcast(i);
        } catch (Throwable t) {
            Log.i("Daka", "catch-up fire failed " + t);
        }
    }
}
