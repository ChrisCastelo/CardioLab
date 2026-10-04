# Ultrahuman Ring AIR diagnosis — 2026-09-28

## Measured result

A separate `com.cardio.diagnostic` Android app connected successfully to the ring from the Samsung S24 (SM-S921W, Android 14). GATT discovery succeeded and returned eight services. Device information reads succeeded:

- Manufacturer: Ultrahuman Healthcare Pvt Ltd
- Model characteristic: R1.01.01.01
- Firmware: 02.01.74.00
- Standard Heart Rate service `180D` / measurement `2A37`: **absent in this discovery**.

This proves direct BLE connectivity, but does not establish live heart-rate access. Developer Mode was enabled in the vendor app. No standard heart-rate notifications could be tested because the characteristic was absent. Other operating modes and firmware versions were not tested.

Discovered services:

| Service UUID | Observed characteristics |
| --- | --- |
| `00001801-0000-1000-8000-00805f9b34fb` | `2A05`, `2B29`, `2B2A` |
| `00001800-0000-1000-8000-00805f9b34fb` | `2A00`, `2A01`, `2A04` |
| `0000180a-0000-1000-8000-00805f9b34fb` | `2A24`, `2A29`, `2A25`, `2A26`, `2A27` |
| `8d53dc1d-1db7-4cd3-868b-8a527460aa84` | `da2e7828-fbce-4e01-ae9e-261174997c48` |
| `86f61000-f706-58a0-95b2-1fb9261e4dc7` | `86f61001…` read/notify |
| `86f65000-f706-58a0-95b2-1fb9261e4dc7` | `86f65001…` write; `86f65002…` read/notify |
| `86f66000-f706-58a0-95b2-1fb9261e4dc7` | `86f66001…` read/notify |
| `86f63000-f706-58a0-95b2-1fb9261e4dc7` | `86f63001…` read/notify |

The full local report is `artifacts/ring-ble-diagnostic.txt`. Private phone artifacts remain Git-ignored; no device address or serial is embedded in the diagnostic source.

The previously downloaded UltraSignal recording contained timestamped green, infrared and red optical samples, with no BPM field. Access to that recording does not demonstrate a live BPM stream.

## Interpretation and next experiment

[Gadgetbridge's protocol notes](https://freeyourgadget.codeberg.page/gadgetbridge_org/internals/specifics/ultrahuman-protocol/) identify `86f61000…` as device state and `86f65000…` as command/response. They document retrieving stored records containing timestamped BPM, using older firmware 02.00.07.46. These roles were not independently tested here. Its [device support page](https://freeyourgadget.codeberg.page/gadgetbridge_org/gadgets/wearables/ultrahuman/) still lists realtime measurements as missing.

The next bounded experiment would retrieve the latest stored BPM and compare its timestamp and update interval with the vendor app during a workout. That would distinguish a usable near-live feed from delayed history. This requires a custom protocol implementation; the current probe deliberately sends no proprietary commands. No claim of live compatibility should be made until that experiment succeeds on the current firmware.

## Repeating the standard-service probe

The diagnostic is a separate app (Android 12+) and does not modify Cardio Lab. It requests only Nearby Devices connection permission, connects directly to an entered address, lists GATT services, and reads manufacturer/model/firmware. If a standard heart-rate measurement exists, it subscribes for 20 seconds; otherwise it disconnects after reading identification. A 45-second timeout and foreground lifecycle bound the connection. It does not scan, unpair, reset, update firmware, or send proprietary sensor commands.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :ble-diagnostic:assembleDebug :ble-diagnostic:lintDebug
$taskAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $taskAdb -s DEVICE_SERIAL install -r .\ble-diagnostic\build\outputs\apk\debug\ble-diagnostic-debug.apk
& $taskAdb -s DEVICE_SERIAL shell am start -n com.cardio.diagnostic/.DiagnosticActivity
# Enter the intended peripheral address in the app and grant Nearby Devices permission.
& $taskAdb -s DEVICE_SERIAL shell run-as com.cardio.diagnostic cat files/last-report.txt
```

Validation: debug build and Android lint completed successfully; installed and exercised on the S24 with the ring. The standard-HR notification path remains untested on hardware that exposes that service.
