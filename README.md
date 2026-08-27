# Just a Weather Widget

![platform](https://img.shields.io/badge/platform-Android-3ddc84)
![minSdk](https://img.shields.io/badge/minSdk-16-blue)
![apk](https://img.shields.io/badge/APK-~31%20KB-brightgreen)
![api key](https://img.shields.io/badge/API%20key-none-success)
![language](https://img.shields.io/badge/language-Java-orange)

A home screen weather widget for Android. Temperature and rain chance, a forecast strip
for the next few days, and an hour by hour view when you tap a day. No account, no API
key, no permissions beyond internet.

The whole app is one widget plus two small screens. There is no dashboard, no login and
no ads - it shows the weather and gets out of the way.

<p align="center">
  <img src="docs/settings.png" alt="Settings screen with a live preview of the widget" width="320">
</p>

## Features

- **Coordinates you type in.** Latitude and longitude are entered while the widget is
  being placed, so it works anywhere without a location permission or a place database.
- **No API key.** Weather comes from [Open-Meteo](https://open-meteo.com), which needs no
  sign-up and no token. The settings screen says so at the bottom.
- **Works offline.** The whole forecast is kept on the phone and drawn again when the
  network is gone, marked with 📴 and the time it was fetched. A forecast kept over a night
  offline still lines up by date; it simply reaches fewer days ahead until the next update.
- **One download, every setting.** All 16 days and all 384 hours come down whatever the
  widget is set to show, so changing the settings or opening an hour list never waits for
  the network. It is deflated into the cache directory - 11.8 KB of JSON becomes 2.2 KB -
  with `java.util.zip`, so the compression adds nothing to the APK.
- **Days or hours.** Either a column per day - today optional, plus up to 15 days after it - or a column per hour, starting at the hour you are in or any number of hours later.
  Every column has the same shape: name, weather emoji, temperature, rain chance.
- **Tap a column for the hour by hour view.** Each column is a tap target the full height
  of the widget, so there is nothing to aim at. The screen lists every hour of that day with
  emoji, temperature and rain chance and lets you switch days; the settings are one button
  in its top right corner. Tapping the widget again while that screen is up refreshes.
- **The text sizes itself.** Font sizes come from the space the launcher gives the widget
  and from how many columns have to fit, so resizing the widget grows the text with it. The
  size setting is a multiplier on top of that.
- **Everything is configurable per widget**, with a live preview that is the real widget:
  background colour, opacity, text colour, text size, rounded or square corners, °C or °F,
  which parts to show, how many columns, and how often to update. Two widgets can watch two
  different places with two different looks.
- **Tiny and idle.** ~31 KB APK, and roughly the same installed, because it is plain Java on
  the core Android classes - no AndroidX, no Kotlin runtime, no support libraries. No service
  runs, nothing holds a wake lock, and an update that is not due does not touch the radio.

## How it works

| Piece | What it does |
|---|---|
| `WeatherWidget` | `AppWidgetProvider`: builds the `RemoteViews`, fills the strip, sizes the text, wires the taps, and keeps the update alarm |
| `ConfigActivity` | Settings, opened while placing the widget and from the button on the hour by hour screen. Draws the preview with the widget's own code, so the preview cannot drift from the real thing |
| `DetailActivity` | Hour by hour view of one day, with a day picker |
| `Api` | Open-Meteo request, one retry, and the weather code to emoji mapping |
| `Data` | Reading the forecast: which index is today, which is this hour, and the values behind them |
| `Store` | The cache file: `[version][fetched at][offline][deflate(json)]` |
| `Cfg` | Per widget settings; widget 0 holds the defaults a new widget starts from |
| `Junk` | Sweeps the code cache and anything stale, leaving the forecast alone |

Columns are built at runtime with `RemoteViews.addView`, one `day.xml` per column, so the
count is a setting rather than something baked into a layout. `onAppWidgetOptionsChanged`
redraws when the widget is resized, since the text size is derived from the widget's size.

The forecast is stored as the raw Open-Meteo response, deflated, in the cache directory -
not in the app's data, since it can always be downloaded again. The widget, the day details
and the settings preview all read that one copy, and all of them keep working with no network.

Updates run from a single `AlarmManager` alarm at the shortest interval any widget asked
for (`updatePeriodMillis` is 0, so this is the only clock). It is `RTC` and inexact on
purpose: it never wakes the device and the system may fold it into a batch it was going to
run anyway. A trigger that arrives while the forecast is still young does not open a socket
at all. Fetching happens on a background thread inside `goAsync()`, with two five second
attempts so it stays inside the time a receiver may hold; a failure never blanks the widget,
it only flags the forecast as offline.

Double tapping the widget refreshes: `DetailActivity` is `singleTop`, so the second tap
lands in `onNewIntent` instead of starting another copy. That way the first tap stays
instant - there is no delay spent waiting to see whether a second one is coming.

## Building

The project builds with the portable toolchain in
[`SmallestApk`](https://github.com/muhammetozeski) (JDK + Android SDK + Gradle in one folder,
no internet needed):

```powershell
pwsh -File .\Derle.ps1
```

The signed APK lands in `Publish\JustAWeatherWidget.apk`. `Derle.ps1` uses a shared `API`
folder for the toolchain; point `$API_DIR` at your own copy, or drop the line to use an
`API` folder next to the script.

To build with a normal Android Studio setup instead, open `Proje` as the project. Two
things are deliberate and worth keeping:

- `android:debuggable="true"` in the manifest. Android then runs the app straight from the
  APK (`run-from-apk`) and never writes `oat/base.odex`, `base.vdex` or `base.art`, which is
  what keeps the installed size at roughly the APK size.
- `-x lintVitalRelease` when assembling release, because that lint check fails on a
  debuggable release build.

`Derle.ps1` also repacks the unsigned APK before signing: `resources.arsc` stays stored and
everything else is deflated at maximum, which beats Gradle's own compression and drops the
`app-metadata.properties` Gradle adds for tooling. `dependenciesInfo` is off for the same
reason - there are no dependencies to describe.

```powershell
gradle -p Proje assembleRelease -x lintVitalRelease
```

## Installing

```powershell
adb install -r "Publish\JustAWeatherWidget.apk"
```

Then long press the home screen, pick **Widgets**, and choose **Just a Weather Widget**.
The settings open while it is being placed. Opening the app from the launcher edits the
defaults for the next widget instead.

## Data

Weather data from [Open-Meteo](https://open-meteo.com) (free for non-commercial use, no key).
Only latitude, longitude, unit and day count are sent. Nothing else leaves the phone, and the
app stores nothing but its own settings and the last forecast.

**How far ahead it goes.** `/v1/forecast` allows 16 days and refuses 17 (*"Allowed range 0 to
16"*), so the settings stop at today plus 15. All 16 days come with full hourly detail - 384
hours, each with a temperature, a weather code and a rain chance - in an ~12 KB response, which
is why the whole forecast is kept as one stored blob. Longer ranges exist only on Open-Meteo's
seasonal endpoint, and it drops everything this widget shows: daily values only, no weather
code and no rain chance.


