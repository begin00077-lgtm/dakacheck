package com.dsh.dakacheck;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 后台抓取考勤：1×1 透明、不占屏幕、不抢前台。
 *
 * 登录状态已存在 Cookie 里，这里直接打开门户 → 我的考勤 → 抓文字 → 关闭。
 */
public class KaoqinGrabActivity extends Activity {
    private WebView wv;
    private int tries = 0;
    private boolean done = false;
    private boolean jumped = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1×1 透明，挪到屏幕外，不干扰使用
        getWindow().setLayout(1, 1);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.x = -100;
        lp.y = -100;
        lp.alpha = 0f;
        getWindow().setAttributes(lp);

        wv = new WebView(this);
        setContentView(wv, new ViewGroup.LayoutParams(1, 1));

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                Log.i("Daka", "GRAB finish " + url);
                // 已在考勤页或 Special 页：直接抓内容
                if (isAttendanceUrl(url) || url.contains("Special/hwkq")) {
                    tick(v, 0);
                    return;
                }
                // 还在门户：跳一次
                if (jumped) {
                    tick(v, 0);
                    return;
                }
                jumped = true;
                Log.i("Daka", "GRAB jump now");
                v.loadUrl("https://ipsapro.isoftstone.com/portal/Special/hwkq");
            }
        });

        Log.i("Daka", "GRAB start");
        wv.loadUrl("https://ipsapro.isoftstone.com/portal");

        // 30 秒还没结果就收工
        wv.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!done) {
                    Log.i("Daka", "GRAB timeout");
                    finish();
                }
            }
        }, 30_000L);
    }

    private void tick(final WebView v, final int n) {
        if (done) return;
        if (n > 50) {
            Log.i("Daka", "GRAB give up");
            finish();
            return;
        }
        v.postDelayed(new Runnable() {
            @Override
            public void run() {
                v.evaluateJavascript(
                        "(function(){return document.body?document.body.innerText:'';})()",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String val) {
                                String txt = val == null ? "" : val;
                                if (isAttendance(txt)) {
                                    done = true;
                                    Log.i("Daka", "GRAB got " + txt.length());
                                    Log.i("Daka", "GRAB RAW >>> " + txt.replace("\n", "|"));
                                    Warden.handleText(KaoqinGrabActivity.this, txt);
                                    showOk();
                                    return;
                                }
                                // 门户页：跳到「我的考勤」
                                if (txt.contains("我的考勤") || txt.contains("员工自助平台")) {
                                    String id = findId(txt);
                                    Log.i("Daka", "GRAB portal, go special");
                                    v.loadUrl("https://ipsapro.isoftstone.com/portal/Special/hwkq");
                                }
                                tick(v, n + 1);
                            }
                        });
            }
        }, 250);
    }

    /** 弹一个自带提示的页面，1.5 秒后自动关。 */
    private void showOk() {
        try {
            startActivity(new android.content.Intent(this, KaoqinOkActivity.class));
        } catch (Throwable t) {
            Log.i("Daka", "GRAB showOk fail " + t);
        }
        finish();
    }

    private String findId(String txt) {
        return "g_hwkq";
    }

    private boolean isAttendanceUrl(String u) {
        return u != null && (u.contains("Attendance/Home") || u.contains("HomeView"));
    }

    private boolean isAttendance(String t) {
        return t.contains("初次打卡") || t.contains("末次打卡")
                || (t.contains("员工信息") && t.contains("月"));
    }

    @Override
    protected void onDestroy() {
        try {
            if (wv != null) {
                wv.stopLoading();
                wv.destroy();
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
