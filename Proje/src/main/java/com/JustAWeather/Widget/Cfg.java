package com.JustAWeather.Widget;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings of one widget instance. Every widget on the home screen keeps its own copy,
 * stored under the "w<id>_" prefix. The "d_" prefix holds the defaults a newly placed
 * widget starts from (they are written every time settings are saved).
 */
class Cfg {

    static final String PREFS = "jaww";

    static final int[] EVERY_MIN = { 15, 30, 60, 180, 360 };
    static final String[] EVERY_TXT = { "15 minutes", "30 minutes", "1 hour", "3 hours", "6 hours" };
    static final float[] SCALE = { 0.8f, 1f, 1.25f, 1.55f };
    static final String[] SIZE_TXT = { "Small", "Medium", "Large", "Huge" };

    /** Open-Meteo gives at most 16 days; 7 is all that fits in a widget strip. */
    static final int MIN_DAYS = 3;
    static final int MAX_DAYS = 7;

    /** Istanbul - only a starting point, the user types their own coordinates. */
    String lat = "41.0082";
    String lon = "28.9784";
    String label = "";
    boolean fahrenheit = false;
    int bg = 0x1A2330;        // RGB only, alpha is kept separately
    int alpha = 220;          // 0..255
    int fg = 0xFFFFFF;        // RGB only
    int size = 1;             // index into SCALE
    boolean round = true;
    boolean showLabel = true;
    boolean showIcon = true;
    boolean showRain = true;
    boolean showTime = true;
    boolean showNow = true;   // the big current temperature block
    int days = 3;             // days in the forecast strip, 0 hides it
    int every = 2;            // index into EVERY_MIN

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String k(int id, String name) {
        return (id > 0 ? "w" + id : "d") + "_" + name;
    }

    /** True once this widget has settings of its own (i.e. it has been configured). */
    static boolean stored(Context c, int id) {
        return id > 0 && prefs(c).contains(k(id, "lat"));
    }

    static Cfg load(Context c, int id) {
        SharedPreferences p = prefs(c);
        Cfg defaults = read(p, 0, new Cfg());
        return id > 0 ? read(p, id, defaults) : defaults;
    }

    private static Cfg read(SharedPreferences p, int id, Cfg d) {
        Cfg o = new Cfg();
        o.lat = p.getString(k(id, "lat"), d.lat);
        o.lon = p.getString(k(id, "lon"), d.lon);
        o.label = p.getString(k(id, "label"), d.label);
        o.fahrenheit = p.getBoolean(k(id, "f"), d.fahrenheit);
        o.bg = p.getInt(k(id, "bg"), d.bg);
        o.alpha = clamp(p.getInt(k(id, "alpha"), d.alpha), 0, 255);
        o.fg = p.getInt(k(id, "fg"), d.fg);
        o.size = clamp(p.getInt(k(id, "size"), d.size), 0, SCALE.length - 1);
        o.round = p.getBoolean(k(id, "round"), d.round);
        o.showLabel = p.getBoolean(k(id, "sl"), d.showLabel);
        o.showIcon = p.getBoolean(k(id, "si"), d.showIcon);
        o.showRain = p.getBoolean(k(id, "sr"), d.showRain);
        o.showTime = p.getBoolean(k(id, "st"), d.showTime);
        o.showNow = p.getBoolean(k(id, "sn"), d.showNow);
        o.days = clamp(p.getInt(k(id, "days"), d.days), MIN_DAYS, MAX_DAYS);
        o.every = clamp(p.getInt(k(id, "every"), d.every), 0, EVERY_MIN.length - 1);
        return o;
    }

    void save(Context c, int id) {
        prefs(c).edit()
                .putString(k(id, "lat"), lat)
                .putString(k(id, "lon"), lon)
                .putString(k(id, "label"), label)
                .putBoolean(k(id, "f"), fahrenheit)
                .putInt(k(id, "bg"), bg)
                .putInt(k(id, "alpha"), alpha)
                .putInt(k(id, "fg"), fg)
                .putInt(k(id, "size"), size)
                .putBoolean(k(id, "round"), round)
                .putBoolean(k(id, "sl"), showLabel)
                .putBoolean(k(id, "si"), showIcon)
                .putBoolean(k(id, "sr"), showRain)
                .putBoolean(k(id, "st"), showTime)
                .putBoolean(k(id, "sn"), showNow)
                .putInt(k(id, "days"), days)
                .putInt(k(id, "every"), every)
                .commit();
    }

    /** Called when a widget is removed from the home screen so nothing is left behind. */
    static void clear(SharedPreferences.Editor e, int id) {
        String[] names = { "lat", "lon", "label", "f", "bg", "alpha", "fg", "size",
                "round", "sl", "si", "sr", "st", "sn", "days", "every" };
        for (String n : names) e.remove(k(id, n));
        Data.clear(e, id);
    }

    int everyMinutes() {
        return EVERY_MIN[clamp(every, 0, EVERY_MIN.length - 1)];
    }

    /** Days to ask the API for: today plus the strip, whatever the strip is set to. */
    int forecastDays() {
        return clamp(days, MIN_DAYS, MAX_DAYS);
    }

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
