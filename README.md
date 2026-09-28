# Cardio Lab — treadmill step experiment

An Android prototype that treats a phone mounted on the **fixed part of a treadmill deck** as a vibration sensor. Version 0.1 focuses on testing whether foot strikes are distinguishable on your current treadmill. It does not connect to the treadmill or Garmin yet.

## First test

1. Open **Cardio Lab**. Secure the phone on a fixed deck edge, outside the moving belt and your foot path. Its placement must stay the same throughout calibration and testing.
2. Set the treadmill to your test speed. With nobody stepping on the belt, tap **Calibrate → Begin**. The app measures six seconds of baseline vibration, excluding the first second of settling.
3. Tap **Start test**, wait for the three-second countdown, and first run an **empty-belt test for 30 seconds**. Pause, then use **Compare count → 0**. Any detected steps are false positives.
4. Tap **New test**. At the same belt speed, start another test, wait for the countdown, then walk and manually count **100 individual foot contacts** (both feet). Pause immediately after the last step. Enter **100** in Compare count. Starting/stopping and reaching for the phone may introduce boundary errors; a helper or recorded video makes the comparison more reliable.
5. Repeat three times. Use **Export CSV** to save each recording. Test jogging/running separately, recalibrating with the empty belt at each new speed and after any placement change.

If the app misses steps, pause and move the threshold slider left. If it counts too many, move right. Use a new test after changing the threshold so comparisons are easy to interpret. The graph shows the filtered vibration signal, a dashed detection threshold, and markers on detected events. Automatic calibration sets a starting threshold; it does not establish accuracy.

The screen stays awake during a test or calibration. Leaving the app, locking the phone, or opening another activity pauses a test. Rotation preserves the active session; the interface scrolls on small or landscape screens. No background workout service is included.

## What is being measured

- `TYPE_ACCELEROMETER`, requested at 100 Hz, with the actual observed rate displayed.
- Magnitude of X/Y/Z acceleration; an approximately 0.5 Hz gravity/drift filter followed by an 8 Hz low-pass.
- Local positive peaks above an adjustable threshold, with hysteresis and a 250 ms minimum separation (a maximum of 240 detected events/minute).
- Cadence from recent event intervals, cleared after two seconds without an event.
- Calibration threshold: 1.7 × the 99th percentile of absolute filtered baseline vibration, with a 0.025 m/s² minimum.

This is an **experimental peak counter**, not a validated pedometer. Motor vibration, mounting resonance, harmonics and deck bounce can create extra peaks; weak/unequal foot strikes can be missed. It has no treadmill speed input and cannot automatically know that the belt stopped. The Android built-in step counter is intentionally not the measurement source: this experiment concerns vibration at a fixed mount, rather than a carried phone. Real treadmill recordings and manual ground truth are required before improving the algorithm or claiming accuracy.

## Recordings

CSV files are stored privately on the phone, with an export picker that includes previous tests. Files have metadata/comment lines beginning with `#`, then timestamped raw X/Y/Z samples, filtered signal, threshold, step marker, cumulative steps, active time, and `settling`/`counting` phase. Ignore comment lines when loading the CSV in analysis tools. Pause/resume and manual reference counts are comment records. A detected peak is reported on the following sample when the signal begins falling.

Writes are batched off the UI thread. Pausing flushes queued data, and exports wait behind queued writes. Normal pause/relaunch restores the displayed session. Abrupt force-stop or power loss can lose the last unwritten batch and leave the displayed count at the last saved pause; preserve/export the recording and start a new test in that case. CSV files remain until app data is cleared or the app is uninstalled; there is no automatic retention limit in this prototype. No Internet, Bluetooth, location, activity-recognition or shared-storage permission is requested.

## Connected phone inspected

On 2026-09-28 the connected device reported model **2311DRK48G**, Android **14**, with an **ST lsm6dsoq_acc** accelerometer (hardware rate range 12.5–400 Hz), plus linear acceleration, gyroscope, step detector and step counter sensors. The app only requests 100 Hz and shows the actual delivered rate.

## Garmin, later

The original **Instinct Solar** documents **ANT+ heart-rate broadcasting**. Bluetooth pairing with Garmin Connect is not evidence of Bluetooth Heart Rate Service broadcasting. The phone advertised Bluetooth/BLE but no ANT+ feature in the inspected system feature list; that alone is not a definitive hardware compatibility test. We would need to investigate an ANT+ receiver/service path or use a BLE-compatible heart-rate source before adding live heart rate.

- [Garmin Instinct Solar specification](https://www.garmin.com.hk/products/intosports/instinct-solar-graphite/)
- [Garmin broadcast-during-activity instructions](https://www8.garmin.com/manuals/webhelp/GUID-A298EB1C-21D9-430F-8D06-A2CC74E5D5E9/EN-US/GUID-57A88A77-3813-4E79-9DB1-FC95B06F01BA.html)
- [Android motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)
- [Android keep-screen-on behavior](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on)

## Build and check

Native Java app, no third-party runtime dependencies. Open this directory in Android Studio. Gradle wrapper 8.14.3, Android Gradle Plugin 8.13.0, JDK 17, compile SDK 36. Target SDK 34 matches the current test phone; revisit the target and window insets before publishing or testing newer Android releases.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:lintDebug
.\test.ps1
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
# Replace DEVICE_SERIAL with the intended phone, then approve its USB installation prompt.
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s DEVICE_SERIAL install -r .\app\build\outputs\apk\debug\app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s DEVICE_SERIAL shell am start -n com.cardio.lab/.MainActivity
```

The automated detector tests cover stationary/sub-threshold noise, walking/running rates, irregular sample timing, orientation, preview/calibration exclusion, restart settling, invalid samples, refractory spacing, and reset. These are synthetic checks, not measured treadmill accuracy.
