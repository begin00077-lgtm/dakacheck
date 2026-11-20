package com.dsh.dakacheck;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 解锁 / 开机解锁时补检一次。
 *
 * 手机放在兜里一夜，闹钟可能被系统省电策略干掉；用户早上解锁就是最好的补检时机。
 */
public class UnlockReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null) return;
        String a = intent.getAction();
        Log.i("Daka", "unlock event " + a);
        Guard.ensure(c);
        CatchUp.check(c);
    }
}
