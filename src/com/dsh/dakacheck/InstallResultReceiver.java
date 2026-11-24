package com.dsh.dakacheck;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** 接收系统安装器返回的结果。 */
public class InstallResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try {
            int status = i.getIntExtra("android.content.pm.extra.STATUS", -999);
            String msg = i.getStringExtra("android.content.pm.extra.STATUS_MESSAGE");
            Log.i("Daka", "install result status=" + status + " msg=" + msg);

            if (status == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
                // 系统要求用户确认：拉起安装界面
                Intent confirm = (Intent) i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        c.startActivity(confirm);
                    } catch (Throwable t) {
                        Log.i("Daka", "start confirm failed " + t);
                        Notify.info(c, "无法打开安装界面",
                                "请到文件管理器→Download 里点「打卡提醒-更新.apk」安装。");
                    }
                }
                return;
            }

            if (status == android.content.pm.PackageInstaller.STATUS_SUCCESS) {
                // 安装包**永不删除**，方便以后随时在 Download 里找到重装
                Log.i("Daka", "install success, apk kept at Download");
            } else {
                // 失败/取消：**保留文件**，并告知原因
                String reason = (msg == null || msg.isEmpty()) ? ("错误码 " + status) : msg;
                Notify.info(c, "安装未完成",
                        "安装包保留在文件管理器→Download。原因：" + reason);
                Log.i("Daka", "install not done, apk kept. reason=" + reason);
            }
        } catch (Throwable t) {
            Log.i("Daka", "install result handler failed: " + t);
        }
    }
}
