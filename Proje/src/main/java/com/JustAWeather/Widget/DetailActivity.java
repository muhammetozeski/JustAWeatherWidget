package com.JustAWeather.Widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;

/**
 * Hour by hour view of one day, opened by tapping a day in the widget strip.
 * It reads the forecast the widget already stored, so it opens instantly and
 * works with no network; the header says how old the reading is.
 */
public class DetailActivity extends Activity {

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private int day;
    private Cfg cfg;
    private Data data;
    private boolean loading;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.detail);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            day = extras.getInt(WeatherWidget.EXTRA_DAY, 0);
        }
        cfg = Cfg.load(this, widgetId);
        data = Data.load(this, slot());

        findViewById(R.id.dSummary).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { reload(); }
        });
        paint();
    }

    private int slot() {
        return widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ? 0 : widgetId;
    }

    // ------------------------------------------------------------------ drawing

    private void paint() {
        boolean known = data.has();
        int dayCount = known ? data.dayCount() : 0;
        if (day >= dayCount) day = Math.max(0, dayCount - 1);

        Date date = known ? data.dayDate(day) : new Date();
        set(R.id.dTitle, known
                ? (day == 0 ? "Today" : day == 1 ? "Tomorrow"
                        : DateFormat.format("EEEE", date).toString())
                + " - " + DateFormat.getMediumDateFormat(this).format(date)
                : "No forecast yet");

        if (known) {
            String rain = data.dayRain(day) >= 0 ? "  " + Api.DROP + " " + data.dayRain(day) + "%" : "";
            set(R.id.dSummary, Api.emoji(data.dayCode(day), true) + "  "
                    + WeatherWidget.degrees(data.dayMax(day)) + " / "
                    + WeatherWidget.degrees(data.dayMin(day)) + "  "
                    + Api.describe(data.dayCode(day)) + rain);
        } else {
            set(R.id.dSummary, loading ? "loading..." : "Tap here to load the forecast");
        }

        String place = cfg.label.trim().length() > 0
                ? Api.PIN + " " + cfg.label.trim()
                : Api.PIN + " " + cfg.lat + ", " + cfg.lon;
        if (data.ts > 0) {
            place += "   " + (data.offline ? Api.OFFLINE : Api.CLOCK) + " "
                    + DateFormat.getTimeFormat(this).format(new Date(data.ts));
        }
        if (loading) place += "   " + "loading...";
        set(R.id.dPlace, place);

        days(dayCount);
        hours(known);
    }

    /** The day picker: same days as the widget strip, so any of them can be opened. */
    private void days(int dayCount) {
        LinearLayout box = (LinearLayout) findViewById(R.id.dDays);
        box.removeAllViews();
        for (int i = 0; i < dayCount; i++) {
            final int index = i;
            boolean on = i == day;
            TextView chip = new TextView(this);
            chip.setText(DateFormat.format("EEE", data.dayDate(i)).toString()
                    + "  " + WeatherWidget.degrees(data.dayMax(i)));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            chip.setTextColor(on ? 0xFFFFFFFF : 0xFF12263A);
            chip.setBackgroundColor(on ? 0xFF1B5E8C : 0xFFDDE7F0);
            chip.setPadding(dp(12), dp(8), dp(12), dp(8));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    day = index;
                    paint();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(6), 0);
            box.addView(chip, lp);
        }
    }

    private void hours(boolean known) {
        LinearLayout box = (LinearLayout) findViewById(R.id.dHours);
        box.removeAllViews();
        if (!known) return;

        int count = data.hourCount(day);
        for (int h = 0; h < count; h++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(7), dp(4), dp(7));
            row.setBackgroundColor((h & 1) == 0 ? 0x00000000 : 0x111B5E8C);

            int code = data.hourCode(day, h);
            boolean daylight = h >= 7 && h <= 19;
            int rain = data.hourRain(day, h);

            cell(row, DateFormat.getTimeFormat(this).format(data.hourDate(day, h)),
                    2.2f, 14, 0xFF44576A, Gravity.LEFT);
            cell(row, Api.emoji(code, daylight), 1f, 18, 0xFF12263A, Gravity.CENTER);
            cell(row, WeatherWidget.degrees(data.hourTemp(day, h)),
                    1.4f, 16, 0xFF12263A, Gravity.CENTER);
            cell(row, rain >= 0 ? Api.DROP + " " + rain + "%" : "",
                    1.6f, 14, 0xFF1B5E8C, Gravity.RIGHT);

            box.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private void cell(LinearLayout row, String s, float weight, int sp, int colour, int gravity) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setGravity(gravity | Gravity.CENTER_VERTICAL);
        t.setSingleLine(true);
        row.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, weight));
    }

    // ------------------------------------------------------------------ refresh

    /** Only used when the widget has nothing stored yet, or the user taps the summary. */
    private void reload() {
        if (loading) return;
        loading = true;
        paint();
        final Context app = getApplicationContext();
        final int slot = slot();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Api.fetch(cfg).save(app, slot);
                    if (widgetId > 0) WeatherWidget.render(app, widgetId);
                } catch (Throwable t) {
                    Log.w(Api.TAG, "detail refresh failed, staying with what we have", t);
                    Data.offline(app, slot);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        data = Data.load(DetailActivity.this, slot);
                        paint();
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ plumbing

    private void set(int id, String s) {
        ((TextView) findViewById(id)).setText(s);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onStop() {
        super.onStop();
        del(getCacheDir());
        try {
            del(getCodeCacheDir());       // API 21+
        } catch (Throwable t) {
            Log.w(Api.TAG, "no code cache directory on this Android version", t);
        }
    }

    private static void del(java.io.File f) {
        if (f == null) return;
        java.io.File[] kids = f.listFiles();
        if (kids != null) {
            for (java.io.File k : kids) del(k);
        }
        f.delete();
    }
}
