package com.dsh.dakacheck;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Calendar;
import java.util.List;

/**
 * 旷工检查：
 * - 每天检查当月考勤，发现异常 → 弹一次提醒
 * - 每月最后一个星期一仍有异常 → 响铃
 */
public final class Warden {
    public static final String URL_LOGIN =
            "https://ipsapro.isoftstone.com/apsa/Attendance/Home/HomeView/Index";

    private static final String PREF = "warden";

    private Warden() {
    }

    /** 重新排下一次检查（开关关闭时什么都不做）。 */
    public static void reschedule(Context c) {
        if (!Config.kaoqinOn(c)) {
            Log.i("Daka", "warden off");
            return;
        }
        Scheduler.ensureWarden(c);
    }

    /** 触发一次检查：交给不可见 Activity 加载考勤页。 */
    /** 每天定时检查：默认静默后台抓，不弹页面。 */
    public static void check(Context c) {
        check(c, false);
    }

    /**
     * @param manual true = 用户手动触发（跳登录页）
     *               false = 定时触发（后台抓，不影响用户）
     */
    /** 同步指定月份（yyyy-MM）。 */
    public static void checkMonth(Context c, String ym) {
        Log.i("Daka", "WARDEN checkMonth " + ym);
        Timeline.add(c, "开始同步 " + ym + " 考勤");
        BackgroundGrab.start(c, ym);
    }

