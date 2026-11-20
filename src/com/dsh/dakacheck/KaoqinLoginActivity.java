package com.dsh.dakacheck;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 考勤抓取页：人工登录一次，之后自动走「返回旧版 → 我的考勤」拿数据。
 *
 * 全程不依赖固定分辨率：元素位置由浏览器计算，点击交给无障碍服务。
 */
public class KaoqinLoginActivity extends Activity {
    private WebView wv;
    private boolean countdownShown = false;
    private boolean jumpedToAttendance = false;
    private boolean handled = false;
    private int retry = 0;

    /** 依次尝试的入口文字，命中即点。 */
    private static final String[] STEPS = {"返回旧版", "我的考勤"};

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wv = new WebView(this);
        // 用一个 FrameLayout 包住：WebView 永远在最底层，遮罩永远在最上层
        rootFrame = new android.widget.FrameLayout(this);
        rootFrame.addView(wv, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(rootFrame, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        // 桌面 UA：门户按 PC 布局渲染，「返回旧版 / 我的考勤」才会正常显示
        s.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        wv.getSettings().setSupportMultipleWindows(true);
        wv.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        wv.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, android.os.Message resultMsg) {
                Log.i("Daka", "KQL onCreateWindow");
                // 把新窗口的 URL 拿过来，在本 WebView 里加载
                WebView.HitTestResult r = view.getHitTestResult();
                if (r != null && r.getExtra() != null) {
                    Log.i("Daka", "KQL newWin url=" + r.getExtra());
                    showMask("\u767b\u5f55\u4e2d...");
                    wv.loadUrl(r.getExtra());
                    return false;
                }
                WebView tmp = new WebView(KaoqinLoginActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView vv, String u) {
                        Log.i("Daka", "KQL newWin load " + u);
                        if (u != null) wv.loadUrl(u);
                    }
                });
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(tmp);
                resultMsg.sendToTarget();
                return true;
            }
        });

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(v, url, favicon);
                Log.i("Daka", "KQL start " + url);
                if (url == null) return;
                if (isLoginPage(url)) {
                    // \u5230\u4e86\u767b\u5f55\u9875\uff1a\u5f7b\u5e95\u64a4\u6389\u906e\u7f69\uff0c\u8ba9\u7528\u6237\u80fd\u8f93\u5165
                    sawLoginPage = true;
                    hideMask();
                } else if (sawLoginPage || loggedInBefore) {
                    // 1) \u8fd9\u4e00\u8f6e\u5df2\u7ecf\u8fc7\u767b\u5f55\u9875\uff1b\u6216
                    // 2) \u4e0a\u6b21\u5df2\u767b\u5f55\u8fc7\uff0c\u8fd9\u6b21\u76f4\u63a5\u8df3\u8003\u52e4\u9875
                    //   -> \u7acb\u523b\u76d6\u4f4f\uff0c\u7edd\u4e0d\u9732\u51fa\u8003\u52e4\u9875
                    showMask("\u767b\u5f55\u4e2d...");
                } else {
                    // \u9996\u6b21\u767b\u5f55\u7684\u521d\u59cb portal \u8df3\u8f6c\uff1a\u4e0d\u76d6\uff0c\u8ba9\u7528\u6237\u770b\u5230\u767b\u5f55\u754c\u9762
                    Log.i("Daka", "KQL pre-login, no mask");
                }
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                Log.i("Daka", "KQL finish " + url);
                if (url == null) return;
                installOpenHook(v);
                if (isLoginPage(url)) {
                    // \u767b\u5f55\u9875\uff1a\u4e0d\u76d6\u906e\u7f69
                    sawLoginPage = true;
                    hideMask();
                } else if (sawLoginPage) {
                    showMask("\u767b\u5f55\u4e2d...");
                }
                // 立刻判断：URL 已经是门户/考勤页就不用等
                // 真正到考勤页：才算登录成功
                if (url.contains("Attendance/Home") || url.contains("HomeView")) {
                    Log.i("Daka", "KQL on attendance page -> grab");
                    grabAndFinish(v);
                    return;
                }
                // 门户页：只跳转，不算登录成功
                if (url.contains("portal") || url.contains("AIPortal")) {
                    if (jumpedToAttendance) return;
                    jumpedToAttendance = true;
                    Log.i("Daka", "KQL portal -> go attendance");
                    // 门户页已经说明登录态有效：从这里开始全部盖住，绝不露出考勤页
                    loggedInBefore = true;
                    showMask("\u767b\u5f55\u4e2d...");
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            v.loadUrl("https://ipsapro.isoftstone.com/portal/Special/hwkq");
                        }
                    }, 300);
                    return;
                }
                v.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        route(v);
                    }
                }, 800);
            }
        });

        wv.loadUrl("https://ipsapro.isoftstone.com/portal");

        // 兜底：40 秒还没结束，直接返回
        wv.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!countdownShown) {
                    Log.i("Daka", "KQL timeout");
                    showMask("\u767b\u5f55\u5931\u8d25");
                    finishAfter(1200);
                }
            }
        }, 40_000L);
    }

    /** 看当前页面处于哪一步，决定下一步做什么。 */
    private void route(final WebView v) {
        if (countdownShown) return;
        v.evaluateJavascript(
                "(function(){return document.body?document.body.innerText:'';})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        String txt = value == null ? "" : value;
                        Log.i("Daka", "KQL text len=" + txt.length());

                        // 已经离开登录页（出现账号/密码框以外的东西）→ 立刻盖遮罩
                        boolean onLoginUrl = false;
                        try {
                            onLoginUrl = isLoginPage(v.getUrl());
                        } catch (Throwable ignored) {
                        }
                        boolean stillLogin = onLoginUrl
                                || txt.contains("\u8bf7\u8f93\u5165\u8d26\u53f7")
                                || txt.contains("\u8bf7\u8f93\u5165\u5bc6\u7801")
                                || txt.contains("\u66f4\u591a\u767b\u5f55\u65b9\u5f0f")
                                || txt.contains("\u94ae\u9489\u5feb\u6377\u767b\u5f55")
                                || txt.contains("\u4e00\u952e\u767b\u5f55")
                                || txt.contains("\u8f6f\u901a\u536b\u58eb");
                        if (stillLogin) {
                            sawLoginPage = true;
                            hideMask();
                        } else if (sawLoginPage && !txt.isEmpty()) {
                            showMask("\u767b\u5f55\u4e2d...");
                        }

                        if (isAttendancePage(txt)) {
                            once(txt);
                            return;
                        }
                        retry = 0;
                        if (txt.contains("请输入账号") || txt.contains("更多登录方式")) {
                            Log.i("Daka", "KQL still login page");
                            return;
                        }
                        // 看到门户不算成功，继续跳考勤页
                        clickNextStep(v, 0);
                    }
                });
    }

    /** 用 Instrumentation 注入触摸（等同系统 input tap，WebView 能收到）。 */
    private void injectTap(final float x, final float y) {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object cur = at.getMethod("currentActivityThread").invoke(null);
            final android.app.Instrumentation ins =
                    (android.app.Instrumentation) at.getMethod("getInstrumentation").invoke(cur);
            if (ins == null) {
                AttendanceService.clickAt(x, y);
                return;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        long t = android.os.SystemClock.uptimeMillis();
                        android.view.MotionEvent down = android.view.MotionEvent.obtain(
                                t, t, android.view.MotionEvent.ACTION_DOWN, x, y, 0);
                        android.view.MotionEvent up = android.view.MotionEvent.obtain(
                                t, t + 60, android.view.MotionEvent.ACTION_UP, x, y, 0);
                        ins.sendPointerSync(down);
                        try { Thread.sleep(40); } catch (Throwable ignored) {}
                        ins.sendPointerSync(up);
                        Log.i("Daka", "KQL injectTap ok " + x + "," + y);
                    } catch (Throwable e) {
                        Log.i("Daka", "KQL injectTap fail " + e);
                        AttendanceService.clickAt(x, y);
                    }
                }
            }).start();
        } catch (Throwable e) {
            Log.i("Daka", "KQL instrumentation fail " + e);
            AttendanceService.clickAt(x, y);
        }
    }

    /** 只处理一次，避免重复抓取。 */
    /** 在考勤页抓一次：拿到数据就提示成功并返回。 */
    private int grabTries = 0;

    private void grabAndFinish(final WebView v) {
        if (handled) return;
        if (grabTries++ > 12) {
            Log.i("Daka", "KQL grab give up -> fail");
            showMask("\u767b\u5f55\u5931\u8d25");
            finishAfter(1200);
            return;
        }
        v.evaluateJavascript(
                "(function(){return document.body?document.body.innerText:'';})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String val) {
                        String txt = val == null ? "" : val;
                        if (isAttendancePage(txt)) {
                            once(txt);
                        } else {
                            Log.i("Daka", "KQL grab retry");
                            v.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    grabAndFinish(v);
                                }
                            }, 800);
                        }
                    }
                });
    }

    private android.widget.TextView maskView = null;
    private android.widget.TextView maskSub = null;
    /** WebView 与遮罩的容器：遮罩始终位于最上层。 */
    private android.widget.FrameLayout rootFrame = null;
    /** 是否已经看到过登录页（决定离开登录页后要不要盖遮罩）。 */
    private boolean sawLoginPage = false;
    /** 本次会话登录是否已成功（成功后 Cookie 有效，下次会直接跳考勤页）。 */
    private boolean loggedInBefore = false;
    private android.view.View maskRoot = null;

    /** 撤掉遮罩（回到登录页时用）。 */
    private void hideMask() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (maskRoot != null) {
                        maskRoot.setVisibility(android.view.View.GONE);
                    }
                } catch (Throwable t) {
                    Log.i("Daka", "hideMask fail " + t);
                }
            }
        });
    }

    /** 全屏遮罩：盖住下方网页，只看到提示文字。 */
    private void showMask(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    float d = getResources().getDisplayMetrics().density;
                    if (maskView == null) {
                        android.widget.FrameLayout mask = new android.widget.FrameLayout(KaoqinLoginActivity.this);
                        mask.setBackgroundColor(0xFFF7F8FA);
                        mask.setClickable(true);

                        android.widget.LinearLayout box = new android.widget.LinearLayout(KaoqinLoginActivity.this);
                        box.setOrientation(android.widget.LinearLayout.VERTICAL);
                        box.setGravity(android.view.Gravity.CENTER);

                        maskView = new android.widget.TextView(KaoqinLoginActivity.this);
                        maskView.setTextSize(22f);
                        maskView.setTextColor(0xFF2E7D32);
                        maskView.setTypeface(null, android.graphics.Typeface.BOLD);
                        maskView.setGravity(android.view.Gravity.CENTER);

                        maskSub = new android.widget.TextView(KaoqinLoginActivity.this);
                        maskSub.setTextSize(14f);
                        maskSub.setTextColor(0xFF888888);
                        maskSub.setGravity(android.view.Gravity.CENTER);
                        maskSub.setPadding(0, (int) (12 * d), 0, 0);

                        box.addView(maskView);
                        box.addView(maskSub);

                        android.widget.FrameLayout.LayoutParams blp =
                                new android.widget.FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT);
                        blp.gravity = android.view.Gravity.CENTER;
                        mask.addView(box, blp);

                        android.view.ViewGroup root =
                                rootFrame != null
                                        ? rootFrame
                                        : (android.view.ViewGroup) findViewById(android.R.id.content);
                        root.addView(mask, new android.widget.FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
                        // 确保遮罩压在最上层
                        mask.bringToFront();
                        maskRoot = mask;
                    }
                    if (maskRoot != null) maskRoot.setVisibility(android.view.View.VISIBLE);
                    maskView.setText(text);
                    maskSub.setText(text.contains("\u6210\u529f") ? "\u6b63\u5728\u8fd4\u56de..."
                            : (text.contains("\u5931\u8d25") ? "\u8bf7\u91cd\u8bd5" : "\u6b63\u5728\u83b7\u53d6\u8003\u52e4\u6570\u636e"));
                } catch (Throwable t) {
                    Log.i("Daka", "mask fail " + t);
                }
            }
        });
    }

    /** 延时关闭页面。 */
    private void finishAfter(long ms) {
        new android.os.Handler(getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, ms);
    }

    private void once(final String txt) {
        if (handled) return;
        handled = true;
        loggedInBefore = true;
        Log.i("Daka", "KQL data ready -> success");
        // 先取颜色（按颜色判异常），再弹成功并返回
        ColorParser.sample(wv, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String colors) {
                Warden.handleColorAndText(KaoqinLoginActivity.this, colors, txt);
                showMask("\u767b\u5f55\u6210\u529f");
                finishAfter(900);
            }
        });
    }

    private boolean isAttendancePage(String t) {
        return t.contains("初次打卡") || t.contains("末次打卡")
                || (t.contains("员工信息") && t.contains("月"));
    }

    /** 依次尝试 STEPS 里的入口文字，找不到就试下一个。 */
    private void clickNextStep(final WebView v, final int idx) {
        if (countdownShown || idx >= STEPS.length) return;
        final String kw = STEPS[idx];
        String js = "(function(){"
                + "var all=document.querySelectorAll('*');"
                + "for(var i=0;i<all.length;i++){"
                + "  var e=all[i];"
                + "  var t=(e.innerText||'').replace(/\\s/g,'');"
                + "  if(t!=='" + kw + "') continue;"
                + "  if(e.children.length>0) continue;"
                + "  e.scrollIntoView({block:'center',inline:'center'});"
                + "  var r=e.getBoundingClientRect();"
                + "  return 'found:'+(r.left+r.width/2)+','+(r.top+r.height/2)"
                + "    +','+window.innerWidth+','+window.innerHeight"
                + "    +','+(window.pageXOffset||0)+','+(window.pageYOffset||0);"
                + "}"
                + "return 'miss';"
                + "})()";

        v.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String res) {
                String raw = res == null ? "" : res.replace("\"", "");
                Log.i("Daka", "KQL step '" + kw + "' = " + raw);
                if (!raw.contains("found")) {
                    clickNextStep(v, idx + 1);
                    return;
                }
                v.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String[] q = raw.split(":")[1].split(",");
                        float ex = Float.parseFloat(q[0]);
                        float ey = Float.parseFloat(q[1]);
                        float vpW = Float.parseFloat(q[2]);
                        float vpH = Float.parseFloat(q[3]);

                        int[] loc = new int[2];
                        v.getLocationOnScreen(loc);
                        // 视口坐标 → 屏幕坐标（按 WebView 实际尺寸缩放）
                        float cx = loc[0] + ex * v.getWidth() / vpW;
                        float cy = loc[1] + ey * v.getHeight() / vpH;
                        Log.i("Daka", "KQL tap '" + kw + "' elem=" + ex + "," + ey
                                + " vp=" + vpW + "x" + vpH
                                + " screen=" + cx + "," + cy);
                        fireJsClick(v, kw);
                        injectTap(cx, cy);
                        v.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                readOpened(v, 0);
                            }
                        }, 1200);
                        v.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                route(v);
                            }
                        }, 4500);
                    }
                }, 900);
            }
        });
    }

    /** 劫持 window.open，把点击后要打开的地址记到 window.__opened。 */
    private void installOpenHook(WebView v) {
        v.evaluateJavascript(
                "(function(){"
                        + "if(window.__hooked) return 'already';"
                        + "window.__hooked=1;"
                        + "window.__opened='';"
                        + "var org=window.open;"
                        + "window.open=function(u,n,f){"
                        + "  window.__opened=(u||'')+'|'+(n||'');"
                        + "  return null;"
                        + "};"
                        + "return 'hooked';"
                        + "})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String r) {
                        Log.i("Daka", "KQL hook=" + r);
                    }
                });
    }

    /** 读取劫持到的地址。 */
    private void readOpened(WebView v, final int tryIdx) {
        v.evaluateJavascript("(function(){return window.__opened||'';})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String r) {
                        String u = r == null ? "" : r.replace("\"", "");
                        Log.i("Daka", "KQL opened=" + u);
                        if (u.isEmpty()) {
                            if (tryIdx < 3) {
                                v.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        readOpened(v, tryIdx + 1);
                                    }
                                }, 1500);
                            }
                            return;
                        }
                        String url = u.split("\\|")[0];
                        if (url.startsWith("http")) {
                            Log.i("Daka", "KQL follow opened " + url);
                            v.loadUrl(url);
                        }
                    }
                });
    }

    /** 对元素及其父链派发完整鼠标事件序列（兼容 jQuery/Vue/React 委托）。 */
    private void fireJsClick(WebView v, String kw) {
        String js = "(function(){"
                + "var all=document.querySelectorAll('*');"
                + "for(var i=0;i<all.length;i++){"
                + "  var e=all[i];"
                + "  var t=(e.innerText||'').replace(/\\s/g,'');"
                + "  if(t!=='" + kw + "') continue;"
                + "  if(e.children.length>0) continue;"
                + "  var el=e;"
                + "  var done=[];"
                + "  var info='';"
                + "  var pa=e;"
                + "  for(var k3=0;k3<5&&pa;k3++){"
                + "    if(pa.tagName==='A'){"
                + "      var at=pa.attributes;"
                + "      for(var ai=0;ai<at.length;ai++){"
                + "        info+=at[ai].name+'='+(''+at[ai].value).substring(0,40)+';';"
                + "      }"
                + "      break;"
                + "    }"
                + "    pa=pa.parentElement;"
                + "  }"
                + "  if(info) {"
                + "    var eid2=e.id||'';"
                + "    var pz=e;"
                + "    for(var kz=0;kz<5&&pz;kz++){"
                + "      if(pz.id){ eid2=pz.id; break; }"
                + "      pz=pz.parentElement;"
                + "    }"
                + "    if(eid2.indexOf('g_')===0){"
                + "      return 'SPECIAL:/portal/Special/'+eid2.substring(2);"
                + "    }"
                + "    var urls=[];"
                + "    var scripts=document.querySelectorAll('script');"
                + "    for(var si=0;si<scripts.length;si++){"
                + "      var sc=scripts[si].innerHTML||'';"
                + "      var mm=sc.match(/https?:[^'\"\\s]*[Aa]ttendance[^'\"\\s]*/g);"
                + "      if(mm) urls=urls.concat(mm.slice(0,3));"
                + "    }"
                + "    var anchors=document.querySelectorAll('a');"
                + "    for(var ai2=0;ai2<anchors.length;ai2++){"
                + "      var hh=anchors[ai2].href||'';"
                + "      if(hh.indexOf('ttendance')>=0) urls.push(hh);"
                + "    }"
                + "    return 'ATTR['+info+'] URLS['+urls.slice(0,4).join(' ')+']';"
                + "  }"
                + "  var href='';"
                + "  var probe=e;"
                + "  for(var k2=0;k2<6&&probe;k2++){"
                + "    var h=probe.getAttribute&&probe.getAttribute('href');"
                + "    if(h&&h.length>0&&h!=='#'){ href=h; break; }"
                + "    probe=probe.parentElement;"
                + "  }"
                + "  var eid=e.id||'';"
                + "  var pa3=e;"
                + "  for(var k4=0;k4<5&&pa3;k4++){"
                + "    if(pa3.id){ eid=pa3.id; break; }"
                + "    pa3=pa3.parentElement;"
                + "  }"
                + "  if(eid.indexOf('g_')===0){"
                + "    var sp='/portal/Special/'+eid.substring(2);"
                + "    done.push('SPECIAL:'+sp);"
                + "  } else if(eid){"
                + "    done.push('ID:'+eid);"
                + "  }"
                + "  for(var up=0;up<6&&el;up++){"
                + "    var r=el.getBoundingClientRect();"
                + "    var o={bubbles:true,cancelable:true,view:window,"
                + "           clientX:r.left+r.width/2,clientY:r.top+r.height/2};"
                + "    ['mousedown','mouseup','click'].forEach(function(tp){"
                + "      try{ el.dispatchEvent(new MouseEvent(tp,o)); }catch(x){}"
                + "    });"
                + "    try{ el.click(); }catch(x){}"
                + "    done.push(el.tagName+(el.className?('.'+(''+el.className).split(' ')[0]):''));"
                + "    el=el.parentElement;"
                + "  }"
                + "  return done.join('>');"
                + "}"
                + "return 'miss';"
                + "})()";
        v.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String r) {
                Log.i("Daka", "KQL fireClick " + r);
                String raw = r == null ? "" : r.replace("\"", "");
                if (raw.contains("SPECIAL:")) {
                    String path = raw.substring(raw.indexOf("SPECIAL:") + 8);
                    int end = path.indexOf('>');
                    if (end > 0) path = path.substring(0, end);
                    final String url = "https://ipsapro.isoftstone.com" + path;
                    Log.i("Daka", "KQL go special " + url);
                    pollGrab();
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            v.loadUrl(url);
                        }
                    }, 300);
                }
            }
        });
    }

    /** 把抓取工作交给后台不可见的 Activity。 */
    private void handOffToBackground() {
        try {
            startActivity(new android.content.Intent(this, KaoqinGrabActivity.class));
        } catch (Throwable t) {
            Log.i("Daka", "KQL handoff fail " + t);
        }
    }

    /** 转后台后继续抓，抓到就停并关闭。 */
    private void keepGrabbing() {
        keepGrabbing(0);
    }

    private void keepGrabbing(final int n) {
        if (n > 25) {
            Log.i("Daka", "KQL keepGrab give up");
            finish();
            return;
        }
        if (wv == null) return;
        wv.postDelayed(new Runnable() {
            @Override
            public void run() {
                wv.evaluateJavascript(
                        "(function(){return document.body?document.body.innerText:'';})()",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String v) {
                                String txt = v == null ? "" : v;
                                if (isAttendancePage(txt)) {
                                    Log.i("Daka", "KQL keepGrab got " + txt.length());
                                    Warden.handleText(KaoqinLoginActivity.this, txt);
                                    finish();
                                    return;
                                }
                                // 还在门户：继续点入口
                                clickNextStep(wv, 0);
                                keepGrabbing(n + 1);
                            }
                        });
            }
        }, 2000);
    }

    /** 倒计时期间反复尝试抓考勤数据，抓到就停。 */
    private void pollGrab() {
        pollGrab(0);
    }

    private void pollGrab(final int n) {
        if (n > 8) return;
        if (wv == null) return;
        wv.postDelayed(new Runnable() {
            @Override
            public void run() {
                wv.evaluateJavascript(
                        "(function(){return document.body?document.body.innerText:'';})()",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String v) {
                                String txt = v == null ? "" : v;
                                if (isAttendancePage(txt)) {
                                    Log.i("Daka", "KQL poll got " + txt.length());
                                    once(txt);
                                    return;
                                }
                                pollGrab(n + 1);
                            }
                        });
            }
        }, 1200);
    }

    /** 全屏遮罩 + 倒计时。盖住下方页面跳转，避免闪屏。 */
    private void showCountdown() {
        if (countdownShown) return;
        countdownShown = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    float d = getResources().getDisplayMetrics().density;

                    // 不透明全屏遮罩
                    android.widget.FrameLayout mask = new android.widget.FrameLayout(KaoqinLoginActivity.this);
                    mask.setBackgroundColor(0xFFF7F8FA);

                    android.widget.LinearLayout box = new android.widget.LinearLayout(KaoqinLoginActivity.this);
                    box.setOrientation(android.widget.LinearLayout.VERTICAL);
                    box.setGravity(android.view.Gravity.CENTER);
                    android.widget.FrameLayout.LayoutParams blp =
                            new android.widget.FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT);
                    blp.gravity = android.view.Gravity.CENTER;
                    box.setLayoutParams(blp);

                    android.widget.TextView title = new android.widget.TextView(KaoqinLoginActivity.this);
                    title.setText("登录成功");
                    title.setTextSize(22f);
                    title.setTextColor(0xFF2E7D32);
                    title.setTypeface(null, android.graphics.Typeface.BOLD);
                    title.setGravity(android.view.Gravity.CENTER);

                    final android.widget.TextView sub = new android.widget.TextView(KaoqinLoginActivity.this);
                    sub.setTextSize(16f);
                    sub.setTextColor(0xFF666666);
                    sub.setGravity(android.view.Gravity.CENTER);
                    sub.setPadding(0, (int) (14 * d), 0, 0);

                    box.addView(title);
                    box.addView(sub);
                    mask.addView(box);

                    android.view.ViewGroup root =
                            (android.view.ViewGroup) findViewById(android.R.id.content);
                    root.addView(mask, new android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));

                    final int[] left = {2};
                    sub.setText(left[0] + " 秒后返回...");
                    final android.os.Handler h = new android.os.Handler(getMainLooper());
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            left[0]--;
                            if (left[0] <= 0) {
                                Log.i("Daka", "KQL finish back to calendar");
                                grabThenFinish();
                                return;
                            }
                            sub.setText(left[0] + " 秒后返回...");
                            h.postDelayed(this, 1000);
                        }
                    }, 750);
                } catch (Throwable t) {
                    Log.i("Daka", "countdown fail " + t);
                    finish();
                }
            }
        });
    }

    /** 返回前尽量抓一次，然后交给后台并关闭。 */
    private void grabThenFinish() {
        if (wv == null) {
            finish();
            return;
        }
        wv.evaluateJavascript(
                "(function(){return document.body?document.body.innerText:'';})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String v) {
                        String txt = v == null ? "" : v;
                        if (isAttendancePage(txt)) {
                            once(txt);
                            Log.i("Daka", "KQL final grab ok");
                        } else {
                            Log.i("Daka", "KQL final grab miss, background");
                            handOffToBackground();
                        }
                        finish();
                    }
                });
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

    /**
     * 是否是登录/认证相关页面。
     *
     * IPSA 的登录页 URL 并不含 "/login"，
     * 而是走 passport 域名或 corplink/agw 等路径，
     * 这里统一判定，避免首次登录被遮罩盖住。
     */
    private static boolean isLoginPage(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains("/login")
                || u.contains("passport.")          // 统一认证域名
                || u.contains("corplink")
                || u.contains("/agw/callback")
                || u.contains("sso")
                || u.contains("oauth")
                || u.contains("signin");
    }
}
