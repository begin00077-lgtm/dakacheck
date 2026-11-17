package com.dsh.dakacheck;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 到点后先看本地记录：已知打过卡就静默结束；没记录就交给 VerifyActivity 现场去看一眼，
 * 而不是直接按「没打卡」处理。
 */
public class CheckReceiver extends BroadcastReceiver {
    public static final String EXTRA_KIND = "kind";
    public static final String EXTRA_TEST = "test";
    public static final String EXTRA_RETRY = "retry";

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null) return;
        String kind = intent.getStringExtra(EXTRA_KIND);
        if (kind == null) kind = Scheduler.KIND_IN;
        boolean test = intent.getBooleanExtra(EXTRA_TEST, false);
        int retry = intent.getIntExtra(EXTRA_RETRY, 0);

        Scheduler.ensure(c);
        Scheduler.ensureKeepAlive(c);

        if (test) {
            String t = Scheduler.KIND_OUT.equals(kind)
                    ? "测试：还没签退的提醒" : "测试：还没打上班卡的提醒";
            Notify.alarm(c, t, "这是一次测试，点「我已打卡」即可停止响铃。", kind);
            return;
        }

        String date = targetDate(c, kind);

        // 非工作日（周末/法定假日，不含调休补班）不核对打卡，直接跳过
        if (!test && !isWorkday(c, date)) {
            Log.i("Daka", "skip non-workday " + date);
            Timeline.add(c, date + " 非工作日，跳过打卡核对");
            return;
        }

        if (Scheduler.KIND_IN.equals(kind)) {
            String in = Store.getIn(c, date);
            if (in != null) {
                Notify.info(c, "上班卡已打", date + " " + (in.isEmpty() ? "已签到" : in) + "。");
                return;
            }
        } else {
            String out = Store.getOut(c, date);
            if (out != null) {
                Notify.info(c, "下班卡已打", date + " " + (out.isEmpty() ? "已签退" : out) + "。");
                return;
            }
        }

        // 只读记录，绝不打开任何 App、不抢前台
        Log.i("Daka", "check " + kind + " date=" + date + " retry=" + retry + " -> decide");
        Decide.unverified(c, kind, date);
    }

    /** 考勤日 = 04:00 到次日 04:00；0–4 点之间算前一天。 */
    static String targetDate(Context c, String kind) {
        return Store.logicalDate();
    }

    /** 是不是工作日：调休补班算，周末和法定假日不算。 */
    static boolean isWorkday(Context c, String date) {
        try {
            String[] p = date.split("-");
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1,
                    Integer.parseInt(p[2]));
            int dow = cal.get(java.util.Calendar.DAY_OF_WEEK);
            boolean weekend = dow == java.util.Calendar.SATURDAY
                    || dow == java.util.Calendar.SUNDAY;
            int year = Integer.parseInt(p[0]);
            boolean makeup = Holidays.workdaySet(c, year).contains(date);
            if (makeup) return true;
            if (weekend) return false;
            return !Holidays.holidaySet(c, year).contains(date);
        } catch (Throwable t) {
            return true;
        }
    }
}
