package com.dsh.dakacheck;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 后台 WebView 登录考勤系统并抓当月考勤页面文字。
 * 挂在 1×1 透明悬浮窗上，避免后台 WebView 被系统冻结。
 */
public final class KaoqinFetch {
    private static final String TAG = "Daka";

    public interface Callback {
        void onText(String text);
    }

    private KaoqinFetch() {
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void fetch(final Context c, final Callback cb) {
        final Handler h = new Handler(Looper.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                final WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
                if (wm == null) return;

                final WebView wv = new WebView(c);
                WebSettings s = wv.getSettings();
                s.setJavaScriptEnabled(true);
                s.setDomStorageEnabled(true);
                s.setDatabaseEnabled(true);
                CookieManager.getInstance().setAcceptCookie(true);
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        1, 1,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT);
                lp.alpha = 0f;

                final boolean[] added = {false};
                try {
                    wm.addView(wv, lp);
                    added[0] = true;
                    Log.i(TAG, "KAOQIN overlay added");
                } catch (Throwable t) {
                    Log.i(TAG, "KAOQIN overlay failed: " + t);
                }

                final StringBuilder log = new StringBuilder();
                final boolean[] done = {false};

                wv.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        log.append("nav: ").append(url).append('\n');
                        return false;
                    }

                    @Override
                    public void onPageFinished(WebView v, String url) {
                        log.append("finish: ").append(url).append('\n');
                        if (done[0]) return;
                        // 只在真正的登录页上操作，中间跳转的空页面直接跳过
                        if (url == null || !url.contains("login")) {
                            log.append("(skip non-login)\n");
                            return;
                        }
                        v.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (done[0]) return;
                                v.evaluateJavascript(
                                        "(function(){" +
                                                "var all=document.querySelectorAll('*');" +
                                                "var hit=null;" +
                                                "for(var i=0;i<all.length;i++){" +
                                                "  var e=all[i];" +
                                                "  var tx=(e.innerText||'').replace(/\\s/g,'');" +
                                                "  if(tx.indexOf('更多登录方式')>=0){ hit=e; break; }" +
                                                "}" +
                                                "if(!hit) return 'no more-btn';" +
                                                "var tgt=hit;" +
                                                "for(var k=0;k<5&&tgt.parentElement;k++){" +
                                                "  if(tgt.tagName==='BUTTON'||tgt.tagName==='A'||" +
                                                "     (tgt.getAttribute&&tgt.getAttribute('role')==='button')" +
                                                "     ||tgt.onclick||tgt.tagName==='SPAN'&&tgt.style.cursor==='pointer') break;" +
                                                "  tgt=tgt.parentElement;" +
                                                "}" +
                                                "try{tgt.click();}catch(e){}" +
                                                "try{hit.click();}catch(e){}" +
                                                "var ev=new MouseEvent('click',{bubbles:true,cancelable:true,view:window});" +
                                                "try{hit.dispatchEvent(ev);}catch(e){}" +
                                                "return 'clicked:'+tgt.tagName;})()",
                                        new android.webkit.ValueCallback<String>() {
                                            @Override
                                            public void onReceiveValue(String value) {
                                                log.append("more: ").append(value).append('\n');
                                                v.postDelayed(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        dump(v, log, cb, wm, done, added[0]);
                                                    }
                                                }, 3000);
                                            }
                                        });
                            }
                        }, 3500);
                    }
                });

                Log.i(TAG, "KAOQIN loading " + Warden.URL_LOGIN);
                wv.loadUrl(Warden.URL_LOGIN);

                // 30 秒兜底：无论如何都收尾，避免窗口一直挂着
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (done[0]) return;
                        done[0] = true;
                        Log.i(TAG, "KAOQIN timeout");
                        try {
                            cb.onText(log + "\nTIMEOUT");
                        } catch (Throwable ignored) {
                        }
                        release(wm, wv, added[0]);
                    }
                }, 30_000L);
            }
        });
    }

    private static void dump(WebView v, final StringBuilder log, final Callback cb,
                             final WindowManager wm, final boolean[] done, final boolean added) {
        v.evaluateJavascript(
                "(function(){var t=document.body?document.body.innerText:'';"
                        + "var i=document.querySelectorAll('input');"
                        + "var ids=[];for(var k=0;k<i.length&&k<12;k++){"
                        + "ids.push((i[k].id||i[k].name||'?')+':'+i[k].type);}"
                        + "var btn=[];var b=document.querySelectorAll('button,a,span,div');"
                        + "for(var j=0;j<b.length&&btn.length<10;j++){"
                        + "var s=(b[j].innerText||'').replace(/\\s/g,'');"
                        + "if(s.length>0&&s.length<12&&(s.indexOf('登')>=0||s.indexOf('确定')>=0))btn.push(s);}"
                        + "return t.substring(0,3000)+'\\n[[INPUTS]]'+ids.join('|')"
                        + "+'\\n[[BTN]]'+btn.join('|')+'\\n[[URL]]'+location.href;})()",
                new android.webkit.ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        String txt = value == null ? "" : value;
                        Log.i(TAG, "KAOQIN_TEXT>>> " + log + "\n" + txt);
                        done[0] = true;
                        try {
                            cb.onText(log + "\n" + txt);
                        } catch (Throwable ignored) {
                        }
                        release(wm, v, added);
                    }
                });
    }

    private static void release(WindowManager wm, WebView wv, boolean added) {
        try {
            wv.stopLoading();
            wv.loadUrl("about:blank");
            if (added) wm.removeView(wv);
            wv.destroy();
            Log.i(TAG, "KAOQIN released");
        } catch (Throwable t) {
            Log.i(TAG, "KAOQIN release failed: " + t);
        }
    }
}
