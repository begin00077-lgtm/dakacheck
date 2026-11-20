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

    /** 今天的这个核对时刻是否已经过去。 */
    private static boolean passed(Context c, Calendar now, boolean kindIn) {
        int h = kindIn ? Config.inHour(c) : Config.outHour(c);
        int m = kindIn ? Config.inMin(c) : Config.outMin(c);
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int targetMin = h * 60 + m;
        // 下班卡默认 02:00，属于「凌晨」，当天的 02:00 已经过了就算过了
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
