# CooApp

An unofficial Material You Android app for SZTE CooSpace ([unics.szte.hu](https://unics.szte.hu/)).

A native Material 3 interface built around the real CooSpace site:

- **Material You:** colours come from your wallpaper (Android 12+), with light / dark / system theme and a themed launcher icon
- **Home:** pinned courses and pages, plus recently visited pages
- **CooSpace tab:** the site itself, with back gesture, refresh, pin, share and open-in-browser
- **Restyled pages:** CooSpace pages use your Material colours, with a proper dark mode (can be turned off)
- **Files:** downloads go to `Downloads/CooSpace/`, and assignment uploads use the system file picker
- **Links:** CooSpace links from other apps open in the app
- **Languages:** English and Hungarian

> Not affiliated with or endorsed by the University of Szeged. You only ever type your password
> into the real CooSpace sign-in page, and the app never stores it.

## Build

Requires the Android SDK (compileSdk 37.2) and JDK 17+.

    ./gradlew assembleRelease
    adb install -r app/build/outputs/apk/release/app-release.apk

The release build is signed with the debug key for convenience. Set up your own signing config
before distributing it.

## Code map

| Path | What it does |
| --- | --- |
| `web/CooWebController.kt` | WebView, session cookies, sign-in detection, downloads, uploads, external links |
| `web/PageStyle.kt` | Material CSS and the dark-mode recolouring script injected into CooSpace pages |
| `ui/screens/` | Home, CooSpace (browse) and Settings screens |
| `ui/theme/Theme.kt` | Dynamic colour and fallback palettes |
| `data/AppSettings.kt` | Settings, pins and recent pages (SharedPreferences) |
