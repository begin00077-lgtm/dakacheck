package com.dsh.dakacheck;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

/**
 * 前台响铃服务：闹钟触发时由它负责发出真正的响声 + 震动。
 *
 * 为什么不用 AlarmActivity 响：
 * Android 10+ 后台禁止 startActivity，AlarmActivity 经常拉不起来，
 * 结果就是「只弹窗不响铃」。前台服务不受这个限制，能稳定出声。
 *
 * 响铃最长 3 分钟，或用户点「停止」/「我已打卡」结束。
 */
public class RingService extends Service {

    public static final String ACTION_START = "com.dsh.dakacheck.RING_START";
    public static final String ACTION_STOP = "com.dsh.dakacheck.RING_STOP";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_BODY = "body";
    public static final String EXTRA_KIND = "kind";

    private static final String CH = "ring_running";
    private static final int ID_ONGOING = 2001;
    private static final int ID_STOP = 2101;
    private static final long AUTO_STOP_MS = 180_000L;

    /** 供其它组件判断是否正在响铃。 */
    public static volatile boolean ringing = false;

    private MediaPlayer player;
    private Vibrator vibrator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = new Runnable() {
        @Override
        public void run() {
            stopSelf();
        }
    };

    /** 启动响铃（幂等：已经在响就只更新通知）。 */
    public static void start(Context c, String title, String body, String kind) {
        try {
            Intent i = new Intent(c, RingService.class)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_BODY, body)
                    .putExtra(EXTRA_KIND, kind);
            if (Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable t) {
            Log.i("Daka", "RingService start failed: " + t);
        }
    }

    /** 停止响铃。 */
    public static void stop(Context c) {
        try {
            c.startService(new Intent(c, RingService.class).setAction(ACTION_STOP));
        } catch (Throwable ignored) {
        }
        try {
            c.stopService(new Intent(c, RingService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String title = intent == null ? null : intent.getStringExtra(EXTRA_TITLE);
        String body = intent == null ? null : intent.getStringExtra(EXTRA_BODY);
        String kind = intent == null ? null : intent.getStringExtra(EXTRA_KIND);
        if (title == null) title = "该打卡了";
        if (body == null) body = "";

        startForeground(ID_ONGOING, buildNotification(title, body, kind));

        if (!ringing) {
            ringing = true;
            startSound();
            startVibrate();
            handler.removeCallbacks(autoStop);
            handler.postDelayed(autoStop, AUTO_STOP_MS);
            Log.i("Daka", "ringing: " + title);
        }
        return START_STICKY;
    }

    private Notification buildNotification(String title, String body, String kind) {
        NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    CH, "响铃中", NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(this, AlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(AlarmActivity.EXTRA_TITLE, title)
                .putExtra(AlarmActivity.EXTRA_BODY, body)
                .putExtra(AlarmActivity.EXTRA_KIND, kind);
        PendingIntent piOpen = PendingIntent.getActivity(this, ID_ONGOING, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, RingService.class).setAction(ACTION_STOP);
        PendingIntent piStop = PendingIntent.getService(this, ID_STOP, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(piOpen)
                .setFullScreenIntent(piOpen, true)
                .addAction(new Notification.Action.Builder(
                        null, "停止响铃", piStop).build());
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_MAX);
        return b.build();
    }

    private void startSound() {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                // 只把闹钟音量调到最大；不静音模式、不改铃声
                int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
            }
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
            Log.i("Daka", "ring sound started");
        } catch (Throwable t) {
            Log.i("Daka", "ring sound failed: " + t);
            player = null;
        }
    }

    private void startVibrate() {
        try {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long[] pattern = {0, 800, 600};
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        } catch (Throwable t) {
            Log.i("Daka", "ring vibrate failed: " + t);
        }
    }

    @Override
    public void onDestroy() {
        ringing = false;
        handler.removeCallbacks(autoStop);
        if (player != null) {
            try {
                player.stop();
            } catch (Throwable ignored) {
            }
            try {
                player.release();
            } catch (Throwable ignored) {
            }
            player = null;
        }
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Throwable ignored) {
            }
        }
        Notify.cancelAlarm(this);
        try {
            stopForeground(true);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
