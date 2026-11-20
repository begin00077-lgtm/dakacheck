package com.dsh.dakacheck;

import android.content.Context;
import android.content.SharedPreferences;

/** 可配置项：两个检查时刻、读不到页面时是否也响铃。 */
public final class Config {
    private static final String P = "config";

    public static final int DEF_IN_H = 9, DEF_IN_M = 15;
    public static final int DEF_OUT_H = 2, DEF_OUT_M = 30;

    private Config() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    public static int inHour(Context c) {
        return sp(c).getInt("in_h", DEF_IN_H);
    }

    public static int inMin(Context c) {
        return sp(c).getInt("in_m", DEF_IN_M);
    }

    public static int outHour(Context c) {
        return sp(c).getInt("out_h", DEF_OUT_H);
    }

    public static int outMin(Context c) {
        return sp(c).getInt("out_m", DEF_OUT_M);
    }

    public static void setIn(Context c, int h, int m) {
        sp(c).edit().putInt("in_h", h).putInt("in_m", m).apply();
        Scheduler.ensure(c);
    }

    public static void setOut(Context c, int h, int m) {
        sp(c).edit().putInt("out_h", h).putInt("out_m", m).apply();
        Scheduler.ensure(c);
    }


    public static void setInHour(Context c, int h) { setIn(c, h, inMin(c)); }
    public static void setInMin(Context c, int m) { setIn(c, inHour(c), m); }
    public static void setOutHour(Context c, int h) { setOut(c, h, outMin(c)); }
    public static void setOutMin(Context c, int m) { setOut(c, outHour(c), m); }

    /** 打卡提醒总开关（关闭后不排核对闹钟）。 */
    public static boolean remindOn(Context c) {
        return sp(c).getBoolean("remind", true);
    }

    public static void setRemindOn(Context c, boolean on) {
        sp(c).edit().putBoolean("remind", on).apply();
    }

    /** 异常提醒永久开启，不再提供开关。 */
    public static boolean kaoqinOn(Context c) {
        return true;
    }

    public static void setKaoqin(Context c, boolean on) {
        sp(c).edit().putBoolean("kaoqin", true).apply();
    }


    /** 考勤系统账号（固定）。 */
    public static String kaoqinUser(Context c) {
        return sp(c).getString("kq_user", "");
    }

    /** 考勤系统密码。 */
    public static String kaoqinPwd(Context c) {
        return sp(c).getString("kq_pwd", "");
    }

    public static void setKaoqinPwd(Context c, String pwd) {
        sp(c).edit().putString("kq_pwd", pwd).apply();
    }

    public static void setKaoqinUser(Context c, String user) {
        sp(c).edit().putString("kq_user", user).apply();
    }

    /** 下班卡检查时刻在 04:00 之前，算的是「前一天」的下班卡。 */
    public static boolean outTargetsPreviousDay(Context c) {
        return outHour(c) < 4;
    }
}
