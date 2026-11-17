package com.dsh.dakacheck;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 打卡记录的本地存储，按日期存「上班签到 / 下班签退」以及「这一天看到过打卡页」。 */
public final class Store {
    private static final String PREF = "daka";
    private static final String K_IN = "in:";
    private static final String K_OUT = "out:";
    private static final String K_PAGE = "page:";
    private static final String K_SEEN = "seen";
    private static final String K_ISSUE = "issue:";

    private Store() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String dateOf(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(millis));
    }

    public static String today() {
        return dateOf(System.currentTimeMillis());
    }

    /**
     * 考勤系统的「当天」是 04:00 到次日 04:00：
     * 0–4 点之间页面显示的是前一天的考勤，所以归属日要用这个。
     */
    public static String logicalDate() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        if (cal.get(java.util.Calendar.HOUR_OF_DAY) < 4) {
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1);
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(cal.getTime());
    }

    /** 现在是否处于「0–4 点，算前一天」的时段。 */
    public static boolean beforeCycleBoundary() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 4;
    }

    /** 当前考勤日的起点（当天 04:00 或前一天 04:00）。 */
    public static long attendanceDayStart() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        if (cal.get(java.util.Calendar.HOUR_OF_DAY) < 4) {
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1);
        }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 4);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public static void putIn(Context c, String date, String time) {
        sp(c).edit().putString(K_IN + date, time == null ? "" : time).apply();
    }

    public static void putOut(Context c, String date, String time) {
        sp(c).edit().putString(K_OUT + date, time == null ? "" : time).apply();
    }

    /** null = 没有记录；"" = 有记录但页面上没显示时间。 */
    public static String getIn(Context c, String date) {
        return sp(c).getString(K_IN + date, null);
    }

    public static String getOut(Context c, String date) {
        return sp(c).getString(K_OUT + date, null);
    }

    /** 所有有记录（上班或下班）的日期。 */
    public static java.util.Set<String> dates(Context c) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        for (String k : sp(c).getAll().keySet()) {
            if (k.startsWith(K_IN) || k.startsWith(K_OUT)) {
                out.add(k.substring(k.indexOf(':') + 1));
            }
        }
        return out;
    }

    /** 记录「这一天的打卡页被读到过」，时间是读取时刻。 */
    public static void putPage(Context c, String date, long at) {
        sp(c).edit().putLong(K_PAGE + date, at).apply();
    }

    public static long pageAt(Context c, String date) {
        return sp(c).getLong(K_PAGE + date, 0L);
    }

    /** 记录某天的考勤异常（旷工/迟到…），空串表示正常。 */
    public static void putIssue(Context c, String date, String issue) {
        sp(c).edit().putString(K_ISSUE + date, issue == null ? "" : issue).commit();
    }

    public static String getIssue(Context c, String date) {
        return sp(c).getString(K_ISSUE + date, null);
    }

    /** 清掉除指定月之外所有旧异常记录（避免陈年数据干扰）。 */
    /** 清掉某一年的所有异常记录（重新写入前先清）。 */
    public static void clearIssues(Context c, String yearPrefix) {
        SharedPreferences.Editor e = sp(c).edit();
        for (String k : sp(c).getAll().keySet()) {
            if (k.startsWith(K_ISSUE)
                    && k.substring(K_ISSUE.length()).startsWith(yearPrefix)) {
                e.remove(k);
            }
        }
        e.commit();
    }

    public static void setSeen(Context c) {
        sp(c).edit().putLong(K_SEEN, System.currentTimeMillis()).apply();
    }

    public static long seen(Context c) {
        return sp(c).getLong(K_SEEN, 0L);
    }
}
