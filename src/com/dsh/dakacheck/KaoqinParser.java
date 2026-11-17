package com.dsh.dakacheck;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析软通考勤日历页面文字，找出有异常的日期。
 *
 * 页面里每天是一段：
 *   11月12日
 *   初次打卡：08:57
 *   末次打卡：
 *   旷工
 * 正常的是： 11月2日 （正常）
 */
public final class KaoqinParser {

    private static final Pattern DAY = Pattern.compile("(\\d{1,2})月(\\d{1,2})日");

    /** 判定为异常的关键字（命中任一即异常）。 */
    /** 绿色三角：请假 / 外出公干（不是异常）。 */
    public static final String[] GREEN = {
            "请假", "外出公干", "公干", "出差",
            "调休", "年假", "事假", "病假"
    };

    /** 判断某天是否属于绿色（请假/公干）。 */
    public static String greenTag(String detail) {
        if (detail == null) return null;
        for (String g : GREEN) {
            if (detail.contains(g)) return g;
        }
        return null;
    }

    private static final String[] BAD = {
            "旷工", "缺勤", "迟到", "早退",
            "缺卡", "未打卡", "未刷", "漏刷",
            "未签退", "未签到", "异常"
    };

    public static final class Day {
        public int month;
        public int day;
        public String detail = "";
        public final List<String> issues = new ArrayList<>();

        public boolean bad() {
            return !issues.isEmpty();
        }
    }

    private KaoqinParser() {
    }

    /** 返回所有有异常的日期。 */
    /** 解析出所有「（正常）」的日期。 */
    public static List<Day> findNormal(String text) {
        List<Day> out = new ArrayList<>();
        for (Day d : parseDays(text)) {
            if (!d.bad() && d.detail.contains("正常")) out.add(d);
        }
        return out;
    }

    /** 把页面文字切成每一天。 */
    public static List<Day> parseDays(String text) {
        List<Day> days = new ArrayList<>();
        if (text == null || text.isEmpty()) return days;
        String flat = text.replace("\\n", "\n");
        Matcher m = DAY.matcher(flat);
        List<Integer> starts = new ArrayList<>();
        List<int[]> md = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
            md.add(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
        }
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = (i + 1 < starts.size()) ? starts.get(i + 1) : flat.length();
            Day d = new Day();
            d.month = md.get(i)[0];
            d.day = md.get(i)[1];
            d.detail = flat.substring(from, to);
            for (String b : BAD) {
                if (d.detail.contains(b) && !d.issues.contains(b)) d.issues.add(b);
            }
            days.add(d);
        }
        return days;
    }

    public static List<Day> findIssues(String text) {
        List<Day> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;

        String flat = text.replace("\\n", "\n");
        Matcher m = DAY.matcher(flat);
        List<int[]> pos = new ArrayList<>();
        List<Day> days = new ArrayList<>();
        while (m.find()) {
            Day d = new Day();
            d.month = Integer.parseInt(m.group(1));
            d.day = Integer.parseInt(m.group(2));
            days.add(d);
            pos.add(new int[]{m.start(), m.end()});
        }

        for (int i = 0; i < days.size(); i++) {
            int start = pos.get(i)[1];
            int end = (i + 1 < days.size()) ? pos.get(i + 1)[0] : flat.length();
            String seg = flat.substring(start, Math.min(end, start + 400));
            days.get(i).detail = seg.replace("\t", " ").replace("\n", " ").trim();

            for (String k : BAD) {
                if (seg.contains(k)) {
                    if (!days.get(i).issues.contains(k)) days.get(i).issues.add(k);
                }
            }
        }

        java.util.Calendar now = java.util.Calendar.getInstance();
        int curMonth = now.get(java.util.Calendar.MONTH) + 1;
        int curDay = now.get(java.util.Calendar.DAY_OF_MONTH);
        for (Day d : days) {
            if (!d.bad()) continue;
            // 未来日期不算（还没发生）
            if (d.month == curMonth && d.day > curDay) continue;
            // 段落里混入图例文字的不算
            if (d.detail.contains("红色") || d.detail.contains("绿色")) continue;
            out.add(d);
        }
        return out;
    }

    /** 调试用：把解析结果拼成一行。 */
    public static String describe(List<Day> ds) {
        StringBuilder sb = new StringBuilder();
        for (Day d : ds) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(d.month).append("月").append(d.day).append("日[")
                    .append(String.join(",", d.issues)).append("]");
        }
        return sb.toString();
    }
}
