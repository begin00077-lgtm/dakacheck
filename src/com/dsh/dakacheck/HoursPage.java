package com.dsh.dakacheck;

import android.app.AlertDialog;
import android.content.Intent;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 工时日历页。 */
public class HoursPage extends BasePage {

    private int year, month;
    private LinearLayout checkRow;
    private LinearLayout tipBox;
    private TextView tipArrow;
    private TextView tip;
    private String lastAbnormalDetail = "";
    private String lastCheckDetail = "";
    private LinearLayout grid;
    private LinearLayout gridAdj;
    private int neighborDelta = 0;
    private int neighborDelta2 = 0;

    /** 登录完成后的刷新。 */
    private final android.content.BroadcastReceiver dataReadyReceiver =
            new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context c, android.content.Intent i) {
                    android.util.Log.i("Daka", "HOURS data ready -> render now");
                    if (monthTitle == null) return;
                    monthTitle.post(new Runnable() {
                        @Override
                        public void run() {
                            render();
                        }
                    });
                }
            };
    private java.util.List<String[]> dailyRows = new java.util.ArrayList<String[]>();
    private final android.os.Handler watchHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private TextView monthTitle, summary, tipView;
    private TextView checkView;
    private boolean tipExpanded = false;
    private FrameLayout pager;
    private long lastVer = 0;
    private boolean loginLostShown = false;
    private static final float CELL_H = 60f;
    /** 工时核对明细的固定行高（dp）。 */
    private static final float ROW_H = 30f;

    public HoursPage(android.app.Activity host) {
        super(host);
    }

    private boolean inited = false;

    private void initOnce() {
        if (inited) return;
        inited = true;
        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH) + 1;
    }

    @Override
    public void onShown() {
        if (monthTitle == null) return;
        monthTitle.post(new Runnable() {
            @Override
            public void run() {
                render();
            }
        });
    }

    protected void startWatcher() {
        lastVer = Warden.dataVersion(ctx);
        watchHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    long now = Warden.dataVersion(ctx);
                    if (now != lastVer) {
                        lastVer = now;
                        Log.i("Daka", "HOURS data changed -> render");
                        render();
                    }
                    if (host.isFinishing() || host.isDestroyed()) {
                        return;
                    }
                    if (!loginLostShown && Warden.takeLoginLost(ctx)) {
                        loginLostShown = true;
                        Log.i("Daka", "HOURS login lost -> dialog");
                        showLoginLost();
                    }
                } catch (Throwable ignored) {
                }
                watchHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkLoginLost();
            }
        }, 500);
            }
        }, 500);
    }

    protected android.graphics.drawable.GradientDrawable btnBg(float radius, String fill, String stroke) {
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(radius);
        g.setColor(Color.parseColor(fill));
        g.setStroke((int) dp(1), Color.parseColor(stroke));
        return g;
    }


    @Override
    public View build() {
        initOnce();
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(16);
        root.setPadding(pad, (int) dp(18), pad, (int) dp(24));
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        LinearLayout monthBar = new LinearLayout(ctx);
        monthBar.setOrientation(LinearLayout.HORIZONTAL);
        monthBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView prev = new TextView(ctx);
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

        monthTitle = new TextView(ctx);
        monthTitle.setTextSize(18f);
        monthTitle.setTextColor(Color.parseColor("#111111"));
        monthTitle.setTypeface(null, Typeface.BOLD);
        monthTitle.setGravity(Gravity.CENTER);
        monthBar.addView(monthTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView next = new TextView(ctx);
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

        LinearLayout week = new LinearLayout(ctx);
        week.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = (int) dp(6);
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        for (String n : names) {
            TextView t = new TextView(ctx);
            t.setText(n);
            t.setTextSize(13f);
            t.setTextColor(Color.parseColor("#777777"));
            t.setGravity(Gravity.CENTER);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            week.addView(t);
        }
        root.addView(week, wlp);

        pager = new FrameLayout(ctx) {
            private float downX, downY;
            private boolean monthSwipe;
            private int dir = 0;

            @Override
            public boolean dispatchTouchEvent(android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downX = e.getX();
                        downY = e.getY();
                        monthSwipe = false;
                        dir = 0;
                        break;
                    case android.view.MotionEvent.ACTION_MOVE: {
                        float dx = e.getX() - downX;
                        float dy = e.getY() - downY;
                        if (!monthSwipe && Math.abs(dx) > 20 && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                            monthSwipe = true;
                        }
                        if (monthSwipe) {
                            // 跟手：当前月跟着手指移动，相邻月从一侧补位进来
                            int w = Math.max(1, pager.getWidth());
                            int d = dx < 0 ? 1 : -1;
                            if (d != dir) {
                                dir = d;
                                prepareNeighbor(d);
                            }
                            grid.setTranslationX(dx);
                            gridAdj.setTranslationX(dx + d * w);
                            return true;
                        }
                        break;
                    }
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL: {
                        if (monthSwipe) {
                            float dx = e.getX() - downX;
                            int w = Math.max(1, pager.getWidth());
                            if (dir != 0 && Math.abs(dx) > w * 0.2f) {
                                commitSwipe(dir, w);
                            } else {
                                cancelSwipe(dir, w);
                            }
                            monthSwipe = false;
                            dir = 0;
                            return true;
                        }
                        break;
                    }
                }
                return super.dispatchTouchEvent(e);
            }
        };
        pager.setTag("cal_area");   // 日历区域滑动=翻月，不切页面
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = (int) dp(6);
        root.addView(pager, plp);

        grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pager.addView(grid);

        gridAdj = new LinearLayout(ctx);
        gridAdj.setOrientation(LinearLayout.VERTICAL);
        gridAdj.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        gridAdj.setVisibility(View.INVISIBLE);
        pager.addView(gridAdj);

        // 工时补录 + 异常提醒（从顶部移到累计上方）
        LinearLayout tools = new LinearLayout(ctx);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toolsLp.topMargin = (int) dp(10);
        tools.setLayoutParams(toolsLp);

        TextView fillBtn2 = new TextView(ctx);
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

        View toolSpacer = new View(ctx);
        tools.addView(toolSpacer, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView loginBtn = new TextView(ctx);
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

        View toolSpacer2 = new View(ctx);
        tools.addView(toolSpacer2, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 异常提醒永久开启，这里不再放开关；原位置改为 IPSA 同步
        TextView syncBtn = new TextView(ctx);
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
        LinearLayout sumRow = new LinearLayout(ctx);
        sumRow.setOrientation(LinearLayout.HORIZONTAL);
        sumRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = (int) dp(10);

        summary = new TextView(ctx);
        summary.setTextSize(15f);
        summary.setTextColor(Color.parseColor("#111111"));
        summary.setTypeface(null, Typeface.BOLD);
        sumRow.addView(summary);

        TextView help = new TextView(ctx);
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
        checkRow = new LinearLayout(ctx);
        checkRow.setOrientation(LinearLayout.HORIZONTAL);
        checkRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams crl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        crl.topMargin = (int) dp(8);

        checkView = new TextView(ctx);
        checkView.setTextSize(14f);
        checkView.setTypeface(null, Typeface.BOLD);
        checkRow.addView(checkView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView checkHelp = helpDot(null);
        checkHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCheckHelp();
            }
        });
        checkRow.addView(checkHelp);
        root.addView(checkRow, crl);

        // 异常提醒：每类一行，行尾带 ?
        tipBox = new LinearLayout(ctx);
        tipBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = (int) dp(6);
        tipBox.setLayoutParams(tlp);
        root.addView(tipBox);

        // 兼容旧字段：正文不再单独显示，统一由 tipBox 生成
        tip = new TextView(ctx);
        tipView = tip;
        tip.setVisibility(View.GONE);

        tipArrow = new TextView(ctx);
        tipArrow.setVisibility(View.GONE);


        scroll.addView(root);

        // 布局完成后才渲染日历（否则宽度为 0，格子画不出来）
        final View result = scroll;
        result.post(new Runnable() {
            @Override
            public void run() {
                render();
                startWatcher();
            }
        });

        // 登录完成后立即刷新，不需要切页面
        try {
            android.content.IntentFilter f =
                    new android.content.IntentFilter(KaoqinLoginActivity.ACTION_DATA_READY);
            host.registerReceiver(dataReadyReceiver, f,
                    android.content.Context.RECEIVER_NOT_EXPORTED);
        } catch (Throwable ignored) {
        }
        return result;
    }

    protected void prepareNeighbor(int delta) {
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

    protected void commitSwipe(final int delta, int w) {
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

    protected void cancelSwipe(int delta, int w) {
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

    protected void shiftMonth(int delta) {
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

    protected void render() {
        monthTitle.setText(year + " 年 " + month + " 月");
        renderInto(grid, year, month);

        double total = 0;
        int counted = 0;
        int days = daysOf(year, month);
        for (int d = 1; d <= days; d++) {
            String date = String.format(Locale.CHINA, "%04d-%02d-%02d", year, month, d);
            Double h = Work.hoursOf(ctx, date);
            if (h != null) {
                total += h;
                counted++;
            }
        }
        summary.setText(String.format(Locale.CHINA, "%d 月累计：%.2f 小时　（已统计 %d 天）", month, total, counted));
        updateCheck(total, year, month, days);
        rebuildAbnormal();
    }

    protected void rebuildAbnormal() {
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
            String iss = Store.getIssue(ctx, dt);
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
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView tv = new TextView(ctx);
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

            LinearLayout row = new LinearLayout(ctx);
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

            TextView tv = new TextView(ctx);
            tv.setText(sb.toString());
            tv.setTextSize(13f);
            tv.setTextColor(Color.parseColor("#D32F2F"));
            tv.setTypeface(null, Typeface.BOLD);
            tv.setMaxLines(2);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(tv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView dot = helpDot(null);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (host.isFinishing() || host.isDestroyed()) return;
                    new AlertDialog.Builder(ctx)
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


    protected void showCheckHelp() {
        if (host.isFinishing() || host.isDestroyed()) return;

        // 外层：汇总 + 表头固定，只有明细行滚动
        LinearLayout outer = new LinearLayout(ctx);
        outer.setOrientation(LinearLayout.VERTICAL);
        int pd = (int) dp(12);
        outer.setPadding(pd, pd, pd, 0);

        TextView head = new TextView(ctx);
        head.setText(lastCheckDetail == null || lastCheckDetail.isEmpty()
                ? "按本月应出勤工作日 × 8 小时计算"
                : lastCheckDetail);
        head.setTextSize(12.5f);
        head.setTextColor(Color.parseColor("#444444"));
        head.setPadding(0, 0, 0, (int) dp(8));
        outer.addView(head);

        if (dailyRows == null || dailyRows.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("本月还没有应出勤的工作日");
            empty.setTextSize(13f);
            empty.setTextColor(Color.parseColor("#888888"));
            outer.addView(empty);
        } else {
            // 表头：不随内容滚动
            String[] titles = {"日期", "工时", "当日差额", "累计差额"};
            outer.addView(tableRow(titles, null, true));

            LinearLayout list = new LinearLayout(ctx);
            list.setOrientation(LinearLayout.VERTICAL);
            for (String[] r : dailyRows) {
                list.addView(tableRow(new String[]{r[0], r[1], r[2], r[3]}, r, false));
            }

            // 固定 15 行高：不同月份弹窗大小不变
            ScrollView sv = new ScrollView(ctx);
            sv.setVerticalScrollBarEnabled(true);
            sv.setScrollbarFadingEnabled(false);          // 滚动条常驻
            sv.setScrollBarStyle(android.view.View.SCROLLBARS_INSIDE_INSET);
            sv.setScrollBarSize((int) dp(6));
            sv.setPadding(0, 0, (int) dp(8), 0);   // 给滚动条留位置
            sv.addView(list);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(ROW_H * 15));
            outer.addView(sv, slp);

            // 底部小字：共多少天
            TextView foot = new TextView(ctx);
            foot.setText(String.format(Locale.CHINA, "共 %d 个工作日", dailyRows.size()));
            foot.setTextSize(11.5f);
            foot.setTextColor(Color.parseColor("#999999"));
            foot.setGravity(Gravity.CENTER);
            foot.setPadding(0, (int) dp(6), 0, (int) dp(2));
            outer.addView(foot);
        }

        new AlertDialog.Builder(ctx)
                .setTitle("工时核对明细")
                .setView(outer)
                .setPositiveButton("知道了", null)
                .show();
    }

    /** 一行四列，每列居中；数值列按自身正负着色。 */
    private View tableRow(String[] cells, String[] colors, boolean isHeader) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // 行高固定，保证 15 行总高恒定
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(isHeader ? ROW_H + 4 : ROW_H)));
        if (isHeader) {
            GradientDrawable hb = new GradientDrawable();
            hb.setColor(Color.parseColor("#F0F0F0"));
            row.setBackground(hb);
        }

        for (int i = 0; i < cells.length; i++) {
            TextView c = new TextView(ctx);
            c.setText(cells[i]);
            c.setGravity(Gravity.CENTER);
            c.setTextSize(isHeader ? 12f : 13f);
            c.setIncludeFontPadding(false);
            c.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            if (isHeader) {
                c.setTextColor(Color.parseColor("#666666"));
                c.setTypeface(null, Typeface.BOLD);
            } else if (i >= 2 && colors != null) {
                // 第 3 列用 colors[4]，第 4 列用 colors[5]，各自独立
                String hex = i == 2 ? colors[4] : colors[5];
                c.setTextColor(Color.parseColor(hex));
            } else {
                c.setTextColor(Color.parseColor("#222222"));
            }
            row.addView(c);
        }
        return row;
    }

    protected int daysOf(int y, int m) {
        Calendar cal = Calendar.getInstance();
        cal.set(y, m - 1, 1);
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    protected void renderInto(LinearLayout target, int y, int m) {
        target.removeAllViews();
        Calendar cal = Calendar.getInstance();
        cal.set(y, m - 1, 1);
        int firstDow = cal.get(Calendar.DAY_OF_WEEK);
        int offset = (firstDow + 5) % 7;
        int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        java.util.Set<String> holidays = Holidays.holidaySet(ctx, y);
        java.util.Set<String> makeups = Holidays.workdaySet(ctx, y);

        LinearLayout row = newRow();
        for (int i = 0; i < offset; i++) row.addView(emptyCell());
        for (int d = 1; d <= days; d++) {
            String date = String.format(Locale.CHINA, "%04d-%02d-%02d", y, m, d);
            Double h = Work.hoursOf(ctx, date);
            String inT = Store.getIn(ctx, date);
            String outT = Store.getOut(ctx, date);

            Calendar dc = Calendar.getInstance();
            dc.set(y, m - 1, d);
            int dow = dc.get(Calendar.DAY_OF_WEEK);
            boolean weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY;
            boolean rest = (weekend || holidays.contains(date)) && !makeups.contains(date);

            String issue = Store.getIssue(ctx, date);
            row.addView(cell(d, date, inT, outT, h, rest, issue));
            if ((offset + d) % 7 == 0 && d != days) {
                target.addView(row);
                row = newRow();
            }
        }
        while (row.getChildCount() < 7) row.addView(emptyCell());
        target.addView(row);
    }

    protected LinearLayout newRow() {
        LinearLayout r = new LinearLayout(ctx);
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

    protected View emptyCell() {
        TextView t = new TextView(ctx);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, (int) dp(CELL_H), 1f));
        return t;
    }

    protected View cell(int day, final String date, String inT, String outT, Double hours, boolean rest, String issue) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setLayoutParams(new LinearLayout.LayoutParams(0, (int) dp(60), 1f));

        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(0);
        // 绿色状态：正常、请假/出勤类、出差类都不算异常
        boolean isNormal = issue != null && issue.contains("正常");
        boolean isGreen = isNormal || (issue != null
                && com.dsh.dakacheck.KaoqinParser.greenTag(issue) != null);
        boolean isBad = issue != null && !issue.isEmpty() && !isGreen;
        if (isBad) {
            g.setColor(Color.parseColor("#33FFA726"));
        } else if (isGreen) {
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

        TextView d = new TextView(ctx);
        d.setIncludeFontPadding(false);
        d.setText(String.valueOf(day));
        d.setTextSize(14f);
        d.setTextColor(Color.parseColor("#111111"));
        d.setTypeface(null, Typeface.BOLD);
        d.setGravity(Gravity.CENTER);
        box.addView(d);

        TextView tIn = new TextView(ctx);
        tIn.setIncludeFontPadding(false);
        tIn.setText(Work.hhmm(inT).equals("--:--") ? "" : Work.hhmm(inT));
        tIn.setTextSize(7.5f);
        tIn.setTextColor(Color.parseColor("#2E7D32"));
        tIn.setGravity(Gravity.CENTER);
        box.addView(tIn);

        TextView tOut = new TextView(ctx);
        tOut.setIncludeFontPadding(false);
        tOut.setText(Work.hhmm(outT).equals("--:--") ? "" : Work.hhmm(outT));
        tOut.setTextSize(7.5f);
        tOut.setTextColor(Color.parseColor("#B26A00"));
        tOut.setGravity(Gravity.CENTER);
        box.addView(tOut);

        // 工时单独一行；没有记录用 — 占位，保持行高一致
        TextView h = new TextView(ctx);
        h.setIncludeFontPadding(false);
        h.setText(hours != null ? String.format(Locale.CHINA, "%.2f", hours) : "");
        h.setTextSize(9.5f);
        h.setTextColor(Color.parseColor("#1B5E20"));
        h.setTypeface(null, Typeface.BOLD);
        h.setGravity(Gravity.CENTER);
        box.addView(h);

        // 考勤状态：始终占一行，避免有无异常时行高不一致
        TextView mark = new TextView(ctx);
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

    protected void paintKaoqinBtn(TextView t) {
        boolean on = Config.kaoqinOn(ctx);
        if (t != null) {
            t.setTextColor(Color.parseColor("#2E7D32"));
        }

    }

    protected void toggleKaoqin(final TextView btn) {
        if (Config.kaoqinOn(ctx)) {
            Config.setKaoqin(ctx, false);
            paintKaoqinBtn(btn);
            Toast.makeText(ctx, "异常提醒已关闭", Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(host)
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
                        Config.setKaoqin(ctx, true);
                        paintKaoqinBtn(btn);
                        Warden.reschedule(ctx);
                        host.startActivity(new Intent(ctx, KaoqinLoginActivity.class));
                    }
                })
                .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(android.content.DialogInterface d) {
                        
                    }
                })
                .show();
    }

    protected void refreshCalendar() {
        try {
            host.recreate();
        } catch (Throwable ignored) {
        }
    }

    protected void showDetail(final String date) {
        String in = Store.getIn(ctx, date);
        String out = Store.getOut(ctx, date);
        Double h = Work.hoursOf(ctx, date);
        StringBuilder sb = new StringBuilder();
        sb.append("上班  ").append(in == null || in.isEmpty() ? "-" : in).append('\n');
        sb.append("下班  ").append(out == null || out.isEmpty() ? "-" : out).append('\n');
        sb.append("工时  ").append(h == null ? "-" : String.format(Locale.CHINA, "%.2f 小时", h));
        new android.app.AlertDialog.Builder(host)
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

    protected void openFillFor(String date) {
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
        showFillPickerLocked(date);
    }

    protected void openLogin() {
        new AlertDialog.Builder(ctx)
                .setTitle("登录 IPSA")
                .setMessage("将打开考勤系统登录页。\n登录成功后自动返回并同步考勤。")
                .setNegativeButton("取消", null)
                .setPositiveButton("去登录", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (host.isFinishing() || host.isDestroyed()) return;
                        host.startActivity(new Intent(ctx, KaoqinLoginActivity.class));
                    }
                })
                .show();
    }

    protected void setKaoqinOn(boolean on) {
        Config.setKaoqin(ctx, on);
        Warden.reschedule(ctx);
        Toast.makeText(ctx,
                on ? "异常提醒已开启" : "异常提醒已关闭",
                Toast.LENGTH_SHORT).show();
        render();
    }

    protected void showLoginLost() {
        if (host.isFinishing() || host.isDestroyed()) return;
        new AlertDialog.Builder(ctx)
                .setTitle("同步失败")
                .setMessage("需重新登录")
                .setNegativeButton("稍后", null)
                .setPositiveButton("去登录", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (host.isFinishing() || host.isDestroyed()) return;
                        host.startActivity(new Intent(ctx, KaoqinLoginActivity.class));
                    }
                })
                .show();
    }

    protected void checkLoginLost() {
        if (Warden.takeLoginLost(ctx)) {
            new android.os.Handler(host.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    showLoginLost();
                }
            }, 500);
        }
    }

    protected void updateCheck(double total, int y, int m, int daysInMonth) {
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
            java.util.List<String[]> rows = new java.util.ArrayList<String[]>();
            double running = 0;
            for (int d = 1; d <= lastDay; d++) {
                String date = String.format(Locale.CHINA, "%04d-%02d-%02d", y, m, d);
                if (!CheckReceiver.isWorkday(ctx, date)) continue;
                workdays++;
                Double hh = Work.hoursOf(ctx, date);
                double got = hh == null ? 0.0 : hh;
                double dd = got - 8.0;
                running += dd;
                rows.add(new String[]{
                        String.format(Locale.CHINA, "%d日", d),
                        String.format(Locale.CHINA, "%.2f", got),
                        (dd >= 0 ? "+" : "-") + String.format(Locale.CHINA, "%.2f", Math.abs(dd)),
                        (running >= 0 ? "+" : "-") + String.format(Locale.CHINA, "%.2f", Math.abs(running)),
                        dd >= 0 ? "#2E7D32" : "#C62828",
                        running >= 0 ? "#2E7D32" : "#C62828"
                });
            }
            dailyRows = rows;

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
                                + "总差额 %s%.2f 小时",
                        workdays, need, total, sign, Math.abs(diff));
            }
        } catch (Throwable t) {
            checkView.setText("");
        }
    }

    protected void applyTipState() {
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

    protected void showCalendarHelp() {
        String h = Holidays.isFetched(ctx, year)
                ? "节假日：" + year + " 年已联网更新（含调休）"
                : "节假日：暂用内置固定日期，联网后会自动更新";
        StringBuilder msg = new StringBuilder();
        msg.append("月累计：按本月已记录的每天工时相加，只统计有打卡记录的天。");
        if (lastAbnormalDetail != null && !lastAbnormalDetail.isEmpty()) {
            msg.append("\n\n异常说明：\n").append(lastAbnormalDetail);
        }
        msg.append("\n\n").append(h);
        msg.append("\n\n工时已扣除 12:00–13:30、17:30–18:00 两段休息");
        new AlertDialog.Builder(ctx)
                .setTitle("说明")
                .setMessage(msg.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

    protected void syncIpsa() {
        if (!Config.kaoqinOn(ctx)) {
            new AlertDialog.Builder(ctx)
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
        Warden.check(ctx, false);
        Toast.makeText(ctx, "正在同步 IPSA 考勤...", Toast.LENGTH_SHORT).show();
        waitSyncResult();
    }

    protected void waitSyncResult() {
        final long before = Warden.dataVersion(ctx);
        final android.os.Handler h = new android.os.Handler(host.getMainLooper());
        final long t0 = android.os.SystemClock.elapsedRealtime();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                // 1) 数据变了 => 成功
                if (Warden.dataVersion(ctx) != before) {
                    Log.i("Daka", "HOURS sync ok");
                    render();
                    Toast.makeText(ctx,
                            "考勤信息获取成功", Toast.LENGTH_LONG).show();
                    return;
                }
                // 2) 登录失效 => 立刻弹窗
                if (Warden.takeLoginLost(ctx)) {
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
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        waitSyncResult();
                    }
                }, 300);
            }
        }, 300);
    }

    /** 「工时补录」按钮进来：日期默认今天，但可以自由选择。 */
    protected void showFillPicker() {
        showFillPicker(Store.logicalDate(), false);
    }

    /** 日历格子进来：日期已定，不允许改。 */
    protected void showFillPickerLocked(String presetDate) {
        showFillPicker(presetDate, true);
    }

    protected void showFillPicker(String presetDate) {
        showFillPicker(presetDate, presetDate != null);
    }

    protected void showFillPicker(String presetDate, boolean lockDate) {
        final String[] date = {presetDate == null ? Store.logicalDate() : presetDate};

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(16);
        box.setPadding(pad, (int) dp(8), pad, 0);

        // 日期：可点时有明显边框，锁定时灰底提示
        final TextView dateRow = new TextView(ctx);
        dateRow.setTextSize(16f);
        dateRow.setTypeface(null, Typeface.BOLD);
        dateRow.setGravity(Gravity.CENTER);
        dateRow.setPadding(0, (int) dp(10), 0, (int) dp(10));
        if (lockDate) {
            dateRow.setText("日期  " + date[0]);
            dateRow.setTextColor(Color.parseColor("#111111"));
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
        LinearLayout quick = new LinearLayout(ctx);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setPadding(0, (int) dp(4), 0, (int) dp(4));
        // 参数：名称, 是否改上班, 上班时, 上班分, 是否改下班, 下班时, 下班分
        String[][] presets = {
                {"补录上午", "1", "8", "0", "0", "0", "0"},
                {"补录下午", "0", "0", "0", "1", "17", "30"},
                {"补录全天", "1", "8", "0", "1", "17", "30"},
        };
        for (final String[] pr : presets) {
            TextView b = new TextView(ctx);
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

        new android.app.AlertDialog.Builder(host)
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
                        Store.putIn(ctx, date[0], iv);
                        Store.putOut(ctx, date[0], ov);
                        render();
                        Widgets.refreshAll(ctx);
                        Archive.syncAsync(ctx);
                        Toast.makeText(ctx, "已保存 " + date[0],
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    protected void pickDate(final String[] date, final TextView dateRow) {
        String[] p = date[0].split("-");
        new android.app.DatePickerDialog(host,
                new android.app.DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(android.widget.DatePicker dp2, int yy, int mm, int dd) {
                        date[0] = String.format(Locale.CHINA, "%04d-%02d-%02d", yy, mm + 1, dd);
                        dateRow.setText("日期  " + date[0] + "   ▾ 点击更换");
                    }
                }, Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1,
                Integer.parseInt(p[2])).show();
    }

    protected android.widget.NumberPicker wheel(int min, int max) {
        android.widget.NumberPicker p = new android.widget.NumberPicker(ctx);
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

    protected LinearLayout wheelRow(android.widget.NumberPicker h,
                                  android.widget.NumberPicker m) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(h);
        TextView colon = new TextView(ctx);
        colon.setText(":");
        colon.setTextSize(22f);
        colon.setPadding((int) dp(4), 0, (int) dp(4), 0);
        row.addView(colon);
        row.addView(m);
        return row;
    }

    protected TextView label(String t) {
        TextView v = new TextView(ctx);
        v.setText(t);
        v.setTextSize(13f);
        v.setTextColor(Color.parseColor("#777777"));
        v.setPadding(0, (int) dp(10), 0, 0);
        return v;
    }

    protected void applyWheels(String date, android.widget.NumberPicker inH,
                             android.widget.NumberPicker inM,
                             android.widget.NumberPicker outH,
                             android.widget.NumberPicker outM) {
        int[] a = Work.split(Store.getIn(ctx, date), 9, 0);
        int[] b = Work.split(Store.getOut(ctx, date), 18, 0);
        inH.setValue(a[0]);
        inM.setValue(a[1]);
        outH.setValue(b[0]);
        outM.setValue(b[1]);
    }
}
