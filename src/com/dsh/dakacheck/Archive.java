package com.dsh.dakacheck;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 把打卡记录同步到手机根目录的「打卡记录」文件夹，卸载 / 清数据后可从这里恢复。
 *
 * 打卡记录/
 *   打卡汇总.csv       一行一天：日期,上班,下班,工时
 *   2026-11-11.txt     当天明细
 */
public final class Archive {
    public static final String FOLDER = "打卡记录";
    public static final String SUMMARY = "打卡汇总.csv";
    public static final String CONFIG = "设置.txt";

    private Archive() {
    }

    public static File dir() {
        return new File(Environment.getExternalStorageDirectory(), FOLDER);
    }

    /** 是否已拿到「所有文件访问权限」。 */
    public static boolean canWrite() {
        try {
            return Environment.isExternalStorageManager();
        } catch (Throwable t) {
            return false;
        }
    }

    public static void syncAsync(final Context c) {
        if (!canWrite()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                sync(c);
            }
        }).start();
    }

    /** 把本地记录写成文件；内容没变就不重复写。 */
    public static synchronized void sync(Context c) {
        if (!canWrite()) {
            Log.i("Daka", "archive: no permission");
            return;
        }
        try {
            File d = dir();
            if (!d.exists() && !d.mkdirs()) {
                Log.i("Daka", "archive: mkdir failed");
                return;
            }

            List<String> dates = new ArrayList<>(Store.dates(c));
            Collections.sort(dates);

            StringBuilder csv = new StringBuilder("日期,上班,下班,工时\n");
            for (String date : dates) {
                String in = Store.getIn(c, date);
                String out = Store.getOut(c, date);
                Double h = Work.hoursOf(c, date);
                csv.append(date).append(',')
                        .append(in == null ? "" : in).append(',')
                        .append(out == null ? "" : out).append(',')
                        .append(h == null ? "" : String.format(Locale.CHINA, "%.2f", h))
                        .append('\n');

                StringBuilder txt = new StringBuilder();
                txt.append("日期  ").append(date).append('\n');
                txt.append("上班  ").append(in == null || in.isEmpty() ? "未读到" : in).append('\n');
                txt.append("下班  ").append(out == null || out.isEmpty() ? "未读到" : out).append('\n');
                txt.append("工时  ").append(h == null ? "—" : String.format(Locale.CHINA, "%.2f 小时", h))
                        .append('\n');
                writeIfChanged(new File(d, date + ".txt"), txt.toString());
            }
            writeIfChanged(new File(d, SUMMARY), csv.toString());

            String cfg = "上班提醒=" + Config.inHour(c) + ":" + pad(Config.inMin(c))
                    + "\n下班提醒=" + Config.outHour(c) + ":" + pad(Config.outMin(c)) + "\n";
            writeIfChanged(new File(d, CONFIG), cfg);
            Log.i("Daka", "archive: synced " + dates.size() + " days");
        } catch (Throwable t) {
            Log.i("Daka", "archive sync failed: " + t);
        }
    }

    private static String pad(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    /** 换机/重装后把提醒时间也带回来（本地还是默认值时才应用）。 */
    private static void restoreConfig(Context c) {
        File f = new File(dir(), CONFIG);
        if (!f.exists()) return;
        boolean isDefault = Config.inHour(c) == Config.DEF_IN_H && Config.inMin(c) == Config.DEF_IN_M
                && Config.outHour(c) == Config.DEF_OUT_H && Config.outMin(c) == Config.DEF_OUT_M;
        if (!isDefault) return;
        int ih = -1, im = -1, oh = -1, om = -1;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] kv = line.split("=", 2);
                    if (kv.length < 2) continue;
                    String[] hm = kv[1].trim().split(":");
                    if (hm.length < 2) continue;
                    int h = Integer.parseInt(hm[0].trim());
                    int m = Integer.parseInt(hm[1].trim());
                    if (kv[0].startsWith("上班")) {
                        ih = h;
                        im = m;
                    } else if (kv[0].startsWith("下班")) {
                        oh = h;
                        om = m;
                    }
                }
            } finally {
                r.close();
            }
            if (ih >= 0 && im >= 0) Config.setIn(c, ih, im);
            if (oh >= 0 && om >= 0) Config.setOut(c, oh, om);
            Log.i("Daka", "archive: config restored");
        } catch (Throwable t) {
            Log.i("Daka", "archive config restore failed: " + t);
        }
    }

    private static void writeIfChanged(File f, String content) throws Exception {
        if (f.exists()) {
            String old = read(f);
            if (content.equals(old)) return;
        }
        FileOutputStream fos = new FileOutputStream(f);
        OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }

    private static String read(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        try {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        } finally {
            r.close();
        }
        return sb.toString();
    }

    /** 从汇总文件把本地缺失的记录补回来；返回补了多少天。 */
    public static synchronized int restore(Context c) {
        if (!canWrite()) return 0;
        restoreConfig(c);
        File f = new File(dir(), SUMMARY);
        if (!f.exists()) return 0;
        int added = 0;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            boolean header = true;
            try {
                while ((line = r.readLine()) != null) {
                    if (header) {
                        header = false;
                        if (line.startsWith("日期")) continue;
                    }
                    String[] p = line.split(",", -1);
                    if (p.length < 3) continue;
                    String date = p[0].trim();
                    if (date.length() < 8) continue;
                    String in = p[1].trim();
                    String out = p[2].trim();

                    if (!in.isEmpty() && !in.equals("null")) {
                        String cur = Store.getIn(c, date);
                        if (cur == null || cur.isEmpty()) {
                            Store.putIn(c, date, in);
                            added++;
                        }
                    }
                    if (!out.isEmpty() && !out.equals("null")) {
                        String cur = Store.getOut(c, date);
                        if (cur == null || cur.isEmpty()) {
                            Store.putOut(c, date, out);
                            added++;
                        }
                    }
                }
            } finally {
                r.close();
            }
            Log.i("Daka", "archive: restored " + added);
        } catch (Throwable t) {
            Log.i("Daka", "archive restore failed: " + t);
        }
        return added;
    }
}
