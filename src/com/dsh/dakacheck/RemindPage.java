package com.dsh.dakacheck;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.Locale;

/**
 * 提醒设置页：核对时间、开关、说明。
 *
 * 只负责构建 View，由 HomeActivity 放进分页容器里；自身不依赖 Activity 生命周期。
 */
public final class RemindPage extends BasePage {

    private Button inBtn;
    private Button outBtn;
    private Switch enableSwitch;

    public RemindPage(Activity host) {
        super(host);
    }

    @Override
    public View build() {
        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setScrollbarFadingEnabled(true);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(16);
        root.setPadding(pad, (int) dp(12), pad, (int) dp(16));
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // 总开关
        LinearLayout swRow = new LinearLayout(ctx);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        swRow.setBackgroundColor(Color.WHITE);
        swRow.setPadding((int) dp(16), (int) dp(14), (int) dp(16), (int) dp(14));

        LinearLayout swText = new LinearLayout(ctx);
        swText.setOrientation(LinearLayout.VERTICAL);

        TextView swTitle = new TextView(ctx);
        swTitle.setText("打卡提醒");
        swTitle.setTextSize(16f);
        swTitle.setTextColor(Color.parseColor("#111111"));

        TextView swDesc = new TextView(ctx);
        swDesc.setText("每天按下面的时间核对是否已打卡");
        swDesc.setTextSize(12f);
        swDesc.setTextColor(Color.parseColor("#888888"));
        swDesc.setPadding(0, (int) dp(3), 0, 0);

        swText.addView(swTitle);
        swText.addView(swDesc);
        swRow.addView(swText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        enableSwitch = new Switch(ctx);
        swRow.addView(enableSwitch);
        root.addView(swRow, matchWrap((int) dp(10)));

        // 说明
        root.addView(section("说明"));

        TextView hint = new TextView(ctx);
        hint.setText("• 考勤日按 04:00 划分：0–4 点之间页面显示的是前一天的考勤，其余时间查当天。\n"
                + "• 签退在次日凌晨核对，方便加班后补打卡。\n"
                + "• 周末和节假日不核对，调休补班日算工作日。\n"
                + "• 异常提醒永久开启，考勤同步每次都会自动执行。");
        hint.setTextSize(13.5f);
        hint.setTextColor(Color.parseColor("#444444"));
        hint.setLineSpacing(dp(4), 1f);
        hint.setPadding((int) dp(4), (int) dp(8), (int) dp(4), 0);
        root.addView(hint, matchWrap(0));

        // 核对时间
        root.addView(section("核对时间"));

        inBtn = new Button(ctx);
        inBtn.setAllCaps(false);
        inBtn.setTextSize(16f);
        inBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pick(true);
            }
        });
        root.addView(inBtn, matchWrap((int) dp(4)));

        outBtn = new Button(ctx);
        outBtn.setAllCaps(false);
        outBtn.setTextSize(16f);
        outBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pick(false);
            }
        });
        root.addView(outBtn, matchWrap((int) dp(4)));

        scroll.setFillViewport(true);
        scroll.addView(root);
        refresh();
        return scroll;
    }

    /** 页面切到时刷新一次，保证显示最新配置。 */
    @Override
    public void onShown() {
        refresh();
    }

    /** 上下滚轮选时间：签退不得早于签到。 */
    private void pick(final boolean isIn) {
        final int curH = isIn ? Config.inHour(ctx) : Config.outHour(ctx);
        final int curM = isIn ? Config.inMin(ctx) : Config.outMin(ctx);

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pd = (int) dpx(16);
        box.setPadding(pd, (int) dpx(8), pd, 0);

        TextView title = new TextView(ctx);
        title.setText(isIn ? "签到核对时间" : "签退核对时间");
        title.setTextSize(15f);
        title.setTextColor(Color.parseColor("#2E7D32"));
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, (int) dpx(6));
        box.addView(title);

        final TextView tip = new TextView(ctx);
        tip.setTextSize(12.5f);
        tip.setTextColor(Color.parseColor("#C62828"));
        tip.setPadding(0, 0, 0, (int) dpx(6));
        tip.setMinHeight((int) dpx(18));
        box.addView(tip);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        final android.widget.NumberPicker hh = wheel(0, 23);
        final android.widget.NumberPicker mm = wheel(0, 59);
        hh.setValue(curH);
        mm.setValue(curM);
        row.addView(hh);
        TextView colon = new TextView(ctx);
        colon.setText(":");
        colon.setTextSize(22f);
        colon.setPadding((int) dpx(4), 0, (int) dpx(4), 0);
        row.addView(colon);
        row.addView(mm);
        box.addView(row);



        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(host)
                .setTitle(isIn ? "设置签到时间" : "设置签退时间")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();

        dlg.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                int h = hh.getValue(), m = mm.getValue();
                                int minutes = h * 60 + m;
                                int inMinutes = Config.inHour(ctx) * 60 + Config.inMin(ctx);
                                int outMinutes = Config.outHour(ctx) * 60 + Config.outMin(ctx);

                                // 考勤周期 04:00 -> 次日 04:00：签退必须在同一周期内且晚于签到
                                if (!isIn) {
                                    int inAbs = cycleMinutes(Config.inHour(ctx), Config.inMin(ctx));
                                    int outAbs = cycleMinutes(h, m);
                                    if (outAbs <= inAbs) {
                                        tip.setText(String.format(Locale.CHINA,
                                                "签退需在签到（%02d:%02d）之后，且在同一考勤周期内（04:00 为分界）",
                                                Config.inHour(ctx), Config.inMin(ctx)));
                                        return;
                                    }
                                    Config.setOutHour(ctx, h);
                                    Config.setOutMin(ctx, m);
                                } else {
                                    int inAbs = cycleMinutes(h, m);
                                    int outAbs = cycleMinutes(Config.outHour(ctx), Config.outMin(ctx));
                                    if (inAbs >= outAbs) {
                                        tip.setText(String.format(Locale.CHINA,
                                                "签到需在签退（%02d:%02d）之前，且在同一考勤周期内（04:00 为分界）",
                                                Config.outHour(ctx), Config.outMin(ctx)));
                                        return;
                                    }
                                    Config.setInHour(ctx, h);
                                    Config.setInMin(ctx, m);
                                }
                                Scheduler.ensure(ctx);
                                refresh();
                                dlg.dismiss();
                                Toast.makeText(ctx, "已更新", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });
        dlg.show();
    }

    private android.widget.NumberPicker wheel(int min, int max) {
        android.widget.NumberPicker p = new android.widget.NumberPicker(ctx);
        p.setMinValue(min);
        p.setMaxValue(max);
        p.setFormatter(new android.widget.NumberPicker.Formatter() {
            @Override
            public String format(int i) {
                return String.format(Locale.CHINA, "%02d", i);
            }
        });
        p.setLayoutParams(new LinearLayout.LayoutParams(0, (int) dpx(110), 1f));
        // 只允许上下滑动选择，不弹键盘
        p.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        p.setFocusable(false);
        for (int i = 0; i < p.getChildCount(); i++) {
            View child = p.getChildAt(i);
            child.setFocusable(false);
            child.setFocusableInTouchMode(false);
            child.setClickable(false);
        }
        return p;
    }

    private float dpx(float v) {
        return dp(v);
    }

    private void refresh() {
        if (inBtn == null) return;
        inBtn.setText(String.format(Locale.CHINA, "签到核对时间    %02d:%02d",
                Config.inHour(ctx), Config.inMin(ctx)));
        outBtn.setText(String.format(Locale.CHINA, "签退核对时间    %02d:%02d",
                Config.outHour(ctx), Config.outMin(ctx)));

        boolean on = Config.remindOn(ctx);
        enableSwitch.setOnCheckedChangeListener(null);
        enableSwitch.setChecked(on);
        enableSwitch.setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                        Config.setRemindOn(ctx, v);
                        Scheduler.ensure(ctx);
                        Toast.makeText(ctx, v ? "打卡提醒已开启" : "打卡提醒已关闭",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }


    /**
     * 把时刻换成「考勤周期」内的绝对分钟数：
     * 周期从 04:00 开始，到次日 04:00 结束。
     * 04:00 -> 0，09:15 -> 315，次日 02:00 -> 1320。
     */
    private static int cycleMinutes(int hour, int minute) {
        int m = hour * 60 + minute;
        int start = 4 * 60;
        int v = m - start;
        if (v < 0) v += 24 * 60;
        return v;
    }
}
