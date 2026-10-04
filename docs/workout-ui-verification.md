# Cardio Lab 0.2 — S24 verification

Tested on 2026-09-28, Samsung S24 (SM-S921W), Android 14.

## Delivered

- Landscape layout based on the approved preview: centered large timer, step/vibration and HR/trend panels, centered Start/Pause/Resume, separate Stop, and Save/Discard/Continue prompt.
- Paired BLE sensor chooser, remembered Instinct Solar selection, standard HR subscription, stale-reading display and reconnect backoff.
- Local SQLite sessions and graph samples, off-main-thread checkpointing, paused draft recovery, saved-session list and graph details.
- Sensor calibration and threshold adjustment under Sensor setup. No CSV export in the new interface.

## Verification performed

- `:app:assembleDebug` and `:app:lintDebug` passed. Lint: 0 errors, 13 warnings (target/Gradle versions, orientation policy, backup compatibility, drawing allocation, programmatic view constructor, and English UI strings).
- Existing synthetic step-detector tests passed. Added tests passed for 8/16-bit HR, malformed packets, sensor contact flags, zero readings, paused-time exclusion, HR average exclusions, and recovered session timing.
- Installed version 0.2 on the S24 and visually checked the landscape layout.
- Selected Instinct Solar and observed changing real BPM inside Cardio Lab.
- Started, paused, resumed, stopped and saved a short test. Database had 15,073 ms active time, 0 stationary steps, 24 valid HR notifications, and 145 trace samples. The average shown was 66 BPM. Confirmed duration, counts, and HR average remained unchanged during pause.
- Opened the saved entry and rendered its cadence/HR charts; vibration is available below in the scrollable details.
- Started another draft, left the app, force-stopped it, and relaunched. Recovered 6,118 ms paused draft and automatically reconnected to the Garmin feed.
- Discarded that recovery-test draft and confirmed its 59 samples were removed while the saved session remained.
- SQLite integrity check returned `ok`; no Cardio Lab / AndroidRuntime error log was found in the inspected recent log window.

The 15-second test session remains available under Sessions. The app was left at Ready with the Garmin selected. Phone-specific screenshots and database inspection copies are local in the ignored `artifacts/` directory.

## Still requiring a real workout

Treadmill vibration step accuracy, automatic watch broadcasting when starting a workout, long-session BLE stability, and recovery after actual watch/radio signal loss. Foreground exit/re-entry reconnection was verified; radio-loss backoff was not exercised on hardware. HR average is based on valid received notifications. HR is considered stale after five seconds without a valid measurement; the UI then displays a dash.
