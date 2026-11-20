package com.dsh.dakacheck;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

/** 工时日历：每天打卡时间 + 工时，左右拖动时相邻月份跟手补位。 */
public class HoursActivity extends Activity {
    private int year, month;
    private TextView monthTitle, summary, tipView;
    private TextView checkView;
    private LinearLayout checkRow;          // 工时核对结果
    private LinearLayout tipBox;         // 异常提示容器（可点击折叠）
    private TextView tipArrow;           // 折叠小三角
    private TextView tip;                // 异常提示正文
    private boolean tipExpanded = false;
    private String lastAbnormalDetail = "";   // 异常描述（放进 ? 说明）
    private String lastCheckDetail = "";      // 工时核对算法说明

    private FrameLayout pager;
    private LinearLayout grid;      // 当前月
    private LinearLayout gridAdj;   // 相邻月
    private int neighborDelta = 0;  // +1 下月 / -1 上月 / 0 未准备

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH) + 1;
        setContentView(build());
        Holidays.refreshAsync(this);
        render();
        startWatcher();
    }

    private final android.os.Handler watchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long lastVer = 0;
    private boolean loginLostShown = false;

    /** 持续监听：数据变化/登录失效都立即反应。 */
    private void startWatcher() {
        lastVer = Warden.dataVersion(this);
        watchHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    long now = Warden.dataVersion(HoursActivity.this);
                    if (now != lastVer) {
                        lastVer = now;
                        Log.i("Daka", "HOURS data changed -> render");
                        render();
                    }
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (!loginLostShown && Warden.takeLoginLost(HoursActivity.this)) {
                        loginLostShown = true;
                        Log.i("Daka", "HOURS login lost -> dialog");
                        showLoginLost();
                    }
                } catch (Throwable ignored) {
                }
                watchHandler.postDelayed(this, 500);
            }
        }, 500);
    }

    /** 生成一个圆角带边框的按钮背景。 */
    private android.graphics.drawable.GradientDrawable btnBg(float radius, String fill, String stroke) {
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(radius);
        g.setColor(Color.parseColor(fill));
        g.setStroke((int) dp(1), Color.parseColor(stroke));
        return g;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(16);
        root.setPadding(pad, (int) dp(18), pad, (int) dp(24));
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        LinearLayout monthBar = new LinearLayout(this);
        monthBar.setOrientation(LinearLayout.HORIZONTAL);
        monthBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView prev = new TextView(this);
        prev.setText("‹");
        prev.setTextSize(24f);
        prev.setTextColor(Color.parseColor("#2E7D32"));
        prev.setPadding((int) dp(8), (int) dp(4), (int) dp(16), (int) dp(4));
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shiftMonth(-1);
            }
        });
        monthBar.addView(prev);

        monthTitle = new TextView(this);
        monthTitle.setTextSize(18f);
        monthTitle.setTextColor(Color.parseColor("#111111"));
        monthTitle.setTypeface(null, Typeface.BOLD);
        monthTitle.setGravity(Gravity.CENTER);
        monthBar.addView(monthTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView next = new TextView(this);
        next.setText("›");
        next.setTextSize(24f);
        next.setTextColor(Color.parseColor("#2E7D32"));
        next.setPadding((int) dp(16), (int) dp(4), (int) dp(8), (int) dp(4));
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shiftMonth(1);
            }
        });
        monthBar.addView(next);
        root.addView(monthBar, mblp);

        LinearLayout week = new LinearLayout(this);
        week.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = (int) dp(6);
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        for (String n : names) {
            TextView t = new TextView(this);
            t.setText(n);
            t.setTextSize(13f);
            t.setTextColor(Color.parseColor("#777777"));
            t.setGravity(Gravity.CENTER);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            week.addView(t);
        }
        root.addView(week, wlp);

        pager = new FrameLayout(this) {
            private float downX, downY;
            private boolean monthSwipe;

            @Override
            public boolean dispatchTouchEvent(android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downX = e.getX();
                        downY = e.getY();
                        monthSwipe = false;
                        break;
                    case android.view.MotionEvent.ACTION_MOVE: {
                        float dx = e.getX() - downX;
                        float dy = e.getY() - downY;
                        if (Math.abs(dx) > 30 && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                            monthSwipe = true;
                        }
                        if (monthSwipe) return true;   // 左右滑动：自己吃掉，不给子视图
                        break;
                    }
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL: {
                        if (monthSwipe) {
                            float dx = e.getX() - downX;
                            if (Math.abs(dx) > 80) {
                                shiftMonth(dx < 0 ? 1 : -1);
                            }
                            monthSwipe = false;
                            return true;
                        }
                        break;
                    }
                }
                return super.dispatchTouchEvent(e);
            }
        };
        pager.setTag("nav_no_swipe");   // 日历区域滑动=翻月，不切页面
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = (int) dp(6);
        root.addView(pager, plp);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pager.addView(grid);

        gridAdj = new LinearLayout(this);
        gridAdj.setOrientation(LinearLayout.VERTICAL);
        gridAdj.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        gridAdj.setVisibility(View.INVISIBLE);
        pager.addView(gridAdj);

        // 工时补录 + 异常提醒（从顶部移到累计上方）
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toolsLp.topMargin = (int) dp(10);
        tools.setLayoutParams(toolsLp);

        TextView fillBtn2 = new TextView(this);
        fillBtn2.setText("工时补录");
        fillBtn2.setTextSize(14f);
        fillBtn2.setTextColor(Color.parseColor("#2E7D32"));
        fillBtn2.setTypeface(null, Typeface.BOLD);
        fillBtn2.setGravity(Gravity.CENTER);
        fillBtn2.setPadding((int) dp(14), (int) dp(9), (int) dp(14), (int) dp(9));
        fillBtn2.setBackground(btnBg(dp(10), "#E8F5E9", "#A5D6A7"));
        fillBtn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFillPicker();
            }
        });
        tools.addView(fillBtn2);

        View toolSpacer = new View(this);
        tools.addView(toolSpacer, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView loginBtn = new TextView(this);
        loginBtn.setText("登录IPSA");
        loginBtn.setTextSize(14f);
        loginBtn.setTextColor(Color.parseColor("#2E7D32"));
        loginBtn.setTypeface(null, Typeface.BOLD);
        loginBtn.setGravity(Gravity.CENTER);
        loginBtn.setPadding((int) dp(14), (int) dp(9), (int) dp(14), (int) dp(9));
        loginBtn.setBackground(btnBg(dp(10), "#E8F5E9", "#A5D6A7"));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.leftMargin = (int) dp(10);
        loginBtn.setLayoutParams(llp);
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openLogin();
            }
        });
        tools.addView(loginBtn);

        View toolSpacer2 = new View(this);
        tools.addView(toolSpacer2, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 异常提醒永久开启，这里不再放开关；原位置改为 IPSA 同步
        TextView syncBtn = new TextView(this);
        syncBtn.setText("↻ IPSA同步");
        syncBtn.setTextSize(14f);
        syncBtn.setTextColor(Color.parseColor("#2E7D32"));
        syncBtn.setTypeface(null, Typeface.BOLD);
        syncBtn.setGravity(Gravity.CENTER);
        syncBtn.setPadding((int) dp(14), (int) dp(9), (int) dp(14), (int) dp(9));
        syncBtn.setBackground(btnBg(dp(10), "#E8F5E9", "#A5D6A7"));
        syncBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                syncIpsa();
            }
        });
        tools.addView(syncBtn);

        root.addView(tools);

        // 累计工时 + 小问号
        LinearLayout sumRow = new LinearLayout(this);
        sumRow.setOrientation(LinearLayout.HORIZONTAL);
        sumRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = (int) dp(10);

        summary = new TextView(this);
        summary.setTextSize(15f);
        summary.setTextColor(Color.parseColor("#111111"));
        summary.setTypeface(null, Typeface.BOLD);
        sumRow.addView(summary);

        TextView help = new TextView(this);
        help.setText("?");
        help.setTextSize(11f);
        help.setTextColor(Color.parseColor("#666666"));
        help.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable cbg = new android.graphics.drawable.GradientDrawable();
        cbg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        cbg.setColor(Color.parseColor("#E0E0E0"));
        cbg.setStroke((int) dp(1), Color.parseColor("#BDBDBD"));
        help.setBackground(cbg);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                (int) dp(18), (int) dp(18));
        hlp.leftMargin = (int) dp(6);
        help.setLayoutParams(hlp);
        help.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCalendarHelp();
            }
        });
        sumRow.addView(help);

        root.addView(sumRow, slp);

        // 工时核对行 + 行内 ?
        checkRow = new LinearLayout(this);
        checkRow.setOrientation(LinearLayout.HORIZONTAL);
        checkRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams crl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        crl.topMargin = (int) dp(8);

        checkView = new TextView(this);
        checkView.setTextSize(14f);
        checkView.setTypeface(null, Typeface.BOLD);
        checkRow.addView(checkView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView checkHelp = helpDot();
        checkHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCheckHelp();
            }
        });
        checkRow.addView(checkHelp);
        root.addView(checkRow, crl);

        // 异常提醒：每类一行，行尾带 ?
        tipBox = new LinearLayout(this);
        tipBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = (int) dp(6);
        tipBox.setLayoutParams(tlp);
        root.addView(tipBox);

        // 兼容旧字段：正文不再单独显示，统一由 tipBox 生成
        tip = new TextView(this);
        tipView = tip;
        tip.setVisibility(View.GONE);

        tipArrow = new TextView(this);
        tipArrow.setVisibility(View.GONE);


        scroll.addView(root);
        return NavBar.attach(this, NavBar.TAB_HOURS, scroll);
    }

    /** 把相邻月份画进 gridAdj，并按拖动方向摆好位置。 */
    private void prepareNeighbor(int delta) {
        int y = year, m = month + delta;
        if (m > 12) {
            m = 1;
            y++;
        } else if (m < 1) {
            m = 12;
            y--;
        }
        renderInto(gridAdj, y, m);
        gridAdj.setVisibility(View.VISIBLE);
        neighborDelta = delta;
    }

    private void commitSwipe(final int delta, int w) {
        grid.animate().translationX(-delta * w).setDuration(180).start();
        gridAdj.animate().translationX(0f).setDuration(180).withEndAction(new Runnable() {
            @Override
            public void run() {
                month += delta;
                if (month > 12) {
                    month = 1;
                    year++;
                } else if (month < 1) {
                    month = 12;
                    year--;
                }
                render();
                grid.setTranslationX(0f);
                gridAdj.setVisibility(View.INVISIBLE);
                gridAdj.setTranslationX(0f);
                neighborDelta = 0;
            }
        }).start();
    }

    private void cancelSwipe(int delta, int w) {
        grid.animate().translationX(0f).setDuration(160).start();
        gridAdj.animate().translationX(delta * w).setDuration(160).withEndAction(new Runnable() {
            @Override
            public void run() {
                gridAdj.setVisibility(View.INVISIBLE);
                gridAdj.setTranslationX(0f);
                neighborDelta = 0;
            }
        }).start();
    }

    /** 左右箭头：淡入 + 轻移。 */
    private void shiftMonth(int delta) {
        grid.animate().cancel();
        grid.setAlpha(0f);
        grid.setTranslationX(delta > 0 ? dp(40) : -dp(40));
        grid.animate().alpha(1f).translationX(0f).setDuration(200).start();
        month += delta;
        if (month > 12) {
            month = 1;
            year++;
        } else if (month < 1) {
            month = 12;
            year--;
        }
        render();
    }

    private void render() {
        monthTitle.setText(year + " 年 " + month + " 月");
        renderInto(grid, year, month);

        double total = 0;
        int counted = 0;
        int days = daysOf(year, month);
        for (int d = 1; d <= days; d++) {
            String date = String.format(Locale.CHINA, "%04d-%02d-%02d", year, month, d);
            Double h = Work.hoursOf(this, date);
            if (h != null) {
                total += h;
                counted++;
            }
        }
        summary.setText(String.format(Locale.CHINA, "%d 月累计：%.2f 小时　（已统计 %d 天）", month, total, counted));
        updateCheck(total, year, month, days);
        rebuildAbnormal();
    }

    /**
     * 异常提醒：按异常类型分组，每类一行，行尾带 ? 看解释。
     *
     *   异常提醒：【缺勤】11月13日              ?
     *            【旷工】11月19日              ?
     * 无异常时显示：异常提醒：无
     */
    private void rebuildAbnormal() {
        if (tipBox == null) return;
        tipBox.removeAllViews();

        java.util.Calendar cn = java.util.Calendar.getInstance();
        int cyy = cn.get(java.util.Calendar.YEAR);
        int cmm = cn.get(java.util.Calendar.MONTH) + 1;

        // 类型 -> 日期列表；同时记录每类给的提示语
        LinkedHashMap<String, ArrayList<Integer>> groups = new LinkedHashMap<>();
        LinkedHashMap<String, String> hints = new LinkedHashMap<>();

        for (int d = 1; d <= daysOf(cyy, cmm); d++) {
            String dt = String.format(Locale.CHINA, "%04d-%02d-%02d", cyy, cmm, d);
            String iss = Store.getIssue(this, dt);
            if (iss == null || iss.isEmpty()) continue;
            if (iss.contains("正常")) continue;
            if (iss.contains("请假") || iss.contains("公干") || iss.contains("出差")
                    || iss.contains("调休") || iss.contains("年假")
                    || iss.contains("事假") || iss.contains("病假")) continue;

            ArrayList<Integer> list = groups.get(iss);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(iss, list);
            }
            list.add(d);

            if (!hints.containsKey(iss)) {
                java.util.Calendar dl = java.util.Calendar.getInstance();
                dl.set(cyy, cmm - 1, d, 14, 30, 0);
                dl.add(java.util.Calendar.DAY_OF_MONTH, 2);
                dl.set(java.util.Calendar.MILLISECOND, 0);
                int showDay = dl.get(java.util.Calendar.DAY_OF_MONTH);
                boolean waiting = cn.getTimeInMillis() < dl.getTimeInMillis();
                String hint;
                if (iss.contains("缺勤")) {
                    hint = "快去申请外出公干，审批完成后次日恢复正常。";
                } else if (waiting) {
                    hint = "若加班到 0 点后打卡，等待 " + showDay + " 日刷新考勤。";
                } else {
                    hint = "已超一天仍未更正，请核实后处理。";
                }
                hints.put(iss, hint);
            }
        }

        if (groups.isEmpty()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView tv = new TextView(this);
            tv.setText("异常提醒：无");
            tv.setTextSize(13f);
            tv.setTextColor(Color.parseColor("#2E7D32"));
            tv.setTypeface(null, Typeface.BOLD);
            row.addView(tv);
            tipBox.addView(row);
            return;
        }

        for (java.util.Map.Entry<String, ArrayList<Integer>> e : groups.entrySet()) {
            final String type = e.getKey();
            final ArrayList<Integer> days = e.getValue();
            final String hint = hints.get(type);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            if (tipBox.getChildCount() > 0) {
                row.setPadding(0, (int) dp(3), 0, 0);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("异常提醒：【").append(type).append("】");
            for (int i = 0; i < days.size(); i++) {
                if (i > 0) sb.append("，");
                sb.append(cmm).append("月").append(days.get(i)).append("日");
            }

            TextView tv = new TextView(this);
            tv.setText(sb.toString());
            tv.setTextSize(13f);
            tv.setTextColor(Color.parseColor("#D32F2F"));
            tv.setTypeface(null, Typeface.BOLD);
            tv.setMaxLines(2);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(tv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView dot = helpDot();
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isFinishing() || isDestroyed()) return;
                    new AlertDialog.Builder(HoursActivity.this)
                            .setTitle("【" + type + "】说明")
                            .setMessage(hint)
                            .setPositiveButton("知道了", null)
                            .show();
                }
            });
            row.addView(dot);
            tipBox.addView(row);
        }
    }

    /** 小问号按钮。 */
    private TextView helpDot() {
        TextView help = new TextView(this);
        help.setText("?");
        help.setTextSize(11f);
        help.setTextColor(Color.parseColor("#666666"));
        help.setGravity(Gravity.CENTER);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setShape(GradientDrawable.OVAL);
        cbg.setColor(Color.parseColor("#E0E0E0"));
        cbg.setStroke((int) dp(1), Color.parseColor("#BDBDBD"));
        help.setBackground(cbg);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                (int) dp(16), (int) dp(16));
        hlp.leftMargin = (int) dp(6);
        help.setLayoutParams(hlp);
        return help;
    }

    /** 工时核对算法说明。 */
    private void showCheckHelp() {
        if (isFinishing() || isDestroyed()) return;
        String msg = lastCheckDetail == null || lastCheckDetail.isEmpty()
                ? "按本月应出勤工作日 × 8 小时计算应完成总量，与实际记录工时相比得出差额。"
                : lastCheckDetail;
        new AlertDialog.Builder(this)
                .setTitle("工时核对说明")
                .setMessage(msg)
                .setPositiveButton("知道了", null)
                .show();
    }

    private int daysOf(int y, int m) {
        Calendar cal = Calendar.getInstance();
        cal.set(y, m - 1, 1);
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    /** 把某一年某月画进指定容器。 */
    private void renderInto(LinearLayout target, int y, int m) {
        target.removeAllViews();
        Calendar cal = Calendar.getInstance();
        cal.set(y, m - 1, 1);
        int firstDow = cal.get(Calendar.DAY_OF_WEEK);
        int offset = (firstDow + 5) % 7;
        int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        java.util.Set<String> holidays = Holidays.holidaySet(this, y);
        java.util.Set<String> makeups = Holidays.workdaySet(this, y);

        LinearLayout row = newRow();
        for (int i = 0; i < offset; i++) row.addView(emptyCell());
        for (int d = 1; d <= days; d++) {
            String date = String.format(Locale.CHINA, "%04d-%02d-%02d", y, m, d);
            Double h = Work.hoursOf(this, date);
            String inT = Store.getIn(this, date);
            String outT = Store.getOut(this, date);

            Calendar dc = Calendar.getInstance();
            dc.set(y, m - 1, d);
            int dow = dc.get(Calendar.DAY_OF_WEEK);
            boolean weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY;
            boolean rest = (weekend || holidays.contains(date)) && !makeups.contains(date);

            String issue = Store.getIssue(this, date);
            row.addView(cell(d, date, inT, outT, h, rest, issue));
            if ((offset + d) % 7 == 0 && d != days) {
                target.addView(row);
                row = newRow();
            }
        }
        while (row.getChildCount() < 7) row.addView(emptyCell());
        target.addView(row);
    }

    private LinearLayout newRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setMinimumHeight(0);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(CELL_H));
        rlp.setMargins(0, 0, 0, 0);
        r.setLayoutParams(rlp);
        r.setPadding(0, 0, 0, 0);
        return r;
    }

    private View emptyCell() {
        TextView t = new TextView(this);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, (int) dp(CELL_H), 1f));
        return t;
    }

    /** 日历单格高度（dp）。 */
    private static final float CELL_H = 60f;

    private View cell(int day, final String date, String inT, String outT, Double hours, boolean rest, String issue) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setLayoutParams(new LinearLayout.LayoutParams(0, (int) dp(60), 1f));

        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(0);
        boolean isNormal = issue != null && issue.contains("正常");
        boolean isBad = issue != null && !issue.isEmpty() && !isNormal;
        if (isBad) {
            g.setColor(Color.parseColor("#33FFA726"));
        } else if (isNormal) {
            g.setColor(Color.parseColor("#E8F5E9"));
        } else if (rest) {
            g.setColor(Color.parseColor("#33FF8FAB"));
        } else if (hours != null) {
            g.setColor(Color.parseColor("#E8F5E9"));
        } else {
            g.setColor(Color.TRANSPARENT);
        }
        box.setBackground(g);
        box.setPadding((int) dp(1), (int) dp(1), (int) dp(1), (int) dp(1));

        TextView d = new TextView(this);
        d.setIncludeFontPadding(false);
        d.setText(String.valueOf(day));
        d.setTextSize(14f);
        d.setTextColor(Color.parseColor("#111111"));
        d.setTypeface(null, Typeface.BOLD);
        d.setGravity(Gravity.CENTER);
        box.addView(d);

        TextView tIn = new TextView(this);
        tIn.setIncludeFontPadding(false);
        tIn.setText(Work.hhmm(inT).equals("--:--") ? "" : Work.hhmm(inT));
        tIn.setTextSize(7.5f);
        tIn.setTextColor(Color.parseColor("#2E7D32"));
        tIn.setGravity(Gravity.CENTER);
        box.addView(tIn);

        TextView tOut = new TextView(this);
        tOut.setIncludeFontPadding(false);
        tOut.setText(Work.hhmm(outT).equals("--:--") ? "" : Work.hhmm(outT));
        tOut.setTextSize(7.5f);
        tOut.setTextColor(Color.parseColor("#B26A00"));
        tOut.setGravity(Gravity.CENTER);
        box.addView(tOut);

        // 工时单独一行；没有记录用 — 占位，保持行高一致
        TextView h = new TextView(this);
        h.setIncludeFontPadding(false);
        h.setText(hours != null ? String.format(Locale.CHINA, "%.2f", hours) : "");
        h.setTextSize(9.5f);
        h.setTextColor(Color.parseColor("#1B5E20"));
        h.setTypeface(null, Typeface.BOLD);
        h.setGravity(Gravity.CENTER);
        box.addView(h);

        // 考勤状态：始终占一行，避免有无异常时行高不一致
        TextView mark = new TextView(this);
        mark.setIncludeFontPadding(false);
        mark.setText(issue == null || issue.isEmpty() ? "" : issue);
        mark.setTextSize(7f);
        mark.setTextColor(Color.parseColor(isBad ? "#D32F2F" : "#2E7D32"));
        mark.setTypeface(null, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        box.addView(mark);

        // 任何格子都能点：查看详情 + 修改当天工时
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDetail(date);
            }
        });
        return box;
    }

    private void paintKaoqinBtn(TextView t) {
        boolean on = Config.kaoqinOn(this);
        if (t != null) {
            t.setTextColor(Color.parseColor("#2E7D32"));
        }

    }

    /** 点异常提醒：直接打开 IPSA 登录页，登录成功后自动回来。 */
    private void toggleKaoqin(final TextView btn) {
        if (Config.kaoqinOn(this)) {
            Config.setKaoqin(this, false);
            paintKaoqinBtn(btn);
            Toast.makeText(this, "异常提醒已关闭", Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("异常提醒")
                .setMessage("异常提醒需要登录 IPSA 考勤系统。\n\n点「确定」后跳转到登录页，登录成功会自动返回。")
                .setNegativeButton("取消", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        
                    }
                })
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Config.setKaoqin(HoursActivity.this, true);
                        paintKaoqinBtn(btn);
                        Warden.reschedule(HoursActivity.this);
                        startActivity(new Intent(HoursActivity.this, KaoqinLoginActivity.class));
                    }
                })
                .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(android.content.DialogInterface d) {
                        
                    }
                })
                .show();
    }

    /** 弹一个短提示，1.5 秒后自动消失。 */
    /** 重新渲染日历（数据可能刚更新）。 */
    private void refreshCalendar() {
        try {
            recreate();
        } catch (Throwable ignored) {
        }
    }

    /** 点格子：显示当天打卡明细，可直接改当天工时。 */
    private void showDetail(final String date) {
        String in = Store.getIn(this, date);
        String out = Store.getOut(this, date);
        Double h = Work.hoursOf(this, date);
        StringBuilder sb = new StringBuilder();
        sb.append("上班  ").append(in == null || in.isEmpty() ? "-" : in).append('\n');
        sb.append("下班  ").append(out == null || out.isEmpty() ? "-" : out).append('\n');
        sb.append("工时  ").append(h == null ? "-" : String.format(Locale.CHINA, "%.2f 小时", h));
        new android.app.AlertDialog.Builder(this)
                .setTitle(date)
                .setMessage(sb.toString())
                .setNegativeButton("取消", null)
                .setPositiveButton("修改工时", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        openFillFor(date);
                    }
                })
                .show();
    }

    /** 跳到工时补录界面，并预设好这一天。 */
    private void openFillFor(String date) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        String[] p = date.split("-");
        try {
            cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
        } catch (Throwable ignored) {
        }
        // 切到该日期所在月份，再打开补录面板并预填日期
        year = cal.get(java.util.Calendar.YEAR);
        month = cal.get(java.util.Calendar.MONTH) + 1;
        render();
        showFillPicker(date);
    }

    /** 补录面板：滚轮选时间 + 异常提醒开关。 */
    /** 点小问号：显示节假日和工时说明。 */
    /** 登录IPSA：打开登录页，登录成功自动返回并抓取。 */
    private void openLogin() {
        new AlertDialog.Builder(this)
                .setTitle("登录 IPSA")
                .setMessage("将打开考勤系统登录页。\n登录成功后自动返回并同步考勤。")
                .setNegativeButton("取消", null)
                .setPositiveButton("去登录", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (isFinishing() || isDestroyed()) return;
                        startActivity(new Intent(HoursActivity.this, KaoqinLoginActivity.class));
                    }
                })
                .show();
    }

    /** 异常提醒开关：仅控制提醒，不弹登录页。 */
    private void setKaoqinOn(boolean on) {
        Config.setKaoqin(this, on);
        Warden.reschedule(this);
        Toast.makeText(this,
                on ? "异常提醒已开启" : "异常提醒已关闭",
                Toast.LENGTH_SHORT).show();
        render();
    }

    /** 同步失败：弹窗提醒重新登录。 */
    private void showLoginLost() {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("同步失败")
                .setMessage("需重新登录")
                .setNegativeButton("稍后", null)
                .setPositiveButton("去登录", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (isFinishing() || isDestroyed()) return;
                        startActivity(new Intent(HoursActivity.this, KaoqinLoginActivity.class));
                    }
                })
                .show();
    }

    private void checkLoginLost() {
        if (Warden.takeLoginLost(this)) {
            new android.os.Handler(getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    showLoginLost();
                }
            }, 500);
        }
    }

    /**
     * 工时核对：公司要求工作日日均 8 小时，本月总工时需达到「工作日数 × 8」。
     * 只统计到今天为止应完成的工作日，避免月初就显示巨额缺口。
     */
    private void updateCheck(double total, int y, int m, int daysInMonth) {
        if (checkView == null) return;
        try {
            java.util.Calendar now = java.util.Calendar.getInstance();
            int curY = now.get(java.util.Calendar.YEAR);
            int curM = now.get(java.util.Calendar.MONTH) + 1;
            int curD = now.get(java.util.Calendar.DAY_OF_MONTH);

            // 本月到目前为止应当出勤的工作日数（只算到当前日期）
            int lastDay = daysInMonth;
            if (y == curY && m == curM) {
                lastDay = curD;
            } else if (y > curY || (y == curY && m > curM)) {
                lastDay = 0;   // 未来的月份不核对
            }

            int workdays = 0;
            for (int d = 1; d <= lastDay; d++) {
                String date = String.format(Locale.CHINA, "%04d-%02d-%02d", y, m, d);
                if (CheckReceiver.isWorkday(this, date)) workdays++;
            }

            double need = workdays * 8.0;
            double diff = total - need;

            String sign = diff >= 0 ? "+" : "-";
            String color = diff >= 0 ? "#2E7D32" : "#C62828";
            String label = diff >= 0 ? "超出" : "还差";

            if (workdays == 0) {
                checkView.setTextColor(Color.parseColor("#888888"));
                checkView.setText("工时核对：本月还没有应出勤的工作日");
            } else {
                // 只体现差额：多了 +X.XXh，少了 -X.XXh
                checkView.setTextColor(Color.parseColor(color));
                checkView.setText(String.format(Locale.CHINA, "工时核对：%s%.2fh", sign, Math.abs(diff)));
                lastCheckDetail = String.format(Locale.CHINA,
                        "本月应出勤 %d 天 × 8 小时 = %.0f 小时\n"
                                + "已记录 %.2f 小时\n"
                                + "差额 %s%.2f 小时\n\n"
                                + "说明：只统计到今天为止应出勤的工作日，周末和法定节假日不计；"
                                + "调休补班日按工作日计。",
                        workdays, need, total, sign, Math.abs(diff));
            }
        } catch (Throwable t) {
            checkView.setText("");
        }
    }

    /** 异常提示折叠 / 展开。 */
    private void applyTipState() {
        if (tip == null || tipArrow == null) return;
        if (tipExpanded) {
            tip.setMaxLines(Integer.MAX_VALUE);
            tip.setEllipsize(null);
            tipArrow.setText("▴");
        } else {
            tip.setMaxLines(1);
            tip.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tipArrow.setText("▾");
        }
    }

    private void showCalendarHelp() {
        String h = Holidays.isFetched(this, year)
                ? "节假日：" + year + " 年已联网更新（含调休）"
                : "节假日：暂用内置固定日期，联网后会自动更新";
        String check = lastCheckDetail == null || lastCheckDetail.isEmpty()
                ? "工时核对：按本月应出勤工作日 × 8 小时计算差额"
                : lastCheckDetail;
        StringBuilder msg = new StringBuilder();
        msg.append(check);
        if (lastAbnormalDetail != null && !lastAbnormalDetail.isEmpty()) {
            msg.append("\n\n异常说明：\n").append(lastAbnormalDetail);
        }
        msg.append("\n\n").append(h);
        msg.append("\n\n工时已扣除 12:00–13:30、17:30–18:00 两段休息");
        new AlertDialog.Builder(this)
                .setTitle("说明")
                .setMessage(msg.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

    /** 右上角刷新：同步 IPSA 考勤。 */
    private void syncIpsa() {
        if (!Config.kaoqinOn(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("同步 IPSA 考勤")
                    .setMessage("开启异常提醒后才能同步。\n\n现在开启？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("开启", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            
                            toggleKaoqin(null);
                        }
                    })
                    .show();
            return;
        }
        Warden.check(this, false);
        Toast.makeText(this, "正在同步 IPSA 考勤...", Toast.LENGTH_SHORT).show();
        waitSyncResult();
    }

    /** 点同步后轮询结果，刷新日历并弹提示。 */
    private void waitSyncResult() {
        final long before = Warden.dataVersion(this);
        final android.os.Handler h = new android.os.Handler(getMainLooper());
        final long t0 = android.os.SystemClock.elapsedRealtime();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                // 1) 数据变了 => 成功
                if (Warden.dataVersion(HoursActivity.this) != before) {
                    Log.i("Daka", "HOURS sync ok");
                    render();
                    Toast.makeText(HoursActivity.this,
                            "考勤信息获取成功", Toast.LENGTH_LONG).show();
                    return;
                }
                // 2) 登录失效 => 立刻弹窗
                if (Warden.takeLoginLost(HoursActivity.this)) {
                    Log.i("Daka", "HOURS sync login lost");
                    loginLostShown = true;
                    showLoginLost();
                    return;
                }
                // 3) 超时
                long waited = android.os.SystemClock.elapsedRealtime() - t0;
                if (waited > 5000) {
                    Log.i("Daka", "HOURS sync timeout");
                    showLoginLost();
                    return;
                }
                h.postDelayed(this, 300);
            }
        }, 300);
    }

    private void showFillPicker() {
        showFillPicker(Store.logicalDate());
    }

    /** 打开补录面板；presetDate 不为空时预填该日期。 */
    private void showFillPicker(String presetDate) {
        showFillPicker(presetDate, presetDate != null);
    }

    private void showFillPicker(String presetDate, boolean lockDate) {
        final String[] date = {presetDate == null ? Store.logicalDate() : presetDate};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(16);
        box.setPadding(pad, (int) dp(8), pad, 0);

        // 日期：可点时有明显边框，锁定时灰底提示
        final TextView dateRow = new TextView(this);
        dateRow.setTextSize(16f);
        dateRow.setTypeface(null, Typeface.BOLD);
        dateRow.setGravity(Gravity.CENTER);
        dateRow.setPadding(0, (int) dp(10), 0, (int) dp(10));
        if (lockDate) {
            dateRow.setText("日期  " + date[0] + "   （已锁定）");
            dateRow.setTextColor(Color.parseColor("#888888"));
            GradientDrawable lbg = new GradientDrawable();
            lbg.setCornerRadius(dp(10));
            lbg.setColor(Color.parseColor("#F0F0F0"));
            dateRow.setBackground(lbg);
        } else {
            dateRow.setText("日期  " + date[0] + "   ▾ 点击更换");
            dateRow.setTextColor(Color.parseColor("#2E7D32"));
            GradientDrawable cbg2 = new GradientDrawable();
            cbg2.setCornerRadius(dp(10));
            cbg2.setColor(Color.parseColor("#E8F5E9"));
            cbg2.setStroke((int) dp(1.5f), Color.parseColor("#2E7D32"));
            dateRow.setBackground(cbg2);
            dateRow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pickDate(date, dateRow);
                }
            });
        }
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.bottomMargin = (int) dp(10);
        box.addView(dateRow, dlp);

        // 上下班滚轮
        final android.widget.NumberPicker inH = wheel(0, 23);
        final android.widget.NumberPicker inM = wheel(0, 59);
        final android.widget.NumberPicker outH = wheel(0, 23);
        final android.widget.NumberPicker outM = wheel(0, 59);

        // 整体补录：只填滚轮，不直接保存
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setPadding(0, (int) dp(4), 0, (int) dp(4));
        // 参数：名称, 是否改上班, 上班时, 上班分, 是否改下班, 下班时, 下班分
        String[][] presets = {
                {"补录上午", "1", "8", "0", "0", "0", "0"},
                {"补录下午", "0", "0", "0", "1", "17", "30"},
                {"补录全天", "1", "8", "0", "1", "17", "30"},
        };
        for (final String[] pr : presets) {
            TextView b = new TextView(this);
            b.setText(pr[0]);
            b.setTextSize(13f);
            b.setTextColor(Color.parseColor("#2E7D32"));
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, (int) dp(8), 0, (int) dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(10));
            bg.setColor(Color.parseColor("#E8F5E9"));
            b.setBackground(bg);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            blp.setMargins((int) dp(2), 0, (int) dp(2), 0);
            b.setLayoutParams(blp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if ("1".equals(pr[1])) {
                        inH.setValue(Integer.parseInt(pr[2]));
                        inM.setValue(Integer.parseInt(pr[3]));
                    }
                    if ("1".equals(pr[4])) {
                        outH.setValue(Integer.parseInt(pr[5]));
                        outM.setValue(Integer.parseInt(pr[6]));
                    }
                }
            });
            quick.addView(b);
        }
        box.addView(quick);

        box.addView(label("上班"));
        box.addView(wheelRow(inH, inM));
        box.addView(label("下班"));
        box.addView(wheelRow(outH, outM));

        applyWheels(date[0], inH, inM, outH, outM);

        // 点击监听已在上面按 lockDate 决定，这里不再重复设置

        new android.app.AlertDialog.Builder(this)
                .setTitle("工时补录")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        String iv = String.format(Locale.CHINA, "%02d:%02d:00",
                                inH.getValue(), inM.getValue());
                        String ov = String.format(Locale.CHINA, "%02d:%02d:00",
                                outH.getValue(), outM.getValue());
                        Store.putIn(HoursActivity.this, date[0], iv);
                        Store.putOut(HoursActivity.this, date[0], ov);
                        render();
                        Widgets.refreshAll(HoursActivity.this);
                        Archive.syncAsync(HoursActivity.this);
                        Toast.makeText(HoursActivity.this, "已保存 " + date[0],
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /** 补录面板里更换日期（只有「工时补录」入口可用）。 */
    private void pickDate(final String[] date, final TextView dateRow) {
        String[] p = date[0].split("-");
        new android.app.DatePickerDialog(this,
                new android.app.DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(android.widget.DatePicker dp2, int yy, int mm, int dd) {
                        date[0] = String.format(Locale.CHINA, "%04d-%02d-%02d", yy, mm + 1, dd);
                        dateRow.setText("日期  " + date[0] + "   ▾ 点击更换");
                    }
                }, Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1,
                Integer.parseInt(p[2])).show();
    }

    private android.widget.NumberPicker wheel(int min, int max) {
        android.widget.NumberPicker p = new android.widget.NumberPicker(this);
        p.setMinValue(min);
        p.setMaxValue(max);
        p.setFormatter(new android.widget.NumberPicker.Formatter() {
            @Override
            public String format(int i) {
                return String.format(Locale.CHINA, "%02d", i);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                (int) dp(110), 1f);
        p.setLayoutParams(lp);
        return p;
    }

    private LinearLayout wheelRow(android.widget.NumberPicker h,
                                  android.widget.NumberPicker m) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(h);
        TextView colon = new TextView(this);
        colon.setText(":");
        colon.setTextSize(22f);
        colon.setPadding((int) dp(4), 0, (int) dp(4), 0);
        row.addView(colon);
        row.addView(m);
        return row;
    }

    private TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13f);
        v.setTextColor(Color.parseColor("#777777"));
        v.setPadding(0, (int) dp(10), 0, 0);
        return v;
    }

    private void applyWheels(String date, android.widget.NumberPicker inH,
                             android.widget.NumberPicker inM,
                             android.widget.NumberPicker outH,
                             android.widget.NumberPicker outM) {
        int[] a = Work.split(Store.getIn(this, date), 9, 0);
        int[] b = Work.split(Store.getOut(this, date), 18, 0);
        inH.setValue(a[0]);
        inM.setValue(a[1]);
        outH.setValue(b[0]);
        outM.setValue(b[1]);
    }
}
