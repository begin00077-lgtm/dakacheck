package com.dsh.dakacheck;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从软通打卡「考勤打卡」页面抓到的文字里解析签到 / 签退状态。 */
public final class StatusReader {

    public static final class Snapshot {
        public String date;      // 页面头部的日期，如 2026-11-10
        public String inTime;    // 已签到时间，可能为 null
        public String outTime;   // 已签退时间，可能为 null
        public boolean sawIn;    // 页面出现「已签到」
        public boolean sawOut;   // 页面出现「已签退」
        public String raw;
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
                s.inTime = findTime(texts, i);
            }
            if (!s.sawOut && flat.contains("已签退")) {
                s.sawOut = true;
                s.outTime = findTime(texts, i);
            }
        }
        return s;
    }

    /** 「已签到」后面的时间通常是紧邻的下一段文字。 */
    private static String findTime(List<String> texts, int from) {
        int end = Math.min(texts.size(), from + 6);
        for (int j = from + 1; j < end; j++) {
            String t = texts.get(j);
            if (t == null) continue;
            Matcher m = TIME.matcher(t.replace("\n", ""));
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private static final class LocaleHolder {
        static final java.util.Locale CN = java.util.Locale.CHINA;
    }
}
