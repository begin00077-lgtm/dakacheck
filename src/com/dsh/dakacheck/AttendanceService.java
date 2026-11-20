package com.dsh.dakacheck;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 只盯软通打卡这一个 App：它开着「考勤打卡」页面时，把页面上的
 * 已签到 / 已签退 读出来存下来，供 09:15 和 02:30 的判断使用。
 */
public class AttendanceService extends AccessibilityService {

    /** 服务是否真的被系统连上了（开关打开不等于连上）。 */
    public static volatile boolean connected = false;
    public static final String TARGET = "com.isoftstone.client.ipsa";
    /** 从企业微信「新考勤打卡」入口打开的页面也认。 */
    private static final String TARGET_WELINK = "com.isoftstone.welink.ipsa";
    public static final String ACTION_STATUS = "com.dsh.dakacheck.STATUS";

    private long lastScan = 0L;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        INSTANCE = this;
        Timeline.add(this, "无障碍已连接");
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.notificationTimeout = 100;
            setServiceInfo(info);
        }
        Scheduler.ensure(this);
        Log.i("Daka", "service connected");
        // 服务重连说明进程刚被拉起，顺手补检一次
        CatchUp.check(this);
    }

    private long lastLog = 0L;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        long now = System.currentTimeMillis();
        if (now - lastLog > 5000L) {
            lastLog = now;
            Log.i("Daka", "evt " + pkg);
        }
        if (pkg == null || !isTarget(pkg)) return;
        // 弹窗只停留 1–2 秒，扫描间隔必须够小才能抓到
        if (now - lastScan < 400L) return;
        lastScan = now;
        scan();
        scheduleFollowUp();
    }

    /**
     * 打完卡后页面往往不会再有明显变化，事件不一定触发。
     * 打卡 App 在前台时，隔一会儿再补扫几次，确保拿到最新一次打卡时间。
     */
    private void scheduleFollowUp() {
        for (long delay : FOLLOW_UPS) {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isTargetForeground()) return;
                    long now2 = System.currentTimeMillis();
                    if (now2 - lastScan < 500L) return;
                    lastScan = now2;
                    Log.i("Daka", "scan: follow-up");
                    scan();
                }
            }, delay);
        }
    }

    /** 当前前台是不是打卡 App。 */
    private boolean isTargetForeground() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            CharSequence p = root.getPackageName();
            return p != null && isTarget(p);
        } catch (Throwable t) {
            return false;
        }
    }

    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    /** 打卡后补扫的时间点（毫秒），覆盖页面异步刷新的情况。 */
    private static final long[] FOLLOW_UPS = {2000L, 5000L, 10000L, 20000L};

    private static boolean isTarget(CharSequence pkg) {
        return TARGET.contentEquals(pkg) || TARGET_WELINK.contentEquals(pkg);
    }

    private void scan() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Log.i("Daka", "scan: root null");
            return;
        }
        CharSequence pkg = root.getPackageName();
        if (pkg == null || !isTarget(pkg)) {
            Log.i("Daka", "scan: root pkg=" + pkg);
            return;
        }

        List<String> texts = new ArrayList<String>();
        collect(root, texts, 0);

        StatusReader.Snapshot s = StatusReader.parse(texts);
        long nowMs = System.currentTimeMillis();
        String stampNow = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                .format(new java.util.Date(nowMs));
        Log.i("Daka", "scan: texts=" + texts.size() + " in=" + s.sawIn + "(" + s.inTime
                + ") out=" + s.sawOut + "(" + s.outTime + ") date=" + s.date
                + " inTimes=" + s.inTimes + " outTimes=" + s.outTimes
                + " popupIn=" + s.inSuccess + " popupOut=" + s.outSuccess);

        boolean attendancePage = s.raw.contains("考勤") || s.raw.contains("签到") || s.raw.contains("签退");
        if (!attendancePage) return;

        // 页面属于「考勤日」：04:00–次日 04:00，0–4 点之间看到的是前一天的卡
        String date = Store.logicalDate();
        // 这一天确实看到过考勤页：没读到「已签到/已签退」才等于真的没打
        long prevPage = Store.pageAt(this, date);
        Store.putPage(this, date, nowMs);

        // 页面没显示时间时：优先用「当天第一次读到这页」的时刻（更接近真实打卡点），
        // 否则用检测到的当下；已经记过时间的绝不覆盖。
        String now = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                .format(new java.util.Date(prevPage > 0 ? prevPage : nowMs));
        if (s.sawIn) {
            String old = Store.getIn(this, date);
            String val;
            if (s.inSuccess) {
                // 弹窗出现的这一刻就是真实签到时刻，直接覆盖
                val = stampNow;
                Log.i("Daka", "popup 签到成功 -> in=" + val);
            } else {
                val = s.inTime != null ? s.inTime : (old != null && old.length() > 0 ? old : now);
            }
            Store.putIn(this, date, val);
        }
        if (s.sawOut) {
            String old = Store.getOut(this, date);
            String val;
            if (s.outSuccess) {
                // 弹窗出现的这一刻就是真实签退时刻，直接覆盖
                val = stampNow;
                Log.i("Daka", "popup 签退成功 -> out=" + val);
            } else {
                val = s.outTime != null ? s.outTime : (old != null && old.length() > 0 ? old : now);
            }
            Store.putOut(this, date, val);
        }
        Store.setSeen(this);
        Timeline.add(this, "读到页面 上班=" + (s.sawIn ? (s.inTime == null ? "有时间" : s.inTime) : "无")
                + " 下班=" + (s.sawOut ? (s.outTime == null ? "有时间" : s.outTime) : "无"));
        sendBroadcast(new Intent(ACTION_STATUS).setPackage(getPackageName()));
        Widgets.refreshAll(this);
        Archive.syncAsync(this);
    }

    private void collect(AccessibilityNodeInfo node, List<String> out, int depth) {
        if (node == null || depth > 40 || out.size() > 800) return;
        CharSequence t = node.getText();
        if (t != null && t.length() > 0) out.add(t.toString());
        CharSequence d = node.getContentDescription();
        if (d != null && d.length() > 0) out.add(d.toString());
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collect(child, out, depth + 1);
        }
    }

    /** 用无障碍手势在屏幕坐标点一下（等同真人触摸）。 */
    public static boolean clickAt(float x, float y) {
        AttendanceService svc = INSTANCE;
        if (svc == null) {
            Log.i("Daka", "clickAt: service null");
            return false;
        }
        try {
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(x, y);
            android.accessibilityservice.GestureDescription.StrokeDescription stroke =
                    new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 120);
            android.accessibilityservice.GestureDescription gd =
                    new android.accessibilityservice.GestureDescription.Builder()
                            .addStroke(stroke).build();
            svc.dispatchGesture(gd, new android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(android.accessibilityservice.GestureDescription d) {
                    Log.i("Daka", "clickAt done");
                }

                @Override
                public void onCancelled(android.accessibilityservice.GestureDescription d) {
                    Log.i("Daka", "clickAt cancelled");
                }
            }, null);
            return true;
        } catch (Throwable t) {
            Log.i("Daka", "clickAt failed " + t);
            return false;
        }
    }

    static volatile AttendanceService INSTANCE = null;

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        connected = false;
        INSTANCE = null;
        Timeline.add(this, "无障碍断开");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        connected = false;
        INSTANCE = null;
        super.onDestroy();
    }
}
