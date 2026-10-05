# CardioLab: treadmill handoff and next-step plan

Updated 2026-10-04. Repository: https://github.com/ChrisCastelo/CardioLab, branch `main`.

## Start here

**Keep FitOS and the stock Echelon app installed.** The native CardioLab console is built and installed alongside them, but it currently simulates belt operation. The next milestone is to capture replies from the real treadmill using the stock app and original harness, before implementing a transport or changing the launcher.

Moving the same laptop preserves the local backups under `C:\Cardio\artifacts`. A fresh clone on another computer does not include those archives. Copy them separately if changing computers; the APK backup is not a full firmware recovery image.

No custom serial commands, motor commands, firmware changes, or stock-app removals have been performed. The UI's Start, speed, incline, and interval controls currently affect only the preview model.

## Current state (2026-10-04 evening, from the Mac)

CardioLab now replaces FitOS on the treadmill link. Details and captures are in
[the capture report](protocol/idle-capture-2026-10-04.md).

- CardioLab `0.8-boot` owns `/dev/ttyS3` while its console or video overlay is on screen: it
  configures the port with busybox `stty` (8N1, no input/output translation) while holding it
  open, sends the stock connect handshake, a 1 Hz A0 heartbeat, echoes D0/D3, and sends B0/B1/B2
  only from its own buttons. Each command is confirmed by the controller's D0/D2/D3 reply.
- Verified with the user on the belt: start, pause, resume, end, speed 0.5–4.9 mph, incline 0–3,
  physical speed/incline/Start/Stop keys mirrored on screen, and heartbeat loss at 0.5 and 3 mph
  ending the workout with an acceptable stop and incline back to 0.
- FitOS (`com.ucare.fitos`) is **disabled**, not uninstalled. The stock Echelon app and updater
  remain enabled.
- The firmware re-adds the Echelon `LandingActivity` as preferred HOME at every boot
  (`PackageManager: Adding preferred activity …` from system_server), so `set-home-activity`
  does not survive a reboot. CardioLab's `BootReceiver` opens the console after boot instead;
  verified twice by reboot.
- The power key does not sleep the screen while CardioLab is in front (it keeps the screen on).
- Development permissions granted over adb: `READ_LOGS` (detects the stock app still using the
  port), `WRITE_SECURE_SETTINGS` (only to reset the display overscan an earlier build used), `ACCESS_FINE_LOCATION` (BLE heart-rate scan on Android 9) and the
  `SYSTEM_ALERT_WINDOW` app-op. They must be granted again after a reinstall.
- The screen has no navigation bar, so the video bar has `‹ Back` (via CardioLab's own
  accessibility service `NavigationService`, which only performs the system Back action and reads
  no window content) and `CardioLab` (return to the console). Enabled with
  `adb shell settings put secure enabled_accessibility_services com.cardio.lab/com.cardio.lab.NavigationService`
  and `accessibility_enabled 1`; turn off by deleting that setting or in Android Accessibility settings.
- Whenever another app is in front without the video bar (Settings, the Echelon app, a dialog), a
  floating `⌂ CardioLab` button in the bottom-right corner returns to the console.
- Video has two modes, switched in ⚙ (last item). Framed: CardioLab's own player (`VideoActivity`,
  built-in WebView, no sign-in) sits inside the edge panels with `‹ Back` in the header. SmartTube:
  full screen under the compact bar (Back, CardioLab, readings, speed −/+, pause), signed in. Shrinking the display (overscan) was tried and dropped:
  the browser drew edge to edge and full-screen video overflowed. This build has no freeform windows.
  Every layer is composited by the GPU here (`dumpsys SurfaceFlinger` shows Client), so fewer and
  smaller overlays keep playback smoother.
- YouTube runs in SmartTube 32.56 (`org.smarttube.stable`, official GitHub armeabi-v7a release,
  unofficial YouTube client with native hardware playback and TV-code sign-in). It handles YouTube
  links, so CardioLab's Open YouTube launches it. Firefox 157 was tried and removed: its video was
  drawn with large white corruption on this Mali GPU. The built-in WebView (66) cannot sign in. The Garmin heart rate is read from its Broadcast Heart Rate mode
  without Android pairing. Step calibration on this screen: 46 counted, 46 detected.
- On the Mac: adb is `~/Library/Android/sdk/platform-tools/adb`, tests run with `./test.sh`.

