package com.dsh.dakacheck;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 后台静默抓取考勤：不启动 Activity、不显示通知、不占屏幕。
 *
 * 依赖无障碍服务常驻进程承载一个 0×0 的 WebView。
 */
public final class BackgroundGrab {

    private static WebView wv;
    private static Handler handler;
    private static boolean running = false;
    private static int tries = 0;
    private static long t0 = 0;
    private static volatile long startEpoch = 0;
    private static boolean jumped = false;
    /** 目标年月，如 "2026-10"；空表示当前月 */
    private static String targetMonth = "";
    private static Context appCtx;

    private BackgroundGrab() {
    }

    /** 开始后台抓取。已经在对账中则忽略。 */
    @SuppressLint("SetJavaScriptEnabled")
    public static void start(Context c) {
        start(c, "");
    }

    public static void start(Context c, String month) {
        targetMonth = month == null ? "" : month;
        if (running) {
            Log.i("Daka", "BG already running");
            return;
        }
        if (AttendanceService.INSTANCE == null) {
            Log.i("Daka", "BG no accessibility service");
            return;
        }
        running = true;
        tries = 0;
        t0 = android.os.SystemClock.elapsedRealtime();
        startEpoch++;
        jumped = false;
        appCtx = c.getApplicationContext();
        handler = new Handler(Looper.getMainLooper());
        Log.i("Daka", "BG start");

        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (wv == null) {
                        wv = new WebView(appCtx);
                        wv.setLayoutParams(new ViewGroup.LayoutParams(0, 0));
                        // 加到当前 Activity 的窗口上；没有则只用内存 WebView
                        if (AttendanceService.INSTANCE == null) {
                            Log.i("Daka", "BG no service for addView, memory only");
                        }
                        try {
                            android.view.WindowManager wm =
                                    (android.view.WindowManager) appCtx.getSystemService(Context.WINDOW_SERVICE);
                            android.view.WindowManager.LayoutParams lp =
                                    new android.view.WindowManager.LayoutParams(
                                            1, 1,
                                            android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                                            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                                    | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                                            android.graphics.PixelFormat.TRANSLUCENT);
                            lp.x = -200;
                            lp.y = -200;
                            lp.alpha = 0f;
                            wm.addView(wv, lp);
                        } catch (Throwable t) {
                            Log.i("Daka", "BG addView fail " + t);
                        }

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
                                Log.i("Daka", "BG finish +" + (android.os.SystemClock.elapsedRealtime() - t0) + "ms " + url);
                                tries = 0;
                                // 登录页 URL 直接判失败（不等轮询）
                                if (url != null && (url.contains("feilian.isoftstone.com")
                                        && url.contains("/login"))) {
                                    Log.i("Daka", "BG login url +"
                                            + (android.os.SystemClock.elapsedRealtime() - t0) + "ms");
                                    fail("同步失败，需重新登录");
                                    return;
                                }
                                // 已在目标页：直接抓（不再跳）
                                if (isAttendanceUrl(url)) {
                                    gotAttendancePage(v);
                                    return;
                                }
                                // Special/hwkq 页：抓一次看看是不是考勤页
                                if (url != null && url.contains("Special/hwkq")) {
                                    gotAttendancePage(v);
                                    return;
                                }
                                // 门户页：只跳一次
                                if (url != null && url.contains("portal") && !jumped) {
                                    jumped = true;
                                    v.loadUrl("https://ipsapro.isoftstone.com/portal/Special/hwkq");
                                    return;
                                }
                                poll(v, true);
                            }
                        });
                    }
                    wv.loadUrl("https://ipsapro.isoftstone.com/portal");
                } catch (Throwable t) {
                    Log.i("Daka", "BG start fail " + t);
                    stop();
                }
            }
        });

        // 用独立线程兜底超时，不受 handler 清理影响
        final long epoch = startEpoch;
        new Thread(new Runnable() {
            @Override
            public void run() {
                long wait = 4500L;
                try {
                    Thread.sleep(wait);
                } catch (Throwable ignored) {
                }
                if (running && startEpoch == epoch) {
                    Log.i("Daka", "BG timeout +"
                            + (android.os.SystemClock.elapsedRealtime() - t0) + "ms");
                    final String r = "同步失败，需重新登录";
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            fail(r);
                        }
                    });
                }
            }
        }).start();
    }

    /** 到达考勤页，直接抓当前月。 */
    private static void gotAttendancePage(final WebView v) {
        poll(v, true);
    }

    private static boolean isAttendanceUrl(String u) {
        return u != null && (u.contains("Attendance/Home") || u.contains("HomeView"));
    }

    private static boolean isAttendance(String t) {
        return t.contains("初次打卡") || t.contains("末次打卡")
                || (t.contains("员工信息") && t.contains("月"));
    }

    private static void poll(final WebView v) {
        poll(v, false);
    }

    private static void poll(final WebView v, boolean immediately) {
        if (!running) return;
        if (tries++ > 40) {
            Log.i("Daka", "BG give up +"
                    + (android.os.SystemClock.elapsedRealtime() - t0) + "ms");
            fail("同步失败，需重新登录");
            return;
        }
        Runnable task = new Runnable() {
            @Override
            public void run() {
                if (v == null) return;
                v.evaluateJavascript(
                        "(function(){return document.body?document.body.innerText:'';})()",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String val) {
                                String txt = val == null ? "" : val;
                                if (isAttendance(txt)) {
                                    Log.i("Daka", "BG got +"
                                            + (android.os.SystemClock.elapsedRealtime() - t0) + "ms");
                                    final String body = txt;
                                    ColorParser.sample(v, new ValueCallback<String>() {
                                        @Override
                                        public void onReceiveValue(String colors) {
                                            Log.i("Daka", "BG colors len="
                                                    + (colors == null ? 0 : colors.length()));
                                            Warden.handleColorAndText(appCtx, colors, body);
                                            stop();
                                        }
                                    });
                                } else if (isLoginPage(txt)) {
                                    Log.i("Daka", "BG login required +"
                                            + (android.os.SystemClock.elapsedRealtime() - t0) + "ms");
                                    fail("同步失败，需重新登录");
                                } else {
                                    poll(v);
                                }
                            }
                        });
            }
        };
        if (immediately) {
            handler.post(task);
        } else {
            handler.postDelayed(task, 250);
        }
    }

    /** 页面是登录页（登录态已失效）。 */
    /** 把每个日期格子的颜色打出来，用于按颜色判异常。 */
    private static void dumpColors(WebView v) {
        v.evaluateJavascript(
                "(function(){"
                + "function cls(e){"
                + "  var out=[];"
                + "  var el=e;"
                + "  for(var i=0;i<4&&el;i++){"
                + "    var c=(el.className||'')+'';"
                + "    if(c) out.push(c);"
                + "    var bg=window.getComputedStyle(el).backgroundColor||'';"
                + "    if(bg&&bg!=='rgba(0, 0, 0, 0)'&&bg!=='transparent') out.push('bg:'+bg);"
                + "    var col=window.getComputedStyle(el).color||'';"
                + "    if(col) out.push('fg:'+col);"
                + "    el=el.parentElement;"
                + "  }"
                + "  return out.join(' ## ').substring(0,220);"
                + "}"
                + "var out=[];"
                + "var all=document.querySelectorAll('td,div,span,li');"
                + "var seen={};"
                + "for(var i=0;i<all.length;i++){"
                + "  var t=(all[i].innerText||'').replace(/\\s/g,'');"
                + "  var m=t.match(/^(\\d{1,2})\\u6708(\\d{1,2})\\u65e5/);"
                + "  if(!m) continue;"
                + "  if(seen[m[1]+'-'+m[2]]) continue;"
                + "  seen[m[1]+'-'+m[2]]=1;"
                + "  out.push(m[1]+'/'+m[2]+' => '+cls(all[i]));"
                + "}"
                + "return out.join(' ||| ');"
                + "})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String val) {
                        Log.i("Daka", "BG COLOR >>> " + val);
                    }
                });
    }

    private static boolean isLoginPage(String t) {
        return t.contains("请输入账号") || t.contains("请输入密码")
                || t.contains("更多登录方式") || t.contains("铉钉快捷登录")
                || t.contains("一键登录");
    }

    /** 同步失败：收工 + 发通知提醒重新登录。 */
    private static void fail(String reason) {
        stop();
        try {
            Warden.markLoginLost(appCtx);
            Notify.info(appCtx, "考勤同步失败", reason);
            Log.i("Daka", "BG fail +" + (android.os.SystemClock.elapsedRealtime() - t0) + "ms: " + reason);
        } catch (Throwable t) {
            Log.i("Daka", "BG fail notify error " + t);
        }
    }

    /** 收工：把 WebView 摘掉释放内存。 */
    public static void stop() {
        running = false;
        startEpoch++;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (wv != null) {
                        android.view.WindowManager wm =
                                (android.view.WindowManager) appCtx.getSystemService(Context.WINDOW_SERVICE);
                        wm.removeViewImmediate(wv);
                        wv.destroy();
                    }
                } catch (Throwable ignored) {
                }
                wv = null;
                Log.i("Daka", "BG stopped");
            }
        });
    }
}
