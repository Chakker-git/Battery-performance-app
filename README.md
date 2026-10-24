# Battery Check

Native Android app (Kotlin, Jetpack Compose) that measures your phone's battery behaviour and suggests fixes.

## What it measures
- Level, temperature, voltage, health, current draw, charge cycles (Android 14+), estimated vs. rated capacity
- Drain rate in %/hour: overall, screen-off (standby) and screen-on, from samples logged every ~15 min by WorkManager
- 24 h battery graph
- Screen time per app (needs Usage access) as a proxy for per-app drain, since Android hides real per-app battery stats from third-party apps
- Display, location, Bluetooth, Data Saver and Battery Saver settings

## What it recommends
`Analyzer.recommend()` turns the readings into ranked tips (High/Medium/Low) with shortcuts to the relevant Settings screen, and a 0-100 score.

## Build
Open in Android Studio (Ladybug or newer, JDK 17) and run, or `./gradlew assembleDebug`. Requires the Android SDK (compileSdk 35); minSdk 28.

## Limits
- Drain numbers need several hours of data after install.
- Rated capacity uses a hidden API and is shown only if the device exposes it.
- Current-draw units/sign vary by manufacturer; values are normalised to mA.