Rollback to the stock experience:

```bash
adb -s 545C101585 shell pm enable com.ucare.fitos
adb -s 545C101585 shell pm disable-user --user 0 com.cardio.lab
adb -s 545C101585 shell cmd package set-home-activity --user 0 com.viatek.fitnation.echelon_android/echelon_android.fitnation.viatek.com.echelon_android_new.activities.LandingActivity
```

Re-enable CardioLab afterwards with `pm enable com.cardio.lab`.

## What we are building

CardioLab will be the screen's main shell, with YouTube in the center and treadmill controls and real workout readings around it. The desired physical Start behavior is: first press wakes/opens CardioLab; a subsequent press starts a workout at 2 mph or the verified controller starting speed. Physical Stop and the safety key must retain priority. Whether the controller exposes a usable Start event while idle/asleep remains unverified; declaring an Android HOME activity does not implement this behavior.

Approved layout and behavior:

- Incline at the extreme left, one column in increments of one; speed at the extreme right, one column from 2 through 12 mph. No plus/minus buttons. Incline 0–12 is presently a preview range in **levels**, not a confirmed percentage or hardware limit.
- Header: compact Sensors and Audio at the far left, session status and interval phase next to them, mini 400 m track in the middle, interval switch beside Resume/Pause, and End at the far right.
- Bottom metrics: incline, speed, centered time, distance, steps, and heart rate with a heart icon. No second bottom controls bar.
- Track expands over the video. Progress is counterclockwise, starting at the bottom-right end of the home straight. Split guides show 100/200/300 m; 100/start and 200/300 align vertically. This is a schematic with equal quarter lengths, not a scale stadium survey.
- Quick start and two-speed intervals; interval changes can follow time, distance, or an explicit A/B switch.
- Garmin heart rate, vibration-derived steps calibrated at the final mount, and normal Android Bluetooth speaker pairing/reconnection. Fan control is not a priority.

Latest native layout: [interval header](images/cardiolab-interval-header.png), [video and overlay](images/cardiolab-video.png). The [interactive design archive](../design/README.md) predates the final native track/header refinements; native code and these captures take precedence.

## What has been completed

1. **Sensor lab:** accelerometer peak detection, real-time graphs, workout start/pause/resume, local SQLite history, saved traces, and interrupted-session recovery.
2. **Garmin investigation:** Instinct Solar delivered standard BLE heart-rate notifications to the S24 and CardioLab. No Garmin Connect API is used. Connect was running during the diagnostic, so independence from that app during broadcast activation was not isolated.
3. **Calibration:** guided empty-belt noise measurement followed by a manually counted walk, threshold search, and explicit application of the result. This fits the reference recording; a separate counted walk is still required to validate accuracy.
4. **Echelon access and preservation:** working USB ADB, device inspection, stock APK/native-library backups, and read-only inspection of stock software and logs.
5. **Protocol analysis:** both stock apps use `/dev/ttyS3` at 9600 baud, 8N1. Eight outgoing stock heartbeats were captured. An offline decoder and tests were written. No treadmill receive packet was captured in the detached-screen investigation.
6. **Interactive design and native implementation:** the approved console now runs on the Android 9 screen. A foreground service owns real accelerometer/BLE sensing while video is foreground. Four edge overlay windows leave the video center touchable. The old sensor lab remains accessible.
7. **On-device preview checks:** quick start, speed/incline selection, pause/resume, interval builder and manual A/B switching, expanded track, paused recovery, and video overlay were exercised. Live accelerometer sampling was observed at about 100 Hz. YouTube's mobile page loaded; playback and account login have not been verified.

Evidence: [console verification](console-verification.md), [workout verification](workout-ui-verification.md), [Garmin diagnostic](garmin-diagnostic.md), [calibration](two-stage-calibration.md), and [protocol findings](protocol/echelon-protocol-findings.txt).

## Current device and installed state