    public static void check(Context c, boolean manual) {
        Log.i("Daka", "WARDEN check on=" + Config.kaoqinOn(c) + " manual=" + manual);
        if (!Config.kaoqinOn(c)) return;
        Timeline.add(c, "开始检查当月考勤");
        if (manual) {
            // 手动：跳登录页（需要用户看到）
            Intent i = new Intent(c, KaoqinLoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } else {
            // 定时：后台静默抓，不弹任何页面
            BackgroundGrab.start(c);
        }
    }

    /** 页面文字到手后处理。 */
    /**
     * 按颜色判异常（优先），文字只用来取详情。
     * 这样不用穷举关键词，平台上红色即异常。
     */
    public static void handleColorAndText(Context c, String colors, String text) {
        List<ColorParser.Day> days = ColorParser.parse(colors);
        Log.i("Daka", "WARDEN color days=" + days.size());

        int year = Calendar.getInstance().get(Calendar.YEAR);
        Store.clearIssues(c, String.format(java.util.Locale.CHINA, "%04d-", year));

        java.util.List<String> badTags = new java.util.ArrayList<>();
        java.util.List<String> okTags = new java.util.ArrayList<>();

        // 文字里取每天的详情（用于显示出勤时间/具体原因）
        java.util.Map<String, String> detailMap = new java.util.HashMap<>();
        for (KaoqinParser.Day d : KaoqinParser.parseDays(text)) {
            detailMap.put(d.month + "-" + d.day, d.detail);
        }

        for (ColorParser.Day d : days) {
            String date = String.format(java.util.Locale.CHINA, "%04d-%02d-%02d",
                    year, d.month, d.day);
            String tag = d.month + "月" + d.day + "日";
            String detail = detailMap.get(d.month + "-" + d.day);

            // 绿色三角：请假 / 外出公干
            String green = KaoqinParser.greenTag(detail);

            // 未来日期不参与判定
            java.util.Calendar nowCal = java.util.Calendar.getInstance();
            int curM = nowCal.get(java.util.Calendar.MONTH) + 1;
            int curD = nowCal.get(java.util.Calendar.DAY_OF_MONTH);
            if (d.month == curM && d.day > curD) continue;

            // 绿色优先：请假/公干即使标红也不算异常
            if (green != null) {
                Store.putIssue(c, date, green);
                okTags.add(tag);
            } else if (d.bad) {
                String label = pickTag(detail);
                if (label.isEmpty()) label = "异常";
                Store.putIssue(c, date, label);
                badTags.add(tag + "【" + label + "】");
            } else if (!d.holiday && hasRecord(detail)) {
                // 平台上真的有记录（打卡时间/正常）才算正常
                Store.putIssue(c, date, "正常");
                okTags.add(tag);
            }
        }

        Log.i("Daka", "WARDEN bad=" + badTags.size() + " ok=" + okTags.size()
                + " " + String.join(",", badTags));

        putResult(c, badTags.isEmpty() ? "本月考勤正常"
                : String.join("；", badTags));
        setFetched(c);
        bumpVersion(c);
        Timeline.add(c, "考勤同步："
                + (badTags.isEmpty() ? "无异常" : String.join("；", badTags)));

        if (badTags.isEmpty()) return;

        StringBuilder body = new StringBuilder();
        for (String t : badTags) {
            if (body.length() > 0) body.append('、');
            body.append(t);
        }
        if (isLastMonday()) {
            Notify.alarm(c, "本月考勤还有异常",
                    body + "。今天是本月最后一个周一，记得处理。", "");
        } else {
            Notify.unknown(c, "考勤有异常", body + "。请到考勤系统确认。");
        }
    }

    /** 这一天在平台上是否真的有记录（而不是空白格子）。 */
    private static boolean hasRecord(String detail) {
        if (detail == null || detail.trim().isEmpty()) return false;
        return detail.contains("初次打卡") || detail.contains("末次打卡")
                || detail.contains("正常") || detail.contains("缺勤")
                || detail.contains("请假") || detail.contains("公干");
    }

    /** 从页面文字里拿一个短标签（如「缺勤」「外出公干」）。 */
    private static String pickTag(String detail) {
        if (detail == null) return "";
        String[] lines = detail.split("\n");
        for (String l : lines) {
            String t = l.trim();
            if (t.isEmpty()) continue;
            if (t.contains("打卡")) continue;
            if (t.startsWith("初次")) continue;
            if (t.startsWith("末次")) continue;
            if (t.matches("\\d{1,2}\\u6708\\d{1,2}\\u65e5")) continue;
            // 去掉括号、空格、制表符等杂字符
            t = t.replace("（", "").replace("）", "")
                    .replace("(", "").replace(")", "")
                    .replace("　", " ").replace(" ", " ")
                    .replaceAll("[\\s\\u00a0]+", " ")
                    .trim();
            if (t.isEmpty()) continue;
            if (t.length() > 8) t = t.substring(0, 8);
            return t;
        }
        return "";
    }

    public static void handleText(Context c, String text) {
        Log.i("Daka", "WARDEN handleText len=" + (text == null ? -1 : text.length()));
        List<KaoqinParser.Day> issues = KaoqinParser.findIssues(text);
        Log.i("Daka", "WARDEN issues=" + issues.size() + " " + KaoqinParser.describe(issues));
        putResult(c, issues.isEmpty() ? "本月考勤正常" : KaoqinParser.describe(issues));
        setFetched(c);

        // 把结果写进 Store，日历直接读取显示
        int year = Calendar.getInstance().get(Calendar.YEAR);
        String prefix = String.format(java.util.Locale.CHINA, "%04d-%02d", year, 0);
        Store.clearIssues(c, String.format(java.util.Locale.CHINA, "%04d-", year));
        for (KaoqinParser.Day d : issues) {
            String date = String.format(java.util.Locale.CHINA, "%04d-%02d-%02d",
                    year, d.month, d.day);
            Store.putIssue(c, date, String.join(",", d.issues));
        }
        // 正常的日子也标上
        java.util.List<KaoqinParser.Day> normals = KaoqinParser.findNormal(text);
        Log.i("Daka", "WARDEN normal count=" + normals.size());
        for (KaoqinParser.Day d : normals) {
            String date = String.format(java.util.Locale.CHINA, "%04d-%02d-%02d",
                    year, d.month, d.day);
            if (Store.getIssue(c, date) == null) {
                Store.putIssue(c, date, "正常");
            }
        }
        bumpVersion(c);
        try {
            android.content.Intent bi = new android.content.Intent("com.dsh.dakacheck.SYNC_DONE");
            bi.setPackage(c.getPackageName());
            c.sendBroadcast(bi);
            Log.i("Daka", "WARDEN SYNC_DONE sent");
        } catch (Throwable t) {
            Log.i("Daka", "WARDEN sync broadcast fail " + t);
        }
        String desc = KaoqinParser.describe(issues);
        Timeline.add(c, "考勤检查：" + (desc.isEmpty() ? "无异常" : desc));
        if (issues.isEmpty()) return;

        // 分界点 = 旷工日 + 2 天的 14:30，之前一律“等待 N 日刷新”，之后判为真旷工
        java.util.List<String> maybe = new java.util.ArrayList<>();
        java.util.List<String> real = new java.util.ArrayList<>();
        java.util.Calendar now = java.util.Calendar.getInstance();
        int cy = now.get(java.util.Calendar.YEAR);
        int cm = now.get(java.util.Calendar.MONTH) + 1;
        int cd = now.get(java.util.Calendar.DAY_OF_MONTH);

        for (KaoqinParser.Day d : issues) {
            java.util.Calendar deadline = java.util.Calendar.getInstance();
            int dy = d.month < cm ? cy + 1 : cy;
            deadline.set(dy, d.month - 1, d.day, 14, 30, 0);
            deadline.add(java.util.Calendar.DAY_OF_MONTH, 2);
            deadline.set(java.util.Calendar.MILLISECOND, 0);

            java.util.Calendar show = (java.util.Calendar) deadline.clone();
            int showDay = show.get(java.util.Calendar.DAY_OF_MONTH);

            String issueName = d.issues.isEmpty() ? "旷工" : String.join("、", d.issues);
            String head = String.format(java.util.Locale.CHINA, "%d月%d日【%s】",
                    d.month, d.day, issueName);
            if (issueName.contains("缺勤")) {
                // 缺勤：去申请外出公干
                real.add(head + " 快去申请外出公干，审批完成后次日恢复正常");
            } else if (now.getTimeInMillis() < deadline.getTimeInMillis()) {
                maybe.add(head + " 若加班到0点后打卡，等待" + showDay + "日刷新考勤");
            } else {
                real.add(head + " 已超一天仍未更正，请核实后处理");
            }
        }

        StringBuilder body = new StringBuilder();
        for (String s1 : maybe) {
            if (body.length() > 0) body.append('\n');
            body.append(s1);
        }
        for (String s1 : real) {
            if (body.length() > 0) body.append('\n');
            body.append(s1);
        }
        String msg = body.toString();

        // 同一天只提醒一次：用「今天日期 + 内容」做去重键
        String today = String.format(java.util.Locale.CHINA, "%04d-%02d-%02d", cy, cm, cd);
        String dedupeKey = today + "|" + msg;
        String last = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString("last", "");

        if (isLastMonday()) {
            // 月末最后一个周一：直接全屏响铃
            Notify.alarm(c, "本月考勤还有异常", msg, "");
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().putString("last", dedupeKey).apply();
            return;
        }

        if (!dedupeKey.equals(last)) {
            Notify.unknown(c, "考勤有异常", msg);
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().putString("last", dedupeKey).apply();
        }
    }

    /** 按考勤日（4点为界）算相差几天。 */
    public static int logicalGap(String curLogical, int m2, int d2) {
        try {
            String[] p = curLogical.split("-");
            int y1 = Integer.parseInt(p[0]);
            int m1 = Integer.parseInt(p[1]);
            int d1 = Integer.parseInt(p[2]);
            java.util.Calendar a = java.util.Calendar.getInstance();
            a.set(y1, m1 - 1, d1, 0, 0, 0);
            a.set(java.util.Calendar.MILLISECOND, 0);
            java.util.Calendar b = java.util.Calendar.getInstance();
            int y2 = m2 < m1 ? y1 + 1 : y1;
            b.set(y2, m2 - 1, d2, 0, 0, 0);
            b.set(java.util.Calendar.MILLISECOND, 0);
            return (int) Math.round((a.getTimeInMillis() - b.getTimeInMillis()) / 86400000.0);
        } catch (Throwable t) {
            return 99;
        }
    }

    /** 两个日期相差多少天（to - from）。 */
    private static int dayDiff(int y1, int m1, int d1, int m2, int d2) {
        java.util.Calendar a = java.util.Calendar.getInstance();
        a.set(y1, m1 - 1, d1, 0, 0, 0);
        a.set(java.util.Calendar.MILLISECOND, 0);
        java.util.Calendar b = java.util.Calendar.getInstance();
        b.set(y2(m1, m2, y1), m2 - 1, d2, 0, 0, 0);
        b.set(java.util.Calendar.MILLISECOND, 0);
        long diff = b.getTimeInMillis() - a.getTimeInMillis();
        return (int) Math.round(diff / 86400000.0);
    }

    private static int y2(int m1, int m2, int y) {
        return m2 < m1 ? y + 1 : y;
    }

    /** 查出异常日期并按天数拼成一句话（日历页也用它）。 */
    public static String summaryLine(String text) {
        List<KaoqinParser.Day> issues = KaoqinParser.findIssues(text);
        if (issues.isEmpty()) return "本月考勤正常";
        java.util.Calendar now = java.util.Calendar.getInstance();
        int cy = now.get(java.util.Calendar.YEAR);
        int cm = now.get(java.util.Calendar.MONTH) + 1;
        int cd = now.get(java.util.Calendar.DAY_OF_MONTH);
        StringBuilder sb = new StringBuilder();
        String curLogical = Store.logicalDate();
        for (KaoqinParser.Day d : issues) {
            int diff = logicalGap(curLogical, d.month, d.day);
            if (sb.length() > 0) sb.append("；");
            sb.append(String.format(java.util.Locale.CHINA, "%d月%d日【旷工】", d.month, d.day));
            sb.append(diff <= 1 ? " 若加班到0点后打卡，等待隔日刷新考勤"
                    : " 已超一天仍未更正，请在下午14:30后核实后处理");
        }
        return sb.toString();
    }

    /** 最近一次写入数据的时间戳，日历页用它判断是否需要刷新。 */
    /** 记下「登录已失效」。 */
    public static void markLoginLost(Context c) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putBoolean("loginLost", true).commit();
    }

