# CooApp (unofficial Android client for CooSpace)

Material You (Material 3 + dynamic color) client for SZTE's CooSpace 5.

> **Note:** CooApp is built for SZTE (University of Szeged, https://unics.szte.hu/). You can change
> the server address in Settings, but the page styling and sign-in detection were written with SZTE's
> CooSpace in mind, so other instances may look or behave a bit off.

This is an unofficial app. It is not made by or affiliated with the University of Szeged or the makers of CooSpace.

## Install

Download the latest `CooApp-X.Y.Z.apk` from the [Releases](https://github.com/Chibisueru/CooApp/releases)
page and open it on your phone (allow installing from unknown sources when asked). Or with adb:

    adb install -r CooApp-1.0.0.apk

Requires Android 10 or newer. Dynamic (wallpaper) colours need Android 12 or newer.

## Features

- **Sign in** on the real CooSpace / SZTE sign-in page inside the app. The session is remembered, and the app never sees or stores your password.
- **Home:** pinned courses and pages, recently visited pages, and an "Open CooSpace" card that hides once you're signed in
- **CooSpace:** the full site in an in-app WebView, with back gesture, refresh, pin, share and open in browser
- **Restyled pages:** CooSpace pages use your Material colours, rounded inputs and buttons, and a proper dark mode
- **Files:** downloads go to `Downloads/CooSpace/` with a notification, and assignment uploads use the system file picker
- **Links:** CooSpace links from other apps open in CooApp
- **Settings:** light / dark / system theme, dynamic colour, page restyling on/off, page text size, server address, sign out
- **Languages:** English and Hungarian, plus a themed (monochrome) launcher icon

## Build

    ./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
    ./gradlew installDebug       # to a connected phone (USB debugging on)
    ./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk

Needs the Android SDK (compileSdk 37.2) and JDK 17+. Release builds are currently signed with the
debug key, so anyone can build an installable APK. Releases signed with a different key will need
an uninstall first.

## How it talks to CooSpace

CooSpace has no public API, so CooApp doesn't scrape or call one. It's a native Material 3 shell
around the real website. The site runs in a WebView that keeps its own cookies, so signing in,
uploads and everything else work exactly as in a browser.

Two scripts are injected into each CooSpace page (`web/PageStyle.kt`):

- **Material stylesheet:** maps the current colour scheme onto links, form buttons, inputs and
  tables. Selectors are kept generic so they survive markup changes.
- **Dark-mode recolouring:** reads each element's real background colour and swaps light
  backgrounds for the matching M3 surface tone. Floating panels like the sidebar get a higher tone,
  and semi-transparent layers (such as the dimming behind the sidebar) keep their transparency. A
  MutationObserver handles content the site adds later. Turning restyling off restores the original look.

Sign-in state is guessed from the loaded page (`web/CooWebController.kt`). A logout link means
signed in, and a visible password field means signed out.

If a page looks wrong, turn off **Settings → Restyle CooSpace pages** to check whether the styling is
the cause. Then adjust the matching rule in `web/PageStyle.kt`, or open an issue with a screenshot
and the element's HTML.

## Code map

| Path | What it does |
| --- | --- |
| `web/CooWebController.kt` | WebView, cookies, sign-in detection, downloads, uploads, external links |
| `web/PageStyle.kt` | Material CSS and the dark-mode recolouring script |
| `ui/screens/` | Home, CooSpace (browse) and Settings screens |
| `ui/theme/Theme.kt` | Dynamic colour and fallback palettes |
| `data/AppSettings.kt` | Settings, pins and recent pages (SharedPreferences) |