| Item | Recorded state |
| --- | --- |
| Screen | Echelon ECHTES-215-S, Rockchip RK3288, Android 9 |
| ADB | Serial `545C101585`, model `rk3288_mtb818` |
| Display | 1920 × 1080, density 160 |
| CardioLab | `com.cardio.lab`, versionCode 4, `0.4-console-preview`, debug build |
| Stock packages | `com.ucare.fitos` (1.65.5), `com.viatek.fitnation.echelon_android` (2.7.0.8-mounted), `com.echelonfit.echelon_21_updater` |
| Default HOME | Stock Echelon `LandingActivity`, restored and verified after installation |
| CardioLab overlay | `SYSTEM_ALERT_WINDOW` app-op enabled for development testing |
| FitOS | Still installed with its services/floating windows; those can overlap CardioLab |
| Preview state | Explicit preview was enabled locally; latest tests left a paused manual interval. Reinspect on reconnect. |
| Physical wiring | User confirmed original DB9 is the screen-to-treadmill connection. Pinout and electrical levels are unverified. |

USB is the laptop maintenance connection, not the treadmill control transport. The screen needs its normal power arrangement. Use the working USB-A cable in the screen's USB/OTG port and the original treadmill harness. Do not infer RS-232/TTL levels or substitute an adapter solely from the DB9 connector shape.

## Verified versus still unknown

| Area | Verified | Still required |
| --- | --- | --- |
| Belt control | Software protocol handlers and outgoing stock heartbeats | Actual replies, identity, units, limits, safe command/state behavior |
| Physical keys | Stock D4 key-event parser; volume codes identified | Start/Stop codes, wake behavior, event delivery while idle/asleep |
| Accelerometer | Real sensor, about 100 Hz on this screen | Calibration and independent step accuracy at the final mount and speeds |
| Heart rate | Standard BLE worked on S24 with Instinct Solar | Pairing, broadcast, reconnect and video-foreground reception on Echelon |
| Video | Mobile YouTube page and overlay loaded | Supported sign-in, playback, fullscreen, keyboard/search, audio routing |
| Audio | Android settings entry | Speaker pairing and actual automatic reconnect |
| Recovery | Preview restores paused; legacy lab saves workouts | Real workout persistence, lifecycle/crash/link-loss handling |
| Launcher | HOME candidate installed; stock remains default | Deliberate launcher switch, boot/reboot recovery, physical wake integration |

Reported controller speed may be a setpoint rather than an independent belt measurement. Do not label it measured belt speed until established. Steps are derived locally from vibration, not an identified stock telemetry field. Missing HR and step sensing must remain distinguishable from valid zero readings.

## Next session: stock, idle, read-only capture

### 1. Connect and establish a baseline

With treadmill power off, reconnect the original screen-to-treadmill harness. Position the laptop and cables clear of the belt. Power on normally, keep the treadmill stopped, and connect the laptop over the working USB/OTG connection. Keep the stock app in charge for this stage. Do not uninstall FitOS or select CardioLab as HOME yet.

From `C:\Cardio` in PowerShell:

