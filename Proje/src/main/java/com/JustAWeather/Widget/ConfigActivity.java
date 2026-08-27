package com.JustAWeather.Widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Settings screen. Opened by the launcher while the widget is being placed, and again
 * whenever the widget is tapped. Started from the app drawer (no widget id) it edits
 * the defaults the next widget will start from.
 */
public class ConfigActivity extends Activity {

    private static final int[] PALETTE = {
            0x000000, 0xFFFFFF, 0x1A2330, 0x37474F, 0x546E7A,
            0x1565C0, 0x00897B, 0x2E7D32, 0xF9A825, 0xD84315,
            0xAD1457, 0x6A1B9A, 0x00B0FF, 0xFFD54F, 0xFF8A80
    };

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private Cfg cfg;
    private boolean ready;
    private boolean loading;

    private FrameLayout preview;
    private EditText lat, lon, label;
    private CheckBox cbF, cbRound, cbNow, cbLabel, cbIcon, cbRain, cbTime;
    private SeekBar sbAlpha, sbSize, sbDays, sbEvery;
    private TextView tvAlpha, tvSize, tvDays, tvEvery;
    private View[] bgSwatch, fgSwatch;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.config);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        boolean placed = Cfg.stored(this, widgetId);
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Backing out must leave no half placed widget behind.
            setResult(RESULT_CANCELED, new Intent()
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        }
        cfg = Cfg.load(this, widgetId);

        text(R.id.title, "🌤️ Just a Weather Widget");
        text(R.id.hLocation, "📍 Location");
        text(R.id.lLat, "Latitude (-90 to 90)");
        text(R.id.lLon, "Longitude (-180 to 180)");
        text(R.id.lLabel, "Name to show (optional)");
        text(R.id.hLook, "🎨 Look");
        text(R.id.lBg, "Background colour");
        text(R.id.lFg, "Text colour");
        text(R.id.hShow, "⚙️ Options");
        text(R.id.foot, "Weather from open-meteo.com - no account, no API key. "
                + "The last reading is kept, so the widget still shows something without internet.");

        preview = (FrameLayout) findViewById(R.id.preview);
        lat = (EditText) findViewById(R.id.lat);
        lon = (EditText) findViewById(R.id.lon);
        label = (EditText) findViewById(R.id.label);
        cbF = check(R.id.cbF, "Fahrenheit (°F) instead of Celsius");
        cbRound = check(R.id.cbRound, "Rounded corners");
        cbNow = check(R.id.cbNow, "Show the big reading for right now");
        cbLabel = check(R.id.cbLabel, "Show the name");
        cbIcon = check(R.id.cbIcon, "Show the weather emoji");
        cbRain = check(R.id.cbRain, "Show the rain chance");
        cbTime = check(R.id.cbTime, "Show the update time");
        sbAlpha = (SeekBar) findViewById(R.id.sbAlpha);
        sbSize = (SeekBar) findViewById(R.id.sbSize);
        sbDays = (SeekBar) findViewById(R.id.sbDays);
        sbEvery = (SeekBar) findViewById(R.id.sbEvery);
        tvAlpha = (TextView) findViewById(R.id.tvAlpha);
        tvSize = (TextView) findViewById(R.id.tvSize);
        tvDays = (TextView) findViewById(R.id.tvDays);
        tvEvery = (TextView) findViewById(R.id.tvEvery);

        lat.setText(cfg.lat);
        lon.setText(cfg.lon);
        label.setText(cfg.label);
        cbF.setChecked(cfg.fahrenheit);
        cbRound.setChecked(cfg.round);
        cbNow.setChecked(cfg.showNow);
        cbLabel.setChecked(cfg.showLabel);
        cbIcon.setChecked(cfg.showIcon);
        cbRain.setChecked(cfg.showRain);
        cbTime.setChecked(cfg.showTime);
        sbAlpha.setProgress(cfg.alpha);
        sbSize.setProgress(cfg.size);
        sbDays.setProgress(cfg.days - Cfg.MIN_DAYS);
        sbEvery.setProgress(cfg.every);

        bgSwatch = swatches((LinearLayout) findViewById(R.id.bgRow), true);
        fgSwatch = swatches((LinearLayout) findViewById(R.id.fgRow), false);

        TextWatcher watch = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { paint(); }
        };
        lat.addTextChangedListener(watch);
        lon.addTextChangedListener(watch);
        label.addTextChangedListener(watch);

        CompoundButton.OnCheckedChangeListener toggled = new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { paint(); }
        };
        cbF.setOnCheckedChangeListener(toggled);
        cbRound.setOnCheckedChangeListener(toggled);
        cbNow.setOnCheckedChangeListener(toggled);
        cbLabel.setOnCheckedChangeListener(toggled);
        cbIcon.setOnCheckedChangeListener(toggled);
        cbRain.setOnCheckedChangeListener(toggled);
        cbTime.setOnCheckedChangeListener(toggled);

        SeekBar.OnSeekBarChangeListener slid = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int v, boolean byUser) { paint(); }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        };
        sbAlpha.setOnSeekBarChangeListener(slid);
        sbSize.setOnSeekBarChangeListener(slid);
        sbDays.setOnSeekBarChangeListener(slid);
        sbEvery.setOnSeekBarChangeListener(slid);

        Button save = (Button) findViewById(R.id.save);
        save.setText(widgetId == AppWidgetManager.INVALID_APPWIDGET_ID
                ? "SAVE AS DEFAULT" : (placed ? "SAVE" : "ADD WIDGET"));
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            TextView hint = (TextView) findViewById(R.id.hint);
            hint.setText("No widget selected. Long press your home screen, pick Widgets, "
                    + "then Just a Weather Widget. What you save here is what the next widget starts with.");
            hint.setVisibility(View.VISIBLE);
        }

        preview.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadPreview(); }
        });

        ready = true;
        paint();
        loadPreview();
    }

    // ------------------------------------------------------------------ preview

    /** Reads the controls into cfg and redraws the preview with the real widget code. */
    private void paint() {
        if (!ready) return;
        cfg.lat = lat.getText().toString();
        cfg.lon = lon.getText().toString();
        cfg.label = label.getText().toString();
        cfg.fahrenheit = cbF.isChecked();
        cfg.round = cbRound.isChecked();
        cfg.showNow = cbNow.isChecked();
        cfg.showLabel = cbLabel.isChecked();
        cfg.showIcon = cbIcon.isChecked();
        cfg.showRain = cbRain.isChecked();
        cfg.showTime = cbTime.isChecked();
        cfg.alpha = sbAlpha.getProgress();
        cfg.size = sbSize.getProgress();
        cfg.days = sbDays.getProgress() + Cfg.MIN_DAYS;
        cfg.every = sbEvery.getProgress();

        tvAlpha.setText("Opacity: " + (cfg.alpha * 100 / 255) + "%");
        tvSize.setText("Text size: " + Cfg.SIZE_TXT[Cfg.clamp(cfg.size, 0, Cfg.SIZE_TXT.length - 1)]);
        tvDays.setText("Days in the forecast strip: " + cfg.days);
        tvEvery.setText("Update every: " + Cfg.EVERY_TXT[Cfg.clamp(cfg.every, 0, Cfg.EVERY_TXT.length - 1)]);

        for (int i = 0; i < bgSwatch.length; i++) mark(bgSwatch[i], PALETTE[i] == cfg.bg);
        for (int i = 0; i < fgSwatch.length; i++) mark(fgSwatch[i], PALETTE[i] == cfg.fg);

        Data d = Data.load(this, slot());
        note(loading ? "loading..."
                : d.has() ? "live weather - tap to refresh, tap a day for its hours"
                : "tap to load the real weather");

        try {
            RemoteViews rv = WeatherWidget.build(this, widgetId, cfg, d);
            View v = rv.apply(this, preview);
            v.setOnClickListener(null);          // the preview must not launch anything
            v.setClickable(false);
            preview.removeAllViews();
            preview.addView(v);
        } catch (Throwable t) {
            Log.e(Api.TAG, "preview could not be drawn", t);
            preview.removeAllViews();
            TextView fallback = new TextView(this);
            fallback.setText("(preview unavailable)");
            fallback.setGravity(Gravity.CENTER);
            preview.addView(fallback);
        }
    }

    /** Where this screen keeps its reading: the widget's own slot, or slot 0 for the defaults. */
    private int slot() {
        return widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ? 0 : widgetId;
    }

    private void note(String s) {
        ((TextView) findViewById(R.id.previewNote)).setText(s);
    }

    /**
     * Pulls the real weather for the coordinates that are typed in right now, so the
     * preview is the actual widget with actual numbers. Also serves as the refresh
     * that happens when the widget itself is tapped.
     */
    private void loadPreview() {
        if (loading) return;
        if (number(lat.getText().toString(), 90) == null
                || number(lon.getText().toString(), 180) == null) {
            note("enter the coordinates to load the real weather");
            return;
        }
        final Cfg snap = new Cfg();          // Api.fetch only needs these four
        snap.lat = cfg.lat;
        snap.lon = cfg.lon;
        snap.fahrenheit = cfg.fahrenheit;
        snap.days = cfg.days;
        final int slot = slot();
        final Context app = getApplicationContext();

        loading = true;
        note("loading...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Api.fetch(snap).save(app, slot);
                    if (widgetId > 0) WeatherWidget.render(app, widgetId);
                } catch (Throwable t) {
                    Log.w(Api.TAG, "preview fetch failed, staying with what we have", t);
                    Data.offline(app, slot);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        paint();
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ colours

    private View[] swatches(LinearLayout box, final boolean background) {
        View[] out = new View[PALETTE.length];
        LinearLayout row = null;
        for (int i = 0; i < PALETTE.length; i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            final int colour = PALETTE[i];
            TextView sw = new TextView(this);
            sw.setGravity(Gravity.CENTER);
            sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            sw.setBackgroundColor(0xFF000000 | colour);
            sw.setTextColor(bright(colour) ? 0xFF000000 : 0xFFFFFFFF);
            sw.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (background) cfg.bg = colour; else cfg.fg = colour;
                    paint();
                }
            });
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, dp(36), 1f);
            lp.setMargins(dp(2), dp(4), dp(2), 0);
            row.addView(sw, lp);
            out[i] = sw;
        }
        return out;
    }

    private static void mark(View swatch, boolean selected) {
        ((TextView) swatch).setText(selected ? "✓" : "");
    }

    private static boolean bright(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 140;
    }

    // ------------------------------------------------------------------ saving

    private void save() {
        paint();
        Double la = number(cfg.lat, 90), lo = number(cfg.lon, 180);
        if (la == null || lo == null) {
            Toast.makeText(this, "Latitude must be -90 to 90 and longitude -180 to 180",
                    Toast.LENGTH_LONG).show();
            return;
        }
        cfg.lat = String.valueOf(la);
        cfg.lon = String.valueOf(lo);
        cfg.label = cfg.label.trim();

        cfg.save(this, 0);                       // becomes the default for the next widget
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Toast.makeText(this, "Saved as default", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        cfg.save(this, widgetId);
        WeatherWidget.render(this, widgetId);
        WeatherWidget.refresh(this, new int[] { widgetId }, null);
        WeatherWidget.schedule(this);
        setResult(RESULT_OK, new Intent()
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }

    /** Parses a coordinate, accepting a comma as the decimal separator. */
    private static Double number(String s, double limit) {
        try {
            double v = Double.parseDouble(s.trim().replace(',', '.'));
            if (Double.isNaN(v) || Math.abs(v) > limit) return null;
            return v;
        } catch (Exception e) {
            Log.w(Api.TAG, "not a coordinate: " + s);
            return null;
        }
    }

    // ------------------------------------------------------------------ plumbing

    private void text(int id, String s) {
        ((TextView) findViewById(id)).setText(s);
    }

    private CheckBox check(int id, String s) {
        CheckBox b = (CheckBox) findViewById(id);
        b.setText(s);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /**
     * Keeps Settings > Storage at ~0: the app has nothing worth caching.
     * (see RAPOR.md > "Veri ve Onbellek")
     */
    @Override
    protected void onStop() {
        super.onStop();
        // Placement was backed out of: drop the reading fetched for a widget that never existed.
        if (isFinishing() && widgetId > 0 && !Cfg.stored(this, widgetId)) {
            SharedPreferences.Editor e = Cfg.prefs(this).edit();
            Data.clear(e, widgetId);
            e.commit();
        }
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
