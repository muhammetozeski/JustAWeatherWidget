package com.JustAWeather.Widget;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The app is English whatever language the phone is in, so day and month names are
 * formatted with Locale.ENGLISH instead of the device locale. The clock still follows
 * the phone's 12 / 24 hour setting, because that is a preference and not a language.
 */
class Fmt {

    static String weekdayShort(Date d) {
        return format("EEE", d);
    }

    static String weekdayLong(Date d) {
        return format("EEEE", d);
    }

    static String date(Date d) {
        return format("d MMMM yyyy", d);
    }

    static String time(Context ctx, Date d) {
        boolean h24 = true;
        try {
            h24 = DateFormat.is24HourFormat(ctx);
        } catch (Throwable t) {
            Log.w(Api.TAG, "clock preference could not be read, using 24 hour", t);
        }
        return format(h24 ? "HH:mm" : "h:mm a", d);
    }

    private static String format(String pattern, Date d) {
        try {
            return new SimpleDateFormat(pattern, Locale.ENGLISH).format(d);
        } catch (Throwable t) {
            Log.w(Api.TAG, "could not format a date with '" + pattern + "'", t);
            return "";
        }
    }
}
