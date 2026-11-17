package com.dsh.dakacheck;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** 打卡高峰前把进程叫醒，让无障碍服务重新绑定，保证那一刻有人读页面。 */
public class KeepAliveReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("Daka", "keepalive");
        Guard.ensure(context);
        if (!Guard.accessibilityEnabled(context)) {
            Guard.reviveAccessibility(context);
        }
        Scheduler.ensureKeepAlive(context);
        Scheduler.ensurePunchWake(context);
        Scheduler.ensure(context);
    }
}
