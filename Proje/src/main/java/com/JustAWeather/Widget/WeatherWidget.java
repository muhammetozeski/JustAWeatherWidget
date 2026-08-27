package com.JustAWeather.Widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.Date;

public class WeatherWidget extends AppWidgetProvider {

    static final String ACTION_TICK = "com.JustAWeather.Widget.TICK";
    static final String EXTRA_DAY = "day";

    // FLAG_IMMUTABLE is a compile time constant (API 23+); older systems ignore the extra bit.
    private static final int PI_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String action = intent.getAction();
        if (!AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                && !ACTION_TICK.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            return;
        }
        int[] ids = ids(ctx);
        schedule(ctx);
        if (ids.length == 0) return;
        for (int id : ids) render(ctx, id);   // the stored forecast shows immediately
        refresh(ctx, ids, goAsync());         // then go online
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        SharedPreferences.Editor e = Cfg.prefs(ctx).edit();
        for (int id : ids) Cfg.clear(e, id);
        e.commit();
        schedule(ctx);
    }

    @Override
    public void onDisabled(Context ctx) {
        alarm(ctx, null);
    }

    static int[] ids(Context ctx) {
        return AppWidgetManager.getInstance(ctx)
                .getAppWidgetIds(new ComponentName(ctx, WeatherWidget.class));
    }

    // ------------------------------------------------------------------ update

    /**
     * Fetches in the background and redraws. A failed fetch is not an error the user
     * has to see: the last forecast stays on screen, flagged as offline.
     *
     * @param pending result of goAsync() when called from onReceive, null otherwise
     */
    static void refresh(final Context ctx, final int[] ids,
                        final BroadcastReceiver.PendingResult pending) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int id : ids) {
                    try {
                        Api.fetch(Cfg.load(app, id)).save(app, id);
                    } catch (Throwable t) {
                        Log.w(Api.TAG, "widget " + id + ": update failed, keeping the last forecast", t);
                        Data.offline(app, id);
                    } finally {
                        try {
                            render(app, id);
                        } catch (Throwable t) {
                            Log.e(Api.TAG, "widget " + id + ": could not be drawn", t);
                        }
                    }
                }
                if (pending != null) {
                    try {
                        pending.finish();
                    } catch (Throwable t) {
                        Log.e(Api.TAG, "PendingResult.finish() failed", t);
                    }
                }
            }
        }).start();
    }

    static void render(Context ctx, int id) {
        AppWidgetManager.getInstance(ctx).updateAppWidget(
                id, build(ctx, id, Cfg.load(ctx, id), Data.load(ctx, id)));
    }

    // ------------------------------------------------------------------ drawing

    static RemoteViews build(Context ctx, int id, Cfg c, Data d) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        float s = Cfg.SCALE[Cfg.clamp(c.size, 0, Cfg.SCALE.length - 1)];
        int fg = 0xFF000000 | c.fg;
        boolean known = d.has();

        v.setImageViewResource(R.id.bg, c.round ? R.drawable.bg_round : R.drawable.bg_flat);
        v.setInt(R.id.bg, "setColorFilter", 0xFF000000 | c.bg);
        v.setInt(R.id.bg, "setImageAlpha", Cfg.clamp(c.alpha, 0, 255));

        // ---- current conditions
        v.setViewVisibility(R.id.now, c.showNow ? View.VISIBLE : View.GONE);
        String label = c.label.trim();
        text(v, R.id.label, c.showLabel && label.length() > 0 ? Api.PIN + " " + label : null, fg, 12f * s);
        text(v, R.id.icon, c.showIcon
                ? (known ? Api.emoji(d.code(), d.daylight()) : Api.UNKNOWN) : null, fg, 26f * s);
        text(v, R.id.temp, known ? degrees(d.temp()) : "--°", fg, 26f * s);

        int rainNow = known ? d.rainNow() : -1;
        text(v, R.id.rain, c.showRain && rainNow >= 0 ? Api.DROP + " " + rainNow + "%" : null, fg, 14f * s);

        String when = null;
        if (c.showTime && d.ts > 0) {
            when = (d.offline ? Api.OFFLINE : Api.CLOCK) + " " + Fmt.time(ctx, new Date(d.ts));
        }
        text(v, R.id.time, when, fg, 10f * s);

        // ---- forecast strip, one column per day
        v.removeAllViews(R.id.days);
        int wanted = c.forecastDays();
        int have = known ? d.dayCount() : 0;
        for (int i = 0; i < wanted; i++) {
            RemoteViews col = new RemoteViews(ctx.getPackageName(), R.layout.day);
            boolean filled = i < have;
            Date date = filled ? d.dayDate(i) : dayFromToday(i);

            text(col, R.id.dName, Fmt.weekdayShort(date), fg, 10f * s);
            text(col, R.id.dIcon, filled ? Api.emoji(d.dayCode(i), true) : Api.UNKNOWN, fg, 14f * s);
            text(col, R.id.dTemp, filled ? degrees(d.dayMax(i)) : "--°", fg, 11f * s);

            int rain = filled ? d.dayRain(i) : -1;
            text(col, R.id.dRain, rain >= 0 ? Api.DROP + rain + "%" : " ", fg, 10f * s);

            // Tapping a day opens its hour by hour detail.
            Intent day = new Intent(ctx, DetailActivity.class)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(EXTRA_DAY, i)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            col.setOnClickPendingIntent(R.id.dayCol,
                    PendingIntent.getActivity(ctx, id * 100 + i, day, PI_FLAGS));

            v.addView(R.id.days, col);
        }
        v.setViewVisibility(R.id.days, wanted > 0 ? View.VISIBLE : View.GONE);

        // Tapping the current conditions opens the settings (which also refreshes).
        Intent settings = new Intent(ctx, ConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, id, settings, PI_FLAGS);
        v.setOnClickPendingIntent(R.id.now, pi);
        v.setOnClickPendingIntent(R.id.bg, pi);     // the empty area around the text
        return v;
    }

    static String degrees(double t) {
        return Double.isNaN(t) ? "--°" : Math.round(t) + "°";
    }

    private static Date dayFromToday(int offset) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, offset);
        return c.getTime();
    }

    private static void text(RemoteViews v, int id, String s, int color, float sp) {
        if (s == null) {
            v.setViewVisibility(id, View.GONE);
            return;
        }
        v.setViewVisibility(id, View.VISIBLE);
        v.setTextViewText(id, s);
        v.setTextColor(id, color);
        v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp);
    }

    // ------------------------------------------------------------------ alarm

    /**
     * One repeating alarm for all widgets, running at the shortest interval any of
     * them asked for. updatePeriodMillis is left at 0 so this is the only clock.
     */
    static void schedule(Context ctx) {
        int[] ids = ids(ctx);
        if (ids.length == 0) {
            alarm(ctx, null);
            return;
        }
        int minutes = Integer.MAX_VALUE;
        for (int id : ids) minutes = Math.min(minutes, Cfg.load(ctx, id).everyMinutes());
        alarm(ctx, minutes * 60000L);
    }

    private static void alarm(Context ctx, Long periodMs) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) {
                Log.e(Api.TAG, "no AlarmManager, falling back to tap to refresh only");
                return;
            }
            Intent i = new Intent(ctx, WeatherWidget.class).setAction(ACTION_TICK);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i, PI_FLAGS);
            am.cancel(pi);
            if (periodMs != null) {
                am.setInexactRepeating(AlarmManager.RTC,
                        System.currentTimeMillis() + periodMs, periodMs, pi);
            }
        } catch (Throwable t) {
            Log.e(Api.TAG, "could not set the update alarm; the widget still refreshes on tap", t);
        }
    }
}
