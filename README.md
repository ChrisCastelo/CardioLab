# Cardio Lab — treadmill step experiment

**Continuing at the treadmill? Start with [the handoff and next-step plan](docs/TREADMILL-HANDOFF.md).** It records the installed build, verified findings, local backup locations, capture commands, open limitations, and the staged control/launcher rollout.

## Echelon console preview (0.4)

The default Cardio Lab launcher entry now opens the native landscape console approved in the interactive mockup: incline shortcuts on the left, speed shortcuts on the right, Sensors/Audio on the far left of the header, centered 400 m track, session controls on the far right, and a centered timer in the bottom stats row.

**This release does not open the treadmill serial port or send any treadmill commands.** Quick start offers an explicit preview mode. Only speed, incline, distance, laps, and interval progression are simulated; steps and heart rate come from the existing real accelerometer detector and standard BLE client. Missing sensor readings display a dash. Preview sessions checkpoint locally and recover paused; they are not added to the saved workout history.

The new foreground service owns sensing while a video app is open, as required for continuous accelerometer access on Android 9. Four separate overlay windows leave the middle of the screen touchable. Open YouTube requests overlay permission and launches an installed app/browser, with an isolated YouTube WebView fallback. The fallback has no JavaScript bridge or file access; Google sign-in requires an installed browser/YouTube app and is not verified yet. Bluetooth speaker pairing/reconnection uses Android settings and depends on the speaker/OS.

Sensors includes paired Garmin selection, the existing two-stage vibration calibration, Android settings, preview mode, and access to the original sensor lab and saved sessions. On this fixed Echelon mount, calibrate again and validate with an independent counted walk. Selecting the old lab ends the console preview and stops the new service so the two sensor/HR owners do not run concurrently.

CardioLab declares the Android HOME category but **does not change the default launcher**, uninstall FitOS/Echelon, or register a boot receiver. Keep the stock apps for the next phase: capture controller identity, units, limits, safety states, telemetry, and physical button events while using the original harness. Physical Start/Stop wake/control integration is not implemented. Only switch HOME after verification and a tested rollback route. See [console verification and rollout](docs/console-verification.md).

Build and install as below, then launch `com.cardio.lab/.ConsoleActivity`. The original `MainActivity` is now internal and opens from **Sensors → Sensor lab and saved sessions**.

## Original sensor lab (0.3)

An Android prototype that treats a phone mounted on the **fixed part of a treadmill deck** as a vibration sensor. Version 0.3 adds guided two-stage calibration to the landscape workout screen, standard BLE heart rate, and local SQLite session history. The step counter remains experimental.

## First test

1. Open **Cardio Lab**. Secure the phone on a fixed deck edge, outside the moving belt and your foot path. Its placement must stay the same throughout calibration and testing.
2. Enable heart-rate broadcasting on the Garmin. Tap **Connect sensor**, allow Nearby Devices, and select **Instinct Solar** from paired devices. The source selection is remembered. Live BPM and its graph can be previewed before recording.
3. Open **Sensor setup → Calibrate · 2 stages**. At your test speed, record the empty belt with nobody stepping: a 3-second countdown, then 6 seconds of noise. Keep speed and phone position fixed. Start the walking stage, settle into your pace during its 5-second countdown, then count **every foot contact, both feet**, during the 30-second recording. Enter your count and review the suggested threshold before applying it. Countdown/start/end sounds use media volume. A helper can count for you.
4. Tap **Start session** for an empty-belt test. Any counted steps are false positives. Tap **Pause** to suspend active time, steps, and the session HR average; the sensor previews remain live. Tap **Resume** to continue.
5. Tap **Stop**, then **Save session**, **Discard**, or **Continue session**. Start a new session, manually count 100 individual foot contacts at the same speed, and compare with the displayed total. The detector has a one-second filter warmup after starting/resuming; the timer starts immediately. A helper or video makes boundary comparisons more reliable.
6. Repeat three times, recording the manual reference separately. Open **Sessions** to review dates, active durations, step totals, average HR and saved graphs. Test jogging/running separately and recalibrate after changing speed or phone position.

If the app misses steps, pause and move the Sensor setup threshold slider left. If it counts too many, move right. **Restore 0.120** restores the original default. Use a new session after threshold changes for comparable results. The live graph shows filtered vibration and a dashed threshold. Calibration fits the reference walk; a separate 100-step test is needed to assess accuracy.

The app stays in landscape and keeps the screen awake during a session or calibration. Leaving the app or locking the phone pauses recording and closes its BLE connection. Returning reconnects to the selected sensor; resume recording explicitly. No background workout service is included.

## What is being measured

