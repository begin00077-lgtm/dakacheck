package com.dsh.dakacheck;

import android.content.Context;
import android.util.Log;

/**
 * 自动登录 + 抓考勤：全部在不可见 Activity 里完成。
 *
 * 流程：
 * 1. 打开登录页
 * 2. 等页面加载完，用真实触摸点「软通(iPSA)」图标
 * 3. JS 填账号密码、勾协议、点登录
 * 4. 登录成功后进「我的考勤」，抓页面文字交给 Warden 解析
 */
public final class KaoqinAuto {

    private KaoqinAuto() {
    }

    /** 软通图标在登录页的坐标（1264×2780 机型实测）。 */
    public static final float ICON_X = 488f;
    public static final float ICON_Y = 2569f;
}
