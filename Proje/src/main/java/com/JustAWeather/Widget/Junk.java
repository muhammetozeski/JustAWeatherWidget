package com.JustAWeather.Widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;

/**
 * Android leaves things behind that this app has no use for: the code cache the runtime
 * fills in, and any temp file a half finished write left about. The cached forecast is
 * worth keeping, so this sweeps around it instead of emptying the cache directory.
 *
 * Called when a screen goes away, which keeps Settings > Storage at about nothing.
 */
class Junk {

    static void sweep(Context c) {
        try {
            wipe(c.getCodeCacheDir());          // API 21+
        } catch (Throwable t) {
            Log.w(Api.TAG, "no code cache directory on this Android version", t);
        }
        purgeOldPrefs(c);
        try {
            File[] kids = c.getCacheDir().listFiles();
            if (kids == null) return;
            int[] live = WeatherWidget.ids(c);
            for (File f : kids) {
                if (f.isDirectory() || f.getName().endsWith(".tmp")) {
                    wipe(f);
                } else if (!wanted(f.getName(), live) && !f.delete()) {
                    Log.w(Api.TAG, "stale cache file stays behind: " + f.getName());
                }
            }
        } catch (Throwable t) {
            Log.w(Api.TAG, "the cache directory could not be swept", t);
        }
    }

    /** f&lt;id&gt;.z belongs to a widget that is still on a home screen, or to the settings (id 0). */
    private static boolean wanted(String name, int[] live) {
        if (!name.startsWith("f") || !name.endsWith(".z")) return false;
        try {
            int id = Integer.parseInt(name.substring(1, name.length() - 2));
            if (id == 0) return true;
            for (int live1 : live) if (live1 == id) return true;
            return false;
        } catch (Throwable t) {
            Log.w(Api.TAG, "unexpected cache file name: " + name, t);
            return true;                        // leave anything unrecognised alone
        }
    }

    /**
     * An earlier build kept the forecast in the preferences file, which is the app's data
     * rather than its cache. Those entries are dead weight now, so they go.
     */
    private static void purgeOldPrefs(Context c) {
        try {
            SharedPreferences p = Cfg.prefs(c);
            SharedPreferences.Editor e = null;
            for (String key : p.getAll().keySet()) {
                if (key.endsWith("_json") || key.endsWith("_ts") || key.endsWith("_off")
                        || key.endsWith("_dt") || key.endsWith("_dr") || key.endsWith("_dc")
                        || key.endsWith("_dd") || key.endsWith("_dts") || key.endsWith("_doff")
                        || key.endsWith("_sn")) {
                    if (e == null) e = p.edit();
                    e.remove(key);
                }
            }
            if (e != null) {
                e.apply();
                Log.i(Api.TAG, "forecast entries left in the settings file were removed");
            }
        } catch (Throwable t) {
            Log.w(Api.TAG, "old settings entries could not be cleaned up", t);
        }
    }

    private static void wipe(File f) {
        if (f == null) return;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) wipe(k);
        }
        if (!f.delete()) Log.w(Api.TAG, "could not remove " + f.getName());
    }
}
