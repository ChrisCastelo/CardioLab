# Echelon native console — 2026-10-04

## Scope

Version 0.4-console-preview installs alongside the existing FitOS and Echelon packages. The implementation retains the vibration detector, calibration algorithm, BLE HR client, and old sensor lab/history. Console sensing runs in a foreground service with a visible ongoing notification. UI and model use the latest approved arrangement; the older widget-state description saying “speed left” was stale and was not applied.

No serial transport, motion commands, automatic treadmill startup, boot receiver, stock-app uninstall, root action, or firmware change was made. The console model rejects starting unless explicit preview mode is enabled. Preview mode cannot reach a hardware transport because none exists in this build. Declared speed/incline ranges are interface preview ranges, not verified controller limits.

The HOME activity is eligible for a later launcher switch. Existing default observed before installation:

`com.viatek.fitnation.echelon_android/echelon_android.fitnation.viatek.com.echelon_android_new.activities.LandingActivity`

Registering an additional HOME candidate caused Android to resolve HOME to its chooser. After testing, the previously observed stock Echelon HOME component was restored with `cmd package set-home-activity --user 0` and verified. CardioLab was not selected as the default. The app itself contains no code to set the default.

## Validation

- `:app:assembleDebug` and `:app:lintDebug` passed. Existing platform-version/orientation and style warnings remain; no lint errors.
- `test.ps1` passed existing detector, workout/HR packet, and calibration tests plus new console model tests. New tests exercise disconnected-start rejection, active time, pause, multi-boundary time/distance intervals, manual switching, invalid limits/speeds, and a 400 m lap.
- Installed with `adb -s 545C101585 install -r` and launched `ConsoleActivity` on ECHTES-215-S / Android 9 / 1920×1080 / 160 dpi.
- Observed live accelerometer at 100 Hz in the native diagnostics. No watch selected or HR packets measured on this screen in this test. No step accuracy claim: screen mount has not been calibrated with real treadmill data.
- Exercised explicit preview start, speed 4 mph, incline level 3, pause from video overlay, and return to console. Timer/distance advanced in the service while `VideoActivity` was resumed. Sensor service remained active at a requested 10,000 μs sampling period.
- Exercised the native manual interval builder and A/B switching, expanded track, and paused recovery after an app update. Both mini and expanded track paths move counterclockwise as requested.
- Final visual refinements: start/finish at the bottom-right end of the home straight; 100/200/300 m guides, aligned into left and right pairs by using equal-length straight/bend quarters in the schematic. Interval switching is beside Resume/Pause in the header; phase/mode appears below the sensor-status line. The bottom interval row was removed. Rebuilt, linted, installed, visually inspected, and exercised the relocated switch successfully.
- YouTube mobile page loaded in the isolated fallback viewer. Account login and video playback are not yet verified. It uses the device's old system WebView; an up-to-date browser or compatible YouTube app is still needed for the intended signed-in experience.
- CardioLab's declared `SYSTEM_ALERT_WINDOW` app-op was enabled on the development screen to test its four edge windows. FitOS still owns floating windows; its installation, app-op, and service were not changed.
- The OEM `com.android.musicfx` receiver crashed during package installation; observed log identifies that process, not CardioLab. No CardioLab crash observed in these checks.

Screenshots and device inspection output are in `artifacts/echelon-protocol/`. These captures may show the stock FitOS equipment-status overlay over the console activity. CardioLab's own overlay is above it in video mode.

## Next rollout stages

1. Reconnect the original screen-to-treadmill harness with power off. Power on and leave the treadmill idle. Capture the stock app's receive logs; do not open competing serial readers.
2. Confirm identity, protocol units, min/max speed, incline levels, safety-key and stop states, and telemetry freshness. Map physical Start/Stop events and establish whether the controller sends them while idle/asleep.
3. Implement a single transport owner, acknowledged state transitions, stale-link inhibition, stop priority, and controller-derived bounds. Decide how the stock communication service is released before any custom transport opens the port. Test stop/safety behavior before movement controls or intervals.
4. Pair the Garmin on this screen and verify streaming while video is foreground. Pair speakers in Android and test reconnect. Recalibrate vibration at the final mount with a separate counted walk as validation.
5. Verify YouTube playback/sign-in using a supported browser/app. The fallback viewer is not a promise of Google embedded sign-in support.
6. Select CardioLab as HOME only after launch/recovery testing, preserving ADB and stock apps for rollback. Do not uninstall stock software for the initial switch. A HOME declaration alone does not map a treadmill serial Start key or guarantee wake from sleep; those need measured hardware behavior.

Android references: [Android 9 sensor restrictions](https://developer.android.com/about/versions/pie/android-9.0-changes-all#bg-sensor-access), [application overlay windows](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY), [HOME intent category](https://developer.android.com/reference/android/content/Intent#CATEGORY_HOME).
