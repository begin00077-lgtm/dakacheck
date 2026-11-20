package com.dsh.dakacheck;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

/**
 * 唯一的主界面：四个页面并排，底部导航栏独立，拖拽时页面真实补位。
 */
public class HomeActivity extends Activity implements PagerHost.Bridge {

    private PagerHost pager;
    private RemindPage remindPage;
    private StatusPage statusPage;
    private HoursPage hoursPage;
    private SettingsPage settingsPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MainActivity.initCtx(getApplicationContext());
        Holidays.refreshAsync(this);
        Scheduler.ensure(this);
        

        pager = new PagerHost(this, this);
        setContentView(pager);
        pager.buildAll();
    }

    @Override
    public View createPage(int tab) {
        switch (tab) {
            case PagerHost.TAB_REMIND:
                if (remindPage == null) remindPage = new RemindPage(this);
                return remindPage.build();
            case PagerHost.TAB_HOURS:
                if (hoursPage == null) hoursPage = new HoursPage(this);
                return hoursPage.build();
            case PagerHost.TAB_SETTINGS:
                if (settingsPage == null) settingsPage = new SettingsPage(this);
                return settingsPage.build();
            default:
                if (statusPage == null) statusPage = new StatusPage(this);
                return statusPage.build();
        }
    }

    @Override
    public boolean inCalendarArea(int tab, float rawX, float rawY) {
        if (tab != PagerHost.TAB_HOURS) return false;
        View page = pager.page(PagerHost.TAB_HOURS);
        if (page == null) return false;
        View cal = page.findViewWithTag("cal_area");
        if (cal == null) return false;
        int[] loc = new int[2];
        cal.getLocationOnScreen(loc);
        return rawX >= loc[0] && rawX <= loc[0] + cal.getWidth()
                && rawY >= loc[1] && rawY <= loc[1] + cal.getHeight();
    }

    @Override
    public void onPageShown(int tab) {
        if (tab == PagerHost.TAB_REMIND && remindPage != null) {
            remindPage.onShown();
        }
        if (statusPage != null) {
            statusPage.refresh();
        }
    }

    /** 交给 PagerHost 统一判定手势方向，避免和子视图抢事件。 */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (pager != null && pager.handleTouch(ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }
}
