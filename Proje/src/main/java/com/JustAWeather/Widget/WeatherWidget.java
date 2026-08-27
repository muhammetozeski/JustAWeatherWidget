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
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.Date;

public class WeatherWidget extends AppWidgetProvider {

    static final String ACTION_TICK = "com.JustAWeather.Widget.TICK";
    static final String EXTRA_DAY = "day";

    /** Used when the host has not told us how big the widget is yet. */
    private static final int FALLBACK_W_DP = 180;
    private static final int FALLBACK_H_DP = 110;

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

    /** The user resized the widget: the text is sized from the widget, so redraw it. */
    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager m, int id, Bundle options) {
        super.onAppWidgetOptionsChanged(ctx, m, id, options);
        render(ctx, id);
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
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int w = FALLBACK_W_DP, h = FALLBACK_H_DP;
        try {
            Bundle o = m.getAppWidgetOptions(id);       // API 16+
            if (o != null) {
                int ow = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
                int oh = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
                if (ow > 0) w = ow;
                if (oh > 0) h = oh;
            }
        } catch (Throwable t) {
            Log.w(Api.TAG, "widget " + id + " size unknown, using the default", t);
        }
        m.updateAppWidget(id, build(ctx, id, Cfg.load(ctx, id), Data.load(ctx, id), w, h));
    }

    // ------------------------------------------------------------------ drawing

    /**
     * @param wDp width the host gives the widget, @param hDp its height. The text is
     *            sized from those and from how many columns have to fit, so the widget
     *            grows with the space it is given instead of staying at one size.
     */
    static RemoteViews build(Context ctx, int id, Cfg c, Data d, int wDp, int hDp) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        int fg = 0xFF000000 | c.fg;
        boolean known = d.has();
        int columns = c.columns();

        v.setImageViewResource(R.id.bg, c.round ? R.drawable.bg_round : R.drawable.bg_flat);
        v.setInt(R.id.bg, "setColorFilter", 0xFF000000 | c.bg);
        v.setInt(R.id.bg, "setImageAlpha", Cfg.clamp(c.alpha, 0, 255));

        String label = c.label.trim();
        boolean hasLabel = c.showLabel && label.length() > 0;
        boolean hasTime = c.showTime && d.ts > 0;

        // ---- automatic size: the column has to fit sideways and the lines vertically
        float colW = Math.max(12f, (wDp - 14f) / Math.max(1, columns));
        float rows = 1f                              // the temperature
                + (c.showIcon ? 0.95f : 0f)          // the emoji
                + 0.65f                              // the day or hour name
                + (c.showRain ? 0.65f : 0f);         // the rain chance
        float freeH = hDp - 12f - (hasLabel ? 16f : 0f) - (hasTime ? 14f : 0f);
        float base = Math.min(colW * 0.42f, freeH / Math.max(1.6f, rows + 0.5f));
        base = Cfg.clamp(base * Cfg.SCALE[Cfg.clamp(c.size, 0, Cfg.SCALE.length - 1)], 8f, 40f);

        text(v, R.id.label, hasLabel ? Api.PIN + " " + label : null, fg,
                Cfg.clamp(base * 0.62f, 8f, 15f));
        text(v, R.id.time, hasTime
                ? (d.offline ? Api.OFFLINE : Api.CLOCK) + " "
                        + DateFormat.getTimeFormat(ctx).format(new Date(d.ts))
                : null, fg, Cfg.clamp(base * 0.55f, 7f, 13f));

        // ---- the strip: same shape for every column, today included
        v.removeAllViews(R.id.days);
        for (int i = 0; i < columns; i++) {
            RemoteViews col = new RemoteViews(ctx.getPackageName(), R.layout.day);
            int day = c.hourly ? fillHour(ctx, col, c, d, known, i, fg, base)
                    : fillDay(ctx, col, c, d, known, i, fg, base);

            // Tapping a column opens that day hour by hour.
            Intent detail = new Intent(ctx, DetailActivity.class)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(EXTRA_DAY, day)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            col.setOnClickPendingIntent(R.id.dayCol,
                    PendingIntent.getActivity(ctx, id * 100 + i, detail, PI_FLAGS));
            v.addView(R.id.days, col);
        }

        // Nothing selected at all: say so instead of showing an empty box.
        text(v, R.id.empty, columns == 0 ? "Tap to choose days or hours" : null, fg,
                Cfg.clamp(base * 0.6f, 9f, 14f));

        // Tapping anywhere else opens the settings (which also refreshes).
        Intent settings = new Intent(ctx, ConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, id, settings, PI_FLAGS);
        v.setOnClickPendingIntent(R.id.bg, pi);
        v.setOnClickPendingIntent(R.id.label, pi);
        v.setOnClickPendingIntent(R.id.time, pi);
        v.setOnClickPendingIntent(R.id.empty, pi);
        return v;
    }

    /** One day column. Returns the day index it stands for. */
    private static int fillDay(Context ctx, RemoteViews col, Cfg c, Data d, boolean known,
                               int i, int fg, float base) {
        int day = (c.showToday ? 0 : 1) + i;
        boolean filled = known && day < d.dayCount();
        Date date = filled ? d.dayDate(day) : dayFromToday(day);

        text(col, R.id.dName, DateFormat.format("EEE", date).toString(), fg, base * 0.65f);
        text(col, R.id.dIcon, c.showIcon
                ? (filled ? Api.emoji(d.dayCode(day), true) : Api.UNKNOWN) : null, fg, base * 0.95f);
        text(col, R.id.dTemp, filled ? degrees(d.dayMax(day)) : "--°", fg, base);
        int rain = filled ? d.dayRain(day) : -1;
        text(col, R.id.dRain, c.showRain ? (rain >= 0 ? Api.DROP + rain + "%" : " ") : null,
                fg, base * 0.65f);
        return day;
    }

    /** One hour column. Returns the day index that hour belongs to. */
    private static int fillHour(Context ctx, RemoteViews col, Cfg c, Data d, boolean known,
                                int i, int fg, float base) {
        int now = known ? d.nowHour() : -1;
        int index = Math.max(0, now) + c.startHours + i;
        boolean filled = known && now >= 0 && index < d.hourTotal();
        Date when = filled ? d.hourDateAt(index) : Data.hourFromNow(c.startHours + i);

        text(col, R.id.dName, DateFormat.getTimeFormat(ctx).format(when), fg, base * 0.65f);
        text(col, R.id.dIcon, c.showIcon
                ? (filled ? Api.emoji(d.hourCodeAt(index), daylight(when)) : Api.UNKNOWN)
                : null, fg, base * 0.95f);
        text(col, R.id.dTemp, filled ? degrees(d.hourTempAt(index)) : "--°", fg, base);
        int rain = filled ? d.hourRainAt(index) : -1;
        text(col, R.id.dRain, c.showRain ? (rain >= 0 ? Api.DROP + rain + "%" : " ") : null,
                fg, base * 0.65f);
        return index / 24;
    }

    private static boolean daylight(Date when) {
        Calendar c = Calendar.getInstance();
        c.setTime(when);
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h >= 7 && h <= 19;
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
