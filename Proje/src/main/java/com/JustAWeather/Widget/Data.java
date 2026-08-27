package com.JustAWeather.Widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;

/**
 * The last forecast of one widget. The raw Open-Meteo response is what gets stored,
 * so the widget, the day details and the settings preview all read the same copy and
 * all of them keep working with no network - the reading is only marked as offline.
 *
 * Open-Meteo returns whole local days: hourly index d * 24 + h is hour h of day d,
 * and day 0 is today. Everything below leans on that.
 */
class Data {

    private static final int HOURS_PER_DAY = 24;

    String json;
    long ts;
    boolean offline;

    private JSONObject root;
    private boolean parsed;

    boolean has() {
        return json != null && json.length() > 0 && ts > 0 && root() != null;
    }

    private JSONObject root() {
        if (!parsed) {
            parsed = true;
            try {
                root = new JSONObject(json);
            } catch (Throwable t) {
                Log.w(Api.TAG, "stored forecast could not be read", t);
                root = null;
            }
        }
        return root;
    }

    // ------------------------------------------------------------------ now

    double temp() {
        return num(obj("current"), "temperature_2m", Double.NaN);
    }

    int code() {
        return (int) num(obj("current"), "weather_code", -1);
    }

    boolean daylight() {
        return num(obj("current"), "is_day", 1) != 0;
    }

    /** Rain chance of the hour we are in, -1 when the API did not give one. */
    int rainNow() {
        String t = str(obj("current"), "time");
        if (t.length() < 13) return -1;
        try {
            return hourRain(0, Integer.parseInt(t.substring(11, 13)));
        } catch (Throwable e) {
            Log.w(Api.TAG, "current hour could not be read from '" + t + "'", e);
            return -1;
        }
    }

    // ------------------------------------------------------------------ days

    int dayCount() {
        JSONArray a = arr(obj("daily"), "time");
        return a == null ? 0 : a.length();
    }

    double dayMax(int d) {
        return item(obj("daily"), "temperature_2m_max", d, Double.NaN);
    }

    double dayMin(int d) {
        return item(obj("daily"), "temperature_2m_min", d, Double.NaN);
    }

    int dayCode(int d) {
        return (int) item(obj("daily"), "weather_code", d, -1);
    }

    int dayRain(int d) {
        return (int) item(obj("daily"), "precipitation_probability_max", d, -1);
    }

    /** Midday of day d, which is what the weekday and date labels are built from. */
    Date dayDate(int d) {
        JSONArray a = arr(obj("daily"), "time");
        String iso = a == null ? null : a.optString(d, null);
        return atNoon(iso, d);
    }

    // ------------------------------------------------------------------ hours

    int hourCount(int d) {
        JSONArray a = arr(obj("hourly"), "time");
        if (a == null) return 0;
        int left = a.length() - d * HOURS_PER_DAY;
        return left < 0 ? 0 : Math.min(HOURS_PER_DAY, left);
    }

    double hourTemp(int d, int h) {
        return item(obj("hourly"), "temperature_2m", d * HOURS_PER_DAY + h, Double.NaN);
    }

    int hourCode(int d, int h) {
        return (int) item(obj("hourly"), "weather_code", d * HOURS_PER_DAY + h, -1);
    }

    int hourRain(int d, int h) {
        return (int) item(obj("hourly"), "precipitation_probability", d * HOURS_PER_DAY + h, -1);
    }

    Date hourDate(int d, int h) {
        Date base = dayDate(d);
        Calendar c = Calendar.getInstance();
        c.setTime(base);
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        return c.getTime();
    }

    /**
     * "2026-08-27" at noon local. Noon keeps the weekday right whatever the time zone
     * does around midnight; without a date the day is counted from today instead.
     */
    private static Date atNoon(String iso, int fallbackOffset) {
        Calendar c = Calendar.getInstance();
        if (iso != null && iso.length() >= 10) {
            try {
                c.clear();
                c.set(Integer.parseInt(iso.substring(0, 4)),
                        Integer.parseInt(iso.substring(5, 7)) - 1,
                        Integer.parseInt(iso.substring(8, 10)), 12, 0, 0);
                return c.getTime();
            } catch (Throwable t) {
                Log.w(Api.TAG, "date '" + iso + "' could not be read, counting from today", t);
            }
        }
        c.setTimeInMillis(System.currentTimeMillis());
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, fallbackOffset);
        return c.getTime();
    }

    // ------------------------------------------------------------------ json helpers

    private JSONObject obj(String name) {
        JSONObject r = root();
        return r == null ? null : r.optJSONObject(name);
    }

    private static JSONArray arr(JSONObject o, String name) {
        return o == null ? null : o.optJSONArray(name);
    }

    private static String str(JSONObject o, String name) {
        return o == null ? "" : o.optString(name, "");
    }

    private static double num(JSONObject o, String name, double fallback) {
        return o == null || o.isNull(name) ? fallback : o.optDouble(name, fallback);
    }

    private static double item(JSONObject o, String name, int i, double fallback) {
        JSONArray a = arr(o, name);
        if (a == null || i < 0 || i >= a.length() || a.isNull(i)) return fallback;
        return a.optDouble(i, fallback);
    }

    // ------------------------------------------------------------------ storage

    private static String k(int id, String n) {
        return "w" + id + "_" + n;
    }

    static Data load(Context c, int id) {
        SharedPreferences p = Cfg.prefs(c);
        Data d = new Data();
        d.json = p.getString(k(id, "json"), null);
        d.ts = p.getLong(k(id, "ts"), 0);
        d.offline = p.getBoolean(k(id, "off"), false);
        return d;
    }

    void save(Context c, int id) {
        Cfg.prefs(c).edit()
                .putString(k(id, "json"), json)
                .putLong(k(id, "ts"), ts)
                .putBoolean(k(id, "off"), false)
                .commit();
    }

    /** Keeps the stored forecast, only marks it as not fresh. */
    static void offline(Context c, int id) {
        try {
            Cfg.prefs(c).edit().putBoolean(k(id, "off"), true).commit();
        } catch (Throwable t) {
            Log.e(Api.TAG, "could not flag widget " + id + " as offline", t);
        }
    }

    static void clear(SharedPreferences.Editor e, int id) {
        String[] names = { "json", "ts", "off" };
        for (String n : names) e.remove(k(id, n));
    }
}
