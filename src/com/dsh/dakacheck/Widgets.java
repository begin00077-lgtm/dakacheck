package com.dsh.dakacheck;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.Locale;

/** 桌面卡片：4x2 和 2x2 两种尺寸，透明背景、分区不同颜色。 */
public class Widgets {

    public static void refreshAll(Context c) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(c);
            int[] a = mgr.getAppWidgetIds(new ComponentName(c, Widget4x2.class));
            for (int id : a) mgr.updateAppWidget(id, build(c, R.layout.widget_4x2));
            int[] b = mgr.getAppWidgetIds(new ComponentName(c, Widget2x2.class));
            for (int id : b) mgr.updateAppWidget(id, build(c, R.layout.widget_2x2));
        } catch (Throwable t) {
            android.util.Log.i("Daka", "widget refresh failed: " + t);
        }
    }

    static RemoteViews build(Context c, int layout) {
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);

        String date = Store.logicalDate();
        String in = Store.getIn(c, date);
        String out = Store.getOut(c, date);
        Double today = Work.hoursOf(c, date);
        Calendar cal = Calendar.getInstance();
        double month = Work.monthTotal(c, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);

        v.setTextViewText(R.id.w_in, Work.hhmm(in));
        v.setTextViewText(R.id.w_out, Work.hhmm(out));
        v.setTextViewText(R.id.w_today, today == null ? "--"
                : String.format(Locale.CHINA, "%.2fh", today));
        v.setTextViewText(R.id.w_month, String.format(Locale.CHINA, "%.1fh", month));

        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 8001, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_in, pi);
        v.setOnClickPendingIntent(R.id.w_out, pi);
        v.setOnClickPendingIntent(R.id.w_today, pi);
        v.setOnClickPendingIntent(R.id.w_month, pi);
        return v;
    }

    public static class Widget4x2 extends AppWidgetProvider {
        @Override
        public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
            for (int id : ids) mgr.updateAppWidget(id, build(c, R.layout.widget_4x2));
            Guard.ensure(c);
        }
    }

    public static class Widget2x2 extends AppWidgetProvider {
        @Override
        public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
            for (int id : ids) mgr.updateAppWidget(id, build(c, R.layout.widget_2x2));
            Guard.ensure(c);
        }
    }
}
