package com.dsh.dakacheck;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从软通打卡「考勤打卡」页面抓到的文字里解析签到 / 签退状态。 */
public final class StatusReader {

    public static final class Snapshot {
        public String date;      // 页面头部的日期，如 2026-11-10
        public String inTime;    // 已签到时间（取最新一次），可能为 null
        public String outTime;   // 已签退时间（取最新一次），可能为 null
        public boolean sawIn;    // 页面出现「已签到」
        public boolean sawOut;   // 页面出现「已签退」
        public String raw;
        public java.util.List<String> inTimes = new java.util.ArrayList<String>();
        public java.util.List<String> outTimes = new java.util.ArrayList<String>();
        /** 出现「签退成功」弹窗：弹窗停留的这一刻就是真实签退时刻。 */
        public boolean outSuccess;
        /** 出现「签到成功」弹窗。 */
        public boolean inSuccess;
    }

    private static final Pattern PAGE_DATE = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}:\\d{2}(?::\\d{2})?)");

    private StatusReader() {
    }

    public static Snapshot parse(List<String> texts) {
        Snapshot s = new Snapshot();
        StringBuilder all = new StringBuilder();
        for (String t : texts) {
            if (t != null) all.append(t).append('\n');
        }
        s.raw = all.toString();

        Matcher dm = PAGE_DATE.matcher(s.raw);
        if (dm.find()) {
            s.date = String.format(LocaleHolder.CN, "%04d-%02d-%02d",
                    Integer.parseInt(dm.group(1)), Integer.parseInt(dm.group(2)), Integer.parseInt(dm.group(3)));
        }

        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            if (t == null) continue;
            String flat = t.replace("\n", "");
            if (!s.sawIn && flat.contains("已签到")) {
                s.sawIn = true;
                collectTimes(texts, i, s.inTimes);
                s.inTime = latest(s.inTimes);
            }
            if (!s.sawOut && flat.contains("已签退")) {
                s.sawOut = true;
                collectTimes(texts, i, s.outTimes);
                s.outTime = latest(s.outTimes);
            }
            // 弹窗判定：页面出现独立的「签退成功」/「签到成功」字样
            if (!s.outSuccess && isSuccessText(flat, "签退")) {
                s.outSuccess = true;
            }
            if (!s.inSuccess && isSuccessText(flat, "签到")) {
                s.inSuccess = true;
            }
        }
        // 弹窗出现即视为已签退（即使徽章还没刷新出来）
        if (s.outSuccess) s.sawOut = true;
        if (s.inSuccess) s.sawIn = true;
        return s;
    }

    /**
     * 是否是「签到成功」/「签退成功」弹窗文字。
     *
     * 弹窗里的文字是独立一段，形如「签退成功」。要求整段等于或非常接近，
     * 避免把「签退成功后可查看记录」这类说明文字也当成弹窗。
     */
    private static boolean isSuccessText(String flat, String kind) {
        String t = flat.trim();
        if (t.isEmpty() || t.length() > 12) return false;
        if (!t.contains(kind + "成功")) return false;
        // 排除长句说明
        return t.equals(kind + "成功") || t.equals("已" + kind) || t.startsWith(kind + "成功");
    }

    /** 收集「已签到/已签退」之后紧邻区间里出现的所有时间。 */
    private static void collectTimes(List<String> texts, int from, List<String> into) {
        int end = Math.min(texts.size(), from + 6);
        for (int j = from + 1; j < end; j++) {
            String t = texts.get(j);
            if (t == null) continue;
            String flat = t.replace("\n", "");
            // 碰到另一个状态标记就停，避免把下班卡时间算进上班卡
            if (flat.contains("已签到") || flat.contains("已签退")) break;
            Matcher m = TIME.matcher(flat);
            while (m.find()) {
                String v = normalize(m.group(1));
                if (!into.contains(v)) into.add(v);
            }
        }
    }

    /**
     * 取最新一次打卡时间。
     *
     * 打卡页按时间正序排列，所以正常情况下「最后一个」就是最新一次。
     * 但若页面出现跨零点（如 23:30、00:30），按数值最大会选错，
     * 因此：先用列表顺序取最后一个；只有当列表里存在明显的零点回落
     * （后一项比前一项小 12 小时以上）时才认为是跨天，仍取最后一个。
     * 两种情况都取最后一个 —— 保留函数只为可读性。
     */
    static String latest(List<String> times) {
        if (times == null || times.isEmpty()) return null;
        String best = null;
        for (String t : times) {
            if (toMinutes(t) >= 0) best = t;
        }
        return best != null ? best : times.get(times.size() - 1);
    }

    private static int toMinutes(String t) {
        if (t == null) return -1;
        String[] p = t.split(":");
        try {
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            return h * 60 + m;
        } catch (Throwable e) {
            return -1;
        }
    }

    /** 统一成 HH:mm，去掉尾部 :ss。 */
    private static String normalize(String t) {
        String[] p = t.split(":");
        try {
            return String.format(LocaleHolder.CN, "%02d:%02d",
                    Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        } catch (Throwable e) {
            return t;
        }
    }

    private static final class LocaleHolder {
        static final java.util.Locale CN = java.util.Locale.CHINA;
    }
}
