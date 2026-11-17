package com.dsh.dakacheck;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 全屏响铃页：响铃 + 震动 + 「去打卡 / 我已打卡 / 10 分钟后再提醒」。 */
public class AlarmActivity extends Activity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_BODY = "body";
    public static final String EXTRA_KIND = "kind";

    private MediaPlayer player;
    private Vibrator vibrator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = new Runnable() {
        @Override
        public void run() {
            stopAll();
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String body = getIntent().getStringExtra(EXTRA_BODY);
        final String kind = getIntent().getStringExtra(EXTRA_KIND);
        if (title == null) title = "该打卡了";
        if (body == null) body = "";

        setContentView(build(title, body, kind));
        startRing();
        handler.postDelayed(autoStop, 180_000L);
    }

    private View build(String title, String body, final String kind) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#B00020"));
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(34f);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTypeface(null, Typeface.BOLD);
        root.addView(tvTitle);

        TextView tvClock = new TextView(this);
        tvClock.setTextColor(Color.WHITE);
        tvClock.setTextSize(56f);
        tvClock.setGravity(Gravity.CENTER);
        tvClock.setText(new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date()));
        root.addView(tvClock);

        TextView tvBody = new TextView(this);
        tvBody.setText(body);
        tvBody.setTextColor(Color.WHITE);
        tvBody.setTextSize(18f);
        tvBody.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad;
        root.addView(tvBody, lp);

        root.addView(button("去打卡", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAttendanceApp();
                stopAll();
                finish();
            }
        }), lp);

        root.addView(button("我已打卡", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAll();
                finish();
            }
        }), lp);

        root.addView(button("10 分钟后再提醒", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Scheduler.snooze(AlarmActivity.this, 10,
                        kind == null ? Scheduler.KIND_IN : kind);
                stopAll();
                finish();
            }
        }), lp);

        return root;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(18f);
        b.setOnClickListener(l);
        return b;
    }

    private void openAttendanceApp() {
        Intent i = getPackageManager().getLaunchIntentForPackage(AttendanceService.TARGET);
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } else {
            Notify.info(this, "没找到软通打卡", "请手动打开软通打卡 App。");
        }
    }

    private void startRing() {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
            }
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception ignored) {
        }

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 800, 600};
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private void stopAll() {
        handler.removeCallbacks(autoStop);
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        Notify.cancelAlarm(this);
    }

    @Override
    protected void onDestroy() {
        stopAll();
        super.onDestroy();
    }
}
