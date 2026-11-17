package com.dsh.dakacheck;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * 节假日数据：优先用联网拉取的（含调休补班），缓存 7 天；
 * 拉不到就用内置的固定日期兜底。
 */
public final class Holidays {
    private static final String PREF = "holidays";
    private static final long TTL = 7 * 24 * 3600_000L;

    private Holidays() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 放假日（含周末调休形成的假期）。 */
    public static Set<String> holidaySet(Context c, int year) {
        Set<String> builtin = builtin(year);
        String v = sp(c).getString("h_" + year, "");
        if (v == null || v.isEmpty()) return builtin;
        Set<String> s = new HashSet<>(builtin);
        for (String d : v.split(",")) if (!d.isEmpty()) s.add(d);
        return s;
    }

    /** 该年是否已从网上拿到数据。 */
    public static boolean isFetched(Context c, int year) {
        String v = sp(c).getString("h_" + year, "");
        return v != null && !v.isEmpty();
    }

    /** 调休补班日（周末但要上班）。 */
    public static Set<String> workdaySet(Context c, int year) {
        Set<String> s = new HashSet<>();
        String v = sp(c).getString("w_" + year, "");
        if (v == null) return s;
        for (String d : v.split(",")) if (!d.isEmpty()) s.add(d);
        return s;
    }

    /** 内置兜底：只含固定日期的几个大假。 */
    private static Set<String> builtin(int year) {
        Set<String> s = new HashSet<>();
        s.add(year + "-01-01");
        s.add(year + "-05-01");
        s.add(year + "-05-02");
        s.add(year + "-05-03");
        for (int d = 1; d <= 7; d++) {
            s.add(String.format("%d-10-%02d", year, d));
        }
        return s;
    }

    /** 后台拉取今年和明年的数据，7 天内不重复拉。 */
    public static void refreshAsync(final Context c) {
        long last = sp(c).getLong("last_fetch", 0L);
        if (System.currentTimeMillis() - last < TTL) return;
        sp(c).edit().putLong("last_fetch", System.currentTimeMillis()).apply();
        final int y = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        new Thread(new Runnable() {
            @Override
            public void run() {
                fetch(c, y);
                fetch(c, y + 1);
            }
        }).start();
    }

    private static void fetch(Context c, int year) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://timor.tech/api/holiday/year/" + year);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "DakaCheck/1.0");
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();

            JSONObject root = new JSONObject(sb.toString());
            JSONObject holiday = root.optJSONObject("holiday");
            if (holiday == null) return;

            StringBuilder hs = new StringBuilder();
            StringBuilder ws = new StringBuilder();
            Iterator<String> it = holiday.keys();
            while (it.hasNext()) {
                String key = it.next();           // 形如 01-01
                JSONObject o = holiday.optJSONObject(key);
                if (o == null) continue;
                String date = o.optString("date", "");
                if (date.isEmpty()) date = year + "-" + key;
                if (o.optBoolean("holiday", false)) {
                    if (hs.length() > 0) hs.append(',');
                    hs.append(date);
                } else {
                    if (ws.length() > 0) ws.append(',');
                    ws.append(date);
                }
            }
            sp(c).edit()
                    .putString("h_" + year, hs.toString())
                    .putString("w_" + year, ws.toString())
                    .apply();
            Log.i("Daka", "holidays fetched " + year + " h=" + hs.length() + " w=" + ws.length());
        } catch (Throwable t) {
            Log.i("Daka", "holidays fetch failed " + year + ": " + t);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
