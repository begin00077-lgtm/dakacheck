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

            // 先把已有的 CSV 读进来（安装前的历史记录不能丢）
            java.util.LinkedHashMap<String, String[]> merged =
                    new java.util.LinkedHashMap<String, String[]>();
            File sumFile = new File(d, SUMMARY);
            if (sumFile.exists()) {
                try {
                    BufferedReader rr = new BufferedReader(
                            new InputStreamReader(new FileInputStream(sumFile), "UTF-8"));
                    try {
                        String line;
                        boolean header = true;
                        while ((line = rr.readLine()) != null) {
                            if (header) {
                                header = false;
                                if (line.startsWith("日期")) continue;
                            }
                            String[] p = line.split(",", -1);
                            if (p.length < 1) continue;
                            String dt = p[0].trim();
                            if (dt.length() < 8) continue;
                            merged.put(dt, new String[]{
                                    p.length > 1 ? p[1].trim() : "",
                                    p.length > 2 ? p[2].trim() : "",
                                    p.length > 3 ? p[3].trim() : ""});
                        }
                    } finally {
                        rr.close();
                    }
                } catch (Throwable t) {
                    Log.i("Daka", "archive: read existing csv failed " + t);
                }
            }

            // 再把旧版单日 txt 里的记录也合并进来（写入前就合并，避免删 txt 前丢数据）
            try {
                File[] txts = d.listFiles();
                if (txts != null) {
                    for (File one : txts) {
                        String nm = one.getName();
                        if (!nm.endsWith(".txt") || nm.equals(CONFIG)) continue;
                        String date = nm.substring(0, nm.length() - 4);
                        if (date.length() < 8) continue;
                        String tIn = "", tOut = "";
                        BufferedReader tr = new BufferedReader(
                                new InputStreamReader(new FileInputStream(one), "UTF-8"));
                        try {
                            String line;
                            while ((line = tr.readLine()) != null) {
                                String t = line.trim();
                                if (t.startsWith("上班")) {
                                    tIn = t.substring(2).trim();
                                } else if (t.startsWith("下班")) {
                                    tOut = t.substring(2).trim();
                                }
                            }
                        } finally {
                            tr.close();
                        }
                        if ("未读到".equals(tIn)) tIn = "";
                        if ("未读到".equals(tOut)) tOut = "";
                        if (tIn.isEmpty() && tOut.isEmpty()) continue;

                        String[] had = merged.get(date);
                        if (had == null) {
                            merged.put(date, new String[]{tIn, tOut, ""});
                        } else {
                            // 两边都有：txt 只补 CSV 里空着的那一格
                            if (had[0].isEmpty()) had[0] = tIn;
                            if (had[1].isEmpty()) had[1] = tOut;
                        }
                    }
                }
            } catch (Throwable t) {
                Log.i("Daka", "archive: merge txt failed " + t);
            }

            // 用本地最新数据覆盖对应天，其余保留
            List<String> dates = new ArrayList<>(Store.dates(c));
            for (String date : dates) {
                String in = Store.getIn(c, date);
                String out = Store.getOut(c, date);
                Double h = Work.hoursOf(c, date);
                String nIn = in == null ? "" : in;
                String nOut = out == null ? "" : out;
                String nH = h == null ? "" : String.format(Locale.CHINA, "%.2f", h);

                String[] had = merged.get(date);
                if (had != null) {
                    // 旧值是有效数据、新值为空时，保留旧值
                    if (nIn.isEmpty() && !had[0].isEmpty()) nIn = had[0];
                    if (nOut.isEmpty() && !had[1].isEmpty()) nOut = had[1];
                    if (nH.isEmpty() && !had[2].isEmpty()) nH = had[2];
                }
                merged.put(date, new String[]{nIn, nOut, nH});
            }

            if (merged.isEmpty()) {
                Log.i("Daka", "archive: nothing to write");
            } else {
                List<String> allDates = new ArrayList<>(merged.keySet());
                Collections.sort(allDates);

                StringBuilder csv = new StringBuilder("日期,上班,下班,工时,状态\n");
                for (String date : allDates) {
                    String[] v = merged.get(date);
                    String issue = Store.getIssue(c, date);
                    csv.append(date).append(',')
                            .append(v[0]).append(',')
                            .append(v[1]).append(',')
                            .append(v[2]).append(',')
                            .append(issue == null ? "" : issue)
                            .append('\n');
                }

                writeIfChanged(sumFile, csv.toString());

                // 清理旧版本留下的单日 txt（信息已并入 CSV）
                try {
                    File[] olds = d.listFiles();
                    if (olds != null) {
                        for (File one : olds) {
                            String nm = one.getName();
                            if (nm.endsWith(".txt") && !nm.equals(CONFIG)) {
                                one.delete();
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
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
        int added = 0;

        // 1) 从汇总 CSV 恢复
        File f = new File(dir(), SUMMARY);
        if (f.exists()) {
            try {
                BufferedReader r = new BufferedReader(
                        new InputStreamReader(new FileInputStream(f), "UTF-8"));
                try {
                    String line;
                    boolean header = true;
                    while ((line = r.readLine()) != null) {
                        if (header) {
                            header = false;
                            if (line.startsWith("日期")) continue;
                        }
                        String[] p = line.split(",", -1);
                        if (p.length < 3) continue;
                        String date = p[0].trim();
                        if (date.length() < 8) continue;
                        String in = p.length > 1 ? p[1].trim() : "";
                        String out = p.length > 2 ? p[2].trim() : "";
                        String st = p.length > 4 ? p[4].trim() : "";

                        added += putIfEmpty(c, date, in, out);
                        if (!st.isEmpty()) {
                            String curIssue = Store.getIssue(c, date);
                            if (curIssue == null || curIssue.isEmpty()) {
                                Store.putIssue(c, date, st);
                            }
                        }
                    }
                } finally {
                    r.close();
                }
            } catch (Throwable t) {
                Log.i("Daka", "archive restore csv failed: " + t);
            }
        }

        // 2) 兼容旧版本：CSV 缺数据时从每天的 txt 补一次
        try {
            File[] files = dir().listFiles();
            if (files != null) {
                for (File one : files) {
                    String nm = one.getName();
                    if (!nm.endsWith(".txt") || nm.equals(CONFIG)) continue;
                    String date = nm.substring(0, nm.length() - 4);
                    if (date.length() < 8) continue;
                    String in = "", out = "";
                    BufferedReader r = new BufferedReader(
                            new InputStreamReader(new FileInputStream(one), "UTF-8"));
                    try {
                        String line;
                        while ((line = r.readLine()) != null) {
                            String t = line.trim();
                            if (t.startsWith("上班")) {
                                in = t.substring(2).trim();
                            } else if (t.startsWith("下班")) {
                                out = t.substring(2).trim();
                            }
                        }
                    } finally {
                        r.close();
                    }
                    if ("未读到".equals(in)) in = "";
                    if ("未读到".equals(out)) out = "";
                    added += putIfEmpty(c, date, in, out);
                }
            }
        } catch (Throwable t) {
            Log.i("Daka", "archive restore txt failed: " + t);
        }

        Log.i("Daka", "archive: restored " + added);
        // 恢复完立即回写 CSV，保证文件与内部数据一致
        if (added > 0) {
            try {
                sync(c);
            } catch (Throwable t) {
                Log.i("Daka", "archive: resync after restore failed " + t);
            }
        }
        return added;
    }

    /** 只有本地为空时才写入，避免覆盖已有的更新数据。 */
    private static int putIfEmpty(Context c, String date, String in, String out) {
        int n = 0;
        Log.i("Daka", "restore day " + date + " txtIn=[" + in + "] txtOut=[" + out
                + "] curIn=[" + Store.getIn(c, date) + "] curOut=[" + Store.getOut(c, date) + "]");
        try {
            if (in != null && !in.isEmpty() && !"null".equals(in)) {
                String cur = Store.getIn(c, date);
                if (cur == null || cur.isEmpty()) {
                    Store.putIn(c, date, in);
                    n++;
                }
            }
            if (out != null && !out.isEmpty() && !"null".equals(out)) {
                String cur = Store.getOut(c, date);
                if (cur == null || cur.isEmpty()) {
                    Store.putOut(c, date, out);
                    n++;
                }
            }
        } catch (Throwable ignored) {
        }
        return n;
    }
}
