package com.dsh.dakacheck;

import android.service.notification.NotificationListenerService;

/**
 * 只为了「被系统拉起」：系统在通知事件时会绑定本服务，
 * 一旦绑定就把闹钟、无障碍、常驻服务重新拉起来。
 */
public class NotifListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Guard.ensure(this);
    }
}
