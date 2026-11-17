package com.dsh.dakacheck;

import android.content.Context;

import java.util.Calendar;
import java.util.Locale;

/** 工时计算（日历和桌面卡片共用）。 */
public final class Work {
    /** 不计工时的时段：午休 12:00–13:30，晚饭 17:30–18:00。 */
    private static final int[][] BREAKS = {{12 * 3600, 13 * 3600 + 1800}, {17 * 3600 + 1800, 18 * 3600}};

    private Work() {
    }

    public static int seconds(String t) {
        if (t == null) return -1;
        String[] p = t.split(":");
        try {
            if (p.length < 2) return -1;
            int h = Integer.parseInt(p[0].trim());
            int m = Integer.parseInt(p[1].trim());
            int s = p.length > 2 ? Integer.parseInt(p[2].trim()) : 0;
            return h * 3600 + m * 60 + s;
        } catch (Throwable e) {
            return -1;
        }
    }

    /** 某天工时；缺上班或下班记录返回 null。 */
    public static Double hoursOf(Context c, String date) {
        String in = Store.getIn(c, date);
        String out = Store.getOut(c, date);
        if (in == null || out == null || in.isEmpty() || out.isEmpty()) return null;
        int a = seconds(in);
        int b = seconds(out);
        if (a < 0 || b < 0) return null;
        if (b < a) b += 24 * 3600;
        double secs = b - a;
        for (int[] br : BREAKS) {
            int lo = Math.max(a, br[0]);
            int hi = Math.min(b, br[1]);
            if (hi > lo) secs -= (hi - lo);
        }
        if (secs < 0) secs = 0;
        return Math.round(secs / 36.0) / 100.0;
    }

    public static double monthTotal(Context c, int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1);
        int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        double total = 0;
        for (int d = 1; d <= days; d++) {
            String date = String.format(Locale.CHINA, "%04d-%02d-%02d", year, month, d);
            Double h = hoursOf(c, date);
            if (h != null) total += h;
        }
        return Math.round(total * 100.0) / 100.0;
    }


    /** 把 HH:mm:ss 拆成 {时, 分}，解析失败用默认值。 */
    public static int[] split(String t, int defH, int defM) {
        int sec = seconds(t);
        if (sec < 0) return new int[]{defH, defM};
        return new int[]{sec / 3600 % 24, sec / 60 % 60};
    }

    public static String hhmm(String t) {
        if (t == null || t.isEmpty()) return "--:--";
        return t.length() >= 5 ? t.substring(0, 5) : t;
    }
}