    /** 取出并清掉登录失效标记。 */
    public static boolean takeLoginLost(Context c) {
        android.content.SharedPreferences sp =
                c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        boolean v = sp.getBoolean("loginLost", false);
        if (v) sp.edit().putBoolean("loginLost", false).commit();
        return v;
    }

    public static long dataVersion(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong("ver", 0L);
    }

    private static void bumpVersion(Context c) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putLong("ver", System.currentTimeMillis()).commit();
    }

    /** 标记「刚成功获取到考勤」，日历页读取后弹提示。 */
    public static void setFetched(Context c) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putLong("fetchedAt", System.currentTimeMillis())
                .putBoolean("fetchedNew", true)
                .apply();
    }

    /** 取出并清掉「新获取」标记。 */
    public static boolean takeFetched(Context c) {
        android.content.SharedPreferences sp =
                c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        boolean v = sp.getBoolean("fetchedNew", false);
        if (v) sp.edit().putBoolean("fetchedNew", false).apply();
        return v;
    }

    /** 记录最近一次检查结果，供界面显示。 */
    public static void putResult(Context c, String r) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString("result", r)
                .putLong("at", System.currentTimeMillis())
                .apply();
    }

    public static String lastResult(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString("result", "");
    }

    public static long lastAt(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong("at", 0L);
    }

    /** 今天是否本月最后一个星期一。 */
    public static boolean isLastMonday() {
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) return false;
        return !hasMondayLater(cal.getTimeInMillis());
    }

    private static boolean hasMondayLater(long from) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(from);
        int month = cal.get(Calendar.MONTH);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        while (cal.get(Calendar.MONTH) == month) {
            if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) return true;
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return false;
    }
}