```powershell
$taskAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $taskAdb devices -l
& $taskAdb -s 545C101585 shell getprop ro.build.version.release
& $taskAdb -s 545C101585 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Proceed only with the expected device in `device` state. If unauthorized, approve this laptop on the screen. If disconnected, solve USB detection first. A dark display alone did not indicate an app crash during earlier testing; wake it normally if necessary.

### 2. Preserve logs without opening the UART

Capture existing logs first. Do not clear the log buffer. Use a fresh timestamped folder; logs can contain personal/device data and stay in ignored `artifacts`.

```powershell
$taskCapture = Join-Path (Get-Location) ('artifacts\treadmill-idle-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $taskCapture | Out-Null
& $taskAdb -s 545C101585 logcat -d -v threadtime | Out-File -Encoding utf8 (Join-Path $taskCapture 'baseline-logcat.txt')
& $taskAdb -s 545C101585 shell dumpsys sensorservice | Out-File -Encoding utf8 (Join-Path $taskCapture 'sensorservice.txt')
```

For a live idle sample, run this in the terminal for approximately 30–60 seconds and stop it with Ctrl+C (not by pressing the treadmill Start/Stop buttons):

```powershell
& $taskAdb -s 545C101585 logcat -v threadtime 'SearialPortManager:V' '*:S' | Out-File -Encoding utf8 (Join-Path $taskCapture 'idle-serial-logcat.txt')
```

`SearialPortManager` is the stock tag's actual spelling. The decoder accepts saved `SNT` and `RCV` log entries, validates checksums, and reconstructs fragments independently by process and direction:

```powershell
python tools/protocol/decode_capture.py (Join-Path $taskCapture 'idle-serial-logcat.txt') | Out-File -Encoding utf8 (Join-Path $taskCapture 'idle-decoded.jsonl')
```

The decoder is offline only. It does not connect to ADB or a serial port. If logs show no receive entries, inspect stock process/logging state before concluding there is no electrical communication. **Do not open a competing reader on `/dev/ttyS3` while the stock service owns it.** Do not inject queries merely to make the capture more interesting.

### 3. Establish evidence before implementing controls

Identify actual controller replies: A1 identity/units, A3 bounds, D0 state/safety, D1 elapsed/distance/calories/HR field, D2 incline, D3 speed, and D4 keys. A0 is the periodic heartbeat. See the protocol report for byte layouts and caveats; guessed units must not become command conversions.

Record cadence, field scaling, controller firmware, allowed speed/incline increments, idle state and telemetry freshness. The UI's 12 mph/12-level ranges must be reconciled with reported hardware limits. If a field is unavailable, preserve that uncertainty.

Physical Start/Stop and safety-key experiments are a separate supervised stage: pressing Start in the stock app may move the belt. Arrange each test with the user present and ready before initiating it. The initial idle capture should not cause movement.

Deliverable: a short capture report with examples of verified incoming/outgoing frames, units/limits, unresolved fields and the next justified test. Keep raw captures local; commit sanitized findings and synthetic replay fixtures.

## Implementation and rollout gates

### Gate A — real transport and controller state

- Inspect device-node access, available native libraries and ABI compatibility. Determine a reversible way to release the stock communication service before CardioLab opens the UART. Do not assume permissions, run two UART owners, or make arbitrary root/SELinux changes.
- Build one transport owner, streaming frame parsing, checksum validation, bounded queues, explicit connection state and a required heartbeat policy based on observed behavior.
- Separate requested speed/state from confirmed controller state. Reconcile acknowledgments and telemetry; enforce controller-derived units and limits, stop priority and safety-key inhibition. Disable motion requests on stale/absent communications.
- Establish the controller's behavior on link loss and process failure through supervised tests. Sending a stop request cannot be treated as a confirmed stop if the link is lost. Preserve the physical stop/safety mechanism.
- On app recovery, require explicit resumption; never replay a persisted running state into a motor command. Switching interval phases must request and confirm a transition rather than silently replacing displayed speed.
- Test parsers/state machines with saved or synthetic frames first, then stopped-device behavior, then agreed supervised low-speed operation. Do not begin with high-speed shortcuts or unattended intervals.

### Gate B — workout readings and media

- Pair the Garmin on the Echelon, enable its HR broadcast and select it in Sensors. Confirm standard BLE 180D/2A37 notifications without relying on a Garmin Connect API. Determine whether the watch requires any companion action to activate the feed; check interference from an existing phone connection. Verify disconnect, stale HR indication, reconnect and video-foreground reception.
- Run existing vibration calibration at the final screen mount. It measures 6 seconds of empty-belt noise and a 30-second manually counted walk. Validate separately with counted steps; repeat across intended speeds and do not claim accuracy from the calibration fit itself.
- Connect the console to the existing workout/history persistence so real sessions, pauses, HR and step totals survive expected interruptions. Preview sessions currently checkpoint only and are not saved as workout history.
- Verify a compatible supported YouTube app/browser, actual account login and playback, touch/search/keyboard access, fullscreen, and overlay behavior. The fallback WebView has no JavaScript bridge or file/content access, but loading its page does not establish sign-in support.
- Pair speakers in Android; test automatic reconnect after speaker power cycling and tablet restart, with video and workout cues. No custom Bluetooth speaker auto-connect implementation has been verified.

### Gate C — replace the shell reversibly

After transport, safety behavior, sensing and media checks pass, select CardioLab as HOME while keeping ADB and the stock packages available. First remove conflicting stock floating windows/services in a documented reversible way, coordinated with UART ownership. Do not uninstall FitOS just to improve the layout.

Future launcher switch (not part of idle capture):

```powershell
& $taskAdb -s 545C101585 shell cmd package set-home-activity --user 0 com.cardio.lab/.ConsoleActivity
```

Recorded stock HOME rollback:

```powershell
& $taskAdb -s 545C101585 shell cmd package set-home-activity --user 0 com.viatek.fitnation.echelon_android/echelon_android.fitnation.viatek.com.echelon_android_new.activities.LandingActivity
```

Verify the resolver after either operation. Test reboot, sleep/wake, foreground/background transitions, service/process failure, USB removal and loss/recovery of treadmill communication. Physical Start-to-wake/launch and second-press-to-run must be designed from captured key behavior; there is no boot receiver or physical-key mapping in this build.

Only consider uninstalling stock apps after there is a proven standalone replacement and a tested recovery route. Keeping them installed may remain the simplest rollback option.

## Source map and development checks

All native sources below are in `app/src/main/java/com/cardio/lab/`:

| Files | Responsibility |
| --- | --- |
| `ConsoleActivity`, `ConsoleUi`, `TrackView` | Native shell, shared edge UI, mini/expanded track |
| `ConsoleService`, `OverlayControls`, `VideoActivity` | Background sensing, edge windows, isolated video fallback |
| `ConsoleSession` | Pure Java preview/session and interval model; no hardware transport |
| `StepDetector`, `StepCalibration`, `CalibrationWizard` | Vibration detection and calibration |
| `HeartRateClient`, `HeartRatePacket` | Direct standard BLE HR |
| `MainActivity`, `Workout`, `SessionDatabase`, `SessionLog`, `TraceView` | Original sensor lab, recording, history and traces |
| `tools/protocol/` | Offline stock-log decoder and unit tests |
| `design/`, `tools/verify-console-mockup.cjs` | Earlier interactive preview and its browser smoke check |

`ble-diagnostic` is the earlier phone probe, with minSdk 31; **do not install it on this Android 9 screen**. Main `app` has minSdk 26. The project uses AGP 8.13.0, Gradle 8.14.3 and compileSdk 36. Use the installed Android Studio JBR (Java 17 or newer) and a local Android SDK; `local.properties` is intentionally untracked.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:lintDebug :ble-diagnostic:assembleDebug :ble-diagnostic:lintDebug
.\test.ps1
python -m unittest discover -s tools/protocol -p 'test_*.py' -v
```

This laptop also has Python at `C:\Python314\python.exe`. `test.ps1` uses Android Studio's JBR explicitly. For optional browser preview checks see `design/README.md`.

Final handoff checks on 2026-10-04 passed: debug assembly and lint for both modules; detector, workout/HR packet, calibration and console-model tests; all five offline decoder tests; and the archived browser mockup smoke checks. Gradle reports existing deprecation warnings. Browser checks exercise simulations, including wake/audio behavior; they do not verify those integrations on the treadmill. On-device checks above were performed during console development, not repeated as part of this Git handoff.

For a later intentional CardioLab update, `app/build/outputs/apk/debug/app-debug.apk` can be installed with `adb install -r`, then launched using `adb shell am start -n com.cardio.lab/.ConsoleActivity` with the expected `-s` serial argument. Preserve app data and verify HOME afterward. Debug builds are development artifacts, not a release-signing/recovery strategy.

## Local archives and repository scope

- `C:\Cardio\artifacts\echelon-backup-20261004-132417`: 75 APKs with recorded SHA-256 verification, native libraries, device information and available shared app data. See `apk-manifest.csv/json`, `backup-summary.json` and `native-library-manifest.json`. This is **not** a complete firmware/partition backup or a guarantee of recoverability after a factory reset.
- `C:\Cardio\artifacts\echelon-protocol`: raw logs, device inspections, JADX output for stock apps, original protocol notes/decoder and screenshots. Some decompilation had errors; source inspection is evidence, not a buildable OEM source tree.
- `C:\Users\c_pc\.codex\visualizations\2026\10\03\01a1004c-b417-73f0-a81d-0bd6388969bf`: original interactive visualization. A usable snapshot is now archived in `design/`.

Git includes our source, tests, design, selected UI screenshots and authored findings. OEM APKs/decompiled code, raw logs, local credentials/configuration and generated build output remain ignored. Preserve those local archives separately; do not force-add them to the repository.

## Prompt to resume on the laptop

> Read `docs/TREADMILL-HANDOFF.md` and the referenced console/protocol findings. The screen is now connected to the treadmill through its original DB9 harness and to this laptop through USB/OTG. Verify the ADB device and current installed/default-app state, then begin a read-only idle capture from the stock app. Keep FitOS installed, do not open a competing UART reader, and do not issue motion commands. Decode the captured replies, establish controller identity/units/limits/safety status, and report the next evidence-based step. Preserve the approved layout and existing Garmin/vibration calibration system.
