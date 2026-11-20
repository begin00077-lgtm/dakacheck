package com.dsh.dakacheck;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
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

/** 提醒设置：核对时间、开关、说明。 */
public class RemindActivity extends Activity {

    private Button inBtn;
    private Button outBtn;
    private Switch enableSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setScrollbarFadingEnabled(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(16);
        root.setPadding(pad, (int) dp(12), pad, (int) dp(16));
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // 总开关
        LinearLayout swRow = new LinearLayout(this);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        swRow.setBackgroundColor(Color.WHITE);
        swRow.setPadding((int) dp(16), (int) dp(14), (int) dp(16), (int) dp(14));

        LinearLayout swText = new LinearLayout(this);
        swText.setOrientation(LinearLayout.VERTICAL);

        TextView swTitle = new TextView(this);
        swTitle.setText("打卡提醒");
        swTitle.setTextSize(16f);
        swTitle.setTextColor(Color.parseColor("#111111"));

        TextView swDesc = new TextView(this);
        swDesc.setText("每天按下面的时间核对是否打卡");
        swDesc.setTextSize(12f);
        swDesc.setTextColor(Color.parseColor("#888888"));
        swDesc.setPadding(0, (int) dp(3), 0, 0);

        swText.addView(swTitle);
        swText.addView(swDesc);
        swRow.addView(swText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        enableSwitch = new Switch(this);
        enableSwitch.setOnCheckedChangeListener(null);
        swRow.addView(enableSwitch);
        root.addView(swRow, matchWrap((int) dp(10)));

        // 说明
        root.addView(section("说明"));

        TextView hint = new TextView(this);
        hint.setText("• 考勤日按 04:00 划分：0–4 点之间页面显示的是前一天的考勤，其余时间查当天。\n"
                + "• 下班卡在次日凌晨核对，方便加班到 0 点后的人补打卡。\n"
                + "• 周末和法定节假日不核对；调休补班日按工作日处理。\n"
                + "• 异常提醒永久开启，考勤同步每次都会自动执行。");
        hint.setTextSize(13.5f);
        hint.setTextColor(Color.parseColor("#444444"));
        hint.setLineSpacing(dp(4), 1f);
        hint.setPadding((int) dp(4), (int) dp(8), (int) dp(4), 0);
        root.addView(hint, matchWrap(0));

        // 核对时间
        root.addView(section("核对时间"));

        inBtn = new Button(this);
        inBtn.setAllCaps(false);
        inBtn.setTextSize(16f);
        inBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pick(true);
            }
        });
        root.addView(inBtn, matchWrap((int) dp(4)));

        outBtn = new Button(this);
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
        return NavBar.attach(this, NavBar.TAB_REMIND, scroll);
    }

    private void pick(final boolean isIn) {
        int h = isIn ? Config.inHour(this) : Config.outHour(this);
        int m = isIn ? Config.inMin(this) : Config.outMin(this);
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                if (isIn) {
                    Config.setInHour(MainActivity.ctx(), hourOfDay);
                    Config.setInMin(MainActivity.ctx(), minute);
                } else {
                    Config.setOutHour(MainActivity.ctx(), hourOfDay);
                    Config.setOutMin(MainActivity.ctx(), minute);
                }
                Scheduler.ensure(RemindActivity.this);
                refresh();
                Toast.makeText(RemindActivity.this, "已更新", Toast.LENGTH_SHORT).show();
            }
        }, h, m, true).show();
    }

    private void refresh() {
        inBtn.setText(String.format(Locale.CHINA, "上班卡核对时间    %02d:%02d",
                Config.inHour(this), Config.inMin(this)));
        outBtn.setText(String.format(Locale.CHINA, "下班卡核对时间    %02d:%02d",
                Config.outHour(this), Config.outMin(this)));

        boolean on = Config.remindOn(this);
        enableSwitch.setOnCheckedChangeListener(null);
        enableSwitch.setChecked(on);
        enableSwitch.setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                        Config.setRemindOn(RemindActivity.this, v);
                        Scheduler.ensure(RemindActivity.this);
                        Toast.makeText(RemindActivity.this,
                                v ? "打卡提醒已开启" : "打卡提醒已关闭",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private TextView section(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(12.5f);
        v.setTextColor(Color.parseColor("#888888"));
        v.setPadding((int) dp(4), (int) dp(18), 0, (int) dp(6));
        return v;
    }
}