- `TYPE_ACCELEROMETER`, requested at 100 Hz, with the actual observed rate displayed.
- Magnitude of X/Y/Z acceleration; an approximately 0.5 Hz gravity/drift filter followed by an 8 Hz low-pass.
- Local positive peaks above an adjustable threshold, with hysteresis and a 250 ms minimum separation (a maximum of 240 detected events/minute).
- Cadence from recent event intervals, cleared after two seconds without an event.
- Calibration first sets a search floor at 1.3 × the 99th percentile of absolute filtered empty-belt vibration, with a 0.025 m/s² minimum. It then replays both raw recordings through the unchanged production detector over 321 threshold candidates. A suggestion must produce zero steps on the recorded empty belt and be within 5% of the walking reference. Within the widest run of thresholds inside that 5%, the one closest to the reference (nearest the run's centre) is selected. Interrupted, flat, short or incompatible recordings are rejected. The user explicitly applies the result; cancellation, failure, or leaving the app preserves the prior threshold. See [calibration details](docs/two-stage-calibration.md).

This is an **experimental peak counter**, not a validated pedometer. Motor vibration, mounting resonance, harmonics and deck bounce can create extra peaks; weak/unequal foot strikes can be missed. It has no treadmill speed input and cannot automatically know that the belt stopped. The Android built-in step counter is intentionally not the measurement source: this experiment concerns vibration at a fixed mount, rather than a carried phone. Real treadmill recordings and manual ground truth are required before improving the algorithm or claiming accuracy.

## Recordings

SQLite database `workouts.db` stores session date, active duration, total steps, HR total/count, threshold, source name, and timestamped graph samples. Filtered vibration, cumulative steps, cadence and current valid HR are sampled at up to 10 Hz for session graphs. Live accelerometer processing still runs at the delivered sensor rate. Saved detail graphs are reduced to about 1,200 time buckets for display; all stored samples remain in the database. The session average is the arithmetic mean of valid BLE HR notifications received while recording, not a time-weighted average.

Database transactions run on a serial background worker and checkpoint approximately every second. An unfinished session recovers paused after relaunch; abrupt termination can lose the latest uncommitted interval. Saving retains the session and its graphs; discarding deletes that draft and its samples transactionally. No CSV export appears in this version. Existing 0.1 CSV files are left intact, but are not imported into session history. Data is local, excluded from Android backup, and removed by uninstall/app-data clearing. There is no automatic retention limit.

BLE uses standard service `180D` / measurement `2A37`, paired-device selection, and Nearby Devices permission on Android 12+. No Internet, location, scanning, or account credentials are required. Stale HR becomes a dash after five seconds; missing readings are excluded from averages and recorded as gaps. The client retries disconnected links with backoff and retries an unresponsive stream after 20 seconds. The ring's custom protocol is not implemented.

## Connected phone inspected

On 2026-09-28 the connected device reported model **2311DRK48G**, Android **14**, with an **ST lsm6dsoq_acc** accelerometer (hardware rate range 12.5–400 Hz), plus linear acceleration, gyroscope, step detector and step counter sensors. The app only requests 100 Hz and shows the actual delivered rate.

## Garmin heart-rate diagnosis

On 2026-09-28 our separate diagnostic app received **30 standard Bluetooth heart-rate notifications in 20 seconds** directly from the user's **Instinct Solar**, connected to the Samsung S24. Standard service `180D` and characteristic `2A37` were present, and notification subscription succeeded. See the [measured Garmin findings](docs/garmin-diagnostic.md).

This corrects the earlier assumption that this setup required an ANT+ receiver. Garmin's original documentation describes ANT+ broadcasting, but the tested watch also delivered BPM over BLE in its current configuration. Version 0.2 integrates this standard feed into Cardio Lab. Automatic workout broadcasting, longer sessions, and the Xiaomi treadmill phone require workout testing.

- [Garmin Instinct Solar specification](https://www.garmin.com.hk/products/intosports/instinct-solar-graphite/)
- [Garmin broadcast-during-activity instructions](https://www8.garmin.com/manuals/webhelp/GUID-A298EB1C-21D9-430F-8D06-A2CC74E5D5E9/EN-US/GUID-57A88A77-3813-4E79-9DB1-FC95B06F01BA.html)
- [Android motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)
- [Android keep-screen-on behavior](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on)

## Build and check

The separate [BLE diagnostic and Ring AIR findings](docs/ultrahuman-diagnostic.md) document the S24 connectivity test. Direct connection succeeded; the ring did not expose standard Bluetooth heart-rate measurements in the tested configuration. Live ring HR is not integrated into Cardio Lab yet.

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
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s DEVICE_SERIAL shell am start -n com.cardio.lab/.ConsoleActivity
```

The automated detector tests cover stationary/sub-threshold noise, walking/running rates, irregular sample timing, orientation, preview/calibration exclusion, restart settling, invalid samples, refractory spacing, and reset. These are synthetic checks, not measured treadmill accuracy.
