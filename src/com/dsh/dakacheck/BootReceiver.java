package com.dsh.dakacheck;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 重启 / 改时间 / 更新后：重排闹钟，并把常驻服务拉起来。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Scheduler.ensure(context);
        Scheduler.ensureKeepAlive(context);
        Guard.ensure(context);
    }
}
