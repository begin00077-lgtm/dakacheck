package com.dsh.dakacheck;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;

/**
 * 到点后的一次「现场核对」：把软通打卡打开，等无障碍服务把页面读进来，
 * 再根据读到的事实决定响不响。锁屏时没法读，就按配置降级。
 */
public class VerifyActivity extends Activity {
    public static final String EXTRA_DATE = "date";
    public static final String EXTRA_KIND = "kind";
    public static final String EXTRA_RETRY = "retry";
    public static final String EXTRA_SILENT = "silent";
    private static final long TIMEOUT_MS = 25000L;
    private static final long POLL_MS = 700L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String date;
    private String kind;
    private int retry;
    private boolean silent;
    private long startAt;
    private boolean done;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        date = getIntent().getStringExtra(EXTRA_DATE);
        kind = getIntent().getStringExtra(EXTRA_KIND);
        retry = getIntent().getIntExtra(EXTRA_RETRY, 0);
        silent = getIntent().getBooleanExtra(EXTRA_SILENT, false);
        if (kind == null) kind = Scheduler.KIND_IN;
        if (date == null) date = CheckReceiver.targetDate(this, kind);
        startAt = System.currentTimeMillis();

        TextView tv = new TextView(this);
        tv.setText("正在核对 " + date + " 的打卡状态…");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(16f);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(Color.parseColor("#CC222222"));
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        setContentView(tv);

        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean locked = km != null && km.isKeyguardLocked();
        if (locked) {
            Log.i("Daka", "verify: locked, cannot read page");
            finishWithDecision(false);
            return;
        }

        Intent i = getPackageManager().getLaunchIntentForPackage(AttendanceService.TARGET);
        if (i == null) {
            Log.i("Daka", "verify: target not installed");
            finishWithDecision(false);
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Throwable t) {
            Log.i("Daka", "verify: launch failed " + t);
            finishWithDecision(false);
            return;
        }
        handler.postDelayed(poll, POLL_MS);
    }

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (done) return;
            if (Store.pageAt(VerifyActivity.this, date) >= startAt) {
                finishWithDecision(true);
                return;
            }
            if (System.currentTimeMillis() - startAt > TIMEOUT_MS) {
                finishWithDecision(false);
                return;
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    private void finishWithDecision(boolean readFresh) {
        if (done) return;
        done = true;
        handler.removeCallbacks(poll);
        if (readFresh) {
            String time;
            boolean punched;
            if (Scheduler.KIND_IN.equals(kind)) {
                time = Store.getIn(this, date);
                punched = time != null;
            } else {
                time = Store.getOut(this, date);
                punched = time != null;
            }
            if (punched) {
                Decide.confirmedDone(this, kind, date, time);
            } else {
                Decide.confirmedMissing(this, kind, date);
            }
        } else if (!silent) {
            // 第一次读不到：提醒一次，并安排稍后重试（等手机解锁/页面能打开）
            if (retry == 0) {
                Decide.unverified(this, kind, date);
                Scheduler.scheduleRetry(this, kind, 1);
            } else if (retry < 3) {
                Log.i("Daka", "retry " + retry + " still unverified, will retry again");
                Scheduler.scheduleRetry(this, kind, retry + 1);
            }
        }
        finish();
        if (silent) {
            try {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
