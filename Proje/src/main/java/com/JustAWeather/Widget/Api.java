package com.JustAWeather.Widget;

import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Open-Meteo: free, no API key, no sign-up.
 * https://api.open-meteo.com/v1/forecast
 */
class Api {

    static final String TAG = "JustAWeather";

    /**
     * The whole range the API offers. Asking for 17 is refused ("Allowed range 0 to 16"),
     * and 16 days with every hour is only ~12 KB, so the widget always downloads the lot
     * whatever it is set to show: changing the settings or opening an hour list then
     * never needs the network, and one download serves every widget setting.
     */
    static final int FORECAST_DAYS = 16;

    // Two tries at five seconds each stay inside the time a broadcast receiver may hold.
    private static final int TIMEOUT_MS = 5000;
    private static final int ATTEMPTS = 2;

    /** Sources are UTF-8; build.gradle pins the compiler encoding so these survive. */
    static final String PIN = "📍";                   // pin
    static final String DROP = "💧";                  // droplet
    static final String CLOCK = "🕒";                 // clock
    static final String OFFLINE = "📴";               // phone off
    static final String UNKNOWN = "📡";               // waiting for the first reading
    private static final String SUN = "☀️";           // sun
    private static final String MOON = "🌙";          // crescent moon
    private static final String SUN_CLOUD = "🌤️";   // sun behind small cloud
    private static final String PART_CLOUD = "⛅";          // sun behind cloud
    private static final String CLOUD = "☁️";         // cloud
    private static final String FOG = "🌫️";     // fog
    private static final String DRIZZLE = "🌦️"; // sun behind rain cloud
    private static final String RAIN = "🌧️";    // rain cloud
    private static final String SNOW = "❄️";          // snowflake
    private static final String STORM = "⛈️";         // thunder cloud
    private static final String THERMO = "🌡️";  // thermometer

    /** Current conditions plus a daily and an hourly forecast for the days asked for. */
    static Data fetch(Cfg c) throws Exception {
        String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + number(c.lat)
                + "&longitude=" + number(c.lon)
                + "&current=temperature_2m,weather_code,is_day"
                + "&hourly=temperature_2m,weather_code,precipitation_probability"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&forecast_days=" + FORECAST_DAYS
                + "&timezone=auto"
                + (c.fahrenheit ? "&temperature_unit=fahrenheit" : "");

        Exception last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                String body = get(url);
                if (new JSONObject(body).optJSONObject("current") == null) {
                    throw new Exception("unexpected response: " + head(body));
                }
                Data d = new Data();
                d.json = body;
                d.ts = System.currentTimeMillis();
                return d;
            } catch (Exception e) {
                last = e;
                Log.w(TAG, "attempt " + attempt + "/" + ATTEMPTS + " failed: " + e);
                if (attempt < ATTEMPTS) {
                    // A phone waking from sleep often loses the first request while the
                    // radio comes up; one short retry catches that case.
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw last != null ? last : new Exception("no response");
    }

    /** Accepts "41,0082" as well and always writes the dot form the API expects. */
    private static String number(String s) {
        return String.valueOf(Double.parseDouble(s.trim().replace(',', '.')));
    }

    private static String head(String s) {
        return s == null ? "null" : s.substring(0, Math.min(120, s.length()));
    }

    private static String get(String url) throws Exception {
        HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection();
        try {
            cn.setConnectTimeout(TIMEOUT_MS);
            cn.setReadTimeout(TIMEOUT_MS);
            cn.setRequestProperty("Accept", "application/json");
            cn.setRequestProperty("User-Agent", "JustAWeatherWidget");
            int status = cn.getResponseCode();
            if (status != 200) throw new Exception("HTTP " + status);

            InputStream in = cn.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return new String(out.toByteArray(), "UTF-8");
            } finally {
                try {
                    in.close();
                } catch (Throwable t) {
                    Log.w(TAG, "closing the response stream failed", t);
                }
            }
        } finally {
            try {
                cn.disconnect();
            } catch (Throwable t) {
                Log.w(TAG, "disconnect failed", t);
            }
        }
    }

    /** WMO weather code -> emoji. */
    static String emoji(int code, boolean daylight) {
        switch (code) {
            case 0:
                return daylight ? SUN : MOON;
            case 1:
                return daylight ? SUN_CLOUD : MOON;
            case 2:
                return PART_CLOUD;
            case 3:
                return CLOUD;
            case 45:
            case 48:
                return FOG;
            case 51:
            case 53:
            case 55:
            case 56:
            case 57:
                return DRIZZLE;
            case 61:
            case 63:
            case 65:
            case 66:
            case 67:
            case 80:
            case 81:
            case 82:
                return RAIN;
            case 71:
            case 73:
            case 75:
            case 77:
            case 85:
            case 86:
                return SNOW;
            case 95:
            case 96:
            case 99:
                return STORM;
            default:
                return THERMO;
        }
    }

    /** Plain wording for the detail screen. */
    static String describe(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mainly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: case 63: case 65: return "Rain";
            case 66: case 67: return "Freezing rain";
            case 71: case 73: case 75: return "Snow";
            case 77: return "Snow grains";
            case 80: case 81: case 82: return "Rain showers";
            case 85: case 86: return "Snow showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm with hail";
            default: return "";
        }
    }
}
