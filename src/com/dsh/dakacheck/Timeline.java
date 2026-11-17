package com.dsh.dakacheck;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 最近发生的事（上线/掉线/读到/没读到），用来排查“为什么没同步”。 */
public final class Timeline {
    private static final String PREF = "timeline";
    private static final String KEY = "log";
    private static final int MAX = 40;

    private Timeline() {
    }

    public static void add(Context c, String what) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(new Date())
                    + "  " + what;
            String old = sp.getString(KEY, "");
            String[] parts = old.isEmpty() ? new String[0] : old.split("\n");
            StringBuilder sb = new StringBuilder(line);
            int keep = Math.min(parts.length, MAX - 1);
            for (int i = parts.length - keep; i < parts.length; i++) {
                sb.append('\n').append(parts[i]);
            }
            sp.edit().putString(KEY, sb.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    public static void clear(Context c) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    public static String dump(Context c) {
        try {
            String s = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "");
            return s == null || s.isEmpty() ? "（还没有记录）" : s;
        } catch (Throwable t) {
            return "（读取失败）";
        }
    }
}
