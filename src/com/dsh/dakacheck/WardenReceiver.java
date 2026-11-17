package com.dsh.dakacheck;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** 旷工检查的定时入口。 */
public class WardenReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("Daka", "warden tick");
        Scheduler.ensureWarden(context);   // 重排明天
        if (!Config.kaoqinOn(context)) return;
        Warden.check(context);
    }
}
