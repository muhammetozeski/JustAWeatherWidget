package com.JustAWeather.Widget;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * The cached forecast of one widget: always the full 16 days with every hour, whatever
 * the widget is set to show. Storing all of it costs one small compressed file and means
 * changing the settings, or opening an hour list, never has to wait for the network.
 *
 * Open-Meteo returns whole local days, so hourly index d * 24 + h is hour h of day d.
 * Which day is "today" is looked up by date rather than assumed to be day 0: a forecast
 * kept through a night offline still lines up, it just reaches fewer days ahead.
 */
class Data {

    private static final int HOURS_PER_DAY = 24;

    String json;
    long ts;
    boolean offline;

    private JSONObject root;
    private boolean parsed;

    static Data load(Context c, int id) {
        Data d = new Data();
        Store.load(c, id, d);
        return d;
    }

    void save(Context c, int id) {
        Store.save(c, id, json, ts);
    }

    boolean has() {
        return json != null && json.length() > 0 && ts > 0 && root() != null;
    }

    private JSONObject root() {
        if (!parsed) {
            parsed = true;
            try {
                root = new JSONObject(json);
            } catch (Throwable t) {
                Log.w(Api.TAG, "cached forecast could not be read", t);
                root = null;
            }
        }
        return root;
    }

    // ------------------------------------------------------------------ where "now" is

    /**
     * The clock at the coordinates, not on the phone: the forecast is in the location's
     * own time zone, and utc_offset_seconds says what that is.
     */
    private Calendar there() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        long offset = (long) num(root(), "utc_offset_seconds", 0);
        c.setTimeInMillis(System.currentTimeMillis() + offset * 1000L);
        return c;
    }

    /** Index of today in the daily arrays, or -1 when the forecast no longer reaches it. */
    int todayIndex() {
        JSONArray days = arr(obj("daily"), "time");
        if (days == null) return -1;
        Calendar now = there();
        String today = iso(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH));
        for (int i = 0; i < days.length(); i++) {
            if (today.equals(days.optString(i, ""))) return i;
        }
        return -1;
    }

    /** Index of the hour we are in, or -1 when the forecast no longer covers it. */
    int nowIndex() {
        int today = todayIndex();
        if (today < 0) return -1;
        int i = today * HOURS_PER_DAY + there().get(Calendar.HOUR_OF_DAY);
        return i < hourTotal() ? i : -1;
    }

    private static String iso(int y, int m, int d) {
        return y + "-" + (m < 10 ? "0" : "") + m + "-" + (d < 10 ? "0" : "") + d;
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
        String date = a == null ? null : a.optString(d, null);
        Calendar c = Calendar.getInstance();
        if (date != null && date.length() >= 10) {
            try {
                c.clear();
                c.set(Integer.parseInt(date.substring(0, 4)),
                        Integer.parseInt(date.substring(5, 7)) - 1,
                        Integer.parseInt(date.substring(8, 10)), 12, 0, 0);
                return c.getTime();
            } catch (Throwable t) {
                Log.w(Api.TAG, "date '" + date + "' could not be read, counting from today", t);
            }
        }
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, d - Math.max(0, todayIndex()));
        return c.getTime();
    }

    // ------------------------------------------------------------------ hours

    int hourCount(int d) {
        int left = hourTotal() - d * HOURS_PER_DAY;
        return left < 0 ? 0 : Math.min(HOURS_PER_DAY, left);
    }

    double hourTemp(int d, int h) {
        return hourTempAt(d * HOURS_PER_DAY + h);
    }

    int hourCode(int d, int h) {
        return hourCodeAt(d * HOURS_PER_DAY + h);
    }

    int hourRain(int d, int h) {
        return hourRainAt(d * HOURS_PER_DAY + h);
    }

    Date hourDate(int d, int h) {
        return hourDateAt(d * HOURS_PER_DAY + h);
    }

    int hourTotal() {
        JSONArray a = arr(obj("hourly"), "time");
        return a == null ? 0 : a.length();
    }

    double hourTempAt(int i) {
        return item(obj("hourly"), "temperature_2m", i, Double.NaN);
    }

    int hourCodeAt(int i) {
        return (int) item(obj("hourly"), "weather_code", i, -1);
    }

    int hourRainAt(int i) {
        return (int) item(obj("hourly"), "precipitation_probability", i, -1);
    }

    Date hourDateAt(int i) {
        JSONArray a = arr(obj("hourly"), "time");
        String time = a == null ? null : a.optString(i, null);
        if (time != null && time.length() >= 13) {
            try {
                Calendar c = Calendar.getInstance();
                c.clear();
                c.set(Integer.parseInt(time.substring(0, 4)),
                        Integer.parseInt(time.substring(5, 7)) - 1,
                        Integer.parseInt(time.substring(8, 10)),
                        Integer.parseInt(time.substring(11, 13)), 0, 0);
                return c.getTime();
            } catch (Throwable t) {
                Log.w(Api.TAG, "hour '" + time + "' could not be read", t);
            }
        }
        return hourFromNow(i - Math.max(0, nowIndex()));
    }

    /** Used before the first forecast arrives, so the columns still carry real labels. */
    static Date hourFromNow(int offset) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.add(Calendar.HOUR_OF_DAY, offset);
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

    private static double num(JSONObject o, String name, double fallback) {
        return o == null || o.isNull(name) ? fallback : o.optDouble(name, fallback);
    }

    private static double item(JSONObject o, String name, int i, double fallback) {
        JSONArray a = arr(o, name);
        if (a == null || i < 0 || i >= a.length() || a.isNull(i)) return fallback;
        return a.optDouble(i, fallback);
    }
}
