package com.dsh.dakacheck;

import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按平台标记判断考勤状态（对应页面图例）：
 *
 *   红色三角 -> 考勤/TM 工时异常（td 带 class="error-red"）
 *   绿色三角 -> 请假 / 外出公干（td 内容里出现对应文字）
 *   其它且真有打卡记录 -> 正常
 *   空白格子 -> 不标
 */
public final class ColorParser {

    public static final class Day {
        public int month;
        public int day;
        public boolean bad;
        public boolean holiday;
    }

    private static final Pattern ENTRY =
            Pattern.compile("(\\d{1,2})/(\\d{1,2})\\s*=>\\s*([^|]*)");

    private ColorParser() {
    }

    public static List<Day> parse(String raw) {
        List<Day> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        String s = raw.replace("\\\"", "").replace("\"", "");
        Matcher m = ENTRY.matcher(s);
        while (m.find()) {
            Day d = new Day();
            try {
                d.month = Integer.parseInt(m.group(1));
                d.day = Integer.parseInt(m.group(2));
            } catch (Throwable t) {
                continue;
            }
            String cls = m.group(3);
            d.bad = cls.contains("error-red") || cls.contains("error_red");
            d.holiday = cls.contains("holiday_td") || cls.contains("holiday-td");
            out.add(d);
        }
        Log.i("Daka", "COLOR parsed " + out.size() + " days");
        return out;
    }

    /** 取页面每个日期格子的 class。 */
    public static void sample(WebView v, ValueCallback<String> cb) {
        String js = "(function(){"
                + "function cls(e){"
                + "  var out=[];"
                + "  var el=e;"
                + "  for(var i=0;i<4&&el;i++){"
                + "    var c=String(el.className||'');"
                + "    if(c.length>0){out.push(c);}"
                + "    el=el.parentElement;"
                + "  }"
                + "  return out.join(' ');"
                + "}"
                + "var res=[];"
                + "var all=document.querySelectorAll('td,div,span,li');"
                + "var seen={};"
                + "for(var i=0;i<all.length;i++){"
                + "  var t=String(all[i].innerText||'').replace(/\\s/g,'');"
                + "  var m=t.match(/^(\\d{1,2})\\u6708(\\d{1,2})\\u65e5/);"
                + "  if(!m){continue;}"
                + "  var k=m[1]+'/'+m[2];"
                + "  if(seen[k]){continue;}"
                + "  seen[k]=1;"
                + "  res.push(k+' => '+cls(all[i]));"
                + "}"
                + "return res.join(' ||| ');"
                + "})()";
        v.evaluateJavascript(js, cb);
    }

    /** 读当前页面显示的年月，来自 span.navitem（例如 2026-11）。 */
    public static void pageMonth(WebView v, ValueCallback<String> cb) {
        String js = "(function(){"
                + "var e=document.querySelector('span.navitem');"
                + "if(!e){return '';}"
                + "return String(e.textContent||'').replace(/\\s/g,'');"
                + "})()";
        v.evaluateJavascript(js, cb);
    }

    /** 点开日期选择器（点 span.navitem）。 */
    public static void openPicker(WebView v, ValueCallback<String> cb) {
        String js = "(function(){"
                + "var nv=document.querySelector('span.navitem');"
                + "if(!nv){return 'no-nav';}"
                + "var r=nv.getBoundingClientRect();"
                + "try{"
                + "  var ev=document.createEvent('MouseEvents');"
                + "  ev.initMouseEvent('click',true,true,window,1,"
                + "    r.left+r.width/2,r.top+r.height/2,r.left+r.width/2,r.top+r.height/2,"
                + "    false,false,false,false,0,null);"
                + "  nv.dispatchEvent(ev);"
                + "  nv.click();"
                + "  return 'ok';"
                + "}catch(e){return 'err:'+e;}"
                + "})()";
        v.evaluateJavascript(js, cb);
    }

    /** 列出所有跟 datepicker / navitem 相关元素的真实状态。 */
    public static void pickerOpen(WebView v, ValueCallback<String> cb) {
        String js = "(function(){"
                + "var out=[];"
                + "var sels=['.datepicker','.nav-item','span.navitem','th.prev','th.next','.td_datepicker_container'];"
                + "for(var i=0;i<sels.length;i++){"
                + "  var list=document.querySelectorAll(sels[i]);"
                + "  out.push(sels[i]+'='+list.length);"
                + "  if(list.length>0){"
                + "    var e=list[0];"
                + "    var r=e.getBoundingClientRect();"
                + "    var st=window.getComputedStyle(e);"
                + "    out.push('  w='+Math.round(r.width)+' h='+Math.round(r.height)"
                + "      +' disp='+st.display+' vis='+st.visibility);"
                + "  }"
                + "}"
                + "return out.join(' | ');"
                + "})()";
        v.evaluateJavascript(js, cb);
    }

    /** 在已展开的选择器里点上/下月。 */
    public static void stepMonth(WebView v, int delta, ValueCallback<String> cb) {
        String sel = (delta < 0) ? "th.prev" : "th.next";
        String js = "(function(){"
                + "var list=document.querySelectorAll('" + sel + "');"
                + "if(!list||list.length===0){return 'miss';}"
                + "for(var i=0;i<list.length;i++){"
                + "  var th=list[i];"
                + "  var r=th.getBoundingClientRect();"
                + "  if(r.width>1&&r.height>1){"
                + "    th.click();"
                + "    return 'ok';"
                + "  }"
                + "}"
                + "list[0].click();"
                + "return 'ok-forced';"
                + "})()";
        v.evaluateJavascript(js, cb);
    }
}
